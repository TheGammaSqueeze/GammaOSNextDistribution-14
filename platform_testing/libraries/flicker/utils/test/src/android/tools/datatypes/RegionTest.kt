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

import android.tools.utils.CleanFlickerEnvironmentRule
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [Region] tests. To run this test: `atest FlickerLibTest:RegionTest` */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RegionTest {
    private var region: Region = Region()

    private fun verifyPointsInsideRegion(area: Array<IntArray>) {
        for (i in area.indices) {
            assertTrue(region.contains(area[i][0], area[i][1]))
        }
    }

    private fun verifyPointsOutsideRegion(area: Array<IntArray>) {
        for (i in area.indices) {
            assertFalse(region.contains(area[i][0], area[i][1]))
        }
    }

    @Before
    fun setup() {
        region = Region()
    }

    @Test
    fun testConstructor() {
        // Test Region()
        Region()

        // Test Region(Region)
        val oriRegion = Region()
        Region.from(oriRegion)

        // Test Region(Rect)
        val rect = Rect.from(0, 0, 0, 0)
        Region.from(rect)

        // Test Region(int, int, int, int)
        Region.from(0, 0, 100, 100)
    }

    @Test
    fun testSet1() {
        val rect = Rect.from(1, 2, 3, 4)
        val oriRegion = Region.from(rect)
        assertTrue(region.set(oriRegion))
        assertEquals(1, region.bounds.left)
        assertEquals(2, region.bounds.top)
        assertEquals(3, region.bounds.right)
        assertEquals(4, region.bounds.bottom)
    }

    @Test
    fun testSet2() {
        val rect = Rect.from(1, 2, 3, 4)
        assertTrue(region.set(rect))
        assertEquals(1, region.bounds.left)
        assertEquals(2, region.bounds.top)
        assertEquals(3, region.bounds.right)
        assertEquals(4, region.bounds.bottom)
    }

    @Test
    fun testSet3() {
        assertTrue(region.set(1, 2, 3, 4))
        assertEquals(1, region.bounds.left)
        assertEquals(2, region.bounds.top)
        assertEquals(3, region.bounds.right)
        assertEquals(4, region.bounds.bottom)
    }

    @Test
    fun testIsRect() {
        assertFalse(region.isRect())
        region = Region.from(1, 2, 3, 4)
        assertTrue(region.isRect())
    }

    @Test
    fun testIsComplex() {
        // Region is empty
        assertFalse(region.isComplex())

        // Only one rectangle
        region = Region()
        region.set(1, 2, 3, 4)
        assertFalse(region.isComplex())

        // More than one rectangle
        region = Region()
        region.set(1, 1, 2, 2)
        region.union(Rect.from(3, 3, 5, 5))
        assertTrue(region.isComplex())
    }

    @Test
    fun testUnion() {
        val rect1 = Rect.from(0, 0, 0, 0)
        val rect2 = Rect.from(0, 0, 20, 20)
        val rect3 = Rect.from(5, 5, 10, 10)
        val rect4 = Rect.from(10, 10, 30, 30)
        val rect5 = Rect.from(40, 40, 60, 60)

        // union (inclusive-or) the two regions
        region.set(rect2)
        // union null rectangle
        assertTrue(region.contains(6, 6))
        assertTrue(region.union(rect1))
        assertTrue(region.contains(6, 6))

        // 1. union rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.union(rect3))
        verifyPointsInsideRegion(UNION_WITH1)
        verifyPointsOutsideRegion(UNION_WITHOUT1)

        // 2. union rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(21, 21))
        assertTrue(region.union(rect4))
        verifyPointsInsideRegion(UNION_WITH2)
        verifyPointsOutsideRegion(UNION_WITHOUT2)

        // 3. union rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.union(rect5))
        verifyPointsInsideRegion(UNION_WITH3)
        verifyPointsOutsideRegion(UNION_WITHOUT3)
    }

    @Test
    fun testContains() {
        region.set(2, 2, 5, 5)
        // Not contain (1, 1).
        assertFalse(region.contains(1, 1))

        // Test point inside this region.
        assertTrue(region.contains(3, 3))

        // Test left-top corner.
        assertTrue(region.contains(2, 2))

        // Test left-bottom corner.
        assertTrue(region.contains(2, 4))

        // Test right-top corner.
        assertTrue(region.contains(4, 2))

        // Test right-bottom corner.
        assertTrue(region.contains(4, 4))

        // Though you set 5, but 5 is not contained by this region.
        assertFalse(region.contains(5, 5))
        assertFalse(region.contains(2, 5))
        assertFalse(region.contains(5, 2))

        // Set a new rectangle.
        region.set(6, 6, 8, 8)
        assertFalse(region.contains(3, 3))
        assertTrue(region.contains(7, 7))
    }

    @Test
    fun testEmpty() {
        assertTrue(region.isEmpty)
        region = Region.from(1, 2, 3, 4)
        assertFalse(region.isEmpty)
        region.setEmpty()
        assertTrue(region.isEmpty)
    }

    @Test
    fun testOp1() {
        val rect1 = Rect.from(0, 0, 0, 0)
        val rect2 = Rect.from(0, 0, 20, 20)
        val rect3 = Rect.from(5, 5, 10, 10)
        val rect4 = Rect.from(10, 10, 30, 30)
        val rect5 = Rect.from(40, 40, 60, 60)
        verifyNullRegionOp1(rect1)
        verifyDifferenceOp1(rect1, rect2, rect3, rect4, rect5)
        verifyIntersectOp1(rect1, rect2, rect3, rect4, rect5)
        verifyUnionOp1(rect1, rect2, rect3, rect4, rect5)
        verifyXorOp1(rect1, rect2, rect3, rect4, rect5)
        verifyReverseDifferenceOp1(rect1, rect2, rect3, rect4, rect5)
        verifyReplaceOp1(rect1, rect2, rect3, rect4, rect5)
    }

    private fun verifyNullRegionOp1(rect1: Rect) {
        // Region without rectangle
        region = Region()
        assertFalse(region.op(rect1, Region.Op.DIFFERENCE))
        assertFalse(region.op(rect1, Region.Op.INTERSECT))
        assertFalse(region.op(rect1, Region.Op.UNION))
        assertFalse(region.op(rect1, Region.Op.XOR))
        assertFalse(region.op(rect1, Region.Op.REVERSE_DIFFERENCE))
        assertFalse(region.op(rect1, Region.Op.REPLACE))
    }

    private fun verifyDifferenceOp1(
        rect1: Rect,
        rect2: Rect,
        rect3: Rect,
        rect4: Rect,
        rect5: Rect
    ) {
        // DIFFERENCE, Region with rectangle
        // subtract the op region from the first region
        region = Region()
        // subtract null rectangle
        region.set(rect2)
        assertTrue(region.op(rect1, Region.Op.DIFFERENCE))

        // 1. subtract rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(rect3, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH1)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT1)

        // 2. subtract rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(11, 11))
        assertTrue(region.op(rect4, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT2)

        // 3. subtract rectangle out of this region
        region.set(rect2)
        assertTrue(region.op(rect5, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT3)
    }

    private fun verifyIntersectOp1(
        rect1: Rect,
        rect2: Rect,
        rect3: Rect,
        rect4: Rect,
        rect5: Rect
    ) {
        // INTERSECT, Region with rectangle
        // intersect the two regions
        region = Region()
        // intersect null rectangle
        region.set(rect2)
        assertFalse(region.op(rect1, Region.Op.INTERSECT))

        // 1. intersect rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.op(rect3, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH1)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT1)

        // 2. intersect rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(9, 9))
        assertTrue(region.op(rect4, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH2)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT2)

        // 3. intersect rectangle out of this region
        region.set(rect2)
        assertFalse(region.op(rect5, Region.Op.INTERSECT))
    }

    private fun verifyUnionOp1(rect1: Rect, rect2: Rect, rect3: Rect, rect4: Rect, rect5: Rect) {
        // UNION, Region with rectangle
        // union (inclusive-or) the two regions
        region = Region()
        region.set(rect2)
        // union null rectangle
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(rect1, Region.Op.UNION))
        assertTrue(region.contains(6, 6))

        // 1. union rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(rect3, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH1)
        verifyPointsOutsideRegion(UNION_WITHOUT1)

        // 2. union rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(rect4, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH2)
        verifyPointsOutsideRegion(UNION_WITHOUT2)

        // 3. union rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(rect5, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH3)
        verifyPointsOutsideRegion(UNION_WITHOUT3)
    }

    private fun verifyXorOp1(rect1: Rect, rect2: Rect, rect3: Rect, rect4: Rect, rect5: Rect) {
        // XOR, Region with rectangle
        // exclusive-or the two regions
        region = Region()
        // xor null rectangle
        region.set(rect2)
        assertTrue(region.op(rect1, Region.Op.XOR))

        // 1. xor rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(rect3, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH1)
        verifyPointsOutsideRegion(XOR_WITHOUT1)

        // 2. xor rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(11, 11))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(rect4, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH2)
        verifyPointsOutsideRegion(XOR_WITHOUT2)

        // 3. xor rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(rect5, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH3)
        verifyPointsOutsideRegion(XOR_WITHOUT3)
    }

    private fun verifyReverseDifferenceOp1(
        rect1: Rect,
        rect2: Rect,
        rect3: Rect,
        rect4: Rect,
        rect5: Rect
    ) {
        // REVERSE_DIFFERENCE, Region with rectangle
        // reverse difference the first region from the op region
        region = Region()
        region.set(rect2)
        // reverse difference null rectangle
        assertFalse(region.op(rect1, Region.Op.REVERSE_DIFFERENCE))

        // 1. reverse difference rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertFalse(region.op(rect3, Region.Op.REVERSE_DIFFERENCE))

        // 2. reverse difference rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(11, 11))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(rect4, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT2)

        // 3. reverse difference rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(rect5, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT3)
    }

    private fun verifyReplaceOp1(rect1: Rect, rect2: Rect, rect3: Rect, rect4: Rect, rect5: Rect) {
        // REPLACE, Region with rectangle
        // replace the dst region with the op region
        region = Region()
        region.set(rect2)
        // subtract null rectangle
        assertFalse(region.op(rect1, Region.Op.REPLACE))
        // subtract rectangle inside this region
        region.set(rect2)
        assertEquals(rect2, region.bounds)
        assertTrue(region.op(rect3, Region.Op.REPLACE))
        assertNotSame(rect2, region.bounds)
        assertEquals(rect3, region.bounds)
        // subtract rectangle overlap this region
        region.set(rect2)
        assertEquals(rect2, region.bounds)
        assertTrue(region.op(rect4, Region.Op.REPLACE))
        assertNotSame(rect2, region.bounds)
        assertEquals(rect4, region.bounds)
        // subtract rectangle out of this region
        region.set(rect2)
        assertEquals(rect2, region.bounds)
        assertTrue(region.op(rect5, Region.Op.REPLACE))
        assertNotSame(rect2, region.bounds)
        assertEquals(rect5, region.bounds)
    }

    @Test
    fun testOp2() {
        val rect2 = Rect.from(0, 0, 20, 20)
        val rect3 = Rect.from(5, 5, 10, 10)
        val rect4 = Rect.from(10, 10, 30, 30)
        val rect5 = Rect.from(40, 40, 60, 60)
        verifyNullRegionOp2()
        verifyDifferenceOp2(rect2)
        verifyIntersectOp2(rect2)
        verifyUnionOp2(rect2)
        verifyXorOp2(rect2)
        verifyReverseDifferenceOp2(rect2)
        verifyReplaceOp2(rect2, rect3, rect4, rect5)
    }

    private fun verifyNullRegionOp2() {
        // Region without rectangle
        region = Region()
        assertFalse(region.op(0, 0, 0, 0, Region.Op.DIFFERENCE))
        assertFalse(region.op(0, 0, 0, 0, Region.Op.INTERSECT))
        assertFalse(region.op(0, 0, 0, 0, Region.Op.UNION))
        assertFalse(region.op(0, 0, 0, 0, Region.Op.XOR))
        assertFalse(region.op(0, 0, 0, 0, Region.Op.REVERSE_DIFFERENCE))
        assertFalse(region.op(0, 0, 0, 0, Region.Op.REPLACE))
    }

    private fun verifyDifferenceOp2(rect2: Rect) {
        // DIFFERENCE, Region with rectangle
        // subtract the op region from the first region
        region = Region()
        // subtract null rectangle
        region.set(rect2)
        assertTrue(region.op(0, 0, 0, 0, Region.Op.DIFFERENCE))

        // 1. subtract rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(5, 5, 10, 10, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH1)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT1)

        // 2. subtract rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(11, 11))
        assertTrue(region.op(10, 10, 30, 30, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT2)

        // 3. subtract rectangle out of this region
        region.set(rect2)
        assertTrue(region.op(40, 40, 60, 60, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT3)
    }

    private fun verifyIntersectOp2(rect2: Rect) {
        // INTERSECT, Region with rectangle
        // intersect the two regions
        region = Region()
        // intersect null rectangle
        region.set(rect2)
        assertFalse(region.op(0, 0, 0, 0, Region.Op.INTERSECT))

        // 1. intersect rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.op(5, 5, 10, 10, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH1)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT1)

        // 2. intersect rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(9, 9))
        assertTrue(region.op(10, 10, 30, 30, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH2)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT2)

        // 3. intersect rectangle out of this region
        region.set(rect2)
        assertFalse(region.op(40, 40, 60, 60, Region.Op.INTERSECT))
    }

    private fun verifyUnionOp2(rect2: Rect) {
        // UNION, Region with rectangle
        // union (inclusive-or) the two regions
        region = Region()
        region.set(rect2)
        // union null rectangle
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(0, 0, 0, 0, Region.Op.UNION))
        assertTrue(region.contains(6, 6))

        // 1. union rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(5, 5, 10, 10, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH1)
        verifyPointsOutsideRegion(UNION_WITHOUT1)

        // 2. union rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(10, 10, 30, 30, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH2)
        verifyPointsOutsideRegion(UNION_WITHOUT2)

        // 3. union rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(40, 40, 60, 60, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH3)
        verifyPointsOutsideRegion(UNION_WITHOUT3)
    }

    private fun verifyXorOp2(rect2: Rect) {
        // XOR, Region with rectangle
        // exclusive-or the two regions
        region = Region()
        region.set(rect2)
        // xor null rectangle
        assertTrue(region.op(0, 0, 0, 0, Region.Op.XOR))

        // 1. xor rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(5, 5, 10, 10, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH1)
        verifyPointsOutsideRegion(XOR_WITHOUT1)

        // 2. xor rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(11, 11))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(10, 10, 30, 30, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH2)
        verifyPointsOutsideRegion(XOR_WITHOUT2)

        // 3. xor rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(40, 40, 60, 60, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH3)
        verifyPointsOutsideRegion(XOR_WITHOUT3)
    }

    private fun verifyReverseDifferenceOp2(rect2: Rect) {
        // REVERSE_DIFFERENCE, Region with rectangle
        // reverse difference the first region from the op region
        region = Region()
        region.set(rect2)
        // reverse difference null rectangle
        assertFalse(region.op(0, 0, 0, 0, Region.Op.REVERSE_DIFFERENCE))
        // reverse difference rectangle inside this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertFalse(region.op(5, 5, 10, 10, Region.Op.REVERSE_DIFFERENCE))
        // reverse difference rectangle overlap this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(11, 11))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(10, 10, 30, 30, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT2)
        // reverse difference rectangle out of this region
        region.set(rect2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(40, 40, 60, 60, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT3)
    }

    private fun verifyReplaceOp2(rect2: Rect, rect3: Rect, rect4: Rect, rect5: Rect) {
        // REPLACE, Region w1ith rectangle
        // replace the dst region with the op region
        region = Region()
        region.set(rect2)
        // subtract null rectangle
        assertFalse(region.op(0, 0, 0, 0, Region.Op.REPLACE))
        // subtract rectangle inside this region
        region.set(rect2)
        assertEquals(rect2, region.bounds)
        assertTrue(region.op(5, 5, 10, 10, Region.Op.REPLACE))
        assertNotSame(rect2, region.bounds)
        assertEquals(rect3, region.bounds)
        // subtract rectangle overlap this region
        region.set(rect2)
        assertEquals(rect2, region.bounds)
        assertTrue(region.op(10, 10, 30, 30, Region.Op.REPLACE))
        assertNotSame(rect2, region.bounds)
        assertEquals(rect4, region.bounds)
        // subtract rectangle out of this region
        region.set(rect2)
        assertEquals(rect2, region.bounds)
        assertTrue(region.op(40, 40, 60, 60, Region.Op.REPLACE))
        assertNotSame(rect2, region.bounds)
        assertEquals(rect5, region.bounds)
    }

    @Test
    fun testOp3() {
        val region1 = Region()
        val region2 = Region.from(0, 0, 20, 20)
        val region3 = Region.from(5, 5, 10, 10)
        val region4 = Region.from(10, 10, 30, 30)
        val region5 = Region.from(40, 40, 60, 60)
        verifyNullRegionOp3(region1)
        verifyDifferenceOp3(region1, region2, region3, region4, region5)
        verifyIntersectOp3(region1, region2, region3, region4, region5)
        verifyUnionOp3(region1, region2, region3, region4, region5)
        verifyXorOp3(region1, region2, region3, region4, region5)
        verifyReverseDifferenceOp3(region1, region2, region3, region4, region5)
        verifyReplaceOp3(region1, region2, region3, region4, region5)
    }

    private fun verifyNullRegionOp3(region1: Region) {
        // Region without rectangle
        region = Region()
        assertFalse(region.op(region1, Region.Op.DIFFERENCE))
        assertFalse(region.op(region1, Region.Op.INTERSECT))
        assertFalse(region.op(region1, Region.Op.UNION))
        assertFalse(region.op(region1, Region.Op.XOR))
        assertFalse(region.op(region1, Region.Op.REVERSE_DIFFERENCE))
        assertFalse(region.op(region1, Region.Op.REPLACE))
    }

    private fun verifyDifferenceOp3(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // DIFFERENCE, Region with rectangle
        // subtract the op region from the first region
        region = Region()
        // subtract null rectangle
        region.set(region2)
        assertTrue(region.op(region1, Region.Op.DIFFERENCE))

        // 1. subtract rectangle inside this region
        region.set(region2)
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(region3, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH1)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT1)

        // 2. subtract rectangle overlap this region
        region.set(region2)
        assertTrue(region.contains(11, 11))
        assertTrue(region.op(region4, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT2)

        // 3. subtract rectangle out of this region
        region.set(region2)
        assertTrue(region.op(region5, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT3)
    }

    private fun verifyIntersectOp3(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // INTERSECT, Region with rectangle
        // intersect the two regions
        region = Region()
        region.set(region2)
        // intersect null rectangle
        assertFalse(region.op(region1, Region.Op.INTERSECT))

        // 1. intersect rectangle inside this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.op(region3, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH1)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT1)

        // 2. intersect rectangle overlap this region
        region.set(region2)
        assertTrue(region.contains(9, 9))
        assertTrue(region.op(region4, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH2)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT2)

        // 3. intersect rectangle out of this region
        region.set(region2)
        assertFalse(region.op(region5, Region.Op.INTERSECT))
    }

    private fun verifyUnionOp3(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // UNION, Region with rectangle
        // union (inclusive-or) the two regions
        region = Region()
        // union null rectangle
        region.set(region2)
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(region1, Region.Op.UNION))
        assertTrue(region.contains(6, 6))

        // 1. union rectangle inside this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(region3, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH1)
        verifyPointsOutsideRegion(UNION_WITHOUT1)

        // 2. union rectangle overlap this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(region4, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH2)
        verifyPointsOutsideRegion(UNION_WITHOUT2)

        // 3. union rectangle out of this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(region5, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH3)
        verifyPointsOutsideRegion(UNION_WITHOUT3)
    }

    private fun verifyXorOp3(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // XOR, Region with rectangle
        // exclusive-or the two regions
        region = Region()
        // xor null rectangle
        region.set(region2)
        assertTrue(region.op(region1, Region.Op.XOR))

        // 1. xor rectangle inside this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertTrue(region.op(region3, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH1)
        verifyPointsOutsideRegion(XOR_WITHOUT1)

        // 2. xor rectangle overlap this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(11, 11))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(region4, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH2)
        verifyPointsOutsideRegion(XOR_WITHOUT2)

        // 3. xor rectangle out of this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(region5, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH3)
        verifyPointsOutsideRegion(XOR_WITHOUT3)
    }

    private fun verifyReverseDifferenceOp3(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // REVERSE_DIFFERENCE, Region with rectangle
        // reverse difference the first region from the op region
        region = Region()
        // reverse difference null rectangle
        region.set(region2)
        assertFalse(region.op(region1, Region.Op.REVERSE_DIFFERENCE))

        // 1. reverse difference rectangle inside this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(6, 6))
        assertFalse(region.op(region3, Region.Op.REVERSE_DIFFERENCE))

        // 2. reverse difference rectangle overlap this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertTrue(region.contains(11, 11))
        assertFalse(region.contains(21, 21))
        assertTrue(region.op(region4, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT2)

        // 3. reverse difference rectangle out of this region
        region.set(region2)
        assertTrue(region.contains(2, 2))
        assertFalse(region.contains(41, 41))
        assertTrue(region.op(region5, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT3)
    }

    private fun verifyReplaceOp3(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // REPLACE, Region with rectangle
        // replace the dst region with the op region
        region = Region()
        region.set(region2)
        // subtract null rectangle
        assertFalse(region.op(region1, Region.Op.REPLACE))
        // subtract rectangle inside this region
        region.set(region2)
        assertEquals(region2.bounds, region.bounds)
        assertTrue(region.op(region3, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region3.bounds, region.bounds)
        // subtract rectangle overlap this region
        region.set(region2)
        assertEquals(region2.bounds, region.bounds)
        assertTrue(region.op(region4, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region4.bounds, region.bounds)
        // subtract rectangle out of this region
        region.set(region2)
        assertEquals(region2.bounds, region.bounds)
        assertTrue(region.op(region5, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region5.bounds, region.bounds)
    }

    @Test
    fun testOp4() {
        val rect1 = Rect.from(0, 0, 0, 0)
        val rect2 = Rect.from(0, 0, 20, 20)
        val region1 = Region()
        val region2 = Region.from(0, 0, 20, 20)
        val region3 = Region.from(5, 5, 10, 10)
        val region4 = Region.from(10, 10, 30, 30)
        val region5 = Region.from(40, 40, 60, 60)
        verifyNullRegionOp4(rect1, region1)
        verifyDifferenceOp4(rect1, rect2, region1, region3, region4, region5)
        verifyIntersectOp4(rect1, rect2, region1, region3, region4, region5)
        verifyUnionOp4(rect1, rect2, region1, region3, region4, region5)
        verifyXorOp4(rect1, rect2, region1, region3, region4, region5)
        verifyReverseDifferenceOp4(rect1, rect2, region1, region3, region4, region5)
        verifyReplaceOp4(rect1, rect2, region1, region2, region3, region4, region5)
    }

    private fun verifyNullRegionOp4(rect1: Rect, region1: Region) {
        // Region without rectangle
        region = Region()
        assertFalse(region.op(rect1, region1, Region.Op.DIFFERENCE))
        assertFalse(region.op(rect1, region1, Region.Op.INTERSECT))
        assertFalse(region.op(rect1, region1, Region.Op.UNION))
        assertFalse(region.op(rect1, region1, Region.Op.XOR))
        assertFalse(region.op(rect1, region1, Region.Op.REVERSE_DIFFERENCE))
        assertFalse(region.op(rect1, region1, Region.Op.REPLACE))
    }

    private fun verifyDifferenceOp4(
        rect1: Rect,
        rect2: Rect,
        region1: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // DIFFERENCE, Region with rectangle
        // subtract the op region from the first region
        region = Region()
        // subtract null rectangle
        assertTrue(region.op(rect2, region1, Region.Op.DIFFERENCE))

        // 1. subtract rectangle inside this region
        region.set(rect1)
        assertTrue(region.op(rect2, region3, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH1)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT1)

        // 2. subtract rectangle overlap this region
        region.set(rect1)
        assertTrue(region.op(rect2, region4, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT2)

        // 3. subtract rectangle out of this region
        region.set(rect1)
        assertTrue(region.op(rect2, region5, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT3)
    }

    private fun verifyIntersectOp4(
        rect1: Rect,
        rect2: Rect,
        region1: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // INTERSECT, Region with rectangle
        // intersect the two regions
        region = Region()
        // intersect null rectangle
        region.set(rect1)
        assertFalse(region.op(rect2, region1, Region.Op.INTERSECT))

        // 1. intersect rectangle inside this region
        region.set(rect1)
        assertTrue(region.op(rect2, region3, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH1)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT1)

        // 2. intersect rectangle overlap this region
        region.set(rect1)
        assertTrue(region.op(rect2, region4, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH2)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT2)

        // 3. intersect rectangle out of this region
        region.set(rect1)
        assertFalse(region.op(rect2, region5, Region.Op.INTERSECT))
    }

    private fun verifyUnionOp4(
        rect1: Rect,
        rect2: Rect,
        region1: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // UNION, Region with rectangle
        // union (inclusive-or) the two regions
        region = Region()
        // union null rectangle
        region.set(rect1)
        assertTrue(region.op(rect2, region1, Region.Op.UNION))
        assertTrue(region.contains(6, 6))

        // 1. union rectangle inside this region
        region.set(rect1)
        assertTrue(region.op(rect2, region3, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH1)
        verifyPointsOutsideRegion(UNION_WITHOUT1)

        // 2. union rectangle overlap this region
        region.set(rect1)
        assertTrue(region.op(rect2, region4, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH2)
        verifyPointsOutsideRegion(UNION_WITHOUT2)

        // 3. union rectangle out of this region
        region.set(rect1)
        assertTrue(region.op(rect2, region5, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH3)
        verifyPointsOutsideRegion(UNION_WITHOUT3)
    }

    private fun verifyXorOp4(
        rect1: Rect,
        rect2: Rect,
        region1: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // XOR, Region with rectangle
        // exclusive-or the two regions
        region = Region()
        // xor null rectangle
        region.set(rect1)
        assertTrue(region.op(rect2, region1, Region.Op.XOR))

        // 1. xor rectangle inside this region
        region.set(rect1)
        assertTrue(region.op(rect2, region3, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH1)
        verifyPointsOutsideRegion(XOR_WITHOUT1)

        // 2. xor rectangle overlap this region
        region.set(rect1)
        assertTrue(region.op(rect2, region4, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH2)
        verifyPointsOutsideRegion(XOR_WITHOUT2)

        // 3. xor rectangle out of this region
        region.set(rect1)
        assertTrue(region.op(rect2, region5, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH3)
        verifyPointsOutsideRegion(XOR_WITHOUT3)
    }

    private fun verifyReverseDifferenceOp4(
        rect1: Rect,
        rect2: Rect,
        region1: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // REVERSE_DIFFERENCE, Region with rectangle
        // reverse difference the first region from the op region
        region = Region()
        // reverse difference null rectangle
        region.set(rect1)
        assertFalse(region.op(rect2, region1, Region.Op.REVERSE_DIFFERENCE))

        // 1. reverse difference rectangle inside this region
        region.set(rect1)
        assertFalse(region.op(rect2, region3, Region.Op.REVERSE_DIFFERENCE))

        // 2. reverse difference rectangle overlap this region
        region.set(rect1)
        assertTrue(region.op(rect2, region4, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT2)

        // 3. reverse difference rectangle out of this region
        region.set(rect1)
        assertTrue(region.op(rect2, region5, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT3)
    }

    private fun verifyReplaceOp4(
        rect1: Rect,
        rect2: Rect,
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // REPLACE, Region with rectangle
        // replace the dst region with the op region
        region = Region()
        // subtract null rectangle
        region.set(rect1)
        assertFalse(region.op(rect2, region1, Region.Op.REPLACE))
        // subtract rectangle inside this region
        region.set(rect1)
        assertTrue(region.op(rect2, region3, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region3.bounds, region.bounds)
        // subtract rectangle overlap this region
        region.set(rect1)
        assertTrue(region.op(rect2, region4, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region4.bounds, region.bounds)
        // subtract rectangle out of this region
        region.set(rect1)
        assertTrue(region.op(rect2, region5, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region5.bounds, region.bounds)
    }

    @Test
    fun testOp5() {
        val region1 = Region()
        val region2 = Region.from(0, 0, 20, 20)
        val region3 = Region.from(5, 5, 10, 10)
        val region4 = Region.from(10, 10, 30, 30)
        val region5 = Region.from(40, 40, 60, 60)
        verifyNullRegionOp5(region1)
        verifyDifferenceOp5(region1, region2, region3, region4, region5)
        verifyIntersectOp5(region1, region2, region3, region4, region5)
        verifyUnionOp5(region1, region2, region3, region4, region5)
        verifyXorOp5(region1, region2, region3, region4, region5)
        verifyReverseDifferenceOp5(region1, region2, region3, region4, region5)
        verifyReplaceOp5(region1, region2, region3, region4, region5)
    }

    private fun verifyNullRegionOp5(region1: Region) {
        // Region without rectangle
        region = Region()
        assertFalse(region.op(region, region1, Region.Op.DIFFERENCE))
        assertFalse(region.op(region, region1, Region.Op.INTERSECT))
        assertFalse(region.op(region, region1, Region.Op.UNION))
        assertFalse(region.op(region, region1, Region.Op.XOR))
        assertFalse(region.op(region, region1, Region.Op.REVERSE_DIFFERENCE))
        assertFalse(region.op(region, region1, Region.Op.REPLACE))
    }

    private fun verifyDifferenceOp5(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // DIFFERENCE, Region with rectangle
        // subtract the op region from the first region
        region = Region()
        // subtract null rectangle
        region.set(region1)
        assertTrue(region.op(region2, region1, Region.Op.DIFFERENCE))

        // 1. subtract rectangle inside this region
        region.set(region1)
        assertTrue(region.op(region2, region3, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH1)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT1)

        // 2. subtract rectangle overlap this region
        region.set(region1)
        assertTrue(region.op(region2, region4, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT2)

        // 3. subtract rectangle out of this region
        region.set(region1)
        assertTrue(region.op(region2, region5, Region.Op.DIFFERENCE))
        verifyPointsInsideRegion(DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(DIFFERENCE_WITHOUT3)
    }

    private fun verifyIntersectOp5(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // INTERSECT, Region with rectangle
        // intersect the two regions
        region = Region()
        // intersect null rectangle
        region.set(region1)
        assertFalse(region.op(region2, region1, Region.Op.INTERSECT))

        // 1. intersect rectangle inside this region
        region.set(region1)
        assertTrue(region.op(region2, region3, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH1)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT1)

        // 2. intersect rectangle overlap this region
        region.set(region1)
        assertTrue(region.op(region2, region4, Region.Op.INTERSECT))
        verifyPointsInsideRegion(INTERSECT_WITH2)
        verifyPointsOutsideRegion(INTERSECT_WITHOUT2)

        // 3. intersect rectangle out of this region
        region.set(region1)
        assertFalse(region.op(region2, region5, Region.Op.INTERSECT))
    }

    private fun verifyUnionOp5(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // UNION, Region with rectangle
        // union (inclusive-or) the two regions
        region = Region()
        // union null rectangle
        region.set(region1)
        assertTrue(region.op(region2, region1, Region.Op.UNION))
        assertTrue(region.contains(6, 6))

        // 1. union rectangle inside this region
        region.set(region1)
        assertTrue(region.op(region2, region3, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH1)
        verifyPointsOutsideRegion(UNION_WITHOUT1)

        // 2. union rectangle overlap this region
        region.set(region1)
        assertTrue(region.op(region2, region4, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH2)
        verifyPointsOutsideRegion(UNION_WITHOUT2)

        // 3. union rectangle out of this region
        region.set(region1)
        assertTrue(region.op(region2, region5, Region.Op.UNION))
        verifyPointsInsideRegion(UNION_WITH3)
        verifyPointsOutsideRegion(UNION_WITHOUT3)
    }

    private fun verifyXorOp5(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // XOR, Region with rectangle
        // exclusive-or the two regions
        region = Region()
        // xor null rectangle
        region.set(region1)
        assertTrue(region.op(region2, region1, Region.Op.XOR))

        // 1. xor rectangle inside this region
        region.set(region1)
        assertTrue(region.op(region2, region3, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH1)
        verifyPointsOutsideRegion(XOR_WITHOUT1)

        // 2. xor rectangle overlap this region
        region.set(region1)
        assertTrue(region.op(region2, region4, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH2)
        verifyPointsOutsideRegion(XOR_WITHOUT2)

        // 3. xor rectangle out of this region
        region.set(region1)
        assertTrue(region.op(region2, region5, Region.Op.XOR))
        verifyPointsInsideRegion(XOR_WITH3)
        verifyPointsOutsideRegion(XOR_WITHOUT3)
    }

    private fun verifyReverseDifferenceOp5(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // REVERSE_DIFFERENCE, Region with rectangle
        // reverse difference the first region from the op region
        region = Region()
        // reverse difference null rectangle
        region.set(region1)
        assertFalse(region.op(region2, region1, Region.Op.REVERSE_DIFFERENCE))

        // 1. reverse difference rectangle inside this region
        region.set(region1)
        assertFalse(region.op(region2, region3, Region.Op.REVERSE_DIFFERENCE))

        // 2. reverse difference rectangle overlap this region
        region.set(region1)
        assertTrue(region.op(region2, region4, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH2)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT2)

        // 3. reverse difference rectangle out of this region
        region.set(region1)
        assertTrue(region.op(region2, region5, Region.Op.REVERSE_DIFFERENCE))
        verifyPointsInsideRegion(REVERSE_DIFFERENCE_WITH3)
        verifyPointsOutsideRegion(REVERSE_DIFFERENCE_WITHOUT3)
    }

    private fun verifyReplaceOp5(
        region1: Region,
        region2: Region,
        region3: Region,
        region4: Region,
        region5: Region
    ) {
        // REPLACE, Region with rectangle
        // replace the dst region with the op region
        region = Region()
        // subtract null rectangle
        region.set(region1)
        assertFalse(region.op(region2, region1, Region.Op.REPLACE))
        // subtract rectangle inside this region
        region.set(region1)
        assertTrue(region.op(region2, region3, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region3.bounds, region.bounds)
        // subtract rectangle overlap this region
        region.set(region1)
        assertTrue(region.op(region2, region4, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region4.bounds, region.bounds)
        // subtract rectangle out of this region
        region.set(region1)
        assertTrue(region.op(region2, region5, Region.Op.REPLACE))
        assertNotSame(region2.bounds, region.bounds)
        assertEquals(region5.bounds, region.bounds)
    }

    val flickerRegionOperations =
        listOf(
            Region.Op.DIFFERENCE,
            Region.Op.INTERSECT,
            Region.Op.UNION,
            Region.Op.XOR,
            Region.Op.REVERSE_DIFFERENCE,
            Region.Op.REPLACE
        )
    val nativeRegionOperations =
        listOf(
            android.graphics.Region.Op.DIFFERENCE,
            android.graphics.Region.Op.INTERSECT,
            android.graphics.Region.Op.UNION,
            android.graphics.Region.Op.XOR,
            android.graphics.Region.Op.REVERSE_DIFFERENCE,
            android.graphics.Region.Op.REPLACE
        )

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForSingleOperation() {
        testFlickerRegionAndNativeRegionProvideSameOutput(1)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForSingleOperationFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameOutput(1, startEmpty = true)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForTwoOperations() {
        testFlickerRegionAndNativeRegionProvideSameOutput(2)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForTwoOperationsFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameOutput(2, startEmpty = true)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForThreeOperations() {
        testFlickerRegionAndNativeRegionProvideSameOutput(3)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForThreeOperationsFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameOutput(3, startEmpty = true)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForFourOperations() {
        testFlickerRegionAndNativeRegionProvideSameOutput(4)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForFourOperationsFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameOutput(4, startEmpty = true)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForFiveOperations() {
        testFlickerRegionAndNativeRegionProvideSameOutput(5)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputForFiveOperationsFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameOutput(5, startEmpty = true)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputFor100Operations() {
        testFlickerRegionAndNativeRegionProvideSameOutput(100, iterations = 10)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputFor100OperationsFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameOutput(100, startEmpty = true, iterations = 10)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameOutputFor1000Operations() {
        testFlickerRegionAndNativeRegionProvideSameOutput(100, iterations = 5)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameBoundsForSingleOperation() {
        testFlickerRegionAndNativeRegionProvideSameBounds(1)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameBoundsForSingleOperationFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameBounds(1, startEmpty = true)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameBoundsForFiveOperations() {
        testFlickerRegionAndNativeRegionProvideSameBounds(5)
    }

    @Test
    fun testFlickerRegionAndNativeRegionProvideSameBoundsForFiveOperationsFromEmpty() {
        testFlickerRegionAndNativeRegionProvideSameBounds(5, startEmpty = true)
    }

    private fun testFlickerRegionAgainstNativeRegion(
        totalOperations: Int,
        startEmpty: Boolean = false,
        iterations: Int = 100,
        assertion:
            (
                seed: Long,
                history: String,
                flickerRegion: Region,
                nativeRegion: android.graphics.Region
            ) -> Any,
        seed: Long = System.currentTimeMillis()
    ) {
        val random = Random(seed)

        for (i in 1..iterations) {
            val flickerRegion: Region
            val nativeRegion: android.graphics.Region
            if (startEmpty) {
                flickerRegion = Region.EMPTY
                nativeRegion = android.graphics.Region()
            } else {
                val left = random.nextInt(0, 5000)
                val top = random.nextInt(0, 5000)
                val right = random.nextInt(left, 5000)
                val bottom = random.nextInt(top, 5000)
                flickerRegion = Region.from(left, top, right, bottom)
                nativeRegion = android.graphics.Region(left, top, right, bottom)
            }
            var history = "$flickerRegion\n"
            for (j in 1..totalOperations) {
                val left = random.nextInt(0, 5000)
                val top = random.nextInt(0, 5000)
                val right = random.nextInt(left, 5000)
                val bottom = random.nextInt(top, 5000)
                val otherFlickerRegion = Region.from(left, top, right, bottom)
                val otherNativeRegion = android.graphics.Region(left, top, right, bottom)

                val operationIndex = random.nextInt(0, flickerRegionOperations.size)
                val flickerOperation = flickerRegionOperations[operationIndex]
                val nativeOperation = nativeRegionOperations[operationIndex]

                history += "\t$flickerOperation $otherFlickerRegion\n"

                flickerRegion.op(otherFlickerRegion, flickerOperation)
                nativeRegion.op(otherNativeRegion, nativeOperation)

                assertion(seed, history, flickerRegion, nativeRegion)
            }
        }
    }

    private fun testFlickerRegionAndNativeRegionProvideSameOutput(
        totalOperations: Int,
        startEmpty: Boolean = false,
        iterations: Int = 100
    ) {
        testFlickerRegionAgainstNativeRegion(
            totalOperations,
            startEmpty,
            iterations,
            {
                seed: Long,
                history: String,
                flickerRegion: Region,
                nativeRegion: android.graphics.Region ->
                {
                    assertEquals(
                        "Ran with seed $seed\n" +
                            "$history should equal \n" +
                            "$nativeRegion\n but was\n$flickerRegion\n\n",
                        nativeRegion.toString(),
                        flickerRegion.toString()
                    )
                }
            }
        )
    }

    private fun testFlickerRegionAndNativeRegionProvideSameBounds(
        totalOperations: Int,
        startEmpty: Boolean = false,
        iterations: Int = 100
    ) {
        testFlickerRegionAgainstNativeRegion(
            totalOperations,
            startEmpty,
            iterations,
            {
                seed: Long,
                history: String,
                flickerRegion: Region,
                nativeRegion: android.graphics.Region ->
                {
                    val bounds = flickerRegion.bounds
                    val nBounds = nativeRegion.bounds
                    assertEquals(
                        "Ran with seed $seed\n" +
                            "$history.bounds() should equal \n" +
                            "${nBounds}\n but was\n${bounds}\n\n",
                        "${nBounds.left},${nBounds.top},${nBounds.right},${nBounds.bottom}",
                        "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                    )
                }
            }
        )
    }

    companion object {
        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()

        // DIFFERENCE
        private val DIFFERENCE_WITH1 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(4, 4),
                intArrayOf(10, 10),
                intArrayOf(19, 19),
                intArrayOf(19, 0),
                intArrayOf(10, 4),
                intArrayOf(4, 10),
                intArrayOf(0, 19)
            )
        private val DIFFERENCE_WITHOUT1 =
            arrayOf(intArrayOf(5, 5), intArrayOf(9, 9), intArrayOf(9, 5), intArrayOf(5, 9))

        private val DIFFERENCE_WITH2 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(19, 0),
                intArrayOf(9, 9),
                intArrayOf(19, 9),
                intArrayOf(0, 19),
                intArrayOf(9, 19)
            )
        private val DIFFERENCE_WITHOUT2 =
            arrayOf(
                intArrayOf(10, 10),
                intArrayOf(19, 10),
                intArrayOf(10, 19),
                intArrayOf(19, 19),
                intArrayOf(29, 10),
                intArrayOf(29, 29),
                intArrayOf(10, 29)
            )

        private val DIFFERENCE_WITH3 =
            arrayOf(intArrayOf(0, 0), intArrayOf(19, 0), intArrayOf(0, 19), intArrayOf(19, 19))
        private val DIFFERENCE_WITHOUT3 =
            arrayOf(intArrayOf(40, 40), intArrayOf(40, 59), intArrayOf(59, 40), intArrayOf(59, 59))

        // INTERSECT
        private val INTERSECT_WITH1 =
            arrayOf(intArrayOf(5, 5), intArrayOf(9, 9), intArrayOf(9, 5), intArrayOf(5, 9))
        private val INTERSECT_WITHOUT1 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(4, 4),
                intArrayOf(10, 10),
                intArrayOf(19, 19),
                intArrayOf(19, 0),
                intArrayOf(10, 4),
                intArrayOf(4, 10),
                intArrayOf(0, 19)
            )

        private val INTERSECT_WITH2 =
            arrayOf(intArrayOf(10, 10), intArrayOf(19, 10), intArrayOf(10, 19), intArrayOf(19, 19))
        private val INTERSECT_WITHOUT2 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(19, 0),
                intArrayOf(9, 9),
                intArrayOf(19, 9),
                intArrayOf(0, 19),
                intArrayOf(9, 19),
                intArrayOf(29, 10),
                intArrayOf(29, 29),
                intArrayOf(10, 29)
            )

        // UNION
        private val UNION_WITH1 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(4, 4),
                intArrayOf(6, 6),
                intArrayOf(10, 10),
                intArrayOf(19, 19),
                intArrayOf(19, 0),
                intArrayOf(10, 4),
                intArrayOf(4, 10),
                intArrayOf(0, 19),
                intArrayOf(5, 5),
                intArrayOf(9, 9),
                intArrayOf(9, 5),
                intArrayOf(5, 9)
            )
        private val UNION_WITHOUT1 =
            arrayOf(intArrayOf(0, 20), intArrayOf(20, 20), intArrayOf(20, 0))

        private val UNION_WITH2 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(19, 0),
                intArrayOf(9, 9),
                intArrayOf(19, 9),
                intArrayOf(0, 19),
                intArrayOf(9, 19),
                intArrayOf(21, 21),
                intArrayOf(10, 10),
                intArrayOf(19, 10),
                intArrayOf(10, 19),
                intArrayOf(19, 19),
                intArrayOf(29, 10),
                intArrayOf(29, 29),
                intArrayOf(10, 29)
            )
        private val UNION_WITHOUT2 =
            arrayOf(
                intArrayOf(0, 29),
                intArrayOf(0, 20),
                intArrayOf(9, 29),
                intArrayOf(9, 20),
                intArrayOf(29, 0),
                intArrayOf(20, 0),
                intArrayOf(29, 9),
                intArrayOf(20, 9)
            )

        private val UNION_WITH3 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(19, 0),
                intArrayOf(0, 19),
                intArrayOf(19, 19),
                intArrayOf(40, 40),
                intArrayOf(41, 41),
                intArrayOf(40, 59),
                intArrayOf(59, 40),
                intArrayOf(59, 59)
            )
        private val UNION_WITHOUT3 = arrayOf(intArrayOf(20, 20), intArrayOf(39, 39))

        // XOR
        private val XOR_WITH1 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(4, 4),
                intArrayOf(10, 10),
                intArrayOf(19, 19),
                intArrayOf(19, 0),
                intArrayOf(10, 4),
                intArrayOf(4, 10),
                intArrayOf(0, 19)
            )
        private val XOR_WITHOUT1 =
            arrayOf(
                intArrayOf(5, 5),
                intArrayOf(6, 6),
                intArrayOf(9, 9),
                intArrayOf(9, 5),
                intArrayOf(5, 9)
            )

        private val XOR_WITH2 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(19, 0),
                intArrayOf(9, 9),
                intArrayOf(19, 9),
                intArrayOf(0, 19),
                intArrayOf(9, 19),
                intArrayOf(21, 21),
                intArrayOf(29, 10),
                intArrayOf(10, 29),
                intArrayOf(20, 10),
                intArrayOf(10, 20),
                intArrayOf(20, 20),
                intArrayOf(29, 29)
            )
        private val XOR_WITHOUT2 =
            arrayOf(
                intArrayOf(10, 10),
                intArrayOf(11, 11),
                intArrayOf(19, 10),
                intArrayOf(10, 19),
                intArrayOf(19, 19)
            )

        private val XOR_WITH3 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(2, 2),
                intArrayOf(19, 0),
                intArrayOf(0, 19),
                intArrayOf(19, 19),
                intArrayOf(40, 40),
                intArrayOf(41, 41),
                intArrayOf(40, 59),
                intArrayOf(59, 40),
                intArrayOf(59, 59)
            )
        private val XOR_WITHOUT3 = arrayOf(intArrayOf(20, 20), intArrayOf(39, 39))

        // REVERSE_DIFFERENCE
        private val REVERSE_DIFFERENCE_WITH2 =
            arrayOf(
                intArrayOf(29, 10),
                intArrayOf(10, 29),
                intArrayOf(20, 10),
                intArrayOf(10, 20),
                intArrayOf(20, 20),
                intArrayOf(29, 29),
                intArrayOf(21, 21)
            )
        private val REVERSE_DIFFERENCE_WITHOUT2 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(19, 0),
                intArrayOf(0, 19),
                intArrayOf(19, 19),
                intArrayOf(2, 2),
                intArrayOf(11, 11)
            )

        private val REVERSE_DIFFERENCE_WITH3 =
            arrayOf(
                intArrayOf(40, 40),
                intArrayOf(40, 59),
                intArrayOf(59, 40),
                intArrayOf(59, 59),
                intArrayOf(41, 41)
            )
        private val REVERSE_DIFFERENCE_WITHOUT3 =
            arrayOf(
                intArrayOf(0, 0),
                intArrayOf(19, 0),
                intArrayOf(0, 19),
                intArrayOf(19, 19),
                intArrayOf(20, 20),
                intArrayOf(39, 39),
                intArrayOf(2, 2)
            )
    }
}
