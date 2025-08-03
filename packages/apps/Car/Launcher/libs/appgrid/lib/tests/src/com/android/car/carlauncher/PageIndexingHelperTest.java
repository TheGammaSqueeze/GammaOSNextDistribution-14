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

package com.android.car.carlauncher;

import static com.android.car.carlauncher.AppGridConstants.PageOrientation;

import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.carlauncher.pagination.PageIndexingHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PageIndexingHelperTest {
    private static final int LTR = View.LAYOUT_DIRECTION_LTR;
    private static final int RTL = View.LAYOUT_DIRECTION_RTL;

    @Test
    public void test2x4x1_testAdapterGridOneToOneMapping_LTR() {
        PageIndexingHelper helper = createTestAppGridPagingUtils(
                /* numOfCols */ 4, /* numOfRows */ 2, LTR);
        int[] gridPositions = new int[] {
                0, 2, 4, 6,
                1, 3, 5, 7,
        };
        int[] adapterIndexes = new int[]{
                0, 1, 2, 3,
                4, 5, 6, 7,
        };
        runTwoWayMappingTest(gridPositions, adapterIndexes, helper);
    }

    @Test
    public void test2x4x1_testAdapterGridOneToOneMapping_RTL() {
        PageIndexingHelper helper = createTestAppGridPagingUtils(
                /* numOfCols */ 4, /* numOfRows */ 2, RTL);
        int[] gridPositions = new int[] {
                0, 2, 4, 6,
                1, 3, 5, 7,
        };
        int[] adapterIndexes = new int[]{
                3, 2, 1, 0,
                7, 6, 5, 4,
        };
        runTwoWayMappingTest(gridPositions, adapterIndexes, helper);
    }

    @Test
    public void test3x5x2_testAdapterGridOneToOneMapping_LTR() {
        PageIndexingHelper helper = createTestAppGridPagingUtils(
                /* numOfCols */ 5, /* numOfRows */ 3, LTR);
        int[] gridPositions = new int[] {
                0, 3, 6, 9, 12,
                1, 4, 7, 10, 13,
                2, 5, 8, 11, 14,
                // next page
                15, 18, 21, 24, 27,
                16, 19, 22, 25, 28,
                17, 20, 23, 26, 29,
        };
        int[] adapterIndexes = new int[]{
                0, 1, 2, 3, 4,
                5, 6, 7, 8, 9,
                10, 11, 12, 13, 14,
                // next page
                15, 16, 17, 18, 19,
                20, 21, 22, 23, 24,
                25, 26, 27, 28, 29,
        };
        runTwoWayMappingTest(gridPositions, adapterIndexes, helper);
    }

    @Test
    public void test3x5x2_testAdapterGridOneToOneMapping_RTL() {
        PageIndexingHelper helper = createTestAppGridPagingUtils(
                /* numOfCols */ 5, /* numOfRows */ 3, RTL);
        int[] gridPositions = new int[] {
                0, 3, 6, 9, 12,
                1, 4, 7, 10, 13,
                2, 5, 8, 11, 14,
                // next page
                15, 18, 21, 24, 27,
                16, 19, 22, 25, 28,
                17, 20, 23, 26, 29,
        };
        int[] adapterIndexes = new int[]{
                4, 3, 2, 1, 0,
                9, 8, 7, 6, 5,
                14, 13, 12, 11, 10,
                // next page
                19, 18, 17, 16, 15,
                24, 23, 22, 21, 20,
                29, 28, 27, 26, 25,
        };
        runTwoWayMappingTest(gridPositions, adapterIndexes, helper);
    }

    @Test
    public void test1x6x3_testAdapterGridOneToOneMapping_LTR() {
        PageIndexingHelper helper = createTestAppGridPagingUtils(
                /* numOfCols */ 6, /* numOfRows */ 1, LTR);
        int[] gridPositions = new int[] {
                0, 1, 2, 3, 4, 5,
                // next page
                6, 7, 8, 9, 10, 11,
                // next page
                12, 13, 14, 15, 16, 17,
        };
        int[] adapterIndexes = new int[]{
                0, 1, 2, 3, 4, 5,
                // next page
                6, 7, 8, 9, 10, 11,
                // next page
                12, 13, 14, 15, 16, 17,
        };
        runTwoWayMappingTest(gridPositions, adapterIndexes, helper);
    }

    @Test
    public void test1x6x3_testAdapterGridOneToOneMapping_RTL() {
        PageIndexingHelper helper = createTestAppGridPagingUtils(
                /* numOfCols */ 6, /* numOfRows */ 1, RTL);
        int[] gridPositions = new int[] {
                0, 1, 2, 3, 4, 5,
                // next page
                6, 7, 8, 9, 10, 11,
                // next page
                12, 13, 14, 15, 16, 17,
        };
        int[] adapterIndexes = new int[]{
                5, 4, 3, 2, 1, 0,
                // next page
                11, 10, 9, 8, 7, 6,
                // next page
                17, 16, 15, 14, 13, 12,
        };
        runTwoWayMappingTest(gridPositions, adapterIndexes, helper);
    }

    private void runTwoWayMappingTest(int[] gridPositions, int[] adapterIndexes,
            PageIndexingHelper testUtils) {
        // check forward mapping from grid position generated from
        // gridPosition = viewHolder.getAbsoluteAdapterPosition()
        for (int i = 0; i < gridPositions.length; i++) {
            assertEquals(testUtils.gridPositionToAdaptorIndex(gridPositions[i]), adapterIndexes[i]);
        }
        // check inverse mapping from adapter index
        for (int i = 0; i < adapterIndexes.length; i++) {
            assertEquals(testUtils.adaptorIndexToGridPosition(adapterIndexes[i]), gridPositions[i]);
        }
    }

    @Test
    public void test2x4x1_roundToFirstAndLastIndex() {
        PageIndexingHelper helperLtr = createTestAppGridPagingUtils(
                /* numOfCols */ 4, /* numOfRows */ 2, LTR);
        PageIndexingHelper helperRtl = createTestAppGridPagingUtils(
                /* numOfCols */ 4, /* numOfRows */ 2, RTL);
        int[] gridPositions = new int[] {
                0, 2, 4, 6,
                1, 3, 5, 7,
        };
        for (int i = 0; i < gridPositions.length; i++) {
            // round to left most index on page
            assertEquals(helperLtr.roundToFirstIndexOnPage(gridPositions[i]), 0);
            assertEquals(helperRtl.roundToFirstIndexOnPage(gridPositions[i]), 0);
            // round to right most index
            assertEquals(helperLtr.roundToLastIndexOnPage(gridPositions[i]), 7);
            assertEquals(helperRtl.roundToLastIndexOnPage(gridPositions[i]), 7);
        }
    }

    @Test
    public void test3x5x2_roundToFirstAndLastIndex() {
        PageIndexingHelper helperLtr = createTestAppGridPagingUtils(
                /* numOfCols */ 5, /* numOfRows */ 3, LTR);
        PageIndexingHelper helperRtl = createTestAppGridPagingUtils(
                /* numOfCols */ 5, /* numOfRows */ 3, RTL);
        int[] gridPositions = new int[] {
                0, 3, 6, 9, 12,
                1, 4, 7, 10, 13,
                2, 5, 8, 11, 14,
                // next page
                15, 18, 21, 24, 27,
                16, 19, 22, 25, 28,
                17, 20, 23, 26, 29,
        };
        for (int i = 0; i < 15; i++) {
            // round to left most index on page
            assertEquals(helperLtr.roundToFirstIndexOnPage(gridPositions[i]), 0);
            assertEquals(helperRtl.roundToFirstIndexOnPage(gridPositions[i]), 0);
            // round to right most index
            assertEquals(helperLtr.roundToLastIndexOnPage(gridPositions[i]), 14);
            assertEquals(helperRtl.roundToLastIndexOnPage(gridPositions[i]), 14);
        }
        for (int i = 15; i < gridPositions.length; i++) {
            // round to left most index on page
            assertEquals(helperLtr.roundToFirstIndexOnPage(gridPositions[i]), 15);
            assertEquals(helperRtl.roundToFirstIndexOnPage(gridPositions[i]), 15);
            // round to right most index
            assertEquals(helperLtr.roundToLastIndexOnPage(gridPositions[i]), 29);
            assertEquals(helperRtl.roundToLastIndexOnPage(gridPositions[i]), 29);
        }
    }

    @Test
    public void test1x6x3_roundToFirstAndLastIndex() {
        PageIndexingHelper helperLtr = createTestAppGridPagingUtils(
                /* numOfCols */ 6, /* numOfRows */ 1, LTR);
        PageIndexingHelper helperRtl = createTestAppGridPagingUtils(
                /* numOfCols */ 6, /* numOfRows */ 1, RTL);
        int[] gridPositions = new int[] {
                0, 1, 2, 3, 4, 5,
                // next page
                6, 7, 8, 9, 10, 11,
                // next page
                12, 13, 14, 15, 16, 17,
        };
        for (int i = 0; i < 6; i++) {
            // round to left most index on page
            assertEquals(helperLtr.roundToFirstIndexOnPage(gridPositions[i]), 0);
            assertEquals(helperRtl.roundToFirstIndexOnPage(gridPositions[i]), 0);
            // round to right most index
            assertEquals(helperLtr.roundToLastIndexOnPage(gridPositions[i]), 5);
            assertEquals(helperRtl.roundToLastIndexOnPage(gridPositions[i]), 5);
        }
        for (int i = 6; i < 11; i++) {
            // round to left most index on page
            assertEquals(helperLtr.roundToFirstIndexOnPage(gridPositions[i]), 6);
            assertEquals(helperRtl.roundToFirstIndexOnPage(gridPositions[i]), 6);
            // round to right most index
            assertEquals(helperLtr.roundToLastIndexOnPage(gridPositions[i]), 11);
            assertEquals(helperRtl.roundToLastIndexOnPage(gridPositions[i]), 11);
        }
        for (int i = 12; i < 17; i++) {
            // round to left most index on page
            assertEquals(helperLtr.roundToFirstIndexOnPage(gridPositions[i]), 12);
            assertEquals(helperRtl.roundToFirstIndexOnPage(gridPositions[i]), 12);
            // round to right most index
            assertEquals(helperLtr.roundToLastIndexOnPage(gridPositions[i]), 17);
            assertEquals(helperRtl.roundToLastIndexOnPage(gridPositions[i]), 17);
        }
    }

    private PageIndexingHelper createTestAppGridPagingUtils(int numOfCols, int numOfRows,
            int layoutDirection) {
        PageIndexingHelper utils = new PageIndexingHelper(numOfCols, numOfRows,
                PageOrientation.HORIZONTAL);
        utils.setLayoutDirection(layoutDirection);
        return utils;
    }
}
