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

package dagger.functional.kotlinsrc.generictypes

import com.google.common.truth.Truth.assertThat
import dagger.functional.kotlinsrc.generictypes.subpackage.Generic
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GenericTest {
  @Test
  fun testGenericComponentCreate() {
    val component: GenericComponent = DaggerGenericComponent.create()
    assertThat(component).isNotNull()
  }

  @Test
  fun testGenericSimpleReferences() {
    val component: GenericComponent = DaggerGenericComponent.create()
    assertThat(component.referencesGeneric().genericA.t).isNotNull()
  }

  @Test
  fun testGenericDoubleReferences() {
    val component = DaggerGenericComponent.create()
    val doubleA = component.doubleGenericA()
    assertThat(doubleA.a).isNotNull()
    assertThat(doubleA.a2).isNotNull()
    assertThat(doubleA.t).isNotNull()
    assertThat(doubleA.t2).isNotNull()
    val doubleB = component.doubleGenericB()
    assertThat(doubleB.a).isNotNull()
    assertThat(doubleB.a2).isNotNull()
    assertThat(doubleB.t).isNotNull()
    assertThat(doubleB.t2).isNotNull()
  }

  @Test
  fun complexGenerics() {
    val component = DaggerGenericComponent.create()
    // validate these can be called w/o exceptions.
    component.complexGenerics()
  }

  @Test
  fun noDepsGenerics() {
    val component = DaggerGenericComponent.create()
    // validate these can be called w/o exceptions.
    component.noDepsA()
    component.noDepsB()
  }

  @Test
  fun boundedGenerics() {
    val expected = BoundedGenericModule()
    val component = DaggerBoundedGenericComponent.create()
    val b1 = component.bounds1()
    assertThat(expected.provideInteger()).isEqualTo(b1.t1)
    assertThat(expected.provideArrayListString()).isEqualTo(b1.t2)
    assertThat(expected.provideLinkedListCharSeq()).isEqualTo(b1.t3)
    assertThat(expected.provideInteger()).isEqualTo(b1.t4)
    assertThat(expected.provideListOfInteger()).isEqualTo(b1.t5)
    val b2 = component.bounds2()
    assertThat(expected.provideDouble()).isEqualTo(b2.t1)
    assertThat(expected.provideLinkedListString()).isEqualTo(b2.t2)
    assertThat(expected.provideArrayListOfComparableString()).isEqualTo(b2.t3)
    assertThat(expected.provideDouble()).isEqualTo(b2.t4)
    assertThat(expected.provideSetOfDouble()).isEqualTo(b2.t5)
  }

  @Test
  fun membersInjections() {
    val component: GenericComponent = DaggerGenericComponent.create()
    val childA = GenericChild<A>()
    component.injectA(childA)
    assertThat(childA.a).isNotNull()
    assertThat(childA.b).isNotNull()
    assertThat(childA.registeredA).isNotNull()
    assertThat(childA.registeredB).isNotNull()
    assertThat(childA.registeredT).isNotNull()
    assertThat(childA.registeredX).isNotNull()
    assertThat(childA.registeredY).isNotNull()
    val childB = GenericChild<B>()
    component.injectB(childB)
    assertThat(childB.a).isNotNull()
    assertThat(childB.b).isNotNull()
    assertThat(childB.registeredA).isNotNull()
    assertThat(childB.registeredB).isNotNull()
    assertThat(childB.registeredT).isNotNull()
    assertThat(childB.registeredX).isNotNull()
    assertThat(childB.registeredY).isNotNull()
  }

  @Test
  fun packagePrivateTypeParameterDependencies() {
    val component = DaggerGenericComponent.create()
    val exposed = component.exposed()
    assertThat(exposed.gpp().t).isNotNull()
    assertThat(exposed.gpp2()).isNotNull()
  }

  @Test
  fun publicSubclassWithPackagePrivateTypeParameterOfSuperclass() {
    val component = DaggerGenericComponent.create()
    val publicSubclass = component.publicSubclass()
    assertThat((publicSubclass as Generic<*>).t).isNotNull()
  }

  @Test
  fun singletonScopesAppliesToEachResolvedType() {
    val component = DaggerSingletonGenericComponent.create()
    val a = component.scopedGenericA()
    assertThat(a).isSameInstanceAs(component.scopedGenericA())
    assertThat(a.t).isNotNull()
    val b = component.scopedGenericB()
    assertThat(b).isSameInstanceAs(component.scopedGenericB())
    assertThat(b.t).isNotNull()
    assertThat(a).isNotSameInstanceAs(b)
  }

  @Test // See https://github.com/google/dagger/issues/671
  fun scopedSimpleGenerics() {
    val component = DaggerSingletonGenericComponent.create()
    val a = component.scopedSimpleGenericA()
    assertThat(a).isSameInstanceAs(component.scopedSimpleGenericA())
    val b = component.scopedSimpleGenericB()
    assertThat(b).isSameInstanceAs(component.scopedSimpleGenericB())
    assertThat(a).isNotSameInstanceAs(b)
  }

  @Test
  fun genericModules() {
    val component = DaggerGenericComponent.create()
    assertThat(component.iterableInt()).containsExactly(1, 2).inOrder()
    assertThat(component.iterableDouble()).containsExactly(3.0, 4.0).inOrder()
  }
}
