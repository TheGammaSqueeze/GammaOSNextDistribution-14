/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.tools.datatypes

import org.junit.Assert
import org.junit.Test

abstract class DatatypeTest<T : DataType> {
    protected abstract val valueEmpty: T
    protected abstract val valueTest: T
    protected abstract val valueEqual: T
    protected abstract val valueDifferent: T
    protected abstract val expectedValueAString: String

    @Test
    fun testToString() {
        Assert.assertEquals(expectedValueAString, valueTest.toString())
    }

    @Test
    fun testEmptyToString() {
        Assert.assertEquals("[empty]", valueEmpty.toString())
    }

    @Test
    fun testCachedConstructor() {
        Assert.assertSame(valueEqual, valueTest)
        Assert.assertNotSame(valueDifferent, valueTest)
    }
}
