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

import dagger.BindsInstance
import dagger.Subcomponent
import library2.MyTransitiveAnnotation
import library2.MyTransitiveType

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * During the compilation of `:app`, [MyTransitiveAnnotation] will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
// TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType::class)
@MySubcomponentScope
@Subcomponent(modules = [MySubcomponentModule::class])
abstract class MySubcomponentWithBuilder {
    @MyQualifier // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    abstract fun qualifiedMySubcomponentBinding(): MySubcomponentBinding

    // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    abstract fun unqualifiedMySubcomponentBinding(): MySubcomponentBinding

    // TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here).
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    abstract fun injectFoo(
        // TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here)
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        foo: Foo
    )

    // TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here).
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Subcomponent.Builder
    abstract class Builder {
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        abstract fun mySubcomponentModule(
            @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            mySubcomponentModule: MySubcomponentModule
        ): Builder

        @BindsInstance
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        abstract fun qualifiedMySubcomponentBinding(
            @MyQualifier
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            subcomponentBinding: MySubcomponentBinding
        ): Builder

        @BindsInstance
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        abstract fun unqualifiedMySubcomponentBinding(
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            subcomponentBinding: MySubcomponentBinding
        ): Builder

        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        abstract fun build(): MySubcomponentWithBuilder

        // Non-dagger code
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        var nonDaggerField = ""

        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        fun nonDaggerMethod(
            @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            str: String
        ) {}

        companion object {
            @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            var nonDaggerStaticField = ""

            @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            fun nonDaggerStaticMethod(
                @MyTransitiveAnnotation
                @MyAnnotation(MyTransitiveType.VALUE)
                @MyOtherAnnotation(MyTransitiveType::class)
                str: String
            ) {}
        }
    }

    // Non-dagger code
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    lateinit var nonDaggerField: MyTransitiveType

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
