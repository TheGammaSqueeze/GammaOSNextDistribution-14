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

package ijar

import java.util.function.Function

// kotlinc requires the anonymous inner class of this function to be on the classpath in order to
// inline this function.
inline fun appender(append: String): Function<String, String> {
  return object : Function<String, String> {
    override fun apply(arg: String) = arg + append
  }
}

// Additional regression test for https://youtrack.jetbrains.net/issue/KT-29471
inline fun appender(crossinline init: () -> String): Function<String, String> {
  return object : Function<String, String> {
    private val base = init()
    override fun apply(arg: String) = base + arg
  }
}
