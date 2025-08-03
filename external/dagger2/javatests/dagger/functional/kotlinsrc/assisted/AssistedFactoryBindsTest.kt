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
import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class AssistedFactoryBindsTest {
  @Component(modules = [FooFactoryModule::class])
  interface ParentComponent {
    // Test using @Binds where Foo => FooImpl and FooFactory => FooFactoryImpl
    fun fooFactory(): FooFactory
  }

  @Module
  interface FooFactoryModule {
    @Binds fun bind(impl: FooFactoryImpl): FooFactory
  }

  interface Foo

  class FooImpl
  @AssistedInject
  constructor(val dep: Dep, @Assisted val assistedDep: AssistedDep) : Foo

  interface FooFactory {
    fun create(assistedDep: AssistedDep): Foo
  }

  @AssistedFactory
  interface FooFactoryImpl : FooFactory {
    override fun create(assistedDep: AssistedDep): FooImpl
  }

  class AssistedDep

  class Dep @Inject constructor()

  @Test
  fun testFooFactory() {
    val fooFactory = DaggerAssistedFactoryBindsTest_ParentComponent.create().fooFactory()
    assertThat(fooFactory).isInstanceOf(FooFactoryImpl::class.java)
    val assistedDep = AssistedDep()
    val foo = fooFactory.create(assistedDep)
    assertThat(foo).isInstanceOf(FooImpl::class.java)
    val fooImpl = foo as FooImpl
    assertThat(fooImpl.dep).isNotNull()
    assertThat(fooImpl.assistedDep).isEqualTo(assistedDep)
  }
}
