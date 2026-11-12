package com.thatguycy.worlddynamicsengine.manager.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.model.WDEreligion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;

/** Updates player mood from applicable animal-killing doctrines. */
public final class AnimalKillingDoctrineManager {
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
    public record Assessment(boolean applicable, int compliantCount, int compliantMoodWeight,
                             int violationMoodWeight, int compliantMoodChange, int violationMoodChange,
                             List<String> violations, List<DoctrineMoodEntry> moodEntries) {
        public Assessment(boolean applicable, int compliantCount, int compliantMoodWeight,
                          int violationMoodWeight, int compliantMoodChange, int violationMoodChange,
                          List<String> violations) {
            this(applicable, compliantCount, compliantMoodWeight, violationMoodWeight,
                    compliantMoodChange, violationMoodChange, violations, List.of());
        }

        public boolean compliant() {
            return applicable && violations.isEmpty();
        }

        public int violationCount() {
            return violations.size();
        }
    }

    private final WorldDynamicsEngine plugin;
    private boolean enabled;

    public AnimalKillingDoctrineManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Reads the animal-killing doctrine action toggle. */
    public void reload() {
        var config = plugin.getIdeologyConfig();
        String path = "mood.doctrineActions.animalKilling.";
        enabled = config == null || config.getBoolean(path + "enabled", true);
    }

    /** Adds or subtracts mood for each doctrine matched by this animal-kill action. */
    public void apply(Player player, Animals animal) {
        if (player == null) {
            return;
        }
        if (!enabled || !plugin.religionEnabled || !player.isOnline() || player.isDead() || animal == null) {
            return;
        }

        var resident = plugin.getResidentManager().getResident(player.getUniqueId());
        WDEreligion religion = resident == null
                ? null : plugin.getReligionManager().getReligion(resident.getReligionName());
        if (religion == null) {
            return;
        }

        Map<String, String> attitudes = plugin.getReligionManager().getConfiguredDoctrineAttitudes(religion);
        String instantAction = "animalKilling";
        Assessment assessment = evaluate(isInnocentAnimal(animal), attitudes,
                plugin.getReligionManager()::getDoctrineMoodWeight,
                (doctrine, attitude, compliant) -> plugin.getReligionManager()
                        .getDoctrineMoodChangePerWeight(doctrine, attitude, compliant, instantAction));
        if (!assessment.applicable()) {
            return;
        }

        for (DoctrineMoodEntry entry : assessment.moodEntries()) {
            plugin.getMoodManager().applyDoctrineMoodChanges(player,
                    entry.compliant() ? entry.moodChange() : 0,
                    entry.compliant() ? 0 : entry.moodChange(),
                    entry.source());
        }

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
        return evaluate(innocentAnimal, attitudes, (doctrineId, attitudeId) -> 1,
                (doctrineId, attitudeId, compliant) -> 1);
    }

    public static Assessment evaluate(boolean innocentAnimal, Map<String, String> attitudes,
                                      ToIntBiFunction<String, String> moodWeight) {
        return evaluate(innocentAnimal, attitudes, moodWeight, (doctrineId, attitudeId, compliant) -> 1);
    }

    public static Assessment evaluate(boolean innocentAnimal, Map<String, String> attitudes,
                                      ToIntBiFunction<String, String> moodWeight,
                                      MoodChangeResolver moodChangeResolver) {
        if (attitudes == null || attitudes.isEmpty()) {
            return new Assessment(false, 0, 0, 0, 0, 0, List.of());
        }

        boolean applicable = false;
        int compliantCount = 0;
        int compliantMoodWeight = 0;
        int violationMoodWeight = 0;
        int compliantMoodChange = 0;
        int violationMoodChange = 0;
        List<String> violations = new ArrayList<>();
        List<DoctrineMoodEntry> moodEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : attitudes.entrySet()) {
            String doctrineId = entry.getKey();
            String attitudeId = localId(entry.getValue());
            if (matchesDoctrine(doctrineId, SLAUGHTER_DOCTRINE)) {
                Boolean compliant = slaughterCompliant(attitudeId);
                if (compliant != null) {
                    applicable = true;
                    if (!compliant) {
                        violations.add(doctrineId);
                        int weight = Math.max(0, moodWeight.applyAsInt(doctrineId, entry.getValue()));
                        violationMoodWeight += weight;
                        int moodChange = weight * Math.max(0,
                                moodChangeResolver.getPerWeightChange(doctrineId, entry.getValue(), false));
                        violationMoodChange += moodChange;
                        moodEntries.add(new DoctrineMoodEntry(doctrineId, entry.getValue(), false, moodChange));
                    } else {
                        compliantCount++;
                        int weight = Math.max(0, moodWeight.applyAsInt(doctrineId, entry.getValue()));
                        compliantMoodWeight += weight;
                        int moodChange = weight * Math.max(0,
                                moodChangeResolver.getPerWeightChange(doctrineId, entry.getValue(), true));
                        compliantMoodChange += moodChange;
                        moodEntries.add(new DoctrineMoodEntry(doctrineId, entry.getValue(), true, moodChange));
                    }
                }
            } else if (innocentAnimal && matchesDoctrine(doctrineId, INNOCENT_DOCTRINE)) {
                Boolean compliant = innocentAnimalCompliant(attitudeId);
                if (compliant != null) {
                    applicable = true;
                    if (!compliant) {
                        violations.add(doctrineId);
                        int weight = Math.max(0, moodWeight.applyAsInt(doctrineId, entry.getValue()));
                        violationMoodWeight += weight;
                        int moodChange = weight * Math.max(0,
                                moodChangeResolver.getPerWeightChange(doctrineId, entry.getValue(), false));
                        violationMoodChange += moodChange;
                        moodEntries.add(new DoctrineMoodEntry(doctrineId, entry.getValue(), false, moodChange));
                    } else {
                        compliantCount++;
                        int weight = Math.max(0, moodWeight.applyAsInt(doctrineId, entry.getValue()));
                        compliantMoodWeight += weight;
                        int moodChange = weight * Math.max(0,
                                moodChangeResolver.getPerWeightChange(doctrineId, entry.getValue(), true));
                        compliantMoodChange += moodChange;
                        moodEntries.add(new DoctrineMoodEntry(doctrineId, entry.getValue(), true, moodChange));
                    }
                }
            }
        }
        return new Assessment(applicable, compliantCount, compliantMoodWeight, violationMoodWeight,
                compliantMoodChange, violationMoodChange, List.copyOf(violations), List.copyOf(moodEntries));
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
