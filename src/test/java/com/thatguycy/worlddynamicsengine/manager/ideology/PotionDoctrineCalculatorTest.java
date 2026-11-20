package com.thatguycy.worlddynamicsengine.manager.ideology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.junit.jupiter.api.Test;

class PotionDoctrineCalculatorTest {
    @Test
    void classifiesAnyHarmfulEffect() {
        var harmful = PotionDoctrineCalculator.inspectEffects(
                List.of(PotionEffectTypeCategory.BENEFICIAL, PotionEffectTypeCategory.HARMFUL));

        assertTrue(harmful.beneficial());
        assertTrue(harmful.debuff());
        assertFalse(PotionDoctrineCalculator.inspectEffects(List.of()).debuff());
    }

    @Test
    void onlyListedPotionTypesOrItemsQualifyForInebriationBonus() {
        assertTrue(PotionDoctrineCalculator.matchesInebriationPotion(
                "POISON", null, Set.of("poison"), Set.of()));
        assertTrue(PotionDoctrineCalculator.matchesInebriationPotion(
                null, "Alchemy:Poison_Tonic", Set.of(), Set.of("alchemy:poison_tonic")));
        assertFalse(PotionDoctrineCalculator.matchesInebriationPotion(
                "HEALING", null, Set.of("poison"), Set.of()));
    }

    @Test
    void appliesBaseAttitudesAndOnlyGrantsInebriationBonusToAllowedAttitudes() {
        var beneficial = new PotionDoctrineCalculator.EffectProfile(true, false);
        var harmful = new PotionDoctrineCalculator.EffectProfile(false, true);

        assertFalse(PotionDoctrineCalculator.assess("forbidden", beneficial, false).compliant());
        assertTrue(PotionDoctrineCalculator.assess("medical_only", beneficial, false).compliant());
        assertFalse(PotionDoctrineCalculator.assess("medical_only", harmful, false).compliant());
        assertTrue(PotionDoctrineCalculator.assess("medical_or_social", harmful, true).compliant());
        assertTrue(PotionDoctrineCalculator.assess("medical_or_social", harmful, true).earnsInebriationBonus());
        assertTrue(PotionDoctrineCalculator.assess("required", harmful, true).earnsInebriationBonus());
        assertFalse(PotionDoctrineCalculator.assess("forbidden", harmful, true).earnsInebriationBonus());
        assertFalse(PotionDoctrineCalculator.assess("required", harmful, false).earnsInebriationBonus());
        assertNull(PotionDoctrineCalculator.assess("unknown", beneficial, false).compliant());
    }
}
