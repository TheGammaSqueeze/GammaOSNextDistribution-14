package com.android.javacard.seprovider;

/**
 * This interface helps to decouple Javacard internal key objects from the keymaster package. Using
 * Javacard key objects provides security by providing protection against side channel attacks.
 * KMAESKey, KMECDeviceUniqueKey and KMHmacKey implements this interface.
 */
public interface KMKey {
  short getPublicKey(byte[] buf, short offset);
}
