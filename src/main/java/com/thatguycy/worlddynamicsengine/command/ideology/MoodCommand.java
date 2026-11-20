package com.thatguycy.worlddynamicsengine.command.ideology;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import com.thatguycy.worlddynamicsengine.manager.ideology.InspirationManager;
import com.thatguycy.worlddynamicsengine.manager.ideology.MoodManager;
import com.thatguycy.worlddynamicsengine.model.WDEresident;
import com.thatguycy.worlddynamicsengine.util.TextColors;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shows the sender's active mood deltas. */
public final class MoodCommand implements CommandExecutor {
    private final WorldDynamicsEngine plugin;

    public MoodCommand(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "common_only_players");
            return true;
        }
        if (args.length != 1) {
            send(sender, "mood_details_usage");
            return true;
        }

        MoodManager moods = plugin.getMoodManager();
        if (moods == null) {
            send(sender, "mood_details_unavailable");
            return true;
        }
        send(sender, "mood_details_header", moods.getMood(player));
        WDEresident resident = plugin.getResidentManager().getResident(player.getUniqueId());
        sendInspirationStatus(sender, resident);
        long now = System.currentTimeMillis();
        moods.pruneExemptMoodPreviews();
        int expectation = moods.getExpectationModifier(player);
        if (moods.isMoodExempt(player)) {
            sendExpectation(sender, expectation);
            String exemptionMessage = resident != null && resident.isDehumanized()
                    ? "mood_details_dehumanized"
                    : player.isOp() ? "mood_details_op_exempt" : "mood_details_creative_exempt";
            send(sender, exemptionMessage);
            sendMoodEntries(sender, moods.getExemptMoodPreviews(player, now), now);
            return true;
        }

        int recordedMood = resident == null ? 50 : resident.getMoodAt(now);
        send(sender, "mood_details_recorded", recordedMood);
        sendExpectation(sender, expectation);

        List<WDEresident.MoodChangeEntry> changes = resident == null
                ? List.of() : resident.getActiveMoodChanges(now);
        sendMoodEntries(sender, changes, now);
        return true;
    }

    private void sendMoodEntries(CommandSender sender, List<WDEresident.MoodChangeEntry> changes, long now) {
        if (changes.isEmpty()) {
            send(sender, "mood_details_empty");
            return;
        }
        for (WDEresident.MoodChangeEntry change : changes) {
            String color = change.delta() > 0 ? TextColors.GREEN
                    : change.delta() < 0 ? TextColors.RED : TextColors.GRAY;
            String delta = change.delta() > 0 ? "+" + change.delta() : Integer.toString(change.delta());
            if (change.permanent()) {
                send(sender, "mood_details_entry_permanent", color, delta, sourceName(change.source()));
            } else {
                send(sender, "mood_details_entry", color, delta, sourceName(change.source()),
                        formatRemaining(change.expiresAt() - now));
            }
        }
    }

    private void sendInspirationStatus(CommandSender sender, WDEresident resident) {
        if (resident == null) return;
        int arrows = resident.getInspirationCharges(InspirationManager.ARROW_ARMOR_PIERCING);
        int melee = resident.getInspirationCharges(InspirationManager.MELEE_ARMOR_PIERCING);
        if (arrows > 0 || melee > 0) {
            send(sender, "mood_details_inspiration_pending", arrows, melee);
        }
    }

    private void sendExpectation(CommandSender sender, int expectation) {
        String color = expectation > 0 ? TextColors.GREEN
                : expectation < 0 ? TextColors.RED : TextColors.GRAY;
        String value = expectation > 0 ? "+" + expectation : Integer.toString(expectation);
        send(sender, "mood_details_expectation", color, value);
    }

    private String sourceName(String source) {
        if (source == null || source.isBlank()) return message("mood_details_source_unknown", "unknown");
        if (source.startsWith("ritual_absence|")) {
            return message("mood_details_source_ritual_absence");
        }
        String[] doctrineParts = parseDoctrineSource(source);
        if (doctrineParts != null) {
            String doctrineId = doctrineParts[0];
            return message(doctrineParts[2].equals("compliant")
                            ? "mood_details_source_doctrine_compliant"
                            : "mood_details_source_doctrine_violation",
                    plugin.getReligionManager().getDoctrineDisplayName(doctrineId),
                    plugin.getReligionManager().getDoctrineAttitudeDisplayName(doctrineId, doctrineParts[1]));
        }
        if (source.startsWith("food:")) {
            String key = switch (source.substring("food:".length()).toLowerCase(Locale.ROOT)) {
                case "vegetarian" -> "mood_details_source_food_vegetarian";
                case "meat" -> "mood_details_source_food_meat";
                case "human_meat" -> "mood_details_source_food_human_meat";
                case "blood" -> "mood_details_source_food_blood";
                default -> "mood_details_source_unknown";
            };
            return message(key, source);
        }
        if (source.startsWith("pain:ideal:sweet_pain:")) {
            String threshold = source.substring("pain:ideal:sweet_pain:".length());
            if (threshold.matches("\\d{1,3}")) {
                return message("mood_details_source_pain_sweet", threshold);
            }
        }
        if (source.startsWith("pain:none:under_")) {
            String threshold = source.substring("pain:none:under_".length());
            if (threshold.matches("\\d{1,3}")) {
                return message("mood_details_source_pain_none", threshold);
            }
        }
        String key = switch (source) {
            case "ritual_satisfaction" -> "mood_details_source_ritual_satisfaction";
            case "animalKilling" -> "mood_details_source_animal";
            case "humanMeat" -> "mood_details_source_food_human_meat";
            case "relief" -> "mood_details_source_relief";
            case "potion:inebriation" -> "mood_details_source_potion_inebriation";
            case "manual" -> "mood_details_source_manual";
            case "doctrine" -> "mood_details_source_doctrine";
            case "rebirth" -> "mood_details_source_rebirth";
            case "townDeath" -> "mood_details_source_town_death";
            case "betrayal" -> "mood_details_source_betrayal";
            case "betrayalHunt" -> "mood_details_source_betrayer_hunt";
            case "bonding:pets" -> "mood_details_source_bonding_pets";
            case "bondingPetDeath" -> "mood_details_source_bonding_pet_death";
            case "pain:ideal:unease" -> "mood_details_source_pain_unease";
            case "temporary" -> "mood_details_source_temporary";
            default -> "mood_details_source_unknown";
        };
        return message(key, source);
    }

    private static String[] parseDoctrineSource(String source) {
        if (source.startsWith("doctrine|")) {
            String[] parts = source.split("\\|", 4);
            return parts.length == 4 && isDoctrineOutcome(parts[3])
                    ? new String[] {parts[1], parts[2], parts[3].toLowerCase(Locale.ROOT)} : null;
        }
        if (!source.startsWith("doctrine:")) return null;

        String outcome;
        if (source.endsWith(":compliant")) outcome = "compliant";
        else if (source.endsWith(":violation")) outcome = "violation";
        else return null;

        String body = source.substring("doctrine:".length(), source.length() - outcome.length() - 1);
        int separator = body.indexOf(":namespace:");
        if (separator < 0) return null;
        return new String[] {body.substring(0, separator), body.substring(separator + 1), outcome};
    }

    private static boolean isDoctrineOutcome(String outcome) {
        return outcome.equalsIgnoreCase("compliant") || outcome.equalsIgnoreCase("violation");
    }

    private static String formatRemaining(long remainingMillis) {
        long seconds = Math.max(0, (remainingMillis + 999L) / 1000L);
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        long remainder = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, remainder);
    }

    private void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(TextColors.translateAlternateColorCodes('&', message(key, args)));
    }

    private String message(String key, Object... args) {
        return plugin.formatLocaleMessage(key, args);
    }
}
