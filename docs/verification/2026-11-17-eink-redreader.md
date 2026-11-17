# RedReader 墨水屏版验证

## 来源与实现

基于上游 `QuantumBadger/RedReader` 的 `3a1ab4f32a763155c49fa233cd1314173abd22d9`。
代码位于本 fork 的 `feat/eink-reading` 分支，版本 `1.27-eink.1`，versionCode 11901。

- `activities/BaseActivity.java` 消费实体键完整序列，直接调用当前页面的屏幕按钮。
- `eink/EinkPager.java` 与 `eink/EinkRecyclerView.java` 按实际阅读区域保留文字重叠、图片整块落页、末页留白和正反向起点。公共页面基类接入屏幕箭头，双栏按最近触摸的列表翻页。
- `views/InlinePreviewHolderView.java` 将图片限制到当前可读高度，保留原比例和彩色。列表图片加载和回收继续使用上游缓存机制。
- `eink/EinkImageView.java` 与 `eink/ImagePages.java` 使用原下载缓存、区域解码、8 MiB 瓦片缓存和按键分段。末段保持正常起点、等比并顶部对齐，动态图使用静态首帧预览。
- 相册采用原生图片目录，点开后使用同一图片查看器。保存、分享等原有图片操作放入“更多”菜单。
- 内置网页使用 `assets/eink-reading.js`，复用 Hydrogen 墨水屏版的排版与末页边界逻辑。
- 包名与缓存 ContentProvider 使用独立 applicationId，支持与原版并存。

使用说明与 Reddit 联网配置见 [墨水屏阅读说明](../eink-reading.md)。

## 本次实际检查

| 检查 | 结果 |
| --- | --- |
| 新增 `EinkPagingTest` | 5 项通过：实体键完整序列、生产 Activity 中按钮与 PageDown 一致性、原生列表文字覆盖及末页返回、图片完整落页、长宽图区域覆盖和短末段 |
| `tests/web_paging_test.py` | Chromium 5 项通过：多个字号的文字完整覆盖、正常末页起点及反向历史、完整长图与比例/色彩、页头和主动回顶、实际可视视口 |
| `pmd` | 0 违规 |
| `checkstyle` | 0 违规 |
| `lint` | 0 错误、0 警告 |
| `test` | 根应用 129 项单元测试通过，0 失败、0 错误、0 跳过 |
| `assembleRelease` | 与上列全量检查同一命令执行，最终 `BUILD SUCCESSFUL in 3m 12s` |
| `git diff --check` | 通过 |

命令在仓库根目录执行：

```bash
env JAVA_HOME=/tmp/hydrogen-build-tools/jdk-21.0.13+9 GRADLE_USER_HOME=/tmp/hydrogen-gradle-cache ANDROID_HOME=/tmp/hydrogen-android-sdk ANDROID_USER_HOME=/tmp/hydrogen-build-android-home bash gradlew pmd checkstyle lint test assembleRelease --continue --no-daemon --console=plain --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' -I /tmp/hydrogen-repositories.init.gradle -I /tmp/redreader-tests.init.gradle
env PAGER_TEST_CHROMIUM=/home/mofanke/.cache/ms-playwright/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell python3 tests/web_paging_test.py
```

使用 JDK 21、上游要求的 Android SDK 36 和 Gradle Wrapper 9.6.1。
本机将 Kotlin 编译设为进程内执行，避免受限目录下的编译 daemon 缓存冲突。
首次 Android 测试受 Robolectric 用户目录下载锁限制；随后将测试 JVM 的用户目录和 SDK 缓存放到 `/tmp` 中。
测试使用 Robolectric 4.16 的 Android 35/36 运行库；模拟 Android 视图并调用生产 Activity，未在真实设备中注入按键。

原生图片回归按实际窗口大小计算边界，避免测试中的固定尺寸与 Android 窗口重新排版冲突。
首次检查发现的异常原因传递、相册 Activity 获取方式和文案资源规范问题均修正；最终检查重新执行并通过。

## 制品核验

发布前从提交后的源码重新生成版本控制元数据并打包：

```bash
bash gradlew :extractReleaseVersionControlInfo --rerun :assembleRelease
```

APK 名为 `RedReader_eink_1.27.1.apk`，包名 `org.mofanke.redreader.eink`，Android API 21 及以上。
使用仓库外的独立密钥签名，`apksigner verify` 的 v1、v2、v3 签名有效，16 KiB ZIP 对齐检查通过。
APK 中的网页脚本与本次源码逐字节一致，不含私人的 `reddit_auth.txt`。

制品、SHA-256 和下载地址记录在 [eink-v1.27.1 发布页](https://github.com/mofanke/RedReader/releases/tag/eink-v1.27.1)，发布后核对 GitHub 制品 digest 与本地文件。

## 未验证范围

本次没有配置私人 Reddit client ID，因此未验证 Reddit 联网、账号登录与实时内容加载。
需按上游要求在应用“设置 → 网络 → Reddit client ID override”填写自己的 Installed app ID。

B7 Pro 上的实体按键映射、正文与图片实际分页、清晰度、加载速度、实际内存占用、刷新和残影仍待实机验证。
