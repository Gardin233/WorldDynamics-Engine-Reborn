# Paper 兼容性与运行基线

> [Wiki 首页](README.md) · [服主使用文档](使用文档.md) · [插件机制说明](插件机制说明.md)

## 项目来源

万邦演变引擎：重生基于 [WorldDynamics-Engine v0.2.1 发布源码](https://github.com/stoleyourharvs/WorldDynamics-Engine/releases/tag/v0.2.1) 演进，主要面向 Paper 服务端。本地分支在该版本基础上继续扩展 Towny 国家治理、国家集团、主权帝国、战争与和约玩法。

## 当前构建目标

| 项目 | 当前目标 |
| --- | --- |
| 服务端 | Paper 1.21.11 |
| Java | Java 21 |
| Paper API | `1.21.11-R0.1-SNAPSHOT` |
| Towny | 运行依赖；构建使用项目 `pom.xml` 中锁定的 API 版本 |
| 经济 | 必须有可通过 Vault API 取得的经济服务 |

`plugin.yml` 声明 `api-version: 1.21`。IntelliJ 模块配置使用 Paper 平台。低于当前目标版本的服务端未作为本分支的兼容承诺。

## 依赖与可选集成

- Towny 和 Vault 经济服务是插件启动所需条件；缺少任一项时插件会停用。
- VaultUnlocked 用于经济 API 接入及可选的多币种能力，具体可用货币仍取决于已注册的经济提供者。
- AuraSkills、CraftEngine、RealisticSeasons 为可选集成，相关功能在依赖不可用时不启用。
- 项目构建声明与运行服务端的插件版本是两个不同层面；部署时应安装与目标 Paper 版本兼容的 Towny 和经济插件。

## Paper 专用事件

护甲戒律使用 Paper 的 `PlayerArmorChangeEvent`，并在玩家登录及物品损坏后补做状态判定。该实现以 Paper 为目标，不承诺在其他服务端软件上可用。

## 验证边界

- 本文记录项目当前构建目标和源码接入，不构成所有插件组合的实服兼容保证。
- Towny 的领土、居民归属、税收和战争扩展均可能影响实际事件顺序与行为；升级后应在测试服回归。
- 同时启用其他战争或药水效果插件时，应重点检查中立、领土保护、战争计分和同类型药水覆盖。
- 可选依赖缺失不会替代 Towny 或经济服务，也不会启用相应集成功能。
- 基线源码版本为 `v0.2.1`；后续本地改动及依赖版本以当前 `pom.xml`、资源配置和目标服务端实测结果为准。
