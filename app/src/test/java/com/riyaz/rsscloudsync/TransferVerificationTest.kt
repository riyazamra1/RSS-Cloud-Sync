package com.riyaz.rsscloudsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferVerificationTest {
    @Test
    fun exactSizeIsVerified() {
        assertTrue(TransferVerification.sameSize(1024L, 1024L))
        assertFalse(TransferVerification.sameSize(1024L, 1023L))
    }

    @Test
    fun zeroByteTransfersMustMatchExactly() {
        assertTrue(TransferVerification.canDeleteAfterVerifiedTransfer(0L, 0L))
        assertFalse(TransferVerification.canDeleteAfterVerifiedTransfer(0L, 1L))
    }

    @Test
    fun unknownExpectedSizeDoesNotBlockVerification() {
        assertTrue(TransferVerification.canDeleteAfterVerifiedTransfer(-1L, 123L))
    }
}
