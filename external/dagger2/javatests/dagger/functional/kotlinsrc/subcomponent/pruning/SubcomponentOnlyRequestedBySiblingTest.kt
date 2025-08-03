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

package dagger.functional.kotlinsrc.subcomponent.pruning

import com.google.common.truth.Truth.assertThat
import dagger.Subcomponent
import dagger.functional.kotlinsrc.subcomponent.pruning.ParentDoesntUseSubcomponent.ChildA
import dagger.functional.kotlinsrc.subcomponent.pruning.ParentDoesntUseSubcomponent.ChildB
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [Subcomponent]s which are included with [Module.subcomponents] but not used directly
 * within the component which adds them.
 *
 * This tests to make sure that while resolving one subcomponent (A), another subcomponent (B) can
 * be requested if they have a shared ancestor component. If that shared ancestor did not resolve B
 * directly via any of its entry points, B will still be generated since it is requested by a
 * descendant.
 */
@RunWith(JUnit4::class)
class SubcomponentOnlyRequestedBySiblingTest {
  @Test
  fun subcomponentAddedInParent_onlyUsedInSibling() {
    val parent = DaggerParentDoesntUseSubcomponent.create()
    val childB = parent.childBBuilder().build()
    assertThat(childB.componentHierarchy())
      .containsExactly(ParentDoesntUseSubcomponent::class.java, ChildB::class.java)
    assertThat(childB.componentHierarchyFromChildA())
      .containsExactly(ParentDoesntUseSubcomponent::class.java, ChildA::class.java)
  }
}
