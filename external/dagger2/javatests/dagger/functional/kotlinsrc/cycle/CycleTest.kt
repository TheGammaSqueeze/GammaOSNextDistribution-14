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

package dagger.functional.kotlinsrc.cycle

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CycleTest {
  @Test
  fun providerIndirectionSelfCycle() {
    val selfCycleComponent = DaggerCycles_SelfCycleComponent.create()
    val s = selfCycleComponent.s()
    assertThat(s.sProvider.get()).isNotNull()
  }

  @Test
  fun providerIndirectionCycle() {
    val cycleComponent = DaggerCycles_CycleComponent.create()
    val a = cycleComponent.a()
    val c = cycleComponent.c()
    assertThat(c.aProvider.get()).isNotNull()
    assertThat(a.b.c.aProvider.get()).isNotNull()
    assertThat(a.e.d.b.c.aProvider.get()).isNotNull()
  }

  @Test
  fun lazyIndirectionSelfCycle() {
    val selfCycleComponent = DaggerCycles_SelfCycleComponent.create()
    val s = selfCycleComponent.s()
    assertThat(s.sLazy.get()).isNotNull()
  }

  @Test
  fun lazyIndirectionCycle() {
    val cycleComponent = DaggerCycles_CycleComponent.create()
    val a = cycleComponent.a()
    val c = cycleComponent.c()
    assertThat(c.aLazy.get()).isNotNull()
    assertThat(a.b.c.aLazy.get()).isNotNull()
    assertThat(a.e.d.b.c.aLazy.get()).isNotNull()
  }

  @Test
  fun subcomponentIndirectionCycle() {
    val childCycleComponent = DaggerCycles_CycleComponent.create().child()
    val a = childCycleComponent.a()
    assertThat(a.b.c.aProvider.get()).isNotNull()
    assertThat(a.e.d.b.c.aProvider.get()).isNotNull()
  }

  @Test
  fun providerMapIndirectionCycle() {
    val cycleMapComponent = DaggerCycles_CycleMapComponent.create()
    assertThat(cycleMapComponent.y()).isNotNull()
    assertThat(cycleMapComponent.y().mapOfProvidersOfX).containsKey("X")
    assertThat(cycleMapComponent.y().mapOfProvidersOfX.get("X")).isNotNull()
    assertThat(cycleMapComponent.y().mapOfProvidersOfX.get("X")!!.get()).isNotNull()
    assertThat(cycleMapComponent.y().mapOfProvidersOfX.get("X")!!.get().y).isNotNull()
    assertThat(cycleMapComponent.y().mapOfProvidersOfX).hasSize(1)
    assertThat(cycleMapComponent.y().mapOfProvidersOfY).containsKey("Y")
    assertThat(cycleMapComponent.y().mapOfProvidersOfY.get("Y")).isNotNull()
    assertThat(cycleMapComponent.y().mapOfProvidersOfY.get("Y")!!.get()).isNotNull()
    assertThat(cycleMapComponent.y().mapOfProvidersOfY.get("Y")!!.get().mapOfProvidersOfX).hasSize(1)
    assertThat(cycleMapComponent.y().mapOfProvidersOfY.get("Y")!!.get().mapOfProvidersOfY).hasSize(1)
    assertThat(cycleMapComponent.y().mapOfProvidersOfY).hasSize(1)
  }

  /**
   * Tests that a cycle where a `@Binds` binding depends on a binding that has to be deferred works.
   */
  @Test
  fun cycleWithDeferredBinds() {
    val bindsCycleComponent = DaggerCycles_BindsCycleComponent.create()
    assertThat(bindsCycleComponent.bar()).isNotNull()
  }
}
