/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.net.module.util

import android.util.Log
import com.android.testutils.tryTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

private val TAG = CleanupTest::class.simpleName

@RunWith(JUnit4::class)
class CleanupTest {
    class TestException1 : Exception()
    class TestException2 : Exception()
    class TestException3 : Exception()

    @Test
    fun testNotThrow() {
        var x = 1
        val result = tryTest {
            x = 2
            Log.e(TAG, "Do nothing")
            6
        } cleanup {
            assertTrue(x == 2)
            x = 3
            Log.e(TAG, "Do nothing")
        }
        assertTrue(x == 3)
        assertTrue(result == 6)
    }

    @Test
    fun testThrowTry() {
        var x = 1
        val thrown = assertFailsWith<TestException1> {
            tryTest {
                x = 2
                throw TestException1()
                x = 4
            } cleanup {
                assertTrue(x == 2)
                x = 3
                Log.e(TAG, "Do nothing")
            }
        }
        assertTrue(thrown.suppressedExceptions.isEmpty())
        assertTrue(x == 3)
    }

    @Test
    fun testThrowCleanup() {
        var x = 1
        val thrown = assertFailsWith<TestException2> {
            tryTest {
                x = 2
                Log.e(TAG, "Do nothing")
            } cleanup {
                assertTrue(x == 2)
                x = 3
                throw TestException2()
                x = 4
            }
        }
        assertTrue(thrown.suppressedExceptions.isEmpty())
        assertTrue(x == 3)
    }

    @Test
    fun testThrowBoth() {
        var x = 1
        val thrown = assertFailsWith<TestException1> {
            tryTest {
                x = 2
                throw TestException1()
                x = 3
            } cleanup {
                assertTrue(x == 2)
                x = 4
                throw TestException2()
                x = 5
            }
        }
        assertTrue(thrown.suppressedExceptions[0] is TestException2)
        assertTrue(x == 4)
    }

    @Test
    fun testReturn() {
        val resultIfSuccess = 11
        val resultIfException = 12
        fun doTestReturn(crash: Boolean) = tryTest {
            if (crash) throw RuntimeException() else resultIfSuccess
        }.catch<RuntimeException> {
            resultIfException
        } cleanup {}

        assertTrue(6 == tryTest { 6 } cleanup { Log.e(TAG, "tested") })
        assertEquals(resultIfSuccess, doTestReturn(crash = false))
        assertEquals(resultIfException, doTestReturn(crash = true))
    }

    @Test
    fun testCatch() {
        var x = 1
        tryTest {
            x = 2
            throw TestException1()
            x = 3
        }.catch<TestException1> {
            x = 4
        }.catch<TestException2> {
            x = 5
        } cleanup {
            assertTrue(x == 4)
            x = 6
        }
        assertTrue(x == 6)
    }

    @Test
    fun testNotCatch() {
        var x = 1
        assertFailsWith<TestException1> {
            tryTest {
                x = 2
                throw TestException1()
            }.catch<TestException2> {
                fail("Caught TestException2 instead of TestException1")
            } cleanup {
                assertTrue(x == 2)
                x = 3
            }
        }
        assertTrue(x == 3)
    }

    @Test
    fun testThrowInCatch() {
        var x = 1
        val thrown = assertFailsWith<TestException2> {
            tryTest {
                x = 2
                throw TestException1()
            }.catch<TestException1> {
                x = 3
                throw TestException2()
            } cleanup {
                assertTrue(x == 3)
                x = 4
            }
        }
        assertTrue(x == 4)
        assertTrue(thrown.suppressedExceptions.isEmpty())
    }

    @Test
    fun testAssertionErrorInCatch() {
        var x = 1
        val thrown = assertFailsWith<AssertionError> {
            tryTest {
                x = 2
                throw TestException1()
            }.catch<TestException1> {
                x = 3
                fail("Test failure in catch")
            } cleanup {
                assertTrue(x == 3)
                x = 4
            }
        }
        assertTrue(x == 4)
        assertTrue(thrown.suppressedExceptions.isEmpty())
    }

    @Test
    fun testMultipleCleanups() {
        var x = 1
        val thrown = assertFailsWith<TestException1> {
            tryTest {
                x = 2
                throw TestException1()
            } cleanupStep {
                assertTrue(x == 2)
                x = 3
                throw TestException2()
                x = 4
            } cleanupStep {
                assertTrue(x == 3)
                x = 5
                throw TestException3()
                x = 6
            } cleanup {
                assertTrue(x == 5)
                x = 7
            }
        }
        assertEquals(2, thrown.suppressedExceptions.size)
        assertTrue(thrown.suppressedExceptions[0] is TestException2)
        assertTrue(thrown.suppressedExceptions[1] is TestException3)
        assert(x == 7)
    }
}
