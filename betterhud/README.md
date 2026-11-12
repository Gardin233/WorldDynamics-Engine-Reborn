# WDER 心情条 BetterHud 集成说明

本集成使用 WDER 的 PlaceholderAPI 扩展 `%wder_mood%`，返回计入当前期望修正后的有效心情值，范围为 0～100。
配置采用 BetterHud 2.x 的 `images`、`layouts`、`huds` 格式。

## 安装步骤

1. 安装 PlaceholderAPI 和 BetterHud，并编译 WDER，将生成的插件 JAR 放入服务端插件目录。
2. 将 `placeholders/wder_mood_value.yml` 复制到 BetterHud 的 `placeholders` 目录；再将 `images/wder_mood.yml`、`layouts/wder_mood.yml` 和 `huds/wder_mood_hud.yml` 分别复制到对应配置目录。
3. 将 `assets/mood/` 中的三张 PNG 图片复制到 BetterHud 的 `assets/mood/` 目录，保持文件名不变。
4. 在 BetterHud 的 HUD 配置中，将 `wder_mood_hud` 加入现有的 `default-hud` 列表。保留列表中的其他 HUD 项。
5. 重新构建 BetterHud 资源包，并将其 `build` 输出合并到服务端下发的资源包中。若使用 CraftEngine，请将 BetterHud 输出与现有 CraftEngine 资源包一并合入最终资源包。
6. 根据所用版本的要求重启或重载 BetterHud，并确认客户端能够收到合并后的资源包。

心情条与 BetterHud 默认饥饿条使用相同的屏幕锚点（`x: 50`、`y: 100`），并放置在 `y: -39`；默认饥饿条位于 `y: -24`，因此心情条显示在其上方。若服务器自定义了饥饿条布局，请调整 `layouts/wder_mood.yml` 中的 `x` 和 `y` 以匹配实际位置。

图标状态依次为：空、半格、满格。BetterHud 数值占位符将心情值划分为 20 个半格等级，图标堆叠显示 10 格，因此每 5 点心情变化对应半格变化。堆叠表达式使用 `number:wder_mood_value` 引用数值变量。本集成不会覆盖 BetterHud 中已有的配置文件。

## 占位符

- `%wder_mood%`：当前有效心情值，整数范围为 0～100。
