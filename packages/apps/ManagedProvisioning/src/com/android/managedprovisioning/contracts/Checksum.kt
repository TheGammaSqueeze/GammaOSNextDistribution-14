package com.android.managedprovisioning.contracts

sealed interface Checksum {
    val bytes: ByteArray

    /**
     * SHA-256 hash of the .apk file
     */
    @JvmInline
    value class PackageChecksum(override val bytes: ByteArray) : Checksum

    /**
     * SHA-256 hash of the signature in the .apk file
     */
    @JvmInline
    value class SignatureChecksum(override val bytes: ByteArray) : Checksum
}