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

package dagger.functional.kotlinsrc.subcomponent.multibindings

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Qualifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Regression tests for an issue where subcomponent builder bindings were incorrectly reused from a
 * parent even if the subcomponent were redeclared on the child component. This manifested via
 * multibindings, especially since subcomponent builder bindings are special in that we cannot
 * traverse them to see if they depend on local multibinding contributions.
 */
@RunWith(JUnit4::class)
class SubcomponentBuilderMultibindingsTest {
  @Qualifier annotation class ParentFoo

  @Qualifier annotation class ChildFoo

  class Foo @Inject internal constructor(val multi: Set<String>)

  // This tests the case where a subcomponent is installed in both the parent and child component.
  // In this case, we expect two subcomponents to be generated with the child one including the
  // child multibinding contribution.
  class ChildInstallsFloating private constructor() {
    @Component(modules = [ParentModule::class])
    interface Parent {
      @ParentFoo fun parentFoo(): Foo
      fun child(): Child
    }

    @Subcomponent(modules = [ChildModule::class])
    interface Child {
      @ChildFoo fun childFoo(): Foo
    }

    @Subcomponent
    interface FloatingSub {
      fun foo(): Foo

      @Subcomponent.Builder
      interface Builder {
        fun build(): FloatingSub
      }
    }

    @Module(subcomponents = [FloatingSub::class])
    interface ParentModule {
      companion object {
        @Provides @IntoSet fun provideStringMulti(): String = "parent"

        @Provides
        @ParentFoo
        fun provideParentFoo(builder: FloatingSub.Builder): Foo = builder.build().foo()
      }
    }

    // The subcomponent installation of FloatingSub here is the key difference
    @Module(subcomponents = [FloatingSub::class])
    interface ChildModule {
      companion object {
        @Provides @IntoSet fun provideStringMulti(): String = "child"

        @Provides
        @ChildFoo
        fun provideChildFoo(builder: FloatingSub.Builder): Foo = builder.build().foo()
      }
    }
  }

  // This is the same as the above, except this time the child does not install the subcomponent
  // builder. Here, we expect the child to reuse the parent subcomponent binding (we want to avoid
  // any mistakes that might implicitly create a new subcomponent relationship) and so therefore
  // we expect only one subcomponent to be generated in the parent resulting in the child not seeing
  // the child multibinding contribution.
  class ChildDoesNotInstallFloating private constructor() {
    @Component(modules = [ParentModule::class])
    interface Parent {
      @ParentFoo fun parentFoo(): Foo
      fun child(): Child
    }

    @Subcomponent(modules = [ChildModule::class])
    interface Child {
      @ChildFoo fun childFoo(): Foo
    }

    @Subcomponent
    interface FloatingSub {
      fun foo(): Foo

      @Subcomponent.Builder
      interface Builder {
        fun build(): FloatingSub
      }
    }

    @Module(subcomponents = [FloatingSub::class])
    interface ParentModule {
      companion object {
        @Provides @IntoSet fun provideStringMulti(): String = "parent"

        @Provides
        @ParentFoo
        fun provideParentFoo(builder: FloatingSub.Builder): Foo = builder.build().foo()
      }
    }

    // The lack of a subcomponent installation of FloatingSub here is the key difference
    @Module
    interface ChildModule {
      companion object {
        @Provides @IntoSet fun provideStringMulti(): String = "child"

        @Provides
        @ChildFoo
        fun provideChildFoo(builder: FloatingSub.Builder): Foo = builder.build().foo()
      }
    }
  }

  // This is similar to the first, except this time the components installs the subcomponent via
  // factory methods. Here, we expect the child to get a new subcomponent and so should see its
  // multibinding contribution.
  class ChildInstallsFloatingFactoryMethod private constructor() {
    @Component(modules = [ParentModule::class])
    interface Parent {
      @ParentFoo fun parentFoo(): Foo
      fun child(): Child
      fun floatingSub(): FloatingSub
    }

    @Subcomponent(modules = [ChildModule::class])
    interface Child {
      @ChildFoo fun childFoo(): Foo
      fun floatingSub(): FloatingSub
    }

    @Subcomponent
    interface FloatingSub {
      fun foo(): Foo
    }

    @Module
    interface ParentModule {
      companion object {
        @Provides @IntoSet fun provideStringMulti(): String = "parent"

        @Provides
        @ParentFoo
        fun provideParentFoo(componentSelf: Parent): Foo = componentSelf.floatingSub().foo()
      }
    }

    @Module
    interface ChildModule {
      companion object {
        @Provides @IntoSet fun provideStringMulti(): String = "child"

        @Provides
        @ChildFoo
        fun provideChildFoo(componentSelf: Child): Foo = componentSelf.floatingSub().foo()
      }
    }
  }

  @Test
  fun testChildInstallsFloating() {
    val parentComponent =
      DaggerSubcomponentBuilderMultibindingsTest_ChildInstallsFloating_Parent.create()
    assertThat(parentComponent.parentFoo().multi).containsExactly("parent")
    assertThat(parentComponent.child().childFoo().multi).containsExactly("parent", "child")
  }

  @Test
  fun testChildDoesNotInstallFloating() {
    val parentComponent =
      DaggerSubcomponentBuilderMultibindingsTest_ChildDoesNotInstallFloating_Parent.create()
    assertThat(parentComponent.parentFoo().multi).containsExactly("parent")
    // Don't expect the child contribution because the child didn't redeclare the subcomponent
    // dependency, meaning it intends to just use the subcomponent relationship from the parent
    // component.
    assertThat(parentComponent.child().childFoo().multi).containsExactly("parent")
  }

  @Test
  fun testChildInstallsFloatingFactoryMethod() {
    val parentComponent =
      DaggerSubcomponentBuilderMultibindingsTest_ChildInstallsFloatingFactoryMethod_Parent.create()
    assertThat(parentComponent.parentFoo().multi).containsExactly("parent")
    assertThat(parentComponent.child().childFoo().multi).containsExactly("parent", "child")
  }
}
