/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.cycle

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.Uninterruptibles
import dagger.Component
import dagger.Module
import dagger.Provides
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.Thread.State.BLOCKED
import java.lang.Thread.State.WAITING
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DoubleCheckCycleTest {
  // TODO(b/77916397): Migrate remaining tests in DoubleCheckTest to functional tests in this class.
  /** A qualifier for a reentrant scoped binding. */
  @Retention(AnnotationRetention.RUNTIME)
  @Qualifier
  internal annotation class Reentrant

  /** A module to be overridden in each test. */
  @Module
  internal open class OverrideModule {
    @Provides
    @Singleton
    open fun provideObject(): Any {
      throw IllegalStateException("This method should be overridden in tests")
    }

    @Provides
    @Singleton
    @Reentrant
    open fun provideReentrantObject(@Reentrant provider: Provider<Any>): Any {
      throw IllegalStateException("This method should be overridden in tests")
    }
  }

  @Singleton
  @Component(modules = [OverrideModule::class])
  internal interface TestComponent {
    fun getObject(): Any

    @Reentrant
    fun getReentrantObject(): Any
  }

  @Test
  fun testNonReentrant() {
    val callCount = AtomicInteger(0)

    // Provides a non-reentrant binding. The provides method should only be called once.
    val component =
      DaggerDoubleCheckCycleTest_TestComponent.builder()
        .overrideModule(
          object : OverrideModule() {
            override fun provideObject(): Any {
              callCount.getAndIncrement()
              return Any()
            }
          }
        )
        .build()
    assertThat(callCount.get()).isEqualTo(0)
    val first = component.getObject()
    assertThat(callCount.get()).isEqualTo(1)
    val second = component.getObject()
    assertThat(callCount.get()).isEqualTo(1)
    assertThat(first).isSameInstanceAs(second)
  }

  @Test
  fun testReentrant() {
    val callCount = AtomicInteger(0)

    // Provides a reentrant binding. Even though it's scoped, the provides method is called twice.
    // In this case, we allow it since the same instance is returned on the second call.
    val component =
      DaggerDoubleCheckCycleTest_TestComponent.builder()
        .overrideModule(
          object : OverrideModule() {
            override fun provideReentrantObject(provider: Provider<Any>): Any {
              return if (callCount.incrementAndGet() == 1) {
                provider.get()
              } else {
                Any()
              }
            }
          }
        )
        .build()
    assertThat(callCount.get()).isEqualTo(0)
    val first = component.getReentrantObject()
    assertThat(callCount.get()).isEqualTo(2)
    val second = component.getReentrantObject()
    assertThat(callCount.get()).isEqualTo(2)
    assertThat(first).isSameInstanceAs(second)
  }

  @Test
  fun testFailingReentrant() {
    val callCount = AtomicInteger(0)

    // Provides a failing reentrant binding. Even though it's scoped, the provides method is called
    // twice. In this case we throw an exception since a different instance is provided on the
    // second call.
    val component =
      DaggerDoubleCheckCycleTest_TestComponent.builder()
        .overrideModule(
          object : OverrideModule() {
            override fun provideReentrantObject(provider: Provider<Any>): Any {
              if (callCount.incrementAndGet() == 1) {
                provider.get()
                return Any()
              }
              return Any()
            }
          }
        )
        .build()
    assertThat(callCount.get()).isEqualTo(0)
    try {
      component.getReentrantObject()
      fail("Expected IllegalStateException")
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("Scoped provider was invoked recursively")
    }
    assertThat(callCount.get()).isEqualTo(2)
  }

  @Test(timeout = 5000)
  @Throws(Exception::class)
  fun testGetFromMultipleThreads() {
    val callCount = AtomicInteger(0)
    val requestCount = AtomicInteger(0)
    val future: SettableFuture<Any> = SettableFuture.create()

    // Provides a non-reentrant binding. In this case, we return a SettableFuture so that we can
    // control when the provides method returns.
    val component =
      DaggerDoubleCheckCycleTest_TestComponent.builder()
        .overrideModule(
          object : OverrideModule() {
            override fun provideObject(): Any {
              callCount.incrementAndGet()
              return try {
                Uninterruptibles.getUninterruptibly(future)
              } catch (e: ExecutionException) {
                throw RuntimeException(e)
              }
            }
          }
        )
        .build()
    val numThreads = 10
    val remainingTasks = CountDownLatch(numThreads)
    val tasks: MutableList<Thread> = ArrayList(numThreads)
    val values: MutableList<Any> = Collections.synchronizedList<Any>(ArrayList<Any>(numThreads))

    // Set up multiple threads that call component.getObject().
    for (i in 0 until numThreads) {
      tasks.add(
        Thread {
          requestCount.incrementAndGet()
          values.add(component.getObject())
          remainingTasks.countDown()
        }
      )
    }

    // Check initial conditions
    assertThat(remainingTasks.getCount()).isEqualTo(10)
    assertThat(requestCount.get()).isEqualTo(0)
    assertThat(callCount.get()).isEqualTo(0)
    assertThat(values).isEmpty()

    // Start all threads
    tasks.forEach(Thread::start)

    // Wait for all threads to wait/block.
    var waiting: Long = 0L
    while (waiting != numThreads.toLong()) {
      waiting =
        tasks.stream()
          .map(Thread::getState)
          .filter { state -> state == WAITING || state == BLOCKED }
          .count()
    }

    // Check the intermediate state conditions.
    // * All 10 threads should have requested the binding, but none should have finished.
    // * Only 1 thread should have reached the provides method.
    // * None of the threads should have set a value (since they are waiting for future to be set).
    assertThat(remainingTasks.getCount()).isEqualTo(10)
    assertThat(requestCount.get()).isEqualTo(10)
    assertThat(callCount.get()).isEqualTo(1)
    assertThat(values).isEmpty()

    // Set the future and wait on all remaining threads to finish.
    val futureValue = Any()
    future.set(futureValue)
    remainingTasks.await()

    // Check the final state conditions.
    // All values should be set now, and they should all be equal to the same instance.
    assertThat(remainingTasks.getCount()).isEqualTo(0)
    assertThat(requestCount.get()).isEqualTo(10)
    assertThat(callCount.get()).isEqualTo(1)
    assertThat(values).isEqualTo(Collections.nCopies(numThreads, futureValue))
  }
}
