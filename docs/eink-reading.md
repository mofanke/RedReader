# RedReader 墨水屏版

本 fork 的版本为 `1.27-eink.4`，基于 RedReader 1.27，支持 Android 5.0（API 21）及以上。
应用名为 `RedReader E Ink`，包名为 `org.mofanke.redreader.eink`，可与原版并存。

## 阅读与翻页

墨水屏模式默认开启。在设置首页的“墨水屏阅读”中可以关闭，或调整音量键方向和 1–5 行文字重叠。
更改模式后重新打开阅读页面。

| 操作 | 效果 |
| --- | --- |
| 音量减、PageDown、屏幕下一页按钮 | 当前阅读栏的下一页 |
| 音量加、PageUp、屏幕上一页按钮 | 当前阅读栏的上一页 |
| 图片查看器的原尺寸按钮 | 按图片原尺寸分块查看，小字和宽图可以逐段阅读 |

实体键直接调用同一屏幕按钮回调；按下、长按重复和松开只产生一次翻页。
输入框中保留原按键行为。图片和列表底部的翻页按钮占用独立区域，正文不会被按钮覆盖。

主页、帖子列表、社区搜索、帖子正文、评论和站内消息使用原生列表分页。
文字保留默认两行重叠；列表末尾增加空白，末页保持正常起点；上一页沿本次实际起点返回。
列表追加内容仍沿用 RedReader 的加载与缓存，不通过按键跳过中间数据。
双栏阅读以最近触摸的阅读栏为目标。

相册在墨水屏模式下显示图片目录，按键翻页、点击进入具体图片；相册图片查看器提供上一张、下一张按钮。
内置网页使用与 Hydrogen 墨水屏版相同的文字重叠、图片排版和末页留白脚本。

## 图片

帖子图片预览保持彩色和比例，限制到当前阅读栏的可读高度。当前页放不下时整体移到下一页。
长图在帖子中显示完整缩略图；点击后按纵向或行列分段阅读原图。
相邻图片段保留约 20dp 重叠，最后一段顶部对齐，只显示剩余区域并留空白。
保存、分享、图片信息等原有操作保留在图片查看器的“更多”菜单中。

图片查看器复用 RedReader 的下载缓存，将压缩文件暂存到缓存目录。
JPEG、PNG、WebP 等受 Android 区域解码器支持的格式只解码当前区域，使用 8 MiB 分段缓存，单段采样限制在 400 万像素以内。
不支持区域解码的格式使用最多约 200 万像素的静态首帧预览，原尺寸按钮不可用。动态图使用静态预览。
临时文件限 128 MiB，页面销毁后关闭解码器并删除临时文件。

列表取消翻页动画，阅读标题栏保持稳定。主题默认使用灰色明亮主题，图片保持原色。
帖子正文与评论沿用原生列表，评论入口不固定悬浮在正文底部。

## Google 登录

“添加账号”会打开系统浏览器，请在 Reddit 登录页选择 Google 登录，随后确认 Reddit 授权。
授权完成后返回 RedReader E Ink；如果系统询问打开方式，选择 RedReader E Ink。
没有浏览器时安装 Chrome、Firefox 等浏览器，再在登录页点击“继续登录”。

`1.27-eink.4` 增加“选择其他浏览器”，可手动指定已安装的浏览器。
自动启动会优先使用默认浏览器；启动被系统拒绝时尝试下一款。
浏览器识别同时查询 HTTP、HTTPS 网页入口和 Android 浏览器类别，兼容缺少 DEFAULT 类别的网页入口。
登录使用普通浏览器页面，未安装、禁用或无法启动浏览器时弹出明确提示。

登录使用浏览器已有会话，Google 密码和验证码在浏览器内填写。
回跳由专用入口返回发起登录的页面，随后沿用 RedReader 原有账号与令牌处理。
取消或过期时可以在登录页重新发起。启用了 Tor 时使用 Tor Browser 登录。

使用 Google 登录仍需要下节中的 Reddit Installed app client ID。
未配置时直接弹出 client ID 填写框，保存后继续登录，也会更新原来的设置 → 网络配置。
这里填写的是 Reddit 应用 ID，不是 Google 邮箱或密码；回调地址保持 `redreader://rr_oauth_redir`。
本次已验证浏览器启动与回跳契约，Google 账号完整授权和 B7 Pro 浏览器表现尚未实机验证。

## Reddit 联网配置

RedReader 上游要求自行构建的客户端使用自己的 Reddit Installed app client ID。
本 APK 不包含私人 client ID，首次联网前需要配置：

1. 在 [Reddit 应用设置](https://old.reddit.com/prefs/apps) 创建或选择自己的 Installed app。
2. Redirect URI 使用 `redreader://rr_oauth_redir`。
3. 在应用中进入“设置 → 网络 → Reddit client ID override”，填写该 ID。
4. 接受 Reddit 使用条款，按需添加自己的 Reddit 账号。

上游要求见 [CONTRIBUTING.md](../CONTRIBUTING.md)。应用内的 ID 不写入公开源码。
本次未配置私人 client ID，未验证 Reddit 联网、登录和实时内容加载。

## 构建

使用 JDK 21、项目 Gradle Wrapper 与 Android SDK 36：

```bash
bash gradlew pmd checkstyle lint test assembleRelease
```

发布 APK 使用仓库外的签名密钥，经 `zipalign` 和 `apksigner` 处理。
复现构建时使用自己的密钥，密钥和密码不提交到仓库。

网页回归使用 Python Playwright 与 Chromium：

```bash
python3 tests/web_paging_test.py
```

已有 Chromium 时可通过 `PAGER_TEST_CHROMIUM` 指定可执行文件。
本机 Android 测试将 Robolectric 缓存与下载锁放在临时目录；验证记录见 `docs/verification/`。

本次尚未在 B7 Pro 上验证按键事件、刷新速度、残影、清晰度与实际内存占用。
