/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools

import org.junit.Assert
import org.junit.Test

class LoggerBuilderTest {

    @Test
    fun testOnVerboseWithoutException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setV { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.v(EXPECTED_TAG, EXPECTED_MSG)
        logger.d(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertNull(actualError)
    }

    @Test
    fun testOnVerboseWithException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setV { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.v(EXPECTED_TAG, EXPECTED_MSG, EXPECTED_ERROR)
        logger.d(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertSame(actualError, EXPECTED_ERROR)
    }

    @Test
    fun testOnDebugWithoutException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setD { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.d(EXPECTED_TAG, EXPECTED_MSG)
        logger.v(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertNull(actualError)
    }

    @Test
    fun testOnDebugWithException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setD { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.d(EXPECTED_TAG, EXPECTED_MSG, EXPECTED_ERROR)
        logger.v(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertSame(actualError, EXPECTED_ERROR)
    }

    @Test
    fun testOnInfoWithoutException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setI { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.i(EXPECTED_TAG, EXPECTED_MSG)
        logger.d(ERROR, ERROR)
        logger.v(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertNull(actualError)
    }

    @Test
    fun testOnInfoWithException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setI { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.i(EXPECTED_TAG, EXPECTED_MSG, EXPECTED_ERROR)
        logger.d(ERROR, ERROR)
        logger.v(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertSame(actualError, EXPECTED_ERROR)
    }

    @Test
    fun testOnWarmWithoutException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setW { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.w(EXPECTED_TAG, EXPECTED_MSG)
        logger.v(ERROR, ERROR)
        logger.d(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertNull(actualError)
    }

    @Test
    fun testOnWarmWithException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setW { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.w(EXPECTED_TAG, EXPECTED_MSG, EXPECTED_ERROR)
        logger.v(ERROR, ERROR)
        logger.d(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.e(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertSame(actualError, EXPECTED_ERROR)
    }

    @Test
    fun testOnErrorWithoutException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setE { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.e(EXPECTED_TAG, EXPECTED_MSG)
        logger.v(ERROR, ERROR)
        logger.d(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertNull(actualError)
    }

    @Test
    fun testOnErrorWithException() {
        var actualTag = ""
        var actualMsg = ""
        var actualError: Throwable? = Exception(ERROR)
        val logger =
            LoggerBuilder()
                .setE { tag, msg, error ->
                    actualTag = tag
                    actualMsg = msg
                    actualError = error
                }
                .build()
        logger.e(EXPECTED_TAG, EXPECTED_MSG, EXPECTED_ERROR)
        logger.v(ERROR, ERROR)
        logger.d(ERROR, ERROR)
        logger.i(ERROR, ERROR)
        logger.w(ERROR, ERROR)
        Assert.assertEquals(actualTag, EXPECTED_TAG)
        Assert.assertEquals(actualMsg, EXPECTED_MSG)
        Assert.assertSame(actualError, EXPECTED_ERROR)
    }

    companion object {
        const val EXPECTED_TAG = "TAG1"
        const val EXPECTED_MSG = "MSG1"
        const val ERROR = "ERROR"
        val EXPECTED_ERROR = Exception("EXPECTED")
    }
}
