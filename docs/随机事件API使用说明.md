# 随机事件 API 使用说明

WDE 提供公共 API，允许其他 Paper 插件向城镇随机事件抽选中注册自定义事件。API 包为：

```text
com.thatguycy.worlddynamicsengine.api.event
```

## 依赖与获取注册表

外部插件编译时将 WDE 作为 `provided` 依赖，不要把 WDE 打包进自己的插件 JAR：

```xml
<dependency>
    <groupId>com.thatguycy</groupId>
    <artifactId>WorldDynamicsEngine-Reborn</artifactId>
    <version>0.3.0</version>
    <scope>provided</scope>
</dependency>
```

`RandomEventContext` 的公开签名包含 Towny `Town`，因此编译插件时需要将 Towny API 作为 `provided` 依赖。

建议在第三方插件的 `plugin.yml` 声明对 WDE 的依赖，确保 WDE 已完成服务注册：

```yaml
depend: [WorldDynamicsEngine-Reborn]
```

注册表可以通过 Bukkit 服务发现获取：

```java
RandomEventRegistry registry =
        getServer().getServicesManager().load(RandomEventRegistry.class);
```

也可以直接通过主插件实例获取：

```java
RandomEventRegistry registry =
        WorldDynamicsEngine.getInstance().getRandomEventRegistry();
```

若 WDE 未启用或注册表不可用，获取结果可能为 `null`。

## 注册一次性事件

```java
import com.thatguycy.worlddynamicsengine.api.event.RandomEvent;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventContext;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventOccurrence;
import com.thatguycy.worlddynamicsengine.api.event.RandomEventRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExampleEventsPlugin extends JavaPlugin {
    private RandomEventRegistry eventRegistry;

    @Override
    public void onEnable() {
        eventRegistry = getServer().getServicesManager().load(RandomEventRegistry.class);
        if (eventRegistry == null) {
            getLogger().warning("WDE random event API is unavailable.");
            return;
        }

        boolean registered = eventRegistry.register(this, new FestivalEvent());
        if (!registered) {
            getLogger().warning("Could not register the festival event.");
        }
    }

    @Override
    public void onDisable() {
        if (eventRegistry != null) {
            eventRegistry.unregister(this, "example_festival");
        }
    }

    private static final class FestivalEvent implements RandomEvent {
        @Override
        public String id() {
            return "example_festival";
        }

        @Override
        public double defaultWeight() {
            return 0.5D;
        }

        @Override
        public boolean isEligible(RandomEventContext context) {
            return context.townWealth() >= 5000.0D;
        }

        @Override
        public RandomEventOccurrence execute(RandomEventContext context) {
            context.target().sendMessage(
                    "A festival has begun in " + context.townName() + "!");
            return new RandomEventOccurrence() {};
        }
    }
}
```

默认 `RandomEventOccurrence` 是一次性事件。返回 `null` 表示事件无法启动，不会启动城镇冷却（随机事件系统不再使用城镇冷却）；返回 occurrence 表示启动成功。

## 权重与资格

`RandomEvent` 提供以下可选默认值：

- `defaultWeight()`：默认权重，缺少配置覆盖时使用，默认 `1.0`。
- `minimumTownWealth()`：最低城镇财富，默认不限制。
- `requiresStrengthPoints()`：是否需要按 WDE 的财富公式计算强度点数，默认 `false`。
- `isEligible(context)`：抽中该事件后进行的额外资格判定，默认允许。

可在 `events.yml` 覆盖第三方事件权重：

```yaml
generation:
  event-weights:
    example_festival: 2.0
```

权重为 0 会禁用事件。事件 ID 必须以小写字母或数字开头，且只允许小写字母、数字、下划线和连字符，长度最多 64 个字符；ID 不能与内置或已注册事件重复。

事件注册于每个城镇每轮最多一次的加权抽选中。`isEligible` 在事件类型选出后调用；若返回 `false`，本轮不重抽其他事件。

## 事件上下文

`RandomEventContext` 是不可变记录，包含：

- `eventId()`：本次事件的唯一 UUID，可用于标记本次生成的实体。
- `target()`：本轮随机选中的在线 Towny 居民。
- `town()` / `townName()`：事件所属 Towny 城镇及其名称。
- `townWealth()`：WDE 记录的城镇财富。
- `onlinePopulation()` / `population()`：在线居民数和总人口。
- `townPlayers()`：本轮在线 Towny 居民快照。
- `strengthPoints()`：WDE 根据财富公式和在线人口比例计算的强度点数。

事件执行和生命周期回调都在服务器主线程调用，请勿在其中执行阻塞 IO 或长时间计算。

## 持续事件

需要跨多个检查周期持续运行的事件可返回自定义 occurrence：

- 覆盖 `isOngoing()` 返回 `true`，使 WDE 持续跟踪该实例。
- 覆盖 `updateTownPlayers()` 和 `onPlayerDeath()` 接收城镇玩家变化。
- 若事件必须持续到自定义条件满足，可覆盖 `endsWhenTownEmpty()` 返回 `false`。
- 覆盖 `wasSuccessful()` 提供事件自己的成功判定。
- 覆盖 `finishesWhenSuccessful()` 返回 `true`，在 `wasSuccessful()` 成立后立即结束事件。
- 在 `finish(successful)` 中结束计时器、移除实体或释放其他资源。

默认情况下，所属城镇没有在线玩家时，WDE 会调用 `finish(occurrence.wasSuccessful())`；覆盖 `endsWhenTownEmpty()` 返回 `false` 可让事件在空城时继续运行。默认不会因 `wasSuccessful()` 为 true 而自动结束，需同时覆盖 `finishesWhenSuccessful()`。主动注销事件或注册它的插件卸载时，仍在进行的实例会以 `successful = false` 结束。插件卸载时，WDE 会自动移除该插件注册的所有事件；正常情况下也建议在 `onDisable()` 主动调用 `unregister`。

注册、注销操作必须在服务器主线程执行。重复 ID、无效 ID、负数或非有限默认权重会被拒绝。
