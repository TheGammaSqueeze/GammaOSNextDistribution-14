package com.android.javacard.keymaster;

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * KMSimpleValue corresponds to CBOR type of Simple value. It holds either true, false or NULL
 * values. The structure is defined as struct{byte SIMPLE_VALUE_TYPE; short length; simple value }
 */
public class KMSimpleValue extends KMType {

  public static final byte FALSE = (byte) 20;
  public static final byte TRUE = (byte) 21;
  public static final byte NULL = (byte) 22;
  private static KMSimpleValue prototype;

  private KMSimpleValue() {}

  private static KMSimpleValue proto(short ptr) {
    if (prototype == null) {
      prototype = new KMSimpleValue();
    }
    instanceTable[KM_SIMPLE_VALUE_OFFSET] = ptr;
    return prototype;
  }

  // pointer to an empty instance used as expression
  public static short exp() {
    return KMType.exp(SIMPLE_VALUE_TYPE);
  }

  public static KMSimpleValue cast(short ptr) {
    if (heap[ptr] != SIMPLE_VALUE_TYPE) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    if (!isSimpleValueValid(heap[(short) (ptr + 3)])) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    return proto(ptr);
  }

  public static short instance(byte value) {
    if (!isSimpleValueValid(value)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    short ptr = KMType.instance(SIMPLE_VALUE_TYPE, (short) 1);
    heap[(short) (ptr + 3)] = value;
    return ptr;
  }

  private static boolean isSimpleValueValid(byte value) {
    switch (value) {
      case TRUE:
      case FALSE:
      case NULL:
        break;
      default:
        return false;
    }
    return true;
  }

  public short length() {
    return Util.getShort(heap, (short) (instanceTable[KM_SIMPLE_VALUE_OFFSET] + 1));
  }

  public byte getValue() {
    return heap[(short) (instanceTable[KM_SIMPLE_VALUE_OFFSET] + 3)];
  }
}
