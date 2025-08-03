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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;

import com.android.car.carlauncher.pagination.PageIndexingHelper;
import com.android.car.carlauncher.recyclerview.AppGridAdapter;
import com.android.car.carlauncher.recyclerview.AppItemViewHolder;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AppGridAdapterTest {

    @Mock public Context mMockContext;
    @Mock public LayoutInflater mMockLayoutInflater;
    @Mock public LauncherViewModel mMockLauncherModel;
    @Mock public AppItemViewHolder.AppItemDragCallback mMockDragCallback;
    @Mock public AppGridPageSnapper.AppGridPageSnapCallback mMockSnapCallback;
    @Mock public Rect mMockPageBound;
    public AppGridAdapter mTestAppGridAdapter;

    @Before
    public void setUp() throws Exception {
        mMockLauncherModel = mock(LauncherViewModel.class);
        mMockDragCallback = mock(AppItemViewHolder.AppItemDragCallback.class);
        mMockSnapCallback = mock(AppGridPageSnapper.AppGridPageSnapCallback.class);
    }

    @Test
    public void testPageRounding_getItemCount_getPageCount() {
        int numOfCols = 5;
        int numOfRows = 3;
        mTestAppGridAdapter = new AppGridAdapter(mMockContext, numOfCols, numOfRows,
                PageOrientation.HORIZONTAL,
                mMockLayoutInflater, mMockLauncherModel, mMockDragCallback, mMockSnapCallback);
        mTestAppGridAdapter.updateViewHolderDimensions(mMockPageBound,
                /* appItemWidth */ 260, /* appItemHeight */ 200);
        mTestAppGridAdapter = spy(mTestAppGridAdapter);

        // when there is 0 items we still need at least one page.
        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(0);
        assertEquals(0, mTestAppGridAdapter.getItemCount());
        assertEquals(1, mTestAppGridAdapter.getPageCount());

        // each page should have 15 items
        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(10);
        assertEquals(15, mTestAppGridAdapter.getItemCount());
        assertEquals(1, mTestAppGridAdapter.getPageCount());

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(16);
        assertEquals(30, mTestAppGridAdapter.getItemCount());
        assertEquals(2, mTestAppGridAdapter.getPageCount());

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(31);
        assertEquals(45, mTestAppGridAdapter.getItemCount());
        assertEquals(3, mTestAppGridAdapter.getPageCount());

        numOfCols = 4;
        numOfRows = 6;

        mTestAppGridAdapter = new AppGridAdapter(mMockContext, numOfCols, numOfRows,
                PageOrientation.HORIZONTAL,
                mMockLayoutInflater, mMockLauncherModel, mMockDragCallback, mMockSnapCallback);
        mTestAppGridAdapter.updateViewHolderDimensions(mMockPageBound,
                /* appItemWidth */ 260, /* appItemHeight */ 200);
        mTestAppGridAdapter = spy(mTestAppGridAdapter);

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(0);
        assertEquals(0, mTestAppGridAdapter.getItemCount());
        assertEquals(1, mTestAppGridAdapter.getPageCount());

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(numOfCols * numOfRows);
        assertEquals(numOfCols * numOfRows, mTestAppGridAdapter.getItemCount());
        assertEquals(1, mTestAppGridAdapter.getPageCount());

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(numOfCols * numOfRows + 1);
        assertEquals(2 * numOfCols * numOfRows, mTestAppGridAdapter.getItemCount());
        assertEquals(2, mTestAppGridAdapter.getPageCount());

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(2 * numOfCols * numOfRows - 1);
        assertEquals(2 * numOfCols * numOfRows, mTestAppGridAdapter.getItemCount());
        assertEquals(2, mTestAppGridAdapter.getPageCount());
    }

    @Test
    public void updatePageScrollDestination_testLeftScrollDestinations() {
        // an adapter with 45 items
        int numOfCols = 5;
        int numOfRows = 3;
        mTestAppGridAdapter = new AppGridAdapter(mMockContext, numOfCols, numOfRows,
                PageOrientation.HORIZONTAL,
                mMockLayoutInflater, mMockLauncherModel, mMockDragCallback, mMockSnapCallback);
        mTestAppGridAdapter.updateViewHolderDimensions(mMockPageBound,
                /* appItemWidth */ 260, /* appItemHeight */ 200);
        mTestAppGridAdapter = spy(mTestAppGridAdapter);
        when(mTestAppGridAdapter.getItemCount()).thenReturn(45);

        // page 1 to page 0
        int startPoint = 29;
        int endPoint = 0;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ false);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());

        // page 0 to page 0
        startPoint = 3;
        endPoint = 0;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ false);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());

        // page 2 to page 1
        startPoint = 31;
        endPoint = 15;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ false);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());
    }

    @Test
    public void updatePageScrollDestination_testRightScrollDestinations() {
        // an adapter with 45 items
        int numOfRows = 5;
        int numOfCols = 3;
        mTestAppGridAdapter = new AppGridAdapter(mMockContext, numOfCols, numOfRows,
                /* pageOrientation */ PageOrientation.HORIZONTAL,
                mMockLayoutInflater, mMockLauncherModel, mMockDragCallback, mMockSnapCallback);
        mTestAppGridAdapter.updateViewHolderDimensions(mMockPageBound,
                /* appItemWidth */ 260, /* appItemHeight */ 200);
        mTestAppGridAdapter = spy(mTestAppGridAdapter);
        when(mTestAppGridAdapter.getItemCount()).thenReturn(45);

        // page 1 to page 2
        int startPoint = 29;
        int endPoint = 44;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ true);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());

        // page 0 to page 1
        startPoint = 0;
        endPoint = 29;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ true);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());

        // page 2 to page 2 (there is exactly 3 pages)
        startPoint = 30;
        endPoint = startPoint;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ true);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());

        // testing for when an item has been added, expanding to page 3 with space for 60 entries
        when(mTestAppGridAdapter.getItemCount()).thenReturn(46);
        startPoint = 30;
        endPoint = 59;
        mTestAppGridAdapter.setDragStartPoint(startPoint);
        mTestAppGridAdapter.updatePageScrollDestination(/* scrollToRightPage */ true);
        assertEquals(endPoint, mTestAppGridAdapter.getPageScrollDestination());
    }

    @Test
    public void getNextRotaryFocus_testLeftRightRotations() {
        // an adapter with 40 items, 3 page, and 5 padded empty items
        int numOfCols = 5;
        int numOfRows = 3;
        mTestAppGridAdapter = new AppGridAdapter(mMockContext, numOfCols, numOfRows,
                PageOrientation.HORIZONTAL,
                mMockLayoutInflater, mMockLauncherModel, mMockDragCallback, mMockSnapCallback);
        mTestAppGridAdapter.updateViewHolderDimensions(mMockPageBound,
                /* appItemWidth */ 260, /* appItemHeight */ 200);
        mTestAppGridAdapter = spy(mTestAppGridAdapter);
        PageIndexingHelper pagingUtils = new PageIndexingHelper(numOfCols, numOfRows,
                PageOrientation.HORIZONTAL);

        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(40);
        assertEquals(3, mTestAppGridAdapter.getPageCount());

        // scroll right to 14
        int source = 13;
        int gridSource = pagingUtils.adaptorIndexToGridPosition(source);
        int gridTarget = pagingUtils.adaptorIndexToGridPosition(source + 1);
        assertEquals(gridTarget, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_FORWARD));
        source += 1;

        // scroll right to 15
        gridSource = pagingUtils.adaptorIndexToGridPosition(source);
        gridTarget = pagingUtils.adaptorIndexToGridPosition(source + 1);
        assertEquals(gridTarget, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_FORWARD));
        source += 1;

        // scroll right to next page
        gridSource = pagingUtils.adaptorIndexToGridPosition(source);
        gridTarget = pagingUtils.adaptorIndexToGridPosition(source + 1);
        assertEquals(gridTarget, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_FORWARD));
        source += 1;

        // scroll back to previous page
        gridSource = pagingUtils.adaptorIndexToGridPosition(source);
        gridTarget = pagingUtils.adaptorIndexToGridPosition(source - 1);
        assertEquals(gridTarget, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_BACKWARD));
        source -= 1;

        // scroll back
        gridSource = pagingUtils.adaptorIndexToGridPosition(source);
        gridTarget = pagingUtils.adaptorIndexToGridPosition(source - 1);
        assertEquals(gridTarget, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_BACKWARD));
    }

    @Test
    public void getNextRotaryFocus_testInvalidRotation() {
        // an adapter with 44 items, 3 page, and 16 padded empty items
        int numOfCols = 4;
        int numOfRows = 5;
        mTestAppGridAdapter = new AppGridAdapter(mMockContext, numOfCols, numOfRows,
                PageOrientation.HORIZONTAL,
                mMockLayoutInflater, mMockLauncherModel, mMockDragCallback, mMockSnapCallback);
        mTestAppGridAdapter.updateViewHolderDimensions(mMockPageBound,
                /* appItemWidth */ 260, /* appItemHeight */ 200);
        mTestAppGridAdapter = spy(mTestAppGridAdapter);
        PageIndexingHelper pagingUtils = new PageIndexingHelper(numOfCols, numOfRows,
                PageOrientation.HORIZONTAL);

        int numItems = 44;
        when(mTestAppGridAdapter.getLauncherItemsCount()).thenReturn(numItems);
        assertEquals(3, mTestAppGridAdapter.getPageCount());

        // scroll right should not focus on a different position
        int source = numItems - 1;
        int gridSource = pagingUtils.adaptorIndexToGridPosition(source);
        assertEquals(gridSource, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_FORWARD));

        // scroll left should not focus on a different position
        gridSource = pagingUtils.adaptorIndexToGridPosition(0);
        assertEquals(gridSource, mTestAppGridAdapter.getNextRotaryFocus(
                gridSource, View.FOCUS_BACKWARD));
    }
}
