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

package com.android.server.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.location.Location;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for {@link AfcLocationUtil}.
 */
@SmallTest

public class AfcLocationUtilTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock
    private AfcLocation mAfcLocation;
    @Mock
    private Location mComparingLocation;
    private AfcLocationUtil mAfcLocationUtil;

    @Before
    public void setUp() {
        mAfcLocationUtil = new AfcLocationUtil();
    }

    /**
     * Verify that the checkLocation result for an AfcLocation object matches the checkLocation
     * result in AfcLocationUtil.
     */
    @Test
    public void checkLocationForAfcObject() {
        when(mComparingLocation.getLongitude()).thenReturn(9.9);
        when(mComparingLocation.getLatitude()).thenReturn(9.9);

        when(mAfcLocation.checkLocation(mComparingLocation)).thenReturn(
                AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);
        assertThat(mAfcLocationUtil.checkLocation(mAfcLocation, mComparingLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);

        when(mAfcLocation.checkLocation(mComparingLocation)).thenReturn(
                AfcLocationUtil.InBoundsCheckResult.ON_BORDER);
        assertThat(mAfcLocationUtil.checkLocation(mAfcLocation, mComparingLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.ON_BORDER);

        when(mAfcLocation.checkLocation(mComparingLocation)).thenReturn(
                AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);
        assertThat(mAfcLocationUtil.checkLocation(mAfcLocation, mComparingLocation)).isEqualTo(
                AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);
    }
}
