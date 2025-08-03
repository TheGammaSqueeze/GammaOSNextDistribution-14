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
package com.android.bedstead.dpmwrapper;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import static com.android.bedstead.dpmwrapper.DataFormatter.addArg;
import static com.android.bedstead.dpmwrapper.DataFormatter.getArg;
import static com.android.bedstead.dpmwrapper.Utils.ACTION_WRAPPED_MANAGER_CALL;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_CLASS;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_METHOD;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_NUMBER_ARGS;
import static com.android.bedstead.dpmwrapper.Utils.VERBOSE;
import static com.android.bedstead.dpmwrapper.Utils.getHandler;

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.HardwarePropertiesManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import org.mockito.stubbing.Answer;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

//TODO(b/176993670): STOPSHIP - it currently uses ordered broadcasts and a Mockito spy to implement
//the IPC between users, but before S is shipped it should be changed to use the connected apps SDK
//or the new CTS infrastructure.
/**
 * Class used to create to provide a {@link DevicePolicyManager} implementation (and other managers
 * that must be run by the device owner user) that automatically funnels calls between the user
 * running the tests and the user that is the device owner.
 */
public final class TestAppSystemServiceFactory {

    private static final String TAG = TestAppSystemServiceFactory.class.getSimpleName();

    private static final int RESULT_NOT_SENT_TO_ANY_RECEIVER = 108;
    static final int RESULT_OK = 42;
    static final int RESULT_EXCEPTION = 666;

    // Must be high enough to outlast long tests like NetworkLoggingTest, which waits up to
    // 6 minutes for network monitoring events.
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    // Caches whether the package declares the required receiver (otherwise each test would be
    // querying package manager, which is expensive)
    private static final HashMap<String, Boolean> sHasRequiredReceiver = new HashMap<>();

    /**
     * Gets the proper {@link DevicePolicyManager} instance to be used by the test.
     */
    public static DevicePolicyManager getDevicePolicyManager(Context context,
            Class<? extends BroadcastReceiver> receiverClass, boolean forDeviceOwner) {
        return getSystemService(context, DevicePolicyManager.class, receiverClass, forDeviceOwner);
    }

    /**
     * Gets the proper {@link WifiManager} instance to be used by device owner tests.
     */
    public static WifiManager getWifiManager(Context context,
            Class<? extends BroadcastReceiver> receiverClass) {
        return getSystemService(context, WifiManager.class, receiverClass,
                /* forDeviceOwner= */ true);
    }

    /**
     * Gets the proper {@link HardwarePropertiesManager} instance to be used by device owner tests.
     */
    public static HardwarePropertiesManager getHardwarePropertiesManager(Context context,
            Class<? extends BroadcastReceiver> receiverClass) {
        return getSystemService(context, HardwarePropertiesManager.class, receiverClass,
                /* forDeviceOwner= */ true);
    }

    /**
     * Gets the proper {@link UserManager} instance to be used by device owner tests.
     */
    public static UserManager getUserManager(Context context,
            Class<? extends BroadcastReceiver> receiverClass) {
        return getSystemService(context, UserManager.class, receiverClass,
                /* forDeviceOwner= */ true);
    }

    /**
     * Gets the proper {@link GenericManager} instance to be used by the test.
     */
    public static GenericManager getGenericManager(Context context,
            Class<? extends BroadcastReceiver> receiverClass) {
        return getSystemService(context, GenericManager.class, receiverClass,
                /* forDeviceOwner= */ true);
    }

    private static void assertHasRequiredReceiver(Context context) {
        if (!Utils.isHeadlessSystemUserMode()) return;

        String packageName = context.getPackageName();
        Boolean hasIt = sHasRequiredReceiver.get(packageName);
        if (hasIt != null && hasIt) {
            return;
        }
        PackageManager pm = context.getPackageManager();
        Class<?> targetClass = TestAppCallbacksReceiver.class;
        PackageInfo packageInfo;
        try {
            packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_RECEIVERS);
        } catch (NameNotFoundException e) {
            Log.wtf(TAG, "Could not get receivers for " + packageName);
            return;
        }

        int numberReceivers = (packageInfo.receivers == null ? 0 : packageInfo.receivers.length);
        Log.d(TAG, "assertHasRequiredReceiver(" + packageName + "): userId=" + context.getUserId()
                + ", info=" + packageInfo + ", receivers=" + numberReceivers);

        if (packageInfo.receivers != null) {
            for (ActivityInfo receiver : packageInfo.receivers) {
                Log.v(TAG, "checking receiver " + receiver);
                Class<?> receiverClass = null;
                try {
                    receiverClass = Class.forName(receiver.name);
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "Invalid receiver class on manifest: " + receiver.name);
                    continue;
                }
                if (TestAppCallbacksReceiver.class.isAssignableFrom(receiverClass)) {
                    Log.d(TAG, "Found " + receiverClass.getName() + " on " + packageName);
                    sHasRequiredReceiver.put(packageName, Boolean.TRUE);
                    return;
                }
            }
        }
        if (numberReceivers == 0) {
            // This is happening sometimes on headless system user; most likely it's a permission
            // issue querying pm, but given that the DpmWrapper is temporary and this check is more
            // of a validation to avoid other issues, it's ok to just log...
            Log.wtf(TAG, "Package " + packageName + " has no receivers");
            return;
        }
        fail("Package " + packageName + " has " + numberReceivers + " receivers, but not extends "
                + TestAppCallbacksReceiver.class.getName() + " - did you add one to the manifest?");
    }

    private static <T> T getSystemService(Context context, Class<T> serviceClass,
            Class<? extends BroadcastReceiver> receiverClass, boolean forDeviceOwner) {
        ServiceManagerWrapper<T> wrapper = null;
        Class<?> wrappedClass;

        @SuppressWarnings("unchecked")
        T manager = null;
        boolean managerCanBeNull = false;

        if (serviceClass.equals(DevicePolicyManager.class)) {
            wrappedClass = DevicePolicyManager.class;
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new DevicePolicyManagerWrapper();
            wrapper = safeCastWrapper;
        } else if (serviceClass.equals(WifiManager.class)) {
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new WifiManagerWrapper();
            wrapper = safeCastWrapper;
            wrappedClass = WifiManager.class;
            managerCanBeNull = true;
        } else if (serviceClass.equals(HardwarePropertiesManager.class)) {
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new HardwarePropertiesManagerWrapper();
            wrapper = safeCastWrapper;
            wrappedClass = HardwarePropertiesManager.class;
        } else if (serviceClass.equals(UserManager.class)) {
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new UserManagerWrapper();
            wrapper = safeCastWrapper;
            wrappedClass = UserManager.class;
        } else if (serviceClass.equals(GenericManager.class)) {
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new GenericManagerWrapper();
            @SuppressWarnings("unchecked")
            T safeCastManager = (T) new GenericManagerImpl(context);
            wrapper = safeCastWrapper;
            wrappedClass = GenericManager.class;
            manager = safeCastManager;
        } else {
            throw new IllegalArgumentException("invalid service class: " + serviceClass);
        }
        if (manager == null) {
            manager = (T) context.getSystemService(wrappedClass);
        }

        if (manager == null) {
            if (managerCanBeNull) {
                Log.i(TAG, "Manager of" + serviceClass + "is null");
                return null;
            }
            fail("Could not get a manager of type " + serviceClass);
        }

        if (!forDeviceOwner) return manager;

        assertHasRequiredReceiver(context);

        int userId = context.getUserId();
        if (userId == UserHandle.USER_SYSTEM || !Utils.isHeadlessSystemUserMode()) {
            Log.i(TAG, "get(): returning 'pure' DevicePolicyManager for user " + userId);
            return manager;
        }

        String receiverClassName = receiverClass.getName();
        final String wrappedClassName = wrappedClass.getName();
        if (VERBOSE) {
            Log.v(TAG, "get(): receiverClassName: " + receiverClassName
                    + ", wrappedClassName: " + wrappedClassName);
        }

        Answer<?> answer = (inv) -> {
            Object[] args = inv.getArguments();
            if (VERBOSE) {
                Log.v(TAG, "spying " + inv + " method: " + inv.getMethod());
            } else {
                Log.i(TAG, "spying " + inv.getMethod());
            }
            String methodName = inv.getMethod().getName();
            Intent intent = new Intent(ACTION_WRAPPED_MANAGER_CALL)
                    .setClassName(context, receiverClassName)
                    .putExtra(EXTRA_CLASS, wrappedClassName)
                    .putExtra(EXTRA_METHOD, methodName)
                    .putExtra(EXTRA_NUMBER_ARGS, args.length);
            for (int i = 0; i < args.length; i++) {
                addArg(intent, args, i);
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Result> resultRef = new AtomicReference<>();
            BroadcastReceiver myReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (VERBOSE) {
                        Log.v(TAG, "spy received intent " + action + " for user "
                                + context.getUserId());
                    }
                    Result result = new Result(this);
                    if (VERBOSE) Log.v(TAG, "result:" + result);
                    resultRef.set(result);
                    latch.countDown();
                };

            };
            if (VERBOSE) {
                Log.v(TAG, "Sending ordered broadcast (" + Utils.toString(intent) + ") from user "
                        + userId + " to user " + UserHandle.SYSTEM);
            }

            // NOTE: method below used to be wrapped under runWithShellPermissionIdentity() to get
            // INTERACT_ACROSS_USERS permission, but that's not needed anymore (as the permission
            // is granted by the test. Besides, this class is now also used by DO apps that are not
            // instrumented, so it was removed
            if (context.checkSelfPermission(INTERACT_ACROSS_USERS)
                    != PackageManager.PERMISSION_GRANTED) {
                fail("Package " + context.getPackageName() + " doesn't have "
                        + INTERACT_ACROSS_USERS + " - did you add it to the manifest and called "
                        + "grantDpmWrapper() (for user " + userId + ") in the host-side test?");
            }
            context.sendOrderedBroadcastAsUser(intent,
                    UserHandle.SYSTEM, /* permission= */ null, myReceiver, getHandler(),
                    RESULT_NOT_SENT_TO_ANY_RECEIVER, /* initialData= */ null,
                    /* initialExtras= */ null);

            if (VERBOSE) {
                Log.d(TAG, "Waiting up to " + TIMEOUT_MS + "ms for response on "
                        + Thread.currentThread());
            }
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Ordered broadcast for %s() not received in %dms", methodName, TIMEOUT_MS);
            }

            Result result = resultRef.get();
            Log.d(TAG, "Received result on user " + userId + ". Code: "
                    + resultCodeToString(result.code));

            if (VERBOSE) {
                // Some results - like network logging events - are quite large
                Log.v(TAG, "Result: " + result);
            }

            switch (result.code) {
                case RESULT_OK:
                    return result.value;
                case RESULT_EXCEPTION:
                    Exception e = (Exception) result.value;
                    throw (e instanceof InvocationTargetException) ? e.getCause() : e;
                case RESULT_NOT_SENT_TO_ANY_RECEIVER:
                    fail("Didn't receive result from ordered broadcast - did you override "
                            + receiverClassName + ".onReceive() to call "
                            + "DeviceOwnerHelper.runManagerMethod()? Did you add "
                            + ACTION_WRAPPED_MANAGER_CALL + " to its intent filter / manifest?");
                    return null;
                default:
                    fail("Received invalid result for method %s: %s", methodName, result);
                    return null;
            }
        };

        T spy = wrapper.getWrapper(context, manager, answer);

        return spy;

    }

    static String resultCodeToString(int code) {
        // Can't use DebugUtils.constantToString() because some valus are private
        switch (code) {
            case RESULT_NOT_SENT_TO_ANY_RECEIVER:
                return "RESULT_NOT_SENT_TO_ANY_RECEIVER";
            case RESULT_OK:
                return "RESULT_OK";
            case RESULT_EXCEPTION:
                return "RESULT_EXCEPTION";
            default:
                return "RESULT_UNKNOWN:" + code;
        }
    }

    private static void fail(String template, Object... args) {
        throw new AssertionError(String.format(Locale.ENGLISH, template, args));
    }

    private static final class Result {
        public final int code;
        @Nullable public final String error;
        @Nullable public final Bundle extras;
        @Nullable public final Object value;

        Result(BroadcastReceiver receiver) {
            int resultCode = receiver.getResultCode();
            String data = receiver.getResultData();
            extras = receiver.getResultExtras(/* makeMap= */ true);
            Object parsedValue = null;
            try {
                if (extras != null && !extras.isEmpty()) {
                    Object[] result = new Object[1];
                    int index = 0;
                    getArg(extras, result, /* parameterTypes= */ null, index);
                    parsedValue = result[index];
                }
            } catch (Exception e) {
                Log.e(TAG, "error parsing extras (code=" + resultCode + ", data=" + data, e);
                data = "error parsing extras";
                resultCode = RESULT_EXCEPTION;
            }
            code = resultCode;
            error = data;
            value = parsedValue;
        }

        @Override
        public String toString() {
            return "Result[code=" + resultCodeToString(code) + ", error=" + error
                    + ", extras=" + extras + ", value=" + value + "]";
        }
    }

    abstract static class ServiceManagerWrapper<T> {
        abstract T getWrapper(Context context, T manager, Answer<?> answer);
    }

    private TestAppSystemServiceFactory() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
