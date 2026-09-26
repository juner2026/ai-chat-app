# AI Chat · 移动端壳（WebView → APK）

把你的单文件网页 `index.html` 装进原生 WebView 壳，GitHub Actions 自动构建 APK。

## 准备：把页面放进去

目标路径：`app/src/main/assets/index.html`

GitHub 手机网页/App → 进到 `app/src/main/assets/` → **Add file → Upload files** → 选你的 html，**上传前把文件名改成 `index.html`**。

## 构建 / 拿 APK

提交后 Actions 自动跑：仓库 → **Actions** 看进度；仓库 → **Releases** 下载 `ai-chat.apk`。

首次约 2~4 分钟。

## 数据搬家

网页版和 APK 版存储是两套：旧页面「设置 → 导出数据」拿 json；装好 APK 后「设置 → 导入数据」选它。
