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

package dagger.functional.kotlinsrc.assisted

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// See https://github.com/google/dagger/issues/2281
@RunWith(JUnit4::class)
internal class AssistedFactoryWithQualifiedTypesTest {
  @Component
  interface TestComponent {
    // Test a factory with duplicate types with unique qualifiers.
    fun dupeTypeFactory(): DupeTypeFactory

    // Test a factory with duplicate qualifiers with unique types.
    fun dupeQualifierFactory(): DupeQualifierFactory

    // Test a factory with unnecessary qualifiers on the factory.
    fun unnecessaryQualifierFactory(): UnnecessaryQualifierFactory

    // Test a factory with different parameter order than the constructor.
    fun swappedDupeTypeFactory(): SwappedDupeTypeFactory
  }

  class DupeType
  @AssistedInject
  constructor(@Assisted("1") val str1: String, @Assisted("2") val str2: String)

  @AssistedFactory
  interface DupeTypeFactory {
    fun create(@Assisted("1") str1: String, @Assisted("2") str2: String): DupeType
  }

  @Test
  fun testDupeTypeFactory() {
    val str1 = "str1"
    val str2 = "str2"
    val dupeType: DupeType =
      DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
        .dupeTypeFactory()
        .create(str1, str2)
    assertThat(dupeType.str1).isEqualTo(str1)
    assertThat(dupeType.str2).isEqualTo(str2)
  }

  @AssistedFactory
  interface SwappedDupeTypeFactory {
    fun create(@Assisted("2") str2: String, @Assisted("1") str1: String): DupeType
  }

  @Test
  fun testSwappedDupeTypeFactory() {
    val str1 = "str1"
    val str2 = "str2"
    val dupeType: DupeType =
      DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
        .swappedDupeTypeFactory()
        .create(str2, str1)
    assertThat(dupeType.str1).isEqualTo(str1)
    assertThat(dupeType.str2).isEqualTo(str2)
  }

  class DupeQualifier
  @AssistedInject
  constructor(@Assisted("1") val str: String, @Assisted("1") val i: Int)

  @AssistedFactory
  interface DupeQualifierFactory {
    fun create(@Assisted("1") str: String, @Assisted("1") i: Int): DupeQualifier
  }

  @Test
  fun testDupeQualifierFactory() {
    val str = "str"
    val i = 11
    val dupeQualifier =
      DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
        .dupeQualifierFactory()
        .create(str, i)
    assertThat(dupeQualifier.str).isEqualTo(str)
    assertThat(dupeQualifier.i).isEqualTo(i)
  }

  class UnnecessaryQualifier
  @AssistedInject
  constructor(@Assisted val str: String, @Assisted val d: Double, @Assisted("") val i: Int)

  @AssistedFactory
  interface UnnecessaryQualifierFactory {
    fun create(@Assisted i: Int, @Assisted("") str: String, d: Double): UnnecessaryQualifier
  }

  @Test
  fun testUnnecessaryQualifierFactory() {
    val str = "str"
    val d = 2.2
    val i = 11
    val unnecessaryQualifier =
      DaggerAssistedFactoryWithQualifiedTypesTest_TestComponent.create()
        .unnecessaryQualifierFactory()
        .create(i, str, d)
    assertThat(unnecessaryQualifier.str).isEqualTo(str)
    assertThat(unnecessaryQualifier.d).isEqualTo(d)
    assertThat(unnecessaryQualifier.i).isEqualTo(i)
  }
}
