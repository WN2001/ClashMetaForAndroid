# CMFA 美化版自动同步与发布

`Upstream Sync` 每天北京时间 10:17 检查上游 `MetaCubeX/ClashMetaForAndroid` 的 main。
GitHub 定时任务可能延迟，随时可以在 Actions 中手动运行。

- 无冲突：正常合并并推送到本仓库 main，保留双方提交历史。
- 有冲突：main 保持原状，为上游原始提交创建草稿 PR，不提交冲突标记。
  已存在的冲突 PR 及人工修复提交保持不变，不强制覆盖分支。
- 每次检查都会查询本仓库 main 的构建记录。没有成功或运行中的构建时，
  显式触发本仓库的 `Build Pre-Release`，补偿漏发、失败或取消的任务。
  同步成功不代表 APK 已发布，实际结果以构建工作流为准。
- 同步与发布分别限制并发。发布前再次检查 main，旧提交的重跑只保留 Artifacts。

## 签名和下载

继续使用仓库已有的 `release.keystore` 和三个 Actions Secrets：
`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。
构建前校验配置，日志不打印签名文件，结束后清理临时签名配置。
自动任务使用内置 `GITHUB_TOKEN`，无需额外 PAT；仓库需允许 Actions 创建 PR。

构建验证四个架构及 universal 共五个 APK 的签名，并生成 `SHA256SUMS.txt`。
APKs 先保存为 14 天有效的 Artifacts，再更新 `Prerelease-alpha` Release。
不再先删除整个 Release；上传成功后才移动滚动 tag。
不同版本的旧附件继续保留，同名附件会替换，因此上传过程并非原子操作。
以 Release 中的源码 SHA、构建记录和校验清单识别当前版本。

## 排错与验证

- 没有新上游提交但 APK 未更新：手动运行 `Upstream Sync`，它也会检查并补发。
- 需要重新编译已成功发布的同一提交：手动运行 `Build Pre-Release`。
- 冲突 PR：在 `upstream-sync` 分支合并 main、解决冲突后再合入 main。
  若旧分支存在人工提交但 PR 已关闭，普通推送会失败；先检查并处理该分支。
- `Check Automation` 在 PR 中运行真实临时 Git 仓库测试，GitHub 请求被模拟。
  本地命令：`python3 -B -m unittest discover -s .github/scripts -p 'test_*.py' -v`。

所有 GitHub CLI 命令都显式指定本仓库，避免 fork 自动解析到上游造成 403。
