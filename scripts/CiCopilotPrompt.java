/* Java 21 script to post a Copilot-ready comment for failed CI runs.
 * Highlights-only mode:
 * - Fetch the failed run and jobs metadata via `gh api`.
 * - Read logs/combined.txt built by the workflow step.
 * - Extract the first N lines that match common failure patterns (Error Highlights).
 * - Post a PR comment (or create an Issue) with:
 *   - Run URL, Failed job URL
 *   - Failed jobs/steps summary (names + conclusions)
 *   - Error Highlights (only, no full logs)
 *   - A concise prompt to ask Copilot for root cause, minimal fix, and next steps
 *
 * Requirements:
 * - JDK 21
 * - GitHub-hosted runner with `gh` CLI available (default on ubuntu-latest)
 * - GH_TOKEN/GITHUB_TOKEN provided by Actions
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CiCopilotPrompt {
    private static final int BODY_MAX_CHARS = 60_000;
    private static final int LOG_MAX_CHARS = 120_000;
    private static final int HIGHLIGHT_MAX_LINES = 200;

    public static void main(String[] args) {
        try {
            String repo = requireEnv("REPO");
            String runId = requireEnv("RUN_ID");
            String workflowName = getenvOr("WORKFLOW_NAME", "(unknown)");
            String serverUrl = getenvOr("SERVER_URL", "https://github.com");

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

            // Read combined logs and compute highlights
            String combinedLogs = readCombinedLogs();
            String errorHighlights = extractErrorHighlights(combinedLogs, HIGHLIGHT_MAX_LINES);

            // Context and instruction (English-only)
            String ctx = String.join("\n", List.of(
                    "Repository: " + repo,
                    "Workflow: " + workflowName,
                    "Run ID: " + runId,
                    "Run URL: " + runUrl,
                    "Event: " + event,
                    "Head branch: " + headBranch,
                    "Commit SHA: " + headSha,
                    "Run conclusion: " + runConclusion
            ));

            String instruction = """
            Copilot, based on the context and Error Highlights below, please provide:
            1) The top 1–3 likely root causes (ranked by confidence; reference key lines/files/commands).
            2) The minimal fix to make CI pass (concrete commands or code changes where possible).
            3) Next steps to verify or further debug (specific commands/files/log keywords).
            If this looks like a dependency/cache/permission/concurrency/timeout/environment/test flakiness issue, call it out and provide a proven template fix.
            """.trim();

            // Build body (no full logs, only highlights)
            StringBuilder body = new StringBuilder();
            body.append("⚠️ CI failed: links and Error Highlights (no full logs embedded)\n\n");
            body.append("- Run: ").append(runUrl).append("\n");
            body.append("- Failed job: ").append(jobHtmlUrl).append("\n");
            body.append("- Tip: open the \"Failed job\" link and expand the failed step to view the complete logs and stack traces.\n\n");

            body.append("Context:\n```\n").append(ctx).append("\n```\n\n");
            body.append("Failed jobs/steps summary (names and conclusions only):\n```\n")
                    .append(jobsSummary).append("\n```\n\n");

            body.append("Error Highlights (first ").append(HIGHLIGHT_MAX_LINES).append(" matching lines):\n```txt\n")
                    .append(errorHighlights).append("\n```\n\n");

            body.append("Ask Copilot with this prompt:\n```\n").append(instruction).append("\n```\n");

            String finalBody = body.toString();
            if (finalBody.length() > BODY_MAX_CHARS) {
                finalBody = finalBody.substring(0, BODY_MAX_CHARS) + "\n\n…(truncated)…";
            }

            // Post comment or create issue
            Path tmp = Files.createTempFile("copilot-comment-highlights-", ".md");
            Files.writeString(tmp, finalBody, StandardCharsets.UTF_8);

            if (prNumber > 0) {
                runProcess(new String[]{
                        "gh", "pr", "comment",
                        String.valueOf(prNumber),
                        "--repo", repo,
                        "--body-file", tmp.toString()
                });
                System.out.println("Posted highlights-only Copilot prompt to PR #" + prNumber);
            } else {
                String title = "CI failed: highlights-only context for run " + runId + " (" + workflowName + ")";
                runProcess(new String[]{
                        "gh", "issue", "create",
                        "--repo", repo,
                        "-t", title,
                        "--body-file", tmp.toString(),
                        "-l", "ci-failure",
                        "-l", "copilot-analysis-request"
                });
                System.out.println("Created issue with highlights-only Copilot prompt");
            }

        } catch (Exception e) {
            // Do not fail the analyzer job
            System.err.println("Failed to post Copilot highlights-only prompt: " + e);
            System.exit(0);
        }
    }

    // Helpers

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
        // Common failure patterns across ecosystems
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