# WorldDynamicsEngine:Reborn

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![GitHub stars](https://img.shields.io/github/stars/Gardin233/WorldDynamics-Engine-Reborn)

**万邦演变引擎：重生（WDER）** 基于 [WorldDynamics-Engine v0.2.1 发布源码](https://github.com/stoleyourharvs/WorldDynamics-Engine/releases/tag/v0.2.1) 演进，主要面向 Paper 与 Towny 服务器。

WDER 为 Towny 城镇和国家增加治理、国家集团、主权帝国、外交、战争与战后条约规则。它不替代 Towny 的建镇、建国和领土保护。

## 项目链接

- [上游 v0.2.1 源码](https://github.com/stoleyourharvs/WorldDynamics-Engine/releases/tag/v0.2.1)
- [本项目源码](https://github.com/stoleyourharvs/WorldDynamics-Engine)
- [插件发布](https://github.com/stoleyourharvs/WorldDynamics-Engine/releases)
- [问题反馈](https://github.com/stoleyourharvs/WorldDynamics-Engine/issues)

## 主要玩法

- 国家和城镇政体、职位任命、选举、税制与征召兵。
- 贸易联盟、军事同盟、意识形态联盟和分级主权帝国。
- 帝国自治、卫戍国、贡税、参战义务及独立路径。
- 战争理由、击杀与占领计分、停战期、单独谋和及可执行和平条约。
- 意识形态归属、教廷金库、什一税、两阶段背景及集结传送。
- 可选的 AuraSkills、CraftEngine、RealisticSeasons 联动，以及旅馆和额外死亡掉落功能。

模因和戒律定义不等于实际游戏效果。目前内置戒律效果主要覆盖护甲穿着和饮食规则。

## 安装

**运行要求：** Java 21、Paper 1.21.11、Towny，以及已注册 Vault 经济服务的经济插件。

1. 从本项目 GitHub Releases 下载 JAR，放入服务器 `plugins/` 目录。
2. 同时安装 Towny 和 Vault 经济服务提供者。
3. 启动 Paper，检查依赖与存储初始化日志，然后按服务器规则修改生成的配置。

VaultUnlocked 可提供现代多币种能力；AuraSkills、CraftEngine 和 RealisticSeasons 为可选集成。当前构建以 Paper 1.21.11、Towny 0.103.0.0 为目标组合；部署其他版本前请先在测试服验证。

## 常用入口

```text
/wde                 主菜单与分页帮助
/wde nation          国家治理
/wde town            城镇治理
/wde org             国家集团
/wde war             战争菜单
/wde ideology        意识形态
/wde background      角色背景
```

菜单是命令的图形入口，不覆盖所有管理操作。国家、城镇和集团事务仍会检查游戏内身份、职位、状态和费用。

## 文档

从 [WDER Wiki 首页](docs/README.md) 按玩家、服主、管理员或开发者身份进入对应文档。常用页面：

- [玩法说明](docs/玩法说明.md)
- [服主使用文档](docs/使用文档.md)
- [插件机制说明](docs/插件机制说明.md)
- [权限与管理员指令](docs/权限组与管理员指令.md)

## 已知边界

- WDER 扩展国家政治与战后结算，不提供实时旗帜围城或战区控制。
- 大部分戒律仍是配置或展示定义，只有明确接入的戒律会产生效果。
- 与其他战争插件并用时，应实测敌对、中立、领土保护和计分规则；不保证开箱兼容。

## 构建与许可证

使用 Java 21 和 Maven 构建：

```powershell
mvn package
```

项目使用 [GNU GPL v3](LICENSE.txt) 许可证。
