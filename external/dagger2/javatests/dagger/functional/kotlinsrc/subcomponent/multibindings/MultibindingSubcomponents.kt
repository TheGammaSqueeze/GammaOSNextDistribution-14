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

package dagger.functional.kotlinsrc.subcomponent.multibindings

import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.StringKey
import java.util.Objects
import javax.inject.Inject

class MultibindingSubcomponents {
  /** Multibindings for this type are bound only in the parent component. */
  enum class BoundInParent {
    INSTANCE
  }

  /** Multibindings for this type are bound only in the child component. */
  enum class BoundInChild {
    INSTANCE
  }

  /** Multibindings for this type are bound in the parent component and the child component. */
  enum class BoundInParentAndChild {
    IN_PARENT,
    IN_CHILD
  }

  class RequiresMultibindings<T>
  @Inject
  constructor(
    val set: Set<@JvmSuppressWildcards T>,
    val map: Map<String, @JvmSuppressWildcards T>
  ) {
    override fun equals(other: Any?): Boolean =
      other is RequiresMultibindings<*> && set == other.set && map == other.map

    override fun hashCode(): Int = Objects.hash(set, map)

    override fun toString(): String =
      "${RequiresMultibindings::class.java.simpleName}{set=$set, map=$map}"
  }

  @Module
  internal abstract class ParentMultibindingModule {
    // This is not static because otherwise we have no tests that cover the case where a
    // subcomponent uses a module instance installed onto a parent component.
    @Binds
    @IntoSet
    abstract fun requiresMultibindingsInParentAndChildElement(
      requiresMultibindingsInParentAndChild: RequiresMultibindings<BoundInParentAndChild>
    ): RequiresMultibindings<BoundInParentAndChild>

    companion object {
      @Provides @IntoSet fun onlyInParentElement(): BoundInParent = BoundInParent.INSTANCE

      @Provides
      @IntoMap
      @StringKey("parent key")
      fun onlyInParentEntry(): BoundInParent = BoundInParent.INSTANCE

      @Provides
      @IntoSet
      fun inParentAndChildElement(): BoundInParentAndChild = BoundInParentAndChild.IN_PARENT

      @Provides
      @IntoMap
      @StringKey("parent key")
      fun inParentAndChildEntry(): BoundInParentAndChild = BoundInParentAndChild.IN_PARENT
    }
  }

  @Module
  internal object ChildMultibindingModule {
    @Provides
    @IntoSet
    fun inParentAndChildElement(): BoundInParentAndChild = BoundInParentAndChild.IN_CHILD

    @Provides
    @IntoMap
    @StringKey("child key")
    fun inParentAndChildEntry(): BoundInParentAndChild = BoundInParentAndChild.IN_CHILD

    @Provides @IntoSet fun onlyInChildElement(): BoundInChild = BoundInChild.INSTANCE

    @Provides
    @IntoMap
    @StringKey("child key")
    fun onlyInChildEntry(): BoundInChild = BoundInChild.INSTANCE
  }

  @Module
  internal abstract class ChildMultibindingModuleWithOnlyBindsMultibindings {
    @Binds
    @IntoSet
    abstract fun bindsLocalContribution(instance: BoundInParentAndChild): BoundInParentAndChild

    @Binds
    @IntoMap
    @StringKey("child key")
    abstract fun inParentAndChildEntry(instance: BoundInParentAndChild): BoundInParentAndChild

    @Binds @IntoSet abstract fun inChild(instance: BoundInChild): BoundInChild

    @Binds
    @IntoMap
    @StringKey("child key")
    abstract fun inChildEntry(instance: BoundInChild): BoundInChild

    companion object {
      @Provides
      fun provideBoundInParentAndChildForBinds(): BoundInParentAndChild =
        BoundInParentAndChild.IN_CHILD

      @Provides fun provideBoundInChildForBinds(): BoundInChild = BoundInChild.INSTANCE
    }
  }

  interface ProvidesBoundInParent {
    fun requiresMultibindingsBoundInParent(): RequiresMultibindings<BoundInParent>
  }

  interface ProvidesBoundInChild {
    fun requiresMultibindingsBoundInChild(): RequiresMultibindings<BoundInChild>
  }

  interface ProvidesBoundInParentAndChild {
    fun requiresMultibindingsBoundInParentAndChild(): RequiresMultibindings<BoundInParentAndChild>
  }

  interface ProvidesSetOfRequiresMultibindings {
    fun setOfRequiresMultibindingsInParentAndChild():
      Set<RequiresMultibindings<BoundInParentAndChild>>
  }

  interface ParentWithProvision :
    ProvidesBoundInParent, ProvidesBoundInParentAndChild, ProvidesSetOfRequiresMultibindings

  interface HasChildWithProvision {
    fun childWithProvision(): ChildWithProvision
  }

  interface HasChildWithoutProvision {
    fun childWithoutProvision(): ChildWithoutProvision
  }

  @Component(modules = [ParentMultibindingModule::class])
  interface ParentWithoutProvisionHasChildWithoutProvision : HasChildWithoutProvision

  @Component(modules = [ParentMultibindingModule::class])
  interface ParentWithoutProvisionHasChildWithProvision : HasChildWithProvision

  @Component(modules = [ParentMultibindingModule::class])
  interface ParentWithProvisionHasChildWithoutProvision :
    ParentWithProvision, HasChildWithoutProvision

  @Component(modules = [ParentMultibindingModule::class])
  interface ParentWithProvisionHasChildWithProvision :
    ParentWithProvision, HasChildWithProvision

  @Subcomponent(modules = [ChildMultibindingModule::class])
  interface ChildWithoutProvision {
    fun grandchild(): Grandchild
  }

  @Subcomponent(modules = [ChildMultibindingModule::class])
  interface ChildWithProvision :
    ProvidesBoundInParent,
    ProvidesBoundInParentAndChild,
    ProvidesBoundInChild,
    ProvidesSetOfRequiresMultibindings {
    fun grandchild(): Grandchild
  }

  @Subcomponent
  interface Grandchild :
    ProvidesBoundInParent,
    ProvidesBoundInParentAndChild,
    ProvidesBoundInChild,
    ProvidesSetOfRequiresMultibindings

  @Component(modules = [ParentMultibindingModule::class])
  interface ParentWithProvisionHasChildWithBinds : ParentWithProvision {
    fun childWithBinds(): ChildWithBinds
  }

  @Subcomponent(modules = [ChildMultibindingModuleWithOnlyBindsMultibindings::class])
  interface ChildWithBinds : ChildWithProvision
}
