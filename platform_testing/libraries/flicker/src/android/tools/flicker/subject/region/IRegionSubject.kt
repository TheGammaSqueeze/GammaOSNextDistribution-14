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

package android.tools.flicker.subject.region

import android.tools.datatypes.Rect
import android.tools.datatypes.Region
import android.tools.flicker.subject.exceptions.IncorrectRegionException

interface IRegionSubject {
    /**
     * Asserts that the top and bottom coordinates of [other] are smaller or equal to those of
     * region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws IncorrectRegionException
     */
    fun isHigherOrEqual(other: Rect): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller or equal to those of
     * region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws IncorrectRegionException
     */
    fun isHigherOrEqual(other: Region): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are greater or equal to those of
     * region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws IncorrectRegionException
     */
    fun isLowerOrEqual(other: Rect): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are greater or equal to those of
     * region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws AssertionError
     */
    fun isLowerOrEqual(other: Region): IRegionSubject

    /**
     * Asserts that the left and right coordinates of [other] are lower or equal to those of region.
     *
     * Also checks that the top and bottom positions, as well as area, don't change
     *
     * @throws AssertionError
     */
    fun isToTheRight(other: Region): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller than those of region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws AssertionError
     */
    fun isHigher(other: Rect): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller than those of region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws AssertionError
     */
    fun isHigher(other: Region): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are greater than those of region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws AssertionError
     */
    fun isLower(other: Rect): IRegionSubject

    /**
     * Asserts that the top and bottom coordinates of [other] are greater than those of region.
     *
     * Also checks that the left and right positions, as well as area, don't change
     *
     * @throws AssertionError
     */
    fun isLower(other: Region): IRegionSubject

    /**
     * Asserts that region covers at most [other], that is, its area doesn't cover any point outside
     * of [other].
     *
     * @param other Expected covered area
     * @throws AssertionError
     */
    fun coversAtMost(other: Region): IRegionSubject

    /**
     * Asserts that region covers at most [other], that is, its area doesn't cover any point outside
     * of [other].
     *
     * @param other Expected covered area
     * @throws AssertionError
     */
    fun coversAtMost(other: Rect): IRegionSubject

    /**
     * Asserts that region is not bigger than [other], even if the regions don't overlap.
     *
     * @param other Area to compare to
     * @throws AssertionError
     */
    fun notBiggerThan(other: Region): IRegionSubject

    /**
     * Asserts that region is positioned to the right and bottom from [other], but the regions can
     * overlap and region can be smaller than [other]
     *
     * @param other Area to compare to
     * @param threshold Offset threshold by which the position might be off
     * @throws AssertionError
     */
    fun isToTheRightBottom(other: Region, threshold: Int): IRegionSubject

    /**
     * Asserts that [other] contains the center of the region.
     *
     * @param other Expected area that the center should be in
     * @throws AssertionError
     */
    fun regionsCenterPointInside(other: Region): IRegionSubject

    /**
     * Asserts that region covers at least [other], that is, its area covers each point in the
     * region
     *
     * @param other Expected covered area
     * @throws AssertionError
     */
    fun coversAtLeast(other: Region): IRegionSubject

    /**
     * Asserts that region covers at least [other], that is, its area covers each point in the
     * region
     *
     * @param other Expected covered area
     * @throws AssertionError
     */
    fun coversAtLeast(other: Rect): IRegionSubject

    /**
     * Asserts that region covers at exactly [other]
     *
     * @param other Expected covered area
     * @throws AssertionError
     */
    fun coversExactly(other: Region): IRegionSubject

    /**
     * Asserts that region covers at exactly [other]
     *
     * @param other Expected covered area
     * @throws AssertionError
     */
    fun coversExactly(other: Rect): IRegionSubject

    /**
     * Asserts that region and [other] overlap
     *
     * @param other Other area
     * @throws AssertionError
     */
    fun overlaps(other: Region): IRegionSubject

    /**
     * Asserts that region and [other] overlap
     *
     * @param other Other area
     * @throws AssertionError
     */
    fun overlaps(other: Rect): IRegionSubject

    /**
     * Asserts that region and [other] don't overlap
     *
     * @param other Other area
     * @throws AssertionError
     */
    fun notOverlaps(other: Region): IRegionSubject

    /**
     * Asserts that region and [other] don't overlap
     *
     * @param other Other area
     * @throws AssertionError
     */
    fun notOverlaps(other: Rect): IRegionSubject

    /**
     * Asserts that region and [other] have same aspect ratio, margin of error up to 0.1.
     *
     * @param other Other region
     * @throws AssertionError
     */
    fun isSameAspectRatio(other: Region, threshold: Double): IRegionSubject

    /**
     * Asserts that region has the same top coordinates as [displayRect]
     *
     * @param displayRect Display area
     * @throws AssertionError
     */
    fun hasSameTopPosition(displayRect: Rect): IRegionSubject

    /**
     * Asserts that region has the same bottom coordinates as [displayRect]
     *
     * @param displayRect Display area
     * @throws AssertionError
     */
    fun hasSameBottomPosition(displayRect: Rect): IRegionSubject

    /**
     * Asserts that region has the same right coordinates as [displayRect]
     *
     * @param displayRect Display area
     * @throws AssertionError
     */
    fun hasSameRightPosition(displayRect: Rect): IRegionSubject

    /**
     * Asserts that region has the same left coordinates as [displayRect]
     *
     * @param displayRect Display area
     * @throws AssertionError
     */
    fun hasSameLeftPosition(displayRect: Rect): IRegionSubject
}
