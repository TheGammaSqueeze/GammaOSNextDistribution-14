/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.app.cts;

import static android.content.pm.Flags.FLAG_STAY_STOPPED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.BootReceiver;
import android.app.stubs.CommandReceiver;
import android.app.stubs.SimpleActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.Flags;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ForceStopTest {

    // A simple test activity from another package.
    private static final String APP_PACKAGE = "com.android.app1";
    private static final String APP_ACTIVITY = "android.app.stubs.SimpleActivity";

    private static final long DELAY_MILLIS = 10_000;

    private Context mTargetContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;

    private long mTimestampMs;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        AmUtils.waitForBroadcastBarrier();
    }

    private Intent createSimpleActivityIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(APP_PACKAGE);
        intent.setClassName(APP_PACKAGE, APP_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private ActivityReceiverFilter forceStopAndStartSimpleActivity(Intent intent) throws Exception {
        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(intent.getPackage()));
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                SimpleActivity.ACTION_ACTIVITY_STARTED);
        // Start an activity of another APK.
        mTargetContext.startActivity(intent);
        assertTrue(appStartedReceiver.waitForActivity());
        return appStartedReceiver;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPackageStoppedState() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();
        forceStopAndStartSimpleActivity(intent);

        assertFalse("Package " + packageName + " shouldn't be in the stopped state",
                mPackageManager.isPackageStopped(packageName));

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
        assertTrue("Package " + packageName + " should be in the stopped state",
                mPackageManager.isPackageStopped(packageName));
    }

    @Test
    public void testPackageRestartedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final ConditionVariable gotRestarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Uri uri = intent.getData();
                final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                if (Intent.ACTION_PACKAGE_RESTARTED.equals(action)
                        && packageName.equals(pkg)) {
                    mTimestampMs = intent.getLongExtra(Intent.EXTRA_TIME, 0L);
                    gotRestarted.open();
                }
            }
        };

        mTimestampMs = 0;
        final long preStopTimestampMs = SystemClock.elapsedRealtime();

        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        mTargetContext.registerReceiver(receiver, filter);

        forceStopAndStartSimpleActivity(intent);

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));

        if (!gotRestarted.block(DELAY_MILLIS)) {
            fail("Didn't get ACTION_PACKAGE_RESTARTED");
        }
        if (Flags.stayStopped()) {
            assertTrue("EXTRA_TIME " + mTimestampMs + " not after " + preStopTimestampMs,
                    mTimestampMs >= preStopTimestampMs);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPackageUnstoppedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final ConditionVariable gotUnstopped = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Uri uri = intent.getData();
                final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                if (Intent.ACTION_PACKAGE_UNSTOPPED.equals(action)
                        && packageName.equals(pkg)) {
                    mTimestampMs = intent.getLongExtra(Intent.EXTRA_TIME, 0L);
                    gotUnstopped.open();
                }
            }
        };

        mTimestampMs = 0;
        final long preUnstopTimestampMs = SystemClock.elapsedRealtime();

        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_UNSTOPPED);
        mTargetContext.registerReceiver(receiver, filter);

        forceStopAndStartSimpleActivity(intent);

        assertTrue("EXTRA_TIME " + mTimestampMs + " not after " + preUnstopTimestampMs,
                mTimestampMs >= preUnstopTimestampMs);

        if (!gotUnstopped.block(DELAY_MILLIS)) {
            fail("Didn't get ACTION_PACKAGE_UNSTOPPED");
        }

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testBootCompletedBroadcasts_activity() throws Exception {
        final Intent intent = createSimpleActivityIntent();

        final ConditionVariable gotLockedBoot = new ConditionVariable();
        final ConditionVariable gotBoot = new ConditionVariable();
        final ConditionVariable gotActivityStarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED.equals(action)) {
                    final String extraAction = intent.getStringExtra(
                            BootReceiver.EXTRA_BOOT_COMPLETED_ACTION);
                    if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(extraAction)) {
                        gotLockedBoot.open();
                    } else if (Intent.ACTION_BOOT_COMPLETED.equals(extraAction)) {
                        gotBoot.open();
                    }
                } else if (SimpleActivity.ACTION_ACTIVITY_STARTED.equals(action)) {
                    gotActivityStarted.open();
                }
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED);
        filter.addAction(SimpleActivity.ACTION_ACTIVITY_STARTED);
        mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        mTargetContext.startActivity(intent);

        assertTrue("Activity didn't start", gotActivityStarted.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        mTargetContext.startActivity(intent);

        assertTrue("Didn't get LOCKED_BOOT_COMPLETED", gotLockedBoot.block(DELAY_MILLIS));
        assertTrue("Didn't get BOOT_COMPLETED", gotBoot.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testBootCompletedBroadcasts_broadcast() throws Exception {
        final ConditionVariable gotLockedBoot = new ConditionVariable();
        final ConditionVariable gotBoot = new ConditionVariable();
        final ConditionVariable appStarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED.equals(action)) {
                    final String extraAction = intent.getStringExtra(
                            BootReceiver.EXTRA_BOOT_COMPLETED_ACTION);
                    if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(extraAction)) {
                        gotLockedBoot.open();
                    } else if (Intent.ACTION_BOOT_COMPLETED.equals(extraAction)) {
                        gotBoot.open();
                    }
                }
            }
        };
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        appStarted.open();
                    }
                });

        assertTrue("Activity didn't start", appStarted.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        AmUtils.waitForBroadcastBarrier();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED);
        mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        CommandReceiver.sendCommand(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null);

        assertTrue("Didn't get LOCKED_BOOT_COMPLETED", gotLockedBoot.block(DELAY_MILLIS));
        assertTrue("Didn't get BOOT_COMPLETED", gotBoot.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPendingIntentCancellation() throws Exception {
        final PendingIntent pendingIntent = triggerPendingIntentCreation(APP_PACKAGE);
        assertNotNull(pendingIntent);

        final ConditionVariable pendingIntentCancelled = new ConditionVariable();
        pendingIntent.addCancelListener(mTargetContext.getMainExecutor(), pi -> {
            if (pendingIntent.equals(pi)) {
                pendingIntentCancelled.open();
            }
        });

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
        assertTrue("Package " + APP_PACKAGE + " should be in the stopped state",
                mPackageManager.isPackageStopped(APP_PACKAGE));

        // Verify that pending intent gets cancelled when the app that created it is force-stopped.
        assertTrue("Did not receive PendingIntent cancellation callback",
                pendingIntentCancelled.block(DELAY_MILLIS));
        assertThrows(CanceledException.class, () -> pendingIntent.send());

        // Trigger the PendingIntent creation to verify the app can create new PendingIntents
        // as usual.
        final PendingIntent pendingIntent2 = triggerPendingIntentCreation(APP_PACKAGE);
        assertNotNull(pendingIntent2);

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    private PendingIntent triggerPendingIntentCreation(final String packageName) throws Exception {
        final BlockingQueue<PendingIntent> blockingQueue = new LinkedBlockingQueue<>();
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_CREATE_FGSL_PENDING_INTENT,
                packageName, packageName, Intent.FLAG_RECEIVER_FOREGROUND, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final PendingIntent pi = getResultExtras(true).getParcelable(
                                CommandReceiver.KEY_PENDING_INTENT, PendingIntent.class);
                        if (pi != null) {
                            blockingQueue.offer(pi);
                        }
                    }
                });
        return blockingQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private String mActivityToFilter;
        private ConditionVariable mBroadcastCondition = new ConditionVariable();

        // Create the filter with the intent to look for.
        ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            final IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mTargetContext.registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                mBroadcastCondition.open();
            }
        }

        public boolean waitForActivity() throws Exception {
            AmUtils.waitForBroadcastBarrier();
            // Wait for the broadcast
            return mBroadcastCondition.block(DELAY_MILLIS);
        }
    }
}
