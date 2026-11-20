package com.thatguycy.worlddynamicsengine.event;

import com.thatguycy.worlddynamicsengine.model.IdeologyMemeDefinition;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired for periodic mood checks and for a selected inspiration or collapse outcome. */
public final class PlayerMoodEvent extends Event {
    public enum Outcome {
        MOOD_CHECK,
        INSPIRATION,
        COLLAPSE
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int mood;
    private final Outcome outcome;
    private final double outcomeChancePercent;
    private final List<IdeologyMemeDefinition> memes;
    private final List<String> effectIds;

    public PlayerMoodEvent(Player player, int mood, Outcome outcome, double outcomeChancePercent,
                           List<IdeologyMemeDefinition> memes, List<String> effectIds) {
        this.player = player;
        this.mood = Math.max(0, Math.min(100, mood));
        this.outcome = outcome;
        this.outcomeChancePercent = Math.max(0.0, Math.min(100.0, outcomeChancePercent));
        this.memes = memes == null ? List.of() : List.copyOf(memes);
        this.effectIds = effectIds == null ? List.of() : List.copyOf(effectIds);
    }

    public Player getPlayer() {
        return player;
    }

    public int getMood() {
        return mood;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public double getOutcomeChancePercent() {
        return outcomeChancePercent;
    }

    public List<IdeologyMemeDefinition> getMemes() {
        return memes;
    }

    public List<String> getEffectIds() {
        return effectIds;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
