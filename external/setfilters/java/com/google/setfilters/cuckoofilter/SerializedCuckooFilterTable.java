// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.setfilters.cuckoofilter;

import java.util.Arrays;

/** Serialization of {@link CuckooFilterTable}. */
public final class SerializedCuckooFilterTable {
  private final byte[] rawSerialization;

  /** Creates serialization from raw byte array. */
  public static SerializedCuckooFilterTable createFromByteArray(byte[] byteArray) {
    return new SerializedCuckooFilterTable(Arrays.copyOf(byteArray, byteArray.length));
  }

  private SerializedCuckooFilterTable(byte[] rawSerialization) {
    this.rawSerialization = rawSerialization;
  }

  /** Returns the serialization as a byte array. */
  public byte[] asByteArray() {
    return Arrays.copyOf(rawSerialization, rawSerialization.length);
  }

  // TODO: Add other methods like asJSON();
}
