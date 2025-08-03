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

class RectFTest : DatatypeTest<RectF>() {
    override val valueEmpty = RectF.EMPTY
    override val valueTest = RectF.from(0.1f, 1.1f, 2.1f, 3.1f)
    override val valueEqual = RectF.from(0.1f, 1.1f, 2.1f, 3.1f)
    override val valueDifferent = RectF.from(4.1f, 5.1f, 6.1f, 7.1f)
    override val expectedValueAString = "(0.1, 1.1) - (2.1, 3.1)"

    @Test
    fun toRectFTest() {
        val valueF = RectF.from(left = 0f, top = 1f, right = 2f, bottom = 3f).toRect()
        val value = Rect.from(left = 0, top = 1, right = 2, bottom = 3)
        Assert.assertSame(value, valueF)
    }

    @Test
    fun testContains() {
        val rect = RectF.from(1f, 1f, 20f, 20f)
        var rectOther = RectF.from(1f, 1f, 20f, 20f)
        Assert.assertTrue(rect.contains(rectOther))
        rectOther = RectF.from(2f, 2f, 19f, 19f)
        Assert.assertTrue(rect.contains(rectOther))
        rectOther = RectF.from(21f, 21f, 22f, 22f)
        Assert.assertFalse(rect.contains(rectOther))
        rectOther = RectF.from(0f, 0f, 19f, 19f)
        Assert.assertFalse(rect.contains(rectOther))
    }

    @Test
    fun testWidth() {
        val rect = RectF.from(6f, 6f, 10f, 10f)
        Assert.assertEquals(4.0f, rect.width)
    }

    @Test
    fun testHeight() {
        val rect = RectF.from(6f, 6f, 10f, 10f)
        Assert.assertEquals(4.0f, rect.height)
    }

    @Test
    fun testIsEmpty() {
        var rect = RectF.EMPTY
        Assert.assertTrue(rect.isEmpty)
        rect = RectF.from(1f, 1f, 1f, 1f)
        Assert.assertTrue(rect.isEmpty)
        rect = RectF.from(0f, 1f, 2f, 1f)
        Assert.assertTrue(rect.isEmpty)
        rect = RectF.from(1f, 1f, 20f, 20f)
        Assert.assertFalse(rect.isEmpty)
    }
}
