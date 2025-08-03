// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

/**
 * Provides secure randomness using {@link SecureRandom}.
 *
 * @since 1.0.0
 */
public final class Random {

  /** Returns a random byte array of size {@code size}. */
  public static byte[] randBytes(int size) {
    return com.google.crypto.tink.internal.Random.randBytes(size);
  }

  /** Returns a random int between 0 and max-1. */
  public static final int randInt(int max) {
    return com.google.crypto.tink.internal.Random.randInt(max);
  }

  /** Returns a random int. */
  public static final int randInt() {
    return com.google.crypto.tink.internal.Random.randInt();
  }

  private Random() {}
}
