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
import android.tools.flicker.subject.FlickerTraceSubject
import android.tools.io.Reader
import android.tools.traces.region.RegionTrace

/**
 * Subject for [RegionTrace] objects, used to make assertions over behaviors that occur on a
 * sequence of regions.
 */
class RegionTraceSubject(val trace: RegionTrace, override val reader: Reader? = null) :
    FlickerTraceSubject<RegionSubject>(), IRegionSubject {

    override val subjects by lazy { trace.entries.map { RegionSubject(it, it.timestamp, reader) } }

    private val componentsAsString =
        if (trace.components == null) {
            "<any>"
        } else {
            "[${trace.components}]"
        }

    /** {@inheritDoc} */
    override fun then(): RegionTraceSubject {
        return super.then() as RegionTraceSubject
    }

    /** {@inheritDoc} */
    override fun isHigherOrEqual(other: Rect): RegionTraceSubject =
        isHigherOrEqual(Region.from(other))

    /** {@inheritDoc} */
    override fun isHigherOrEqual(other: Region): RegionTraceSubject = apply {
        addAssertion("isHigherOrEqual($other, $componentsAsString)") { it.isHigherOrEqual(other) }
    }

    /** {@inheritDoc} */
    override fun isLowerOrEqual(other: Rect): RegionTraceSubject =
        isLowerOrEqual(Region.from(other))

    /** {@inheritDoc} */
    override fun isLowerOrEqual(other: Region): RegionTraceSubject = apply {
        addAssertion("isLowerOrEqual($other, $componentsAsString)") { it.isLowerOrEqual(other) }
    }

    /** {@inheritDoc} */
    override fun isToTheRight(other: Region): RegionTraceSubject = apply {
        addAssertion("isToTheRight($other, $componentsAsString)") { it.isToTheRight(other) }
    }

    /** {@inheritDoc} */
    override fun isHigher(other: Rect): RegionTraceSubject = isHigher(Region.from(other))

    /** {@inheritDoc} */
    override fun isHigher(other: Region): RegionTraceSubject = apply {
        addAssertion("isHigher($other, $componentsAsString)") { it.isHigher(other) }
    }

    /** {@inheritDoc} */
    override fun isLower(other: Rect): RegionTraceSubject = isLower(Region.from(other))

    /** {@inheritDoc} */
    override fun isLower(other: Region): RegionTraceSubject = apply {
        addAssertion("isLower($other, $componentsAsString)") { it.isLower(other) }
    }

    /** {@inheritDoc} */
    override fun coversAtMost(other: Region): RegionTraceSubject = apply {
        addAssertion("coversAtMost($other, $componentsAsString") { it.coversAtMost(other) }
    }

    /** {@inheritDoc} */
    override fun coversAtMost(other: Rect): RegionTraceSubject =
        this.coversAtMost(Region.from(other))

    /** {@inheritDoc} */
    override fun notBiggerThan(other: Region): RegionTraceSubject = apply {
        addAssertion("notBiggerThan($other, $componentsAsString") { it.notBiggerThan(other) }
    }

    /** {@inheritDoc} */
    override fun isToTheRightBottom(other: Region, threshold: Int): RegionTraceSubject = apply {
        addAssertion("isToTheRightBottom($other, $componentsAsString") {
            it.isToTheRightBottom(other, threshold)
        }
    }

    /** {@inheritDoc} */
    override fun regionsCenterPointInside(other: Region): RegionTraceSubject = apply {
        addAssertion("regionsCenterPointInside($other, $componentsAsString") {
            it.regionsCenterPointInside(other)
        }
    }

    /** {@inheritDoc} */
    override fun coversAtLeast(other: Region): RegionTraceSubject = apply {
        addAssertion("coversAtLeast($other, $componentsAsString)") { it.coversAtLeast(other) }
    }

    /** {@inheritDoc} */
    override fun coversAtLeast(other: Rect): RegionTraceSubject =
        this.coversAtLeast(Region.from(other))

    /** {@inheritDoc} */
    override fun coversExactly(other: Region): RegionTraceSubject = apply {
        addAssertion("coversExactly($other, $componentsAsString)") { it.coversExactly(other) }
    }

    /** {@inheritDoc} */
    override fun coversExactly(other: Rect): RegionTraceSubject = apply {
        addAssertion("coversExactly($other, $componentsAsString") { it.coversExactly(other) }
    }

    /** {@inheritDoc} */
    override fun overlaps(other: Region): RegionTraceSubject = apply {
        addAssertion("overlaps($other, $componentsAsString") { it.overlaps(other) }
    }

    /** {@inheritDoc} */
    override fun overlaps(other: Rect): RegionTraceSubject = overlaps(Region.from(other))

    /** {@inheritDoc} */
    override fun notOverlaps(other: Region): RegionTraceSubject = apply {
        addAssertion("notOverlaps($other, $componentsAsString") { it.notOverlaps(other) }
    }

    /** {@inheritDoc} */
    override fun notOverlaps(other: Rect): RegionTraceSubject = notOverlaps(Region.from(other))

    fun isSameAspectRatio(other: Region): RegionTraceSubject =
        isSameAspectRatio(other, threshold = 0.1)

    /** {@inheritDoc} */
    override fun isSameAspectRatio(other: Region, threshold: Double): RegionTraceSubject = apply {
        addAssertion("isSameAspectRatio($other, $componentsAsString") {
            it.isSameAspectRatio(other, threshold)
        }
    }

    override fun hasSameLeftPosition(displayRect: Rect): RegionTraceSubject = apply {
        addAssertion("hasSameLeftPosition($displayRect") { it.hasSameLeftPosition(displayRect) }
    }

    override fun hasSameBottomPosition(displayRect: Rect): RegionTraceSubject = apply {
        addAssertion("hasSameBottomPosition($displayRect") { it.hasSameBottomPosition(displayRect) }
    }

    override fun hasSameRightPosition(displayRect: Rect): RegionTraceSubject = apply {
        addAssertion("hasSameRightPosition($displayRect") { it.hasSameRightPosition(displayRect) }
    }

    override fun hasSameTopPosition(displayRect: Rect): RegionTraceSubject = apply {
        addAssertion("hasSameTopPosition($displayRect") { it.hasSameTopPosition(displayRect) }
    }
}
