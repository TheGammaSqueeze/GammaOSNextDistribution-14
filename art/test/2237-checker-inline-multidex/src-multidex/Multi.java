/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class Multi {
  public static String $inline$NeedsEnvironmentMultiDex(String str) {
    // StringBuilderAppend needs an environment but it doesn't need a .bss entry.
    StringBuilder sb = new StringBuilder();
    return sb.append(str).toString();
  }

  public static String $inline$NeedsBssEntryStringMultiDex() {
    return "def";
  }

  private static String $noinline$InnerInvokeMultiDex() {
    return "ghi";
  }

  public static String $inline$NeedsBssEntryInvokeMultiDex() {
    return $noinline$InnerInvokeMultiDex();
  }

  public static Class<?> NeedsBssEntryClassMultiDex() {
    return Multi2.class;
  }

  private class Multi2 {}

  public static int $inline$TryCatch(String str) {
    try {
      return Integer.parseInt(str);
    } catch (NumberFormatException ex) {
      return -1;
    }
  }
}
