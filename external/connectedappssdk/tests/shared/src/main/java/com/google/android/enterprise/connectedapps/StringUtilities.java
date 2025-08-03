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
package com.google.android.enterprise.connectedapps;

import java.util.Random;

public class StringUtilities {
  private StringUtilities() {}

  private static final long RANDOM_SEED = 1;

  /** Generate a random String of the given length. */
  public static String randomString(int length) {
    Random r = new Random(RANDOM_SEED);
    char[] chars = new char[length];
    for (int i = 0; i < length; i++) {
      chars[i] = (char) (r.nextInt(26) + 'a');
    }
    return new String(chars);
  }
}
