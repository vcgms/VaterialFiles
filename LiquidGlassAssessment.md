# VaterialFiles 液态玻璃 UI 风格改造评估

> 状态：**评估稿，待确认后动工**。任何代码改动（含依赖、布局、主题）都未开始。
> 参考文档：`AndroidLiquidGlass.md`（Photofall 项目基于 Kyant0/backdrop 的 Compose 落地经验）。

---

## 1. 结论摘要（TL;DR）

| 项 | 结论 |
|---|---|
| 改动性质 | 中等偏大：**新增一套可切换的玻璃主题层 + 玻璃渲染组件 + 页面接入**，非重构业务逻辑 |
| 最大障碍 | 经验文档的 backdrop 库是 **Jetpack Compose 库**，本项目是 **纯 View 体系（ViewBinding + Fragment + XML）**，不能直接照搬 |
| 推荐路线 | **路线 B：View 原生液态玻璃**（AGSL RuntimeShader + RenderEffect + 截图采样 + 玻璃色板），不引入 Compose |
| 预估工作量 | 约 **5~9 人日**（分 4 个阶段，可增量落地、可随时停在任何阶段） |
| git 并行开发 | **可行且推荐**：`git worktree` 双工作树 + 功能分支并行，前提是先把当前工作区未提交改动提交 |
| 风险点 | 性能（列表滚动采样）、玻璃面可读性（深色模式）、上游 fork 同步 |

核心判断：**液态玻璃可以作为"风格层"增量叠加在现有 MD3 之上，不需要重写任何业务代码**。文件列表、双列、拖拽、设置等全部既有功能保持不变，只替换"表面"。

---

## 2. 项目现状盘点（改造基线）

### 2.1 技术栈

- 纯 View 体系：`buildFeatures { viewBinding = true }`，**无 Jetpack Compose**（无 compose 依赖、无 composeOptions）
- `minSdk 33` / `targetSdk 35` / `compileSdk 37`，Kotlin 2.4.0，AGP 9.4.1
- UI 组件：Material Components 1.14.0 + AppCompat + CoordinatorLayout/AppBarLayout + PreferenceCompat + RecyclerView + DrawerLayout + SpeedDial

> minSdk 33 是关键利好：**AGSL RuntimeShader（API 33+）与 RenderEffect 模糊（API 31+）全量可用**，View 体系下可以实现折射/色散/增饱和/模糊整条特效管线，不需要 Compose。

### 2.2 主题与全局注入体系（已确认）

- 全应用恒定 MD3：`Theme.MaterialFiles.Material3`（themes_material3.xml）+ Material You 动态色开关（`Settings.MATERIAL_YOU`，DynamicColors 程序化叠加）
- **统一注入点 `AppActivity`**（28 个 Activity 全部继承它）：NightModeHelper（getDelegate）、CustomThemeHelper（onCreate）、DisplaySizeHelper（attachBaseContext）、SecurityManager（onResume）
- 切换设置项实时生效的既有机制：`SettingLiveData` + `CustomThemeHelper.sync()` 检测变化 → `recreateCompat()`
- 夜间模式：NightMode 设置（跟随系统/浅/深）+ 黑色模式（Black 主题变体）

### 2.3 页面与容器清单（玻璃化目标）

| 目标 | 文件 | 数量 |
|---|---|---|
| 文件列表页（核心） | file_list_fragment_*.xml + FileListFragment/FileListPaneFragment | 1 宿主 + 2 列 pane |
| 顶栏 | file_list_fragment_app_bar_include.xml（`?colorAppBarSurface`）、13 个二级页面 toolbar 布局（已统一 48dp 高度） | 14 |
| 底部栏 | file_list_fragment_bottom_bar_include.xml（`?colorSurface`） | 1 |
| FAB | file_list_fragment_speed_dial_include.xml | 1 |
| 抽屉 | navigationFragment（`?colorSurface`） | 1 |
| Preference 页面 | settings_fragment / ftp_server_fragment 等 + preference_material3.xml 行布局 | ~8 |
| 对话框 | FileActionsDialogFragment、FilePropertiesDialogFragment 等 30+ | 30+ |

### 2.4 设置项扩展点（已确认，新增"界面风格"开关将沿用）

枚举型单选设置项有固定的 **4 处扩展点**（缺一无效/崩溃）：
1. `settings/Settings.kt`：enum class + `EnumSettingLiveData(pref_key, pref_default, Enum::class)`
2. `values/donottranslate_prefs.xml`：key / default / entryValues（序号数组）
3. `values/strings.xml` 及各语言：title + entries
4. `xml/settings.xml`：`EndAlignedSimpleMenuPreference`（其自定义布局必须含 `@android:id/empty` 视图——已有布局满足）

### 2.5 git 现状（重要前置条件）

- 单分支 `master`，与 `origin/master` 一致（HEAD = 7df5d2ef）；`origin` = 本 fork（vcgms/VaterialFiles），**未配置 upstream remote**
- **工作区有 20+ 未提交改动**（上一会话的「全页面标题栏高度统一 + 设置页 MD3 偏好重构」，构建产物 v2.5.91）。这是并行开发的阻塞点，必须先 commit 使 master 干净
- 既有经验约束：fork 向 origin push 前需 fetch + rebase 对齐上游；build.sh 每轮自动递增 versionCode/versionName

---

## 3. 经验文档适用性分析

`AndroidLiquidGlass.md` 的经验**不能直接照搬**，但**大部分可迁移**：

### 3.1 不可直接使用（原因：Compose 专属）

| 文档内容 | 障碍 |
|---|---|
| `rememberLayerBackdrop()` / `Modifier.layerBackdrop()` | 基于 Compose GraphicsLayer 录制，无法录制 View 内容 |
| `Modifier.drawBackdrop(...)` / `presetEffects` | Compose DrawModifier 体系 |
| `DampedDragAnimation` / `InteractiveHighlight` / `inspectDragGestures` | Compose 手势/动画体系 |
| 双风格切换 `LocalGlassContext` | CompositionLocal，Compose 概念 |

**结论：引入 backdrop 库 = 必须引入 Compose + 把页面 Compose 化。** 文件列表页（RecyclerView + 拖拽 + 双列 + 面包屑 + 手势矩阵，含大量定制交互）Compose 化等于重写整个 APP 的 UI 层，风险极高，**排除**。

### 3.2 可迁移（与 UI 框架无关的经验）

- **GlassPreset 参数体系与三档预设**（通透 1dp / 均衡 3dp / 磨砂 24dp；透明度 100 + 高光 0.9 + 阴影 0.1）
- **三种背景源模式**（§4）：文件列表页/设置页对应「纯色背景源模式」；列表项内部对应「staticGlass 模式」
- **玻璃表面色调**（§3.3）：近白 `#FAFAFA` / 近黑 `#121212` 半透明罩，浓度随透明度参数
- **深浅色必须跟随应用实际主题**（§8.2）——本项目有 NightMode + 黑色模式 + Material You，直接对应
- **玻璃容器内文本显式着色**（§8.3）——View 体系下靠玻璃色板主题自动保证
- 特效管线顺序 `vibrancy → blur → lens → opacity` 与调参范围（§10）

---

## 4. 方案路线对比

| 路线 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| A. Compose + backdrop | 引入 Compose + backdrop，逐页 Compose 化 | 完整复刻 Photofall 效果 | = 重写全部 UI 层；文件列表核心页 Compose 化风险极高；改动量 30+ 人日 | ❌ 排除 |
| **B. View 原生液态玻璃** | 自研玻璃渲染层（AGSL + RenderEffect + 截图采样）+ 玻璃色板主题 overlay + 风格开关 | 低侵入、增量落地、不重写业务、minSdk 33 全量支持 AGSL | 需自研渲染组件（约 3~5 个类）；"真透过"玻璃需额外性能策略 | ✅ **推荐** |
| C. 混合（View 骨架 + ComposeView 玻璃件） | 只把玻璃控件写成 Compose | 玻璃件开发快 | backdrop 无法跨 View/Compose 边界采样，文件列表内容采样不了；双体系维护 | ❌ 不推荐 |

### 推荐路线 B 的技术底座

| 经验文档管线 | View 体系等价物 |
|---|---|
| 背景采样（layerBackdrop） | 静态背景源（页面背景色/渐变绘制为 Bitmap/AGSL 输入）；可选周期截图（RealtimeBlurView 思路） |
| vibrancy（增饱和）/ lens（折射/色散）/ blur | **AGSL RuntimeShader**（API 33+，本项目全量可用） |
| Highlight / Shadow / InnerShadow | 渐变 drawable / 描边 / 阴影层 |
| onDrawSurface 表面色调 | 半透明玻璃色板 |
| layerBlock 按压动效 | ViewPropertyAnimator scale/translation + 触点高光 overlay |

---

## 5. 改动范围（分 4 阶段，每阶段独立可验收）

### P0：基础设施（0.5~1 人日，改动最集中）

**新增文件**
1. `theme/glass/GlassMode.kt`：enum（MATERIAL / LIQUID_GLASS）
2. `theme/glass/GlassPreset.kt`：三档预设数据类（照搬经验文档 §3）
3. `ui/glass/GlassBackgroundDrawable.kt`：AGSL 折射/色散/高光/阴影 + 表面色调的 Drawable（替代经验文档 liquidGlass/staticGlass 修饰器）
4. `ui/glass/GlassFrameLayout.kt`：玻璃容器 ViewGroup——把 GlassBackgroundDrawable 设为前景/背景，按 GlassPreset 渲染（含"静态玻璃"模式）
5. `res/values/colors_glass.xml`：玻璃色板（浅色 `#FAFAFA`、深色 `#121212`、高光/阴影透明度档位）
6. `res/values/themes_glass.xml`：ThemeOverlay（把 colorSurface/colorAppBarSurface 等映射为半透明玻璃色 + 玻璃行布局引用）

**修改文件（最小接触点）**
7. `settings/Settings.kt`：+1 行 `GLASS_MODE: SettingLiveData<GlassMode>`
8. `res/values/donottranslate_prefs.xml`：+pref_key_glass_mode / pref_default_value_glass_mode（默认 MATERIAL）/ pref_entry_values_glass_mode
9. `res/values/strings.xml`（+各语言）：settings_glass_mode_title / settings_glass_mode_entries
10. `res/xml/settings.xml`：界面分类下 +1 个 `EndAlignedSimpleMenuPreference`
11. `app/AppActivity.kt`：onCreate 里按 GlassMode 注入玻璃 overlay（复用 CustomThemeHelper 同款 sync/recreateCompat 模式）

**验收**：设置页出现「界面风格」；切换后主题色板变化并重建生效；`./build.sh` 通过。

### P1：核心页面（1~2 人日）

12. 文件列表页：
    - app_bar_include：`CoordinatorAppBarLayout` 背景 → 玻璃渲染（滚动抬起态 liftOnScroll 用玻璃浓度变化）
    - bottom_bar_include：底部栏玻璃容器
    - speed_dial_include：FAB 玻璃化
    - 抽屉 navigationFragment：玻璃面板
    - 列表背景：玻璃模式下 content 区铺渐变/纯色背景源（对应经验 §4.2 纯色背景源模式）
13. 设置/FTP 等 Preference 页面：preference_material3.xml 行布局 + 分类标题改为玻璃卡片容器
14. 双列 pane 分隔线、面包屑选中态适配玻璃对比度

**验收**：核心页面呈现高光 + 折射 + 半透明玻璃质感；深/浅/黑三模式下文字可读。

### P2：全页面铺开（1~2 人日，机械替换为主）

15. 13 个二级页面 toolbar 布局（about/ftp_server/storage_list/viewer 等，高度已统一）：背景由实色改为玻璃
16. 对话框家族（FileActionsDialogFragment、FilePropertiesDialogFragment、输入类对话框等 30+）：容器背景/卡片玻璃化（对话框对应经验 §4.2/§4.3）
17. 玻璃行/卡片样式在 styles 层统一注册，布局文件只改 1 处引用

**验收**：全部页面与对话框风格一致；无漏网实色容器。

### P3：质感细节（2~4 人日，调优重）

18. 三档玻璃浓度（通透/均衡/磨砂）设置项 + 试验页（可选）
19. 按压动效：FAB/工具栏按钮 scale + 触点高光
20. 可选增强——「真透过玻璃」：RealtimeBlurView 式周期截图采样（滚动时暂停/降频），仅当用户确认真透过是刚需时才做（性能风险见 §6）

**验收**：动效流畅、滚动帧率无明显下降。

---

## 6. 技术要点与风险对策

| 风险 | 说明 | 对策 |
|---|---|---|
| 性能（滚动采样） | 玻璃实时采样 RecyclerView 内容需每帧截图模糊，滚动卡顿 | MVP 采用「纯色背景源」模式（玻璃只采样页面背景，不透过列表内容），质感完整、零采样成本；真透过列为 P3 可选增强，滚动中暂停采样 |
| 可读性 | 深色/黑模式下玻璃面文字对比度 | 玻璃色板按应用实际深浅色派生（对应经验 §8.2 的 LocalAppDarkTheme 教训）；玻璃内文本显式着色（主题保证） |
| 切换实时性 | 切换风格后旧页面残留 | 复用既有 `SettingLiveData + recreateCompat` 机制（与 Material You 开关同款） |
| 与动态色冲突 | Material You 动态色 + 玻璃表面色调叠加 | 玻璃表面色调固定近白/近黑（不随动态色漂移），仅强调色（选中态/FAB）沿用动态色 |
| AGSL 兼容 | 折射/色散在低端 GPU 的表现 | AGSL 为官方 RuntimeShader 标准管线；降级路径：shader 失败 → 退化为纯半透明 + 高光（对应经验 §1.3 无 shader 退化） |
| 上游 fork 同步 | 上游 MaterialFiles 持续演进 | 合并前 fetch + rebase，按定制意图裁决冲突（既有经验） |

---

## 7. git 并行开发方案

### 7.1 结论：**可行，推荐 git worktree 双工作树**

UI 玻璃化与功能开发**互不阻塞**，可并行进行。

### 7.2 前置动作（必须先做）

1. **提交当前工作区**：现有 20+ 未提交文件（标题栏高度统一 + MD3 偏好重构）commit 到 master 并 push（或 stash，不推荐长期 stash）
2. （可选）`git remote add upstream https://github.com/zhanghai/MaterialFiles.git` 以便后续同步

### 7.3 并行方案（推荐 worktree）

```bash
# master 干净后
git worktree add ../VaterialFiles-ui   -b feature/liquid-glass-ui   # UI 工作树
git worktree add ../VaterialFiles-func -b feature/your-feature       # 功能工作树
```

- 两个目录独立编辑/独立 `./build.sh`（各生成自己的 apks/，互不干扰），共享 .git 元数据
- 每完成一个阶段在各自分支 commit；阶段完成合入 master 后另一分支 `rebase master` 即可
- 备选：同一目录来回 checkout 分支（简单但不算真并行）

### 7.4 冲突面控制（关键设计原则，已写入 P0 方案）

UI 改造**新文件化**（玻璃组件/色板/主题 overlay 全部新文件），公共文件只保留 5 个最小接触点：
`Settings.kt`（1 行）、`settings.xml`（1 块）、`strings/donottranslate_prefs`（各 1 块）、`AppActivity.kt`（1 行）、`themes_material3.xml`（1 行 overlay 引用）。

约定：功能分支**不碰**这 5 个接触点 → 合并冲突面收敛到几乎为零。

### 7.5 已知必然冲突与处理约定

- `app/build.gradle` 版本号：build.sh 每轮自增 → 合并后**重跑一次 build.sh** 解决，不手工改
- 布局文件的背景替换（P1/P2）：UI 分支独占，功能分支若必须改同一布局，先 rebase 最新 UI 分支

### 7.6 合并顺序建议

UI 分支 P0+P1 完成并验收后**先合入 master**（功能分支可基于玻璃基线继续开发），随后功能分支 rebase 合入；后续 P2/P3 以独立分支迭代。

---

## 8. 里程碑与验收标准

| 里程碑 | 内容 | 验收标准 |
|---|---|---|
| M0 | 提交现有工作区、master 干净 | git status 干净 |
| M1（P0） | 玻璃基础设施 + 风格开关 | 设置页出现「界面风格」；切换生效；build.sh 通过 |
| M2（P1） | 文件列表 + 设置页玻璃化 | 核心页面玻璃质感；三模式下可读；build.sh 通过 |
| M3（P2） | 全页面 + 对话框铺开 | 无漏网实色容器；build.sh 通过 |
| M4（P3） | 三档浓度 + 按压动效 | 动效流畅；（可选）真透过玻璃性能达标 |

每轮遵循既有约定：`./build.sh` 编译验证、APK 进 apks/；上机截图验证仅按需。

---

## 9. 待确认事项

1. **路线确认**：是否采用推荐路线 B（View 原生液态玻璃，不引入 Compose）？
2. **玻璃浓度**：是否沿用经验文档三档（通透/均衡/磨砂，默认均衡）？
3. **MVP 范围**：是否接受「内容不透过」的静态玻璃作为首版（P1），「真透过玻璃」列为 P3 可选增强？
4. **优先级**：P1 是否以「文件列表页 + 设置页」为先（其他页面 P2 后置）？
5. **git 并行**：近期是否有具体功能开发计划需要并行？若有，请告知，我按 §7.3 建 worktree；若无，UI 分支直接基于 master 开发即可。
6. **当前未提交改动**：是否同意先把上一会话的 MD3 偏好重构提交（M0）？
