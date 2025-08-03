/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserManager;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.test.mock.MockContext;
import android.util.Log;
import android.util.SparseArray;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.HashSet;
import java.util.concurrent.Executor;

public class TestContext extends MockContext {

    private static final String TAG = "TestContext";
    // Stub used to grant all permissions
    public static final String STUB_PERMISSION_ENABLE_ALL = "stub_permission_enable_all";

    @Mock CarrierConfigManager mMockCarrierConfigManager;
    @Mock TelecomManager mMockTelecomManager;
    @Mock TelephonyManager mMockTelephonyManager;
    @Mock SubscriptionManager mMockSubscriptionManager;
    @Mock ImsManager mMockImsManager;
    @Mock UserManager mMockUserManager;

    private final SparseArray<PersistableBundle> mCarrierConfigs = new SparseArray<>();

    private Intent mIntent;

    private BroadcastReceiver mReceiver;

    private final HashSet<String> mPermissionTable = new HashSet<>();

    public TestContext() {
        MockitoAnnotations.initMocks(this);
        doAnswer((Answer<PersistableBundle>) invocation -> {
            int subId = (int) invocation.getArguments()[0];
            return getTestConfigs(subId);
        }).when(mMockCarrierConfigManager).getConfigForSubId(anyInt());
        doAnswer((Answer<PersistableBundle>) invocation -> {
            int subId = (int) invocation.getArguments()[0];
            return getTestConfigs(subId);
        }).when(mMockCarrierConfigManager).getConfigForSubId(anyInt(), anyString());
    }

    @Override
    public Executor getMainExecutor() {
        // Just run on current thread
        return Runnable::run;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public String getPackageName() {
        return "com.android.phone.tests";
    }

    @Override
    public String getAttributionTag() {
        return "";
    }

    @Override
    public AttributionSource getAttributionSource() {
        return new AttributionSource(Process.myUid(), getPackageName(), "");
    }

    @Override
    public void sendBroadcast(Intent intent) {
        mIntent = intent;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        mReceiver = receiver;
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        mReceiver = receiver;
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler) {
        mReceiver = receiver;
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler, int flags) {
        mReceiver = receiver;
        return null;
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        mReceiver = null;
    }

    @Override
    public ContentResolver getContentResolver() {
        return null;
    }

    @Override
    public Object getSystemService(String name) {
        switch (name) {
            case Context.CARRIER_CONFIG_SERVICE: {
                return mMockCarrierConfigManager;
            }
            case Context.TELECOM_SERVICE: {
                return mMockTelecomManager;
            }
            case Context.TELEPHONY_SERVICE: {
                return mMockTelephonyManager;
            }
            case Context.TELEPHONY_SUBSCRIPTION_SERVICE: {
                return mMockSubscriptionManager;
            }
            case Context.TELEPHONY_IMS_SERVICE: {
                return mMockImsManager;
            }
            case Context.USER_SERVICE: {
                return mMockUserManager;
            }
        }
        return null;
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        if (serviceClass == CarrierConfigManager.class) {
            return Context.CARRIER_CONFIG_SERVICE;
        }
        if (serviceClass == TelecomManager.class) {
            return Context.TELECOM_SERVICE;
        }
        if (serviceClass == TelephonyManager.class) {
            return Context.TELEPHONY_SERVICE;
        }
        if (serviceClass == SubscriptionManager.class) {
            return Context.TELEPHONY_SUBSCRIPTION_SERVICE;
        }
        if (serviceClass == ImsManager.class) {
            return Context.TELEPHONY_IMS_SERVICE;
        }
        if (serviceClass == UserManager.class) {
            return Context.USER_SERVICE;
        }
        return null;
    }

    @Override
    public Handler getMainThreadHandler() {
        return new Handler(Looper.getMainLooper());
    }

    /**
     * @return CarrierConfig PersistableBundle for the subscription specified.
     */
    public PersistableBundle getCarrierConfig(int subId) {
        PersistableBundle b = mCarrierConfigs.get(subId);
        if (b == null) {
            b = new PersistableBundle();
            mCarrierConfigs.put(subId, b);
        }
        return b;
    }

    @Override
    public void enforceCallingOrSelfPermission(String permission, String message) {
        if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(permission + " denied: " + message);
        }
    }

    @Override
    public void enforcePermission(String permission, int pid, int uid, String message) {
        enforceCallingOrSelfPermission(permission, message);
    }

    @Override
    public void enforceCallingPermission(String permission, String message) {
        enforceCallingOrSelfPermission(permission, message);
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        return checkPermission(permission, Binder.getCallingPid(), Binder.getCallingUid());
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        synchronized (mPermissionTable) {
            if (mPermissionTable.contains(permission)
                    || mPermissionTable.contains(STUB_PERMISSION_ENABLE_ALL)) {
                logd("checkCallingOrSelfPermission: " + permission + " return GRANTED");
                return PackageManager.PERMISSION_GRANTED;
            } else {
                logd("checkCallingOrSelfPermission: " + permission + " return DENIED");
                return PackageManager.PERMISSION_DENIED;
            }
        }
    }

    public void grantPermission(String permission) {
        synchronized (mPermissionTable) {
            if (permission == null) return;
            mPermissionTable.remove(STUB_PERMISSION_ENABLE_ALL);
            mPermissionTable.add(permission);
        }
    }

    public void revokePermission(String permission) {
        synchronized (mPermissionTable) {
            if (permission == null) return;
            mPermissionTable.remove(permission);
        }
    }

    public void revokeAllPermissions() {
        synchronized (mPermissionTable) {
            mPermissionTable.clear();
        }
    }

    public Intent getBroadcast() {
        return mIntent;
    }

    public BroadcastReceiver getBroadcastReceiver() {
        return mReceiver;
    }

    private PersistableBundle getTestConfigs(int subId) {
        if (subId < 0) {
            return new PersistableBundle();
        }
        PersistableBundle b = getCarrierConfig(subId);
        return (b != null ? b : new PersistableBundle());
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
