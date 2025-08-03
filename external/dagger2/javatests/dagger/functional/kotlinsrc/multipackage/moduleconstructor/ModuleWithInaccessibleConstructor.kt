/*
 * Copyright (C) 2023 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.functional.kotlinsrc.multipackage.moduleconstructor

import dagger.Module
import dagger.Provides
import kotlin.random.Random

@Module
class ModuleWithInaccessibleConstructor
/* intentionally package private */
internal constructor() {
  private val i: Int = Random.nextInt()

  @Provides fun i(): Int = i

  // This is a regression test for b/283164293
  private companion object {
    fun someMethod(): String = "someString"
  }
}
