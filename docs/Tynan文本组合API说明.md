# Tynan 文本组合 API

WDE 已将 TynanArtSimulator 的文本组合核心改写为 Java，供后续事件、命令和其他行动模块调用。原项目 v1/v2 使用相同的组合引擎和官方数据；本模块迁入完整数据集，包括 92 个故事事件、8 种作品载体、规则包、双语词池、代词及品质表。

## 配置目录

插件会将默认文件导出到 `plugins/WorldDynamics-Engine-Reborn/lang/tynan/`。语言数据分别位于 `zh/` 与 `en/`，每份语言文件只保留对应语言的文本：

| 文件 | 用途 |
| --- | --- |
| `<语言>/core.yml` | 数据集版本、载体目录、规则包索引、代词、品质和处理流程。 |
| `<语言>/rulepacks/base.yml` | 基础、全局和描述根规则。 |
| `<语言>/rulepacks/carriers.yml` | 雕塑、家具、武器、棺材等载体规则。 |
| `<语言>/rulepacks/naming.yml` | 作品名称生成规则。 |
| `<语言>/word-pools.yml` | 该语言的随机词池与概念词库。 |
| `<语言>/tales/core-01.yml`–`core-09.yml` | 分片保存 81 个 core 故事事件，每份 9 个。 |
| `<语言>/tales/royalty.yml`、`biotech.yml`、`anomaly.yml`、`odyssey.yml` | 各扩展包的故事事件。 |
| `presets.yml` | 管理员配置的共享人物预设，默认为空。 |
| `presets/<uuid>.yml` | 每名玩家独立保存的人物预设，不会互相覆盖。 |

拆分后的事件文件仍使用原始事件 ID；组合器根据请求语言只加载对应文件夹，再按固定顺序合并配置。新增 core 事件时放入下一个分片，扩展包事件放入对应文件。各语言 YAML 保留模板占位符格式，例如 `[COLONIST_nameDef]`、`[image]`、`[artstyle_adj]`。修改配置后执行 `/wde reload`，组合器会重新加载。

升级时会把旧版 `art-gen.yml`、扁平分片及合并的 `rulepacks.yml` 迁移到 `zh/` 和 `en/`；会保留原文并按语言拆分，迁移成功后才移除旧文件。已存在的语言分片不会被迁移默认值覆盖。

## 组合文本

通过 `WorldDynamicsEngine#getTynanTextCompositionManager()` 获取服务。请求支持 `light`/`strict` 两种引擎模式、`auto`/`tale`/`taleless` 分支、中英文、画风、情绪、风格句选择、上下文开关、数量和随机种子。

```java
var manager = plugin.getTynanTextCompositionManager();
var request = new TynanCompositionRequest(
        "TornadoFromItem",
        "sculpture",
        Map.of(
                "COLONIST_nameFull", "Ada Vale",
                "COLONIST_nameDef", "Ada Vale",
                "DATE", "5500"),
        "zh",
        "light",
        "tale",
        1.0D,
        "auto",
        "auto",
        List.of(),
        true,
        3,
        "town-event-42");

List<TynanGeneratedText> descriptions = manager.compose(request);
```

种子非空时，同一请求会产生可复现的结果；数量限制为 1–20。可通过 `getTales(language, carrierId)`、`getCarriers(language)`、`getArtStyles(language)`、`getStyleClauses(language)` 和 `getConceptTones()` 查询可选项。`TaleOption` 同时包含原始槽位定义，便于行动模块构造输入。

## 人物预设

预设字段与原项目一致：姓名、称呼、代词和默认主体标记。玩家预设按 UUID 写入独立 YAML；设置一个预设为默认主体时，会清除同一玩家其他预设的默认标记。

```java
UUID playerId = player.getUniqueId();
manager.savePersonPreset(playerId,
        new TynanPersonPreset("Ada Vale", "艺术家", "she", true));

Map<String, String> slots = manager.applyPersonPreset(
        playerId, "Ada Vale", "COLONIST", "zh", Map.of());
```

`applyPersonPreset` 会填充角色名、称呼、代词、所有格和宾格槽位，返回值可直接传入 `TynanCompositionRequest`。`getPersonPresets`、`getDefaultPersonPreset` 和 `deletePersonPreset` 提供查询、默认预设读取和删除功能。

本次移植提供 Java API 和 YAML 数据，不包含原网页的 UI；后续行动模块可直接依赖该管理器生成描述或套用人物预设。
