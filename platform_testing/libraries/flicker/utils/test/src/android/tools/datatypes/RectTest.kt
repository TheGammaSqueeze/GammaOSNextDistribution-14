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

class RectTest : DatatypeTest<Rect>() {
    override val valueEmpty = Rect.EMPTY
    override val valueTest = Rect.from(0, 1, 2, 3)
    override val valueEqual = Rect.from(0, 1, 2, 3)
    override val valueDifferent = Rect.from(4, 5, 6, 7)
    override val expectedValueAString = "(0, 1) - (2, 3)"

    @Test
    fun toRectFTest() {
        val value = Rect.from(left = 0, top = 1, right = 2, bottom = 3).toRectF()
        val valueF = RectF.from(left = 0f, top = 1f, right = 2f, bottom = 3f)
        Assert.assertSame(valueF, value)
    }

    @Test
    fun testIntersects1() {
        var rect = Rect.from(0, 0, 10, 10)
        var rectOther = Rect.from(5, 5, 15, 15)
        var intersection = rect.intersection(rectOther)
        Assert.assertFalse(intersection.isEmpty)
        Assert.assertEquals(0, rect.left)
        Assert.assertEquals(0, rect.top)
        Assert.assertEquals(10, rect.right)
        Assert.assertEquals(10, rect.bottom)
        rect = Rect.from(0, 0, 10, 10)
        rectOther = Rect.from(15, 15, 25, 25)
        intersection = rect.intersection(rectOther)
        Assert.assertTrue(intersection.isEmpty)
        Assert.assertEquals(0, rect.left)
        Assert.assertEquals(0, rect.top)
        Assert.assertEquals(10, rect.right)
        Assert.assertEquals(10, rect.bottom)
    }

    @Test
    fun testIntersects2() {
        var rect = Rect.from(0, 0, 10, 10)
        var rectOther = Rect.from(5, 5, 15, 15)
        var intersection = rect.intersection(rectOther)
        Assert.assertFalse(intersection.isEmpty)
        rect = Rect.from(0, 0, 10, 10)
        rectOther = Rect.from(15, 15, 25, 25)
        intersection = rect.intersection(rectOther)
        Assert.assertTrue(intersection.isEmpty)
    }

    @Test
    fun testContains1() {
        val rect = Rect.from(1, 1, 20, 20)
        Assert.assertFalse(rect.contains(0, 0))
        Assert.assertTrue(rect.contains(1, 1))
        Assert.assertTrue(rect.contains(19, 19))
        Assert.assertFalse(rect.contains(20, 20))
    }

    @Test
    fun testContains2() {
        val rect = Rect.from(1, 1, 20, 20)
        var rectOther = Rect.from(1, 1, 20, 20)
        Assert.assertTrue(rect.contains(rectOther))
        rectOther = Rect.from(2, 2, 19, 19)
        Assert.assertTrue(rect.contains(rectOther))
        rectOther = Rect.from(21, 21, 22, 22)
        Assert.assertFalse(rect.contains(rectOther))
        rectOther = Rect.from(0, 0, 19, 19)
        Assert.assertFalse(rect.contains(rectOther))
    }

    @Test
    fun testWidth() {
        val rect = Rect.from(6, 6, 10, 10)
        Assert.assertEquals(4, rect.width)
    }

    @Test
    fun testHeight() {
        val rect = Rect.from(6, 6, 10, 10)
        Assert.assertEquals(4, rect.height)
    }

    @Test
    fun testIsEmpty() {
        var rect = Rect.EMPTY
        Assert.assertTrue(rect.isEmpty)
        rect = Rect.from(1, 1, 1, 1)
        Assert.assertTrue(rect.isEmpty)
        rect = Rect.from(0, 1, 2, 1)
        Assert.assertTrue(rect.isEmpty)
        rect = Rect.from(1, 1, 20, 20)
        Assert.assertFalse(rect.isEmpty)
    }

    @Test
    fun testCenterY() {
        var rect = Rect.from(10, 10, 20, 20)
        Assert.assertEquals(15, rect.centerY())
        rect = Rect.from(10, 11, 20, 20)
        Assert.assertEquals(15, rect.centerY())
        rect = Rect.from(10, 12, 20, 20)
        Assert.assertEquals(16, rect.centerY())
    }

    @Test
    fun testCenterX() {
        var rect = Rect.from(10, 10, 20, 20)
        Assert.assertEquals(15, rect.centerX())
        rect = Rect.from(11, 10, 20, 20)
        Assert.assertEquals(15, rect.centerX())
        rect = Rect.from(12, 10, 20, 20)
        Assert.assertEquals(16, rect.centerX())
    }
}
