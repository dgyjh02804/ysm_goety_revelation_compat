# YSM × GoetyRevelation 兼容 Mod

桥接 [YSM (Yes Steve Model)](https://ysm.cfpa.team/) 和 GoetyRevelation (终末之环) 的渲染兼容性。

## 问题

YSM 通过 Mixin 在 `EntityRenderDispatcher.render()` 级别拦截玩家渲染，绕过原版 `PlayerRenderer`。GoetyRevelation 的 `PlayerHaloLayer`（Ascension Halo / Broken Halo）和 RevelationFix 的 `OdamaneHaloLayer`（halo_of_the_end，通过 CuriosLayer 渲染）依赖原版渲染管线，YSM 会跳过这些图层，导致光环不显示或显示在错误位置。

## 解决方案

使用 Forge `RenderLivingEvent.Post` 事件 + Mixin `@Accessor` 在 YSM 渲染完成后手动重放光环图层。

- **仅一个 Accessor Mixin** — 暴露 `LivingEntityRenderer` 的 `layers` 和 `model` 字段，不修改任何逻辑
- **只渲染光环层** — 跳过手柄物品、盔甲、披风等 YSM 已处理的层，避免双重渲染
- **每种光环独立配置** — `ascension_halo`、`broken_halo`、`halo_of_the_end` 各有独立的 X/Y/Z 位置偏移和旋转角度
- **Buffer 刷新修复** — `OdamaneHaloLayer` 内部使用主 BufferSource 渲染但不调用 `endBatch()`，兼容层在所有光环渲染后显式提交缓冲区

## 构建

### 前置条件
- JDK 17
- 网络连接（首次构建需下载 ForgeGradle 和 Minecraft Forge 1.20.1）

### 步骤

```bash
./gradlew build
```

构建产物：`build/libs/ysm_goety_revelation_compat-1.0.0.jar`

## 使用

将以下 Mod 放入 Minecraft 1.20.1 Forge 的 `mods/` 文件夹：

1. Goety（GoetyRevelation 的前置依赖）
2. GoetyRevelation
3. YSM (Yes Steve Model)
4. ysm_goety_revelation_compat（本 Mod）
5. 其他前置：EndingLibrary、Patchouli、Curios

### 配置

进游戏 → Mods → 找到 YSM GoetyRevelation 兼容 → Config（齿轮图标）→ 每种光环可独立调整位置和旋转：

| 光环 | 位置 X/Y/Z (px) | 旋转 X/Y/Z (°) |
|---|---|---|
| `ascension_halo` | 0 / 30 / -8 | -90 / 0 / 0 |
| `broken_halo` | 0 / 30 / -8 | -90 / 0 / 0 |
| `halo_of_the_end` | 0 / 20 / -5 | 0 / 0 / 180 |

- 位置单位：像素（1 格 = 16 像素）。X = 左右、Y = 上下、Z = 前后
- 旋转单位：度。X = 俯仰、Y = 偏航、Z = 翻滚
- 范围：位置 -128 ~ 128，旋转 -180 ~ 180

## 技术架构

```
RenderLivingEvent.Post 触发
    │
    ├── 非 AbstractClientPlayer? → 跳过
    ├── renderer instanceof PlayerRenderer? → 跳过（原版已处理）
    │
    └── 只渲染光环相关层:
        ├── PlayerHaloLayer
        │   ├── hasBrokenHalo()? → 应用 broken_halo 配置
        │   └── hasHalo()?      → 应用 ascension_halo 配置
        └── CuriosLayer → OdamaneHaloLayer
            └── 应用 halo_of_the_end 配置
            │
            ├── setupAnim() 动画化 PlayerRenderer 模型（修复头部旋转跟踪）
            ├── 类型感知的位置偏移 + 旋转（每种光环独立可配）
            ├── 每个层独立 pushPose/popPose 隔离
            └── endBatch() 刷新主 BufferSource（修复终末之环不显示）
```

**光环类型识别：** `PlayerHaloLayer` 内部通过 `ATAHelper.hasHalo()` / `hasBrokenHalo()` 判断纹理。本 Mod 通过反射调用同一方法，在渲染前确定光环类型以应用正确的配置偏移。

**Buffer 刷新：** `OdamaneHaloLayer.render()` 创建独立 BufferSource 渲染光环几何体，但在非 shader / 非特效路径下不调用 `endBatch()`。兼容层在所有图层渲染完成后显式调用 `mc.renderBuffers().bufferSource().endBatch()` 提交缓冲区，确保终末之环可见。

## 作者

dgyjh02804

## 许可证

MIT License — 可自由使用、修改、分发。

## 致谢

- YSM (Yes Steve Model) 团队
- Goety / GoetyRevelation 团队
- RevelationFix 团队
