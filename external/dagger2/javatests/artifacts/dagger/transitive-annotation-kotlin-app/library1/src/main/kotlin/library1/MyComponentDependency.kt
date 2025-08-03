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

package library1

import library2.MyTransitiveAnnotation
import library2.MyTransitiveType

/**
 * A class used to test that Dagger won't fail on unresolvable transitive types used in non-dagger
 * related elements and annotations.
 */
// TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here).
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType::class)
class MyComponentDependency
@MyTransitiveAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType::class)
constructor() {
    private val qualifiedMyComponentDependencyBinding = MyComponentDependencyBinding()
    private val unqualifiedMyComponentDependencyBinding = MyComponentDependencyBinding()

    // @MyTransitiveAnnotation cannot be used here.
    @MyQualifier
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    fun qualifiedMyComponentDependencyBinding(): MyComponentDependencyBinding {
        return qualifiedMyComponentDependencyBinding
    }

    // @MyTransitiveAnnotation cannot be used here.
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    fun unqualifiedMyComponentDependencyBinding(): MyComponentDependencyBinding {
        return unqualifiedMyComponentDependencyBinding
    }

    // Non-dagger code
    // Note: As this is supposed to be non-dagger code we use @JvmField to avoid the getter method,
    // otherwise, Dagger will interpret the method as contributing a binding.
    @JvmField
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    var nonDaggerField: MyTransitiveType? = null

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    fun nonDaggerMethod(
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        nonDaggerParameter: MyTransitiveType
    ): MyTransitiveType = nonDaggerParameter

    companion object {
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        lateinit var nonDaggerStaticField: MyTransitiveType

        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        fun nonDaggerStaticMethod(
            @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            nonDaggerParameter: MyTransitiveType
        ): MyTransitiveType = nonDaggerParameter
    }
}
