/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.text.SegmentFinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;


import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests StaticLayout vertical metrics behavior.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SegmentFinderTest {
    private static final int[] SEGMENTS = new int[] { 1, 2, 3, 4, 4, 5, 5, 7 };

    @Test
    public void testDefaultSegmentFinder_previousStartBoundary() {
        final SegmentFinder segmentFinder = new SegmentFinder.PrescribedSegmentFinder(SEGMENTS);

        assertThat(segmentFinder.previousStartBoundary(SegmentFinder.DONE))
                .isEqualTo(SegmentFinder.DONE);

        final int firstSegmentStart = SEGMENTS[0];
        assertThat(segmentFinder.previousStartBoundary(firstSegmentStart))
                .isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.previousStartBoundary(firstSegmentStart - 1))
                .isEqualTo(SegmentFinder.DONE);

        for (int index = 2; index < SEGMENTS.length; index += 2) {
            final int currentSegmentStart = SEGMENTS[index];
            final int previousSegmentStart = SEGMENTS[index - 2];
            assertThat(segmentFinder.previousStartBoundary(currentSegmentStart))
                    .isEqualTo(previousSegmentStart);
        }

        for (int index = 0; index < SEGMENTS.length; index += 2) {
            final int currentSegmentStart = SEGMENTS[index];
            final int currentSegmentEnd = SEGMENTS[index + 1];

            assertThat(segmentFinder.previousStartBoundary(currentSegmentEnd))
                    .isEqualTo(currentSegmentStart);
            assertThat(segmentFinder.previousStartBoundary(currentSegmentStart + 1))
                    .isEqualTo(currentSegmentStart);
        }

        final int lastSegmentStart = SEGMENTS[SEGMENTS.length - 2];
        final int lastSegmentEnd = SEGMENTS[SEGMENTS.length - 1];
        assertThat(segmentFinder.previousStartBoundary(lastSegmentEnd + 1))
                .isEqualTo(lastSegmentStart);
    }

    @Test
    public void testDefaultSegmentFinder_previousEndBoundary() {
        final SegmentFinder segmentFinder = new SegmentFinder.PrescribedSegmentFinder(SEGMENTS);

        assertThat(segmentFinder.previousEndBoundary(SegmentFinder.DONE))
                .isEqualTo(SegmentFinder.DONE);

        final int firstSegmentStart = SEGMENTS[0];
        final int firstSegmentEnd = SEGMENTS[1];
        assertThat(segmentFinder.previousEndBoundary(firstSegmentStart))
                .isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.previousEndBoundary(firstSegmentEnd))
                .isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.previousEndBoundary(firstSegmentEnd - 1))
                .isEqualTo(SegmentFinder.DONE);

        for (int index = 2; index < SEGMENTS.length; index += 2) {
            final int currentSegmentEnd = SEGMENTS[index + 1];
            final int previousSegmentEnd = SEGMENTS[index - 1];

            assertThat(segmentFinder.previousEndBoundary(currentSegmentEnd))
                    .isEqualTo(previousSegmentEnd);
        }

        for (int index = 0; index < SEGMENTS.length; index += 2) {
            final int currentSegmentEnd = SEGMENTS[index + 1];
            assertThat(segmentFinder.previousEndBoundary(currentSegmentEnd + 1))
                    .isEqualTo(currentSegmentEnd);
        }
    }

    @Test
    public void testDefaultSegmentFinder_nextEndBoundary() {
        final SegmentFinder segmentFinder = new SegmentFinder.PrescribedSegmentFinder(SEGMENTS);

        assertThat(segmentFinder.nextEndBoundary(SegmentFinder.DONE))
                .isEqualTo(SegmentFinder.DONE);

        final int firstSegmentStart = SEGMENTS[0];
        final int firstSegmentEnd = SEGMENTS[1];
        assertThat(segmentFinder.nextEndBoundary(firstSegmentStart - 1))
                .isEqualTo(firstSegmentEnd);

        for (int index = 0; index < SEGMENTS.length - 3; index += 2) {
            final int currentSegmentEnd = SEGMENTS[index + 1];
            final int nextSegmentEnd = SEGMENTS[index + 3];

            assertThat(segmentFinder.nextEndBoundary(currentSegmentEnd)).isEqualTo(nextSegmentEnd);
        }

        for (int index = 0; index < SEGMENTS.length; index += 2) {
            final int currentSegmentStart = SEGMENTS[index];
            final int currentSegmentEnd = SEGMENTS[index + 1];
            assertThat(segmentFinder.nextEndBoundary(currentSegmentStart))
                    .isEqualTo(currentSegmentEnd);
            assertThat(segmentFinder.nextEndBoundary(currentSegmentEnd - 1))
                    .isEqualTo(currentSegmentEnd);
        }


        final int lastSegmentEnd = SEGMENTS[SEGMENTS.length - 1];
        assertThat(segmentFinder.nextEndBoundary(lastSegmentEnd)).isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.nextEndBoundary(lastSegmentEnd + 1)).isEqualTo(SegmentFinder.DONE);
    }

    @Test
    public void testDefaultSegmentFinder_nextStartBoundary() {
        final SegmentFinder segmentFinder = new SegmentFinder.PrescribedSegmentFinder(SEGMENTS);

        assertThat(segmentFinder.nextStartBoundary(SegmentFinder.DONE))
                .isEqualTo(SegmentFinder.DONE);

        for (int index = 0; index < SEGMENTS.length - 2; index += 2) {
            final int currentSegmentStart = SEGMENTS[index];
            final int nextSegmentStart = SEGMENTS[index + 2];

            assertThat(segmentFinder.nextStartBoundary(currentSegmentStart))
                    .isEqualTo(nextSegmentStart);
        }

        for (int index = 0; index < SEGMENTS.length; index += 2) {
            final int currentSegmentStart = SEGMENTS[index];
            assertThat(segmentFinder.nextStartBoundary(currentSegmentStart - 1))
                    .isEqualTo(currentSegmentStart);
        }

        final int lastSegmentStart = SEGMENTS[SEGMENTS.length - 2];
        final int lastSegmentEnd = SEGMENTS[SEGMENTS.length - 1];
        assertThat(segmentFinder.nextStartBoundary(lastSegmentStart)).isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.nextStartBoundary(lastSegmentEnd)).isEqualTo(SegmentFinder.DONE);
        assertThat(segmentFinder.nextStartBoundary(lastSegmentStart + 1))
                .isEqualTo(SegmentFinder.DONE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultSegmentFinder_segmentsArrayMustBeEven() {
        new SegmentFinder.PrescribedSegmentFinder(new int[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultSegmentFinder_segmentsArrayMustBeSorted() {
        // The segment 3, 4 can't be placed after segment 1, 2.
        new SegmentFinder.PrescribedSegmentFinder(new int[] { 3, 4, 1, 2 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultSegmentFinder_segmentsArrayNoEmptySegment() {
        // The segment 3, 3 is empty, which is not allowed.
        new SegmentFinder.PrescribedSegmentFinder(new int[] { 1, 2, 3, 3 });
    }
}
