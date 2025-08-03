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

package com.android.systemui.car.userpicker;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.filters.SmallTest;

import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerItemSpacingDecorationTest extends UserPickerTestCase {
    private static final int TEST_VERTICAL_SPACING = 30;
    private static final int TEST_HORIZONTAL_SPACING = 60;
    private static final int TEST_COLS = 3;

    private UserPickerItemSpacingDecoration mSpacingDecoration;
    private ArrayList<View> mViews;
    @Mock
    private RecyclerView mMockRecyclerView;
    @Mock
    private View mMockView0;
    @Mock
    private View mMockView1;
    @Mock
    private View mMockView2;
    @Mock
    private View mMockView3;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMockRecyclerView.getLayoutManager()).thenReturn(mock(GridLayoutManager.class));
        mViews = new ArrayList<>(Arrays.asList(mMockView0, mMockView1, mMockView2, mMockView3));
        for (int i = 0; i < mViews.size(); i++) {
            View view = mViews.get(i);
            RecyclerView.LayoutParams layoutParams = mock(RecyclerView.LayoutParams.class);
            when(layoutParams.getViewLayoutPosition()).thenReturn(i);
            when(view.getLayoutParams()).thenReturn(layoutParams);
            when(mMockRecyclerView.getChildAdapterPosition(view)).thenReturn(i);
        }

        mSpacingDecoration = new UserPickerItemSpacingDecoration(TEST_VERTICAL_SPACING,
                TEST_HORIZONTAL_SPACING, TEST_COLS);
    }

    @Test
    public void testHasBottomSpacing() {
        for (int i = 0; i < mViews.size(); i++) {
            Rect rect = new Rect();
            mSpacingDecoration.getItemOffsets(rect, mViews.get(i), mMockRecyclerView,
                    new RecyclerView.State());
            assertThat(rect.bottom).isEqualTo(TEST_VERTICAL_SPACING);
        }
    }

    @Test
    public void testHasEqualHorizontalSpacing() {
        ArrayList<Integer> sizeSums = new ArrayList<>();
        for (View view : mViews) {
            Rect rect = new Rect();
            mSpacingDecoration.getItemOffsets(rect, view, mMockRecyclerView,
                    new RecyclerView.State());
            sizeSums.add((rect.left + rect.right));
        }
        assertThat(sizeSums.stream().distinct().count()).isEqualTo(1);
    }

    @Test
    public void testAllElementsHaveCorrectHorizontalSpacing() {
        for (int i = 1; i < mViews.size(); i++) {
            if (i % TEST_COLS == 0) {
                // skip first element of row since there is no item to the left of it
                continue;
            }
            Rect rect1 = new Rect();
            Rect rect2 = new Rect();
            mSpacingDecoration.getItemOffsets(rect1, mViews.get(i - 1), mMockRecyclerView,
                    new RecyclerView.State());
            mSpacingDecoration.getItemOffsets(rect2, mViews.get(i), mMockRecyclerView,
                    new RecyclerView.State());
            assertThat(rect1.right + rect2.left).isEqualTo(TEST_HORIZONTAL_SPACING);
        }

    }
}
