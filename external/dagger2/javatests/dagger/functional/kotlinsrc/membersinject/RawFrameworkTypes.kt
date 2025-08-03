/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.membersinject

import dagger.Lazy
import dagger.MembersInjector
import javax.inject.Provider

// https://github.com/google/dagger/issues/419
// Note: This is converted from its associate java test in dagger/function/membersinject, but given
// that there isn't actually raw types in kotlin I'm not sure how useful it is to keep it. For the
// time being, I've decided to leave this as it tests the next closest thing, which is star types.
class RawFrameworkTypes {
  fun nonInjectMethodWithARawProvider(@Suppress("UNUSED_PARAMETER") rawProvider: Provider<*>) {}
  fun nonInjectMethodWithARawLazy(@Suppress("UNUSED_PARAMETER") rawLazy: Lazy<*>) {}
  fun nonInjectMethodWithARawMembersInjector(
    @Suppress("UNUSED_PARAMETER") rawMembersInjector: MembersInjector<*>
  ) {}
}
