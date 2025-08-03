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

package android.telephony.ims.cts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.service.carrier.CarrierService;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ImsUtils {
    public static final boolean VDBG = true;

    // ImsService rebind has an exponential backoff capping at 64 seconds. Wait for 70 seconds to
    // allow for the new poll to happen in the framework.
    public static final int TEST_TIMEOUT_MS = 70000;

    // Id for non compressed auto configuration xml.
    public static final int ITEM_NON_COMPRESSED = 2000;
    // Id for compressed auto configuration xml.
    public static final int ITEM_COMPRESSED = 2001;

    private static final String TAG = "ImsUtils";

    public static boolean shouldTestTelephony() {
        try {
            InstrumentationRegistry.getInstrumentation().getContext()
                    .getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            return false;
        }
        final PackageManager pm = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    public static boolean shouldTestImsService() {
        boolean hasIms = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
        return shouldTestTelephony() && hasIms;
    }

    public static boolean shouldTestImsCall() {
        final PackageManager pm = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS)
                && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING);
    }

    public static boolean shouldTestImsSingleRegistration() {
        boolean hasSingleReg = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION);
        return shouldTestTelephony() && hasSingleReg;
    }

    public static int getPreferredActiveSubId() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SubscriptionManager sm = (SubscriptionManager) context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> infos = ShellIdentityUtils.invokeMethodWithShellPermissions(sm,
                SubscriptionManager::getActiveSubscriptionInfoList);

        int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        // Couldn't resolve a default. We can try to resolve a default using the active
        // subscriptions.
        if (!infos.isEmpty()) {
            return infos.get(0).getSubscriptionId();
        }
        // There must be at least one active subscription.
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static boolean isSubIdInInfoList(List<SubscriptionInfo> infos, int subId) {
        return infos.stream().anyMatch(info -> info.getSubscriptionId() == subId);
    }

    /**
     * If a carrier app implements CarrierMessagingService it can choose to take care of handling
     * SMS OTT so SMS over IMS APIs won't be triggered which would be WAI so we do not run the tests
     * if there exist a carrier app that declares a CarrierMessagingService
     */
    public static boolean shouldRunSmsImsTests(int subId) {
        if (!shouldTestImsService()) {
            return false;
        }
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        TelephonyManager tm =
                (TelephonyManager) InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(Context.TELEPHONY_SERVICE);
        tm = tm.createForSubscriptionId(subId);
        final long token = Binder.clearCallingIdentity();
        List<String> carrierPackages;
        try {
            carrierPackages = ShellIdentityUtils.invokeMethodWithShellPermissions(tm,
                    (m) -> m.getCarrierPackageNamesForIntent(
                            new Intent(CarrierService.CARRIER_SERVICE_INTERFACE)));
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        final PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent("android.service.carrier.CarrierMessagingService");
        List<ResolveInfo> resolveInfos = packageManager.queryIntentServices(intent, 0);
        boolean detected = resolveInfos != null && !resolveInfos.isEmpty();
        Log.i(TAG, "resolveInfos are detected: " + detected);

        boolean exist = carrierPackages != null && !carrierPackages.isEmpty();
        Log.i(TAG, "carrierPackages exist: " + exist);

        if (!exist) {
            return true;
        }

        for (ResolveInfo info : resolveInfos) {
            if (carrierPackages.contains(info.serviceInfo.packageName)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Retry every 5 seconds until the condition is true or fail after TEST_TIMEOUT_MS seconds.
     */
    public static boolean retryUntilTrue(Callable<Boolean> condition) throws Exception {
        return retryUntilTrue(condition, TEST_TIMEOUT_MS, 14 /*numTries*/);
    }

    /**
     * Retry every timeoutMs/numTimes until the condition is true or fail if the condition is never
     * met.
     */
    public static boolean retryUntilTrue(Callable<Boolean> condition,
            int timeoutMs, int numTimes) throws Exception {
        int sleepTime = timeoutMs / numTimes;
        int retryCounter = 0;
        while (retryCounter < numTimes) {
            try {
                Boolean isSuccessful = condition.call();
                isSuccessful = (isSuccessful == null) ? false : isSuccessful;
                if (isSuccessful) return true;
            } catch (Exception e) {
                // we will retry
            }
            Thread.sleep(sleepTime);
            retryCounter++;
        }
        return false;
    }

    /**
     * compress the gzip format data
     * @hide
     */
    public static byte[] compressGzip(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        byte[] out = null;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            GZIPOutputStream gzipCompressingStream =
                    new GZIPOutputStream(outputStream);
            gzipCompressingStream.write(data);
            gzipCompressingStream.close();
            out = outputStream.toByteArray();
            outputStream.close();
        } catch (IOException e) {
        }
        return out;
    }

    /**
     * decompress the gzip format data
     * @hide
     */
    public static byte[] decompressGzip(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        byte[] out = null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            GZIPInputStream gzipDecompressingStream =
                    new GZIPInputStream(inputStream);
            byte[] buf = new byte[1024];
            int size = gzipDecompressingStream.read(buf);
            while (size >= 0) {
                outputStream.write(buf, 0, size);
                size = gzipDecompressingStream.read(buf);
            }
            gzipDecompressingStream.close();
            inputStream.close();
            out = outputStream.toByteArray();
            outputStream.close();
        } catch (IOException e) {
        }
        return out;
    }

    public static  void waitInCurrentState(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (Exception e) {
            Log.d(TAG, "InterruptedException");
        }
    }
}
