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

/**
 * Regression test for b/143465893: inlining this function requires access to the inner class
 * used by the inlined helper [makeAppender], which uses suspend functions.
 */
suspend inline fun doubleInline(also: String, crossinline init: suspend () -> String): String {
  return makeAppender { init() }.apply(also)
}

inline fun makeAppender(crossinline init: suspend () -> String): Suspended<String> {
  return object : Suspended<String> {
    override suspend fun apply(toAppend: String) = init() + toAppend
  }
}

interface Suspended<T> {
  suspend fun apply(input: T): T
}
