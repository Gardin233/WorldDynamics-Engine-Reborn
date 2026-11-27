package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AlchemyDependencyCalculatorTest {
    @Test
    void satisfactionMapsToFourConfiguredStages() {
        assertEquals(AlchemyDependencyCalculator.Stage.SATISFIED,
                AlchemyDependencyCalculator.stageAt(75, 75, 50, 25));
        assertEquals(AlchemyDependencyCalculator.Stage.DEFICIENT,
                AlchemyDependencyCalculator.stageAt(50, 75, 50, 25));
        assertEquals(AlchemyDependencyCalculator.Stage.CRAVING,
                AlchemyDependencyCalculator.stageAt(25, 75, 50, 25));
        assertEquals(AlchemyDependencyCalculator.Stage.MADNESS,
                AlchemyDependencyCalculator.stageAt(24.9, 75, 50, 25));
    }

    @Test
    void consecutiveDrinksRaiseDependencyChanceUntilConfiguredMaximum() {
        assertEquals(10, AlchemyDependencyCalculator.dependencyChancePercent(1, 10, 10, 100));
        assertEquals(20, AlchemyDependencyCalculator.dependencyChancePercent(2, 10, 10, 100));
        assertEquals(30, AlchemyDependencyCalculator.dependencyChancePercent(3, 10, 10, 100));
        assertEquals(100, AlchemyDependencyCalculator.dependencyChancePercent(20, 10, 10, 100));
        assertEquals(25, AlchemyDependencyCalculator.dependencyChancePercent(5, 80, 10, 25));
    }

}
