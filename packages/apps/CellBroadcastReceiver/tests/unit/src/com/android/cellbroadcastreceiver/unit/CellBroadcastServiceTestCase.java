/**
 * Copyright (C) 2016 The Android Open Source Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver.unit;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.ServiceTestCase;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.internal.telephony.ISub;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

public abstract class CellBroadcastServiceTestCase<T extends Service> extends ServiceTestCase<T> {

    @Mock
    protected CarrierConfigManager mMockedCarrierConfigManager;
    @Mock
    Resources mResources;
    @Mock
    protected ISub.Stub mSubService;
    @Mock
    protected AudioManager mMockedAudioManager;
    @Mock
    protected SubscriptionManager mMockedSubscriptionManager;
    @Mock
    protected SubscriptionInfo mMockSubscriptionInfo;
    @Mock
    protected TelephonyManager mMockedTelephonyManager;
    @Mock
    protected Vibrator mMockedVibrator;
    @Mock
    protected SharedPreferences mMockedSharedPreferences;
    @Mock
    protected Context mMockContextForRoaming;
    @Mock
    protected NotificationManager mMockedNotificationManager;
    protected PowerManager mMockedPowerManager;

    protected Configuration mConfiguration;

    private PackageManager mPackageManager;

    MockedServiceManager mMockedServiceManager;

    Intent mServiceIntentToVerify;

    Intent mActivityIntentToVerify;

    Bundle mBundle;

    CellBroadcastServiceTestCase(Class<T> serviceClass) {
        super(serviceClass);
    }

    protected static void waitFor(BooleanSupplier condition) {
        if (condition.getAsBoolean()) {
            return;
        }
        for (int i = 0; i < 50; i++) {
            SystemClock.sleep(100);
            if (condition.getAsBoolean()) {
                return;
            }
        }
    }

    protected void enablePreference(String pref) {
        doReturn(true).when(mMockedSharedPreferences).getBoolean(eq(pref), anyBoolean());
    }

    protected void disablePreference(String pref) {
        doReturn(false).when(mMockedSharedPreferences).getBoolean(eq(pref), anyBoolean());
    }

    public class TestContextWrapper extends ContextWrapper {

        private final String TAG = TestContextWrapper.class.getSimpleName();

        public TestContextWrapper(Context base) {
            super(base);
            mMockContextForRoaming = null;
        }

        @Override
        public ComponentName startService(Intent service) {
            mServiceIntentToVerify = service;
            return null;
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public void startActivity(Intent intent) {
            mActivityIntentToVerify = intent;
        }

        @Override
        public void startActivity(Intent intent, Bundle bundle) {
            mActivityIntentToVerify = intent;
            mBundle = bundle;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Object getSystemService(String name) {
            switch (name) {
                case Context.CARRIER_CONFIG_SERVICE:
                    return mMockedCarrierConfigManager;
                case Context.AUDIO_SERVICE:
                    return mMockedAudioManager;
                case Context.TELEPHONY_SUBSCRIPTION_SERVICE:
                    return mMockedSubscriptionManager;
                case Context.TELEPHONY_SERVICE:
                    return mMockedTelephonyManager;
                case Context.VIBRATOR_SERVICE:
                    return mMockedVibrator;
                case Context.NOTIFICATION_SERVICE:
                    return mMockedNotificationManager;
                case Context.POWER_SERVICE:
                    if (mMockedPowerManager != null) {
                        return mMockedPowerManager;
                    }
                    break;
            }
            return super.getSystemService(name);
        }

        @Override
        public String getSystemServiceName(Class<?> serviceClass) {
            if (TelephonyManager.class.equals(serviceClass)) {
                return Context.TELEPHONY_SERVICE;
            }
            return super.getSystemServiceName(serviceClass);
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return mMockedSharedPreferences;
        }

        @Override
        public Context createConfigurationContext(Configuration overrideConfiguration) {
            if (mMockContextForRoaming == null) {
                return this;
            } else {
                return mMockContextForRoaming;
            }
        }

        @Override
        public PackageManager getPackageManager() {
            if (mPackageManager != null) {
                return mPackageManager;
            }
            return super.getPackageManager();
        }


        public void injectCreateConfigurationContext(Context context) {
            mMockContextForRoaming = context;
        }

    }

    public void injectPackageManager(PackageManager packageManager) {
        mPackageManager = packageManager;
    }

    public void setWatchFeatureEnabled(boolean enabled) {
        PackageManager mockPackageManager = mock(PackageManager.class);
        doReturn(enabled).when(mockPackageManager).hasSystemFeature(PackageManager.FEATURE_WATCH);
        injectPackageManager(mockPackageManager);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // A hack to return mResources from static method
        // CellBroadcastSettings.getResources(context).
        //doReturn(mSubService).when(mSubService).queryLocalInterface(anyString());
        try {
            // Only exist after U-QPR2, so the reflection amounts to a QPR version check.
            ISub.Stub.class.getMethod("getDefaultSubIdAsUser", int.class);
            doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    .when(mSubService).getDefaultSubIdAsUser(anyInt());
            doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    .when(mSubService).getDefaultSmsSubIdAsUser(anyInt());
        } catch (Exception methodNotFound) {
            doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    .when(mSubService).getDefaultSubId();
            doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    .when(mSubService).getDefaultSmsSubId();
        }
        doReturn(new String[]{""}).when(mResources).getStringArray(anyInt());

        mConfiguration = new Configuration();
        doReturn(mConfiguration).when(mResources).getConfiguration();

        doReturn(1).when(mMockSubscriptionInfo).getSubscriptionId();
        doReturn(Arrays.asList(mMockSubscriptionInfo)).when(mMockedSubscriptionManager)
                .getActiveSubscriptionInfoList();

        doReturn(mMockedTelephonyManager).when(mMockedTelephonyManager)
                .createForSubscriptionId(anyInt());

        mMockedServiceManager = new MockedServiceManager();
        mMockedServiceManager.replaceService("isub", mSubService);

        mContext = new TestContextWrapper(getContext());
        setContext(mContext);
        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
    }

    @After
    public void tearDown() throws Exception {
        mMockedServiceManager.restoreAllServices();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
    }

    void putResources(int id, String[] values) {
        doReturn(values).when(mResources).getStringArray(eq(id));
    }

    void putResources(int id, boolean values) {
        doReturn(values).when(mResources).getBoolean(eq(id));
    }
}
