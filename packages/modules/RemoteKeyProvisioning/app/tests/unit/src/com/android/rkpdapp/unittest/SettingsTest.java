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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.testutil.SystemPropertySetter;
import com.android.rkpdapp.utils.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class SettingsTest {

    private static Context sContext;

    @BeforeClass
    public static void init() {
        sContext = ApplicationProvider.getApplicationContext();
    }

    @Before
    public void setUp() {
        Settings.clearPreferences(sContext);
    }

    @After
    public void tearDown() {
        Settings.clearPreferences(sContext);
    }

    @Test
    public void testDefaultUrlEmpty() {
        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("")) {
            assertThat(Settings.getDefaultUrl()).isEmpty();
        }
    }

    @Test
    public void testDefaultUrlNonEmpty() {
        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("your.hostname")) {
            assertThat(Settings.getDefaultUrl()).isEqualTo("https://your.hostname/v1");
        }
    }

    @Test
    public void testCheckDefaults() throws Exception {
        assertEquals(Settings.EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                     Settings.getExtraSignedKeysAvailable(sContext));
        assertEquals(Settings.EXPIRING_BY_MS_DEFAULT,
                     Settings.getExpiringBy(sContext).toMillis());
        assertEquals(Settings.getDefaultUrl(),
                     Settings.getUrl(sContext));
        assertEquals(0, Settings.getFailureCounter(sContext));
    }

    @Test
    public void testCheckIdSettings() throws Exception {
        int defaultRandom = Settings.getId(sContext);
        assertTrue("Default ID out of bounds.",
                defaultRandom < Settings.ID_UPPER_BOUND && defaultRandom >= 0);
        Settings.generateAndSetId(sContext);
        int setId = Settings.getId(sContext);
        assertTrue("Stored ID out of bounds.",
                setId < Settings.ID_UPPER_BOUND && setId >= 0);
        Settings.generateAndSetId(sContext);
        assertEquals("ID should not be updated by a repeated call",
                     setId, Settings.getId(sContext));
    }

    @Test
    public void testResetDefaults() throws Exception {
        int extraKeys = 12;
        Duration expiringBy = Duration.ofMillis(1000);
        String url = "https://www.remoteprovisionalot";
        assertTrue("Method did not return true on write.",
                   Settings.setDeviceConfig(sContext, extraKeys, expiringBy, url));
        Settings.incrementFailureCounter(sContext);
        Settings.setMaxRequestTime(sContext, 100);
        Settings.resetDefaultConfig(sContext);
        assertEquals(Settings.EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                     Settings.getExtraSignedKeysAvailable(sContext));
        assertEquals(Settings.EXPIRING_BY_MS_DEFAULT,
                     Settings.getExpiringBy(sContext).toMillis());
        assertEquals(Settings.getDefaultUrl(),
                     Settings.getUrl(sContext));
        assertEquals(0, Settings.getFailureCounter(sContext));
        assertEquals(20000, Settings.getMaxRequestTime(sContext));
    }

    @Test
    public void testSetDeviceConfig() {
        int extraKeys = 12;
        Duration expiringBy = Duration.ofMillis(1000);
        String url = "https://www.remoteprovisionalot";
        assertTrue("Method did not return true on write.",
                   Settings.setDeviceConfig(sContext, extraKeys, expiringBy, url));
        assertEquals(extraKeys, Settings.getExtraSignedKeysAvailable(sContext));
        assertEquals(expiringBy.toMillis(), Settings.getExpiringBy(sContext).toMillis());
        assertEquals(url, Settings.getUrl(sContext));
    }

    @Test
    public void testGetExpirationTime() {
        long expiringBy = Settings.getExpiringBy(sContext).toMillis();
        long timeDif = Settings.getExpirationTime(sContext).toEpochMilli()
                       - (expiringBy + System.currentTimeMillis());
        assertTrue(Math.abs(timeDif) < 1000);
    }

    @Test
    public void testFailureCounter() {
        assertEquals(1, Settings.incrementFailureCounter(sContext));
        assertEquals(1, Settings.getFailureCounter(sContext));
        for (int i = 1; i < 10; i++) {
            assertEquals(i + 1, Settings.incrementFailureCounter(sContext));
        }
        Settings.clearFailureCounter(sContext);
        assertEquals(0, Settings.getFailureCounter(sContext));
        Settings.incrementFailureCounter(sContext);
        assertEquals(1, Settings.getFailureCounter(sContext));
    }

    @Test
    public void testDataBudgetUnused() {
        assertEquals(0, Settings.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetIncrement() {
        int[] bytesUsed = new int[]{1, 40, 100};
        assertEquals(0, Settings.getErrDataBudgetConsumed(sContext));

        Settings.consumeErrDataBudget(sContext, bytesUsed[0]);
        assertEquals(bytesUsed[0], Settings.getErrDataBudgetConsumed(sContext));

        Settings.consumeErrDataBudget(sContext, bytesUsed[1]);
        assertEquals(bytesUsed[0] + bytesUsed[1],
                     Settings.getErrDataBudgetConsumed(sContext));

        Settings.consumeErrDataBudget(sContext, bytesUsed[2]);
        assertEquals(bytesUsed[0] + bytesUsed[1] + bytesUsed[2],
                     Settings.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetInvalidIncrement() {
        assertEquals(0, Settings.getErrDataBudgetConsumed(sContext));
        Settings.consumeErrDataBudget(sContext, -20);
        assertEquals(0, Settings.getErrDataBudgetConsumed(sContext));
        Settings.consumeErrDataBudget(sContext, 40);
        Settings.consumeErrDataBudget(sContext, -400);
        Settings.consumeErrDataBudget(sContext, 60);
        assertEquals(100, Settings.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetReset() {
        // The first call to hasErrDataBudget will set the start of the bucket.
        assertTrue(Settings.hasErrDataBudget(sContext, null /* curTime */));

        Settings.consumeErrDataBudget(sContext, 100);
        assertTrue(Settings.hasErrDataBudget(sContext, null));
        assertEquals(100, Settings.getErrDataBudgetConsumed(sContext));

        assertTrue(Settings.hasErrDataBudget(sContext,
                Instant.now().plusMillis(Settings.FAILURE_DATA_USAGE_WINDOW.toMillis() + 20)));
        assertEquals(0, Settings.getErrDataBudgetConsumed(sContext));
    }

    @Test
    public void testDataBudgetExceeded() {
        // The first call to hasErrDataBudget will set the start of the bucket.
        assertTrue(Settings.hasErrDataBudget(sContext, null /* curTime */));
        Settings.consumeErrDataBudget(sContext, Settings.FAILURE_DATA_USAGE_MAX - 1);
        assertTrue(Settings.hasErrDataBudget(sContext, null));
        Settings.consumeErrDataBudget(sContext, 1);
        assertFalse(Settings.hasErrDataBudget(sContext, null));
    }

    @Test
    public void testServerTimeoutSetting() {
        assertEquals(20000, Settings.getMaxRequestTime(sContext));
        Settings.setMaxRequestTime(sContext, 100);
        assertEquals(100, Settings.getMaxRequestTime(sContext));
    }
}
