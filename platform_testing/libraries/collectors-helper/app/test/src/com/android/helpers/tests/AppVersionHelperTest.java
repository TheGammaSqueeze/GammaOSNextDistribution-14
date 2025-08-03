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

package com.android.helpers.tests;

import static com.android.helpers.MetricUtility.constructKey;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.AppVersionHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Android Unit tests for {@link AppVersionHelper}.
 *
 * To run:
 * atest CollectorsHelperTest:com.android.helpers.tests.AppVersionHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class AppVersionHelperTest {

    // Package name used for testing
    private static final String TEST_PACKAGE_NAME = "com.android.systemui";
    // Second package name used for testing
    private static final String TEST_PACKAGE_NAME_2 = "com.google.android.apps.nexuslauncher";
    // Invalid package name used for testing
    private static final String INVALID_PACKAGE_NAME = "abc";
    // App version metric prefix in Key.
    private static final String METRIC_PREFIX = "app-version-code-long";

    private AppVersionHelper mAppVersionHelper;
    private static final String TAG = AppVersionHelper.class.getSimpleName();

    @Before
    public void setUp() {
        mAppVersionHelper = new AppVersionHelper();
    }

    /** Test all app version metrics are sampled if package name is empty. */
    @Test
    public void testEmptyPackageName() {
        mAppVersionHelper.setUp("");
        Map<String, Long> appVersionMetrics = mAppVersionHelper.getMetrics();
        assertFalse("Metrics should not be empty for empty package name.",
                appVersionMetrics.isEmpty());
    }

    /** Test all app version metrics are sampled if package names is null */
    @Test
    public void testNullPackageName() {
        mAppVersionHelper.setUp(null);
        Map<String, Long> appVersionMetrics = mAppVersionHelper.getMetrics();
        assertFalse("Metrics should not be empty for null package name.",
                appVersionMetrics.isEmpty());
    }

    /** Test getting metrics for single package. */
    @Test
    public void testGetMetrics_OnePackage() {
        mAppVersionHelper.setUp(TEST_PACKAGE_NAME);
        Map<String, Long> appVersionMetrics = mAppVersionHelper.getMetrics();
        assertFalse("Metrics should not be empty for single package name.",
                appVersionMetrics.isEmpty());
        String metricKey = constructKey(METRIC_PREFIX, TEST_PACKAGE_NAME);
        assertTrue("Missing metric key: " + metricKey, appVersionMetrics.containsKey(metricKey));
        assertTrue("Bad metric for " + metricKey + ": " + appVersionMetrics.get(metricKey),
                appVersionMetrics.get(metricKey) > 0);
    }

    /** Test getting metrics for multiple packages. */
    @Test
    public void testGetMetrics_MultiplePackages() {
        mAppVersionHelper.setUp(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2);
        Map<String, Long> appVersionMetrics = mAppVersionHelper.getMetrics();
        assertFalse("Metrics should not be empty for multiple package names.",
                appVersionMetrics.isEmpty());
        String metricKey1 = constructKey(METRIC_PREFIX, TEST_PACKAGE_NAME);
        String metricKey2 = constructKey(METRIC_PREFIX, TEST_PACKAGE_NAME_2);

        assertTrue("Missing metric key: " + metricKey1,
                appVersionMetrics.containsKey(metricKey1));
        assertTrue("Missing metric key: " + metricKey2,
                appVersionMetrics.containsKey(metricKey2));
        assertTrue("Bad metric for " + metricKey1 + ": " + appVersionMetrics.get(metricKey1),
                appVersionMetrics.get(metricKey1) > 0);
        assertTrue("Bad metric for " + metricKey2 + ": " + appVersionMetrics.get(metricKey2),
                appVersionMetrics.get(metricKey2) > 0);
    }

    /** Test app version metric is 0 for invalid package name. */
    @Test
    public void testGetMetrics_InvalidPackage() {
        mAppVersionHelper.setUp(INVALID_PACKAGE_NAME);
        Map<String, Long> appVersionMetrics = mAppVersionHelper.getMetrics();
        assertTrue("Metrics should not be collected for invalid package names.",
                appVersionMetrics.isEmpty());
    }
}