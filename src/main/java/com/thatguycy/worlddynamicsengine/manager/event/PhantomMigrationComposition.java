package com.thatguycy.worlddynamicsengine.manager.event;

final class PhantomMigrationComposition {
    private static final int MAXIMUM_ALLOWED_PHANTOMS = 128;

    private PhantomMigrationComposition() {
    }

    static int phantomCount(int strengthPoints, double phantomsPerStrengthPoint,
                            int minimumPhantoms, int maximumPhantoms) {
        if (strengthPoints <= 0 || !Double.isFinite(phantomsPerStrengthPoint)
                || phantomsPerStrengthPoint <= 0.0D || maximumPhantoms <= 0) {
            return 0;
        }
        int maximum = Math.min(MAXIMUM_ALLOWED_PHANTOMS, maximumPhantoms);
        int minimum = Math.min(maximum, Math.max(1, minimumPhantoms));
        double requested = Math.ceil(strengthPoints * phantomsPerStrengthPoint);
        if (!Double.isFinite(requested)) {
            return maximum;
        }
        return Math.max(minimum, (int) Math.min(maximum, requested));
    }
}
