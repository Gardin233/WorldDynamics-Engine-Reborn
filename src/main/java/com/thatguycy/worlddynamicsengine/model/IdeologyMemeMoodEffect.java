package com.thatguycy.worlddynamicsengine.model;

import java.util.List;

/** An effect identifier made available by a meme over an inclusive mood range. */
public record IdeologyMemeMoodEffect(int minimumMood, int maximumMood, List<String> effectIds) {
    public IdeologyMemeMoodEffect {
        minimumMood = Math.max(0, Math.min(100, minimumMood));
        maximumMood = Math.max(minimumMood, Math.min(100, maximumMood));
        effectIds = effectIds == null ? List.of() : List.copyOf(effectIds);
    }

    public boolean matches(int mood) {
        return mood >= minimumMood && mood <= maximumMood;
    }
}
