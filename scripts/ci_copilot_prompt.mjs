/* eslint-disable no-console */
import fs from 'node:fs';
import path from 'node:path';

const {
    GITHUB_TOKEN,
    API_URL = 'https://api.github.com',
    SERVER_URL = 'https://github.com',
    RUN_ID,
    REPO,
    JOB_ID,
    JOB_NAME,
    WORKFLOW_NAME,
} = process.env;

if (!RUN_ID || !REPO) {
    console.error('Missing RUN_ID or REPO in environment.');
    process.exit(0);
}

const headers = {
    Authorization: `Bearer ${GITHUB_TOKEN}`,
    Accept: 'application/vnd.github+json',
    'Content-Type': 'application/json',
};

async function ghGet(url) {
    const res = await fetch(url, { headers });
    if (!res.ok) throw new Error(`GitHub API GET ${url} failed: ${res.status} ${res.statusText}`);
    return res.json();
}

async function ghPost(url, body) {
    const res = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
    });
    if (!res.ok) {
        const txt = await res.text().catch(() => '');
        throw new Error(`GitHub API POST ${url} failed: ${res.status} ${res.statusText} ${txt}`);
    }
    return res.json();
}

function readTextSafe(p, maxChars = 120_000) {
    if (!fs.existsSync(p)) return '';
    let text = fs.readFileSync(p, 'utf8');
    if (text.length > maxChars) {
        text = text.slice(-maxChars);
        text = `...[truncated to last ${maxChars} chars]...\n` + text;
    }
    return text;
}

function readCombinedLogs() {
    const combined = readTextSafe(path.join(process.cwd(), 'logs', 'combined.txt'));
    return combined || 'No combined logs were captured.';
}

function extractErrorHighlights(text, maxLines = 200) {
    if (!text) return '(no highlights)';
    const lines = text.split(/\r?\n/);
    const re = /\b(error|failed|failure|exception|traceback|no classdef|classnotfound|assertion(error)?|segmentation fault|build failed|npm ERR!|yarn ERR!|gradle|maven|test failed|cannot find symbol|undefined reference|stack trace)\b/i;
    const hit = [];
    for (const ln of lines) {
        if (re.test(ln)) hit.push(ln);
        if (hit.length >= maxLines) break;
    }
    return hit.length ? hit.join('\n') : '(no highlights matched common failure patterns)';
}

function mkRunLink(run) {
    return run?.html_url ?? `${SERVER_URL}/${REPO}/actions/runs/${RUN_ID}`;
}

function summarizeFailedSteps(jobsData) {
    const summaries = [];
    for (const job of jobsData.jobs ?? []) {
        if (job.conclusion && job.conclusion !== 'success') {
            const failedSteps = (job.steps ?? []).filter(s => s.conclusion && s.conclusion !== 'success');
            const stepNames = failedSteps.map(s => `- ${s.name} (conclusion: ${s.conclusion})`).join('\n');
            summaries.push(
                `Job: ${job.name}\nConclusion: ${job.conclusion}\nStarted: ${job.started_at}\nCompleted: ${job.completed_at}\nFailed steps:\n${stepNames || '(none listed)'}`
            );
        }
    }
    return summaries.join('\n\n');
}

async function getPrNumberFromRun(run) {
    const prs = run?.pull_requests ?? [];
    if (prs.length > 0 && prs[0]?.number) return prs[0].number;
    return null;
}

async function createIssue(title, body) {
    const url = `${API_URL}/repos/${REPO}/issues`;
    return ghPost(url, {
        title,
        body,
        labels: ['ci-failure', 'copilot-analysis-request'],
    });
}

async function commentOnPr(prNumber, body) {
    const url = `${API_URL}/repos/${REPO}/issues/${prNumber}/comments`;
    return ghPost(url, { body });
}

function buildCopilotPromptBody({ run, jobsSummary, combinedLogs, errorHighlights }) {
    const ctx = [
        `Repository: ${REPO}`,
        `Workflow: ${WORKFLOW_NAME}`,
        `Job: ${JOB_NAME}`,
        `Job ID: ${JOB_ID}`,
        `Run ID: ${RUN_ID}`,
        `Run URL: ${mkRunLink(run)}`,
        `Event: ${run?.event}`,
        `Head branch: ${run?.head_branch}`,
        `Commit SHA: ${run?.head_sha}`,
        `Run conclusion: ${run?.conclusion}`,
    ].join('\n');

    const instruction = `
Copilot，请基于下面的上下文与日志进行深入分析，并在本线程直接给出：
1) 最可能的1~3个根因（按置信度排序，引用关键日志行/文件路径/命令输出）
2) 最小修复方案（尽量小的改动即可让 CI 通过，给出具体命令或代码修改示意）
3) 如需继续排查，给出下一步验证步骤（具体到命令/文件/应关注的日志关键字）
若属于依赖/缓存/权限/并发/超时/环境差异/测试不确定性等常见问题，请直指要害并给出相应修复模板。`;

    const combinedLogsFenced = [
        '--- BEGIN LOG TAIL (combined) ---',
        '```txt',
        combinedLogs,
        '```',
        '--- END LOG TAIL (combined) ---',
    ].join('\n');

    const MAX = 60_000;
    let body = `⚠️ CI 失败：已为 GitHub Copilot 准备上下文、错误高亮信息与日志

- Run: ${mkRunLink(run)}
- Workflow: ${WORKFLOW_NAME} · Job: ${JOB_NAME}

在此评论下点击“Ask Copilot/请求 Copilot”（或将对话分配给 Copilot），即可让 Copilot 基于以下上下文输出“根因 + 最小修复 + 验证步骤”。

上下文：
\`\`\`
${ctx}
\`\`\`

失败的 jobs/steps 摘要：
\`\`\`
${jobsSummary || '(no summary)'}
\`\`\`

Error Highlights（从日志中提取的高信号行）：
\`\`\`txt
${errorHighlights}
\`\`\`

推荐给 Copilot 的提示词（点击“Ask Copilot”后它会参考本段并直接作答）：
\`\`\`
${instruction.trim()}
\`\`\`

${combinedLogsFenced}
`;

    if (body.length > MAX) {
        body = body.slice(0, MAX) + '\n\n…(内容过长已截断)…';
    }
    return body;
}

async function main() {
    try {
        const run = await ghGet(`${API_URL}/repos/${REPO}/actions/runs/${RUN_ID}`);
        const jobs = await ghGet(`${API_URL}/repos/${REPO}/actions/runs/${RUN_ID}/jobs?per_page=100`);

        const combinedLogs = readCombinedLogs();
        const errorHighlights = extractErrorHighlights(combinedLogs);
        const jobsSummary = summarizeFailedSteps(jobs);

        const commentBody = buildCopilotPromptBody({ run, jobsSummary, combinedLogs, errorHighlights });
        const prNumber = await getPrNumberFromRun(run);

        if (prNumber) {
            await commentOnPr(prNumber, commentBody);
            console.log(`Posted Copilot-ready prompt to PR #${prNumber}`);
        } else {
            const title = `CI failed: Copilot-ready context for run ${RUN_ID} (${WORKFLOW_NAME} · ${JOB_NAME})`;
            const issue = await createIssue(title, commentBody);
            console.log(`Created issue #${issue.number} with Copilot-ready prompt`);
        }
    } catch (e) {
        console.error('Failed to post Copilot-ready prompt:', e);
        process.exit(0); // 不让该 job 失败，避免噪音
    }
}

await main();