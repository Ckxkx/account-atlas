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

## 提交信息

一行说清改了什么，发版说明直接用它，例如：

- `卡片排序：拖动不再抖动，松手后稳定落位`
- `安卓：弹窗打开更顺滑`

以 `CI:`、`docs:`、`README` 开头的提交不会写进发版说明。
