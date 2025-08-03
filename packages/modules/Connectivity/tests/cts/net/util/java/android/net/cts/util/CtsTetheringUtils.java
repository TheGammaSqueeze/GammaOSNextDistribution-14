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

package android.net.cts.util;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.TETHER_PRIVILEGED;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_FAILED;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_STARTED;
import static android.net.TetheringManager.TETHER_HARDWARE_OFFLOAD_STOPPED;

import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Network;
import android.net.TetheredClient;
import android.net.TetheringInterface;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringEventCallback;
import android.net.TetheringManager.TetheringInterfaceRegexps;
import android.net.TetheringManager.TetheringRequest;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.SoftApCallback;
import android.os.ConditionVariable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;
import com.android.net.module.util.ArrayTrackRecord;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class CtsTetheringUtils {
    private TetheringManager mTm;
    private WifiManager mWm;
    private Context mContext;

    private static final int DEFAULT_TIMEOUT_MS = 60_000;

    public CtsTetheringUtils(Context ctx) {
        mContext = ctx;
        mTm = mContext.getSystemService(TetheringManager.class);
        mWm = mContext.getSystemService(WifiManager.class);
    }

    public static class StartTetheringCallback implements TetheringManager.StartTetheringCallback {
        private static int TIMEOUT_MS = 30_000;
        public static class CallbackValue {
            public final int error;

            private CallbackValue(final int e) {
                error = e;
            }

            public static class OnTetheringStarted extends CallbackValue {
                OnTetheringStarted() { super(TETHER_ERROR_NO_ERROR); }
            }

            public static class OnTetheringFailed extends CallbackValue {
                OnTetheringFailed(final int error) { super(error); }
            }

            @Override
            public String toString() {
                return String.format("%s(%d)", getClass().getSimpleName(), error);
            }
        }

        private final ArrayTrackRecord<CallbackValue>.ReadHead mHistory =
                new ArrayTrackRecord<CallbackValue>().newReadHead();

        @Override
        public void onTetheringStarted() {
            mHistory.add(new CallbackValue.OnTetheringStarted());
        }

        @Override
        public void onTetheringFailed(final int error) {
            mHistory.add(new CallbackValue.OnTetheringFailed(error));
        }

        public void verifyTetheringStarted() {
            final CallbackValue cv = mHistory.poll(TIMEOUT_MS, c -> true);
            assertNotNull("No onTetheringStarted after " + TIMEOUT_MS + " ms", cv);
            assertTrue("Fail start tethering:" + cv,
                    cv instanceof CallbackValue.OnTetheringStarted);
        }

        public void expectTetheringFailed(final int expected) throws InterruptedException {
            final CallbackValue cv = mHistory.poll(TIMEOUT_MS, c -> true);
            assertNotNull("No onTetheringFailed after " + TIMEOUT_MS + " ms", cv);
            assertTrue("Expect fail with error code " + expected + ", but received: " + cv,
                    (cv instanceof CallbackValue.OnTetheringFailed) && (cv.error == expected));
        }
    }

    private static boolean isRegexMatch(final String[] ifaceRegexs, String iface) {
        if (ifaceRegexs == null) fail("ifaceRegexs should not be null");

        for (String regex : ifaceRegexs) {
            if (iface.matches(regex)) return true;
        }

        return false;
    }

    public static boolean isAnyIfaceMatch(final String[] ifaceRegexs, final List<String> ifaces) {
        if (ifaces == null) return false;

        for (String s : ifaces) {
            if (isRegexMatch(ifaceRegexs, s)) return true;
        }

        return false;
    }

    private static TetheringInterface getFirstMatchingTetheringInterface(final List<String> regexs,
            final int type, final Set<TetheringInterface> ifaces) {
        if (ifaces == null || regexs == null) return null;

        final String[] regexArray = regexs.toArray(new String[0]);
        for (TetheringInterface iface : ifaces) {
            if (isRegexMatch(regexArray, iface.getInterface()) && type == iface.getType()) {
                return iface;
            }
        }

        return null;
    }

    // Must poll the callback before looking at the member.
    public static class TestTetheringEventCallback implements TetheringEventCallback {
        private static final int TIMEOUT_MS = 30_000;

        public enum CallbackType {
            ON_SUPPORTED,
            ON_UPSTREAM,
            ON_TETHERABLE_REGEX,
            ON_TETHERABLE_IFACES,
            ON_TETHERED_IFACES,
            ON_ERROR,
            ON_CLIENTS,
            ON_OFFLOAD_STATUS,
        };

        public static class CallbackValue {
            public final CallbackType callbackType;
            public final Object callbackParam;
            public final int callbackParam2;

            private CallbackValue(final CallbackType type, final Object param, final int param2) {
                this.callbackType = type;
                this.callbackParam = param;
                this.callbackParam2 = param2;
            }
        }

        private final ArrayTrackRecord<CallbackValue> mHistory =
                new ArrayTrackRecord<CallbackValue>();

        private final ArrayTrackRecord<CallbackValue>.ReadHead mCurrent =
                mHistory.newReadHead();

        private TetheringInterfaceRegexps mTetherableRegex;
        private List<String> mTetherableIfaces;
        private List<String> mTetheredIfaces;
        private String mErrorIface;
        private int mErrorCode;

        @Override
        public void onTetheringSupported(boolean supported) {
            mHistory.add(new CallbackValue(CallbackType.ON_SUPPORTED, null, (supported ? 1 : 0)));
        }

        @Override
        public void onUpstreamChanged(Network network) {
            mHistory.add(new CallbackValue(CallbackType.ON_UPSTREAM, network, 0));
        }

        @Override
        public void onTetherableInterfaceRegexpsChanged(TetheringInterfaceRegexps reg) {
            mTetherableRegex = reg;
            mHistory.add(new CallbackValue(CallbackType.ON_TETHERABLE_REGEX, reg, 0));
        }

        @Override
        public void onTetherableInterfacesChanged(List<String> interfaces) {
            mTetherableIfaces = interfaces;
        }
        // Call the interface default implementation, which will call
        // onTetherableInterfacesChanged(List<String>). This ensures that the default implementation
        // of the new callback method calls the old callback method and avoids the need to convert
        // Set<TetheringInterface> to List<String> in this code.
        @Override
        public void onTetherableInterfacesChanged(Set<TetheringInterface> interfaces) {
            TetheringEventCallback.super.onTetherableInterfacesChanged(interfaces);
            assertHasAllTetheringInterfaces(interfaces, mTetherableIfaces);
            mHistory.add(new CallbackValue(CallbackType.ON_TETHERABLE_IFACES, interfaces, 0));
        }

        @Override
        public void onTetheredInterfacesChanged(List<String> interfaces) {
            mTetheredIfaces = interfaces;
        }

        @Override
        public void onTetheredInterfacesChanged(Set<TetheringInterface> interfaces) {
            TetheringEventCallback.super.onTetheredInterfacesChanged(interfaces);
            assertHasAllTetheringInterfaces(interfaces, mTetheredIfaces);
            mHistory.add(new CallbackValue(CallbackType.ON_TETHERED_IFACES, interfaces, 0));
        }

        @Override
        public void onError(String ifName, int error) {
            mErrorIface = ifName;
            mErrorCode = error;
        }

        @Override
        public void onError(TetheringInterface ifName, int error) {
            TetheringEventCallback.super.onError(ifName, error);
            assertEquals(ifName.getInterface(), mErrorIface);
            assertEquals(error, mErrorCode);
            mHistory.add(new CallbackValue(CallbackType.ON_ERROR, ifName, error));
        }

        @Override
        public void onClientsChanged(Collection<TetheredClient> clients) {
            mHistory.add(new CallbackValue(CallbackType.ON_CLIENTS, clients, 0));
        }

        @Override
        public void onOffloadStatusChanged(int status) {
            mHistory.add(new CallbackValue(CallbackType.ON_OFFLOAD_STATUS, status, 0));
        }

        private void assertHasAllTetheringInterfaces(Set<TetheringInterface> tetheringIfaces,
                List<String> ifaces) {
            // This does not check that the interfaces are the same. This checks that the
            // List<String> has all the interface names contained by the Set<TetheringInterface>.
            assertEquals(tetheringIfaces.size(), ifaces.size());
            for (TetheringInterface tether : tetheringIfaces) {
                assertTrue("iface " + tether.getInterface()
                        + " seen by new callback but not old callback",
                        ifaces.contains(tether.getInterface()));
            }
        }

        public void expectTetherableInterfacesChanged(@NonNull final List<String> regexs,
                final int type) {
            assertNotNull("No expected tetherable ifaces callback", mCurrent.poll(TIMEOUT_MS,
                (cv) -> {
                    if (cv.callbackType != CallbackType.ON_TETHERABLE_IFACES) return false;
                    final Set<TetheringInterface> interfaces =
                            (Set<TetheringInterface>) cv.callbackParam;
                    return getFirstMatchingTetheringInterface(regexs, type, interfaces) != null;
                }));
        }

        public void expectNoTetheringActive() {
            assertNotNull("At least one tethering type unexpectedly active",
                    mCurrent.poll(TIMEOUT_MS, (cv) -> {
                        if (cv.callbackType != CallbackType.ON_TETHERED_IFACES) return false;

                        return ((Set<TetheringInterface>) cv.callbackParam).isEmpty();
                    }));
        }

        @Nullable
        public TetheringInterface pollTetheredInterfacesChanged(
                @NonNull final List<String> regexs, final int type, long timeOutMs) {
            while (true) {
                final CallbackValue cv = mCurrent.poll(timeOutMs, c -> true);
                if (cv == null) return null;

                if (cv.callbackType != CallbackType.ON_TETHERED_IFACES) continue;

                final Set<TetheringInterface> interfaces =
                        (Set<TetheringInterface>) cv.callbackParam;

                final TetheringInterface iface =
                        getFirstMatchingTetheringInterface(regexs, type, interfaces);

                if (iface != null) return iface;
            }
        }

        @NonNull
        public TetheringInterface expectTetheredInterfacesChanged(
                @NonNull final List<String> regexs, final int type) {
            final TetheringInterface iface = pollTetheredInterfacesChanged(regexs, type,
                    TIMEOUT_MS);

            if (iface == null) {
                fail("No expected tethered ifaces callback, expected type: " + type);
            }

            return iface;
        }

        public void expectCallbackStarted() {
            // This method uses its own readhead because it just check whether last tethering status
            // is updated after TetheringEventCallback get registered but do not check content
            // of received callbacks. Using shared readhead (mCurrent) only when the callbacks the
            // method polled is also not necessary for other methods which using shared readhead.
            // All of methods using mCurrent is order mattered.
            final ArrayTrackRecord<CallbackValue>.ReadHead history =
                    mHistory.newReadHead();
            int receivedBitMap = 0;
            // The each bit represent a type from CallbackType.ON_*.
            // Expect all of callbacks except for ON_ERROR.
            final int expectedBitMap = 0xff ^ (1 << CallbackType.ON_ERROR.ordinal());
            // Receive ON_ERROR on started callback is not matter. It just means tethering is
            // failed last time, should able to continue the test this time.
            while ((receivedBitMap & expectedBitMap) != expectedBitMap) {
                final CallbackValue cv = history.poll(TIMEOUT_MS, c -> true);
                if (cv == null) {
                    fail("No expected callbacks, " + "expected bitmap: "
                            + expectedBitMap + ", actual: " + receivedBitMap);
                }

                receivedBitMap |= (1 << cv.callbackType.ordinal());
            }
        }

        public void expectOneOfOffloadStatusChanged(int... offloadStatuses) {
            assertNotNull("No offload status changed", mCurrent.poll(TIMEOUT_MS, (cv) -> {
                if (cv.callbackType != CallbackType.ON_OFFLOAD_STATUS) return false;

                final int status = (int) cv.callbackParam;
                for (int offloadStatus : offloadStatuses) {
                    if (offloadStatus == status) return true;
                }

                return false;
            }));
        }

        public void expectErrorOrTethered(final TetheringInterface iface) {
            assertNotNull("No expected callback", mCurrent.poll(TIMEOUT_MS, (cv) -> {
                if (cv.callbackType == CallbackType.ON_ERROR
                        && iface.equals((TetheringInterface) cv.callbackParam)) {
                    return true;
                }
                if (cv.callbackType == CallbackType.ON_TETHERED_IFACES
                        && ((Set<TetheringInterface>) cv.callbackParam).contains(iface)) {
                    return true;
                }

                return false;
            }));
        }

        public Network getCurrentValidUpstream() {
            final CallbackValue result = mCurrent.poll(TIMEOUT_MS, (cv) -> {
                return (cv.callbackType == CallbackType.ON_UPSTREAM)
                        && cv.callbackParam != null;
            });

            assertNotNull("No valid upstream", result);
            return (Network) result.callbackParam;
        }

        public void assumeTetheringSupported() {
            assumeTrue(isTetheringSupported());
        }

        private boolean isTetheringSupported() {
            final ArrayTrackRecord<CallbackValue>.ReadHead history =
                    mHistory.newReadHead();
            final CallbackValue result = history.poll(TIMEOUT_MS, (cv) -> {
                return cv.callbackType == CallbackType.ON_SUPPORTED;
            });

            assertNotNull("No onSupported callback", result);
            return result.callbackParam2 == 1 /* supported */;
        }

        public void assumeWifiTetheringSupported(final Context ctx) throws Exception {
            assumeTrue(isWifiTetheringSupported(ctx));
        }

        public boolean isWifiTetheringSupported(final Context ctx) throws Exception {
            return isTetheringSupported()
                    && !getTetheringInterfaceRegexps().getTetherableWifiRegexs().isEmpty()
                    && isPortableHotspotSupported(ctx);
        }

        public TetheringInterfaceRegexps getTetheringInterfaceRegexps() {
            return mTetherableRegex;
        }
    }

    private static boolean isWifiEnabled(final WifiManager wm) {
        return runAsShell(ACCESS_WIFI_STATE, () -> wm.isWifiEnabled());

    }

    private static void waitForWifiEnabled(final Context ctx) throws Exception {
        WifiManager wm = ctx.getSystemService(WifiManager.class);
        if (isWifiEnabled(wm)) return;

        final ConditionVariable mWaiting = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                    if (isWifiEnabled(wm)) mWaiting.open();
                }
            }
        };
        try {
            ctx.registerReceiver(receiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
            if (!mWaiting.block(DEFAULT_TIMEOUT_MS)) {
                assertTrue("Wifi did not become enabled after " + DEFAULT_TIMEOUT_MS + "ms",
                        isWifiEnabled(wm));
            }
        } finally {
            ctx.unregisterReceiver(receiver);
        }
    }

    public TestTetheringEventCallback registerTetheringEventCallback() {
        final TestTetheringEventCallback tetherEventCallback =
                new TestTetheringEventCallback();

        runAsShell(ACCESS_NETWORK_STATE, NETWORK_SETTINGS, () -> {
            mTm.registerTetheringEventCallback(c -> c.run() /* executor */, tetherEventCallback);
            tetherEventCallback.expectCallbackStarted();
        });

        return tetherEventCallback;
    }

    public void unregisterTetheringEventCallback(final TestTetheringEventCallback callback) {
        runAsShell(ACCESS_NETWORK_STATE, () -> mTm.unregisterTetheringEventCallback(callback));
    }

    private static List<String> getWifiTetherableInterfaceRegexps(
            final TestTetheringEventCallback callback) {
        return callback.getTetheringInterfaceRegexps().getTetherableWifiRegexs();
    }

    /* Returns if wifi supports hotspot. */
    private static boolean isPortableHotspotSupported(final Context ctx) throws Exception {
        final PackageManager pm = ctx.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) return false;
        final WifiManager wm = ctx.getSystemService(WifiManager.class);
        // Wifi feature flags only work when wifi is on.
        final boolean previousWifiEnabledState = isWifiEnabled(wm);
        try {
            if (!previousWifiEnabledState) SystemUtil.runShellCommand("svc wifi enable");
            waitForWifiEnabled(ctx);
            return runAsShell(ACCESS_WIFI_STATE, () -> wm.isPortableHotspotSupported());
        } finally {
            if (!previousWifiEnabledState) {
                new CtsNetUtils(ctx).disableWifi();
            }
        }
    }

    public TetheringInterface startWifiTethering(final TestTetheringEventCallback callback)
            throws InterruptedException {
        final List<String> wifiRegexs = getWifiTetherableInterfaceRegexps(callback);

        final StartTetheringCallback startTetheringCallback = new StartTetheringCallback();
        final TetheringRequest request = new TetheringRequest.Builder(TETHERING_WIFI)
                .setShouldShowEntitlementUi(false).build();

        return runAsShell(TETHER_PRIVILEGED, () -> {
            mTm.startTethering(request, c -> c.run() /* executor */, startTetheringCallback);
            startTetheringCallback.verifyTetheringStarted();

            final TetheringInterface iface =
                    callback.expectTetheredInterfacesChanged(wifiRegexs, TETHERING_WIFI);

            callback.expectOneOfOffloadStatusChanged(
                    TETHER_HARDWARE_OFFLOAD_STARTED,
                    TETHER_HARDWARE_OFFLOAD_FAILED);

            return iface;
        });
    }

    private static class StopSoftApCallback implements SoftApCallback {
        private final ConditionVariable mWaiting = new ConditionVariable();
        @Override
        public void onStateChanged(int state, int failureReason) {
            if (state == WifiManager.WIFI_AP_STATE_DISABLED) mWaiting.open();
        }

        @Override
        public void onConnectedClientsChanged(List<WifiClient> clients) { }

        public void waitForSoftApStopped() {
            if (!mWaiting.block(DEFAULT_TIMEOUT_MS)) {
                fail("stopSoftAp Timeout");
            }
        }
    }

    // Wait for softAp to be disabled. This is necessary on devices where stopping softAp
    // deletes the interface. On these devices, tethering immediately stops when the softAp
    // interface is removed, but softAp is not yet fully disabled. Wait for softAp to be
    // fully disabled, because otherwise the next test might fail because it attempts to
    // start softAp before it's fully stopped.
    public void expectSoftApDisabled() {
        final StopSoftApCallback callback = new StopSoftApCallback();
        try {
            runAsShell(NETWORK_SETTINGS, () -> mWm.registerSoftApCallback(c -> c.run(), callback));
            // registerSoftApCallback will immediately call the callback with the current state, so
            // this callback will fire even if softAp is already disabled.
            callback.waitForSoftApStopped();
        } finally {
            runAsShell(NETWORK_SETTINGS, () -> mWm.unregisterSoftApCallback(callback));
        }
    }

    public void stopWifiTethering(final TestTetheringEventCallback callback) {
        runAsShell(TETHER_PRIVILEGED, () -> {
            mTm.stopTethering(TETHERING_WIFI);
            callback.expectNoTetheringActive();
            callback.expectOneOfOffloadStatusChanged(TETHER_HARDWARE_OFFLOAD_STOPPED);
        });
        expectSoftApDisabled();
    }

    public void stopAllTethering() {
        final TestTetheringEventCallback callback = registerTetheringEventCallback();
        try {
            runAsShell(TETHER_PRIVILEGED, () -> {
                mTm.stopAllTethering();
                callback.expectNoTetheringActive();
            });
        } finally {
            unregisterTetheringEventCallback(callback);
        }
    }
}
