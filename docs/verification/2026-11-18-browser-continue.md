# “继续登录”按钮无反馈与浏览器启动兼容性

## 问题与范围

用户反馈 `1.27-eink.3` 点击“在浏览器继续登录”没有反应。
源码确认未配置 Reddit client ID、未识别到浏览器或启动失败时，只更新状态文字并返回；
这些路径缺少明显反馈。尚未收到设备上的提示文字和浏览器名称，不能确定其设备触发了哪一条。

版本 `1.27-eink.4` / 11904 继续使用 `feat/eink-reading` 分支，修改以下行为：

- 未配置 client ID 时弹出填写框，保存到原有配置后立即继续登录；空输入提示错误并保留弹窗。
- HTTP、HTTPS 网页入口使用完整查询结果，并显式指定浏览器 Activity，不依赖 DEFAULT 类别。
- 补充 Android 浏览器类别查询与可见性声明，兼容通过类别识别的系统浏览器。
- 默认浏览器启动受拒绝或失效时，尝试下一款；保留 Tor 仅使用 Tor Browser 的限制。
- 增加“选择其他浏览器”；所有浏览器均无法启动或未安装时弹出明确提示。
- 使用普通浏览器 ACTION_VIEW，不携带 Custom Tabs 参数；登录说明页面支持滚动。

授权 URI、随机 state、回跳校验及账号令牌交换沿用 `1.27-eink.3`。
未添加私人 client ID、Google 凭据或上游客户端的 ID。

## 回归与旧版对照

BrowserOAuthTest 从 12 项增加至 20 项。首次定向运行全部 20 项通过，
PMD 通过；Checkstyle 因新增生产代码的四处行长度超限失败，随后已断行修正。
首次全量又发现一处链式调用缩进导致行长度超限，调整缩进后完成最终检查。

新增回归覆盖 client ID 弹窗与保存、空输入、无浏览器提示、缺少 DEFAULT 的网页入口、
浏览器类别识别、系统拒绝后的备用浏览器、全部启动失败的提示及手动选择。

另外临时将 OAuthLoginActivity 与 OAuthBrowser 恢复为 `e4a0f84` 的旧实现，
保留新增测试，运行以下三项对照：

```bash
bash gradlew testDebugUnitTest --tests '*BrowserOAuthTest.missingClientIdShowsInputAndSavingStartsBrowser' --tests '*BrowserOAuthTest.browserRoleProvidesPackageFallback' --tests '*BrowserOAuthTest.browserWithoutDefaultCategoryUsesExplicitActivity'
```

三项均失败，构建退出码为 1；随后恢复修复代码。
日志为 `/tmp/redreader-browser-before-fix.log`。该对照证明旧实现不满足新增契约，不能代替实机诊断。

## 全量检查与 APK

最终命令：

```bash
env JAVA_HOME=/tmp/hydrogen-build-tools/jdk-21.0.13+9 GRADLE_USER_HOME=/tmp/hydrogen-gradle-cache ANDROID_HOME=/tmp/hydrogen-android-sdk ANDROID_USER_HOME=/tmp/hydrogen-build-android-home bash gradlew pmd checkstyle lint test assembleRelease --continue --no-daemon --console=plain --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' -I /tmp/hydrogen-repositories.init.gradle -I /tmp/redreader-tests.init.gradle
```

`BUILD SUCCESSFUL in 1m 52s`，退出码 0，日志 `/tmp/redreader-browser-continue-final-v2.log`。
Gradle 按输入复用已经完成的任务；核对 XML 报告后确认：

- 24 份单元测试报告，共 150 项，失败、错误、跳过均为 0；浏览器授权相关为 20 项。
- PMD、Checkstyle 无违规，Lint 无错误或警告，Release 构建通过。
- 通过 `zipalign -c -P 16 4` 与 `apksigner verify --verbose --print-certs`。
- v1、v2、v3 签名有效，证书 SHA-256 与 `eink-v1.27.3` 一致。
- APK 的 `assets/eink-reading.js` 与源码一致，未包含 `assets/reddit_auth.txt`。

提交后重新执行 `:extractReleaseVersionControlInfo --rerun :assembleRelease`，
交付前核对 APK 版本控制元数据与最终源码提交一致，再次签名并校验制品。
发布标签 `eink-v1.27.4`，文件 `RedReader_eink_1.27.4.apk`。
下载及制品 SHA-256 见 [GitHub Release](https://github.com/mofanke/RedReader/releases/tag/eink-v1.27.4)。

## 未验证范围

浏览器包信息、启动拒绝和授权回跳由 Robolectric 模拟；未输入真实 Google 账号，
未执行 Reddit 令牌交换，未在 B7 Pro 上验证浏览器启动与系统跳转。
如果用户没有自己的 Reddit Installed app client ID，仍需要先创建或配置该 ID；
Google 账号本身不能替代 Reddit 应用配置。
