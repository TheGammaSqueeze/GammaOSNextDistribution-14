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

import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SubcomponentTest(val parentGetters: ParentGetters, val childComponent: ChildComponent) {
  @Test
  fun scopePropagatesUpward_class() {
    assertThat(childComponent.requiresSingleton().singletonType())
      .isSameInstanceAs(childComponent.requiresSingleton().singletonType())
    assertThat(childComponent.requiresSingleton().singletonType())
      .isSameInstanceAs(childComponent.newGrandchildComponent().requiresSingleton().singletonType())
  }

  @Test
  fun scopePropagatesUpward_provides() {
    assertThat(childComponent.requiresSingleton().unscopedTypeBoundAsSingleton())
      .isSameInstanceAs(childComponent.requiresSingleton().unscopedTypeBoundAsSingleton())
    assertThat(childComponent.requiresSingleton().unscopedTypeBoundAsSingleton())
      .isSameInstanceAs(
        childComponent.newGrandchildComponent().requiresSingleton().unscopedTypeBoundAsSingleton()
      )
  }

  @Test
  fun multibindingContributions() {
    val parentObjectSet: Set<Any> = parentGetters.objectSet()
    assertThat(parentObjectSet).hasSize(2)
    val childObjectSet: Set<Any> = childComponent.objectSet()
    assertThat(childObjectSet).hasSize(3)
    val grandchildObjectSet: Set<Any> = childComponent.newGrandchildComponent().objectSet()
    assertThat(grandchildObjectSet).hasSize(4)
    assertThat(Sets.intersection(parentObjectSet, childObjectSet)).hasSize(1)
    assertThat(Sets.intersection(parentObjectSet, grandchildObjectSet)).hasSize(1)
    assertThat(Sets.intersection(childObjectSet, grandchildObjectSet)).hasSize(1)
  }

  @Test
  fun unscopedProviders() {
    assume().that(System.getProperty("dagger.mode")).doesNotContain("FastInit")
    assertThat(parentGetters.getUnscopedTypeProvider())
      .isSameInstanceAs(childComponent.getUnscopedTypeProvider())
    assertThat(parentGetters.getUnscopedTypeProvider())
      .isSameInstanceAs(childComponent.newGrandchildComponent().getUnscopedTypeProvider())
  }

  @Test
  fun passedModules() {
    val childModuleWithState: ChildModuleWithState = ChildModuleWithState()
    val childComponent1: ChildComponentRequiringModules =
      parentComponent.newChildComponentRequiringModules(
        ChildModuleWithParameters(Any()),
        childModuleWithState
      )
    val childComponent2: ChildComponentRequiringModules =
      parentComponent.newChildComponentRequiringModules(
        ChildModuleWithParameters(Any()),
        childModuleWithState
      )
    assertThat(childComponent1.getInt()).isEqualTo(0)
    assertThat(childComponent2.getInt()).isEqualTo(1)
  }

  @Test
  fun dependenceisInASubcomponent() {
    assertThat(childComponent.newGrandchildComponent().needsAnInterface()).isNotNull()
  }

  @Test
  fun qualifiedSubcomponentIsBound() {
    assertThat(parentComponent.unresolvableChildComponentBuilder().build().unboundString())
      .isEqualTo("unbound")
  }

  companion object {
    private val parentComponent = DaggerParentComponent.create()
    private val parentOfGenericComponent = DaggerParentOfGenericComponent.create()

    @JvmStatic
    @Parameterized.Parameters
    fun parameters(): Collection<Array<Any>> {
      return listOf(
        arrayOf(parentComponent, parentComponent.newChildComponent()),
        arrayOf(parentComponent, parentComponent.newChildAbstractClassComponent()),
        arrayOf(parentOfGenericComponent, parentOfGenericComponent.subcomponent()),
      )
    }
  }
}
