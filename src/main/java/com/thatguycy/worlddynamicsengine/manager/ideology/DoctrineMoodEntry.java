package com.thatguycy.worlddynamicsengine.manager.ideology;

/** One weighted mood change attributed to a specific doctrine attitude. */
public record DoctrineMoodEntry(String doctrineId, String attitudeId, boolean compliant, int moodChange) {
    public String source() {
        return "doctrine|" + doctrineId + "|" + attitudeId + "|"
                + (compliant ? "compliant" : "violation");
    }
}
