/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.testing.testinstallin;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.testing.TestInstallIn;
import javax.inject.Qualifier;

/** Modules and classes used in TestInstallInFooTest and TestInstallInBarTest. */
final class TestInstallInModules {
  private TestInstallInModules() {}

  @Qualifier
  public @interface ActivityLevel {}

  @Qualifier
  public @interface FragmentLevel {}

  static class Foo {
    Class<?> moduleClass;

    Foo(Class<?> moduleClass) {
      this.moduleClass = moduleClass;
    }
  }

  static class Bar {
    Class<?> moduleClass;

    Bar(Class<?> moduleClass) {
      this.moduleClass = moduleClass;
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  interface SingletonFooModule {
    @Provides
    static Foo provideFoo() {
      return new Foo(SingletonFooModule.class);
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  interface SingletonBarModule {
    @Provides
    static Bar provideFoo() {
      return new Bar(SingletonBarModule.class);
    }
  }

  @Module
  @TestInstallIn(components = SingletonComponent.class, replaces = SingletonFooModule.class)
  interface SingletonFooTestModule {
    @Provides
    static Foo provideFoo() {
      return new Foo(SingletonFooTestModule.class);
    }
  }

  @Module
  @InstallIn(ActivityComponent.class)
  interface ActivityFooModule {
    @Provides
    @ActivityLevel
    static Foo provideFoo() {
      throw new AssertionError("This provides method should never be called.");
    }
  }

  @Module
  @InstallIn(ActivityComponent.class)
  interface ActivityBarModule {
    @Provides
    @ActivityLevel
    // Add an activity dependency to make sure Dagger adds this module to the correct component.
    static Bar provideFoo(@SuppressWarnings("UnusedVariable") Activity activity) {
      return new Bar(ActivityBarModule.class);
    }
  }

  @Module
  @TestInstallIn(components = ActivityComponent.class, replaces = ActivityFooModule.class)
  interface ActivityFooTestModule {
    @Provides
    @ActivityLevel
    // Add an activity dependency to make sure Dagger adds this module to the correct component.
    static Foo provideFoo(@SuppressWarnings("UnusedVariable") Activity activity) {
      return new Foo(ActivityFooTestModule.class);
    }
  }

  @Module
  @InstallIn(FragmentComponent.class)
  interface FragmentFooModule {
    @Provides
    @FragmentLevel
    static Foo provideFoo() {
      throw new AssertionError("This provides method should never be called.");
    }
  }

  @Module
  @InstallIn(FragmentComponent.class)
  interface FragmentBarModule {
    @Provides
    @FragmentLevel
    // Add a fragment dependency to make sure Dagger adds this module to the correct component.
    static Bar provideFoo(@SuppressWarnings("UnusedVariable") Fragment fragment) {
      return new Bar(FragmentBarModule.class);
    }
  }

  @Module
  @TestInstallIn(components = FragmentComponent.class, replaces = FragmentFooModule.class)
  interface FragmentFooTestModule {
    @Provides
    @FragmentLevel
    // Add a fragment dependency to make sure Dagger adds this module to the correct component.
    static Foo provideFoo(@SuppressWarnings("UnusedVariable") Fragment fragment) {
      return new Foo(FragmentFooTestModule.class);
    }
  }
}
