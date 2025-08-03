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

package com.android.car.carlauncher.calmmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.car.cluster.navigation.NavigationState;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.icu.util.MeasureUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class NavigationStateDataTest extends AbstractExtendedMockitoTestCase {

    private static final String SEPARATOR = "\\u0020\\u0020\\u0020\\u2022\\u0020\\u0020\\u0020";
    private static final String TIME_TO_DEST = "1hr 30min";
    private static final double DISTANCE_TO_DEST = 100;
    private static final String DISTANCE_TO_DEST_STR = "100";
    private static final MeasureUnit DISTANCE_UNIT_IMPERIAL = MeasureUnit.MILE;
    private static final MeasureUnit DISTANCE_UNIT_METRIC = MeasureUnit.MILE;
    private static final String DISTANCE_UNIT_IMPERIAL_STR = "mi";
    private static final String DISTANCE_UNIT_METRIC_STR = "mi";
    private static final Locale LOCALE_US = Locale.US;
    private static final float TEST_FLOAT_DELTA = 0.05f;
    @Mock
    private NavigationStateData mNavigationStateDataImperial;
    @Mock
    private NavigationStateData mNavigationStateDataMetric;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mNavigationStateDataImperial.getTimeToDestination()).thenReturn(TIME_TO_DEST);
        when(mNavigationStateDataImperial.getDistanceToDestination()).thenReturn(DISTANCE_TO_DEST);
        when(mNavigationStateDataImperial.getDistanceUnit()).thenReturn(DISTANCE_UNIT_IMPERIAL);

        when(mNavigationStateDataMetric.getTimeToDestination()).thenReturn(TIME_TO_DEST);
        when(mNavigationStateDataMetric.getDistanceToDestination()).thenReturn(DISTANCE_TO_DEST);
        when(mNavigationStateDataMetric.getDistanceUnit()).thenReturn(DISTANCE_UNIT_METRIC);
    }

    @After
    public void tearDown() throws Exception {}

    @Test
    public void testBuilder_validArgs_objectCreated() {
        NavigationStateData navStateData =
                NavigationStateData.newBuilder()
                        .setTimeToDestination(TIME_TO_DEST)
                        .setDistanceToDestination(DISTANCE_TO_DEST_STR)
                        .setDistanceUnit(NavigationState.Distance.Unit.MILES)
                        .build();

        assertNotNull(navStateData);
        assertEquals(TIME_TO_DEST, navStateData.getTimeToDestination());
        assertEquals(DISTANCE_TO_DEST, navStateData.getDistanceToDestination(), TEST_FLOAT_DELTA);
        assertEquals(MeasureUnit.MILE, navStateData.getDistanceUnit());
    }

    @Test
    public void testBuildTripStatusString_nullNavState_returnsNull() {
        assertNull(NavigationStateData.buildTripStatusString(null, LOCALE_US,
                SEPARATOR));
    }

    @Test
    public void testBuildTripStatusString_nullTime_returnsNull() {
        when(mNavigationStateDataImperial.getTimeToDestination()).thenReturn(null);
        final String tripStatusActual =
                NavigationStateData.buildTripStatusString(mNavigationStateDataImperial, null,
                        SEPARATOR);
        assertNull(tripStatusActual);
    }

    @Test
    public void testBuildTripStatusString_nullDistanceUnit_returnsNull() {
        when(mNavigationStateDataImperial.getDistanceUnit()).thenReturn(null);
        final String tripStatusActual =
                NavigationStateData.buildTripStatusString(mNavigationStateDataImperial, LOCALE_US,
                        SEPARATOR);
        assertNull(tripStatusActual);
    }

    @Test
    public void testBuildTripStatusString_navStateDataImperial_matchesForLocaleUS() {
        final String tripStatusExpected =
                TIME_TO_DEST + SEPARATOR + DISTANCE_TO_DEST_STR + " " + DISTANCE_UNIT_IMPERIAL_STR;
        final String tripStatusActual =
                NavigationStateData.buildTripStatusString(mNavigationStateDataImperial, LOCALE_US,
                        SEPARATOR);
        assertEquals(tripStatusExpected, tripStatusActual);
    }

    @Test
    public void testBuildTripStatusString_navStateDataMetric_matchesForLocaleUS() {
        final String tripStatusExpected =
                TIME_TO_DEST + SEPARATOR + DISTANCE_TO_DEST_STR + " " + DISTANCE_UNIT_METRIC_STR;
        final String tripStatusActual =
                NavigationStateData.buildTripStatusString(mNavigationStateDataMetric, LOCALE_US,
                        SEPARATOR);
        assertEquals(tripStatusExpected, tripStatusActual);
    }
}
