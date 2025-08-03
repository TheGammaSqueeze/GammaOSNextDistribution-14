/*
 * Copyright(C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" (short)0IS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.javacard.seprovider;

import javacard.security.ECPublicKey;
import javacard.security.KeyPair;
import org.globalplatform.upgrade.Element;

/** This is a wrapper class for KeyPair. */
public class KMECDeviceUniqueKeyPair implements KMKey {

  public KeyPair ecKeyPair;

  @Override
  public short getPublicKey(byte[] buf, short offset) {
    ECPublicKey publicKey = (ECPublicKey) ecKeyPair.getPublic();
    return publicKey.getW(buf, offset);
  }

  public KMECDeviceUniqueKeyPair(KeyPair ecPair) {
    ecKeyPair = ecPair;
  }

  public static void onSave(Element element, KMECDeviceUniqueKeyPair kmKey) {
    element.write(kmKey.ecKeyPair);
  }

  public static KMECDeviceUniqueKeyPair onRestore(KeyPair ecKey) {
    if (ecKey == null) {
      return null;
    }
    return new KMECDeviceUniqueKeyPair(ecKey);
  }

  public static short getBackupPrimitiveByteCount() {
    return (short) 0;
  }

  public static short getBackupObjectCount() {
    return (short) 1;
  }
}
