"""Synchronize CMFA and recover missed builds using only git, gh and Python."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time


UPSTREAM = "https://github.com/MetaCubeX/ClashMetaForAndroid.git"
WORKFLOW = "build-pre-release.yaml"
SYNC_BRANCH = "upstream-sync"


def command(*args, check=True):
    result = subprocess.run(args, text=True, encoding="utf-8", capture_output=True)
    if check and result.returncode:
        raise RuntimeError(f"{' '.join(args)}\n{result.stderr.strip()}")
    return result


def retry(*args):
    """Retry read-only network requests; never retry a merge or create request."""
    for attempt in range(3):
        result = command(*args, check=False)
        if result.returncode == 0:
            return result.stdout.strip()
        if attempt < 2:
            time.sleep(3 * (attempt + 1))
    raise RuntimeError(f"{' '.join(args)}\n{result.stderr.strip()}")


def report(message):
    print(message)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(message + "\n\n")


def conflict_pr(repository, conflicts):
    existing = json.loads(retry(
        "gh", "pr", "list", "--repo", repository, "--base", "main",
        "--head", SYNC_BRANCH, "--state", "open", "--limit", "1",
        "--json", "number,url",
    ))
    if existing:
        # 保留维护者正在解决冲突的提交，不 force-push、不重复创建 PR。
        report(f"上游存在冲突，请继续处理 {existing[0]['url']}；已保留该分支。")
        return

    # 分支只包含真实上游提交；绝不将冲突标记 git add 后提交。
    # 若旧分支有人工提交，非快进 push 会安全失败，避免覆盖。
    command("git", "push", "origin", f"refs/remotes/upstream/main:refs/heads/{SYNC_BRANCH}")
    body = (
        "上游更新与本仓库的美化改动存在冲突，main 保持不变。\n\n"
        + "\n".join(f"- `{path}`" for path in conflicts)
        + "\n\n此分支保留上游原始提交，没有提交冲突标记。"
        "请在此分支合并 main 并解决冲突，再将 PR 标为可合并。"
        "合入 main 后会自动编译；每日同步也会补偿漏掉的构建。\n"
    )
    with tempfile.TemporaryDirectory() as directory:
        body_file = Path(directory) / "conflicts.md"
        body_file.write_text(body, encoding="utf-8")
        # 不依赖仓库预先存在 label，也不自动重试非幂等的创建请求。
        url = command(
            "gh", "pr", "create", "--repo", repository, "--base", "main",
            "--head", SYNC_BRANCH, "--draft", "--title", "上游同步：保留美化并解决冲突",
            "--body-file", str(body_file),
        ).stdout.strip()
    report(f"上游合并需要人工处理：{url}")


def sync(repository):
    if command("git", "branch", "--show-current").stdout.strip() != "main":
        raise RuntimeError("同步只允许在 main 分支运行。")
    if command("git", "status", "--porcelain").stdout.strip():
        raise RuntimeError("工作区有未提交改动，停止同步。")
    command("git", "config", "user.name", "github-actions[bot]")
    command("git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    remote = command("git", "remote", "get-url", "upstream", check=False)
    command("git", "remote", "set-url" if remote.returncode == 0 else "add", "upstream", UPSTREAM)
    # 不使用 --depth：浅历史可能使 merge-base 无法发现共同祖先。
    retry("git", "fetch", "--no-tags", "upstream", "+refs/heads/main:refs/remotes/upstream/main")
    ancestor = command("git", "merge-base", "--is-ancestor", "upstream/main", "HEAD", check=False)
    if ancestor.returncode == 0:
        report("上游已经同步，继续检查 APK 构建是否遗漏。")
        return
    if ancestor.returncode != 1:
        raise RuntimeError(ancestor.stderr.strip())
    merged = command("git", "merge", "--no-edit", "upstream/main", check=False)
    if merged.returncode:
        conflicts = command("git", "diff", "--name-only", "--diff-filter=U").stdout.splitlines()
        if not conflicts:
            raise RuntimeError(f"合并失败，且不是文件冲突：{merged.stderr.strip()}")
        command("git", "merge", "--abort")
        conflict_pr(repository, conflicts)
        return
    # 并发人工推送会导致普通 push 被拒绝；下次从最新 main 重试。
    command("git", "push", "origin", "HEAD:refs/heads/main")
    report(f"已同步上游到 main：{command('git', 'rev-parse', 'HEAD').stdout.strip()}")


def ensure_build(repository):
    # 重新读取远端 main，包含同步后或同时由维护者推送的提交。
    sha = retry("gh", "api", f"repos/{repository}/commits/main", "--jq", ".sha")
    runs = json.loads(retry(
        "gh", "run", "list", "--repo", repository, "--workflow", WORKFLOW,
        "--branch", "main", "--commit", sha, "--limit", "20",
        "--json", "status,conclusion,url",
    ))
    for run in runs:
        if run["status"] != "completed" or run["conclusion"] == "success":
            report(f"main 已有成功或尚未结束的构建，跳过重复触发：{run['url']}")
            return
    # 不重试 dispatch：超时也可能已创建运行，下次定时检查会先去重再补偿。
    command("gh", "workflow", "run", WORKFLOW, "--repo", repository, "--ref", "main")
    report(f"已为 main ({sha}) 触发签名构建；构建结果请查看 Build Pre-Release。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["sync", "ensure-build"])
    args = parser.parse_args()
    repository = os.environ["GITHUB_REPOSITORY"]
    if args.action == "sync":
        sync(repository)
    else:
        ensure_build(repository)


if __name__ == "__main__":
    main()
