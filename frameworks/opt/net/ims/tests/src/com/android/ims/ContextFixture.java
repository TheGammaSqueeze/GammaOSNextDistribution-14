/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ims;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.PersistableBundle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;

import org.mockito.stubbing.Answer;

import java.util.HashSet;
import java.util.concurrent.Executor;

public class ContextFixture {

    private final Context mContext = spy(new FakeContext());

    private final TelephonyManager mTelephonyManager = mock(TelephonyManager.class);
    private final ConnectivityManager mConnectivityManager = mock(ConnectivityManager.class);
    private final CarrierConfigManager mCarrierConfigManager = mock(CarrierConfigManager.class);
    private final PackageManager mPackageManager = mock(PackageManager.class);
    private final SubscriptionManager mSubscriptionManager = mock(SubscriptionManager.class);
    private final ImsManager mImsManager = mock(ImsManager.class);
    private final Resources mResources = mock(Resources.class);

    private final PersistableBundle mBundle = new PersistableBundle();
    private final HashSet<String> mSystemFeatures = new HashSet<>();
    private final MockContentResolver mMockContentResolver = new MockContentResolver();

    public ContextFixture() throws Exception {
        doReturn(mBundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());
        doReturn(mBundle).when(mCarrierConfigManager).getConfig();

        doAnswer((Answer<Boolean>)
                invocation -> mSystemFeatures.contains((String) invocation.getArgument(0)))
                .when(mPackageManager).hasSystemFeature(any());

        doReturn(mResources).when(mPackageManager).getResourcesForApplication(anyString());
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
    }

    public void destroy() {
    }

    public class FakeContext extends MockContext {
        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        public Object getSystemService(String name) {
            switch (name) {
                case Context.TELEPHONY_SERVICE:
                    return mTelephonyManager;
                case Context.CARRIER_CONFIG_SERVICE:
                    return mCarrierConfigManager;
                case Context.CONNECTIVITY_SERVICE:
                    return mConnectivityManager;
                case Context.TELEPHONY_SUBSCRIPTION_SERVICE:
                    return mSubscriptionManager;
                case Context.TELEPHONY_IMS_SERVICE:
                    return mImsManager;
                default:
                    return null;
            }
        }

        @Override
        public String getSystemServiceName(Class<?> serviceClass) {
            if (serviceClass == SubscriptionManager.class) {
                return Context.TELEPHONY_SUBSCRIPTION_SERVICE;
            } else if (serviceClass == TelecomManager.class) {
                return Context.TELECOM_SERVICE;
            } else if (serviceClass == ConnectivityManager.class) {
                return Context.CONNECTIVITY_SERVICE;
            } else if (serviceClass == TelephonyManager.class) {
                return Context.TELEPHONY_SERVICE;
            } else if (serviceClass == ImsManager.class) {
                return Context.TELEPHONY_IMS_SERVICE;
            } else if (serviceClass == CarrierConfigManager.class) {
                return Context.CARRIER_CONFIG_SERVICE;
            }
            return super.getSystemServiceName(serviceClass);
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            return null;
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler) {
            return null;
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
                String broadcastPermission, Handler scheduler, int flags) {
            return null;
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
        }

        @Override
        public ContentResolver getContentResolver() {
            return mMockContentResolver;
        }

        @Override
        public Executor getMainExecutor() {
            return Runnable::run;
        }

        @Override
        public Context getApplicationContext() {
            return mContext;
        }
    }

    public Context getContext() {
        return mContext;
    }

    public PersistableBundle getTestCarrierConfigBundle() {
        return mBundle;
    }

    public void addSystemFeature(String feature) {
        mSystemFeatures.add(feature);
    }

    public void removeSystemFeature(String feature) {
        mSystemFeatures.remove(feature);
    }
}
