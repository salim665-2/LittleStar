# LittleStar 验收清单（§15）

当前主题策略见 [D45](DECISIONS.md#d45--2026-11-09--只维护浅色模式)：应用固定浅色，系统深色开关不改变应用配色。下文的深色截图与结论是历史记录，不是当前产品要求或新版本验收证据。

## 2026-11-27 同步 GitHub main（PR #22 / #23）

目的：把队友推到 GitHub 的最新界面与通知功能合入本地，并核对文档是否需要同步。`git fetch` 在本机被 TLS 拦截（schannel 握手失败、OpenSSL 后端 `unexpected eof`），改用 `codeload.github.com` 下载 main 快照并取逐条 commit patch，用 `git am` 应用 23f2e04（文化浏览器与故事时间通知）与 d95b9d9（恢复 WordListActivity）；两个合并提交 cd43431、4f4650c 无独立内容。

- 新增界面：文化分类/列表改为 `CultureCategoriesFragment` + `CultureListFragment`，窄屏切换、≥600dp 并排（[D60]）；文化列表行长按快览、播放页封面左右滑动切歌与 `onPlayerError` 恢复（[D61]）。
- 新增通知：`CultureNotifier` 发送带 Play now / Read together / Dismiss 的故事时间通知，`PlaybackService` 配置 `DefaultMediaNotificationProvider` 与 `ic_stat_panda`，两类通知互不干扰（[D62]）。
- 新增入口：恢复 `WordListActivity` 作为词表独立宿主，`WordListFragment` 改为面向 `Host` 接口，与 `ThemesActivity` 双栏共用实现（[D64]），新增 `WordListActivityTest`。
- 合并处理：队友独立修了同一处 Windows 原子写问题（[D63]），`AtomicStateFile` 采用其 NIO 版本；`docs/DECISIONS.md` 编号按上游 D59–D64、本地 D65–D67 重排，`ACCEPTANCE.md` 的链接同步改写。
- 计数：`AndroidManifest.xml` 13 个正式 Activity；`app/src/test` 18 个类 88 个 `@Test`（`testDebugUnitTest` 88/88 通过）；`app/src/androidTest` 25 个文件 91 个 `@Test`，`compileDebugAndroidTestJavaWithJavac` 通过。
- 设备回归（Pixel_2_API30）：首轮 92 条用例里 1 失败——`CultureBrowserFragmentsTest.storytimeNotificationProvidesPlayReadAndDismissActions` 在 `detailTranscript` 上直接断言可见，1080×1920 屏幕上正文在折叠线以下；改为先 `scrollTo` 再断言后，该类 4/4 通过，全量复跑 **0 失败、1 跳过**，4 分 55 秒，BUILD SUCCESSFUL。
- 报告同步：`Assignment2.docx` §9 的计数改为 88 个 JVM 用例、25 个设备测试类（91 条用例），词数 1,774 不变。
- 文档同步：`README.md` 增加自适应文化浏览与故事提醒一节；`DESIGN.md` 页面清单补 WordListActivity 与文化 Fragment；`ENGINEERING.md` Activity 清单同步；`TEAM_PLAN.md` 正式清单改为 13 个 Activity。

## 2026-11-27 API 30 兼容性回归与字体下限修复

设备：Pixel_2_API30 AVD（Android 11 / API 30 / 1080×1920 / 420 dpi，`system-images;android-30;google_apis;x86_64`）。目的：补上 API 30 的运行证据，并顺手修掉 A 模块里唯一红灯的字体用例。

- 工具链：用 curl 下载 `commandlinetools-win-13114758_latest.zip` 解压到 `D:\Android\Sdk\cmdline-tools\latest`（sdkmanager 19.0），接受许可后安装 API 30 google_apis x86_64 镜像，`avdmanager create avd -n Pixel_2_API30 -d pixel_2`；模拟器以 `-no-window -gpu swiftshader_indirect` 启动。
- `testDebugUnitTest`：87/87 通过。
- `connectedDebugAndroidTest` 全量：22 个测试类、86 条用例，**0 失败、1 跳过**（`SettingsFlowTest.save_blockedUnchangedReminderWarns_andKeepsSavedPlan`，依赖模拟器用户无法关闭的通知渠道），API 30 用时 4 分 12 秒，BUILD SUCCESSFUL。
- 同一份测试在 Pixel_7a（Android 17 / API 37）复跑：22 个测试类、86 条用例，**0 失败、1 跳过**，5 分 02 秒，BUILD SUCCESSFUL——两个 API 级别的结果一致。
- 首轮在 API 30 上暴露并修复的问题：
  1. `ActionableReminderTest`、`ReminderDeliveryProbeTest` 无条件调用 `grantRuntimePermission(POST_NOTIFICATIONS)`，API 33 以前该权限不存在，抛 `IllegalArgumentException: Unknown permission`；改为 `SDK_INT >= 33` 才授予。
  2. `CultureDetailTest.recreation_restoresSelectedContentAndExpandedTranscript` 在 `recreate()` 后等 `detailTitle` 可见；1920 高的屏幕上标题在折叠线以上，改为 `scrollBackToTitle()` 先 `scrollTo` 再断言。
  3. `SmokeTest` 在识字卡与测验流程里直接点 NEXT，小屏上按钮不足 90% 可见；改为 `perform(scrollTo(), click())`（识字卡 NEXT、测验 NEXT 与选项三处）。
  4. `ActionableReminderTest.learnNowOpensDailyFlashcardFromExistingHome` 断言通知立即消失；API 30 上系统处理取消有延迟，改为最多轮询 3 秒后仍为 null 才通过。
  5. 产品代码 `FlashcardLayout.textSize()` 用 `getDimensionPixelSize(56sp)` 得到整数 160 px，而断言用 `getDimension()` 得 160.2 px；2 倍字体下汉字比无障碍下限低 0.2 px 导致 `PeerReviewLearningTest` 红灯。改为新增 `dimensionFloat()` 使用精确 `getDimension()`，Pixel 7a / API 37 与 Pixel 2 / API 30 均通过。
- 报告同步：§9 改为“两个模拟器均通过、API 30 兼容性修复”；§10 写清与 WenxingZhang、YinuoFang 的协作细节；`Assignment2.docx` 共 1,997 词（上限 2,000）。
- 截图复核时又发现并修复一处布局缺陷：`RoundReviewActivity` 详情页的汉字用 `@dimen/ls_cover`（56dp，图标尺寸）做宽高，96sp 的字形被裁成小方块；新增 `ls_review_hero = 168dp` 并让 `detailHanzi` 使用它，`includeFontPadding=false`，重新构建后在 Pixel 2 / API 30 上复测正常。
- 报告改版：按导师反馈把四条 AI 提问改成带约束、指向代码，并把其中一条换成调试类提问（0.2 px 字体下限）；加入三张 API 30 模拟器截图（家长门 + 学习计划、回顾列表 + 详情、提醒通知）与 Google 官方文档参考文献；正文压到 1,774 词。
- 剩余缺口：无真机、无平板模拟器、无视频素材。

## 2026-11-25 个人报告事实复核与全量设备回归

设备：Pixel_7a AVD（Android 17 / API 37）。目的：把个人报告里的每条事实与当天 14:38 队友改动后的代码对齐。

- `testDebugUnitTest`：87/87 通过（UP-TO-DATE，BUILD SUCCESSFUL）。
- `connectedDebugAndroidTest` 全量：22 个测试类、86 条用例，1 失败、1 跳过。失败仍是 `PeerReviewLearningTest.flashcard_doubleFont_keepsAlignedButtonsAndReadableListen`（断言在第 216 行，属 A 的识字词卡模块，本模块未改其代码）；跳过是既有的 `SettingsFlowTest.save_blockedUnchangedReminderWarns_andKeepsSavedPlan`，与本次改动无关。
- 静态计数与报告一致：`app/src/main/AndroidManifest.xml` 12 个正式 Activity，`app/src/test` 17 个类 87 个 `@Test`，`app/src/androidTest` 22 个测试类（另 `UiEvidence` 辅助类）86 个 `@Test`。
- 代码复核：`PlaybackService` 已改为 `MediaSessionService`（`MediaSession` + `MediaController`，前台媒体通知由会话提供）；`ReminderReceiver` 在 `remaining <= 0` 时不发提醒；`ParentGateActivity` 只有首次创建 PIN 的路径，设置页内没有改 PIN 入口；`SongProgressView` 未扩大触摸区，只有拖动/提交分离。
- 据此修正报告：媒体段改称 MediaSessionService；删除“设置页可改 PIN”“触摸区超出滑块”“A1 有分析面板与云备份”等无法从代码佐证的表述；C01–C05 对齐 `TEAM_PLAN.md` 第 42–46 行的用例定义。
- 范围：仍未覆盖 API 30 真机与平板模拟器。

## 2026-11-24 测试稳定性、回顾 Fragment、PIN 哈希与步进器长按

设备：Pixel_7a AVD（Android 17 / API 37）。本轮同时修复了开发机上 Gradle 测试进程无法启动的问题。

- `testDebugUnitTest`：87/87 通过。根因是 `androidx.core` 的 `AtomicFile.rename` 直接调用 `File.renameTo`，在 Windows 上目标已存在时失败并被吞掉，导致第一次之后的保存全部静默失效；改为 `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` 并保留旧 `.bak` 迁移后用例全部通过（[D63](DECISIONS.md)）。
- `connectedDebugAndroidTest`：全量 22 个类、86 个用例，1 失败、4 跳过。修复通知权限自授予后，原先跳过的 `ActionableReminderTest` 3 条与 `ReminderDeliveryProbeTest` 1 条实际执行并通过（定向复跑 3/3、1/1），剩余 1 跳过属于既有用例的显式假设。
- 新增 `ReviewFragmentsTest` 2 条：点行进入详情 Fragment、上一题/下一题、返回回到列表；空回顾显示空状态。
- 回顾页拆分为 `ReviewListFragment` 与 `ReviewDetailFragment`：窄屏整页替换并保留返回栈，可用宽度 ≥600dp 时列表与详情并排、点选只更新右侧内容（[D65](DECISIONS.md)）。
- 家长 PIN 改为 PBKDF2WithHmacSHA256（120000 次迭代、16 字节随机盐），旧四位明文在首次校验成功后自动升级，`SettingsFlowTest` 14 条通过（[D66](DECISIONS.md)）。
- 目标步进器支持长按 400ms 后每 120ms 连续调整，到 1 或 10 自动停止，抬指立即停止（[D67](DECISIONS.md)）。
- `assembleDebug`：通过，`app/build/outputs/apk/debug/app-debug.apk` 为 74,064,501 字节。
- `gradle.properties` 移除 `-Dfile.encoding=UTF-8`：JDK 18+ 默认 UTF-8，该参数会让 Gradle 9.6 在中文用户目录下生成乱码 worker 参数文件，表现为 `ClassNotFoundException: GradleWorkerMain`（与 D63 同批）。
- 已知失败：`PeerReviewLearningTest.flashcard_doubleFont_keepsAlignedButtonsAndReadableListen` 断言汉字字号不低于 `ls_flashcard_hanzi_min`。该用例把视口强制为 360×440dp 并临时把字体缩放到 2 倍，`FlashcardLayout` 的二次测量在该设备上先按旧字号写入 px，判定为设备相关的时序问题，属于 A 的识字词卡模块，本轮未改动其代码，需在 Pixel 10 Pro 上复跑确认。
- 范围：仍未覆盖 API 30 真机、小屏与全部放大字体配置。


## 2026-10-26 三人团队子系统与文化详情

设备：Pixel 10 Pro 模拟器，Android 17。

- 正式 Manifest 映射检查通过：A 识字 4 页、B 文化 4 页、C 计划与反馈 4 页，加共同 Home，共 13 页；独立 Help 已移除。
- `testDebugUnitTest`：37/37 通过，无失败、无跳过。
- `assembleDebug`、`assembleRelease`、设备测试包编译通过；release 保持既有课堂安装签名配置。
- `connectedDebugAndroidTest`：14/14 通过，无失败、无跳过。
- 设备回归覆盖：分类 → 列表 → 详情 → 播放、暂停/切歌、返回详情再播放、后台继续播放；详情重建保留条目与文本展开状态；无效 ID 禁用播放；设置 Help/About 菜单不丢失草稿；原学习、PIN、保存取消和数据用例继续通过。
- 自动设备测试关闭动画；对话框断言限定 dialog root，避免菜单、主窗口和弹窗混淆。
- 深色详情的状态栏和导航栏断言通过，最新原始截图确认浅色图标可见；共享基类在应用主题后启用 EdgeToEdge，并在获得窗口焦点时同步外观。
- 内容校验、浅深色对比度、源 XML 解析与 `git diff --check` 通过。
- 11 条文化内容均有简介、学习重点和文本预览；子集字体覆盖现有内容全部汉字，文件约 99KB。
- 当前分工、用例、数据交接与真实材料待办见 [团队计划](TEAM_PLAN.md)；旧设计与工程说明已归档，当前文档与实现同步。

原始截图：[详情浅色](screenshots/team-modules/culture_detail_light.png)、[展开文本](screenshots/team-modules/culture_detail_preview.png)、[详情深色](screenshots/team-modules/culture_detail_dark.png)、[设置菜单](screenshots/team-modules/settings_menu.png)。

范围：本轮设备结果来自上述模拟器，没有覆盖 API 30 真机、小屏或全部放大字体配置。
原词卡占位图、文化音频素材质量与七条歌词时间戳越界仍待后续处理，本轮没有修改音频或伪造同步验收结果。


## 2026-10-24 · 删除收藏与睡前定时（D36）

- `:app:testDebugUnitTest`：37/37 通过；覆盖 v1/v2 升级到 v3、收藏字段移除及学习记录保留。
- `assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：12/12 通过。新增分类 → 列表 → 播放器、暂停/恢复、上一首/下一首、退到后台继续播放，以及旧晚安渠道清理和学习提醒渠道保留测试。
- 内容校验、浅深色对比度检查及 XML 解析通过。
- 主程序 Java 文件由 68 个减少为 63 个；移除收藏页、收藏状态、睡前定时面板及控制器。
- 页面截图：[分类浅色](screenshots/simple-playback/categories_light.png)、[分类深色](screenshots/simple-playback/categories_dark.png)、[内容列表](screenshots/simple-playback/list_light.png)、[播放器浅色](screenshots/simple-playback/player_light.png)、[深色](screenshots/simple-playback/player_dark.png)、[360×640 / 1.3 倍字体](screenshots/simple-playback/player_small_font13.png)、[小屏底部](screenshots/simple-playback/player_small_font13_bottom.png)。

## 2026-10-24 · 暖白背景（D35）

- 浅色背景 `#FAF9F6`、卡片 `#FFFFFF`；通过统一颜色资源应用到全局页面与启动背景。
- `:app:testDebugUnitTest`、`assembleDebug`、内容校验、对比度检查均通过。
- 实际页面：[首页](screenshots/warm-white/home_light.png)、[文化分类](screenshots/warm-white/culture_light.png)、[文化分类深色](screenshots/warm-white/culture_dark.png)。

## 2026-10-24 · 文化分类页提示行调整

- 将贴底的大标题、时长副标题和黄色熊猫圆章，改为收藏入口下方的小熊猫与单行提示。
- 页面支持垂直滚动，小屏与放大字体时仍可访问收藏和提示行。
- `:app:testDebugUnitTest`、`assembleDebug`、内容校验、对比度检查均通过。
- 页面截图：[浅色](screenshots/culture-footer/light.png)、[深色](screenshots/culture-footer/dark.png)、[360×640 / 1.3 倍字体](screenshots/culture-footer/small_font13.png)。

## 2026-10-24 · 移除星星奖励（D34）

设备：Pixel 10 Pro 临时只读模拟器（Android 17）。

- `:app:testDebugUnitTest`：35/35 通过，覆盖旧星星存档迁移、学习历史保留和会话完成幂等保存。
- `assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：10/10 通过，覆盖完整学习流程、完成页重建、学习任务设置及原子文件存储。
- `validate_content.py` 与 `contrast_check.py`：通过。
- 首页、识字主题和文化分类均移除累计星星；完成页保留本轮汉字、鼓励反馈、完成和再来一次。
- 存档升级至 v2，只移除旧会话的 `stars` 字段，保留识字进度、每日计划、答题记录和完成时间。
- 页面目视检查：[首页](screenshots/no-stars/home_light.png)、[主题](screenshots/no-stars/themes_light.png)、[文化分类](screenshots/no-stars/culture_light.png)、[完成页浅色](screenshots/no-stars/completion_light.png)、[深色](screenshots/no-stars/completion_dark.png)、[360×640 / 1.3 倍字体](screenshots/no-stars/completion_small_font13.png)、[小屏底部](screenshots/no-stars/completion_small_font13_bottom.png)。小屏可滚动访问两个按钮。

## 2026-10-24 · 家长页精简为学习任务（D33）

设备：Pixel 10 Pro 模拟器（Android 17）。

- `:app:testDebugUnitTest`：36/36 通过；已删除功能对应的时长策略测试随实现移除。
- `assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：9/9 通过，含 PIN 创建/验证、编辑取消、保存后今日计划和历史不变、页面重建保留草稿、关闭学习提醒、旧限时配置不再阻止学习、完整识字到奖励流程、原子文件存储。
- `validate_content.py` 与 `contrast_check.py`：通过。
- 页面目视检查：[浅色](screenshots/learning-plan/learning_plan_light.png)、[深色](screenshots/learning-plan/learning_plan_dark.png)、[360×640 / 1.3 倍字体](screenshots/learning-plan/learning_plan_small_font13.png)、[小屏滚动到底部](screenshots/learning-plan/learning_plan_small_font13_bottom.png)。文字与星期选项可读，保存和取消可访问。
- 家长页仅提供每日识字目标、学习提醒时间及星期；PIN 保留。应用不再采集孩子使用时长，不再触发休息、限时或加时页面；旧学习历史保留。

## 历史验收 · 2026-10-21

以下为功能精简前的验收记录，其中限时、休息及家长统计相关条目不再适用于当前版本。

日期：2026-10-21 ｜ 设备：一加 8T（KB2000，1080×2400，ColorOS Android 15）
构建：debug `com.littlestar.app.debug` + release `com.littlestar.app`（R8 开启，验收用 debug 签名 D27）

| # | 检查项 | 结果 | 证据 |
|---|---|---|---|
| 1 | contrast_check.py 浅色、深色都通过 | ✅ | `python3 tools/contrast_check.py` 退出码 0（全部 OK，含 night 组） |
| 2 | 没有 INTERNET 权限 | ✅ | `aapt dump permissions app-release.apk`：只有 POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE(_MEDIA_PLAYBACK) / ACCESS_NETWORK_STATE / WAKE_LOCK（后两个来自 media3） |
| 3 | 布局 / Java 无十六进制色值、硬编码中文 | ✅ | `grep -rE "#[0-9a-fA-F]{6}" app/src/main/res/layout app/src/main/java` 无输出；中文字面量脚本扫描无输出（内容 JSON 除外） |
| 4 | 教学汉字子集字体 + zh-CN，「骨」「直」字形正确 | ✅ | M1 已验（docs/screenshots/M1）；子集字体 43KB（D6） |
| 5 | 硬影按下：内容下沉、影子剩 2dp；深色下影子黑色 | ✅ | M1 Gallery 截图；M8 深色走查 docs/screenshots/M8/*_dark.png 目视确认 |
| 6 | 每日目标改为 5，第二天首页 5 段 | ✅ | 真机无法改系统时间（shell 无 SET_TIME）；用「Plan 保存 5 → 删 progress.json 重建当天 plan → 首页 Today 0 / 5 words、5 段」等价验证（docs/screenshots/M6/home_goal5_next.png，方法 D26） |
| 7 | LIMIT 后重开 app 仍停在休息页；家长延长后可继续 | ✅ | M7 真机全流程验收（commit 268cde2） |
| 8 | 会话中途到时，不打断答题 | ✅ | Flashcard/Quiz deferTimeUp()=true（M3 起）；M7 TimeUp 验收通过 |
| 9 | 睡前定时「播完这首再停」生效并发晚安通知 | ✅ | M5 真机：1 分钟定时到点 → 播完当前音轨 → PAUSED + 通知 id=1003「故事讲完啦，晚安」 |
| 10 | 重启设备后每日提醒仍触发；目标完成当天不发 | ⚠️ 代码审查通过，真机重启未测 | BootReceiver 注册 BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED → reminders.reschedule()（manifest 已注册）；不 reboot 测试机（D24 教训）；「当天完成不发」由 ReminderReceiver 读今日计划判定 |
| 11 | 进程被杀后从 Quiz 恢复到同一题 | ✅ | M8 真机：答到第 2 题 → `am kill` → 重开回到 Quiz 2/3（docs/screenshots/M8/quiz_recovery_before/after.png） |
| 12 | 小屏 360×640 每屏可滚动、无裁切 | ⚠️ 真机 ROM 限制未测 | ColorOS shell 无 WRITE_SECURE_SETTINGS，`wm size` 被拒；布局全部 wrap_content/minHeight + NestedScrollView 按 §11 实现 |
| 13 | `./gradlew test connectedAndroidTest` 全绿 | ✅ | JVM 单测 46/46；instrumented 4/4（SmokeTest 全流程、TimeUpActivityTest ×2、AtomicStateFile） |
| 14 | 无 .kt/.kts、无 Kotlin 插件、无 -ktx | ✅ | `find . -name "*.kt*"` 无输出；releaseRuntimeClasspath 只有传递的 kotlin-stdlib（§15 允许） |
| 15 | 依赖无 Room/SQLite、无 annotationProcessor | ✅ | build.gradle 仅 androidx/gson/media3；无 annotationProcessor 行 |
| 16 | StrictMode 主流程无违规 | ✅ | debug 开启 detectDiskReads/Writes；应用代码主线程无 progress.json/content 读写（D29：prefs 预热 + 朗读日期检查改异步）；残留违规均为框架内部（布局膨胀 mmap / startActivity / 输入子系统） |
| 17 | 学一个字按 Home 划掉 app，重开进度还在 | ✅ | release 包真机：学会「猫」→ `am kill` → 重开 Today 1/3 保留（docs/screenshots/M8/release_home_before/after.png） |
| 18 | progress.json 改非法 JSON 重开不崩溃、从备份恢复 | ✅ | ProgressStoreTest.corruptMain_fallsBackToBackup / corruptBoth_startsFresh 单测覆盖（真机 run-as 写文件被 SELinux 拒，代码路径单测覆盖） |
| 19 | 公开方法有 @NonNull/@Nullable；Lint 无 nullability 错误 | ✅ | 全部公开方法已标注；`assembleDebug` Lint 无 nullability 报错 |
| 20 | release APK < 30MB | ✅ | app-release.apk = 5.8MB（R8 + shrinkResources） |

## 性能

- 冷启动：`am start -W` TotalTime 726ms（< 1.5s 目标）。

## M8 走查结论（8 屏深色变体，docs/screenshots/M8/*_dark.png）

home_word / words_list / flashcard / quiz（答对反馈态）/ reward / culture_stories / player / parent_dashboard：
深色下硬影为纯黑、无文字裁切、无溢出；星星、印章、反馈色（mint/clay）正常。1.3 倍字体与小屏变体受设备 ROM 限制未做真机走查（见 #12 与 DECISIONS D24；组件级 1.3× 已由 M1 Gallery 覆盖）。
