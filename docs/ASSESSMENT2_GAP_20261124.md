# Assessment 2（Android Project Part 2）对照检查

> 历史记录：本文写于 2026-11-24，此后代码已合入 PR #22（文化浏览器与故事通知）与 PR #23（恢复 WordListActivity），正式页面由 12 个变为 13 个。当前结论以 `docs/ACCEPTANCE.md` 为准。

检查日期：2026-11-24 20:00　｜　截止：2026-11-27（周五）23:59 北京时间，剩约 3 天
对照文档：`C:\Users\杨国辉\Desktop\安卓\assessment 2\Assignment_2_ITECH3106_AndroidProject2_2026.docx`
被检查项目：`D:\LittleStar-main`（Java 17 / AGP 9.3.0 / Gradle 9.6.0 / minSdk 30 / target 37）

---

## 一、一句话结论

**功能实现基本齐了（碎片、多媒体、通知、菜单、动画、SharedPreferences 都有真实代码），丢分点几乎全在"提交物"和"证据"上：**

1. 报告（500–700 词英文，6 节）**完全没有**
2. AI 使用记录（≥4 个提问 + 批判 + 200–300 词反思 + 单独的 AI 回答 Word）**完全没有**
3. 同伴代码评审**没有真实材料**（DECISIONS.md 自己写着是"AI 模拟的评审"）
4. 演示视频（≤5 分钟）**没有**
5. 单元测试 87 个里 **4 个确定性失败**，且 API 30 / 平板宽度**没测过**
6. 触摸交互只有进度条拖动——评分表里 Touch 4 分，这是技术面最薄的一项
7. 多媒体只有音频 + 图片，**没有视频**

---

## 二、评分表逐项对照

| 评分项 | 分值 | 现有证据 | 缺口 | 优先级 |
|---|---|---|---|---|
| Requirements Specification | 5 | README 功能清单、docs/DESIGN.md | 报告里要有"全部已实现功能 + 说明 + 额外功能及理由"的表格 | P1 |
| Fragments | 4 | `ThemesActivity` 托管 `ThemesFragment` + `WordListFragment`，`layout-w600dp` 双栏，`isTwoPane()` 判断 | 代码达标；需补"手机单栏 / 平板双栏"对比截图和文字说明 | P2 |
| Multimedia | 4 | 36 段单词读音 + 11 段文化音频 + 4 个音效 + 36 张矢量图 + Media3 前台播放 | **无视频**；评分表写的是 audio/video/images，建议至少加 1 段短视频（文化页内嵌） | P1 |
| Touch | 4 | 只有 `SongProgressView.onTouchEvent`（拖动进度条） | 无手势/滑动/长按，全项目搜不到 `GestureDetector`/`onFling` | **P0** |
| UI/UX | 5 | Material 3、自定义 toolbar、对话框、Snackbar、响应式双栏 | 基本齐；补平板与大字体截图 | P2 |
| Notifications | 4 | `Notifier.reminder()` 带 `Learn now` + `Dismiss` 两个 action，定时提醒 + 开机恢复 | 已达标；可在报告里说明"channels/权限/点击跳转"设计 | P2 |
| Menus | 4 | `menu_word_browser.xml`（Continue browsing / Play theme audio）、`menu_settings.xml`（Help / 测试通知 / About） | 已达标 | P2 |
| Testing & Bug Fixes | 5 | 87 个 JVM 测试 + 24 个 instrumentation 测试类 | **4 个测试失败**；API 30 未跑；平板未测；无修复记录 | **P0** |
| Teammate Code Review | 5 | 无 | 需要真实的互评记录（谁看了哪段代码、提了什么、怎么回应） | **P0** |
| Video Presentation | 10 | 无 | ≤5 分钟屏幕录制 + 讲解，含两个子系统集成 | **P0** |
| AI Usage | 10 | 无（仓库里没有记录） | ≥4 条真实提问 + 对回答的批判 + 200–300 词反思 + 单独 Word 附件 | **P0** |
| Group report | 10 | 无 | 500–700 词英文，6 个小节 | **P0** |
| Combined screenshots | 5 | docs/screenshots 有 118 张（11-23 10:30 截止） | 需要最新版"合并功能"截图（学习闭环 + 文化模块互跳） | P1 |

---

## 三、P0：三天内必须做完的事

### 1. 修 4 个失败的单元测试（约 0.5–1 天）

当前 `./gradlew :app:testDebugUnitTest` 结果：**87 tests, 4 failed**（确定性失败，重跑结果一致）：

| 测试 | 位置 | 失败信息 |
|---|---|---|
| `AtomicStateFileTest.legacyAtomicBackup_takesPriorityOverUncommittedMain` | `app/src/test/.../data/AtomicStateFileTest.java` | `ComparisonFailure: expected:<{"nextSessionId":42}> but was:<{ incomplete>` |
| `ProgressStoreAsyncTest.queuedRecoveryAnswersAndCompletion_flushBeforeDebounceAndKeepSnapshots` | 同上，断言在 **141 行** | 期望完整 3 条答案的 JSON，实际 `answers` 为空、`box:2` |
| `ProgressStoreAsyncTest.debouncedSave_persistsLastQueuedMutationWithoutFlush` | **171 行** | `expected:<101> but was:<1>` |
| `ProgressStoreAsyncTest.concurrentCallers_mutationsAreSerializedBeforeFlush` | **199 行** | `nextSessionId` 期望 101 |

三个 `ProgressStoreAsyncTest` 失败都指向同一件事：**`store.update(...)` 排队后没有真正落到磁盘**（防抖 30ms 也没写、`flushNow()` 后仍是初始值 1）。先判断是"实现有 bug"还是"测试没跟上实现"（比如 `update()` 改成只在 `loadAsync()` 完成后才受理，测试里 `loadAsync()` 是异步的）。**测试过不了就别说"测试通过"，报告里必须如实写测试策略和修 bug 过程——这项本身就是 5 分。**

> 环境坑：这台机器上 `org.gradle.jvmargs` 带 `-Dfile.encoding=UTF-8` 时，Gradle 9.6 起的测试 worker 起不来（`ClassNotFoundException: GradleWorkerMain`，每个 executor 都失败），与你代码无关。临时验证办法：把 `gradle.properties` 第一行改成 `org.gradle.jvmargs=-Xmx4g`，`gradlew --stop` 后重跑；或在 Android Studio 里用内置 JDK 跑。交付前记得把配置还原（或干脆保留精简版并在报告里说明）。

### 2. 加一个真实的手势交互（约 0.5 天）

最小改动、收益最高：

- **播放页左右滑切歌**（`PlayerActivity` 的封面区域加 `GestureDetector.onFling`，左滑下一首、右滑上一首，配 `Motion.popIn` 过渡）；
- 或 **长按单词卡朗读**（`setOnLongClickListener` → `SpeechPlayer` 慢速朗读）。

改完在报告"Touch"一节写清：用的是什么手势、响应区域、与点击的冲突怎么处理。**注意别加回卡片滑动翻页**——`docs/DECISIONS.md:314` 记录过你明确移除 ViewPager2、只保留 Next 按钮，两者会互相打脸。

### 3. 跑一遍 API 30 + 平板宽度（约 0.5 天）

- 装一个 API 30 的 Pixel 模拟器，跑 `connectedDebugAndroidTest`（或至少手工走主流程），截图存档；
- 用 `layout-w600dp` 预览/模拟器验证双栏；再把字体调大 1.3 倍看是否截断。
- 现有 `docs/ACCEPTANCE.md` 自己写着"没有覆盖 API 30 真机、小屏或全部放大字体配置"——评分表明确要求 API 30 → latest，这条是硬缺口。

### 4. 报告 + AI 使用记录 + 视频 + 互评（约 1.5 天，可并行）

见下面第四节提纲。四样东西互相引用，建议先写报告，再从报告里抽出视频脚本和 AI 提问清单。

---

## 四、报告写作提纲（500–700 词英文，别超太多）

评分表要求的 6 个小节，按这个骨架写（每节 70–110 词）：

1. **Recap of A1 design**（约 80 词）
   一句话场景（heritage kids 3–6，English UI + Chinese content）、A1 的功能清单、两个人的子系统怎么划分。**注意：brief 写的是"pair assignment / 每人负责自己在 A1 设计的子系统"，而仓库里是 A/B/C 三人结构（TEAM_PLAN.md、README 都写"三人团队"）。这条要么改成两人叙事，要么先跟老师确认三人是否允许。**

2. **Mapping of implemented features**（约 120 词，配表格）
   表格列：功能 / 实现类 / 对应 A1 需求 / 额外功能及理由。至少覆盖：碎片双栏、工具栏菜单、可操作通知、Media3 播放、SharedPreferences 设置、动画、响应式布局。**"额外功能"要写理由**，比如"父辈门禁 PIN 保护儿童误操作""每日目标 + 星星奖励闭环"。

3. **AI usage**（约 100 词 + 指向附件）
   说明用了哪些工具、在哪几处用（布局、测试、文案、代码审查）、哪些建议采纳/拒绝及原因。

4. **Integration strategy**（约 100 词）
   两个子系统怎么通过 `HomeActivity` 汇合：首页入口 → 子系统 A（字：主题/词卡/测验/复习）→ 子系统 B（文：文化分类/详情/播放）→ 共享的 `ProgressStore` / `SettingsStore` / 通知。**写清数据层是共享的**，这是"integration through MainActivity"最有力的证据。

5. **Testing strategy & results**（约 120 词）
   层次：JVM 单测（87 个）→ instrumentation（24 个类）→ 手工（Pixel 10 Pro API 37 + API 30 + 平板宽度）。**如实写：曾有 4 个失败、根因是什么、怎么修的**（这一段就是 5 分里的"bug fixes"）。

6. **Lessons learned / future improvements**（约 80 词）
   比如：早期没做手势交互导致后期补；测试环境编码问题耗时间；未来可加视频内容、云同步。

另附 **200–300 词 AI reflection**：具体到"哪次 AI 给的方案不对、你怎么发现、最后怎么改"，不要泛泛说"AI 很有帮助"。

**提交结构**（评分表明确要求）：

```
LittleStar_submission/
├─ code/                 ← 只放 wrapper + app/ + docs/，不要 local.properties、build/、.gradle
├─ Report_<学号>_<姓名>.docx        （500–700 词正文 + 每节的 prompt/批判）
└─ AI_Responses_<学号>.docx        （单独的 AI 回答记录，不要和报告合并）
```

---

## 五、其他会被扣分/被问到的点

| 问题 | 现状 | 建议 |
|---|---|---|
| 没有 `MainActivity` | 启动页是 `HomeActivity` | 报告里明确写"`HomeActivity` 即应用入口（MainActivity 角色）"，或加一个空的 `MainActivity extends HomeActivity` 并在 manifest 里替换（有风险，非必要不做） |
| README 里的 PR #16/#17 | 仓库**没有 .git**，链接是死的 | 删掉或改成"见 docs/ENGINEERING.md 对应章节"；老师若问协作痕迹，没有 commit history 是弱项 |
| 文档陈旧 | `WordListActivity`（已删除的类）仍出现在 DESIGN.md:31、ENGINEERING.md:56、TEAM_PLAN.md:20,33、MEMBER_A_COMPLIANCE:4；ACCEPTANCE 写"37/37"已过期（现 87 个） | 统一清理一次，避免报告与代码对不上 |
| `FlashcardViewModel.java:203` 注释 | 还写着"翻页（ViewPager2 回调）" | 删掉，否则和"不做手势翻页"的设计说明矛盾 |
| PIN 明文存储 | `SettingsStore` 里 `parent_pin` 明文 4 位 | 加盐 SHA-256（10 行代码），正好对应 learning outcome 里的 security，报告可写 |
| 视频缺失 | 只有音频/图片 | 文化详情页加 10–20 秒短视频（ExoPlayer 已支持），或明确在报告里说明"视频以音频形式呈现的取舍" |
| applicationId | `com.littlestar.app` | TEAM_PLAN 自己写着"课程命名格式待确认"，尽快问老师，改名成本很低（改 namespace 要全局搜） |
| 三人 vs 两人 | brief 写 pair | 见上，优先确认 |

---

## 六、三天倒排计划

- **11/24 今晚**：修 4 个测试（先定位 `ProgressStore.update` 的写入路径）→ 决定手势方案
- **11/25**：实现手势 + 短视频 + PIN hash；起 API 30 模拟器跑一遍；截合并功能截图
- **11/26**：写报告 6 节 + AI 反思；整理 AI 回答 Word；做同伴互评（互相看对方的 2 个类，留文字记录）
- **11/27**：录 ≤5 分钟视频（讲两个子系统 + 集成 + 演示手势/通知）→ 打包 → 提交前逐项对照本表打勾

---

## 附：自查命令

```powershell
cd D:\LittleStar-main
.\gradlew.bat :app:testDebugUnitTest --console=plain   # 目标：87 passed
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain   # 需先启模拟器
```
