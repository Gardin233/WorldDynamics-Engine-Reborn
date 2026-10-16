package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.potion.PotionEffectType;

/** 根据玩家击杀的动物和其意识形态戒律施加限时抗性提升或虚弱。 */
public final class AnimalKillingDoctrineManager {
    private static final String BUFF_SOURCE = "wde:animal_killing_doctrines";
    private static final String SLAUGHTER_DOCTRINE = "slaughter_animals";
    private static final String INNOCENT_DOCTRINE = "killing_innocent_animals";
    private static final Set<String> SLAUGHTER_VIOLATIONS = Set.of("taboo", "fear", "oppose");
    private static final Set<String> INNOCENT_KILLING_VIOLATIONS = Set.of("abhor", "fear", "disgust");
    private static final Set<String> SLAUGHTER_ATTITUDES = Set.of(
            "taboo", "fear", "oppose", "neutral", "support", "love");
    private static final Set<String> INNOCENT_KILLING_ATTITUDES = Set.of(
            "abhor", "fear", "disgust", "neutral");
    private static final Set<EntityType> CAN_ATTACK_PLAYERS = Set.of(
            EntityType.BEE,
            EntityType.GOAT,
            EntityType.LLAMA,
            EntityType.PANDA,
            EntityType.POLAR_BEAR,
            EntityType.TRADER_LLAMA,
            EntityType.WOLF
    );

    /** 保存戒律是否适用以及本次击杀触犯的戒律。 */
    public record Assessment(boolean applicable, List<String> violations) {
        public boolean compliant() {
            return applicable && violations.isEmpty();
        }
    }

    private final WorldDynamicsEngine plugin;
    private boolean enabled;
    private int durationTicks;
    private int resistanceLevel;
    private int weaknessLevel;

    public AnimalKillingDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 读取独立可配置的动物击杀效果参数。 */
    public void reload() {
        var config = plugin.getIdeologyConfig();
        String path = "doctrineEffects.animalKilling.";
        enabled = config == null || config.getBoolean(path + "enabled", true);
        int seconds = config == null ? 30 : config.getInt(path + "durationSeconds", 30);
        durationTicks = Math.max(1, Math.min(3600, seconds)) * 20;
        resistanceLevel = config == null ? 1
                : Math.max(0, Math.min(10, config.getInt(path + "resistanceLevel", 1)));
        weaknessLevel = config == null ? 1
                : Math.max(0, Math.min(10, config.getInt(path + "weaknessLevel", 1)));
    }

    /** 玩家击杀动物后判定，并替换本机制此前提交的限时效果。 */
    public void apply(Player player, Animals animal) {
        if (player == null) {
            return;
        }
        if (!enabled || !plugin.religionEnabled || !player.isOnline() || player.isDead() || animal == null) {
            plugin.getDoctrineBuffManager().removeSource(player, BUFF_SOURCE);
            return;
        }

        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) {
            plugin.getDoctrineBuffManager().removeSource(player, BUFF_SOURCE);
            return;
        }

        Assessment assessment = evaluate(isInnocentAnimal(animal),
                plugin.getReligionManager().getConfiguredDoctrineAttitudes(religion));
        if (!assessment.applicable()) {
            plugin.getDoctrineBuffManager().removeSource(player, BUFF_SOURCE);
            return;
        }

        PotionEffectType effect = assessment.compliant()
                ? PotionEffectType.RESISTANCE : PotionEffectType.WEAKNESS;
        int level = assessment.compliant() ? resistanceLevel : weaknessLevel;
        plugin.getDoctrineBuffManager().replaceTimed(
                player, BUFF_SOURCE, Map.of(effect, level), durationTicks);

        for (String doctrineId : assessment.violations()) {
            if (plugin.getPlayerMenuManager() != null) {
                plugin.getPlayerMenuManager().sendDoctrineViolation(player, doctrineId);
            }
        }
    }

    /** 只将不会主动攻击或反击玩家的 Bukkit 动物视为无辜动物。 */
    public static boolean isInnocentAnimal(Animals animal) {
        if (animal == null || CAN_ATTACK_PLAYERS.contains(animal.getType())) {
            return false;
        }
        return !(animal instanceof Rabbit rabbit && rabbit.getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY);
    }

    public static Assessment evaluate(boolean innocentAnimal, Map<String, String> attitudes) {
        if (attitudes == null || attitudes.isEmpty()) {
            return new Assessment(false, List.of());
        }

        boolean applicable = false;
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> entry : attitudes.entrySet()) {
            String doctrineId = entry.getKey();
            String attitudeId = localId(entry.getValue());
            if (matchesDoctrine(doctrineId, SLAUGHTER_DOCTRINE)) {
                Boolean compliant = slaughterCompliant(attitudeId);
                if (compliant != null) {
                    applicable = true;
                    if (!compliant) {
                        violations.add(doctrineId);
                    }
                }
            } else if (innocentAnimal && matchesDoctrine(doctrineId, INNOCENT_DOCTRINE)) {
                Boolean compliant = innocentAnimalCompliant(attitudeId);
                if (compliant != null) {
                    applicable = true;
                    if (!compliant) {
                        violations.add(doctrineId);
                    }
                }
            }
        }
        return new Assessment(applicable, List.copyOf(violations));
    }

    private static Boolean slaughterCompliant(String attitudeId) {
        if (attitudeId == null) {
            return null;
        }
        if (!SLAUGHTER_ATTITUDES.contains(attitudeId)) {
            return null;
        }
        return !SLAUGHTER_VIOLATIONS.contains(attitudeId);
    }

    private static Boolean innocentAnimalCompliant(String attitudeId) {
        if (attitudeId == null) {
            return null;
        }
        if (!INNOCENT_KILLING_ATTITUDES.contains(attitudeId)) {
            return null;
        }
        return !INNOCENT_KILLING_VIOLATIONS.contains(attitudeId);
    }

    private static boolean matchesDoctrine(String doctrineId, String path) {
        return doctrineId != null
                && (doctrineId.endsWith(":wder/" + path) || doctrineId.endsWith(":wde/" + path));
    }

    private static String localId(String attitudeId) {
        if (attitudeId == null) {
            return null;
        }
        return attitudeId.substring(attitudeId.lastIndexOf('/') + 1);
    }
}
