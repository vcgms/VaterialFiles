# AndroidLiquidGlass 液态玻璃实战指南

> 基于 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`backdrop` 模块）
> 在 Photofall 项目中全应用落地的完整经验总结：库的用法、参数体系、背景源模式、
> 六类控件实现方法、双风格架构，以及十余个真实踩坑的修复方案。供其他 Compose 项目直接参考。

---

## 目录

1. [库简介与集成](#1-库简介与集成)
2. [核心概念与 API](#2-核心概念与-api)
3. [预设参数体系（GlassPreset）](#3-预设参数体系glasspreset)
4. [三种背景源模式（关键架构决策）](#4-三种背景源模式关键架构决策)
5. [基础修饰器：liquidGlass / staticGlass](#5-基础修饰器liquidglass--staticglass)
6. [控件实现详解](#6-控件实现详解)
7. [手势与动画底座](#7-手势与动画底座)
8. [双风格架构（玻璃 / Material 实时切换）](#8-双风格架构玻璃--material-实时切换)
9. [踩坑清单与最佳实践（必读）](#9-踩坑清单与最佳实践必读)
10. [参数调节参考](#10-参数调节参考)

---

## 1. 库简介与集成

### 1.1 能力概述

`backdrop` 模块提供 Compose 下的**背景采样 + 特效管线**能力，是 iOS「液态玻璃」的
Android 实现基础：

- **背景采样**：将任意 Compose 图层录制为 `Backdrop`（基于 `GraphicsLayer` 录制显示列表），
  玻璃控件实时采样其"下方"内容；
- **特效管线**：增饱和（vibrancy）→ 模糊（blur）→ 透镜折射/色散（lens）→ 透明度（opacity）；
- **质感叠加**：高光（Highlight）、外阴影（Shadow）、内阴影（InnerShadow）、表面色调（onDrawSurface）；
- **按压动效原料**：`layerBlock` 提供每帧图层变换（缩放/位移）的注入点。

库本身**不提供现成控件**（按钮/滑杆/标签栏等），需要自行组装——本文第 6 节给出全部实现。

### 1.2 依赖引入（Maven 方式，推荐）

```toml
# gradle/libs.versions.toml
[versions]
kyantBackdrop = "2.0.1"

[libraries]
kyant-backdrop = { group = "io.github.kyant0", name = "backdrop", version.ref = "kyantBackdrop" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.kyant.backdrop)
}
```

> 备选方案对比：源码内嵌（可改库内部实现，但升级麻烦）、JitPack（不稳定）。
> 只做应用层玻璃化时 Maven 依赖足够。

### 1.3 环境要求

| 项 | 要求 | 备注 |
|---|---|---|
| minSdk | 33+ | 折射/色散依赖 AGSL RuntimeShader（Android 13+）；低版本走降级（无 shader 时高光退化为纯色） |
| Compose | 任意近期版本 | 本项目 BOM 2026.08.00（UI 1.12 / Material3 1.4）验证通过 |
| Kotlin / AGP | 2.4.x / 9.3.x | 库对编译器无特殊要求 |
| targetSdk | 建议 ≤35 | targetSdk 36+ 在 Android 16 强制预测式返回手势，会覆盖自定义页面转场（与玻璃无关但同期踩到） |

---

## 2. 核心概念与 API

### 2.1 Backdrop：背景源

```kotlin
// ① 图层录制源：记录宿主节点的绘制内容（含其子树），供他处采样
val backdrop = rememberLayerBackdrop()
Box(Modifier.layerBackdrop(backdrop)) { /* 被录制的内容 */ }

// ② 空背景源：不采样任何内容（配合表面色调做"静态玻璃"）
val empty = emptyBackdrop()

// ③ 合成背景源：把多个 Backdrop 叠加为一个（标签栏选中块 = 画布 + tint 内容层）
val combined = rememberCombinedBackdrop(backdropA, backdropB)
```

**铁律：采样方不得位于被录制图层的子树内**（详见 §9.1，违反会原生崩溃）。

### 2.2 drawBackdrop：玻璃渲染修饰器

一切玻璃效果的入口，签名要点：

```kotlin
Modifier.drawBackdrop(
    backdrop: Backdrop,              // 背景源
    shape: () -> Shape,              // 玻璃轮廓（胶囊/圆角/圆形）
    effects: BackdropEffectScope.() -> Unit,   // 特效管线（见 2.3）
    highlight: () -> Highlight?,     // 边缘高光
    shadow: () -> Shadow?,           // 外阴影
    innerShadow: () -> InnerShadow?, // 内阴影（按压时"浮起"感）
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,  // 每帧图层变换（按压缩放等）
    onDrawSurface: DrawScope.() -> Unit,       // 表面色调（画在采样内容之上）
)
```

`layerBlock` 是按压动效的核心注入点：在其中读写 `scaleX/scaleY/translationX/translationY`，
可实现玻璃随手指膨胀、位移、速度挤压。传 `null` 表示采样时不做层变换（§9.7 视差坑）。

### 2.3 特效管线（effects 内可用）

```kotlin
effects = {
    vibrancy()                    // 增饱和：采样内容提艳（iOS 同款）
    blur(radiusPx)                // 高斯模糊，单位 px（用 dp.toPx() 换算）
    lens(                         // 透镜折射
        refractionHeight,         // 折射高度 px：边缘"厚度"感
        refractionAmount,         // 折射强度 px：内容弯曲程度
        chromaticAberration = true, // 色散：边缘 RGB 分离彩虹纹
    )
    opacity(alpha)                // 采样内容整体透明度（0=看不见背景，1=全透）
}
```

**管线顺序即视觉效果顺序**，推荐固定为 `vibrancy → blur → lens → opacity`。

### 2.4 Highlight / Shadow / InnerShadow

```kotlin
highlight = { Highlight.Default.copy(alpha = 0.9f) }   // 顶部/边缘环境光
highlight = { Highlight.Ambient.copy(alpha = progress) } // 按压时出现的环形光
shadow = { Shadow(alpha = 0.1f) }                       // 常规外阴影
shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(0.05f)) }
innerShadow = { InnerShadow(radius = 6.dp * progress, alpha = progress) } // 按压"浮起"
```

按压动效套路：`progress`（0→1 按压进度）驱动——高光/阴影/内阴影从无到有、
模糊渐弱、折射渐强，松手回弹。见 §6 各控件。

---

## 3. 预设参数体系（GlassPreset）

把可调参数收敛为一个数据类，全应用共享一套预设，保证观感统一、支持档位切换：

```kotlin
data class GlassPreset(
    val opacity: Int,               // 采样透明度 0–100
    val blurRadiusDp: Int,          // 模糊半径 dp
    val refractionHeightDp: Int,    // 折射高度 dp
    val refractionAmountDp: Int,    // 折射强度 dp
    val chromaticAberration: Boolean, // 色散开关
    val highlightAlpha: Float,      // 高光 0–1
    val shadowAlpha: Float,         // 阴影 0–1
)
```

### 3.1 统一效果管线函数

```kotlin
fun BackdropEffectScope.presetEffects(preset: GlassPreset) {
    vibrancy()
    blur(preset.blurRadiusDp.dp.toPx())
    lens(
        refractionHeight = preset.refractionHeightDp.dp.toPx(),
        refractionAmount = preset.refractionAmountDp.dp.toPx(),
        chromaticAberration = preset.chromaticAberration,
    )
    opacity(lerp(0.55f, 1f, preset.opacity / 100f))
}
```

### 3.2 三档实用预设（Photofall 最终调校值）

| 档位 | 模糊半径 | 共同参数 |
|---|---|---|
| 通透 | 1dp | 透明度 100、折射高度 12、强度 16、色散开、高光 0.9、阴影 0.1 |
| 均衡（默认） | 3dp | 同上 |
| 磨砂 | 24dp | 同上 |

> 调校结论：**透明度拉满（100）+ 高光 0.9 + 阴影 0.1** 得到干净的 iOS 质感；
> 三档只动模糊半径，用户感知清晰。磨砂档（24dp）下必须给选中类玻璃禁用叠加模糊（§9.6）。

### 3.3 表面色调（onDrawSurface）

玻璃不能只靠采样——采样内容之上要叠一层半透明近白/近黑，否则深色背景上"隐形"：

```kotlin
val isDark = /* 应用真实深浅，见 §8.2，勿用 isSystemInDarkTheme() */
val overlayColor = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)
// 常规玻璃：浓度随透明度参数增减
drawRect(overlayColor, alpha = lerp(0.25f, 0.55f, preset.opacity / 100f))
// 静态玻璃（无采样内容）：浓度更高维持质感
drawRect(overlayColor, alpha = lerp(0.45f, 0.75f, preset.opacity / 100f))
```

---

## 4. 三种背景源模式（关键架构决策）

**按"玻璃控件与内容的空间关系"选择背景源**，这是全库使用中最重要的决策：

### 4.1 内容画布页：录制内容图层（编辑页模式）

页面中央有真实绘制内容（照片画布/地图/图片编辑器），玻璃浮层悬在其上：

```kotlin
val glassBackdrop = rememberLayerBackdrop()

Box(Modifier.fillMaxSize()) {
    // 画布：被录制的兄弟节点（玻璃不在此子树内！）
    Box(Modifier.layerBackdrop(glassBackdrop)) {
        // ★ 第一层先铺全屏纯色（遮盖按压形变时的采样错位，见 §9.5）
        Canvas(Modifier.fillMaxSize()) { drawRect(surfaceContainerHighest) }
        Canvas(Modifier.fillMaxSize()) { drawContent() }   // 真实内容
    }
    // 玻璃浮层：与画布互为兄弟
    GlassTopBar(backdrop = glassBackdrop, ...)
    GlassBottomBar(backdrop = glassBackdrop, ...)
}
```

### 4.2 无画布页：纯色背景源（设置页/主页/表单页模式）

页面只有普通 Compose 控件、没有可供采样的"画面"。录制一个全屏纯色层即可让玻璃
有真实采样对象（比 emptyBackdrop 质感好）：

```kotlin
@Composable
fun rememberPlainGlassBackdrop(color: Color): Pair<Backdrop, Modifier> {
    val backdrop = rememberLayerBackdrop()
    val modifier = Modifier.layerBackdrop(backdrop).background(color)
    return backdrop to modifier
}

// 使用：铺底层在玻璃控件之下（兄弟关系）
val (backdrop, backdropModifier) = rememberPlainGlassBackdrop(colorScheme.surface)
Box(Modifier.fillMaxSize()) {
    Box(backdropModifier.fillMaxSize())   // 背景源
    /* 顶栏、卡片等玻璃控件叠在其上 */
}
```

### 4.3 重叠场景：静态玻璃（懒加载列表卡片模式）

玻璃与被采样内容**位置重叠**（玻璃卡片就在网格/列表项内部）时，任何图层录制都会
自引用 → 原生崩溃（§9.1）。此时放弃采样，用 `emptyBackdrop` + 高浓度表面色调：

```kotlin
Modifier.staticGlass(preset, shape)   // 见 §5.2
```

### 4.4 决策速查

| 场景 | 方案 |
|---|---|
| 玻璃悬浮在画布/内容之上（兄弟节点） | 4.1 录制内容图层 |
| 普通页面（设置/主页/表单） | 4.2 纯色背景源 |
| 玻璃在懒加载列表项内部 / 与内容重叠 | 4.3 staticGlass |
| 标签栏选中块 = 画布 + 标签 tint 内容 | rememberCombinedBackdrop（§6.5） |

---

## 5. 基础修饰器：liquidGlass / staticGlass

### 5.1 liquidGlass（常规采样玻璃）

```kotlin
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    preset: GlassPreset,
    shape: () -> Shape = { RoundedCornerShape(percent = 50) },  // 默认胶囊
): Modifier {
    val alpha = preset.opacity / 100f
    val overlayColor = if (rememberAppInDarkTheme()) Color(0xFF121212) else Color(0xFFFAFAFA)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = { presetEffects(preset) },
        highlight = { Highlight.Default.copy(alpha = preset.highlightAlpha) },
        shadow = { Shadow(alpha = preset.shadowAlpha) },
        onDrawSurface = { drawRect(overlayColor, alpha = lerp(0.25f, 0.55f, alpha)) },
    )
}
```

用途：顶栏标题胶囊、卡片容器、输入框、页签容器、滑杆轨道/拇指等一切"静态玻璃面"。

### 5.2 staticGlass（无采样静态玻璃）

```kotlin
@Composable
fun Modifier.staticGlass(
    preset: GlassPreset,
    shape: () -> Shape = { RoundedCornerShape(percent = 50) },
): Modifier {
    val overlayColor = if (rememberAppInDarkTheme()) Color(0xFF121212) else Color(0xFFFAFAFA)
    return this.drawBackdrop(
        backdrop = emptyBackdrop(),
        shape = shape,
        effects = {},                       // 无内容可处理
        highlight = { Highlight.Default.copy(alpha = preset.highlightAlpha) },
        shadow = { Shadow(alpha = preset.shadowAlpha) },
        onDrawSurface = { drawRect(overlayColor, alpha = lerp(0.45f, 0.75f, preset.opacity / 100f)) },
    )
}
```

---

## 6. 控件实现详解

以下控件全部基于三个共用底座（§7）：`DampedDragAnimation`（值动画+按压进度+挤压）、
`InteractiveHighlight`（触点高光）、`inspectDragGestures`（拖拽手势检测）。

### 6.1 LiquidButton / LiquidIconButton（胶囊按钮 / 圆形图标按钮）

结构：`drawBackdrop`（含按压缩放 layerBlock）+ `clickable/combinedClickable` + 触点高光。

```kotlin
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    preset: GlassPreset,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,     // 支持长短按组合手势
    content: @Composable () -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val (surfaceColor, surfaceAlpha) = rememberGlassSurface(preset)

    Box(
        modifier
            .size(44.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsuleShape() },               // RoundedCornerShape(percent = 50)
                effects = { presetEffects(preset) },
                highlight = { Highlight.Default.copy(alpha = preset.highlightAlpha) },
                shadow = { Shadow(alpha = preset.shadowAlpha) },
                layerBlock = {
                    // 按压放大 4dp 等效比例
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height,
                        interactiveHighlight.pressProgress)
                    scaleX = scale; scaleY = scale
                },
                onDrawSurface = { drawRect(surfaceColor, alpha = surfaceAlpha) },
            )
            .combinedClickable(interactionSource = null, indication = null,
                enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .then(interactiveHighlight.modifier)          // 触点高光绘制
            .then(interactiveHighlight.gestureModifier),  // 按压手势追踪
        contentAlignment = Alignment.Center,
    ) { content() }
}
```

要点：
- `interactionSource = null, indication = null`：去掉 MD 涟漪，玻璃高光就是反馈；
- 胶囊按钮（LiquidButton）同款结构，`layerBlock` 中额外做**跟手位移**
  （`translationX/Y = maxOffset * tanh(0.05 * offset / maxOffset)`）与**方向性挤压**
  （scaleX/Y 随触点角度增减），得到"果冻拖拽"手感；
- 图标禁用态：内容 tint 降透明度（`onSurface.copy(alpha = 0.38f)`）+ `enabled = false`。

### 6.2 LiquidToggle（玻璃开关）

结构：着色轨道（`layerBackdrop` 录制）+ 玻璃滑块（采样**轨道合成背景**）。

```kotlin
val trackBackdrop = rememberLayerBackdrop()
Box(modifier, contentAlignment = Alignment.CenterStart) {
    // 轨道：灰 → 主题色渐变（随开合进度 lerp）
    Box(Modifier.layerBackdrop(trackBackdrop).clip(capsule)
        .background(lerp(trackColor, accent.copy(0.75f), progress))
        .size(64.dp, 28.dp))
    // 滑块：Combined(外部背景, 轨道)，按压时"从轨道浮起"
    Box(Modifier
        .graphicsLayer { translationX = lerp(2dp, 22dp, progress) }
        .then(dampedDragAnimation.modifier)          // 拖拽手势
        .drawBackdrop(
            backdrop = rememberCombinedBackdrop(backdrop,
                rememberBackdrop(trackBackdrop) { drawBackdrop ->
                    // 按压时轨道在滑块内被压扁显现（浮起错觉）
                    val p = dampedDragAnimation.pressProgress
                    scale(lerp(2f/3f, 0.75f, p), lerp(0f, 0.75f, p)) { drawBackdrop() }
                }),
            effects = {
                val p = dampedDragAnimation.pressProgress
                blur(8.dp.toPx() * (1f - p))          // 常态模糊 → 按压清透
                lens(5.dp.toPx() * p, 10.dp.toPx() * p, chromaticAberration = true)
            },
            highlight = { Highlight.Ambient.copy(alpha = pressProgress, ...) },
            innerShadow = { InnerShadow(radius = 4.dp * p, alpha = p) },
            layerBlock = { /* 按压缩放 + 速度挤压，见 §7.1 */ },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - p)) },
        )
        .size(40.dp, 24.dp))
}
```

交互语义（DampedDragAnimation 回调内）：
- **拖动**：`fraction += dragAmount.x / dragWidth`，松手按 `≥0.5` 吸附并提交；
- **点按**：直接取反提交；
- 外部状态变化经 `snapshotFlow { latestSelected() }` 同步动画（勿直接捕获参数，§9.2）。

### 6.3 LiquidSlider（玻璃滑杆）

结构同 Toggle：录制轨道（背景槽 + 主题色进度条）+ 玻璃拇指。核心是**点按/拖动区分**：

```kotlin
var pressX by remember { mutableFloatStateOf(0f) }     // 按下位置
var accumX by remember { mutableFloatStateOf(0f) }     // 累计位移
val touchSlopPx = 8.dp.toPx()                          // 阈值

DampedDragAnimation(
    onDragStarted = { position -> pressX = position.x; accumX = 0f },
    onDrag = { _, dragAmount ->
        accumX += dragAmount.x
        if (!didDrag) {
            if (abs(accumX) <= touchSlopPx) return@DampedDragAnimation  // 阈值内视为点按
            didDrag = true
        }
        onValueChange(targetValue + delta)             // 超阈值：跟手
    },
    onDragStopped = {
        if (didDrag) onValueChange(targetValue)
        else {                                          // 点按：动画滑到触点（对齐系统 Slider）
            val target = valueAt(pressX / trackWidthPx)
            animateToValue(target)                      // 接收者即动画实例自身
            onValueChange(target)
        }
    },
)
```

拇指按压效果同 Toggle（模糊→折射过渡 + 从轨道浮起）。`value` 提供器必须经
`rememberUpdatedState` 中转（§9.2）。

### 6.4 LiquidBottomTabs（底部导航栏）——三层结构

**demo 原版三层结构，缺一不可**：

```kotlin
BoxWithConstraints(modifier) {
    val tabWidth = (constraints.maxWidth - 8.dp.toPx()) / tabsCount

    // ── 第 1 层：玻璃胶囊容器 + 可点击标签 ──
    Row(Modifier.graphicsLayer { translationX = panelOffset }
        .drawBackdrop(backdrop, capsule, effects = { presetEffects(preset) },
            highlight, shadow,
            layerBlock = { /* 按压时整体放大 16dp 等效 */ },
            onDrawSurface = { drawRect(containerColor) })   // 近白/近黑 40%
        .then(interactiveHighlight.modifier)
        .height(64.dp).fillMaxWidth().padding(4.dp),
    ) { content() }                                        // 各 LiquidBottomTab

    // ── 第 2 层：隐藏重复层（选中块内容源）──
    CompositionLocalProvider(LocalLiquidBottomTabScale provides { ... }) {
        Row(Modifier.clearAndSetSemantics { }
            .alpha(0f)                                     // 不可见但参与录制
            .layerBackdrop(tabsBackdrop)                   // 录制标签内容
            .drawBackdrop(...同容器..., onDrawSurface = { drawRect(containerColor) })
            .height(56.dp).fillMaxWidth().padding(horizontal = 4.dp)
            .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),  // 主题色 tint
        ) { content() }                                    // 同一 content 再渲染一遍
    }

    // ── 第 3 层：移动选中玻璃块 ──
    Box(Modifier.padding(horizontal = 4.dp)
        .graphicsLayer { translationX = index * tabWidth + panelOffset; alpha = indicatorAlpha }
        .then(if (gesturesEnabled) interactiveHighlight.gestureModifier else Modifier)
        .then(if (gesturesEnabled) dampedDragAnimation.modifier else Modifier)
        .drawBackdrop(
            backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),  // 画布 + tint 内容
            effects = {
                val p = pressProgress
                lens(10.dp.toPx() * p, 14.dp.toPx() * p, chromaticAberration = true)
                // ★ 不要叠加 blur(preset)——见 §9.6
            },
            highlight = { Highlight.Default.copy(alpha = p) },
            shadow = { Shadow(alpha = p) },
            innerShadow = { InnerShadow(radius = 8.dp * p, alpha = p) },
            layerBlock = { /* 按压缩放 + 速度挤压 */ },
            onDrawSurface = { /* 常态 10% 黑/白罩，按压渐隐 */ },
        )
        .height(56.dp).fillMaxWidth(1f / tabsCount))
}
```

选中块拖拽（DampedDragAnimation 驱动，值为 0..count-1 连续索引）：
- `onDrag`：`updateValue(target + dx / tabWidth)`，同时容器 `panelOffset` 跟随少量位移
  （拖出边界的"拉拽面板"感）；
- `onDragStopped`：吸附最近索引 → `animateToValue` → `onTabSelected(index)` 提交。

### 6.5 LiquidSelectTabs（功能区页签栏）——BottomTabs 的适配版

与 6.4 完全同构，仅三点差异：

1. **可空选中**：`selectedIndex: () -> Int?`，null 时选中块 `indicatorAlpha` 渐隐；
2. **圆形选中块**（图标页签）：容器高 44dp、上下左右留白各 3dp、选中块高 38dp——
   **图标页签宽必须同为 38dp**（容器宽 = 数量×38+6），选中块才呈正圆；
   文本页签则自适应宽度（容器 fillMaxWidth）；
3. **项目定制**（消除重影，见 §9.7/§9.8）：
   - 选中块采样传 `layerBlock = null`（不做逆变换，避免与标签自身缩放原点不一致的视差）；
   - 选中块的按压放大/速度挤压施加在**外层 graphicsLayer**（作用于整体渲染而非采样）；
   - 第 2 层布局/层变换与第 1 层严格一致（含按压膨胀），tint 副本才与原件对齐；
   - 被选中块覆盖的第 1 层标签按重叠度淡出（`LocalLiquidSelectedValue` 连续值下发，
     标签只以玻璃内主题色副本呈现，避免"原色原件 + tint 副本"双层叠影）。

### 6.6 页面骨架与容器类组件

```kotlin
// 玻璃子页骨架：纯色背景源 + 玻璃顶栏 + 滚动内容（内部提供 LocalGlassContext）
GlassPageScaffold(title, backdropSource, preset, onBack) { /* 卡片列表 */ }

// 玻璃顶栏：返回按钮 + 标题玻璃胶囊
Row(Modifier.fillMaxWidth().statusBarsPadding().padding(h = 12.dp, v = 8.dp)) {
    LiquidIconButton(onClick = onBack, backdrop, preset) { Icon(ArrowBack, ...) }
    Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(44.dp)
        .liquidGlass(backdrop, preset, shape = { RoundedCornerShape(22.dp) }),
        contentAlignment = Alignment.Center,
    ) { Text(title, color = colorScheme.onSurface) }   // ★ 显式着色
}

// 分区卡片：玻璃圆角容器（20dp）+ 20dp 内边距
Column(Modifier.fillMaxWidth()
    .liquidGlass(backdrop, preset, shape = { RoundedCornerShape(20.dp) })
    .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { ... }

// 数值调整统一行：图标/预览 + 滑杆(weight) + 当前数值
Row(Modifier.fillMaxWidth(), spacedBy(16.dp), CenterVertically) {
    icon(); Slider(Modifier.weight(1f)); Text(format(value))
}

// 玻璃输入框：胶囊容器 + BasicTextField
Box(Modifier.fillMaxWidth()
    .liquidGlass(backdrop, preset, shape = { RoundedCornerShape(16.dp) })
    .padding(horizontal = 16.dp, vertical = 10.dp)) {
    BasicTextField(value, onValueChange,
        textStyle = bodyMedium.copy(color = colorScheme.onSurface),   // ★ 显式着色
        cursorBrush = SolidColor(colorScheme.primary), maxLines = 3)
}
```

---

## 7. 手势与动画底座

### 7.1 DampedDragAnimation（值动画 + 按压进度 + 挤压 + 速度弹性）

一个类管理玻璃控件的全部动效状态，`remember(scope)` 只创建一次：

```kotlin
class DampedDragAnimation(
    animationScope, initialValue, valueRange, visibilityThreshold,
    initialScale, pressedScale,
    onDragStarted: (position) -> Unit,
    onDragStopped: DampedDragAnimation.() -> Unit,   // 可读 targetValue、调 animateToValue
    onDrag: (size, dragAmount) -> Unit,
) {
    val value: Float          // 当前动画值（弹簧）
    val pressProgress: Float  // 按压进度 0..1（驱动折射/高光/内阴影）
    val scaleX / scaleY       // 按压缩放（pressedScale，如 78/56≈1.39）
    val velocity              // 速度弹性（松手后挤压回弹）
    val modifier              // 挂到节点即获得完整拖拽手势
    fun press() / release() / updateValue(v) / animateToValue(v)
}
```

**速度挤压**（layerBlock 内，所有移动玻璃通用）：

```kotlin
layerBlock = {
    scaleX = animation.scaleX; scaleY = animation.scaleY
    val v = animation.velocity / 10f
    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)   // 运动方向拉长
    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)   // 垂直方向压扁
}
```

### 7.2 InteractiveHighlight（触点高光）

按压时在触点处渲染跟随手指的径向高光（AGSL RuntimeShader  smoothstep 衰减；
无 shader 支持时退化为整体提亮）：

```kotlin
val highlight = remember(scope) { InteractiveHighlight(scope, position = { size, offset -> ... }) }
Modifier.then(highlight.modifier)         // drawWithContent：Plus 混合白色径向光
       .then(highlight.gestureModifier)   // inspectDragGestures 驱动 pressProgress/offset
```

标签栏中 `position` 传选中块中心，实现"光从选中块里透出"。

### 7.3 inspectDragGestures（拖拽手势检测）

按下即开始、支持手指换接、区分结束与取消的通用检测器（demo DragGestureInspector 移植）。
**关键：drag 循环内每帧 `change.consume()`**——否则外层滚动容器会抢走位移（§9.4）。

---

## 8. 双风格架构（玻璃 / Material 实时切换）

### 8.1 LocalGlassContext：控件形态自动分支

```kotlin
// 非空 = 玻璃风格（背景源 + 预设）；null = Material 风格
val LocalGlassContext = staticCompositionLocalOf<Pair<Backdrop, GlassPreset>?> { null }

// 页面骨架 provide：
CompositionLocalProvider(LocalGlassContext provides (backdrop to preset)) { ... }

// 任意控件内部自动分支：
val glass = LocalGlassContext.current
if (glass != null) LiquidSelectTabs(..., backdrop = glass.first, preset = glass.second)
else SingleChoiceSegmentedButtonRow { ... }   // MD3 原样
```

配合 DataStore 热更新的 `uiStyle` 设置项，切换风格 = 一次重组，无需重启。

### 8.2 LocalAppDarkTheme：玻璃色调跟随应用主题（勿用系统深浅）

玻璃表面色调必须与**应用实际主题**一致，而不是系统深浅——应用可能强制浅色/深色，
或关闭动态取色用品牌配色，此时 `isSystemInDarkTheme()` 与真实主题脱节，
出现"深色玻璃 + 黑字"不可读：

```kotlin
val LocalAppDarkTheme = staticCompositionLocalOf { false }

// 主题层统一 provide（darkTheme 由用户设置计算：白→false / 黑→true / 跟随系统→系统值）
CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) { content() }

@Composable fun rememberAppInDarkTheme() = LocalAppDarkTheme.current
```

所有玻璃组件（修饰器、滑杆轨道、开关轨道、标签栏容器）统一读它。

### 8.3 文本着色规范

玻璃容器**不是 Surface**，不提供内容色：卡片/胶囊内的 `Text`、`BasicTextField`
必须显式指定 `color = colorScheme.onSurface`（副文本 `onSurfaceVariant`），
否则继承色在深色玻璃上不可见。

---

## 9. 踩坑清单与最佳实践（必读）

### 9.1 ★ 图层自引用 → 原生崩溃（最严重）

**现象**：`libhwui.so` 原生崩溃，`prepareTreeImpl/prepareListAndChildren` 数百层递归栈溢出，
无 Kotlin 异常。

**根因**：用 `layerBackdrop` 录制了容器（如 LazyVerticalGrid），又在**其子树内**
（列表项）用 `drawBackdrop` 采样同一图层——RenderNode 显示列表循环引用，渲染同步无限递归。

**规则**：采样方与被录制图层必须是**互不包含的兄弟层级**。玻璃位于被采样容器内部时
（尤其懒加载列表项），只能用 `staticGlass`（emptyBackdrop）。
任何"录制图层供他人采样"的机制都适用此规则。

### 9.2 ★ 闭包过期（本项目五次踩中，最高频）

**现象**：控件交互失效但无报错——滑杆点按/拖动无反应、页签点击后选中块不动
（看似"点不动"）、开关只能朝一个方向切换（能关不能开）。

**根因**：把"值提供器/回调"以普通 lambda 参数传给**只创建一次**的内部实例
（`remember(scope) { DampedDragAnimation(...) }`、`LaunchedEffect(Unit) { snapshotFlow { ... } }`），
闭包捕获首次组合的参数副本；重组产生的新 lambda 永远进不去。
最隐蔽变体：调用方 lambda 捕获组合期局部值（如 `{ if (filled) 0 else 1 }`），
点击确实生效、状态确实变了，但组件内部快照流永远读初始值。

**修复（组件层根治，调用方免疫）**：

```kotlin
val latestSelectedIndex by rememberUpdatedState(selectedIndex)
val latestOnTabSelected by rememberUpdatedState(onTabSelected)
LaunchedEffect(stableKey) {                       // key 用稳定实例，勿用会重建的 lambda
    snapshotFlow { latestSelectedIndex() }...     // 流内只读委托
    latestOnTabSelected(index)
}
```

**规则**：凡向"内部只启动一次的协程/流/动画实例"传递会随重组变化的值，
一律经 `rememberUpdatedState` 中转；在组件内部统一处理，不依赖调用方自觉。

### 9.3 选中块拦截点击 → "再点当前项无响应"

**现象**：点击**未被选中块覆盖**的页签正常，点击**已选中**的页签只出按压特效、无业务响应。

**根因**：Compose 指针分发为深层优先、同级后渲染者优先；选中块（第 3 层，后渲染）
覆盖当前页签，其手势层先消费 down，第 1 层页签的 `clickable` 拿不到未消费的 down。

**修复**：给标签栏加 `onCurrentTabClick` 参数，由选中块手势检测**无位移点按**
（累计位移 < 8dp）后转发"再点当前项"语义（如再点收起面板/取消选择）：

```kotlin
var pressDragDistance by remember { mutableFloatStateOf(0f) }
onDragStarted = { pressDragDistance = 0f }
onDrag = { _, d -> pressDragDistance += d.getDistance(); ... }
onDragStopped = {
    ...吸附提交...
    if (pressDragDistance < 8.dp.toPx()) latestCurrentTabClick?.invoke()
}
```

### 9.4 无选中态时透明选中块拦截首个页签

选中块 `alpha = 0`（无选中项）时仍停在索引 0 上方拦截点击。
修复：`gesturesEnabled = indicatorAlpha > 0f`，条件挂载手势修饰器。

### 9.5 按压形变采样错位 → 录制图层内铺纯色底

**现象**：按压导航类按钮时底层内容错位伪影；有图片的区域正常，空白区暴露错位。

**根因**：按压时控件层形变（scale/translate）而采样内容与录制图层错位；
采样区无实体绘制内容的区域暴露伪影。

**修复（唯一有效）**：在**被录制图层内部**、主绘制 Canvas 之前铺一个全屏实体纯色层
（`Canvas(fillMaxSize) { drawRect(surfaceContainerHighest) }`）。
注意录制层**外部**的 `background` 不参与采样、无效。
已验证无效的方向（勿再尝试）：调阴影/高光参数、裁剪三层结构。

### 9.6 选中块叠加模糊 → 内容涂成雾面

透明度 100 + 模糊半径 > 0 时，选中块 `effects` 若含 `blur(preset.blurRadiusDp * (1-p))`，
块内主题色图标副本被涂成雾面。修复：选中块**只保留按压时的 lens 折射**，
整体雾感由容器层 `presetEffects` 提供（与底部导航一致）。

### 9.7 采样层变换视差 → 重影

选中块采样合成背景时若施加"以玻璃自身为中心"的按压逆变换（layerBlock 内 inverse），
与"以标签自身为中心"的按压膨胀原点不一致，产生逐帧视差重影。
修复：选中块采样传 `layerBlock = null`；玻璃本体的放大施加在**外层 graphicsLayer**
（作用于整体渲染，不进采样管线）。

### 9.8 双层内容叠影 → 被覆盖标签淡出

选中块内 tint 副本与第 1 层原色原件同时可见（无图片背景时尤明显）。
修复：下发选中动画连续值（-1 = 无选中），第 1 层标签按与选中块的重叠度淡出，
只保留玻璃内的主题色副本。

### 9.9 自绘拖拽控件必须消费位移

玻璃滑杆/标签栏放在可滚动行内时，滚动父容器抢先消费位移 → 控件拖不动。
修复：拖拽循环内每帧 `change.consume()`；同时玻璃模式的选项行直接禁用
`horizontalScroll`（内容不滚动即无冲突）。画布类手势还应在按下点落入
悬浮控件区域时提前放弃（交给控件）。

### 9.10 点击路径双提交守卫

点击页签时：标签自身 `onClick` 已触发一次业务处理；若选中块同步流再提交一次
`onTabSelected(index)`，对"重点 = 切换"语义的回调（再点收起面板）会导致面板开了又关。
修复：`onTabSelected = { i -> if (latestSelected != i) onSelect(i) }`。

### 9.11 其他小坑

- **Canvas 的 DrawScope 不是 @Composable 上下文**：`MaterialTheme.colorScheme.*`
  必须在 Canvas 外读取后以局部值传入绘制块；
- **weight/fillMaxWidth 在水平滚动容器内失效**（无界宽度）：统一行组件按风格分支——
  玻璃（不滚动）用 `weight(1f)`，MD3（滚动）用固定宽度；
- **DataStore 多实例闪退**：仓库必须进程级单例（Activity 重建时二次 create 同文件会崩）。

---

## 10. 参数调节参考

库全量可调项（可做一个"试验页"逐项调试，本项目 GlassLabParams 22 字段）：

| 分组 | 参数 | 建议范围 | 说明 |
|---|---|---|---|
| 采样 | opacity | 0–100 | 映射到管线 `opacity(lerp(0.55,1,x))`；100 = 内容全透 |
| 模糊 | blurRadius | 0–32dp | 0–3 通透、8–16 半磨砂、24+ 磨砂 |
| 折射 | refractionHeight | 8–16dp | 边缘厚度感 |
| 折射 | refractionAmount | 10–20dp | 内容弯曲程度，过大会晕 |
| 折射 | chromaticAberration | bool | 色散彩虹边，开启更"液态" |
| 高光 | Highlight.Default.alpha | 0.3–0.9 | 环境光边 |
| 阴影 | Shadow.alpha | 0.05–0.3 | 常规投影 |
| 表面色调 | overlay alpha | 0.25–0.55 | 近白/近黑罩浓度（静态玻璃 0.45–0.75） |
| 动效 | pressedScale | 1.3–1.5 | 选中块按压缩放（78/56≈1.39） |
| 动效 | 按压弹簧 | spring(dampingRatio 0.5–1, stiffness 250–1000) | 值动画硬、视觉动画软 |

三档成品预设见 §3.2。调校方法论：先固定透明度 100 与折射 12/16，
只扫模糊半径找档位感，再统一微调高光/阴影。

---

## 附：文件组织建议

```
ui/
├── components/
│   ├── LiquidGlass.kt        # liquidGlass/staticGlass/presetEffects/背景源/LocalAppDarkTheme
│   └── PageScaffolds.kt      # 玻璃页骨架、顶栏、分区卡片
└── glass/
    ├── GlassGesture.kt       # inspectDragGestures（含 consume）
    ├── DampedDragAnimation.kt# 值动画 + 按压进度 + 挤压 + 速度
    ├── InteractiveHighlight.kt # 触点径向高光
    └── LiquidComponents.kt   # Button/IconButton/Toggle/Slider/BottomTabs/SelectTabs
```

移植顺序建议：底座三件套 → liquidGlass 修饰器 → IconButton（验证采样与按压）→
Slider/Toggle（验证 DampedDragAnimation）→ BottomTabs/SelectTabs（验证三层结构），
每步真机检查 §9 清单对应项。
