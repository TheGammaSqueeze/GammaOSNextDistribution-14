/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.stats;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.provider.DeviceConfig.NAMESPACE_STATSD_JAVA;
import static android.provider.DeviceConfig.Properties;

import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.app.StatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IStatsCompanionService;
import android.os.IStatsd;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.StatsFrameworkInitializer;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.util.Log;
import android.util.PropertyParcel;
import android.util.proto.ProtoOutputStream;
import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.stats.StatsHelper;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper service for statsd (the native stats management service in cmds/statsd/).
 * Used for registering and receiving alarms on behalf of statsd.
 *
 * @hide
 */
public class StatsCompanionService extends IStatsCompanionService.Stub {

    private static final long MILLIS_IN_A_DAY = TimeUnit.DAYS.toMillis(1);

    public static final String RESULT_RECEIVER_CONTROLLER_KEY = "controller_activity";
    public static final String CONFIG_DIR = "/data/misc/stats-service";

    static final String TAG = "StatsCompanionService";
    static final boolean DEBUG = false;
    /**
     * Hard coded field ids of frameworks/base/cmds/statsd/src/uid_data.proto
     * to be used in ProtoOutputStream.
     */
    private static final int APPLICATION_INFO_FIELD_ID = 1;
    private static final int UID_FIELD_ID = 1;
    private static final int VERSION_FIELD_ID = 2;
    private static final int VERSION_STRING_FIELD_ID = 3;
    private static final int PACKAGE_NAME_FIELD_ID = 4;
    private static final int INSTALLER_FIELD_ID = 5;
    private static final int CERTIFICATE_HASH_FIELD_ID = 6;

    public static final int DEATH_THRESHOLD = 10;

    private final Context mContext;
    private final AlarmManager mAlarmManager;
    @GuardedBy("sStatsdLock")
    private static IStatsd sStatsd;
    private static final Object sStatsdLock = new Object();

    private final OnAlarmListener mPullingAlarmListener;
    private final OnAlarmListener mPeriodicAlarmListener;

    private StatsManagerService mStatsManagerService;

    @GuardedBy("sStatsdLock")
    private final HashSet<Long> mDeathTimeMillis = new HashSet<>();
    @GuardedBy("sStatsdLock")
    private final HashMap<Long, String> mDeletedFiles = new HashMap<>();
    private final Handler mHandler;

    // Flag that is set when PHASE_BOOT_COMPLETED is triggered in the StatsCompanion lifecycle.
    private AtomicBoolean mBootCompleted = new AtomicBoolean(false);

    public StatsCompanionService(Context context) {
        super();
        mContext = context;
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        if (DEBUG) Log.d(TAG, "Registered receiver for ACTION_PACKAGE_REPLACED and ADDED.");
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mPullingAlarmListener = new PullingAlarmListener(context);
        mPeriodicAlarmListener = new PeriodicAlarmListener(context);
    }

    /**
     * Non-blocking call to retrieve a reference to statsd
     *
     * @return IStatsd object if statsd is ready, null otherwise.
     */
    private static IStatsd getStatsdNonblocking() {
        synchronized (sStatsdLock) {
            return sStatsd;
        }
    }

    private static String getInstallerPackageName(PackageManager pm, String name) {
        InstallSourceInfo installSourceInfo = null;
        try {
            installSourceInfo = pm.getInstallSourceInfo(name);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get installer for package: " + name, e);
        }

        String installerPackageName = null;
        if (installSourceInfo != null) {
            installerPackageName = installSourceInfo.getInitiatingPackageName();
            if (installerPackageName == null || installerPackageName.equals("com.android.shell")) {
                installerPackageName = installSourceInfo.getInstallingPackageName();
            }
        }

        return installerPackageName == null ? "" : installerPackageName;
    }

    private static byte[] getPackageCertificateHash(final SigningInfo si) {
        if (si == null) {
            return new byte[0];
        }

        final Signature[] signatures = si.getApkContentsSigners();
        if (signatures == null || signatures.length < 1) {
            return new byte[0];
        }

        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to get SHA-256 instance of MessageDigest", e);
            return new byte[0];
        }

        Arrays.sort(signatures, Comparator.comparing(Signature::hashCode));
        for (final Signature signature : signatures) {
            messageDigest.update(signature.toByteArray());
        }

        return messageDigest.digest();
    }

    private static void informAllUids(Context context) {
        ParcelFileDescriptor[] fds;
        try {
            fds = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create a pipe to send uid map data.", e);
            return;
        }
        HandlerThread backgroundThread = new HandlerThread(
                "statsCompanionService.bg", THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        Handler handler = new Handler(backgroundThread.getLooper());
        handler.post(() -> {
            if (DEBUG) Log.d(TAG, "Start thread for sending uid map data.");
            UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
            PackageManager pm = context.getPackageManager();
            final List<UserHandle> users = um.getUserHandles(true);
            if (DEBUG) {
                Log.d(TAG, "Iterating over " + users.size() + " userHandles.");
            }
            IStatsd statsd = getStatsdNonblocking();
            if (statsd == null) {
                return;
            }
            FileOutputStream fout = new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]);
            try {
                ProtoOutputStream output = new ProtoOutputStream(fout);
                int numRecords = 0;

                // Add in all the apps for every user/profile.
                for (UserHandle userHandle : users) {
                    List<PackageInfo> packagesPlusApex = getAllPackagesWithApex(pm, userHandle);
                    for (int j = 0; j < packagesPlusApex.size(); j++) {
                        if (packagesPlusApex.get(j).applicationInfo != null) {
                            final String installer = getInstallerPackageName(
                                    pm, packagesPlusApex.get(j).packageName);

                            long applicationInfoToken =
                                    output.start(ProtoOutputStream.FIELD_TYPE_MESSAGE
                                            | ProtoOutputStream.FIELD_COUNT_REPEATED
                                            | APPLICATION_INFO_FIELD_ID);
                            output.write(ProtoOutputStream.FIELD_TYPE_INT32
                                            | ProtoOutputStream.FIELD_COUNT_SINGLE | UID_FIELD_ID,
                                    packagesPlusApex.get(j).applicationInfo.uid);
                            output.write(ProtoOutputStream.FIELD_TYPE_INT64
                                            | ProtoOutputStream.FIELD_COUNT_SINGLE
                                            | VERSION_FIELD_ID,
                                    packagesPlusApex.get(j).getLongVersionCode());
                            output.write(ProtoOutputStream.FIELD_TYPE_STRING
                                            | ProtoOutputStream.FIELD_COUNT_SINGLE
                                            | VERSION_STRING_FIELD_ID,
                                    packagesPlusApex.get(j).versionName);
                            output.write(ProtoOutputStream.FIELD_TYPE_STRING
                                    | ProtoOutputStream.FIELD_COUNT_SINGLE
                                    | PACKAGE_NAME_FIELD_ID, packagesPlusApex.get(j).packageName);
                            output.write(ProtoOutputStream.FIELD_TYPE_STRING
                                            | ProtoOutputStream.FIELD_COUNT_SINGLE
                                            | INSTALLER_FIELD_ID,
                                    installer);
                            final byte[] certHash =
                                getPackageCertificateHash(packagesPlusApex.get(j).signingInfo);
                            output.write(ProtoOutputStream.FIELD_TYPE_BYTES
                                    | ProtoOutputStream.FIELD_COUNT_SINGLE
                                    | CERTIFICATE_HASH_FIELD_ID,
                                certHash);

                            numRecords++;
                            output.end(applicationInfoToken);
                        }
                    }
                }
                try {
                    // inform statsd about data is ready to be consumed to avoid blocking in
                    // statsd while reading & in this thread while writing (see flush below)
                    statsd.informAllUidData(fds[0]);
                    // close read fd since it is duped by binder transaction
                    fds[0].close();
                    output.flush();
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send uid map to statsd");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close the read side of the pipe.", e);
                }
                if (DEBUG) {
                    Log.d(TAG, "Sent data for " + numRecords + " apps");
                }
            } finally {
                if (DEBUG) Log.d(TAG, "End thread for sending uid map data.");
                FileUtils.closeQuietly(fout);
                backgroundThread.quit();
            }
        });
    }

    private static List<PackageInfo> getAllPackagesWithApex(PackageManager pm,
            UserHandle userHandle) {
        // We want all the uninstalled packages because uninstalled package uids can still be logged
        // to statsd.
        List<PackageInfo> allPackages = new ArrayList<>(
                pm.getInstalledPackagesAsUser(PackageManager.GET_SIGNING_CERTIFICATES
                                | PackageManager.MATCH_UNINSTALLED_PACKAGES
                                | PackageManager.MATCH_ANY_USER
                                | PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                        userHandle.getIdentifier()));
        // We make a second query to package manager for the apex modules because package manager
        // returns both installed and uninstalled apexes with
        // PackageManager.MATCH_UNINSTALLED_PACKAGES flag. We only want active apexes because
        // inactive apexes can conflict with active ones.
        for (PackageInfo packageInfo : pm.getInstalledPackages(PackageManager.MATCH_APEX)) {
            if (packageInfo.isApex) {
                allPackages.add(packageInfo);
            }
        }
        return allPackages;
    }

    private static class WakelockThread extends Thread {
        private final PowerManager.WakeLock mWl;
        private final Runnable mRunnable;

        WakelockThread(Context context, String wakelockName, Runnable runnable) {
            PowerManager powerManager = (PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
            mWl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakelockName);
            mRunnable = runnable;
        }
        @Override
        public void run() {
            try {
                mRunnable.run();
            } finally {
                mWl.release();
            }
        }
        @Override
        public void start() {
            mWl.acquire();
            super.start();
        }
    }

    private final static class AppUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            /**
             * App updates actually consist of REMOVE, ADD, and then REPLACE broadcasts. To avoid
             * waste, we ignore the REMOVE and ADD broadcasts that contain the replacing flag.
             * If we can't find the value for EXTRA_REPLACING, we default to false.
             */
            if (!intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return; // Keep only replacing or normal add and remove.
            }
            if (DEBUG) Log.d(TAG, "StatsCompanionService noticed an app was updated.");
            synchronized (sStatsdLock) {
                if (sStatsd == null) {
                    Log.w(TAG, "Could not access statsd to inform it of an app update");
                    return;
                }
                try {
                    if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                        Bundle b = intent.getExtras();
                        int uid = b.getInt(Intent.EXTRA_UID);
                        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                        if (!replacing) {
                            // Don't bother sending an update if we're right about to get another
                            // intent for the new version that's added.
                            String app = intent.getData().getSchemeSpecificPart();
                            sStatsd.informOnePackageRemoved(app, uid);
                        }
                    } else {
                        PackageManager pm = context.getPackageManager();
                        Bundle b = intent.getExtras();
                        int uid = b.getInt(Intent.EXTRA_UID);
                        String app = intent.getData().getSchemeSpecificPart();
                        PackageInfo pi = pm.getPackageInfo(app,
                                    PackageManager.GET_SIGNING_CERTIFICATES
                                    | PackageManager.MATCH_ANY_USER);
                        final String installer = getInstallerPackageName(pm, app);

                        // Get Package certificate hash.
                        byte[] certHash = getPackageCertificateHash(pi.signingInfo);

                        sStatsd.informOnePackage(
                                app,
                                uid,
                                pi.getLongVersionCode(),
                                pi.versionName == null ? "" : pi.versionName,
                                installer,
                                certHash);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to inform statsd of an app update", e);
                }
            }
        }
    }

    private static final class UserUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Pull the latest state of UID->app name, version mapping.
            // Needed since the new user basically has a version of every app.
            informAllUids(context);
        }
    }

    public final static class PullingAlarmListener implements OnAlarmListener {
        private final Context mContext;

        PullingAlarmListener(Context context) {
            mContext = context;
        }

        @Override
        public void onAlarm() {
            if (DEBUG) {
                Log.d(TAG, "Time to poll something.");
            }
            IStatsd statsd = getStatsdNonblocking();
            if (statsd == null) {
                Log.w(TAG, "Could not access statsd to inform it of pulling alarm firing.");
                return;
            }

            // Wakelock needs to be retained while calling statsd.
            Thread thread = new WakelockThread(mContext,
                    PullingAlarmListener.class.getCanonicalName(), new Runnable() {
                        @Override
                        public void run() {
                            try {
                                statsd.informPollAlarmFired();
                            } catch (RemoteException e) {
                                Log.w(TAG, "Failed to inform statsd of pulling alarm firing.", e);
                            }
                        }
                    });
            thread.start();
        }
    }

    public final static class PeriodicAlarmListener implements OnAlarmListener {
        private final Context mContext;

        PeriodicAlarmListener(Context context) {
            mContext = context;
        }

        @Override
        public void onAlarm() {
            if (DEBUG) {
                Log.d(TAG, "Time to trigger periodic alarm.");
            }
            IStatsd statsd = getStatsdNonblocking();
            if (statsd == null) {
                Log.w(TAG, "Could not access statsd to inform it of periodic alarm firing.");
                return;
            }

            // Wakelock needs to be retained while calling statsd.
            Thread thread = new WakelockThread(mContext,
                    PeriodicAlarmListener.class.getCanonicalName(), new Runnable() {
                        @Override
                        public void run() {
                            try {
                                statsd.informAlarmForSubscriberTriggeringFired();
                            } catch (RemoteException e) {
                                Log.w(TAG, "Failed to inform statsd of periodic alarm firing.", e);
                            }
                        }
                    });
            thread.start();
        }
    }

    public final static class ShutdownEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            /**
             * Skip immediately if intent is not relevant to device shutdown.
             */
            if (!intent.getAction().equals(Intent.ACTION_REBOOT)
                    && !(intent.getAction().equals(Intent.ACTION_SHUTDOWN)
                    && (intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0)) {
                return;
            }

            if (DEBUG) {
                Log.i(TAG, "StatsCompanionService noticed a shutdown.");
            }
            IStatsd statsd = getStatsdNonblocking();
            if (statsd == null) {
                Log.w(TAG, "Could not access statsd to inform it of a shutdown event.");
                return;
            }
            try {
                // two way binder call
                statsd.informDeviceShutdown();
            } catch (Exception e) {
                Log.w(TAG, "Failed to inform statsd of a shutdown event.", e);
            }
        }
    }

    @Override // Binder call
    // Unused, but keep the IPC due to the bootstrap apex issue on R.
    public void setAnomalyAlarm(long timestampMs) {}

    @Override // Binder call
    // Unused, but keep the IPC due to the bootstrap apex issue on R.
    public void cancelAnomalyAlarm() {}

    @Override // Binder call
    public void setAlarmForSubscriberTriggering(long timestampMs) {
        StatsCompanion.enforceStatsdCallingUid();
        if (DEBUG) {
            Log.d(TAG,
                    "Setting periodic alarm in about " + (timestampMs
                            - SystemClock.elapsedRealtime()));
        }
        final long callingToken = Binder.clearCallingIdentity();
        try {
            // using ELAPSED_REALTIME, not ELAPSED_REALTIME_WAKEUP, so if device is asleep, will
            // only fire when it awakens.
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME, timestampMs, TAG + ".periodic",
                    mPeriodicAlarmListener, mHandler);
        } finally {
            Binder.restoreCallingIdentity(callingToken);
        }
    }

    @Override // Binder call
    public void cancelAlarmForSubscriberTriggering() {
        StatsCompanion.enforceStatsdCallingUid();
        if (DEBUG) {
            Log.d(TAG, "Cancelling periodic alarm");
        }
        final long callingToken = Binder.clearCallingIdentity();
        try {
            mAlarmManager.cancel(mPeriodicAlarmListener);
        } finally {
            Binder.restoreCallingIdentity(callingToken);
        }
    }

    @Override // Binder call
    public void setPullingAlarm(long nextPullTimeMs) {
        StatsCompanion.enforceStatsdCallingUid();
        if (DEBUG) {
            Log.d(TAG, "Setting pulling alarm in about "
                    + (nextPullTimeMs - SystemClock.elapsedRealtime()));
        }
        final long callingToken = Binder.clearCallingIdentity();
        try {
            // using ELAPSED_REALTIME, not ELAPSED_REALTIME_WAKEUP, so if device is asleep, will
            // only fire when it awakens.
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME, nextPullTimeMs, TAG + ".pull",
                    mPullingAlarmListener, mHandler);
        } finally {
            Binder.restoreCallingIdentity(callingToken);
        }
    }

    @Override // Binder call
    public void cancelPullingAlarm() {
        StatsCompanion.enforceStatsdCallingUid();
        if (DEBUG) {
            Log.d(TAG, "Cancelling pulling alarm");
        }
        final long callingToken = Binder.clearCallingIdentity();
        try {
            mAlarmManager.cancel(mPullingAlarmListener);
        } finally {
            Binder.restoreCallingIdentity(callingToken);
        }
    }

    @Override // Binder call
    public void statsdReady() {
        StatsCompanion.enforceStatsdCallingUid();
        if (DEBUG) {
            Log.d(TAG, "learned that statsdReady");
        }
        sayHiToStatsd(); // tell statsd that we're ready too and link to it

        if (SdkLevel.isAtLeastS()) {
            StatsHelper.sendStatsdReadyBroadcast(mContext);
        } else {
            sendStatsdStartedDirectedBroadcast();
        }
    }

    /**
     * Sends directed broadcasts to all receivers interested in ACTION_STATSD_STARTED broadcast.
     *
     * Only use this on R- platform.
     * Use {@link android.stats.StatsHelper.sendStatsdReadyBroadcast(Context context)} on S+.
     **/
    private void sendStatsdStartedDirectedBroadcast() {
        final Intent intent = new Intent(StatsManager.ACTION_STATSD_STARTED);
        // Retrieve list of broadcast receivers for this broadcast & send them directed broadcasts
        // to wake them up (if they're in background).
        List<ResolveInfo> resolveInfos =
                mContext.getPackageManager().queryBroadcastReceiversAsUser(
                        intent, 0, UserHandle.SYSTEM);
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            return; // No need to send broadcast.
        }

        for (ResolveInfo resolveInfo : resolveInfos) {
            Intent intentToSend = new Intent(intent);
            intentToSend.setComponent(new ComponentName(
                    resolveInfo.activityInfo.applicationInfo.packageName,
                    resolveInfo.activityInfo.name));
            mContext.sendBroadcastAsUser(intentToSend, UserHandle.SYSTEM,
                    android.Manifest.permission.DUMP);
        }
    }

    @Override // Binder call
    public boolean checkPermission(String permission, int pid, int uid) {
        StatsCompanion.enforceStatsdCallingUid();
        return mContext.checkPermission(permission, pid, uid) == PackageManager.PERMISSION_GRANTED;
    }

    // Statsd related code

    /**
     * Fetches the statsd IBinder service. This is a blocking call that always refetches statsd
     * instead of returning the cached sStatsd.
     * Note: This should only be called from {@link #sayHiToStatsd()}. All other clients should use
     * the cached sStatsd via {@link #getStatsdNonblocking()}.
     */
    private IStatsd fetchStatsdServiceLocked() {
        sStatsd = IStatsd.Stub.asInterface(StatsFrameworkInitializer
                .getStatsServiceManager()
                .getStatsdServiceRegisterer()
                .get());
        return sStatsd;
    }

    private void registerStatsdDeathRecipient(IStatsd statsd, List<BroadcastReceiver> receivers) {
        StatsdDeathRecipient deathRecipient = new StatsdDeathRecipient(statsd, receivers);

        try {
            statsd.asBinder().linkToDeath(deathRecipient, /*flags=*/0);
        } catch (RemoteException e) {
            Log.e(TAG, "linkToDeath (StatsdDeathRecipient) failed");
            // Statsd has already died. Unregister receivers ourselves.
            for (BroadcastReceiver receiver : receivers) {
                mContext.unregisterReceiver(receiver);
            }
            synchronized (sStatsdLock) {
                if (statsd == sStatsd) {
                    statsdNotReadyLocked();
                }
            }
        }
    }

    /**
     * Now that the android system is ready, StatsCompanion is ready too, so inform statsd.
     */
    void systemReady() {
        if (DEBUG) Log.d(TAG, "Learned that systemReady");
        sayHiToStatsd();
    }

    void setStatsManagerService(StatsManagerService statsManagerService) {
        mStatsManagerService = statsManagerService;
    }

    private void onPropertiesChanged(final Properties properties) {
        updateProperties(properties);
    }

    private void updateProperties(final Properties properties) {
        if (DEBUG) {
            Log.d(TAG, "statsd_java properties updated");
        }

        final Set<String> propertyNames = properties.getKeyset();
        if (propertyNames.isEmpty()) {
            return;
        }

        final PropertyParcel[] propertyParcels = new PropertyParcel[propertyNames.size()];
        int index = 0;
        for (final String propertyName : propertyNames) {
            propertyParcels[index] = new PropertyParcel();
            propertyParcels[index].property = propertyName;
            propertyParcels[index].value = properties.getString(propertyName, null);
            index++;
        }

        final IStatsd statsd = getStatsdNonblocking();
        if (statsd == null) {
            Log.w(TAG, "Could not access statsd to inform it of updated statsd_java properties");
            return;
        }

        try {
            statsd.updateProperties(propertyParcels);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to inform statsd of updated statsd_java properties", e);
        }
    }

    /**
     * Tells statsd that statscompanion is ready. If the binder call returns, link to
     * statsd.
     */
    private void sayHiToStatsd() {
        IStatsd statsd;
        synchronized (sStatsdLock) {
            if (sStatsd != null && sStatsd.asBinder().isBinderAlive()) {
                Log.e(TAG, "statsd has already been fetched before",
                        new IllegalStateException("IStatsd object should be null or dead"));
                return;
            }
            statsd = fetchStatsdServiceLocked();
        }

        if (statsd == null) {
            Log.i(TAG, "Could not yet find statsd to tell it that StatsCompanion is alive.");
            return;
        }

        // Cleann up from previous statsd - cancel any alarms that had been set.
        // Do this here instead of in binder death because statsd can come back
        // and set different alarms, or not want to set an alarm when it had
        // been set. This guarantees that when we get a new statsd, we cancel
        // any alarms before it is able to set them.
        cancelPullingAlarm();
        cancelAlarmForSubscriberTriggering();

        if (DEBUG) Log.d(TAG, "Saying hi to statsd");
        mStatsManagerService.statsdReady(statsd);
        try {
            statsd.statsCompanionReady();

            BroadcastReceiver appUpdateReceiver = new AppUpdateReceiver();
            BroadcastReceiver userUpdateReceiver = new UserUpdateReceiver();
            BroadcastReceiver shutdownEventReceiver = new ShutdownEventReceiver();

            // Setup broadcast receiver for updates.
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            mContext.registerReceiverForAllUsers(appUpdateReceiver, filter, null,
                    /* scheduler= */ mHandler);

            // Setup receiver for user initialize (which happens once for a new user)
            // and if a user is removed.
            filter = new IntentFilter(Intent.ACTION_USER_INITIALIZE);
            filter.addAction(Intent.ACTION_USER_REMOVED);
            mContext.registerReceiverForAllUsers(userUpdateReceiver, filter, null,
                    /* scheduler= */ mHandler);

            // Setup receiver for device reboots or shutdowns.
            filter = new IntentFilter(Intent.ACTION_REBOOT);
            filter.addAction(Intent.ACTION_SHUTDOWN);
            mContext.registerReceiverForAllUsers(shutdownEventReceiver, filter, null, null);

            // Register listener for statsd_java properties updates.
            DeviceConfig.addOnPropertiesChangedListener(NAMESPACE_STATSD_JAVA,
                    mContext.getMainExecutor(), this::onPropertiesChanged);

            // Get current statsd_java properties.
            final long token = Binder.clearCallingIdentity();
            try {
                updateProperties(DeviceConfig.getProperties(NAMESPACE_STATSD_JAVA));
            } finally {
                Binder.restoreCallingIdentity(token);
            }

            // Register death recipient.
            List<BroadcastReceiver> broadcastReceivers =
                    List.of(appUpdateReceiver, userUpdateReceiver, shutdownEventReceiver);
            registerStatsdDeathRecipient(statsd, broadcastReceivers);

            // Tell statsd that boot has completed. The signal may have already been sent, but since
            // the signal-receiving function is idempotent, that's ok.
            if (mBootCompleted.get()) {
                statsd.bootCompleted();
            }

            // Pull the latest state of UID->app name, version mapping when statsd starts.
            informAllUids(mContext);

            Log.i(TAG, "Told statsd that StatsCompanionService is alive.");
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to inform statsd that statscompanion is ready", e);
        }
    }

    private class StatsdDeathRecipient implements IBinder.DeathRecipient {

        private final IStatsd mStatsd;
        private final List<BroadcastReceiver> mReceiversToUnregister;

        StatsdDeathRecipient(IStatsd statsd, List<BroadcastReceiver> receivers) {
            mStatsd = statsd;
            mReceiversToUnregister = receivers;
        }

        // It is possible for binderDied to be called after a restarted statsd calls statsdReady,
        // but that's alright because the code does not assume an ordering of the two calls.
        @Override
        public void binderDied() {
            Log.i(TAG, "Statsd is dead - erase all my knowledge, except pullers");
            synchronized (sStatsdLock) {
                long now = SystemClock.elapsedRealtime();
                for (Long timeMillis : mDeathTimeMillis) {
                    long ageMillis = now - timeMillis;
                    if (ageMillis > MILLIS_IN_A_DAY) {
                        mDeathTimeMillis.remove(timeMillis);
                    }
                }
                for (Long timeMillis : mDeletedFiles.keySet()) {
                    long ageMillis = now - timeMillis;
                    if (ageMillis > MILLIS_IN_A_DAY * 7) {
                        mDeletedFiles.remove(timeMillis);
                    }
                }
                mDeathTimeMillis.add(now);
                if (mDeathTimeMillis.size() >= DEATH_THRESHOLD) {
                    mDeathTimeMillis.clear();
                    File[] configs = new File(CONFIG_DIR).listFiles();
                    if (configs != null && configs.length > 0) {
                        String fileName = configs[0].getName();
                        if (configs[0].delete()) {
                            mDeletedFiles.put(now, fileName);
                        }
                    }
                }

                // Unregister receivers on death because receivers can only be unregistered once.
                // Otherwise, an IllegalArgumentException is thrown.
                for (BroadcastReceiver receiver: mReceiversToUnregister) {
                    mContext.unregisterReceiver(receiver);
                }

                // It's possible for statsd to have restarted and called statsdReady, causing a new
                // sStatsd binder object to be fetched, before the binderDied callback runs. Only
                // call #statsdNotReadyLocked if that hasn't happened yet.
                if (mStatsd == sStatsd) {
                    statsdNotReadyLocked();
                }
            }
        }
    }

    private void statsdNotReadyLocked() {
        sStatsd = null;
        mStatsManagerService.statsdNotReady();
    }

    void bootCompleted() {
        mBootCompleted.set(true);
        IStatsd statsd = getStatsdNonblocking();
        if (statsd == null) {
            // Statsd is not yet ready.
            // Delay the boot completed ping to {@link #sayHiToStatsd()}
            return;
        }
        try {
            statsd.bootCompleted();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify statsd that boot completed");
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        synchronized (sStatsdLock) {
            writer.println("Number of configuration files deleted: " + mDeletedFiles.size());
            if (mDeletedFiles.size() > 0) {
                writer.println("  timestamp, deleted file name");
            }
            long lastBootMillis =
                    SystemClock.currentThreadTimeMillis() - SystemClock.elapsedRealtime();
            for (Long elapsedMillis : mDeletedFiles.keySet()) {
                long deletionMillis = lastBootMillis + elapsedMillis;
                writer.println("  " + deletionMillis + ", " + mDeletedFiles.get(elapsedMillis));
            }
        }
    }
}
