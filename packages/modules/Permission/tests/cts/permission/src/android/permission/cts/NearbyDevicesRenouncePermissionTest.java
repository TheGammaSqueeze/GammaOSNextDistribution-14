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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.AppOpsManager.OPSTR_FINE_LOCATION;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.app.AsyncNotedAppOp;
import android.app.SyncNotedAppOp;
import android.bluetooth.BluetoothManager;
import android.bluetooth.cts.EnableBluetoothRule;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.EnableLocationRule;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.Uninterruptibles;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests behaviour when performing bluetooth scans with renounced location permission.
 */
public class NearbyDevicesRenouncePermissionTest {

    private static final String TAG = "NearbyDevicesRenouncePermissionTest";
    private static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @ClassRule
    public static final EnableBluetoothRule sEnableBluetoothRule = new EnableBluetoothRule(true);

    @Rule
    public DeviceConfigStateChangerRule safetyLabelChangeNotificationsEnabledConfig =
            new DeviceConfigStateChangerRule(
                    mContext,
                    DeviceConfig.NAMESPACE_BLUETOOTH,
                    "scan_quota_count",
                    Integer.toString(1000)
            );

    @Rule
    public final EnableLocationRule enableLocationRule = new EnableLocationRule();

    private AppOpsManager mAppOpsManager;

    private volatile long mTestStartTimestamp;
    private final AtomicInteger mLocationNoteCount = new AtomicInteger(0);
    private final AtomicInteger mScanNoteCount = new AtomicInteger(0);

    private enum Result {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    private enum Scenario {
        DEFAULT, RENOUNCE, RENOUNCE_MIDDLE, RENOUNCE_END
    }

    @Before
    public void setUp() throws Exception {
        // Sleep to guarantee that past noteOp timestamps are less than mTestStartTimestamp
        Uninterruptibles.sleepUninterruptibly(2, TimeUnit.MILLISECONDS);
        mTestStartTimestamp = System.currentTimeMillis();

        mLocationNoteCount.set(0);
        mScanNoteCount.set(0);

        mAppOpsManager = getApplicationContext().getSystemService(AppOpsManager.class);
        mAppOpsManager.setOnOpNotedCallback(getApplicationContext().getMainExecutor(),
                new AppOpsManager.OnOpNotedCallback() {
                    @Override
                    public void onNoted(SyncNotedAppOp op) {
                        switch (op.getOp()) {
                            case OPSTR_FINE_LOCATION:
                                logNoteOp(op);
                                mLocationNoteCount.incrementAndGet();
                                break;
                            case OPSTR_BLUETOOTH_SCAN:
                                logNoteOp(op);
                                mScanNoteCount.incrementAndGet();
                                break;
                            default:
                        }
                    }

                    @Override
                    public void onSelfNoted(SyncNotedAppOp op) {
                    }

                    @Override
                    public void onAsyncNoted(AsyncNotedAppOp asyncOp) {
                        switch (asyncOp.getOp()) {
                            case OPSTR_FINE_LOCATION:
                                logNoteOp(asyncOp);
                                if (asyncOp.getTime() < mTestStartTimestamp) {
                                    Log.i(TAG, "ignoring asyncOp that originated before test "
                                            + "start");
                                    return;
                                }
                                mLocationNoteCount.incrementAndGet();
                                break;
                            case OPSTR_BLUETOOTH_SCAN:
                                logNoteOp(asyncOp);
                                if (asyncOp.getTime() < mTestStartTimestamp) {
                                    Log.i(TAG, "ignoring asyncOp that originated before test "
                                            + "start");
                                    return;
                                }
                                mScanNoteCount.incrementAndGet();
                                break;
                            default:
                        }
                    }
                });
    }

    private void logNoteOp(SyncNotedAppOp op) {
        Log.i(TAG, "OnOpNotedCallback::onNoted(op=" + op.getOp() + ")");
    }

    private void logNoteOp(AsyncNotedAppOp asyncOp) {
        Log.i(TAG, "OnOpNotedCallback::"
                + "onAsyncNoted(op=" + asyncOp.getOp()
                + ", testStartTimestamp=" + mTestStartTimestamp
                + ", noteOpTimestamp=" + asyncOp.getTime() + ")");
    }

    @After
    public void tearDown() throws Exception {
        mAppOpsManager.setOnOpNotedCallback(null, null);
    }

    @AppModeFull
    @Test
    public void scanWithoutRenouncingNotesBluetoothAndLocation() throws Exception {
        assumeTrue(supportsBluetoothLe());

        assertThat(performScan(Scenario.DEFAULT)).isEqualTo(Result.FULL);
        SystemUtil.eventually(() -> {
            assertThat(mLocationNoteCount.get()).isGreaterThan(0);
            assertThat(mScanNoteCount.get()).isGreaterThan(0);
        });
    }

    @AppModeFull
    @Test
    public void scanRenouncingLocationNotesBluetoothButNotLocation() throws Exception {
        assumeTrue(supportsBluetoothLe());

        assertThat(performScan(Scenario.RENOUNCE)).isEqualTo(Result.FILTERED);
        SystemUtil.eventually(() -> {
            assertThat(mLocationNoteCount.get()).isEqualTo(0);
            assertThat(mScanNoteCount.get()).isGreaterThan(0);
        });
    }

    @AppModeFull
    @Test
    public void scanRenouncingInMiddleOfChainNotesBluetoothButNotLocation() throws Exception {
        assumeTrue(supportsBluetoothLe());

        assertThat(performScan(Scenario.RENOUNCE_MIDDLE)).isEqualTo(Result.FILTERED);
        SystemUtil.eventually(() -> {
            assertThat(mLocationNoteCount.get()).isEqualTo(0);
            assertThat(mScanNoteCount.get()).isGreaterThan(0);
        });
    }

    @AppModeFull
    @Test
    public void scanRenouncingAtEndOfChainNotesBluetoothButNotLocation() throws Exception {
        assertThat(performScan(Scenario.RENOUNCE_END)).isEqualTo(Result.FILTERED);
        SystemUtil.eventually(() -> {
            assertThat(mLocationNoteCount.get()).isEqualTo(0);
            assertThat(mScanNoteCount.get()).isGreaterThan(0);
        });
    }

    private Result performScan(Scenario scenario) {
        try {
            Context context = createContext(scenario, getApplicationContext());

            final BluetoothManager bm = context.getSystemService(BluetoothManager.class);
            final BluetoothLeScanner scanner = bm.getAdapter().getBluetoothLeScanner();

            final HashSet<String> observed = new HashSet<>();

            ScanCallback callback = new ScanCallback() {
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.v(TAG, String.valueOf(result));
                    observed.add(Base64.encodeToString(result.getScanRecord().getBytes(), 0));
                }

                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) {
                        onScanResult(0, result);
                    }
                }
            };
            scanner.startScan(callback);

            // Wait a few seconds to figure out what we actually observed
            SystemClock.sleep(3000);
            scanner.stopScan(callback);
            switch (observed.size()) {
                case 0:
                    return Result.EMPTY;
                case 1:
                    return Result.FILTERED;
                case 5:
                    return Result.FULL;
                default:
                    return Result.UNKNOWN;
            }
        } catch (Throwable t) {
            Log.v(TAG, "Failed to scan", t);
            return Result.EXCEPTION;
        }
    }

    private Context createContext(Scenario scenario, Context context) throws Exception {
        if (scenario == Scenario.DEFAULT) {
            return context;
        }

        Set<String> renouncedPermissions = new ArraySet<>();
        renouncedPermissions.add(ACCESS_FINE_LOCATION);

        switch (scenario) {
            case RENOUNCE:
                return SystemUtil.callWithShellPermissionIdentity(() ->
                        context.createContext(
                                new ContextParams.Builder()
                                        .setRenouncedPermissions(renouncedPermissions)
                                        .setAttributionTag(context.getAttributionTag())
                                        .build())
                );
            case RENOUNCE_MIDDLE:
                AttributionSource nextAttrib = new AttributionSource(
                        Process.SHELL_UID, "com.android.shell", null, (Set<String>) null, null);
                return SystemUtil.callWithShellPermissionIdentity(() ->
                        context.createContext(
                                new ContextParams.Builder()
                                        .setRenouncedPermissions(renouncedPermissions)
                                        .setAttributionTag(context.getAttributionTag())
                                        .setNextAttributionSource(nextAttrib)
                                        .build())
                );
            case RENOUNCE_END:
                nextAttrib = new AttributionSource(
                        Process.SHELL_UID, "com.android.shell", null, renouncedPermissions, null);
                return SystemUtil.callWithShellPermissionIdentity(() ->
                        context.createContext(
                                new ContextParams.Builder()
                                        .setAttributionTag(context.getAttributionTag())
                                        .setNextAttributionSource(nextAttrib)
                                        .build())
                );
            default:
                throw new IllegalStateException();
        }
    }

    private boolean supportsBluetoothLe() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }
}
