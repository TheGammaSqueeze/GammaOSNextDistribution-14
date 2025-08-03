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

package com.android.server.sdksandbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.OutcomeReceiver;
import android.os.Process;
import android.provider.DeviceConfig;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.sdksandbox.verifier.SdkDexVerifier;

/**
 * Broadcast Receiver for receiving new Sdk install requests and verifying Sdk code before running
 * it in Sandbox.
 *
 * @hide
 */
public class SdkSandboxVerifierReceiver extends BroadcastReceiver {

    private static final String TAG = "SdkSandboxVerifier";
    private Handler mHandler;
    private SdkDexVerifier mSdkDexVerifier;

    public SdkSandboxVerifierReceiver() {
        HandlerThread handlerThread =
                new HandlerThread("DexVerifierHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_NEEDS_VERIFICATION.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "Received sdk sandbox verification intent " + intent.toString());
        Log.d(TAG, "Extras " + intent.getExtras());

        verifySdkHandler(context, intent, mHandler);
    }

    @VisibleForTesting
    void setSdkDexVerifier(SdkDexVerifier sdkDexVerifier) {
        mSdkDexVerifier = sdkDexVerifier;
    }

    @VisibleForTesting
    void verifySdkHandler(Context context, Intent intent, Handler handler) {
        int verificationId = intent.getIntExtra(PackageManager.EXTRA_VERIFICATION_ID, -1);

        boolean enforceRestrictions =
                DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        SdkSandboxManagerService.PROPERTY_ENFORCE_RESTRICTIONS,
                        SdkSandboxManagerService.DEFAULT_VALUE_ENFORCE_RESTRICTIONS);
        if (!enforceRestrictions) {
            context.getPackageManager()
                    .verifyPendingInstall(verificationId, PackageManager.VERIFICATION_ALLOW);
            Log.d(TAG, "Restrictions disabled. Sent VERIFICATION_ALLOW");
            return;
        }

        String apkPath = intent.getData() != null ? intent.getData().getPath() : null;

        PackageInfo packageInfo =
                apkPath != null
                        ? context.getPackageManager().getPackageArchiveInfo(apkPath, /* flags */ 0)
                        : null;

        if (packageInfo == null) {
            Log.e(TAG, "Package data to verify was absent or invalid.");
            context.getPackageManager()
                    .verifyPendingInstall(verificationId, PackageManager.VERIFICATION_REJECT);
            return;
        }

        if (mSdkDexVerifier == null) {
            mSdkDexVerifier = SdkDexVerifier.getInstance();
        }
        int targetSdkVersion =
                packageInfo.applicationInfo != null
                        ? packageInfo.applicationInfo.targetSdkVersion
                        : Build.VERSION.SDK_INT;
        handler.post(
                () ->
                        mSdkDexVerifier.startDexVerification(
                                apkPath,
                                packageInfo.packageName,
                                targetSdkVersion,
                                new OutcomeReceiver<Void, Exception>() {
                                    @Override
                                    public void onResult(Void result) {}

                                    @Override
                                    public void onError(Exception e) {
                                        Log.e(TAG, "Error at SdkSandboxVerifierReceiver", e);
                                    }
                                }));

        // Verification will continue to run on background, return VERIFICATION_ALLOW to
        // unblock install
        context.getPackageManager()
                .verifyPendingInstall(verificationId, PackageManager.VERIFICATION_ALLOW);
        Log.d(TAG, "Sent VERIFICATION_ALLOW");
    }
}
