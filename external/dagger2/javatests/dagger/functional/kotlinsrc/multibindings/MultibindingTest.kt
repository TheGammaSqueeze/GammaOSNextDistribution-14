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

package dagger.functional.kotlinsrc.multibindings

import com.google.auto.value.AutoAnnotation
import com.google.common.truth.Truth.assertThat
import dagger.functional.kotlinsrc.multibindings.NestedAnnotationContainer.NestedWrappedKey
import dagger.multibindings.ClassKey
import dagger.multibindings.StringKey
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [MultibindingComponent]. */
@RunWith(JUnit4::class)
class MultibindingTest {
  private val multibindingComponent =
    DaggerMultibindingComponent.builder().multibindingDependency { 0.0 }.build()

  @Test
  fun map() {
    val map = multibindingComponent.map()
    assertThat(map).hasSize(2)
    assertThat(map).containsEntry("foo", "foo value")
    assertThat(map).containsEntry("bar", "bar value")
  }

  @Test
  fun mapOfArrays() {
    val map = multibindingComponent.mapOfArrays()
    assertThat(map).hasSize(2)
    assertThat(map).containsKey("foo")
    assertThat(map["foo"]).asList().containsExactly("foo1", "foo2").inOrder()
    assertThat(map).containsKey("bar")
    assertThat(map["bar"]).asList().containsExactly("bar1", "bar2").inOrder()
  }

  @Test
  fun mapOfProviders() {
    val mapOfProviders = multibindingComponent.mapOfProviders()
    assertThat(mapOfProviders).hasSize(2)
    assertThat(mapOfProviders["foo"]!!.get()).isEqualTo("foo value")
    assertThat(mapOfProviders["bar"]!!.get()).isEqualTo("bar value")
  }

  @Test
  fun mapKeysAndValues() {
    assertThat(multibindingComponent.mapKeys()).containsExactly("foo", "bar")
    assertThat(multibindingComponent.mapValues()).containsExactly("foo value", "bar value")
  }

  @Test
  fun nestedKeyMap() {
    assertThat(multibindingComponent.nestedKeyMap())
      .containsExactly(
        AutoAnnotationHolder.nestedWrappedKey(java.lang.Integer::class.java),
        "integer",
        AutoAnnotationHolder.nestedWrappedKey(java.lang.Long::class.java),
        "long"
      )
  }

  @Test
  fun unwrappedAnnotationKeyMap() {
    assertThat(multibindingComponent.unwrappedAnnotationKeyMap())
      .containsExactly(AutoAnnotationHolder.testStringKey("foo\n"), "foo annotation")
  }

  @Test
  fun wrappedAnnotationKeyMap() {
    assertThat(multibindingComponent.wrappedAnnotationKeyMap())
      .containsExactly(
        AutoAnnotationHolder.testWrappedAnnotationKey(
          AutoAnnotationHolder.testStringKey("foo"),
          intArrayOf(1, 2, 3),
          arrayOf(),
          arrayOf(java.lang.Long::class.java, java.lang.Integer::class.java)
        ),
        "wrapped foo annotation"
      )
  }

  @Test
  fun booleanKeyMap() {
    assertThat(multibindingComponent.booleanKeyMap()).containsExactly(true, "true")
  }

  @Test
  fun byteKeyMap() {
    assertThat(multibindingComponent.byteKeyMap()).containsExactly(100.toByte(), "100 byte")
  }

  @Test
  fun charKeyMap() {
    assertThat(multibindingComponent.characterKeyMap())
      .containsExactly('a', "a char", '\n', "newline char")
  }

  @Test
  fun classKeyMap() {
    assertThat(multibindingComponent.classKeyMap())
      .containsExactly(java.lang.Integer::class.java, "integer", java.lang.Long::class.java, "long")
  }

  @Test
  fun numberClassKeyMap() {
    assertThat(multibindingComponent.numberClassKeyMap())
      .containsExactly(BigDecimal::class.java, "bigdecimal", BigInteger::class.java, "biginteger")
  }

  @Test
  fun intKeyMap() {
    assertThat(multibindingComponent.integerKeyMap()).containsExactly(100, "100 int")
  }

  @Test
  fun longKeyMap() {
    assertThat(multibindingComponent.longKeyMap()).containsExactly(100.toLong(), "100 long")
  }

  @Test
  fun shortKeyMap() {
    assertThat(multibindingComponent.shortKeyMap()).containsExactly(100.toShort(), "100 short")
  }

  @Test
  fun setBindings() {
    assertThat(multibindingComponent.set())
      .containsExactly(-90, -17, -1, 5, 6, 832, 1742, -101, -102)
  }

  @Test
  fun complexQualifierSet() {
    assertThat(multibindingComponent.complexQualifierStringSet()).containsExactly("foo")
  }

  @Test
  fun emptySet() {
    assertThat(multibindingComponent.emptySet()).isEmpty()
  }

  @Test
  fun emptyQualifiedSet() {
    assertThat(multibindingComponent.emptyQualifiedSet()).isEmpty()
  }

  @Test
  fun emptyMap() {
    assertThat(multibindingComponent.emptyMap()).isEmpty()
  }

  @Test
  fun emptyQualifiedMap() {
    assertThat(multibindingComponent.emptyQualifiedMap()).isEmpty()
  }

  @Test
  fun maybeEmptySet() {
    assertThat(multibindingComponent.maybeEmptySet()).containsExactly("foo")
  }

  @Test
  fun maybeEmptyQualifiedSet() {
    assertThat(multibindingComponent.maybeEmptyQualifiedSet()).containsExactly("qualified foo")
  }

  @Test
  fun maybeEmptyMap() {
    assertThat(multibindingComponent.maybeEmptyMap()).containsEntry("key", "foo value")
  }

  @Test
  fun maybeEmptyQualifiedMap() {
    assertThat(multibindingComponent.maybeEmptyQualifiedMap())
      .containsEntry("key", "qualified foo value")
  }

  // Note: @AutoAnnotation requires a static method. Normally, we would just use a companion object
  // but that generates both a static and non-static method so we need to use a normal object.
  object AutoAnnotationHolder {
    @JvmStatic
    @AutoAnnotation
    fun testStringKey(value: String): StringKey {
      return AutoAnnotation_MultibindingTest_AutoAnnotationHolder_testStringKey(value)
    }

    @JvmStatic
    @AutoAnnotation
    fun nestedWrappedKey(value: Class<*>): NestedWrappedKey {
      return AutoAnnotation_MultibindingTest_AutoAnnotationHolder_nestedWrappedKey(value)
    }

    @JvmStatic
    @AutoAnnotation
    fun testWrappedAnnotationKey(
      value: StringKey,
      integers: IntArray,
      annotations: Array<ClassKey>,
      classes: Array<Class<out Number>>
    ): WrappedAnnotationKey {
      return AutoAnnotation_MultibindingTest_AutoAnnotationHolder_testWrappedAnnotationKey(
        value,
        integers,
        annotations,
        classes
      )
    }
  }
}
