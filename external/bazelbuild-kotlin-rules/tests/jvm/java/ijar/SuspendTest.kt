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

import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.resume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests exercising suspend functions. */
@RunWith(JUnit4::class)
class SuspendTest {

  /** Regression test for b/143465893. */
  @Test
  fun testSuspendDoubleInline() {
    suspend fun doubleInlineExample(): String {
      return doubleInline("b") { "a" }
    }

    // Run doubleInlineExample avoiding dependency on kotlinx.coroutines
    var result: Result<String>? = null
    ::doubleInlineExample.startCoroutine(Continuation(EmptyCoroutineContext, { r -> result = r }))

    assertThat(result!!.getOrThrow()).isEqualTo("ab")
  }
}
