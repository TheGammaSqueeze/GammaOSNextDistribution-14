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

package app

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Before
import com.google.common.truth.Truth.assertThat
import library1.*
import org.junit.Test

@RunWith(JUnit4::class)
class MyComponentTest {
    private lateinit var component: MyComponent

    @Before
    fun setup() {
        component = DaggerMyComponent.factory()
            .create(MyComponentModule(Dep()), MyComponentDependency())
    }

    @Test
    fun testFooIsScoped() {
        assertThat(component.foo()).isEqualTo(component.foo())
    }

    @Test
    fun testAssistedFoo() {
        assertThat(component.assistedFooFactory().create(5)).isNotNull()
    }

    @Test
    fun testScopedQualifiedBindsTypeIsScoped() {
        assertThat(component.scopedQualifiedBindsType())
            .isEqualTo(component.scopedQualifiedBindsType())
    }

    @Test
    fun testScopedUnqualifiedBindsTypeIsScoped() {
        assertThat(component.scopedUnqualifiedBindsType())
            .isEqualTo(component.scopedUnqualifiedBindsType())
    }

    @Test
    fun testUnscopedQualifiedBindsTypeIsNotScoped() {
        assertThat(component.unscopedQualifiedBindsType())
            .isNotEqualTo(component.unscopedQualifiedBindsType())
    }

    @Test
    fun testUnscopedUnqualifiedBindsTypeIsNotScoped() {
        assertThat(component.unscopedUnqualifiedBindsType())
            .isNotEqualTo(component.unscopedUnqualifiedBindsType())
    }

    @Test
    fun testScopedQualifiedProvidesTypeIsScoped() {
        assertThat(component.scopedQualifiedProvidesType())
            .isEqualTo(component.scopedQualifiedProvidesType())
    }

    @Test
    fun testScopedUnqualifiedProvidesTypeIsScoped() {
        assertThat(component.scopedUnqualifiedProvidesType())
            .isEqualTo(component.scopedUnqualifiedProvidesType())
    }

    @Test
    fun testUnscopedQualifiedProvidesTypeIsNotScoped() {
        assertThat(component.unscopedQualifiedProvidesType())
            .isNotEqualTo(component.unscopedQualifiedProvidesType())
    }

    @Test
    fun testUnscopedUnqualifiedProvidesTypeIsNotScoped() {
        assertThat(component.unscopedUnqualifiedProvidesType())
            .isNotEqualTo(component.unscopedUnqualifiedProvidesType())
    }

    @Test
    fun testMyComponentDependencyBinding() {
        assertThat(component.qualifiedMyComponentDependencyBinding())
            .isNotEqualTo(component.unqualifiedMyComponentDependencyBinding())
    }
}