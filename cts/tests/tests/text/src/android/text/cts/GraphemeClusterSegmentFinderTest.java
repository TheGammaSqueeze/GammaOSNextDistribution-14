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

package android.text.cts;

import static android.text.SegmentFinder.DONE;

import static com.google.common.truth.Truth.assertThat;

import android.text.GraphemeClusterSegmentFinder;
import android.text.SegmentFinder;
import android.text.TextPaint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GraphemeClusterSegmentFinderTest {
    private static final TextPaint PAINT = new TextPaint();

    @Test
    public void singleCharacterGraphemes() {
        String text = "abc";
        SegmentFinder segmentFinder = new GraphemeClusterSegmentFinder(text, PAINT);
        assertSegments(segmentFinder, new int[]{ 0, 1, 1, 2, 2, 3});
    }

    @Test
    public void surrogates() {
        //" \uD83D\uDE00" is a grinning face emoji.
        String text = "\uD83D\uDE00a\uD83D\uDE00b";
        SegmentFinder segmentFinder = new GraphemeClusterSegmentFinder(text, PAINT);
        assertSegments(segmentFinder, new int[]{ 0, 2, 2, 3, 3, 5, 5, 6});
    }

    @Test
    public void countryCodeFlag() {
        // "\uD83C\uDDFA\uD83C\uDDF8" is US flag
        String text = "\uD83C\uDDFA\uD83C\uDDF8ab";
        SegmentFinder segmentFinder = new GraphemeClusterSegmentFinder(text, PAINT);
        assertSegments(segmentFinder, new int[]{ 0, 4, 4, 5, 5, 6});
    }

    private void assertSegments(SegmentFinder segmentFinder, int[] segments) {
        int currentStart = segmentFinder.nextEndBoundary(0);
        currentStart = segmentFinder.previousStartBoundary(currentStart);
        int currentEnd = segmentFinder.nextEndBoundary(currentStart);

        int index = 0;
        while (index < segments.length - 1 && currentStart != DONE && currentEnd != DONE) {
            assertThat(currentStart).isEqualTo(segments[index++]);
            assertThat(currentEnd).isEqualTo(segments[index++]);

            currentStart = segmentFinder.nextStartBoundary(currentStart);
            currentEnd = segmentFinder.nextEndBoundary(currentEnd);
        }
        // No more unchecked segments in the SegmentFinder.
        assertThat(currentStart).isEqualTo(DONE);
        assertThat(currentEnd).isEqualTo(DONE);
        // Make sure all expected segments are checked.
        assertThat(index).isEqualTo(segments.length);
    }
}
