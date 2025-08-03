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

package dagger.functional.binds;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for b/267223822 where a scoped @Binds used in a cycle caused problems
// in fastInit mode.
@RunWith(JUnit4.class)
public class RecursiveBindsTest {

  public interface Foo {}

  public static final class FooImpl implements Foo {
    @Inject FooImpl(@SuppressWarnings("unused") Provider<Foo> provider) {}
  }

  @Module
  public interface FooModule {
    // This binding must be scoped to create the cycle. Otherwise without a scope, the generated
    // code just doesn't have a field for this @Binds because we can directly use FooImpl's
    // provider as they are equivalent.
    @Binds
    @Singleton
    Foo bindFoo(FooImpl impl);
  }

  @Component(modules = FooModule.class)
  @Singleton
  public interface TestSingletonComponent {
    // We get the impl here to create a cycle where the impl factory needs to be delegated.
    // That way the scoped binds (which does something like DoubleCheck.provider(implFactory)) is
    // the one that would fail if it wasn't delegated properly.
    Provider<FooImpl> getFooImplProvider();
  }

  @Test
  public void test() {
    // Technically the NPE would happen when just initializing the component.
    assertThat(DaggerRecursiveBindsTest_TestSingletonComponent.create().getFooImplProvider().get())
        .isNotNull();
  }
}
