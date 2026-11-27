# Tynan 文本生成器规则说明

本文说明 Tynan 文本组合器的资源加载顺序、艺术描述与随机事件任务两种生成流程，以及日期、姓名和模板占位符的规则。Java API 示例见 [Tynan 文本组合 API](Tynan文本组合API说明.md)。

## 资源目录

默认资源位于 `plugins/WorldDynamics-Engine-Reborn/lang/tynan/`。缺少文件时，插件首次加载会从 JAR 导出默认资源；修改后重载插件即可重新读取。

| 路径 | 内容 |
| --- | --- |
| `zh/core.yml`、`en/core.yml` | 载体、规则包索引、代词及基础元数据。 |
| `<lang>/rulepacks/*.yml` | 全局、载体和命名规则；新增规则包文件会按文件名排序载入。 |
| `<lang>/word-pools.yml` | 规则展开用的词池及别名。 |
| `<lang>/tales/*.yml` | 故事事件规则；除内置分片外，也会载入该目录下的其他 YAML 文件。 |
| `<lang>/event-quests.yml` | 随机文本事件的任务叙事模板。 |
| `calendar.yml` | 共用历法名称、年份范围和季节定义，季节名可分别设置中英文。 |
| `rimworld/namer-person-sophian.yml` | RimWorld Sophian 人名生成规则。 |
| `rimworld/<lang>/Names/`、`rimworld/<lang>/WordParts/` | 人名、姓氏和音节词表。 |

语言目录中的 `<lang>` 为 `zh` 或 `en`。组合器合并基础数据、规则包、词池、事件任务模板、历法和故事事件；同名普通配置项由后载入的资源覆盖，故事事件则合并列表。

## 艺术描述

普通 Tynan 描述通过 `TynanTextCompositionManager.compose(TynanCompositionRequest)` 生成。

1. `taleId` 和 `carrierId` 必须对应已加载的故事与载体。
2. `branch` 可设为 `tale`、`taleless` 或 `auto`。`auto` 在故事存在可用规则时以约 80% 概率采用故事分支。
3. `mode` 可设为 `light` 或 `strict`。`light` 分段组合载体、图像、情境、描述和可选风格句；`strict` 从 `r_art_description` 根规则递归展开，缺少根规则时退回 `light`。
4. `styleLevel` 控制风格句使用程度；`artStyle`、`conceptTone` 和 `styleClauseIds` 进一步限制风格、概念情绪和句式。
5. `count` 会限制在 1 到 20。种子非空时，同一请求、配置和种子会产生可复现结果。

模板按 `[TOKEN]` 形式展开。请求 `slots` 中的值优先用于对应占位符；其余占位符由故事、规则包和词池提供。未知且没有词池的占位符会展开为空字符串。规则递归有深度和重复次数上限，避免错误配置造成无限展开。

未显式提供 `DATE` 时，组合器会按历法配置和本次文本种子生成日期；显式提供的 `DATE` 会保留，不会被自动日期覆盖。

## 事件任务文本

事件任务文本与艺术描述分开配置，但复用同一个 `TynanTextComposer` 和种子式模板展开。入口为：

```java
generateRandomEventText(language, eventId, slots, seed)
```

`eventId` 必须与事件注册 ID 对应。当前 `mislead_enemies` 使用五段任务结构：

| 段落键 | 作用 |
| --- | --- |
| `opening` | 日期、来访者和事件开场。 |
| `problem` | 来访者面临的困境。 |
| `proposal` | 请求玩家执行的委托。 |
| `risk` | 接受委托后的威胁或后果。 |
| `terms` | 报酬和接受条件。 |

每段都是候选列表；生成器使用 `weight` 加权抽取一条，再按顺序组合为五段文本。每段至少要有一条当前语言可用的候选项，否则生成失败，不会静默输出缺段文本。模板权重建议使用大于零的有限数字；无效权重按默认权重处理。

`RandomTextEvent` 会把以下上下文传给模板：

| 占位符 | 内容 |
| --- | --- |
| `[PERSON]` | 由现有 `RimWorldPersonNameGenerator` 按玩家语言生成的姓名。 |
| `[TOWN]` | 事件目标城镇名。 |
| `[REWARD]` | 本次实际抽取并校验后的报酬摘要。 |
| `[DATE]` | 自动生成的本地化历法日期；也可由调用方显式传入覆盖。 |

新增文本事件时，需在中英文 `event-quests.yml` 中各增加相同的事件 ID 和五个段落。模板只负责叙事，事件是否开始、奖励发放和袭击效果仍由对应的 Java 事件逻辑控制。

## 历法配置

`calendar.yml` 的主要字段如下：

```yaml
calendar:
  name:
    zh: 环世界历
    en: RimWorld Calendar
  year-range:
    minimum: 5500
    maximum: 5505
  seasons:
    - id: spring
      name:
        zh: 春季（Aprimay）
        en: Aprimay
      days: 15
```

年份上下限均包含在可选范围内；若上下限顺序填反，生成器会自动按从小到大处理。生成时先在年份范围内均匀抽取年份，再按全年总天数均匀抽取日期，因此季节出现概率与该季节配置的天数成正比。每季 `days` 限制为 1 到 1000 天，全年长度由季节天数总和决定。

默认历法为 5500–5505 年、四季各 15 天：Aprimay、Jugust、Septober、Decembary。默认日期格式示例：

- 中文：`环世界历·5502年·夏季（Jugust）第8天`
- English: `RimWorld Calendar — Jugust 8, 5502`

季节名的 `zh`、`en` 可分别自定义；缺少一种语言名称时，会回退到另一种语言名称或季节 ID。没有有效季节配置时使用上述四季默认值。

## 姓名规则

姓名仍由 `RimWorldPersonNameGenerator` 生成，不在 `event-quests.yml` 中重复维护姓名池。规则位于 `rimworld/namer-person-sophian.yml`，姓名和音节词表按语言放在 `rimworld/en/`、`rimworld/zh/`。

默认男女规则都按权重 `3:2:2` 选择 `FIRST_LAST`、`FIRST_IMPERIAL`、`IMPERIAL_IMPERIAL`。帝国式词汇的音节数按配置权重 `4:2:0.2` 选择 2、3、4 个音节。调用方为生成器提供种子，因此相同语言和种子会生成相同姓名。

## 修改检查

修改规则后，检查所有占位符是否已提供，并确认双语任务 profile 都包含五个段落。测试可覆盖固定种子重复生成、语言回退、日期范围及中英文模板。完整组合 API 的请求字段和示例见 [Tynan 文本组合 API](Tynan文本组合API说明.md)。
