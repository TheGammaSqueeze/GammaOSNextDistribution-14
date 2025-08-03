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

package com.android.cellbroadcastservice.tests;

import static com.android.cellbroadcastservice.CellBroadcastServiceMetrics.FeatureMetrics.ADDITIONAL_CBR_PACKAGES;
import static com.android.cellbroadcastservice.CellBroadcastServiceMetrics.FeatureMetrics.AREA_INFO_PACKAGES;
import static com.android.cellbroadcastservice.CellBroadcastServiceMetrics.FeatureMetrics.RESET_AREA_INFO;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.cellbroadcastservice.CellBroadcastServiceMetrics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CellBroadcastServiceMetricsTest extends CellBroadcastServiceTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();

        doReturn(mSharedPreference).when(mMockedContext)
                .getSharedPreferences(anyString(), anyInt());
        doReturn(false).when(mSharedPreference).getBoolean(anyString(), anyBoolean());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetInstance() {
        CellBroadcastServiceMetrics test1 = CellBroadcastServiceMetrics.getInstance();
        CellBroadcastServiceMetrics test2 = CellBroadcastServiceMetrics.getInstance();

        assertSame(test1, test2);
    }

    @Test
    public void testGetFeatureMetrics() {
        CellBroadcastServiceMetrics.FeatureMetrics test1 =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);
        CellBroadcastServiceMetrics.FeatureMetrics test2 =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);

        assertSame(test1, test2);

        assertFalse(test1.isOverrideCbrPkgs());
        assertFalse(test1.isOverrideAreaInfoPkgs());
        assertFalse(test1.isResetAreaInfo());

        doReturn(true).when(mSharedPreference)
                .getBoolean(eq(ADDITIONAL_CBR_PACKAGES), anyBoolean());
        doReturn(false).when(mSharedPreference)
                .getBoolean(eq(AREA_INFO_PACKAGES), anyBoolean());
        doReturn(true).when(mSharedPreference)
                .getBoolean(eq(RESET_AREA_INFO), anyBoolean());

        CellBroadcastServiceMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastServiceMetrics.getInstance().setFeatureMetricsSharedPreferences(null);

        test1 = CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);

        assertTrue(test1.isOverrideCbrPkgs());
        assertFalse(test1.isOverrideAreaInfoPkgs());
        assertTrue(test1.isResetAreaInfo());

        CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext)
                .onChangedAdditionalCbrPackage(false);
        CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext)
                .onChangedAreaInfoPackage(Arrays.asList(new String[]{"com.google.android"}));
        CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext)
                .onChangedResetAreaInfo(false);

        test1 = CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);

        assertFalse(test1.isOverrideCbrPkgs());
        assertTrue(test1.isOverrideAreaInfoPkgs());
        assertFalse(test1.isResetAreaInfo());
    }

    @Test
    public void testEquals() {
        CellBroadcastServiceMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastServiceMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);
        CellBroadcastServiceMetrics.FeatureMetrics testSharedPreferenceFeatureMetrics =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetricsSharedPreferences();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));
        assertNotSame(testFeatureMetrics, testSharedPreferenceFeatureMetrics);

        testFeatureMetrics.onChangedAdditionalCbrPackage(false);
        testFeatureMetrics.onChangedAreaInfoPackage(Arrays.asList(new String[]{"test.com"}));
        testFeatureMetrics.onChangedResetAreaInfo(false);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testSharedPreferenceFeatureMetrics
                .onChangedAdditionalCbrPackage(false);
        testSharedPreferenceFeatureMetrics
                .onChangedAreaInfoPackage(Arrays.asList(new String[]{"test.com"}));
        testSharedPreferenceFeatureMetrics
                .onChangedResetAreaInfo(false);

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        CellBroadcastServiceMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastServiceMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);
        CellBroadcastServiceMetrics.FeatureMetrics testSharedPreferenceFeatureMetrics =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetricsSharedPreferences();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testFeatureMetrics.onChangedAdditionalCbrPackage(false);
        testFeatureMetrics.onChangedAreaInfoPackage(Arrays.asList(new String[]{"test.com"}));
        testFeatureMetrics.onChangedResetAreaInfo(false);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testSharedPreferenceFeatureMetrics =
                (CellBroadcastServiceMetrics.FeatureMetrics) testFeatureMetrics.clone();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));
        assertNotSame(testFeatureMetrics, testSharedPreferenceFeatureMetrics);
    }

    @Test
    public void testLogFeatureChangedAsNeeded() throws CloneNotSupportedException {
        CellBroadcastServiceMetrics.getInstance().setFeatureMetrics(null);
        CellBroadcastServiceMetrics.FeatureMetrics testFeatureMetrics =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mMockedContext);
        CellBroadcastServiceMetrics.FeatureMetrics testSharedPreferenceFeatureMetrics =
                CellBroadcastServiceMetrics.getInstance().getFeatureMetricsSharedPreferences();

        assertTrue(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        testFeatureMetrics.onChangedAdditionalCbrPackage(true);
        testFeatureMetrics.onChangedAreaInfoPackage(Arrays.asList(new String[]{"test.com"}));
        testFeatureMetrics.onChangedResetAreaInfo(true);

        assertFalse(testFeatureMetrics.equals(testSharedPreferenceFeatureMetrics));

        CellBroadcastServiceMetrics.getInstance().logFeatureChangedAsNeeded(mMockedContext);

        CellBroadcastServiceMetrics.FeatureMetrics mockedFeatureMetrics =
                mock(CellBroadcastServiceMetrics.FeatureMetrics.class);

        CellBroadcastServiceMetrics.getInstance()
                .setFeatureMetricsSharedPreferences(mockedFeatureMetrics);

        assertFalse(mockedFeatureMetrics.equals(testFeatureMetrics));

        CellBroadcastServiceMetrics.getInstance().logFeatureChangedAsNeeded(mMockedContext);

        CellBroadcastServiceMetrics.getInstance().setFeatureMetrics(mockedFeatureMetrics);

        assertTrue(testFeatureMetrics.equals(CellBroadcastServiceMetrics.getInstance()
                .getFeatureMetricsSharedPreferences()));

        CellBroadcastServiceMetrics.getInstance().logFeatureChangedAsNeeded(mMockedContext);

        verify(mockedFeatureMetrics, times(1)).logFeatureChanged();
        verify(mockedFeatureMetrics, times(1)).updateSharedPreferences();
        verify(mockedFeatureMetrics, times(1)).clone();
    }
}
