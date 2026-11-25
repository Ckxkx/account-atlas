# 发版规范

## 版本号

用 `主版本.次版本`，例如 `2.0`、`2.1`、`2.2`。

- **次版本 +1**（2.1 → 2.2）：每一批小改进、修复、性能优化，攒好后发一次。
- **主版本 +1**（2.x → 3.0）：大改版，例如数据格式变化、界面整体重做。
- **补丁 `2.1.1`**：只在已发布版本出了必须马上修的问题时用。

三端（安卓、iPhone、网页）共用同一个版本号。安卓 `versionName` 和 iOS `CFBundleShortVersionString` 就是这个版本号；`versionCode` / `CFBundleVersion` 由提交数自动生成，只增不减，覆盖安装不会出问题。

## 一个版本 = 一个 Release

- 标签：`v2.1`，标题：`账号星图 2.1`
- 附件固定两个：`Atlas.apk`、`Atlas.ipa`（文件名不带版本号，`releases/latest/download/Atlas.apk` 这类链接永远有效）
- 说明：自动列出和上一个版本之间的提交，外加安装说明
- 不再按每次推送发 Release，也不再有 `apk` / `ipa` / `shots` 分支

## 分支

| 分支 | 用途 |
|---|---|
| `main` | 线上版本。只通过合并 `dev`（Pull Request）更新，网页版从这里自动部署，发版标签打在这里。 |
| `dev` | 日常开发。所有改动先推到这里，CI 自动构建 APK / IPA（Actions → 运行记录 → Artifacts）用来真机测试，不影响线上网页和 Release。 |
| `gh-pages` | 网页版的发布产物，由 CI 写入，不要手动改。 |

较大的功能可以从 `dev` 拉 `feat/xxx` 分支，做完合回 `dev`。

## 日常流程

1. 在 `dev` 上改，推送后从 Actions 下载测试包装到手机上试。
2. 试好后开 Pull Request：`dev` → `main`，CI 通过后合并（网页版随之上线）。
3. 在 `main` 上打下一个版本标签发版（见下）。

## 怎么发版

`dev` 合并进 `main` 以后，在 `main` 的最新提交上二选一：

```bash
git tag v2.2 && git push origin v2.2
```

或在 GitHub → Actions → Release → Run workflow，填 `2.2`。

`release.yml` 会用这个提交同时构建 APK 和 IPA，生成一个 Release 并标为最新。

## App 内的「发现新版本」（version.json）

iOS / 安卓每天最多一次读取 `https://huichen.dev/account-atlas/version.json`，比已安装的版本新就弹窗（去下载 / 跳过此版本 / 以后再说）。只有读到了文件才算当天检查过，断网 / 404 / 格式不对时下次启动会再试。网页版不弹，它随 `sw.js` 自动更新。

设置 → **软件更新**（iOS / 安卓）：打开就检查，显示当前版本、最新版本的更新日志和「去下载」（打开蓝奏云并复制密码），已跳过的版本在这里照样显示；「自动检查更新」开关也在这一页。

- **版本号不用手改**：`release.yml` 发完 Release 后，会把 gh-pages 上的 `version.json` 改成刚发布的版本；`web.yml` 每次部署网页也会按「GitHub 上最新的 Release」重新生成。所以只有 APK / IPA 都已发布的版本才会弹窗。
- **更新说明**：发版前可以在仓库根目录的 `version.json` 里把 `version` 改成要发的版本、写好 `notes`（3–5 条，每条一句话）；版本号对得上就用它，对不上就自动取 Release 说明里「更新内容」的前 8 条。
- `url` / `password` / `github`：蓝奏云链接、密码、GitHub Releases，直接改仓库里的 `version.json`，下次部署网页生效。
- 生成逻辑在 `tools/version-json.mjs`。CI 测试包（安卓版本号 `dev`、iOS `0.0`）不会自动检查；「软件更新」页把当前版本显示为「测试版」，并照样显示最新版本，方便测试。

## 提交信息

一行说清改了什么，发版说明直接用它，例如：

- `卡片排序：拖动不再抖动，松手后稳定落位`
- `安卓：弹窗打开更顺滑`

以 `CI:`、`docs:`、`README` 开头的提交不会写进发版说明。
