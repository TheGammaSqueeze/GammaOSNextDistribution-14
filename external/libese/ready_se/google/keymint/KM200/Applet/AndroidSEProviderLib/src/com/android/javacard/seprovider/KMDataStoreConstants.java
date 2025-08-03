package com.android.javacard.seprovider;

/**
 * This class holds different interface type constants to differentiate between the instances of
 * Computed Hmac key, device unique key pair, RKP Mac key, and master key when passed as generic
 * objects. These constants are used in upgrade flow to retrieve the size of the object and
 * primitive types saved and restored for respective key types.
 */
public class KMDataStoreConstants {
  // INTERFACE Types
  public static final byte INTERFACE_TYPE_COMPUTED_HMAC_KEY = 0x01;
  // 0x02 reserved for INTERFACE_TYPE_ATTESTATION_KEY
  public static final byte INTERFACE_TYPE_DEVICE_UNIQUE_KEY_PAIR = 0x03;
  public static final byte INTERFACE_TYPE_MASTER_KEY = 0x04;
  public static final byte INTERFACE_TYPE_PRE_SHARED_KEY = 0x05;
  public static final byte INTERFACE_TYPE_RKP_MAC_KEY = 0x06;
}
