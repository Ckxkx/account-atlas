<p align="center"><img src="icon-512.png" width="112" alt="账号星图"></p>
<h1 align="center">账号星图 · Atlas</h1>
<p align="center">这个邮箱注册过什么？那个手机号绑了哪些 App？一眼看清。</p>
<p align="center">网页 · iOS · 安卓 三端可用，iOS 用 SwiftUI、安卓用 Jetpack Compose 原生编写，数据加密同步互通</p>
<p align="center">
  <a href="https://huichen.dev/account-atlas/">在线使用</a> ·
  <a href="https://github.com/Ckxkx/account-atlas/releases/latest">下载 App（安卓 APK · iOS IPA）</a>
</p>

<p align="center">
  <img src="docs/wallet.gif" width="30%">
  <img src="docs/map.gif" width="30%">
  <img src="docs/theme.gif" width="30%">
</p>

## 它能做什么
- **卡包**：每个邮箱、手机号都是一张 Wallet 风格的卡，左右翻看，手机号做成 SIM 卡
- **点选添加**：内置 100+ 常用网站和 App（官方图标），多选后再选用哪个邮箱或手机号，同一网站可记多个账号名
- **关系图**：左边身份、右边 App，自动连线，点卡片只看它连着的
- **深色 / 浅色**：从按钮扩散开的圆形切换
- **隐私模式**：号码和账号名一键打码，截图分享也安心
- **加密同步（可选）**：数据用你的同步密码在本地 AES-GCM 加密后，存进你自己的 Gitee（国内直连）或 GitHub 私密 Gist
- 拖动排序、撤销删除、JSON 导入导出、离线可用

## 下载
最新版在 [Releases](https://github.com/Ckxkx/account-atlas/releases/latest)，同一个版本里有两个文件：
- **Atlas.apk**：安卓（Android 8.0+）原生版，下载后直接安装；以后的版本签名相同，覆盖安装，数据保留
- **Atlas.ipa**：iOS 原生版（未签名），用 SideStore / 爱思助手 / Sideloadly 等自签后安装

## 不想自签？用网页版装到桌面（PWA）
网页版本身就能“安装”成 App：有桌面图标，全屏打开没有地址栏，断网也能用。

**iPhone / iPad**
1. 用 **Safari** 打开 https://huichen.dev/account-atlas/
2. 点「分享」按钮（方框加向上的箭头；iOS 26 先点右下角「⋯」）
3. 往下滑，选「添加到主屏幕」，再点「添加」

**安卓**
1. 用 Chrome 或 Edge 打开同一个网址
2. 右上角「⋮」→「添加到主屏幕」或「安装应用」

数据存在这台设备的浏览器里，换手机前记得用「加密同步」或「导出」备份。

## 隐私
数据默认只存在你自己的设备上，没有任何服务器。**不记录密码**。开启同步时，云端只有加密后的文件。

## 三个版本
| | 网页版 | iOS 原生版 | 安卓原生版 |
|---|---|---|---|
| 技术 | 纯 HTML/CSS/JS，零依赖 | SwiftUI，iOS 17+ | Kotlin + Jetpack Compose，Android 8.0+ |
| 安装 | 打开网址，Safari「添加到主屏幕」 | 从 Releases 下载 IPA，用SideStore / 爱思助手 / Sideloadly 等自签安装 | 从 Releases 下载 APK 直接安装 |
| 代码 | 根目录 `index.html` `app.js` `styles.css` | `native/` | `android/` |

三个版本的数据格式相同，可以通过同步或 JSON 互通。

## 自己运行 / 构建
- 网页版：`python3 -m http.server 8080`，打开 `http://localhost:8080`
- 安卓版：`cd android && ./gradlew assembleRelease`（JDK 17 + Android SDK）；推送 `android/` 的改动后，`.github/workflows/android.yml` 会构建签名 APK 并发布到 Releases。启动图标只有一个源文件 `android/icon-src/icon-1024.png`，换图后重新构建即可
- iOS 版：推送 `native/` 的改动后，GitHub Actions（`.github/workflows/ios.yml`，macOS runner）会用 `swiftc` 编译并发布未签名 IPA 到 Releases

## 请我吃包花生米
觉得好用的话，可以用微信扫码赞赏，随意就好。

<p align="center"><img src="docs/wechat-reward.jpg" width="240" alt="微信赞赏码"></p>

## 许可
[MIT](LICENSE)，随便用，欢迎 PR 和 Issue。
