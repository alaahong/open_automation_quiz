/* Java 21 Hugging Face Inference-based CI failure analyzer (highlights-only) with rule-based fallback + preflight.
 * - Extracts high-signal error lines (first N matches) from combined logs.
 * - PRE-FLIGHT: probes the configured HF model with a tiny request to detect 404/401/403 early,
 *   auto-falls back to a public model list if inaccessible.
 * - Calls Hugging Face Inference API (text-generation) to produce: root causes, minimal fix, next steps.
 * - If HF returns non-2xx (e.g., 404/401/403/503), falls back to a built-in rule engine so PR still receives suggestions.
 *
 * Optional env:
 * - HF_MODEL: primary model to use (trimmed). Default "mistralai/Mistral-7B-Instruct-v0.2"
 * - HF_FALLBACK_MODELS: comma-separated fallback list, e.g.:
 *     "mistralai/Mistral-7B-Instruct-v0.2,microsoft/Phi-3-mini-4k-instruct,Qwen/Qwen2-7B-Instruct,bigcode/starcoder2-3b"
 *   If not set, a sane default list is used (see DEFAULT_FALLBACKS).
 * - HF_MAX_NEW_TOKENS, ANALYZER_MAX_HIGHLIGHTS (see workflow)
 */

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

public class HfCiFailureAnalyzer {
    private static final int BODY_MAX_CHARS = 60000;
    private static final int LOG_MAX_CHARS = 120000;

    private static final String[] DEFAULT_FALLBACKS = new String[]{
            "mistralai/Mistral-7B-Instruct-v0.2",
            "microsoft/Phi-3-mini-4k-instruct",
            "Qwen/Qwen2-7B-Instruct",
            "bigcode/starcoder2-3b"
    };

    public static void main(String[] args) {
        try {
            String repo = requireEnv("REPO");
            String runId = requireEnv("RUN_ID");
            String workflowName = getenvOr("WORKFLOW_NAME", "(unknown)").trim();
            String serverUrl = getenvOr("SERVER_URL", "https://github.com").trim();
            int highlightMax = parseIntSafe(getenvOr("ANALYZER_MAX_HIGHLIGHTS", "200"), 200);
            int hfMaxNew = parseIntSafe(getenvOr("HF_MAX_NEW_TOKENS", "800"), 800);

            String hfToken = getenvOr("HF_API_TOKEN", "").trim();
            String hfModelRaw = getenvOr("HF_MODEL", "mistralai/Mistral-7B-Instruct-v0.2");
            String hfModel = hfModelRaw == null ? "" : hfModelRaw.trim();

            // Prepare fallback list (trim each)
            List<String> fallbacks = new ArrayList<>();
            String cfgFallbacks = getenvOr("HF_FALLBACK_MODELS", "");
            if (!isBlank(cfgFallbacks)) {
                for (String s : cfgFallbacks.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) fallbacks.add(t);
                }
            } else {
                fallbacks.addAll(Arrays.asList(DEFAULT_FALLBACKS));
            }

            // Run details
            String runUrl = ghApiJq("repos/" + repo + "/actions/runs/" + runId, ".html_url");
            if (isBlank(runUrl)) runUrl = serverUrl + "/" + repo + "/actions/runs/" + runId;
            String event = ghApiJq("repos/" + repo + "/actions/runs/" + runId, ".event");
            String headBranch = ghApiJq("repos/" + repo + "/actions/runs/" + runId, ".head_branch");
            String headSha = ghApiJq("repos/" + repo + "/actions/runs/" + runId, ".head_sha");
            String runConclusion = ghApiJq("repos/" + repo + "/actions/runs/" + runId, ".conclusion");

            // PR number (0 if none)
            String prNumStr = ghApiJq("repos/" + repo + "/actions/runs/" + runId, "(.pull_requests[0].number // 0)");
            int prNumber = parseIntSafe(prNumStr, 0);

            // Resolve failed job URL (first failed job)
            String jobsEndpoint = "repos/" + repo + "/actions/runs/" + runId + "/jobs?per_page=100";
            String jobHtmlUrl = ghApiJq(jobsEndpoint,
                    "([.jobs[] | select(.conclusion != \"success\" and .conclusion != null) | .html_url] | first // \"\")");
            if (isBlank(jobHtmlUrl)) jobHtmlUrl = runUrl;

            // Failed jobs/steps summary
            String jobsSummary = ghApiJq(jobsEndpoint,
                    "([.jobs[] "
                            + "| select(.conclusion != \"success\" and .conclusion != null) "
                            + "| \"Job: \\(.name)\\n\" "
                            + "+ \"Conclusion: \\(.conclusion)\\n\" "
                            + "+ \"Started: \\(.started_at)\\n\" "
                            + "+ \"Completed: \\(.completed_at)\\n\" "
                            + "+ \"Failed steps:\\n\" "
                            + "+ ( [ (.steps // [])[] "
                            + "| select(.conclusion != \"success\" and .conclusion != null) "
                            + "| \"- \\(.name) (conclusion: \\(.conclusion))\" ] "
                            + " | if length>0 then join(\"\\n\") else \"(none listed)\" end )"
                            + "] | if length>0 then join(\"\\n\\n\") else \"(no summary)\" end)"
            );

            // Read logs and extract highlights
            String combinedLogs = readCombinedLogs();
            String errorHighlights = extractErrorHighlights(combinedLogs, highlightMax);

            // HF model preflight
            String chosenModel = hfModel;
            String preflightDiag;
            if (isBlank(hfToken)) {
                preflightDiag = "HF preflight: skipped (HF_API_TOKEN missing)";
            } else {
                PreflightResult pr = preflightHF(hfToken, chosenModel);
                if (pr.ok) {
                    preflightDiag = "HF preflight: OK (" + chosenModel + ") status=" + pr.status + (pr.note.isEmpty() ? "" : " - " + pr.note);
                } else {
                    StringBuilder diag = new StringBuilder();
                    diag.append("HF preflight: FAILED for ").append(chosenModel)
                            .append(" (status=").append(pr.status).append(") - ").append(pr.note)
                            .append("; trying fallbacks: ");
                    boolean switched = false;
                    for (int i = 0; i < fallbacks.size(); i++) {
                        String fb = fallbacks.get(i);
                        if (fb.equalsIgnoreCase(chosenModel)) continue; // skip same
                        PreflightResult prFb = preflightHF(hfToken, fb);
                        diag.append(fb).append("[").append(prFb.status).append(prFb.ok ? "✔" : "✖").append("]");
                        if (i < fallbacks.size() - 1) diag.append(", ");
                        if (prFb.ok && !switched) {
                            chosenModel = fb;
                            switched = true;
                        }
                    }
                    if (switched) {
                        diag.append(" -> using fallback: ").append(chosenModel);
                    } else {
                        diag.append(" -> no accessible fallback, will use rule-based fallback if HF call fails.");
                    }
                    preflightDiag = diag.toString();
                }
            }

            String context = String.join("\n", List.of(
                    "Repository: " + repo,
                    "Workflow: " + workflowName,
                    "Run ID: " + runId,
                    "Run URL: " + runUrl,
                    "Event: " + event,
                    "Head branch: " + headBranch,
                    "Commit SHA: " + headSha,
                    "Run conclusion: " + runConclusion,
                    "HF model (requested): " + hfModel,
                    "HF model (chosen): " + chosenModel,
                    preflightDiag
            ));

            String prompt = buildPrompt(context, jobsSummary, errorHighlights);

            String analysis;
            if (isBlank(hfToken)) {
                analysis = "Hugging Face Inference is not configured (HF_API_TOKEN is missing). " +
                        "Please add HF_API_TOKEN in repository Secrets and re-run this workflow.\n\n" +
                        ruleBasedAnalysis(errorHighlights);
            } else {
                analysis = analyzeWithHuggingFaceOrFallback(hfToken, chosenModel, prompt, errorHighlights, combinedLogs, hfMaxNew);
            }

            // Build comment body
            StringBuilder body = new StringBuilder();
            body.append("🤖 CI failure: Hugging Face Inference analysis (with preflight and rule-based fallback)\n\n");
            body.append("- Run: ").append(runUrl).append("\n");
            body.append("- Failed job: ").append(jobHtmlUrl).append("\n\n");

            body.append("Context (includes HF preflight):\n```\n").append(context).append("\n```\n\n");
            body.append("Failed jobs/steps summary:\n```\n").append(jobsSummary).append("\n```\n\n");
            body.append("Error Highlights (first ").append(highlightMax).append(" matching lines):\n```txt\n")
                    .append(errorHighlights).append("\n```\n\n");
            body.append("Analysis and suggestions:\n").append(analysis).append("\n");

            String finalBody = body.toString();
            if (finalBody.length() > BODY_MAX_CHARS) {
                finalBody = finalBody.substring(0, BODY_MAX_CHARS) + "\n\n…(truncated)…";
            }

            // Post to PR or create issue
            Path tmp = Files.createTempFile("hf-analyzer-", ".md");
            Files.writeString(tmp, finalBody, StandardCharsets.UTF_8);

            if (prNumber > 0) {
                runProcess(new String[]{
                        "gh", "pr", "comment",
                        String.valueOf(prNumber),
                        "--repo", repo,
                        "--body-file", tmp.toString()
                });
                System.out.println("Posted HF analysis to PR #" + prNumber);
            } else {
                String title = "CI failed: Hugging Face analysis for run " + runId + " (" + workflowName + ")";
                runProcess(new String[]{
                        "gh", "issue", "create",
                        "--repo", repo,
                        "-t", title,
                        "--body-file", tmp.toString(),
                        "-l", "ci-failure",
                        "-l", "ai-analysis-hf"
                });
                System.out.println("Created issue with HF analysis");
            }

        } catch (Exception e) {
            System.err.println("Failed to post HF analysis: " + e);
            System.exit(0);
        }
    }

    // ----------------- HF call with smarter errors + fallback -----------------

    private static String analyzeWithHuggingFaceOrFallback(String token, String model, String prompt,
                                                           String highlights, String fullLogs, int maxNewTokens) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            String body = """
                    {
                      "inputs": %s,
                      "parameters": {
                        "max_new_tokens": %d,
                        "temperature": 0.2,
                        "return_full_text": false
                      }
                    }
                    """.formatted(jsonString(prompt), maxNewTokens);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-inference.huggingface.co/models/" + model))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            int maxAttempts = 5;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    String txt = resp.body();
                    String gen = extractJsonField(txt, "generated_text");
                    if (isBlank(gen)) gen = txt; // fallback raw
                    return "```\n" + gen.trim() + "\n```";
                }
                if (status == 503) {
                    int sleepSec = parseEstimatedTimeSeconds(resp.body(), 15);
                    sleepSec = Math.min(sleepSec + (attempt - 1) * 5, 60);
                    System.out.println("HF 503 loading. Waiting " + sleepSec + "s and retrying (" + attempt + "/" + maxAttempts + ")...");
                    Thread.sleep(sleepSec * 1000L);
                    continue;
                }

                String hint;
                if (status == 401) {
                    hint = "HF 401 Unauthorized: check HF_API_TOKEN.";
                } else if (status == 403) {
                    hint = "HF 403 Forbidden: token lacks access to the model (gated/private). Accept model terms or use an open model.";
                } else if (status == 404) {
                    hint = "HF 404 Not Found: HF_MODEL likely misspelled or not accessible. Try a public model (e.g., mistralai/Mistral-7B-Instruct-v0.2).";
                } else {
                    hint = "HF call failed: " + status;
                }
                return hint + "\n\nFalling back to rule-based analysis:\n" + ruleBasedAnalysis(highlights);
            }
            return "HF did not become ready after retries.\n\nFalling back to rule-based analysis:\n" + ruleBasedAnalysis(highlights);
        } catch (Exception e) {
            return "HF call error: " + e.getMessage() + "\n\nFalling back to rule-based analysis:\n" + ruleBasedAnalysis(highlights);
        }
    }

    // ----------------- HF preflight -----------------

    private static class PreflightResult {
        final boolean ok;
        final int status;
        final String note;

        PreflightResult(boolean ok, int status, String note) {
            this.ok = ok;
            this.status = status;
            this.note = note == null ? "" : note;
        }
    }

    // Treat 2xx and 503 (loading) as accessible. 401/403/404 -> not accessible with reason.
    private static PreflightResult preflightHF(String token, String model) {
        String trimmedModel = model == null ? "" : model.trim();
        if (trimmedModel.isEmpty()) return new PreflightResult(false, 404, "empty model name");
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            String body = """
                    {"inputs":"preflight","parameters":{"max_new_tokens":1,"temperature":0.0,"return_full_text":false}}
                    """;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-inference.huggingface.co/models/" + trimmedModel))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int sc = resp.statusCode();
            if (sc >= 200 && sc < 300) return new PreflightResult(true, sc, "ready");
            if (sc == 503) return new PreflightResult(true, sc, "loading");
            if (sc == 401) return new PreflightResult(false, sc, "unauthorized token");
            if (sc == 403) return new PreflightResult(false, sc, "forbidden (gated/private or no terms accepted)");
            if (sc == 404) return new PreflightResult(false, sc, "not found (typo or no access)");
            return new PreflightResult(false, sc, "unexpected status");
        } catch (Exception e) {
            return new PreflightResult(false, -1, "preflight error: " + e.getMessage());
        }
    }

    private static int parseEstimatedTimeSeconds(String json, int def) {
        try {
            String key = "\"estimated_time\"";
            int i = json.indexOf(key);
            if (i < 0) return def;
            int colon = json.indexOf(':', i + key.length());
            if (colon < 0) return def;
            int end = colon + 1;
            while (end < json.length() && (Character.isWhitespace(json.charAt(end)) || json.charAt(end) == '\"')) end++;
            StringBuilder num = new StringBuilder();
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
                num.append(json.charAt(end++));
            }
            double seconds = Double.parseDouble(num.toString());
            return Math.max(1, (int) Math.ceil(seconds));
        } catch (Exception e) {
            return def;
        }
    }

    private static String buildPrompt(String ctx, String jobsSummary, String highlights) {
        String instruction = """
                You are a senior CI/CD debugging assistant.
                Based on the CI context, failed jobs/steps summary, and the Error Highlights below, provide:
                1) Top 1–3 likely root causes (ranked by confidence; cite key lines/files/commands).
                2) The minimal fix to make CI pass (concrete commands or code changes).
                3) Next steps to verify or further debug (specific commands/files/log keywords).
                If this resembles dependency/cache/permission/concurrency/timeout/environment/test flakiness,
                call it out and provide a proven template fix. Keep the answer concise and structured.
                """;
        String prompt = "CI Context:\n" + ctx + "\n\n" +
                "Failed jobs/steps summary:\n" + jobsSummary + "\n\n" +
                "Error Highlights:\n" + highlights + "\n\n" +
                instruction;
        if (prompt.length() > 16000) {
            prompt = prompt.substring(prompt.length() - 16000);
            prompt = "...[prompt truncated]...\n" + prompt;
        }
        return prompt;
    }

    // ----------------- Log processing -----------------

    private static String readCombinedLogs() throws IOException {
        Path p = Paths.get("logs", "combined.txt");
        if (!Files.exists(p)) return "No combined logs were captured.";
        String text = Files.readString(p, StandardCharsets.UTF_8);
        if (text.length() > LOG_MAX_CHARS) {
            text = "...[truncated to last " + LOG_MAX_CHARS + " chars]...\n" +
                    text.substring(text.length() - LOG_MAX_CHARS);
        }
        return text;
    }

    private static String extractErrorHighlights(String text, int maxLines) {
        if (text == null || text.isBlank()) return "(no highlights)";
        String[] lines = text.split("\\R");
        Pattern re = Pattern.compile(
                "\\b(error|err!|failed|failure|exception|traceback|no classdef|classnotfound|assertion(?:error)?|segmentation fault|build failed|gradle|maven|npm ERR!|yarn ERR!|test failed|cannot find symbol|undefined reference|stack trace|fatal:)\\b",
                Pattern.CASE_INSENSITIVE
        );
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String ln : lines) {
            if (re.matcher(ln).find()) {
                sb.append(ln).append("\n");
                if (++count >= maxLines) break;
            }
        }
        return count > 0 ? sb.toString().trim() : "(no lines matched common failure patterns)";
    }

    // ----------------- Rule-based fallback -----------------

    private record Rule(String name, Pattern pattern, String explanation, List<String> minimalFix,
                        List<String> nextSteps) {
    }

    private record DiagnosisEntry(Rule rule, int score, List<String> samples) {
    }

    private record DiagnosisResult(List<DiagnosisEntry> entries) {
    }

    private static String ruleBasedAnalysis(String highlights) {
        List<Rule> rules = defaultRules();
        DiagnosisResult diag = diagnose(highlights, rules);
        StringBuilder sb = new StringBuilder();
        if (diag.entries.isEmpty()) {
            sb.append("- No specific rule matched. Generic triage:\n");
            sb.append("  • Re-run failed jobs with verbose logging.\n");
            sb.append("  • Verify toolchain versions (JDK/Node/Python) match local dev.\n");
            sb.append("  • Clear caches (Actions cache, package managers).\n");
            sb.append("  • Check permissions/secrets for private registries/tokens.\n");
            sb.append("  • Confirm network access to central registries.\n");
            return sb.toString();
        }
        int rank = 1;
        for (DiagnosisEntry e : diag.entries) {
            sb.append(rank++).append(") ").append(e.rule.name).append("\n");
            if (!isBlank(e.rule.explanation)) {
                sb.append("   - Why: ").append(e.rule.explanation).append("\n");
            }
            if (!e.samples.isEmpty()) {
                sb.append("   - Evidence examples:\n");
                for (String s : e.samples.subList(0, Math.min(3, e.samples.size()))) {
                    sb.append("     • ").append(s).append("\n");
                }
            }
            if (!e.rule.minimalFix.isEmpty()) {
                sb.append("   - Minimal fix:\n");
                for (String f : e.rule.minimalFix) sb.append("     • ").append(f).append("\n");
            }
            if (!e.rule.nextSteps.isEmpty()) {
                sb.append("   - Next steps:\n");
                for (String n : e.rule.nextSteps) sb.append("     • ").append(n).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static DiagnosisResult diagnose(String highlights, List<Rule> rules) {
        List<DiagnosisEntry> entries = new ArrayList<>();
        List<String> lines = Arrays.asList(highlights.split("\\R"));
        for (Rule r : rules) {
            int score = 0;
            List<String> samples = new ArrayList<>();
            for (String ln : lines) {
                if (r.pattern.matcher(ln).find()) {
                    score++;
                    if (samples.size() < 5) samples.add(ln);
                }
            }
            if (score > 0) entries.add(new DiagnosisEntry(r, score, samples));
        }
        entries.sort((a, b) -> Integer.compare(b.score, a.score));
        return new DiagnosisResult(entries);
    }

    private static List<Rule> defaultRules() {
        List<Rule> rs = new ArrayList<>();
        rs.add(new Rule(
                "Java: ClassNotFound/NoClassDefFoundError",
                Pattern.compile("ClassNotFoundException|NoClassDefFoundError|cannot find symbol.*class", Pattern.CASE_INSENSITIVE),
                "Missing class due to wrong dependency/package/class name or build misconfiguration.",
                List.of(
                        "Check the class/package name and that it is compiled.",
                        "Add/align the dependency providing the class (Maven/Gradle).",
                        "Ensure test source set is compiled and included."
                ),
                List.of(
                        "Search the missing class name in logs and locate which module should provide it.",
                        "Run a clean build locally: mvn -U -e -X clean test or gradle --info clean test.",
                        "Check classpath exclusions/relocation."
                )
        ));
        rs.add(new Rule(
                "Maven: artifact not found / dependency resolution",
                Pattern.compile("Could not find artifact|Could not resolve dependencies|failure to find .* in .* was cached", Pattern.CASE_INSENSITIVE),
                "Dependency cannot be resolved from configured repositories.",
                List.of(
                        "Verify artifact coordinates/version and repository availability.",
                        "Add/repair mirrors/repositories; ensure credentials for private repos.",
                        "Clear caches: rm -rf ~/.m2/repository; purge Actions cache key."
                ),
                List.of(
                        "Enable debug: mvn -X -e -U -DskipTests=false test.",
                        "Check rate limits/network/proxy/mirror settings."
                )
        ));
        rs.add(new Rule(
                "Gradle: Toolchain/daemon/resolve issues",
                Pattern.compile("Could not resolve all files|Execution failed for task|Toolchain.*not found|Daemon (stopped|disappeared)", Pattern.CASE_INSENSITIVE),
                "Gradle failed to resolve dependencies or required JDK toolchain.",
                List.of(
                        "Specify a compatible toolchain (setup-java Temurin 21).",
                        "Invalidate caches; re-run with --refresh-dependencies."
                ),
                List.of(
                        "Run gradle --stacktrace --info; verify JAVA_HOME and Gradle version."
                )
        ));
        rs.add(new Rule(
                "JUnit/Test assertion failures",
                Pattern.compile("AssertionError|expected:.*but was:|Tests? failed:|There were test failures", Pattern.CASE_INSENSITIVE),
                "Tests failed due to assertion mismatch or runtime failure.",
                List.of(
                        "Fix the test expectation or the code under test.",
                        "If flaky, add retries or stabilize preconditions."
                ),
                List.of(
                        "Open the failed test report (Surefire/Failsafe).",
                        "Run the specific test locally with same JDK/flags."
                )
        ));
        rs.add(new Rule(
                "Node: npm/yarn dependency resolution",
                Pattern.compile("npm ERR! ERESOLVE|npm ERR! code (EAI_AGAIN|ENOTFOUND|ETIMEDOUT)|yarn ERR!", Pattern.CASE_INSENSITIVE),
                "Dependency conflict or network/DNS issue.",
                List.of(
                        "Pin versions or add overrides/resolutions.",
                        "Retry with clean cache: npm ci or yarn --check-files.",
                        "Ensure registry/network access."
                ),
                List.of(
                        "Check which package has a conflicting peer dependency.",
                        "Enable verbose logs: npm ci --verbose."
                )
        ));
        rs.add(new Rule(
                "Node: Cannot find module / TS compilation",
                Pattern.compile("Cannot find module|TS\\d{4}:|TSError", Pattern.CASE_INSENSITIVE),
                "Missing module/build output or TypeScript compile errors.",
                List.of(
                        "Install/build before use; fix TS errors.",
                        "Align tsconfig path mappings."
                ),
                List.of(
                        "Inspect exact TS error codes; run tsc --noEmit."
                )
        ));
        rs.add(new Rule(
                "Python: ModuleNotFoundError/ImportError",
                Pattern.compile("ModuleNotFoundError|ImportError", Pattern.CASE_INSENSITIVE),
                "Missing Python module or wrong environment.",
                List.of(
                        "Add dependency to requirements and reinstall.",
                        "Check PYTHONPATH/venv and interpreter version."
                ),
                List.of(
                        "pip install -U -r requirements.txt in a clean venv.",
                        "Run pytest -vv to reproduce."
                )
        ));
        rs.add(new Rule(
                "OutOfMemory / resource limits",
                Pattern.compile("OutOfMemoryError|Java heap space|Killed process.*out of memory|ENOMEM", Pattern.CASE_INSENSITIVE),
                "Build/test ran out of memory or resources.",
                List.of(
                        "Reduce parallelism or memory usage; split tests.",
                        "Use larger runner or increase memory flags (e.g., -Xmx)."
                ),
                List.of(
                        "Check memory graphs; enable GC logs; confirm dataset sizes."
                )
        ));
        rs.add(new Rule(
                "Timeout / long-running step",
                Pattern.compile("timeout|timed out after|No output has been received", Pattern.CASE_INSENSITIVE),
                "Step exceeded time limits or stalled.",
                List.of(
                        "Increase timeout-minutes or add keep-alive logging.",
                        "Optimize the long-running operation or add retries/backoff."
                ),
                List.of(
                        "Identify the hanging sub-command; add --debug/--info.",
                        "Check network calls and external service SLAs."
                )
        ));
        rs.add(new Rule(
                "Permission / auth / token missing",
                Pattern.compile("Permission denied|access denied|401 Unauthorized|403 Forbidden|denied|Authentication failed", Pattern.CASE_INSENSITIVE),
                "Insufficient permissions or missing/expired credentials.",
                List.of(
                        "Grant required scopes/permissions to GITHUB_TOKEN/secret.",
                        "Configure credentials for private registries."
                ),
                List.of(
                        "Validate token scopes; test with curl -I.",
                        "Check SSO enforcement."
                )
        ));
        return rs;
    }

    // ----------------- GH helpers & utils -----------------

    private static String ghApiJq(String endpoint, String jq) throws IOException, InterruptedException {
        return runProcess(new String[]{"gh", "api", endpoint, "--jq", jq}).trim();
    }

    private static String runProcess(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        if (isBlank(env.get("GH_TOKEN"))) {
            String gt = env.getOrDefault("GITHUB_TOKEN", "");
            if (!isBlank(gt)) env.put("GH_TOKEN", gt);
        }
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) throw new IOException("Command failed (" + code + "): " + String.join(" ", cmd) + "\n" + out);
        return out;
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) return "";
        int colon = json.indexOf(':', i + key.length());
        if (colon < 0) return "";
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) return "";
        int end = startQuote + 1;
        boolean escape = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && !escape) break;
            escape = (c == '\\') && !escape;
            end++;
        }
        if (end >= json.length()) return "";
        String val = json.substring(startQuote + 1, end);
        return val.replace("\\n", "\n").replace("\\\"", "\"");
    }

    private static String requireEnv(String key) {
        String v = System.getenv(key);
        if (isBlank(v)) throw new IllegalStateException("Missing environment variable: " + key);
        return v;
    }

    private static String getenvOr(String key, String def) {
        String v = System.getenv(key);
        return isBlank(v) ? def : v;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}