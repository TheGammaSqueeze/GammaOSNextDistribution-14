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

package dagger.functional.kotlinsrc.subcomponent.repeat

import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RepeatedModuleTest {
  private lateinit var parentComponent: ParentComponent

  @Before
  fun initializeParentComponent() {
    parentComponent = DaggerParentComponent.builder().build()
  }

  @Test
  fun repeatedModuleHasSameStateInSubcomponent() {
    val childComponent = parentComponent.newChildComponentBuilder().build()
    assertThat(parentComponent.state()).isSameInstanceAs(childComponent.state())
  }

  @Test
  fun repeatedModuleHasSameStateInGrandchildSubcomponent() {
    val childComponent = parentComponent.newChildComponentWithoutRepeatedModule()
    val grandchildComponent: SubcomponentWithRepeatedModule =
      childComponent.newGrandchildBuilder().build()
    assertThat(parentComponent.state()).isSameInstanceAs(grandchildComponent.state())
  }

  @Test
  fun repeatedModuleBuilderThrowsInSubcomponent() {
    val childComponentBuilder = parentComponent.newChildComponentBuilder()
    try {
      childComponentBuilder.repeatedModule(RepeatedModule())
      Assert.fail()
    } catch (expected: UnsupportedOperationException) {
      assertThat(expected)
        .hasMessageThat()
        .isEqualTo(
          "dagger.functional.kotlinsrc.subcomponent.repeat.RepeatedModule cannot be set " +
            "because it is inherited from the enclosing component"
        )
    }
  }

  @Test
  fun repeatedModuleBuilderThrowsInGrandchildSubcomponent() {
    val childComponent = parentComponent.newChildComponentWithoutRepeatedModule()
    val grandchildComponentBuilder = childComponent.newGrandchildBuilder()
    try {
      grandchildComponentBuilder.repeatedModule(RepeatedModule())
      Assert.fail()
    } catch (expected: UnsupportedOperationException) {
      assertThat(expected)
        .hasMessageThat()
        .isEqualTo(
          "dagger.functional.kotlinsrc.subcomponent.repeat.RepeatedModule cannot be set " +
            "because it is inherited from the enclosing component"
        )
    }
  }
}
