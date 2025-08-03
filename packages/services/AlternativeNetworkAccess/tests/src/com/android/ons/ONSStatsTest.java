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

package com.android.ons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.ons.ONSProfileActivator.Result;
import com.android.ons.ONSProfileDownloader.DownloadRetryResultCode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public class ONSStatsTest {

    private static final String ONS_ATOM_LOG_FILE = "ons_atom_log_info";
    private static final String KEY_DETAILED_ERROR_CODE = "_detailed_error_code";

    @Spy private Context mContext;
    @Mock private SubscriptionManager mSubscriptionManager;
    private SharedPreferences mSharedPreferences;
    @Mock private SubscriptionInfo mSubInfoId1;
    @Mock private SubscriptionInfo mSubInfoId2;
    private ONSStats mONSStats;

    private class FakeSharedPreferences implements SharedPreferences {
        HashMap<String, Object> mMap = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return mMap;
        }

        @Override
        public String getString(String key, String defValue) {
            return (String) mMap.getOrDefault(key, defValue);
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            if (mMap.containsKey(key)) {
                return (Set<String>) mMap.get(key);
            }
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            return (int) mMap.getOrDefault(key, defValue);
        }

        @Override
        public long getLong(String key, long defValue) {
            return 0; // not used
        }

        @Override
        public float getFloat(String key, float defValue) {
            return (float) mMap.getOrDefault(key, defValue);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return (boolean) mMap.getOrDefault(key, defValue);
        }

        @Override
        public boolean contains(String key) {
            return mMap.containsKey(key);
        }

        @Override
        public Editor edit() {
            TestEditor editor = new TestEditor();
            editor.map = mMap;
            return editor;
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        private class TestEditor implements SharedPreferences.Editor {
            HashMap<String, Object> map = new HashMap<>();

            @Override
            public SharedPreferences.Editor putString(String key, String value) {
                map.put(key, value);
                return this;
            }

            @Override
            public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
                map.put(key, values);
                return this;
            }

            @Override
            public SharedPreferences.Editor putInt(String key, int value) {
                map.put(key, value);
                return this;
            }

            @Override
            public SharedPreferences.Editor putLong(String key, long value) {
                map.put(key, value);
                return this;
            }

            @Override
            public SharedPreferences.Editor putFloat(String key, float value) {
                map.put(key, value);
                return this;
            }

            @Override
            public SharedPreferences.Editor putBoolean(String key, boolean value) {
                map.put(key, value);
                return this;
            }

            @Override
            public SharedPreferences.Editor remove(String key) {
                map.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor clear() {
                map.clear();
                return this;
            }

            @Override
            public boolean commit() {
                mMap = map;
                return true;
            }

            @Override
            public void apply() {
                mMap = map;
            }
        }
        ;
    }
    ;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSharedPreferences = new FakeSharedPreferences();
        doReturn(mSharedPreferences)
                .when(mContext)
                .getSharedPreferences(eq(ONS_ATOM_LOG_FILE), eq(Context.MODE_PRIVATE));
        doReturn(123).when(mSubInfoId1).getCarrierId();
        doReturn(456).when(mSubInfoId2).getCarrierId();
        doReturn(mSubInfoId1).when(mSubscriptionManager).getActiveSubscriptionInfo(1);
        doReturn(mSubInfoId2).when(mSubscriptionManager).getActiveSubscriptionInfo(2);
        mONSStats = new ONSStats(mContext, mSubscriptionManager);
    }

    @After
    public void tearDown() {
        mSharedPreferences.edit().clear().apply();
    }

    @Test
    public void testLogEvent() {
        ONSStatsInfo info =
                new ONSStatsInfo()
                        .setPrimarySimSubId(1)
                        .setProvisioningResult(Result.ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE);
        assertTrue(mONSStats.logEvent(info));
    }

    @Test
    public void testIgnoredLogEvents() {
        // ignored error codes should not log.
        ONSStatsInfo info = new ONSStatsInfo().setProvisioningResult(Result.DOWNLOAD_REQUESTED);
        assertFalse(mONSStats.logEvent(info));

        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_NO_SIM_INSERTED);
        assertFalse(mONSStats.logEvent(info));

        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_DUPLICATE_DOWNLOAD_REQUEST);
        assertFalse(mONSStats.logEvent(info));

        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_SWITCHING_TO_DUAL_SIM_MODE);
        assertFalse(mONSStats.logEvent(info));
    }

    @Test
    public void testRepeatedLogEvents() {
        ONSStatsInfo info;
        info =
                new ONSStatsInfo()
                        .setDownloadResult(DownloadRetryResultCode.ERR_MEMORY_FULL)
                        .setDetailedErrCode(10011);
        assertTrue(mONSStats.logEvent(info));

        // same result should not log consecutively
        assertFalse(mONSStats.logEvent(info));
        assertFalse(mONSStats.logEvent(info));
    }

    @Test
    public void testRepeatedAllowedLogEvents() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_DOWNLOADED_ESIM_NOT_FOUND);
        assertTrue(mONSStats.logEvent(info));

        // ERR_DOWNLOADED_ESIM_NOT_FOUND is allowed to log consecutively
        assertTrue(mONSStats.logEvent(info));
        assertTrue(mONSStats.logEvent(info));

        info =
                new ONSStatsInfo()
                        .setDownloadResult(DownloadRetryResultCode.ERR_INSTALL_ESIM_PROFILE_FAILED);
        assertTrue(mONSStats.logEvent(info));

        // ERR_INSTALL_ESIM_PROFILE_FAILED is allowed to log consecutively
        assertTrue(mONSStats.logEvent(info));
        assertTrue(mONSStats.logEvent(info));
    }

    @Test
    public void testRepeatedSuccessLogEvents() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setProvisioningResult(Result.SUCCESS).setRetryCount(2);

        // should log every time if eSIM is newly downloaded.
        assertTrue(mONSStats.logEvent(info));
        assertTrue(mONSStats.logEvent(info));

        info = new ONSStatsInfo().setProvisioningResult(Result.SUCCESS);
        // should log even if eSIM is already downloaded and event triggered just to group it.
        assertTrue(mONSStats.logEvent(info));
        assertTrue(mONSStats.logEvent(info));
    }

    @Test
    public void testRepeatedErrorWithInfoChangeLogEvents() {
        ONSStatsInfo info =
                new ONSStatsInfo()
                        .setPrimarySimSubId(1)
                        .setProvisioningResult(Result.ERR_AUTO_PROVISIONING_DISABLED);
        assertTrue(mONSStats.logEvent(info));

        // Same error should log if the info is changed.
        info.setPrimarySimSubId(2);
        assertTrue(mONSStats.logEvent(info));

        // no change in info
        assertFalse(mONSStats.logEvent(info));
    }

    @Test
    public void testDetailedErrorCodeLogEvents() {
        ONSStatsInfo info;
        info = new ONSStatsInfo().setProvisioningResult(Result.ERR_WAITING_FOR_INTERNET_CONNECTION);
        assertTrue(mONSStats.logEvent(info));

        // For provisioning errors; Result enum ordinal is set as detailed error code.
        assertEquals(
                Result.ERR_WAITING_FOR_INTERNET_CONNECTION.ordinal(),
                mSharedPreferences.getInt(KEY_DETAILED_ERROR_CODE, -1));
        assertEquals(
                Result.ERR_WAITING_FOR_INTERNET_CONNECTION.ordinal(), info.getDetailedErrCode());

        // For Download errors; detailed error code is updated from EuiccManager.
        info =
                new ONSStatsInfo()
                        .setDownloadResult(DownloadRetryResultCode.ERR_MEMORY_FULL)
                        .setDetailedErrCode(10223);
        assertTrue(mONSStats.logEvent(info));
        assertEquals(10223, mSharedPreferences.getInt(KEY_DETAILED_ERROR_CODE, -1));
        assertEquals(10223, info.getDetailedErrCode());
    }
}
