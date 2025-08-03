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

@file:JvmName("Cleanup")

package com.android.testutils

import com.android.testutils.FunctionalUtils.ThrowingRunnable
import com.android.testutils.FunctionalUtils.ThrowingSupplier
import javax.annotation.CheckReturnValue

/**
 * Utility to do cleanup in tests without replacing exceptions with those from a finally block.
 *
 * This utility is meant for tests that want to do cleanup after they execute their test
 * logic, whether the test fails (and throws) or not.
 *
 * The usual way of doing this is to have a try{}finally{} block and put cleanup in finally{}.
 * However, if any code in finally{} throws, the exception thrown in finally{} is thrown before
 * any thrown in try{} ; that means errors reported from tests are from finally{} even if they
 * have been caused by errors in try{}. This is unhelpful in tests, because it results in a
 * stacktrace for a symptom rather than a stacktrace for a cause.
 *
 * To alleviate this, tests are encouraged to make sure the code in finally{} can't throw, or
 * that the code in try{} can't cause it to fail. This is not always realistic ; not only does
 * it require the developer thinks about complex interactions of code, test code often relies
 * on bricks provided by other teams, not controlled by the team writing the test, which may
 * start throwing with an update (see b/198998862 for an example).
 *
 * This utility allows a different approach : it offers a new construct, tryTest{}cleanup{} similar
 * to try{}finally{}, but that will always throw the first exception that happens. In other words,
 * if only tryTest{} throws or only cleanup{} throws, that exception will be thrown, but contrary
 * to the standard try{}finally{}, if both throws, the construct throws the exception that happened
 * in tryTest{} rather than the one that happened in cleanup{}.
 *
 * Kotlin usage is as try{}finally{}, but with multiple finally{} blocks :
 * tryTest {
 *   testing code
 * } cleanupStep {
 *   cleanup code 1
 * } cleanupStep {
 *   cleanup code 2
 * } cleanup {
 *   cleanup code 3
 * }
 * Catch blocks can be added with the following syntax :
 * tryTest {
 *   testing code
 * }.catch<ExceptionType> { it ->
 *   do something to it
 * }
 *
 * Java doesn't allow this kind of syntax, so instead a function taking lambdas is provided.
 * testAndCleanup(() -> {
 *   testing code
 * }, () -> {
 *   cleanup code 1
 * }, () -> {
 *   cleanup code 2
 * });
 */

@CheckReturnValue
fun <T> tryTest(block: () -> T) = TryExpr(
        try {
            Result.success(block())
        } catch (e: Throwable) {
            Result.failure(e)
        })

// Some downstream branches have an older kotlin that doesn't know about value classes.
// TODO : Change this to "value class" when aosp no longer merges into such branches.
@Suppress("INLINE_CLASS_DEPRECATED")
inline class TryExpr<T>(val result: Result<T>) {
    inline infix fun <reified E : Throwable> catch(block: (E) -> T): TryExpr<T> {
        val originalException = result.exceptionOrNull()
        if (originalException !is E) return this
        return TryExpr(try {
            Result.success(block(originalException))
        } catch (e: Throwable) {
            Result.failure(e)
        })
    }

    @CheckReturnValue
    inline infix fun cleanupStep(block: () -> Unit): TryExpr<T> {
        try {
            block()
        } catch (e: Throwable) {
            val originalException = result.exceptionOrNull()
            return TryExpr(if (null == originalException) {
                Result.failure(e)
            } else {
                originalException.addSuppressed(e)
                Result.failure(originalException)
            })
        }
        return this
    }

    inline infix fun cleanup(block: () -> Unit): T = cleanupStep(block).result.getOrThrow()
}

// Java support
fun <T> testAndCleanup(tryBlock: ThrowingSupplier<T>, vararg cleanupBlock: ThrowingRunnable): T {
    return cleanupBlock.fold(tryTest { tryBlock.get() }) { previousExpr, nextCleanup ->
        previousExpr.cleanupStep { nextCleanup.run() }
    }.cleanup {}
}
fun testAndCleanup(tryBlock: ThrowingRunnable, vararg cleanupBlock: ThrowingRunnable) {
    return testAndCleanup(ThrowingSupplier { tryBlock.run() }, *cleanupBlock)
}
