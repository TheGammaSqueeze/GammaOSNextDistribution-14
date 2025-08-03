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

package com.android.car.carlauncher.recents;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RecentsUtilsTest {
    @Mock
    private RecyclerView mRecyclerView;
    @Mock
    private LinearLayoutManager mLinearLayoutManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mRecyclerView.getLayoutManager()).thenReturn(mLinearLayoutManager);
    }

    @Test
    public void areItemsRightToLeft_returnsFalse_RTL_reversedLayout() {
        when(mLinearLayoutManager.getReverseLayout()).thenReturn(true);
        when(mRecyclerView.isLayoutRtl()).thenReturn(true);

        assertFalse(RecentsUtils.areItemsRightToLeft(mRecyclerView));
    }

    @Test
    public void areItemsRightToLeft_returnsTrue_RTL_noReversedLayout() {
        when(mLinearLayoutManager.getReverseLayout()).thenReturn(false);
        when(mRecyclerView.isLayoutRtl()).thenReturn(true);

        assertTrue(RecentsUtils.areItemsRightToLeft(mRecyclerView));
    }

    @Test
    public void areItemsRightToLeft_returnsTrue_LTR_reversedLayout() {
        when(mLinearLayoutManager.getReverseLayout()).thenReturn(true);
        when(mRecyclerView.isLayoutRtl()).thenReturn(false);

        assertTrue(RecentsUtils.areItemsRightToLeft(mRecyclerView));
    }

    @Test
    public void areItemsRightToLeft_returnsFalse_LTR_noReversedLayout() {
        when(mLinearLayoutManager.getReverseLayout()).thenReturn(false);
        when(mRecyclerView.isLayoutRtl()).thenReturn(false);

        assertFalse(RecentsUtils.areItemsRightToLeft(mRecyclerView));
    }
}
