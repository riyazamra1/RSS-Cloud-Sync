package com.riyaz.rsscloudsync

/** Small release-safety helpers used to gate destructive operations behind verification. */
object TransferVerification {
    fun sameSize(expectedBytes: Long, actualBytes: Long): Boolean =
        expectedBytes <= 0L || expectedBytes == actualBytes

    fun canDeleteAfterVerifiedTransfer(expectedBytes: Long, actualBytes: Long): Boolean =
        sameSize(expectedBytes, actualBytes)
}
