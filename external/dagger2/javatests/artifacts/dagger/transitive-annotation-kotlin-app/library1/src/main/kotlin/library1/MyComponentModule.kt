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

import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import library2.MyTransitiveAnnotation
import library2.MyTransitiveType

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 *
 * During the compilation of `:app`, [MyTransitiveAnnotation] will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
@MyTransitiveAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType::class)
@Module(includes = [MyComponentModule.MyAbstractModule::class])
class MyComponentModule
@MyTransitiveAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType::class)
constructor(
    // Non-Dagger elements
    @field:MyOtherAnnotation(MyTransitiveType::class)
    @field:MyAnnotation(MyTransitiveType.VALUE)
    @field:MyTransitiveAnnotation
    @param:MyTransitiveAnnotation
    @param:MyAnnotation(MyTransitiveType.VALUE)
    @param:MyOtherAnnotation(MyTransitiveType::class)
    private val dep: Dep
) {
    // Define bindings for each configuration: Scoped/Unscoped, Qualified/UnQualified, Provides/Binds
    open class ScopedQualifiedBindsType
    class ScopedQualifiedProvidesType : ScopedQualifiedBindsType()
    open class ScopedUnqualifiedBindsType
    class ScopedUnqualifiedProvidesType : ScopedUnqualifiedBindsType()
    open class UnscopedQualifiedBindsType
    class UnscopedQualifiedProvidesType : UnscopedQualifiedBindsType()
    open class UnscopedUnqualifiedBindsType
    class UnscopedUnqualifiedProvidesType : UnscopedUnqualifiedBindsType()

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Provides
    @Singleton
    @MyQualifier
    fun scopedQualifiedProvidesType(
        @MyQualifier
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        dep: Dep
    ): ScopedQualifiedProvidesType = ScopedQualifiedProvidesType()

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Provides
    @Singleton
    fun scopedUnqualifiedProvidesType(
        @MyQualifier
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        dep: Dep
    ): ScopedUnqualifiedProvidesType = ScopedUnqualifiedProvidesType()

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Provides
    @MyQualifier
    fun unscopedQualifiedProvidesType(
        @MyQualifier
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        dep: Dep
    ): UnscopedQualifiedProvidesType = UnscopedQualifiedProvidesType()

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Provides
    fun unscopedUnqualifiedProvidesType(
        @MyQualifier
        @MyTransitiveAnnotation
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        dep: Dep
    ): UnscopedUnqualifiedProvidesType = UnscopedUnqualifiedProvidesType()

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Module
    internal interface MyAbstractModule {
        // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        @Binds
        @Singleton
        @MyQualifier
        fun scopedQualifiedBindsType(
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyQualifier
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            scopedQualifiedProvidesType: ScopedQualifiedProvidesType
        ): ScopedQualifiedBindsType

        // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        @Binds
        @Singleton
        fun scopedUnqualifiedBindsType(
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            scopedUnqualifiedProvidesType: ScopedUnqualifiedProvidesType
        ): ScopedUnqualifiedBindsType

        // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        @Binds
        @MyQualifier
        fun unscopedQualifiedBindsType(
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyQualifier
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            unscopedQualifiedProvidesType: UnscopedQualifiedProvidesType
        ): UnscopedQualifiedBindsType

        // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
        @MyAnnotation(MyTransitiveType.VALUE)
        @MyOtherAnnotation(MyTransitiveType::class)
        @Binds
        fun unscopedUnqualifiedBindsType(
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            unscopedUnqualifiedProvidesType: UnscopedUnqualifiedProvidesType
        ): UnscopedUnqualifiedBindsType
    }

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Provides
    @MyQualifier
    fun provideQualifiedDep(): Dep = Dep()

    // Provide an unqualified Dep to ensure that if we accidentally drop the qualifier
    // we'll get a runtime exception.
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    @Provides
    fun provideDep(): Dep = TODO()

    // Non-Dagger elements

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType::class)
    private val nonDaggerField: MyTransitiveType = MyTransitiveType()

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
        fun nonDaggerStaticMethod(
            @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType::class)
            nonDaggerParameter: MyTransitiveType
        ): MyTransitiveType = nonDaggerParameter
    }
}
