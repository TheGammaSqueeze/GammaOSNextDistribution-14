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

package android.app.cts.broadcasts;

import static com.android.app.cts.broadcasts.Common.TAG;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNull;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.app.cts.broadcasts.BroadcastReceipt;
import com.android.app.cts.broadcasts.ICommandReceiver;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;
import com.android.compatibility.common.util.ThrowingSupplier;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

abstract class BaseBroadcastTest {
    protected static final long TIMEOUT_BIND_SERVICE_SEC = 2;

    protected static final long SHORT_FREEZER_TIMEOUT_MS = 5000;
    protected static final long BROADCAST_RECEIVE_TIMEOUT_MS = 5000;

    protected static final String HELPER_PKG1 = "com.android.app.cts.broadcasts.helper";
    protected static final String HELPER_PKG2 = "com.android.app.cts.broadcasts.helper2";
    protected static final String HELPER_SERVICE = HELPER_PKG1 + ".TestService";

    protected static final String TEST_ACTION1 = "com.android.app.cts.TEST_ACTION1";

    protected static final String TEST_EXTRA1 = "com.android.app.cts.TEST_EXTRA1";

    protected static final String TEST_VALUE1 = "value1";
    protected static final String TEST_VALUE2 = "value2";

    protected static final long BROADCAST_FORCED_DELAYED_DURATION_MS = 120_000;

    // TODO: Avoid hardcoding the device_config constant here.
    protected static final String KEY_FREEZE_DEBOUNCE_TIMEOUT = "freeze_debounce_timeout";

    protected Context mContext;
    protected ActivityManager mAm;

    @Before
    public void setUp() {
        mContext = getContext();
        mAm = mContext.getSystemService(ActivityManager.class);
        AmUtils.waitForBroadcastBarrier();
    }

    @Before
    public void unsetPackageStoppedState() {
        // Bring test apps out of the stopped state so that they can receive broadcasts
        SystemUtil.runWithShellPermissionIdentity(() -> {
            for (String pkg : new String[] {HELPER_PKG1, HELPER_PKG2}) {
                AppGlobals.getPackageManager().setPackageStoppedState(pkg, false,
                        Process.myUserHandle().getIdentifier());
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            for (String pkg : new String[] {HELPER_PKG1, HELPER_PKG2}) {
                mAm.forceDelayBroadcastDelivery(pkg, 0);
                mAm.forceStopPackage(pkg);
            }
        });
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    protected void forceDelayBroadcasts(String targetPackage) {
        forceDelayBroadcasts(targetPackage, BROADCAST_FORCED_DELAYED_DURATION_MS);
    }

    private void forceDelayBroadcasts(String targetPackage, long delayedDurationMs) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mAm.forceDelayBroadcastDelivery(targetPackage, delayedDurationMs));
    }

    protected boolean isModernBroadcastQueueEnabled() {
        return SystemUtil.runWithShellPermissionIdentity(() ->
                mAm.isModernBroadcastQueueEnabled());
    }

    protected boolean isAppFreezerEnabled() throws Exception {
        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        return am.getService().isAppFreezerEnabled();
    }

    protected void waitForProcessFreeze(int pid, long timeoutMs) {
        // TODO: Add a listener to monitor freezer state changes.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            TestUtils.waitUntil("Timed out waiting for test process to be frozen; pid=" + pid,
                    (int) TimeUnit.MILLISECONDS.toSeconds(timeoutMs),
                    () -> mAm.isProcessFrozen(pid));
        });
    }

    protected boolean isProcessFrozen(int pid) {
        return SystemUtil.runWithShellPermissionIdentity(() -> mAm.isProcessFrozen(pid));
    }

    protected String runShellCmd(String cmdFormat, Object... args) {
        final String cmd = String.format(cmdFormat, args);
        final String output = SystemUtil.runShellCommand(cmd);
        Log.d(TAG, String.format("Output of '%s': '%s'", cmd, output));
        return output;
    }

    /**
     * @param matchExact If {@code matchExact} is {@code true}, then it is verified that
     *                   expected broadcasts exactly match the actual received broadcasts.
     *                   Otherwise, it is verified that expected broadcasts are part of the
     *                   actual received broadcasts.
     */
    protected void verifyReceivedBroadcasts(ICommandReceiver cmdReceiver, String cookie,
            List<Intent> expectedBroadcasts, boolean matchExact,
            BroadcastReceiptVerifier verifier) throws Exception {
        verifyReceivedBroadcasts(() -> cmdReceiver.getReceivedBroadcasts(cookie),
                expectedBroadcasts, matchExact, verifier);
    }

    /**
     * @param matchExact If {@code matchExact} is {@code true}, then it is verified that
     *                   expected broadcasts exactly match the actual received broadcasts.
     *                   Otherwise, it is verified that expected broadcasts are part of the
     *                   actual received broadcasts.
     */
    protected void verifyReceivedBroadcasts(
            ThrowingSupplier<List<BroadcastReceipt>> actualBroadcastsSupplier,
            List<Intent> expectedBroadcasts, boolean matchExact) throws Exception {
        verifyReceivedBroadcasts(actualBroadcastsSupplier, expectedBroadcasts, matchExact, null);
    }

    /**
     * @param matchExact If {@code matchExact} is {@code true}, then it is verified that
     *                   expected broadcasts exactly match the actual received broadcasts.
     *                   Otherwise, it is verified that expected broadcasts are part of the
     *                   actual received broadcasts.
     */
    protected void verifyReceivedBroadcasts(
            ThrowingSupplier<List<BroadcastReceipt>> actualBroadcastsSupplier,
            List<Intent> expectedBroadcasts, boolean matchExact,
            BroadcastReceiptVerifier verifier) throws Exception {
        waitForBroadcastBarrier();

        final List<BroadcastReceipt> actualBroadcasts = actualBroadcastsSupplier.get();
        final String errorMsg = "Expected: " + toString(expectedBroadcasts)
                + "; Actual: " + toString(actualBroadcasts);
        if (matchExact) {
            assertWithMessage(errorMsg).that(actualBroadcasts.size())
                    .isEqualTo(expectedBroadcasts.size());
            for (int i = 0; i < expectedBroadcasts.size(); ++i) {
                final Intent expected = expectedBroadcasts.get(i);
                final BroadcastReceipt receipt = actualBroadcasts.get(i);
                final Intent actual = receipt.intent;
                assertWithMessage(errorMsg).that(compareIntents(expected, actual))
                        .isEqualTo(true);
                if (verifier != null) {
                    assertNull(verifier.verify(receipt));
                }
            }
        } else {
            assertWithMessage(errorMsg).that(actualBroadcasts.size())
                    .isAtLeast(expectedBroadcasts.size());
            final Iterator<BroadcastReceipt> it = actualBroadcasts.iterator();
            int countMatches = 0;
            while (it.hasNext()) {
                final BroadcastReceipt receipt = it.next();
                final Intent actual = receipt.intent;
                final Intent expected = expectedBroadcasts.get(countMatches);
                if (compareIntents(expected, actual)
                        && (verifier == null || verifier.verify(receipt) == null)) {
                    countMatches++;
                }
                if (countMatches == expectedBroadcasts.size()) {
                    break;
                }
            }
            assertWithMessage(errorMsg).that(countMatches).isEqualTo(expectedBroadcasts.size());
        }
    }

    @FunctionalInterface
    public interface BroadcastReceiptVerifier {
        String verify(BroadcastReceipt receipt);
    }

    private static <T> String toString(List<T> list) {
        return list == null ? null : Arrays.toString(list.toArray());
    }

    private boolean compareIntents(Intent expected, Intent actual) {
        if (!actual.getAction().equals(expected.getAction())) {
            return false;
        }
        if (!compareExtras(expected.getExtras(), actual.getExtras())) {
            return false;
        }
        return true;
    }

    private boolean compareExtras(Bundle expected, Bundle actual) {
        if (expected == actual) {
            return true;
        } else if (expected == null || actual == null) {
            return false;
        }
        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (String key : expected.keySet()) {
            final Object expectedValue = expected.get(key);
            final Object actualValue = actual.get(key);
            if (expectedValue == actualValue) {
                continue;
            } else if (expectedValue == null || actualValue == null) {
                return false;
            }
            if (actualValue.getClass() != expectedValue.getClass()) {
                return false;
            }
            if (expectedValue.getClass().isArray()) {
                if (Array.getLength(actualValue) != Array.getLength(expectedValue)) {
                    return false;
                }
                for (int i = 0; i < Array.getLength(expectedValue); ++i) {
                    if (!Objects.equal(Array.get(actualValue, i), Array.get(expectedValue, i))) {
                        return false;
                    }
                }
            } else if (expectedValue instanceof ArrayList) {
                final ArrayList<?> expectedList = (ArrayList<?>) expectedValue;
                final ArrayList<?> actualList = (ArrayList<?>) actualValue;
                if (actualList.size() != expectedList.size()) {
                    return false;
                }
                for (int i = 0; i < expectedList.size(); ++i) {
                    if (!Objects.equal(actualList.get(i), expectedList.get(i))) {
                        return false;
                    }
                }
            } else {
                if (!Objects.equal(actualValue, expectedValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void waitForBroadcastBarrier() {
        SystemUtil.runCommandAndPrintOnLogcat(TAG,
                "cmd activity wait-for-broadcast-barrier --flush-application-threads");
    }

    protected TestServiceConnection bindToHelperService(String packageName) {
        final TestServiceConnection
                connection = new TestServiceConnection(mContext);
        final Intent intent = new Intent().setComponent(
                new ComponentName(packageName, HELPER_SERVICE));
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return connection;
    }

    protected class TestServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();
        private ICommandReceiver mCommandReceiver;

        TestServiceConnection(Context context) {
            mContext = context;
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service got connected: " + componentName);
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "Service got disconnected: " + componentName);
        }

        private IBinder getService() throws Exception {
            final IBinder service = mBlockingQueue.poll(TIMEOUT_BIND_SERVICE_SEC,
                    TimeUnit.SECONDS);
            return service;
        }

        public ICommandReceiver getCommandReceiver() throws Exception {
            if (mCommandReceiver == null) {
                mCommandReceiver = ICommandReceiver.Stub.asInterface(getService());
            }
            return mCommandReceiver;
        }

        public void unbind() {
            if (mCommandReceiver != null) {
                mContext.unbindService(this);
            }
            mCommandReceiver = null;
        }
    }

    static class ResultReceiver extends BroadcastReceiver {
        private final BlockingQueue<String> mResultData = new ArrayBlockingQueue<>(1);

        @Override
        public void onReceive(Context context, Intent intent) {
            mResultData.offer(getResultData());
        }

        public String getResult() throws Exception {
            return mResultData.poll(BROADCAST_RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
