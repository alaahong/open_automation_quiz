/* Java 21 Hugging Face Inference-based CI failure analyzer (highlights-only).
 * - Extracts high-signal error lines (first N matches) from combined logs.
 * - Calls Hugging Face Inference API (text-generation) to produce:
 *   root causes, minimal fix, and next steps.
 * - Posts a PR comment (or creates an Issue) with links, summary, highlights, and analysis.
 *
 * Requirements:
 * - JDK 21
 * - GitHub-hosted runner with `gh` CLI available (ubuntu-latest)
 * - Secrets: HF_API_TOKEN
 * - Optional vars: HF_MODEL (default: meta-llama/Llama-3.2-3B-Instruct),
 *                  ANALYZER_MAX_HIGHLIGHTS (default 200),
 *                  HF_MAX_NEW_TOKENS (default 800)
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

    public static void main(String[] args) {
        try {
            String repo = requireEnv("REPO");
            String runId = requireEnv("RUN_ID");
            String workflowName = getenvOr("WORKFLOW_NAME", "(unknown)");
            String serverUrl = getenvOr("SERVER_URL", "https://github.com");
            int highlightMax = parseIntSafe(getenvOr("ANALYZER_MAX_HIGHLIGHTS", "200"), 200);
            int hfMaxNew = parseIntSafe(getenvOr("HF_MAX_NEW_TOKENS", "800"), 800);

            // Hugging Face config
            String hfToken = getenvOr("HF_API_TOKEN", "");
            String hfModel = getenvOr("HF_MODEL", "deepseek-ai/deepseek-coder-6.7b-instruct");
//            String hfModel = getenvOr("HF_MODEL", "meta-llama/Llama-3.2-3B-Instruct");

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

            String context = String.join("\n", List.of(
                    "Repository: " + repo,
                    "Workflow: " + workflowName,
                    "Run ID: " + runId,
                    "Run URL: " + runUrl,
                    "Event: " + event,
                    "Head branch: " + headBranch,
                    "Commit SHA: " + headSha,
                    "Run conclusion: " + runConclusion
            ));

            String prompt = buildPrompt(context, jobsSummary, errorHighlights);

            String analysis;
            if (isBlank(hfToken)) {
                analysis = "Hugging Face Inference is not configured (HF_API_TOKEN is missing). " +
                        "Please add HF_API_TOKEN in repository Secrets and re-run this workflow.";
            } else {
                analysis = analyzeWithHuggingFace(hfToken, hfModel, prompt, hfMaxNew);
            }

            // Build comment body
            StringBuilder body = new StringBuilder();
            body.append("🤖 CI failure: Hugging Face Inference analysis\n\n");
            body.append("- Run: ").append(runUrl).append("\n");
            body.append("- Failed job: ").append(jobHtmlUrl).append("\n\n");

            body.append("Context:\n```\n").append(context).append("\n```\n\n");
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

    // ----------------- HF Inference caller -----------------

    private static String analyzeWithHuggingFace(String token, String model, String prompt, int maxNewTokens) {
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

            // Retry on 503 (model loading) with backoff using "estimated_time" if available
            int maxAttempts = 5;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    String txt = resp.body();
                    String gen = extractJsonField(txt, "generated_text");
                    if (isBlank(gen)) gen = txt; // fallback raw
                    return "```\n" + gen.trim() + "\n```";
                } else if (status == 503) {
                    int sleepSec = parseEstimatedTimeSeconds(resp.body(), 15);
                    sleepSec = Math.min(sleepSec + (attempt - 1) * 5, 60); // cap wait
                    System.out.println("Model loading (503). Waiting " + sleepSec + "s and retrying (" + attempt + "/" + maxAttempts + ")...");
                    Thread.sleep(sleepSec * 1000L);
                    continue;
                } else {
                    return "HF Inference call failed: " + status + " " + resp.body();
                }
            }
            return "HF Inference did not become ready after retries. Please retry the workflow later.";
        } catch (Exception e) {
            return "HF Inference call error: " + e.getMessage();
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
            return Math.max(1, (int)Math.ceil(seconds));
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

    // ----------------- GH helpers -----------------

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

    // ----------------- Utils -----------------

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String extractJsonField(String json, String field) {
        // simplistic: search for "field":"..."
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
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}