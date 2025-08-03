/*
 * * Copyright 2022 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jni

import com.google.common.truth.Truth.assertThat

/** Unit test for JNI function. */
fun main(args: Array<String>) {
  if (args.getOrNull(0) == "--load") {
    System.loadLibrary("NativeApi") // loads libNativeApi.so
  }

  assertThat(NativeApi.hello("World")).isEqualTo("Hello, World")

  assertThat(NativeApi.hello("0123456789".repeat(11) + "willbedropped"))
    .isEqualTo("Hello, " + "0123456789".repeat(11) + "willbedrop")
}
