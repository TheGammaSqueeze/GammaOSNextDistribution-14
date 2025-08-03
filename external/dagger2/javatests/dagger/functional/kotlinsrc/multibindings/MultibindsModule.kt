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

package dagger.functional.kotlinsrc.multibindings

import dagger.Module
import dagger.multibindings.Multibinds
import javax.inject.Named

/**
 * A module that uses [@Multibinds][Multibinds]-annotated abstract methods to declare multibindings.
 */
@Module
internal abstract class MultibindsModule {
  @Multibinds abstract fun emptySet(): Set<Any>

  @Multibinds abstract fun emptyMap(): Map<String, Any>

  @Multibinds abstract fun set(): Set<CharSequence>

  @Multibinds abstract fun map(): Map<String, CharSequence>

  @Multibinds @Named("complexQualifier") abstract fun emptyQualifiedSet(): Set<Any>

  @Multibinds @Named("complexQualifier") abstract fun emptyQualifiedMap(): Map<String, Any>

  @Multibinds @Named("complexQualifier") abstract fun qualifiedSet(): Set<CharSequence>

  @Multibinds @Named("complexQualifier") abstract fun qualifiedMap(): Map<String, CharSequence>
}
