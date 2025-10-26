/* Java 21 single-file script to post a Copilot-ready comment for failed CI jobs.
 * "Links-only" mode: does NOT embed any log content. It posts:
 * - Run URL
 * - Failed job URL (direct link to job logs)
 * - Failed steps list (names and conclusions only)
 * - Instructions to ask Copilot, recommending pasting key error lines manually
 *
 * Requirements:
 * - GitHub-hosted runner with `gh` CLI available (default on ubuntu-latest)
 * - GH_TOKEN/GITHUB_TOKEN provided by Actions
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class CiCopilotPrompt {
    private static final int BODY_MAX_CHARS = 60_000;

    public static void main(String[] args) {
        try {
            String repo = requireEnv("REPO");
            String runId = requireEnv("RUN_ID");
            String jobId = getenvOr("JOB_ID", "");
            String jobName = getenvOr("JOB_NAME", "(unknown)");
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

            // Find current job html_url and build failed steps summary
            String jobsJson = ghApi("repos/" + repo + "/actions/runs/" + runId + "/jobs?per_page=100");
            String jobHtmlUrl = ghApiJqFromInput(jobsJson,
                    "([.jobs[] | select(.id == " + jobId + ") | .html_url] | first // \"\")");
            if (isBlank(jobHtmlUrl)) {
                // fallback to generic logs page
                jobHtmlUrl = runUrl;
            }

            String jobsSummary = ghApiJqFromInput(jobsJson,
                    "([.jobs[] " +
                            " | select(.conclusion != \"success\" and .conclusion != null) " +
                            " | \"Job: \\(.name)\\n\" " +
                            "+ \"Conclusion: \\(.conclusion)\\n\" " +
                            "+ \"Started: \\(.started_at)\\n\" " +
                            "+ \"Completed: \\(.completed_at)\\n\" " +
                            "+ \"Failed steps:\\n\" " +
                            "+ ( [ (.steps // [])[] " +
                            "| select(.conclusion != \"success\" and .conclusion != null) " +
                            "| \"- \\(.name) (conclusion: \\(.conclusion))\" ] " +
                            " | if length>0 then join(\"\\n\") else \"(none listed)\" end )" +
                            "] | if length>0 then join(\"\\n\\n\") else \"(no summary)\" end)"
            );

            String ctx = String.join("\n", List.of(
                    "Repository: " + repo,
                    "Workflow: " + workflowName,
                    "Job: " + jobName,
                    "Job ID: " + jobId,
                    "Run ID: " + runId,
                    "Run URL: " + runUrl,
                    "Event: " + event,
                    "Head branch: " + headBranch,
                    "Commit SHA: " + headSha,
                    "Run conclusion: " + runConclusion
            ));

            // Since we do not embed logs, instruct maintainers to copy/paste the key error lines
            String instruction = """
            Copilot，请基于上下文信息以及维护者随后粘贴到本线程中的关键错误行，给出：
            1) 最可能的1~3个根因（按置信度排序，引用粘贴的关键日志行/文件路径/命令输出）
            2) 最小修复方案（尽量小的改动即可让 CI 通过，给出具体命令或代码修改示意）
            3) 下一步验证/排查步骤（具体到命令/文件/日志关键字）
            """.trim();

            StringBuilder body = new StringBuilder();
            body.append("⚠️ CI 失败：日志位置与快速定位（不内嵌日志）\n\n");
            body.append("- Run: ").append(runUrl).append("\n");
            body.append("- Failed job: ").append(jobHtmlUrl).append("\n");
            body.append("- 提示：打开 “Failed job” 链接，展开失败的 Step 查看完整日志与错误堆栈。\n\n");

            body.append("上下文：\n```\n").append(ctx).append("\n```\n\n");
            body.append("失败的 jobs/steps 摘要（仅名称与结论，不包含日志）：\n```\n")
                    .append(jobsSummary).append("\n```\n\n");

            body.append("如何让 Copilot 给出根因与修复建议：\n");
            body.append("1) 点击上方 “Failed job” 打开日志页面，复制最具代表性的报错行或堆栈（约 10~30 行）。\n");
            body.append("2) 回到此评论，点击 “Ask Copilot/请求 Copilot”，将错误片段粘贴进对话中并附上以下提示词：\n");
            body.append("```\n").append(instruction).append("\n```\n");

            String finalBody = body.toString();
            if (finalBody.length() > BODY_MAX_CHARS) {
                finalBody = finalBody.substring(0, BODY_MAX_CHARS) + "\n\n…(内容过长已截断)…";
            }

            // Write to temp and post via gh
            Path tmp = Files.createTempFile("copilot-comment-links-only-", ".md");
            Files.writeString(tmp, finalBody, StandardCharsets.UTF_8);

            if (prNumber > 0) {
                runProcess(new String[]{
                        "gh", "pr", "comment",
                        String.valueOf(prNumber),
                        "--repo", repo,
                        "--body-file", tmp.toString()
                });
                System.out.println("Posted links-only Copilot prompt to PR #" + prNumber);
            } else {
                String title = "CI failed: Links-only context for run " + runId + " (" + workflowName + " · " + jobName + ")";
                runProcess(new String[]{
                        "gh", "issue", "create",
                        "--repo", repo,
                        "-t", title,
                        "--body-file", tmp.toString(),
                        "-l", "ci-failure",
                        "-l", "copilot-analysis-request"
                });
                System.out.println("Created issue with links-only Copilot prompt");
            }

        } catch (Exception e) {
            // Do not fail the analyzer job
            System.err.println("Failed to post Copilot links-only prompt: " + e);
            System.exit(0);
        }
    }

    // Helpers

    private static String ghApi(String endpoint) throws IOException, InterruptedException {
        return runProcess(new String[]{"gh", "api", endpoint});
    }

    private static String ghApiJq(String endpoint, String jq) throws IOException, InterruptedException {
        return runProcess(new String[]{"gh", "api", endpoint, "--jq", jq}).trim();
    }

    private static String ghApiJqFromInput(String json, String jq) throws IOException, InterruptedException {
        // gh api can read from stdin using "-"
        ProcessBuilder pb = new ProcessBuilder("gh", "api", "--method", "GET", "--input", "-", "--jq", jq);
        Map<String, String> env = pb.environment();
        if (isBlank(env.get("GH_TOKEN"))) {
            String gt = env.getOrDefault("GITHUB_TOKEN", "");
            if (!isBlank(gt)) env.put("GH_TOKEN", gt);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) throw new IOException("gh api (stdin) failed: " + out);
        return out.trim();
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