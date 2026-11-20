package com.thatguycy.worlddynamicsengine.manager.ideology;

import java.util.Locale;

/** Applies roughing-it mood only when an accepting believer sleeps outdoors. */
public final class RoughingItDoctrineCalculator {
    private RoughingItDoctrineCalculator() {}

    public static Boolean assessOutdoorSleep(String attitude, boolean outdoors) {
        return outdoors && "accept".equals(localId(attitude)) ? Boolean.TRUE : null;
    }

    private static String localId(String attitude) {
        if (attitude == null || attitude.isBlank()) return "";
        int separator = attitude.lastIndexOf('/');
        return (separator < 0 ? attitude : attitude.substring(separator + 1))
                .trim().toLowerCase(Locale.ROOT);
    }
}
