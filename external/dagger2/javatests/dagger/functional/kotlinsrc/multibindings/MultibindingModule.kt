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

import dagger.Module
import dagger.Provides
import dagger.functional.kotlinsrc.multibindings.NestedAnnotationContainer.NestedWrappedKey
import dagger.multibindings.ClassKey
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.LongKey
import dagger.multibindings.StringKey
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Named
import javax.inject.Provider

@Module
internal object MultibindingModule {
  @Provides
  @IntoMap
  @StringKey("foo")
  fun provideFooKey(@Suppress("UNUSED_PARAMETER") doubleDependency: Double): String = "foo value"

  @Provides @IntoMap @StringKey("bar") fun provideBarKey(): String = "bar value"

  @Provides
  @IntoMap
  @StringKey("foo")
  fun provideFooArrayValue(
    @Suppress("UNUSED_PARAMETER") doubleDependency: Double
  ): Array<String> = arrayOf("foo1", "foo2")

  @Provides
  @IntoMap
  @StringKey("bar")
  fun provideBarArrayValue(): Array<String> = arrayOf("bar1", "bar2")

  @Provides @IntoSet fun provideFiveToSet(): Int = 5

  @Provides @IntoSet fun provideSixToSet(): Int = 6

  @Provides @ElementsIntoSet fun provideElementsIntoSet(): Set<Int> = setOf(-101, -102)

  @Provides
  fun provideMapKeys(map: Map<String, @JvmSuppressWildcards Provider<String>>): Set<String> =
    map.keys

  @Provides fun provideMapValues(map: Map<String, String>): Collection<String> = map.values

  @Provides
  @IntoMap
  @NestedWrappedKey(java.lang.Integer::class)
  fun valueForInteger(): String = "integer"

  @Provides @IntoMap @NestedWrappedKey(java.lang.Long::class) fun valueForLong(): String = "long"

  @Provides
  @IntoMap
  @ClassKey(java.lang.Integer::class)
  fun valueForClassInteger(): String = "integer"

  @Provides @IntoMap @ClassKey(java.lang.Long::class) fun valueForClassLong(): String = "long"

  @Provides
  @IntoMap
  @NumberClassKey(BigDecimal::class)
  fun valueForNumberClassBigDecimal(): String = "bigdecimal"

  @Provides
  @IntoMap
  @NumberClassKey(BigInteger::class)
  fun valueForNumberClassBigInteger(): String = "biginteger"

  @Provides @IntoMap @LongKey(100) fun valueFor100Long(): String = "100 long"

  @Provides @IntoMap @IntKey(100) fun valueFor100Int(): String = "100 int"

  @Provides @IntoMap @ShortKey(100) fun valueFor100Short(): String = "100 short"

  @Provides @IntoMap @ByteKey(100) fun valueFor100Byte(): String = "100 byte"

  @Provides @IntoMap @BooleanKey(true) fun valueForTrue(): String = "true"

  @Provides @IntoMap @CharKey('a') fun valueForA(): String = "a char"

  @Provides @IntoMap @CharKey('\n') fun valueForNewline(): String = "newline char"

  @Provides
  @IntoMap
  @UnwrappedAnnotationKey(StringKey("foo\n"))
  fun valueForUnwrappedAnnotationKeyFoo(): String = "foo annotation"

  @Provides
  @IntoMap
  @WrappedAnnotationKey(
    value = StringKey("foo"),
    integers = [1, 2, 3],
    annotations = [],
    classes = [java.lang.Long::class, java.lang.Integer::class]
  )
  fun valueForWrappedAnnotationKeyFoo(): String = "wrapped foo annotation"

  @Provides @IntoSet @Named("complexQualifier") fun valueForComplexQualifierSet(): String = "foo"

  @Provides @IntoSet fun setContribution(): CharSequence = "foo"

  @Provides
  @IntoSet
  @Named("complexQualifier")
  fun qualifiedSetContribution(): CharSequence = "qualified foo"

  @Provides @IntoMap @StringKey("key") fun mapContribution(): CharSequence = "foo value"

  @Provides
  @IntoMap
  @Named("complexQualifier")
  @StringKey("key")
  fun qualifiedMapContribution(): CharSequence = "qualified foo value"
}
