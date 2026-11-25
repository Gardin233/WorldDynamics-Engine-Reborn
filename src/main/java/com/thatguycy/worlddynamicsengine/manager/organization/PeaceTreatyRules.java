package com.thatguycy.worlddynamicsengine.manager.organization;

import com.thatguycy.worlddynamicsengine.manager.ideology.RaidingDoctrineCalculator;
import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.PeaceTreatyClauseState;
import com.thatguycy.worlddynamicsengine.manager.organization.WarManager.PeaceTreatyClauseState.ClauseType;
import com.thatguycy.worlddynamicsengine.model.war.WarGoalState;
import com.thatguycy.worlddynamicsengine.model.war.WarReason;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.bukkit.configuration.ConfigurationSection;

/** Evaluates treaty terms without looking up nations, mutating war state or applying clauses. */
final class PeaceTreatyRules {
    private PeaceTreatyRules() {}

    static int points(List<PeaceTreatyClauseState> clauses, ConfigurationSection config,
                      boolean separatePeace, DoubleSupplier ideologyMultiplier) {
        double total = 0D;
        for (PeaceTreatyClauseState clause : clauses) {
            total += clausePoints(clause, config);
        }
        if (separatePeace && total > 0) total *= 2;
        return (int) Math.ceil(total * ideologyMultiplier.getAsDouble());
    }

    private static int clausePoints(PeaceTreatyClauseState clause, ConfigurationSection config) {
        if (clause == null || clause.getType() == null) return 0;
        return switch (clause.getType()) {
            case REPARATIONS -> Math.max(0, clause.getInt("percent"))
                    * cost(config, "peaceTreaty.clauses.reparations.pointsPerPercent", 1);
            case FORCE_CONVERSION -> cost(config, "peaceTreaty.clauses.forceConversionPoints", 15);
            case TRANSFER_TOWN -> cost(config, "peaceTreaty.clauses.transferTownPoints", 20);
            case DISSOLVE_ALLIANCE -> cost(config, "peaceTreaty.clauses.dissolveAlliancePoints", 30);
            case EXPEL_FROM_ALLIANCE -> cost(config, "peaceTreaty.clauses.expelFromAlliancePoints", 15);
            case LIBERATE_SUBJECT -> cost(config, "peaceTreaty.clauses.liberateSubjectPoints", 25);
            case UNCONDITIONAL_PASSAGE -> cost(config, "peaceTreaty.clauses.unconditionalPassagePoints", 10);
            case HUMILIATE -> cost(config, "peaceTreaty.clauses.humiliatePoints", 5);
            case LOOT_CAPITAL -> cost(config, "peaceTreaty.clauses.lootCapitalPoints", 20);
            case TRIBUTE -> cost(config, "peaceTreaty.clauses.tributePoints", 25);
            case ALLOW_INDEPENDENCE -> cost(config, "peaceTreaty.clauses.allowIndependencePoints", 40);
            case FORCE_VASSALIZE -> cost(config, "peaceTreaty.clauses.forceVassalizePoints", 60);
            case FORCE_DEMOCRATIZATION -> cost(config, "warGoals.clauses.forceDemocracyPoints", 60);
            case DISSOLVE_THREAT_ORGANIZATION -> cost(config, "warGoals.clauses.dissolveThreatOrganizationPoints", 70);
        };
    }

    private static int cost(ConfigurationSection config, String path, int fallback) {
        return Math.max(0, config.getInt(path, fallback));
    }

    static boolean matchesWarGoal(WarReason reason, WarGoalState goal, String attacker,
                                  PeaceTreatyClauseState clause) {
        return switch (reason) {
            case CONQUEST, RECONQUEST -> clause.getType() == ClauseType.TRANSFER_TOWN
                    && goal.getTargetTownName().equalsIgnoreCase(clause.getString("town"))
                    && attacker.equalsIgnoreCase(clause.getString("destinationNation"));
            case INDEPENDENCE -> clause.getType() == ClauseType.ALLOW_INDEPENDENCE;
            case HOLY_WAR -> clause.getType() == ClauseType.FORCE_CONVERSION;
            case EXPORT_REVOLUTION -> clause.getType() == ClauseType.FORCE_DEMOCRATIZATION
                    && !clause.getString("president").isBlank();
            case HUMILIATE_RIVAL -> clause.getType() == ClauseType.HUMILIATE;
            case DESTROY_THREAT -> clause.getType() == ClauseType.DISSOLVE_THREAT_ORGANIZATION
                    && goal.getTargetOrganizationName().equalsIgnoreCase(clause.getString("organization"));
            case TRADE_CONFLICT -> false;
        };
    }

    static boolean hasQualifyingRaidingTerms(List<PeaceTreatyClauseState> clauses) {
        if (clauses == null) return false;
        return clauses.stream().anyMatch(clause -> RaidingDoctrineCalculator.hasQualifyingEconomicTerms(
                clause.getType() == ClauseType.TRIBUTE,
                clause.getType() == ClauseType.REPARATIONS ? clause.getInt("percent") : 0));
    }
}
