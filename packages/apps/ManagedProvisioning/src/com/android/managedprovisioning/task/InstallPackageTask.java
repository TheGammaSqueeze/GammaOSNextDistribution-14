/*
 * Copyright 2014, The Android Open Source Project
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
package com.android.managedprovisioning.task;

import static android.app.PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT;
import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageManager.INSTALL_REPLACE_EXISTING;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_INSTALL_PACKAGE_TASK_MS;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import androidx.core.os.BuildCompat;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;


/**
 * Installs the management app apk from a download location provided by
 * {@link PackageLocationProvider#getPackageLocation()}.
 */
public class InstallPackageTask extends AbstractProvisioningTask {
    private static final String ACTION_INSTALL_DONE = InstallPackageTask.class.getName() + ".DONE.";

    public static final int ERROR_PACKAGE_INVALID = 0;
    public static final int ERROR_INSTALLATION_FAILED = 1;

    private final PackageLocationProvider mPackageLocationProvider;

    private final PackageManager mPm;
    private final DevicePolicyManager mDpm;
    private final PackageInstaller.SessionCallback mSessionCallback =  new SessionCallback();
    private final String mPackageName;
    private final Utils mUtils;
    private int mSessionId = -1;

    private static final int SUCCESS_INSTALLED_BROADCAST = 1;
    private static final int SUCCESS_INSTALLED_CALLBACK = 2;
    private final Set<Integer> mSuccessCodes = new HashSet<>();

    /**
     * Create an InstallPackageTask. When run, this will attempt to install the device admin package
     * if it is non-null.
     *
     * {@see #run(String, String)} for more detail on package installation.
     */
    public InstallPackageTask(
            PackageLocationProvider packageLocationProvider,
            Context context,
            ProvisioningParams params,
            Callback callback,
            String packageName) {
        this(packageLocationProvider, context, params, callback,
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)),
                new Utils(),
                packageName);
    }

    @VisibleForTesting
    InstallPackageTask(
            PackageLocationProvider packageLocationProvider,
            Context context,
            ProvisioningParams params,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            Utils utils,
            String packageName) {
        super(context, params, callback, provisioningAnalyticsTracker);

        mPm = context.getPackageManager();
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mPackageLocationProvider = requireNonNull(packageLocationProvider);
        mPackageName = requireNonNull(packageName);
        mUtils = requireNonNull(utils);
    }

    private static void copyStream(@NonNull InputStream in, @NonNull OutputStream out)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int numRead;
        while ((numRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, numRead);
        }
    }

    /**
     * Installs a package. The package will be installed from the given location if one is provided.
     * If a null or empty location is provided, and the package is installed for a different user,
     * it will be enabled for the calling user. If the package location is not provided and the
     * package is not installed for any other users, this task will produce an error.
     *
     * Errors will be indicated if a downloaded package is invalid, or installation fails.
     */
    @Override
    public void run(int userId) {
        startTaskTimer();

        File packageLocation = mPackageLocationProvider.getPackageLocation();
        ProvisionLogger.logi("Installing package " + mPackageName + " on user " + userId + " from "
                + packageLocation);
        if (packageLocation == null) {
            success();
            return;
        }

        int installFlags = INSTALL_REPLACE_EXISTING;
        // Current device owner (if exists) must be test-only, so it is fine to replace it with a
        // test-only package of same package name. No need to further verify signature as
        // installation will fail if signatures don't match.
        if (mDpm.isDeviceOwnerApp(mPackageName)) {
            installFlags |= PackageManager.INSTALL_ALLOW_TEST;
        }

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.installFlags |= installFlags;

        try {
            installPackage(packageLocation, mPackageName, params, mContext, mSessionCallback);
        } catch (IOException e) {
            ProvisionLogger.loge("Installing package " + mPackageName + " failed.", e);
            error(ERROR_INSTALLATION_FAILED);
        } finally {
            packageLocation.delete();
        }
    }
    /*
    The reason why we have both SessionCallback and BroadcastReceiver is as follows:
    Initially we were just listening for the ACTION_INSTALL_DONE broadcast
    but this was being received too early causing the bug(b/185897624).
    In (ag/14971042) we reworked the logic to register for session callback,
    it still ran into the race condition(b/165012101) causing the bug(b/15160090)
    But it was still fine to keep that logic since it's the recommended approach.
    In current state(ag/15160090), we've now added Intent#ACTION_PACKAGE_ADDED receiver
    as that's the latest possible callback.
    */
    private void installPackage(
            File source,
            String packageName,
            PackageInstaller.SessionParams params,
            Context context,
            PackageInstaller.SessionCallback sessionCallback)
            throws IOException {
        PackageInstaller pi = context.getPackageManager().getPackageInstaller();
        context.registerReceiver(
                new PackageAddedReceiver(packageName),
                createPackageAddedIntentFilter(), Context.RECEIVER_EXPORTED/*UNAUDITED*/);
        pi.registerSessionCallback(sessionCallback);
        mSessionId  = pi.createSession(params);
        try (PackageInstaller.Session session = pi.openSession(mSessionId)) {
            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = session.openWrite(source.getName(), 0, -1)) {
                copyStream(in, out);
            } catch (IOException e) {
                session.abandon();
                throw e;
            }

            String action = ACTION_INSTALL_DONE + mSessionId;
            int pendingIntentFlags = FLAG_ONE_SHOT | FLAG_UPDATE_CURRENT | FLAG_MUTABLE;
            if (BuildCompat.isAtLeastU()) {
                pendingIntentFlags |= PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT;
            }
            PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context, mSessionId, new Intent(action), pendingIntentFlags);

            session.commit(pendingIntent.getIntentSender());
        }
    }

    private IntentFilter createPackageAddedIntentFilter() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        return intentFilter;
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_INSTALL_PACKAGE_TASK_MS;
    }

    private void addSuccessStatus(int successStatus) {
        mSuccessCodes.add(successStatus);
        if (mSuccessCodes.contains(SUCCESS_INSTALLED_BROADCAST)
                && mSuccessCodes.contains(SUCCESS_INSTALLED_CALLBACK)) {
            ProvisionLogger.logd("Package " + mPackageName + " is successfully installed.");
            stopTaskTimer();
            success();
        }
    }

    private class PackageAddedReceiver extends BroadcastReceiver {

        private final String mPackageName;

        PackageAddedReceiver(String packageName) {
            mPackageName = requireNonNull(packageName);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ProvisionLogger.logd("PACKAGE_ADDED broadcast received with intent data "
                    + intent.getDataString());
            if (!mPackageName.equals(extractPackageNameFromDataString(intent.getDataString()))) {
                ProvisionLogger.logd("The package name provided in the intent data does not equal "
                        + mPackageName);
                return;
            }
            addSuccessStatus(SUCCESS_INSTALLED_BROADCAST);
            context.unregisterReceiver(this);
        }

        private String extractPackageNameFromDataString(String dataString) {
            return dataString.substring("package:".length());
        }
    }

    private class SessionCallback extends PackageInstaller.SessionCallback {

        @Override
        public void onCreated(int sessionId) {}

        @Override
        public void onBadgingChanged(int sessionId) {}

        @Override
        public void onActiveChanged(int sessionId, boolean active) {}

        @Override
        public void onProgressChanged(int sessionId, float progress) {}

        @Override
        public void onFinished(int sessionId, boolean success) {
            if (sessionId != mSessionId) {
                return;
            }
            PackageInstaller packageInstaller = mPm.getPackageInstaller();
            packageInstaller.unregisterSessionCallback(mSessionCallback);
            if (!success) {
                boolean packageInstalled =
                        mUtils.isPackageInstalled(mPackageName, mContext.getPackageManager());
                if (packageInstalled) {
                    ProvisionLogger.logd("Current version of " + mPackageName
                            + " higher than the version to be installed. It was not reinstalled.");
                    // If the package is already at a higher version: success.
                    // Do not log time if package is already at a higher version, as that isn't
                    // useful.
                    success();
                    return;
                } else {
                    ProvisionLogger.logd("Installing package " + mPackageName + " failed.");
                    error(ERROR_INSTALLATION_FAILED);
                    return;
                }
            }
            ProvisionLogger.logd("Install package callback received for " + mPackageName);
            addSuccessStatus(SUCCESS_INSTALLED_CALLBACK);
        }
    }
}
