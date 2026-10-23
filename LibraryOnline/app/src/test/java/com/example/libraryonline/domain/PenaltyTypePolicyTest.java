package com.example.libraryonline.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.libraryonline.domain.validator.PenaltyTypePolicy;

import org.junit.Test;

/** Only overdue return fines may be collected through the active circulation UI. */
public class PenaltyTypePolicyTest {
    @Test public void overdueReturnIsCollectible() {
        assertTrue(PenaltyTypePolicy.canCollect("LateReturn"));
    }

    @Test public void oldLossCompensationCannotBeCollectedViaFineWorkflow() {
        assertFalse(PenaltyTypePolicy.canCollect("LostBook"));
    }

    @Test public void unknownOrEmptyTypeCannotBeCollected() {
        assertFalse(PenaltyTypePolicy.canCollect(null));
        assertFalse(PenaltyTypePolicy.canCollect(""));
        assertFalse(PenaltyTypePolicy.canCollect("Other"));
    }
}
