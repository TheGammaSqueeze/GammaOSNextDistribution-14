/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.internal;

/** This class is only for internal use by the SDK. */
public final class ByteUtilities {
  private ByteUtilities() {}

  /** Join two byte arrays into a single byte array, with firstArray followed by secondArray. */
  public static byte[] joinByteArrays(byte[] firstArray, byte[] secondArray) {
    byte[] results = new byte[firstArray.length + secondArray.length];
    System.arraycopy(firstArray, 0, results, 0, firstArray.length);
    System.arraycopy(secondArray, 0, results, firstArray.length, secondArray.length);
    return results;
  }
}
