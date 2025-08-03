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

import static com.android.bedstead.dpmwrapper.DataFormatter.addArg;
import static com.android.bedstead.dpmwrapper.DataFormatter.getArg;
import static com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.RESULT_EXCEPTION;
import static com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.RESULT_OK;
import static com.android.bedstead.dpmwrapper.Utils.ACTION_WRAPPED_MANAGER_CALL;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_CLASS;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_METHOD;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_NUMBER_ARGS;
import static com.android.bedstead.dpmwrapper.Utils.VERBOSE;
import static com.android.bedstead.dpmwrapper.Utils.callOnHandlerThread;
import static com.android.bedstead.dpmwrapper.Utils.isHeadlessSystemUser;

import android.annotation.Nullable;
import android.app.admin.DeviceAdminReceiver;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class used by the device owner apps.
 */
public final class DeviceOwnerHelper {

    private static final String TAG = DeviceOwnerHelper.class.getSimpleName();

    /**
     * Executes a method requested by the test app.
     *
     * <p>Typical usage:
     *
     * <pre><code>
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DeviceOwnerAdminReceiverHelper.runManagerMethod(this, context, intent)) return;
            super.onReceive(context, intent);
        }
</code></pre>
     *
     * @return whether the {@code intent} represented a method that was executed.
     */
    public static boolean runManagerMethod(BroadcastReceiver receiver, Context context,
            Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "runManagerMethod(): user=" + context.getUserId() + ", action=" + action);

        if (!action.equals(ACTION_WRAPPED_MANAGER_CALL)) {
            if (VERBOSE) Log.v(TAG, "ignoring, it's not " + ACTION_WRAPPED_MANAGER_CALL);
            return false;
        }

        try {
            String className = intent.getStringExtra(EXTRA_CLASS);
            String methodName = intent.getStringExtra(EXTRA_METHOD);
            int numberArgs = intent.getIntExtra(EXTRA_NUMBER_ARGS, 0);
            Log.d(TAG, "runManagerMethod(): userId=" + context.getUserId()
                    + ", intent=" + intent.getAction() + ", class=" + className
                    + ", methodName=" + methodName + ", numberArgs=" + numberArgs);
            final Object[] args;
            Class<?>[] parameterTypes = null;
            if (numberArgs > 0) {
                args = new Object[numberArgs];
                parameterTypes = new Class<?>[numberArgs];
                Bundle extras = intent.getExtras();
                for (int i = 0; i < numberArgs; i++) {
                    getArg(extras, args, parameterTypes, i);
                }
                Log.d(TAG, "converted args: " + Arrays.toString(args) + " (with types "
                        + Arrays.toString(parameterTypes) + ")");
            } else {
                args = null;
            }
            Class<?> managerClass = Class.forName(className);
            Method method = findMethod(managerClass, methodName, parameterTypes);
            if (method == null) {
                sendError(receiver, new IllegalArgumentException(
                        "Could not find method " + methodName + " using reflection"));
                return true;
            }
            Object manager = managerClass.equals(GenericManager.class)
                    ? new GenericManagerImpl(context)
                    : context.getSystemService(managerClass);
            // Must handle in a separate thread as some APIs will fail when called from main's
            Object result = callOnHandlerThread(() -> method.invoke(manager, args));

            if (VERBOSE) {
                // Some results - like network logging events - are quite large
                Log.v(TAG, "runManagerMethod(): method returned " + result);
            } else {
                Log.v(TAG, "runManagerMethod(): method returned fine");
            }
            sendResult(receiver, result);
        } catch (Exception e) {
            sendError(receiver, e);
        }

        return true;
    }

    /**
     * Called by the device owner {@link DeviceAdminReceiver} to broadcasts an intent to the
     * receivers in the test case app.
     *
     * <p>It must be used in place of standard APIs (such as
     * {@code LocalBroadcastManager.sendBroadcast()}) because on headless system user mode the test
     * app might be running in a different user (and this method will take care of IPC'ing the
     * intent over).
     */
    public static void sendBroadcastToTestAppReceivers(Context context, Intent intent) {
        if (forwardBroadcastToTestApp(context, intent)) return;

        Log.d(TAG, "Broadcasting " + intent.getAction() + " locally on user "
                + context.getUserId());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Forwards the intent to the test app.
     *
     * <p>This method is needed in cases where the received of DPM callback must to some processing;
     * it should try to forward it first, as if it's running on headless system user, the processing
     * should be tone on the test user side.
     *
     * @return when {@code true}, the intent was forwarded and should not be processed locally.
     */
    public static boolean forwardBroadcastToTestApp(Context context, Intent intent) {
        if (!isHeadlessSystemUser()) return false;

        TestAppCallbacksReceiver.sendBroadcast(context, intent);
        return true;
    }

    @Nullable
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        // Handle some special cases first...

        // Methods that use CharSequence instead of String
        if (parameterTypes != null && parameterTypes.length == 2) {
            switch (methodName) {
                case "wipeData":
                    return clazz.getDeclaredMethod(methodName,
                            new Class<?>[] { int.class, CharSequence.class });
                case "setDeviceOwnerLockScreenInfo":
                case "setOrganizationName":
                    return clazz.getDeclaredMethod(methodName,
                            new Class<?>[] { ComponentName.class, CharSequence.class });
            }
        }
        if ((methodName.equals("setStartUserSessionMessage")
                || methodName.equals("setEndUserSessionMessage"))) {
            return clazz.getDeclaredMethod(methodName,
                    new Class<?>[] { ComponentName.class, CharSequence.class });
        }

        // Calls with null parameters (and hence the type cannot be inferred)
        Method method = findMethodWithNullParameterCall(clazz, methodName, parameterTypes);
        if (method != null) return method;

        // ...otherwise return exactly what as asked
        return clazz.getDeclaredMethod(methodName, parameterTypes);
    }

    @Nullable
    private static Method findMethodWithNullParameterCall(Class<?> clazz, String methodName,
            Class<?>[] parameterTypes) {
        if (parameterTypes == null) return null;

        Log.d(TAG, "findMethodWithNullParameterCall(): " + clazz + "." + methodName + "("
                    + Arrays.toString(parameterTypes) + ")");

        boolean hasNullParameter = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == null) {
                if (VERBOSE) {
                    Log.v(TAG, "Found null parameter at index " + i + " of " + methodName);
                }
                hasNullParameter = true;
                break;
            }
        }
        if (!hasNullParameter) return null;

        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == parameterTypes.length) {
                methods.add(method);
            }
        }
        if (VERBOSE) Log.v(TAG, "Methods found: " + methods);

        switch (methods.size()) {
            case 0:
                return null;
            case 1:
                return methods.get(0);
            default:
                return findBestMethod(methods, parameterTypes);
        }
    }

    @Nullable
    private static Method findBestMethod(List<Method> methods, Class<?>[] parameterTypes) {
        if (VERBOSE) {
            Log.v(TAG, "Found " + methods.size() + " methods: " + methods);
        }
        Method bestMethod = null;

        _methods: for (Method method : methods) {
            Parameter[] methodParameters = method.getParameters();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> expectedType = parameterTypes[i];
                if (expectedType == null) continue;

                Class<?> actualType = methodParameters[i].getType();
                if (!expectedType.equals(actualType)) {
                    if (VERBOSE) {
                        Log.v(TAG, "Parameter at index " + i + " doesn't match (expecting "
                                + expectedType + ", got " + actualType + "); rejecting " + method);
                    }
                    continue _methods;
                }
            }
            // double check there isn't more than one
            if (bestMethod != null) {
                Log.e(TAG, "found another method (" + method + "), but will use " + bestMethod);
            } else {
                bestMethod = method;
            }
        }
        if (VERBOSE) Log.v(TAG, "Returning " + bestMethod);
        return bestMethod;
    }

    private static void sendError(BroadcastReceiver receiver, Exception e) {
        Log.e(TAG, "Exception handling wrapped DPC call" , e);
        sendNoLog(receiver, RESULT_EXCEPTION, e);
    }

    private static void sendResult(BroadcastReceiver receiver, Object result) {
        sendNoLog(receiver, RESULT_OK, result);
        if (VERBOSE) Log.v(TAG, "Sent");
    }

    private static void sendNoLog(BroadcastReceiver receiver, int code, Object result) {
        if (VERBOSE) {
            Log.v(TAG, "Sending " + TestAppSystemServiceFactory.resultCodeToString(code)
                    + " (result='" + result + "') to " + receiver + " on "
                    + Thread.currentThread());
        }
        receiver.setResultCode(code);
        if (result != null) {
            Intent intent = new Intent();
            addArg(intent, new Object[] { result }, /* index= */ 0);
            receiver.setResultExtras(intent.getExtras());
        }
    }

    private DeviceOwnerHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
