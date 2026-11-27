package com.thatguycy.worlddynamicsengine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Repairs one culture config in memory, without plugin or disk dependencies. */
final class CultureConfigRepair {
    private final FileConfiguration ideologyConfig;
    private final String defaultCurrency;

    CultureConfigRepair(FileConfiguration ideologyConfig, String defaultCurrency) {
        this.ideologyConfig = ideologyConfig;
        this.defaultCurrency = defaultCurrency;
    }

    boolean apply() {
        if (ideologyConfig == null) {
            return false;
        }

        boolean changed = false;
        if (!ideologyConfig.contains("history.enabled")) {
            ideologyConfig.set("history.enabled", false);
            changed = true;
        }
        if (!ideologyConfig.contains("history.clock")) {
            ideologyConfig.set("history.clock", "REAL_TIME");
            changed = true;
        }
        if (!ideologyConfig.contains("history.world")) {
            ideologyConfig.set("history.world", "world");
            changed = true;
        }
        if (!ideologyConfig.contains("religionEnabled")) {
            ideologyConfig.set("religionEnabled", true);
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.mode")) {
            ideologyConfig.set("cooldowns.mode", "REAL_TIME");
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.world")) {
            ideologyConfig.set("cooldowns.world", "world");
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.memes.days")) {
            ideologyConfig.set("cooldowns.memes.days", 7);
            changed = true;
        }
        if (!ideologyConfig.contains("cooldowns.doctrines.days")) {
            ideologyConfig.set("cooldowns.doctrines.days", 7);
            changed = true;
        }
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionCreation", "15000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionPlayerConversion", "3000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionTownConversion", "12000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionNationConversion", "30000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionForcedPlayerConversion", "5000.00");
        changed |= ensureChargeDefaults(ideologyConfig, "economy.charges.religionForcedTownConversion", "20000.00");
        Map<String, Object> governanceDefaults = Map.ofEntries(
                Map.entry("governance.defaultMode", "LIFETIME"),
                Map.entry("cardinals.baseSeats", 2),
                Map.entry("cardinals.followersPerAdditionalSeat", 25),
                Map.entry("cardinals.maximumSeats", 9),
                Map.entry("elections.intervalDays", 90),
                Map.entry("elections.votingDays", 7),
                Map.entry("elections.clock", "REAL_TIME"),
                Map.entry("elections.world", "world"),
                Map.entry("elections.quorumRatio", 0.5D),
                Map.entry("elections.winnerMinimumRatio", 0.5D),
                Map.entry("elections.tiePolicy", "KEEP_INCUMBENT"),
                Map.entry("elections.noWinnerPolicy", "RETRY_AFTER_DAYS"),
                Map.entry("elections.votingRule", "ONE_CARDINAL_ONE_VOTE"),
                Map.entry("elections.candidateScope", "ALL_FOLLOWERS"),
                Map.entry("elections.absenceCountsTowardQuorum", false),
                Map.entry("elections.abstentionCountsTowardQuorum", true),
                Map.entry("elections.noWinnerRetryDays", 30),
                Map.entry("economy.governance.appointCardinalCost", "500.00"),
                Map.entry("economy.governance.revokeCardinalCost", "250.00"),
                Map.entry("economy.governance.churchCreation.amount", "5000.00"),
                Map.entry("economy.governance.churchCreation.currency", "USD"),
                Map.entry("churches.maximumPerIdeology", 32),
                Map.entry("churches.membershipCooldownHours", 24),
                Map.entry("churches.governanceActionCooldownHours", 24),
                Map.entry("council.proposalDurationDays", 7),
                Map.entry("council.proposalCooldownHours", 12),
                Map.entry("council.voteWeighting", "MEMBERS"),
                Map.entry("council.maximumChurchWeight", 10000),
                Map.entry("council.quorumRatio", 0.5D),
                Map.entry("council.approvalRatio", 0.5D),
                Map.entry("council.sanctionMaximumDays", 90));
        for (var entry : governanceDefaults.entrySet()) {
            if (!ideologyConfig.contains(entry.getKey())) {
                ideologyConfig.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (!"ONE_CARDINAL_ONE_VOTE".equalsIgnoreCase(
                ideologyConfig.getString("elections.votingRule", "ONE_CARDINAL_ONE_VOTE"))) {
            ideologyConfig.set("elections.votingRule", "ONE_CARDINAL_ONE_VOTE");
            changed = true;
        }
        Map<String, Object> treasuryDefaults = Map.of(
                "enabled", true, "titheEnabled", true, "taxWorld", "world",
                "defaultTitheAmount", "0",
                "logPartialCollection", true);
        for (var entry : treasuryDefaults.entrySet()) {
            String path = "economy.treasury." + entry.getKey();
            if (!ideologyConfig.contains(path)) {
                ideologyConfig.set(path, entry.getValue());
                changed = true;
            }
        }
        String defaultTitheTypePath = "economy.treasury.defaultTitheType";
        if (ideologyConfig.getString(defaultTitheTypePath, "").equalsIgnoreCase("WEALTH_PERCENTAGE")) {
            ideologyConfig.set("economy.treasury.defaultTitheAmount", "0");
            changed = true;
        }
        if (ideologyConfig.contains(defaultTitheTypePath)) {
            ideologyConfig.set(defaultTitheTypePath, null);
            changed = true;
        }
        Map<String, Object> moodDefaults = Map.ofEntries(
                Map.entry("mood.changes.compliant", 1),
                Map.entry("mood.changes.violation", 1),
                Map.entry("mood.decayMinutes", 30),
                Map.entry("mood.events.deathRebirthBonus", 10),
                Map.entry("mood.events.townDeathPenalty", -4),
                Map.entry("mood.events.betrayalPenalty", -4),
                Map.entry("mood.events.betrayerHuntBonus", 10),
                Map.entry("mood.inspiration.intervalMinutes", 2),
                Map.entry("mood.inspiration.effects.arrowArmorPiercing.enabled", true),
                Map.entry("mood.inspiration.effects.meleeArmorPiercing.enabled", true),
                Map.entry("mood.inspiration.effects.animalTaming.enabled", true),
                Map.entry("mood.inspiration.effects.nightingale.enabled", true),
                Map.entry("mood.inspiration.effects.nightingale.durationMinutes", 15),
                Map.entry("mood.inspiration.effects.consolation.enabled", true),
                Map.entry("mood.inspiration.effects.consolation.moodBonus", 10),
                Map.entry("mood.inspiration.effects.actionIncentive.enabled", true),
                Map.entry("mood.inspiration.effects.actionIncentive.durationMinutes", 15),
                Map.entry("mood.inspiration.effects.miningIncentive.enabled", true),
                Map.entry("mood.inspiration.effects.miningIncentive.durationMinutes", 15),
                Map.entry("mood.inspiration.effects.stretch.enabled", true),
                Map.entry("mood.inspiration.effects.stretch.durationMinutes", 15),
                Map.entry("mood.instantActions.animalKilling.compliant", 1),
                Map.entry("mood.instantActions.animalKilling.violation", 1),
                Map.entry("mood.instantActions.humanMeat.compliant", 1),
                Map.entry("mood.instantActions.humanMeat.violation", 1),
                Map.entry("mood.doctrineActions.animalKilling.enabled",
                        ideologyConfig.getBoolean("doctrineEffects.animalKilling.enabled", true)),
                Map.entry("mood.doctrineActions.animalResonance.enabled", true),
                Map.entry("mood.doctrineActions.animalPersonhoodLeash.enabled", true),
                Map.entry("mood.doctrineActions.animalPersonhoodLeash.radiusBlocks", 8),
                Map.entry("mood.doctrineActions.animalPersonhoodLeash.checkIntervalTicks", 40),
                Map.entry("mood.doctrineActions.animalPersonhoodLeash.moodPenalty", 5),
                Map.entry("mood.doctrineActions.animalPersonhoodLeash.moodBonus", 5),
                Map.entry("mood.doctrineActions.animalPersonhoodLeash.moodDurationMinutes", 5),
                Map.entry("mood.doctrineActions.rituals.enabled", true),
                Map.entry("mood.doctrineActions.rituals.longAbsenceDays", 30),
                Map.entry("mood.doctrineActions.rituals.satisfactionDurationMinutes", 30),
                Map.entry("mood.doctrineActions.bonding.enabled", true),
                Map.entry("mood.doctrineActions.bonding.moodPerTamedAnimal", 2),
                Map.entry("mood.doctrineActions.bonding.petDeathPenalty", 4),
                Map.entry("mood.doctrineActions.bonding.petDeathDurationDays", 7),
                Map.entry("mood.doctrineActions.comfort.enabled", true),
                Map.entry("mood.doctrineActions.comfort.noSleepThresholdDays", 3),
                Map.entry("mood.doctrineActions.roughingIt.enabled", true),
                Map.entry("mood.doctrineActions.pain.enabled", true),
                Map.entry("mood.doctrineActions.pain.checkIntervalTicks", 20),
                Map.entry("mood.doctrineActions.pain.healthThresholds", List.of(0.75, 0.50, 0.25, 0.10)),
                Map.entry("mood.doctrineActions.pain.ideal.highHealthPenalty", 2),
                Map.entry("mood.doctrineActions.pain.ideal.lowHealthBonuses", List.of(2, 4, 6, 10)),
                Map.entry("mood.doctrineActions.pain.none.lowHealthPenalties", List.of(2, 4, 6, 10)),
                Map.entry("mood.doctrineActions.lighting.enabled", true),
                Map.entry("mood.doctrineActions.lighting.checkIntervalTicks", 20),
                Map.entry("mood.doctrineActions.indoors.enabled", true),
                Map.entry("mood.doctrineActions.indoors.checkIntervalTicks", 20),
                Map.entry("mood.doctrineActions.mining.enabled", true),
                Map.entry("mood.doctrineActions.mining.ores.vanilla", List.of(
                        "STONE", "COAL_ORE", "DEEPSLATE_COAL_ORE",
                        "IRON_ORE", "DEEPSLATE_IRON_ORE",
                        "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
                        "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "NETHER_GOLD_ORE",
                        "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE",
                        "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
                        "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
                        "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
                        "NETHER_QUARTZ_ORE", "ANCIENT_DEBRIS")),
                Map.entry("mood.doctrineActions.mining.ores.craftengine", List.of()),
                Map.entry("mood.doctrineActions.logging.enabled", true),
                Map.entry("mood.doctrineActions.logging.wood.vanilla", List.of(
                        "OAK_LOG", "STRIPPED_OAK_LOG",
                        "SPRUCE_LOG", "STRIPPED_SPRUCE_LOG",
                        "BIRCH_LOG", "STRIPPED_BIRCH_LOG",
                        "JUNGLE_LOG", "STRIPPED_JUNGLE_LOG",
                        "ACACIA_LOG", "STRIPPED_ACACIA_LOG",
                        "DARK_OAK_LOG", "STRIPPED_DARK_OAK_LOG",
                        "MANGROVE_LOG", "STRIPPED_MANGROVE_LOG",
                        "CHERRY_LOG", "STRIPPED_CHERRY_LOG",
                        "PALE_OAK_LOG", "STRIPPED_PALE_OAK_LOG",
                        "CRIMSON_STEM", "STRIPPED_CRIMSON_STEM",
                        "WARPED_STEM", "STRIPPED_WARPED_STEM")),
                Map.entry("mood.doctrineActions.logging.wood.craftengine", List.of()),
                Map.entry("mood.doctrineActions.trees.enabled", true),
                Map.entry("mood.doctrineActions.trees.saplings.vanilla", List.of(
                        "OAK_SAPLING", "SPRUCE_SAPLING", "BIRCH_SAPLING",
                        "JUNGLE_SAPLING", "ACACIA_SAPLING", "DARK_OAK_SAPLING",
                        "MANGROVE_PROPAGULE", "CHERRY_SAPLING", "PALE_OAK_SAPLING",
                        "AZALEA", "FLOWERING_AZALEA")),
                Map.entry("mood.doctrineActions.trees.saplings.craftengine", List.of()),
                Map.entry("mood.doctrineActions.potions.enabled", true),
                Map.entry("mood.doctrineActions.potions.inebriationMoodBonus", 10),
                Map.entry("mood.doctrineActions.potions.reinforcementMoodBonus", 5),
                Map.entry("mood.doctrineActions.potions.dependencyGainPerPotion", 5),
                Map.entry("mood.doctrineActions.potions.satisfactionGainPerPotion", 35),
                Map.entry("mood.doctrineActions.potions.dependencyAcquisition.baseChancePercent", 10),
                Map.entry("mood.doctrineActions.potions.dependencyAcquisition.increasePerConsecutiveDrinkPercent", 10),
                Map.entry("mood.doctrineActions.potions.dependencyAcquisition.maximumChancePercent", 100),
                Map.entry("mood.doctrineActions.potions.dependencyAcquisition.streakWindowMinutes", 10),
                Map.entry("mood.doctrineActions.potions.satisfactionDecayPerHour", 10.0),
                Map.entry("mood.doctrineActions.potions.maxDependencyDecayAccelerationPercent", 20),
                Map.entry("mood.doctrineActions.potions.splashSatisfactionLoss", 20),
                Map.entry("mood.doctrineActions.potions.satisfactionThresholds.satisfiedMinimum", 75),
                Map.entry("mood.doctrineActions.potions.satisfactionThresholds.deficientMinimum", 50),
                Map.entry("mood.doctrineActions.potions.satisfactionThresholds.cravingMinimum", 25),
                Map.entry("mood.doctrineActions.potions.moodByStage.satisfied", 2),
                Map.entry("mood.doctrineActions.potions.moodByStage.deficient", -5),
                Map.entry("mood.doctrineActions.potions.moodByStage.craving", -10),
                Map.entry("mood.doctrineActions.potions.moodByStage.madness", -20),
                Map.entry("mood.doctrineActions.potions.craftenginePotionItems", List.of()),
                Map.entry("mood.doctrineActions.potions.inebriationPotions.vanilla", List.of(
                        "POISON", "LONG_POISON", "STRONG_POISON",
                        "HARMING", "STRONG_HARMING",
                        "WEAKNESS", "LONG_WEAKNESS",
                        "SLOWNESS", "LONG_SLOWNESS", "STRONG_SLOWNESS",
                        "TURTLE_MASTER", "LONG_TURTLE_MASTER", "STRONG_TURTLE_MASTER",
                        "INFESTED", "OOZING", "WEAVING", "WIND_CHARGED")),
                Map.entry("mood.doctrineActions.potions.inebriationPotions.craftengine", List.of()),
                Map.entry("mood.doctrineActions.displayOfHeads.enabled", true),
                Map.entry("mood.doctrineActions.displayOfHeads.checkIntervalTicks", 40),
                Map.entry("mood.doctrineActions.displayOfHeads.radiusBlocks", 8),
                Map.entry("mood.doctrineActions.fieldBattle.enabled", true),
                Map.entry("mood.doctrineActions.fieldBattle.combatTimeoutSeconds", 30),
                Map.entry("mood.doctrineActions.proselytizing.enabled", true),
                Map.entry("mood.doctrineActions.kindness.enabled", true),
                Map.entry("mood.doctrineActions.kindness.giftMoodCooldownMinutes", 30),
                Map.entry("mood.doctrineActions.kindness.refusalMoodCooldownHours", 24),
                Map.entry("mood.doctrineActions.ranching.enabled", true),
                Map.entry("mood.doctrineActions.war.enabled", true),
                Map.entry("mood.doctrineActions.war.longPeaceDays", 30),
                Map.entry("mood.doctrineActions.raiding.enabled", true),
                Map.entry("mood.doctrineActions.raiding.longPeaceDays", 30),
                Map.entry("mood.doctrineActions.multiculturalism.enabled", true),
                Map.entry("mood.doctrineActions.multiculturalism.checkIntervalTicks", 100));
        for (var entry : moodDefaults.entrySet()) {
            String path = entry.getKey();
            if (!ideologyConfig.contains(path)) {
                ideologyConfig.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        Map<String, Object> expectationDefaults = Map.of(
                "mood.expectations.newPlayer.stepHours", 2,
                "mood.expectations.newPlayer.moodBonuses", List.of(30, 24, 18, 12, 6, 0),
                "mood.expectations.roles.townMayor", -8,
                "mood.expectations.roles.nationKing", -12
        );
        for (var entry : expectationDefaults.entrySet()) {
            if (!ideologyConfig.contains(entry.getKey())) {
                ideologyConfig.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        Map<String, Object> collapseDefaults = Map.ofEntries(
                Map.entry("mood.collapse.severity.mildMinimumMood", 34),
                Map.entry("mood.collapse.severity.moderateMinimumMood", 17),
                Map.entry("mood.collapse.effects.sadWander.enabled", true),
                Map.entry("mood.collapse.effects.sadWander.durationSeconds", 300),
                Map.entry("mood.collapse.effects.sadWander.walkSpeed", 0.11D),
                Map.entry("mood.collapse.effects.sadWander.wanderRadius", 6.0D),
                Map.entry("mood.collapse.effects.darkVision.enabled", true),
                Map.entry("mood.collapse.effects.darkVision.durationSeconds", 180),
                Map.entry("mood.collapse.effects.crazyTalk.enabled", true),
                Map.entry("mood.collapse.effects.crazyTalk.durationSeconds", 180),
                Map.entry("mood.collapse.effects.crazyTalk.intervalSeconds", 15),
                Map.entry("mood.collapse.effects.crazyTalk.messages",
                        List.of("我听见有人在呼唤我。", "这世界看起来不太对劲。")),
                Map.entry("mood.collapse.effects.catatonicStupor.enabled", true),
                Map.entry("mood.collapse.effects.catatonicStupor.durationSeconds", 120),
                Map.entry("mood.collapse.effects.faithCrisis.enabled", true),
                Map.entry("mood.collapse.effects.faithCrisis.durationSeconds", 120),
                Map.entry("mood.collapse.effects.selfHarm.enabled", true),
                Map.entry("mood.collapse.effects.selfHarm.durationSeconds", 5),
                Map.entry("mood.collapse.effects.wildOutburst.enabled", true),
                Map.entry("mood.collapse.effects.wildOutburst.durationSeconds", 86400),
                Map.entry("mood.collapse.effects.wildOutburst.dropIntervalTicks", 10),
                Map.entry("mood.collapse.effects.humanityCollapse.enabled", true),
                Map.entry("mood.collapse.effects.humanityCollapse.durationSeconds", 180),
                Map.entry("mood.collapse.effects.humanityCollapse.titleFadeInTicks", 5),
                Map.entry("mood.collapse.effects.humanityCollapse.titleStayTicks", 40),
                Map.entry("mood.collapse.effects.humanityCollapse.titleFadeOutTicks", 10),
                Map.entry("mood.collapse.effects.humanityCollapse.sequence", List.of(
                        Map.of("delaySeconds", 2, "title", "&5人性崩溃",
                                "subtitle", "&7你开始失去对自我的认同。", "chat", "&7你是谁，已经不再重要。"),
                        Map.of("delaySeconds", 7, "title", "&d剥离旧我",
                                "subtitle", "&7曾经定义你的规则正在消散。", "chat", "&d旧有的人性已经无法约束你。"),
                        Map.of("delaySeconds", 13, "title", "&5非人化",
                                "subtitle", "&7你终于超越了人类身份。", "chat", "&5从现在起，你不再属于人类。")
                )),
                Map.entry("mood.collapse.release.moodBonus", 40),
                Map.entry("mood.collapse.release.durationMinutes", 30)
        );
        for (var entry : collapseDefaults.entrySet()) {
            if (!ideologyConfig.contains(entry.getKey())) {
                ideologyConfig.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (ideologyConfig.contains("mood.collapse.effects.sadWander.intervalTicks")) {
            ideologyConfig.set("mood.collapse.effects.sadWander.intervalTicks", null);
            changed = true;
        }
        if (ideologyConfig.contains("mood.collapse.effects.faithCrisis.intervalSeconds")) {
            ideologyConfig.set("mood.collapse.effects.faithCrisis.intervalSeconds", null);
            changed = true;
        }
        if (ideologyConfig.contains("doctrineEffects")) {
            ideologyConfig.set("doctrineEffects", null);
            changed = true;
        }
        changed |= migrateLegacyDoctrineRegistryToMemeRegistry();
        changed |= migrateExistingDoctrineNamespaces();
        changed |= removeSpiritualVisionDoctrineMigration();
        changed |= ensureLightingCraveAttitudeMigration();
        changed |= ensureMiningStoneMigration();
        changed |= ensureRanchingOpposeAttitudeMigration();
        changed |= normalizeMemeOutcomeEffectLists();
        changed |= ensureNaturePrimacyTamingInspirationMigration();
        changed |= ensureDarknessNightingaleInspirationMigration();
        changed |= ensureProselytizerConsolationInspirationMigration();
        changed |= ensureNudistActionIncentiveInspirationMigration();
        changed |= ensureTunnelerMiningIncentiveInspirationMigration();
        changed |= ensurePainVirtueMoodEffectMigration();

        return changed;
    }

    private boolean ensureChargeDefaults(FileConfiguration configuration, String path, String defaultAmount) {
        boolean changed = false;
        if (!configuration.contains(path + ".amount")) {
            configuration.set(path + ".amount", defaultAmount);
            changed = true;
        }
        if (!configuration.contains(path + ".currency")) {
            configuration.set(path + ".currency", defaultCurrency);
            changed = true;
        }
        return changed;
    }

    private boolean ensureMemeDefaults() {
        if (ideologyConfig == null) {
            return false;
        }

        boolean changed = false;
        changed |= ensureMemeDefinition("original_sin", "原罪主义", "我们生来有罪。其他人比我们更有价值。");
        changed |= ensureMemeDefinition("nature_primacy", "自然至上", "人类是完美大自然中的污点。");
        changed |= ensureMemeDefinition("proselytizer", "传教热忱", "我们的使命是传播信仰。");
        changed |= ensureMemeDefinition("loyalty", "赤胆忠心", "我们在面对其他人时必须站在自己人的立场上。");
        changed |= ensureMemeDefinition("supremacist", "霸权主义", "我们的人民应该主宰其他所有人。");
        changed |= ensureMemeDefinition("collectivist", "集体主义", "每个人都是集体的一部分。人们应该努力发挥自己的作用为集体做贡献。");
        changed |= ensureMemeDefinition("ritualist", "仪式热忱", "宇宙尚有广大潜能。借助仪式，我们方能对它有所了解，进而驾驭其中的力量。");
        changed |= ensureMemeDefinition("darkness", "黑暗亲和", "明亮的光线带来燃烧和毁灭！黑暗带来滋养和创造。人们应该生活在黑暗中。");
        changed |= ensureMemeDefinition("tunneler", "地底之心", "人类应该生活在地下，享受地下深处甜美多汁的果实。");
        changed |= ensureMemeDefinition("cannibal", "食人狂热", "我们需要人肉。");
        changed |= ensureMemeDefinition("bloodfeeder", "汲血狂热", "享用血液乃神圣之事，汲血者理应受到我们的崇拜。");
        changed |= ensureMemeDefinition("pain_is_virtue", "苦痛美德", "美德是通过自己和他人的痛苦表现出来的。");
        changed |= ensureMemeDefinition("nudist", "裸体主义", "护甲束缚、控制着我们，这让我们感到窒息。我们应当自由地一丝不挂。");
        changed |= ensureMemeDefinition("nonhuman_alienation", "非人异化", "在神与祂那丰厚的赏赐面前，人性不过是阻碍罢了。");
        changed |= ensureMemeDefinition("raider", "劫掠成性", "强者理应掠夺弱者。");
        changed |= ensureMemeDefinition("individualist", "个人主义", "每个人都是自由的个体，都有权有自己的想法和决定。任何人都不应该被强迫顺从。");
        changed |= ensureMemeDefinition("animal_personhood", "动物平权", "动物应该拥有和人类一样的权利。");
        changed |= ensureMemeDefinition("human_primacy", "人类至上", "人类是宇宙的道德中心。");
        return changed;
    }

    private boolean ensureMemeDefinition(String id, String title, String description) {
        String basePath = "memes.registry." + id;
        boolean changed = false;
        if (!ideologyConfig.contains(basePath + ".title")) {
            ideologyConfig.set(basePath + ".title", title);
            changed = true;
        }
        if (!ideologyConfig.contains(basePath + ".description")) {
            ideologyConfig.set(basePath + ".description", description);
            changed = true;
        }
        return changed;
    }

    private boolean ensureDoctrineDefaults() {
        if (ideologyConfig == null) {
            return false;
        }

        boolean changed = false;
        for (String doctrinePath : List.of(
                "armor_wearing",
                "cannibalism",
                "blood_feeding",
                "multiculturalism",
                "display_of_heads",
                "slaughter_animals",
                "betrayal",
                "kindness",
                "potions",
                "killing_innocent_animals",
                "lighting",
                "field_battle",
                "meat_eating",
                "mining",
                "raiding",
                "logging",
                "trees",
                "nonhumanization",
                "rituals",
                "animal_resonance",
                "bonding",
                "comfort",
                "pain",
                "proselytizing",
                "ranching",
                "roughing_it",
                "indoors")) {
            changed |= migrateDoctrineNamespace(doctrinePath);
        }

        List<Map<String, Object>> warMemeRules = new ArrayList<>();
        Map<String, Object> supremacistRule = new LinkedHashMap<>();
        Map<String, Object> supremacistValue = new LinkedHashMap<>();
        supremacistValue.put("default", List.of("want"));
        supremacistValue.put("cant_use", List.of("hate"));
        supremacistRule.put("supremacist", supremacistValue);
        warMemeRules.add(supremacistRule);

        changed |= ensureDoctrineDefinition("namespace:wde/war", "战争", "对于国家之间战争的渴望", List.of(
                createDoctrineAttitudeEntry("neutral", "中立", "战争作为政治上的最后手段，我们不会滥用也不会放弃"),
                createDoctrineAttitudeEntry("hate", "厌恶", "除了毫无意义的牺牲，战争还能带来什么?"),
                createDoctrineAttitudeEntry("want", "渴望", "只有鲜血才能实现生命的意义")
        ), warMemeRules);
        changed |= ensureDoctrineDefinition("namespace:wder/armor_wearing", "穿着护甲", "对于身体遮蔽与护甲穿着的态度", List.of(
                createDoctrineAttitudeEntry("naked", "全裸", "身体不该被护甲束缚，赤裸才是最自然的状态。"),
                createDoctrineAttitudeEntry("pants_only", "仅裤子", "最低限度的遮蔽已经足够，其余护甲都显得多余。"),
                createDoctrineAttitudeEntry("unrestricted", "无规则", "穿不穿护甲都只是个人选择，无需上升为信条。"),
                createDoctrineAttitudeEntry("fully_covered", "完全遮蔽", "身体应被严密遮蔽，完整护甲象征体面与秩序。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/cannibalism", "食人", "对于食用同类尸体的态度", List.of(
                createDoctrineAttitudeEntry("abhor", "厌恶", "食人是最污秽的禁忌，应被彻底排斥。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "食人的想法令人本能恐惧，最好远远避开。"),
                createDoctrineAttitudeEntry("disgust", "反感", "这种行为令人不适，即便不是绝对禁忌也不应鼓励。"),
                createDoctrineAttitudeEntry("accept", "认可", "在特定处境下，食人可以被视为可接受的选择。"),
                createDoctrineAttitudeEntry("enjoy", "喜好", "食用同类被当作某种独特而强烈的偏好。"),
                createDoctrineAttitudeEntry("crave", "渴望", "对人肉有明显追求，它被看作特殊满足。"),
                createDoctrineAttitudeEntry("greedy", "贪婪", "对血肉的占有欲永无止境，食人被视为应被放纵的欲望。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/blood_feeding", "血食", "对于饮用或食用血液的态度", List.of(
                createDoctrineAttitudeEntry("abhor", "厌恶", "饮血是不可接受的禁忌。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "血食令人不安，应当回避。"),
                createDoctrineAttitudeEntry("disgust", "反感", "排斥食用血液。"),
                createDoctrineAttitudeEntry("accept", "认可", "血食是可以接受的进食方式。"),
                createDoctrineAttitudeEntry("enjoy", "喜好", "血食带来满足。"),
                createDoctrineAttitudeEntry("crave", "渴望", "渴望饮血或食用血液。"),
                createDoctrineAttitudeEntry("greedy", "贪婪", "对血食有强烈而持续的需求。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/multiculturalism", "多元文化", "对于外来文化与异质群体的态度", List.of(
                createDoctrineAttitudeEntry("strong_prejudice", "强烈的偏见", "异文化被视为威胁，必须保持明确隔阂。"),
                createDoctrineAttitudeEntry("measured_prejudice", "适当的偏见", "可以接触外来者，但必须始终保留戒备与优先级差异。"),
                createDoctrineAttitudeEntry("mild_prejudice", "温和的偏见", "对外来文化不完全信任，但也不必彻底拒绝。"),
                createDoctrineAttitudeEntry("neutral", "中立", "文化差异只是事实本身，不特别赞扬也不特别排斥。"),
                createDoctrineAttitudeEntry("approve", "认可", "不同文化能共处并互补，多样性值得接纳。"),
                createDoctrineAttitudeEntry("highly_approve", "高度认可", "文化交融会让共同体更强大，应积极欢迎。"),
                createDoctrineAttitudeEntry("noble", "高尚", "尊重多元并保护差异，本身就是一种高贵德性。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/display_of_heads", "枭示", "对于公开枭首示众的态度", List.of(
                createDoctrineAttitudeEntry("disgust", "反感", "以头颅示众过于残忍低劣，不应成为常态。"),
                createDoctrineAttitudeEntry("crave", "渴望", "枭示能传递威慑与荣耀，值得主动追求。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/slaughter_animals", "宰杀动物", "对于主动宰杀动物的态度", List.of(
                createDoctrineAttitudeEntry("taboo", "禁忌", "杀生本身就是禁忌，轻易动刀不可接受。"),
                createDoctrineAttitudeEntry("fear", "恐惧", "屠宰令人本能不安，应尽可能回避。"),
                createDoctrineAttitudeEntry("oppose", "反对", "不赞同主动宰杀，但并未上升到绝对禁令。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "这只是日常生存行为，没有特别道德倾向。"),
                createDoctrineAttitudeEntry("support", "支持", "宰杀动物是正当资源获取方式，应被允许。"),
                createDoctrineAttitudeEntry("love", "喜爱", "屠宰与分肉被当作令人满足的事务。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/betrayal", "变节", "对于背叛、倒戈与失节的态度", List.of(
                createDoctrineAttitudeEntry("hostile", "敌视", "背叛者必须被视为永远的敌人。"),
                createDoctrineAttitudeEntry("abhor", "厌恶", "变节令人深恶痛绝，几乎不可原谅。"),
                createDoctrineAttitudeEntry("disgust", "反感", "虽不至于绝对敌对，但仍应受到明显贬斥。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "立场转换只是现实选择，并不天然带有道德污点。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/kindness", "仁善", "对于仁慈、宽和与善行的态度", List.of(
                createDoctrineAttitudeEntry("required", "必须", "善良不是可选美德，而是每个人必须遵守的准则。"),
                createDoctrineAttitudeEntry("important", "重点", "仁善值得被重点强调，是理想共同体的重要基础。"),
                createDoctrineAttitudeEntry("joy", "愉悦", "施与善意能带来积极满足，应被鼓励。"),
                createDoctrineAttitudeEntry("neutral", "无感", "仁善并非核心价值，有没有都不是重点。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/potions", "药水", "对于药水使用目的与频率的态度", List.of(
                createDoctrineAttitudeEntry("forbidden", "禁止", "药水会破坏纯粹与秩序，应被严格禁用。"),
                createDoctrineAttitudeEntry("medical_only", "仅医疗", "药水只应用于治疗与救命，不应用于其他用途。"),
                createDoctrineAttitudeEntry("medical_or_social", "医疗或社交",
                        "治疗与社交仪式中的药水都可接受，但不应滥用；饮用负面效果药水会额外获得醉生梦死。"),
                createDoctrineAttitudeEntry("required", "必须",
                        "药水是文明生活的重要组成部分，应被主动使用；饮用负面效果药水会额外获得醉生梦死。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/killing_innocent_animals", "杀戮无辜动物", "对于伤害无害动物的态度", List.of(
                createDoctrineAttitudeEntry("abhor", "厌恶", "无辜动物不该被屠害，这种行为令人厌恶。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "这种伤害会引起本能恐惧，最好彻底避开。"),
                createDoctrineAttitudeEntry("disgust", "反感", "并不认同这种行为，哪怕并非绝对禁令。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "这不被视为特殊道德议题。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/lighting", "照明", "对于光照环境的偏好", List.of(
                createDoctrineAttitudeEntry("neutral", "无态度", "亮与暗都只是环境条件，没有特殊偏好。"),
                createDoctrineAttitudeEntry("dim", "微光", "只喜爱低于7的光照；夜晚忽略天空光，只按方块光计算。"),
                createLightingCraveAttitudeEntry()
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/field_battle", "野战", "对于开阔地正面作战的态度", List.of(
                createDoctrineAttitudeEntry("enjoy", "喜好", "在旷野正面交锋才能体现勇武与决断。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "野战只是一种战斗方式，本身并无价值倾向。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/meat_eating", "肉食", "对于食肉与素食倾向的态度", List.of(
                createDoctrineAttitudeEntry("abhor_meat", "厌恶", "食肉令人排斥，克制肉欲才算纯净。"),
                createDoctrineAttitudeEntry("fear_meat", "畏惧", "肉食让人感到本能不安，应尽可能远离。"),
                createDoctrineAttitudeEntry("disgust_meat", "反感", "虽然并非绝对禁忌，但仍明显不喜欢肉食。"),
                createDoctrineAttitudeEntry("anti_vegetarian_disgust", "反感素食", "排斥长期素食，认为它违背正常生活。"),
                createDoctrineAttitudeEntry("anti_vegetarian_hate", "憎恶素食", "将素食视为严重偏差，难以容忍。"),
                createDoctrineAttitudeEntry("anti_vegetarian_abhor", "厌恶素食", "素食被看作令人厌恶的禁欲倾向，应明确抵制。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/mining", "采矿", "对于深入地下采掘资源的态度", List.of(
                createDoctrineAttitudeEntry("taboo", "禁忌", "挖掘地下会惊扰不该触碰的东西，因此必须禁绝。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "矿洞深处令人恐惧，不应轻率涉足。"),
                createDoctrineAttitudeEntry("disgust", "反感", "采矿并不受欢迎，最好只在不得已时进行。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/raiding", "掠袭", "对于掠夺式袭击的态度", List.of(
                createDoctrineAttitudeEntry("valued", "重视", "掠袭是展示力量与获取财富的重要方式。"),
                createDoctrineAttitudeEntry("required", "必要", "若想生存和扩张，掠袭几乎不可避免。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "是否掠袭取决于现实局势，并非信条核心。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/logging", "伐木", "对于砍伐树木的态度", List.of(
                createDoctrineAttitudeEntry("taboo", "禁忌", "伤害树木就是触犯禁忌，不能轻易进行。"),
                createDoctrineAttitudeEntry("fear", "畏惧", "砍树会带来不安和后果，应尽量避免。"),
                createDoctrineAttitudeEntry("disgust", "反感", "虽然并非绝对禁止，但仍不赞同滥伐。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "树木只是资源之一，取用与否无关信仰。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/trees", "树", "对于树木与林地本身的态度", List.of(
                createDoctrineAttitudeEntry("crave", "渴望", "树木、林冠与绿意本身就是值得主动追求的存在。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "树只是环境的一部分，没有特别倾向。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/nonhumanization", "非人化", "对于摆脱传统人类身份的态度", List.of(
                createDoctrineAttitudeEntry("required", "必需", "超越人类本身是进步与信条要求。"),
                createDoctrineAttitudeEntry("neutral", "无态度", "是否改变自身并不是价值核心。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/rituals", "仪式", "对于仪式与典礼行为的态度", List.of(
                createDoctrineAttitudeEntry("promote", "推崇", "仪式能塑造秩序、神圣感与共同体认同。"),
                createDoctrineAttitudeEntry("disgust", "反感", "繁复仪式令人厌烦，没有必要过度重视。"),
                createDoctrineAttitudeEntry("abhor", "厌恶", "仪式被视为虚饰与负担，应尽量摒弃。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/animal_resonance", "动物共鸣", "对于与动物建立精神共鸣的态度", List.of(
                createDoctrineAttitudeEntry("powerful", "强力", "与动物的共鸣被视为强大而珍贵的能力。"),
                createDoctrineAttitudeEntry("none", "无", "并不重视这种联系，也不赋予特殊意义。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/bonding", "牵绊", "对于深层情感连结与羁绊的态度", List.of(
                createDoctrineAttitudeEntry("disgust", "反感", "过深牵绊会削弱意志，应避免沉溺其中。"),
                createDoctrineAttitudeEntry("none", "无", "是否建立羁绊都只是个人选择。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/comfort", "舒适", "对于安逸、享受与舒适环境的态度", List.of(
                createDoctrineAttitudeEntry("indifferent", "漠视", "舒适并不重要，过度追求只会腐化心志。"),
                createDoctrineAttitudeEntry("none", "无", "舒适与否都不是信条关注的重点。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/pain", "痛苦", "对于痛苦与受难的态度", List.of(
                createDoctrineAttitudeEntry("ideal", "理想", "痛苦能提炼人格与意义，因此值得追求。"),
                createDoctrineAttitudeEntry("none", "无", "痛苦只是处境之一，并不带有特殊价值。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/proselytizing", "传教", "对于对外传播信条的积极程度", List.of(
                createDoctrineAttitudeEntry("occasional", "偶尔", "有机会时可以传播，但不必过度强求。"),
                createDoctrineAttitudeEntry("sometimes", "有时", "在合适场合主动传教是可以接受的常规行为。"),
                createDoctrineAttitudeEntry("frequent", "频繁", "持续而高频地扩张信仰，是应被鼓励的职责。"),
                createDoctrineAttitudeEntry("neutral", "无感", "是否传教都无所谓，不是核心任务。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/ranching", "畜牧", "对于驯养与规模化饲养动物的态度", List.of(
                createDoctrineAttitudeEntry("primary", "主要", "畜牧应成为生产与生活的主要支柱。"),
                createDoctrineAttitudeEntry("none", "无", "不特别强调畜牧，它只是众多手段之一。"),
                createDoctrineAttitudeEntry("oppose", "反对", "主动繁殖与扩大畜群违背信条，应当避免。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/roughing_it", "餐风露宿", "对于露天生存与艰苦环境的态度", List.of(
                createDoctrineAttitudeEntry("accept", "接纳", "风餐露宿是生活的一部分，应坦然接受。"),
                createDoctrineAttitudeEntry("neutral", "无感", "露宿并无特殊意义，只是环境条件。")
        ));
        changed |= ensureDoctrineDefinition("namespace:wder/indoors", "室内", "对于居住在室内空间的态度", List.of(
                createDoctrineAttitudeEntry("love", "喜爱", "有墙、有顶和封闭空间带来真正的安全与安宁。")
        ));
        return changed;
    }

    private boolean migrateDoctrineNamespace(String doctrinePath) {
        String oldBasePath = "doctrines.registry.namespace:wde/" + doctrinePath;
        ConfigurationSection oldSection = ideologyConfig.getConfigurationSection(oldBasePath);
        if (oldSection == null) {
            return false;
        }

        String newBasePath = "doctrines.registry.namespace:wder/" + doctrinePath;
        for (String relativePath : oldSection.getKeys(true)) {
            if (oldSection.isConfigurationSection(relativePath)) {
                continue;
            }
            String newPath = newBasePath + "." + relativePath;
            if (!ideologyConfig.contains(newPath)) {
                ideologyConfig.set(newPath, oldSection.get(relativePath));
            }
        }
        ideologyConfig.set(oldBasePath, null);
        return true;
    }

    /** Migrates only definitions that the administrator kept in the configuration. */
    private boolean migrateExistingDoctrineNamespaces() {
        if (ideologyConfig == null) {
            return false;
        }
        boolean changed = false;
        for (String doctrinePath : List.of(
                "armor_wearing",
                "cannibalism",
                "blood_feeding",
                "multiculturalism",
                "display_of_heads",
                "slaughter_animals",
                "betrayal",
                "kindness",
                "potions",
                "killing_innocent_animals",
                "lighting",
                "field_battle",
                "meat_eating",
                "mining",
                "raiding",
                "logging",
                "trees",
                "nonhumanization",
                "rituals",
                "animal_resonance",
                "bonding",
                "comfort",
                "pain",
                "proselytizing",
                "ranching",
                "roughing_it",
                "indoors")) {
            changed |= migrateDoctrineNamespace(doctrinePath);
        }
        return changed;
    }

    /** Removes the retired spiritual-vision doctrine from existing configuration files. */
    private boolean removeSpiritualVisionDoctrineMigration() {
        String migrationPath = "internalMigrations.spiritualVisionDoctrineRemovedV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        ideologyConfig.set("doctrines.registry.namespace:wder/spiritual_vision", null);
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    private boolean ensureDoctrineDefinition(String doctrineId,
                                            String title,
                                            String lore,
                                            List<Map<String, Object>> attitudes) {
        return ensureDoctrineDefinition(doctrineId, title, lore, attitudes, null);
    }

    private boolean ensureDoctrineDefinition(String doctrineId,
                                            String title,
                                            String lore,
                                            List<Map<String, Object>> attitudes,
                                            List<Map<String, Object>> memeRules) {
        String basePath = "doctrines.registry." + doctrineId;
        boolean changed = false;
        if (!ideologyConfig.contains(basePath + ".title")) {
            ideologyConfig.set(basePath + ".title", title);
            changed = true;
        }
        if (!ideologyConfig.contains(basePath + ".lore")) {
            ideologyConfig.set(basePath + ".lore", lore);
            changed = true;
        }
        if (memeRules != null && !ideologyConfig.contains(basePath + ".meme")) {
            ideologyConfig.set(basePath + ".meme", memeRules);
            changed = true;
        }
        if (!ideologyConfig.contains(basePath + ".attitudes")) {
            List<String> ids = attitudes.stream().map(entry -> doctrineId + "/" + entry.keySet().iterator().next())
                    .toList();
            String preferred = com.thatguycy.worlddynamicsengine.model.IdeologyDoctrineDefinition
                    .preferredDefaultAttitudeId(doctrineId, ids);
            String preferredLocalId = preferred.substring(preferred.lastIndexOf('/') + 1);
            List<Map<String, Object>> ordered = new ArrayList<>(attitudes);
            ordered.sort((left, right) -> Boolean.compare(
                    right.containsKey(preferredLocalId), left.containsKey(preferredLocalId)));
            ideologyConfig.set(basePath + ".attitudes", ordered);
            changed = true;
        }
        return changed;
    }

    private Map<String, Object> createDoctrineAttitudeEntry(String attitudeId, String title, String lore) {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", title);
        value.put("lore", lore);
        entry.put(attitudeId, value);
        return entry;
    }

    private Map<String, Object> createLightingCraveAttitudeEntry() {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", "渴求");
        value.put("color", "&d");
        value.put("moodWeight", 1);
        value.put("moodChange", Map.of("compliant", 1, "violation", 1));
        value.put("lore", "必须身处大于7的光照中；夜晚忽略天空光，只按方块光计算。");
        entry.put("crave", value);
        return entry;
    }

    /** Adds this attitude once while preserving any custom lighting attitudes. */
    private boolean ensureLightingCraveAttitudeMigration() {
        String migrationPath = "internalMigrations.lightingCraveAttitudeV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;

        String attitudesPath = "doctrines.registry.namespace:wder/lighting.attitudes";
        Object configuredAttitudes = ideologyConfig.get(attitudesPath);
        if (configuredAttitudes instanceof List<?> entries) {
            boolean alreadyPresent = entries.stream().anyMatch(entry -> hasAttitudeId(entry, "crave"));
            if (!alreadyPresent) {
                List<Object> updated = new ArrayList<>(entries);
                updated.add(createLightingCraveAttitudeEntry());
                ideologyConfig.set(attitudesPath, updated);
            }
        } else if (configuredAttitudes instanceof Map<?, ?> entries) {
            boolean alreadyPresent = entries.keySet().stream()
                    .anyMatch(key -> "crave".equals(localId(String.valueOf(key))));
            if (!alreadyPresent) {
                ideologyConfig.set(attitudesPath + ".crave",
                        createLightingCraveAttitudeEntry().get("crave"));
            }
        } else if (ideologyConfig.isConfigurationSection(attitudesPath)) {
            ConfigurationSection section = ideologyConfig.getConfigurationSection(attitudesPath);
            if (section != null && !section.contains("crave")) {
                ideologyConfig.set(attitudesPath + ".crave",
                        createLightingCraveAttitudeEntry().get("crave"));
            }
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds STONE once to existing mining lists without overriding later admin edits. */
    private boolean ensureMiningStoneMigration() {
        String migrationPath = "internalMigrations.miningStoneV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;

        String oresPath = "mood.doctrineActions.mining.ores.vanilla";
        Object configuredOres = ideologyConfig.get(oresPath);
        if (configuredOres instanceof List<?> entries) {
            boolean alreadyPresent = entries.stream().anyMatch(entry -> {
                String id = String.valueOf(entry).trim();
                return "STONE".equalsIgnoreCase(id) || "minecraft:stone".equalsIgnoreCase(id);
            });
            if (!alreadyPresent) {
                List<Object> updated = new ArrayList<>(entries);
                updated.add("STONE");
                ideologyConfig.set(oresPath, updated);
            }
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds the ranching opposition attitude to existing registries without replacing custom attitudes. */
    private boolean ensureRanchingOpposeAttitudeMigration() {
        String migrationPath = "internalMigrations.ranchingOpposeAttitudeV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;

        String attitudesPath = "doctrines.registry.namespace:wder/ranching.attitudes";
        Object configuredAttitudes = ideologyConfig.get(attitudesPath);
        if (configuredAttitudes instanceof List<?> entries) {
            boolean alreadyPresent = entries.stream().anyMatch(entry -> hasAttitudeId(entry, "oppose"));
            if (!alreadyPresent) {
                List<Object> updated = new ArrayList<>(entries);
                updated.add(createRanchingOpposeAttitudeEntry());
                ideologyConfig.set(attitudesPath, updated);
            }
        } else if (configuredAttitudes instanceof Map<?, ?> entries) {
            boolean alreadyPresent = entries.keySet().stream()
                    .anyMatch(key -> "oppose".equals(localId(String.valueOf(key))));
            if (!alreadyPresent) {
                ideologyConfig.set(attitudesPath + ".oppose",
                        createRanchingOpposeAttitudeEntry().get("oppose"));
            }
        } else if (ideologyConfig.isConfigurationSection(attitudesPath)) {
            ConfigurationSection section = ideologyConfig.getConfigurationSection(attitudesPath);
            if (section != null && !section.contains("oppose")) {
                ideologyConfig.set(attitudesPath + ".oppose",
                        createRanchingOpposeAttitudeEntry().get("oppose"));
            }
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Converts legacy scalar outcome IDs to one-item lists without changing configured IDs. */
    private boolean normalizeMemeOutcomeEffectLists() {
        var registry = ideologyConfig.getConfigurationSection("memes.registry");
        if (registry == null) return false;

        boolean changed = false;
        for (String memeId : registry.getKeys(false)) {
            for (String outcome : List.of("collapse", "inspiration")) {
                String path = "memes.registry." + memeId + ".moodEffects." + outcome;
                Object configuredEffects = ideologyConfig.get(path);
                if (!(configuredEffects instanceof String effectId)) continue;
                String normalized = effectId.trim();
                ideologyConfig.set(path, normalized.isEmpty() ? List.of() : List.of(normalized));
                changed = true;
            }
        }
        return changed;
    }

    /** Adds the new nature-primacy inspiration to old configs without replacing custom meme effects. */
    private boolean ensureNaturePrimacyTamingInspirationMigration() {
        String migrationPath = "internalMigrations.naturePrimacyTamingInspirationV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        if (!ideologyConfig.contains("memes.registry.nature_primacy")) {
            ideologyConfig.set(migrationPath, true);
            return true;
        }

        String effectsPath = "memes.registry.nature_primacy.moodEffects.inspiration";
        Object configuredEffects = ideologyConfig.get(effectsPath);
        if (configuredEffects == null) {
            ideologyConfig.set(effectsPath, List.of("animal_taming"));
        } else if (configuredEffects instanceof Collection<?> effects) {
            boolean alreadyPresent = effects.stream()
                    .anyMatch(effect -> "animal_taming".equalsIgnoreCase(String.valueOf(effect)));
            if (!alreadyPresent && !effects.isEmpty()) {
                List<Object> updated = new ArrayList<>(effects);
                updated.add("animal_taming");
                ideologyConfig.set(effectsPath, updated);
            }
        } else if (!"animal_taming".equalsIgnoreCase(String.valueOf(configuredEffects))) {
            ideologyConfig.set(effectsPath,
                    List.of(String.valueOf(configuredEffects), "animal_taming"));
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds the nightingale inspiration to old darkness memes without replacing custom effects. */
    private boolean ensureDarknessNightingaleInspirationMigration() {
        String migrationPath = "internalMigrations.darknessNightingaleInspirationV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        if (!ideologyConfig.contains("memes.registry.darkness")) {
            ideologyConfig.set(migrationPath, true);
            return true;
        }

        String effectsPath = "memes.registry.darkness.moodEffects.inspiration";
        Object configuredEffects = ideologyConfig.get(effectsPath);
        if (configuredEffects == null) {
            ideologyConfig.set(effectsPath, List.of("nightingale"));
        } else if (configuredEffects instanceof Collection<?> effects) {
            boolean alreadyPresent = effects.stream()
                    .anyMatch(effect -> "nightingale".equalsIgnoreCase(String.valueOf(effect)));
            if (!alreadyPresent && !effects.isEmpty()) {
                List<Object> updated = new ArrayList<>(effects);
                updated.add("nightingale");
                ideologyConfig.set(effectsPath, updated);
            }
        } else if (!"nightingale".equalsIgnoreCase(String.valueOf(configuredEffects))) {
            ideologyConfig.set(effectsPath,
                    List.of(String.valueOf(configuredEffects), "nightingale"));
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds the consolation inspiration to old proselytizer memes without replacing custom effects. */
    private boolean ensureProselytizerConsolationInspirationMigration() {
        String migrationPath = "internalMigrations.proselytizerConsolationInspirationV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        if (!ideologyConfig.contains("memes.registry.proselytizer")) {
            ideologyConfig.set(migrationPath, true);
            return true;
        }

        String effectsPath = "memes.registry.proselytizer.moodEffects.inspiration";
        Object configuredEffects = ideologyConfig.get(effectsPath);
        if (configuredEffects == null) {
            ideologyConfig.set(effectsPath, List.of("consolation"));
        } else if (configuredEffects instanceof Collection<?> effects) {
            boolean alreadyPresent = effects.stream()
                    .anyMatch(effect -> "consolation".equalsIgnoreCase(String.valueOf(effect)));
            if (!alreadyPresent && !effects.isEmpty()) {
                List<Object> updated = new ArrayList<>(effects);
                updated.add("consolation");
                ideologyConfig.set(effectsPath, updated);
            }
        } else if (!"consolation".equalsIgnoreCase(String.valueOf(configuredEffects))) {
            ideologyConfig.set(effectsPath,
                    List.of(String.valueOf(configuredEffects), "consolation"));
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds the action incentive to old nudist memes without replacing custom effects. */
    private boolean ensureNudistActionIncentiveInspirationMigration() {
        String migrationPath = "internalMigrations.nudistActionIncentiveInspirationV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        if (!ideologyConfig.contains("memes.registry.nudist")) {
            ideologyConfig.set(migrationPath, true);
            return true;
        }

        String effectsPath = "memes.registry.nudist.moodEffects.inspiration";
        Object configuredEffects = ideologyConfig.get(effectsPath);
        if (configuredEffects == null) {
            ideologyConfig.set(effectsPath, List.of("action_incentive"));
        } else if (configuredEffects instanceof Collection<?> effects) {
            boolean alreadyPresent = effects.stream()
                    .anyMatch(effect -> "action_incentive".equalsIgnoreCase(String.valueOf(effect)));
            if (!alreadyPresent && !effects.isEmpty()) {
                List<Object> updated = new ArrayList<>(effects);
                updated.add("action_incentive");
                ideologyConfig.set(effectsPath, updated);
            }
        } else if (!"action_incentive".equalsIgnoreCase(String.valueOf(configuredEffects))) {
            ideologyConfig.set(effectsPath,
                    List.of(String.valueOf(configuredEffects), "action_incentive"));
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds the mining incentive to old tunneler memes without replacing custom effects. */
    private boolean ensureTunnelerMiningIncentiveInspirationMigration() {
        String migrationPath = "internalMigrations.tunnelerMiningIncentiveInspirationV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        if (!ideologyConfig.contains("memes.registry.tunneler")) {
            ideologyConfig.set(migrationPath, true);
            return true;
        }

        String effectsPath = "memes.registry.tunneler.moodEffects.inspiration";
        Object configuredEffects = ideologyConfig.get(effectsPath);
        if (configuredEffects == null) {
            ideologyConfig.set(effectsPath, List.of("mining_incentive"));
        } else if (configuredEffects instanceof Collection<?> effects) {
            boolean alreadyPresent = effects.stream()
                    .anyMatch(effect -> "mining_incentive".equalsIgnoreCase(String.valueOf(effect)));
            if (!alreadyPresent && !effects.isEmpty()) {
                List<Object> updated = new ArrayList<>(effects);
                updated.add("mining_incentive");
                ideologyConfig.set(effectsPath, updated);
            }
        } else if (!"mining_incentive".equalsIgnoreCase(String.valueOf(configuredEffects))) {
            ideologyConfig.set(effectsPath,
                    List.of(String.valueOf(configuredEffects), "mining_incentive"));
        }
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    /** Adds the pain-virtue collapse and inspiration effects without replacing custom lists. */
    private boolean ensurePainVirtueMoodEffectMigration() {
        String migrationPath = "internalMigrations.painVirtueMoodEffectsV1";
        if (ideologyConfig.getBoolean(migrationPath, false)) return false;
        if (!ideologyConfig.contains("memes.registry.pain_is_virtue")) {
            ideologyConfig.set(migrationPath, true);
            return true;
        }

        ensureMemeOutcomeEffect("pain_is_virtue", "collapse", "self_harm");
        ensureMemeOutcomeEffect("pain_is_virtue", "inspiration", "stretch");
        ideologyConfig.set(migrationPath, true);
        return true;
    }

    private void ensureMemeOutcomeEffect(String memeId, String outcome, String effectId) {
        String effectsPath = "memes.registry." + memeId + ".moodEffects." + outcome;
        Object configuredEffects = ideologyConfig.get(effectsPath);
        if (configuredEffects == null) {
            ideologyConfig.set(effectsPath, List.of(effectId));
        } else if (configuredEffects instanceof Collection<?> effects) {
            boolean alreadyPresent = effects.stream()
                    .anyMatch(effect -> effectId.equalsIgnoreCase(String.valueOf(effect)));
            if (!alreadyPresent && !effects.isEmpty()) {
                List<Object> updated = new ArrayList<>(effects);
                updated.add(effectId);
                ideologyConfig.set(effectsPath, updated);
            }
        } else if (!effectId.equalsIgnoreCase(String.valueOf(configuredEffects))) {
            ideologyConfig.set(effectsPath, List.of(String.valueOf(configuredEffects), effectId));
        }
    }

    private Map<String, Object> createRanchingOpposeAttitudeEntry() {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", "反对");
        value.put("color", "&c");
        value.put("moodChange", Map.of("compliant", 1, "violation", 1));
        value.put("lore", "主动繁殖与扩大畜群违背信条，应当避免。");
        entry.put("oppose", value);
        return entry;
    }

    private static boolean hasAttitudeId(Object entry, String expectedLocalId) {
        if (!(entry instanceof Map<?, ?> attitudeEntry)) return false;
        return attitudeEntry.keySet().stream()
                .anyMatch(key -> expectedLocalId.equals(localId(String.valueOf(key))));
    }

    private static String localId(String id) {
        int separator = id.lastIndexOf('/');
        return (separator < 0 ? id : id.substring(separator + 1))
                .trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean migrateLegacyDoctrineRegistryToMemeRegistry() {
        if (ideologyConfig == null || !ideologyConfig.isConfigurationSection("doctrines.registry")) {
            return false;
        }

        boolean changed = false;
        ConfigurationSection doctrineRegistry = ideologyConfig.getConfigurationSection("doctrines.registry");
        if (doctrineRegistry == null) {
            return false;
        }

        List<String> migratedIds = new ArrayList<>();
        for (String id : doctrineRegistry.getKeys(false)) {
            // Namespaced entries are doctrine IDs; only legacy bare meme IDs belong here.
            if (!id.matches("^[a-z0-9_.-]+$")) {
                continue;
            }

            String sourceBasePath = "doctrines.registry." + id;
            String targetBasePath = "memes.registry." + id;
            if (!ideologyConfig.contains(targetBasePath + ".title")) {
                ideologyConfig.set(targetBasePath + ".title", ideologyConfig.getString(sourceBasePath + ".title", id));
                changed = true;
            }
            if (!ideologyConfig.contains(targetBasePath + ".description")) {
                ideologyConfig.set(targetBasePath + ".description", ideologyConfig.getString(sourceBasePath + ".description", ""));
                changed = true;
            }
            migratedIds.add(id);
        }

        for (String migratedId : migratedIds) {
            ideologyConfig.set("doctrines.registry." + migratedId, null);
            changed = true;
        }
        ConfigurationSection remainingDoctrines = ideologyConfig.getConfigurationSection("doctrines.registry");
        if (remainingDoctrines != null && remainingDoctrines.getKeys(false).isEmpty()) {
            ideologyConfig.set("doctrines", null);
            changed = true;
        }
        return changed;
    }
}
