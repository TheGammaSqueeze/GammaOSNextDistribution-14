/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.withSettings;

import android.os.SystemProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit test for {@link RegulatoryInfoDisplayActivity}. */
@RunWith(AndroidJUnit4.class)
public class RegulatoryInfoDisplayActivityTest {

    private RegulatoryInfoDisplayActivity mActivity;
    private MockitoSession mSession;

    @Rule
    public ActivityTestRule<RegulatoryInfoDisplayActivity> mActivityTestRule =
            new ActivityTestRule<>(RegulatoryInfoDisplayActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                SystemProperties.class, withSettings().lenient()).startMocking();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getRegulatoryInfoImageFileName_skuIsNotEmpty() {
        ExtendedMockito.when(SystemProperties.get(eq("ro.boot.hardware.sku"), anyString()))
                .thenReturn("test");

        assertThat(mActivity.getRegulatoryInfoImageFileName())
                .isEqualTo("/data/misc/elabel/regulatory_info_test.png");
    }

    @Test
    public void getRegulatoryInfoImageFileName_skuIsEmpty() {
        ExtendedMockito.when(SystemProperties.get(eq("ro.boot.hardware.sku"), anyString()))
                .thenReturn("");

        assertThat(mActivity.getRegulatoryInfoImageFileName())
                .isEqualTo("/data/misc/elabel/regulatory_info.png");
    }

    @Test
    public void getSku_shouldReturnSystemProperty() {
        String testSku = "test";
        ExtendedMockito.when(SystemProperties.get(eq("ro.boot.hardware.sku"), anyString()))
                .thenReturn(testSku);

        assertThat(mActivity.getSku()).isEqualTo(testSku);
    }
}
