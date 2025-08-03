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

package dagger.functional.kotlinsrc.subcomponent

import dagger.Component
import javax.inject.Singleton

@Component(modules = [ParentModule::class, UnresolvableChildComponentModule::class])
@Singleton
interface ParentComponent : ParentGetters {
  fun newChildComponent(): ChildComponent
  fun newChildAbstractClassComponent(): ChildAbstractClassComponent
  fun newChildComponentRequiringModules(
    cmwp: ChildModuleWithParameters,
    childModuleWithState: ChildModuleWithState
  ): ChildComponentRequiringModules

  /**
   * Requests a qualified version of this subcomponent builder, which does not install it as a
   * subcomponent, but instead, uses the explicit binding of this qualified builder.
   */
  @SomeQualifier fun unresolvableChildComponentBuilder(): UnresolvableChildComponent.Builder
}
