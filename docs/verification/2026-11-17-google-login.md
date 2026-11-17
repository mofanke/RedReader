# Google 登录与浏览器授权回跳

## 问题与修复

用户反馈 Google 账号登录失败。源码中的 OAuthLoginActivity 将整个授权页放在内置 WebView 中。
Google 的 OAuth 策略禁止嵌入式浏览器授权；上游的浏览器回退会关闭发起登录的页面，再由通用链接入口处理返回。
这些是本次从源码确认的问题，尚未获取用户设备的具体错误提示。

依据：[Google OAuth 的 disallowed_useragent 说明](https://developers.google.com/identity/protocols/oauth2/native-app#disallowed_useragent)。

本次版本为 `1.27-eink.3` / 11903，修改位于同一 `feat/eink-reading` 分支。

- `OAuthLoginActivity` 使用原生说明页与系统浏览器，在浏览器完成 Google、Apple 或密码登录。
- `OAuthBrowser` 选择实际网页浏览器，将初始授权网址留在浏览器中，并保持浏览器会话。未安装浏览器时给出提示；Tor 模式选择 Tor Browser。
- `OAuthRedirectActivity` 注册精确的 `redreader://rr_oauth_redir` 回跳，回到原授权 Activity，返回已有的 123 / url 账号结果契约。
- `BrowserOAuthSession` 保存一次随机 state 与对应 client ID，校验回调地址、参数、有效期和重复使用。页面重建时保留待完成请求，取消时清除。
- 通用 LinkDispatchActivity 的 redreader 回跳也经过同一入口；不直接将未经校验的外部 URI 交给令牌交换。
- 登录请求的临时状态不参加 Android 云备份或设备迁移。缺少 client ID 时先提示设置，登录页不注入阅读脚本或生成翻页按钮。

Reddit 的令牌交换、账号保存和账号列表更新继续使用原有流程。
回调地址与 Installed app 配置要求保持一致，实际私人 client ID 不加入源码或 APK。

## 已执行回归

新增 BrowserOAuthTest 的 12 项回归通过：

1. 登录使用真实浏览器 Intent，允许回跳，不创建 WebView。
2. 未配置 client ID 时不打开无效授权地址。
3. 有效回跳返回原有账号结果，已使用请求不再有效。
4. 回到原 Activity 时使用 CLEAR_TOP / SINGLE_TOP，保留原结果回调关系。
5. 隐式授权回跳解析到专用入口。
6. 非本次 state 不取消合法待完成请求。
7. 重试生成新 state，拒绝旧回跳。
8. 更换 client ID 或请求过期时拒绝回跳。
9. 校验回调地址及重复参数。
10. 取消授权不返回成功。
11. 登录页重建后保留浏览器中的请求。
12. Tor 模式不选择普通浏览器。

使用 Robolectric 4.16 / Android 35 的生产 Activity、Manifest 和 SharedPreferences。
Chrome/Tor Browser 的包信息由测试模拟，未输入真实 Google 账号或执行实际令牌交换。

```bash
env JAVA_HOME=/tmp/hydrogen-build-tools/jdk-21.0.13+9 GRADLE_USER_HOME=/tmp/hydrogen-gradle-cache ANDROID_HOME=/tmp/hydrogen-android-sdk ANDROID_USER_HOME=/tmp/hydrogen-build-android-home bash gradlew testDebugUnitTest --tests '*BrowserOAuthTest' checkstyle pmd --continue --no-daemon --console=plain --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' -I /tmp/hydrogen-repositories.init.gradle -I /tmp/redreader-tests.init.gradle
```

目标检查 `BUILD SUCCESSFUL in 31s`，PMD 和 Checkstyle 通过。

核对 AndroidX CustomTabsIntent 的 `EXTRA_SEND_TO_EXTERNAL_DEFAULT_HANDLER` 后，确认
`android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER` 控制初始网址交给外部应用。
本次保留 false，防止初始 Reddit 授权页被另一个 Reddit 链接处理器接走；OAuth 的自定义 scheme 回跳由专用入口处理。

## 全量构建与制品

最终全量命令：

```bash
env JAVA_HOME=/tmp/hydrogen-build-tools/jdk-21.0.13+9 GRADLE_USER_HOME=/tmp/hydrogen-gradle-cache ANDROID_HOME=/tmp/hydrogen-android-sdk ANDROID_USER_HOME=/tmp/hydrogen-build-android-home bash gradlew pmd checkstyle lint test assembleRelease --continue --no-daemon --console=plain --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' -I /tmp/hydrogen-repositories.init.gradle -I /tmp/redreader-tests.init.gradle
```

结果为 `BUILD SUCCESSFUL in 2m 43s`，退出码 0；日志位于 `/tmp/redreader-google-login-final-v3.log`。
Gradle 按输入复用已完成任务；核对生成的 XML 报告后确认：

- 24 份单元测试报告，共 142 项，失败、错误、跳过均为 0，其中新增 BrowserOAuthTest 为 12 项。
- PMD 与 Checkstyle 报告无违规，Lint 无错误或警告。
- Release APK 构建通过，包名 `org.mofanke.redreader.eink`，版本 `1.27-eink.3` / 11903。
- `apksigner verify --verbose --print-certs` 验证 v1、v2、v3 签名均有效，证书 SHA-256 与 `eink-v1.27.2` APK 一致。
- `zipalign -c -P 16 4` 通过；APK 中的 `assets/eink-reading.js` 与源码逐字节一致，未包含 `assets/reddit_auth.txt`。

签名与密码继续使用仓库外文件，不写入源码。提交后使用以下任务重新提取版本控制信息，
交付前核对 APK 的 `META-INF/version-control-info.textproto` 与最终源码提交一致：

```bash
env JAVA_HOME=/tmp/hydrogen-build-tools/jdk-21.0.13+9 GRADLE_USER_HOME=/tmp/hydrogen-gradle-cache ANDROID_HOME=/tmp/hydrogen-android-sdk ANDROID_USER_HOME=/tmp/hydrogen-build-android-home bash gradlew :extractReleaseVersionControlInfo --rerun :assembleRelease --no-daemon --console=plain --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' -I /tmp/hydrogen-repositories.init.gradle
```

交付包为 `RedReader_eink_1.27.3.apk`，发布标签为 `eink-v1.27.3`。
实际下载链接与 APK SHA-256 以 [GitHub Release](https://github.com/mofanke/RedReader/releases/tag/eink-v1.27.3) 的制品信息为准。

## 实机范围

Google 完整授权、Reddit 令牌交换、B7 Pro 系统浏览器和第三方登录网络表现未实机验证。
登录失败如果来自无效 client ID、未注册正确回调、网络或服务端限制，需要对应配置或服务状态恢复。
本次修复不包含私人 client ID，使用前仍需在设置 → 网络配置自己的 Reddit Installed app。
