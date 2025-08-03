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

package com.android.server.nearby.managers.registration;

import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.nearby.IScanListener;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceScanFilter;
import android.nearby.ScanCallback;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.common.CancelableAlarm;
import com.android.server.nearby.managers.ListenerMultiplexer;
import com.android.server.nearby.managers.MergedDiscoveryRequest;
import com.android.server.nearby.presence.PresenceDiscoveryResult;
import com.android.server.nearby.util.identity.CallerIdentity;
import com.android.server.nearby.util.permissions.DiscoveryPermissions;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * Class responsible for all client based operations. Each {@link DiscoveryRegistration} is for one
 * valid unique {@link android.nearby.NearbyManager#startScan(ScanRequest, Executor, ScanCallback)}
 */
public class DiscoveryRegistration extends BinderListenerRegistration<IScanListener> {

    /**
     * Timeout before a previous discovered device is reported as lost.
     */
    @VisibleForTesting
    static final int ON_LOST_TIME_OUT_MS = 10000;
    /** Lock for registration operations. */
    final Object mMultiplexerLock;
    private final ListenerMultiplexer<IScanListener, DiscoveryRegistration, MergedDiscoveryRequest>
            mOwner;
    private final AppOpsManager mAppOpsManager;
    /** Presence devices that are currently discovered, and not lost yet. */
    @GuardedBy("mMultiplexerLock")
    private final Map<Long, NearbyDeviceParcelable> mDiscoveredDevices;
    /** A map of deviceId and alarms for reporting device lost. */
    @GuardedBy("mMultiplexerLock")
    private final Map<Long, DeviceOnLostAlarm> mDiscoveryOnLostAlarmPerDevice = new ArrayMap<>();
    /**
     * The single thread executor to run {@link CancelableAlarm} to report
     * {@link NearbyDeviceParcelable} on lost after timeout.
     */
    private final ScheduledExecutorService mAlarmExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final ScanRequest mScanRequest;
    private final CallerIdentity mCallerIdentity;

    public DiscoveryRegistration(
            ListenerMultiplexer<IScanListener, DiscoveryRegistration, MergedDiscoveryRequest> owner,
            ScanRequest scanRequest, IScanListener scanListener, Executor executor,
            CallerIdentity callerIdentity, Object multiplexerLock, AppOpsManager appOpsManager) {
        super(scanListener.asBinder(), executor, scanListener);
        mOwner = owner;
        mListener = scanListener;
        mScanRequest = scanRequest;
        mCallerIdentity = callerIdentity;
        mMultiplexerLock = multiplexerLock;
        mDiscoveredDevices = new ArrayMap<>();
        mAppOpsManager = appOpsManager;
    }

    /**
     * Gets the scan request.
     */
    public ScanRequest getScanRequest() {
        return mScanRequest;
    }

    /**
     * Gets the actions from the scan filter(s).
     */
    public Set<Integer> getActions() {
        Set<Integer> result = new ArraySet<>();
        List<ScanFilter> filters = mScanRequest.getScanFilters();
        for (ScanFilter filter : filters) {
            if (filter instanceof PresenceScanFilter) {
                result.addAll(((PresenceScanFilter) filter).getPresenceActions());
            }
        }
        return ImmutableSet.copyOf(result);
    }

    /**
     * Gets all the filters that are for Nearby Presence.
     */
    public Set<ScanFilter> getPresenceScanFilters() {
        Set<ScanFilter> result = new ArraySet<>();
        List<ScanFilter> filters = mScanRequest.getScanFilters();
        for (ScanFilter filter : filters) {
            if (filter.getType() == SCAN_TYPE_NEARBY_PRESENCE) {
                result.add(filter);
            }
        }
        return ImmutableSet.copyOf(result);
    }

    @VisibleForTesting
    Map<Long, DeviceOnLostAlarm> getDiscoveryOnLostAlarms() {
        synchronized (mMultiplexerLock) {
            return mDiscoveryOnLostAlarmPerDevice;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DiscoveryRegistration) {
            DiscoveryRegistration otherRegistration = (DiscoveryRegistration) other;
            return Objects.equals(mScanRequest, otherRegistration.mScanRequest) && Objects.equals(
                    mListener, otherRegistration.mListener);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mListener, mScanRequest);
    }

    @Override
    public ListenerMultiplexer<
            IScanListener, DiscoveryRegistration, MergedDiscoveryRequest> getOwner() {
        return mOwner;
    }

    @VisibleForTesting
    ListenerOperation<IScanListener> reportDeviceLost(NearbyDeviceParcelable device) {
        long deviceId = device.getDeviceId();
        return reportResult(DiscoveryResult.DEVICE_LOST, device, () -> {
            synchronized (mMultiplexerLock) {
                // Remove the device from reporting devices after reporting lost.
                mDiscoveredDevices.remove(deviceId);
                DeviceOnLostAlarm alarm = mDiscoveryOnLostAlarmPerDevice.remove(deviceId);
                if (alarm != null) {
                    alarm.cancel();
                }
            }
        });
    }

    /**
     * Called when there is device discovered from the server.
     */
    public ListenerOperation<IScanListener> onNearbyDeviceDiscovered(
            NearbyDeviceParcelable device) {
        if (!filterCheck(device)) {
            Log.d(TAG, "presence filter does not match for the scanned Presence Device");
            return null;
        }
        synchronized (mMultiplexerLock) {
            long deviceId = device.getDeviceId();
            boolean deviceReported = mDiscoveredDevices.containsKey(deviceId);
            scheduleOnLostAlarm(device);
            if (deviceReported) {
                NearbyDeviceParcelable oldDevice = mDiscoveredDevices.get(deviceId);
                if (device.equals(oldDevice)) {
                    return null;
                }
                return reportUpdated(device);
            }
            return reportDiscovered(device);
        }
    }

    @VisibleForTesting
    static boolean presenceFilterMatches(NearbyDeviceParcelable device,
            List<ScanFilter> scanFilters) {
        if (scanFilters.isEmpty()) {
            return true;
        }
        PresenceDiscoveryResult discoveryResult = PresenceDiscoveryResult.fromDevice(device);
        for (ScanFilter scanFilter : scanFilters) {
            PresenceScanFilter presenceScanFilter = (PresenceScanFilter) scanFilter;
            if (discoveryResult.matches(presenceScanFilter)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    ListenerOperation<IScanListener> reportDiscovered(NearbyDeviceParcelable device) {
        long deviceId = device.getDeviceId();
        return reportResult(DiscoveryResult.DEVICE_DISCOVERED, device, () -> {
            synchronized (mMultiplexerLock) {
                // Add the device to discovered devices after reporting device is
                // discovered.
                mDiscoveredDevices.put(deviceId, device);
                scheduleOnLostAlarm(device);
            }
        });
    }

    @Nullable
    ListenerOperation<IScanListener> reportUpdated(NearbyDeviceParcelable device) {
        long deviceId = device.getDeviceId();
        return reportResult(DiscoveryResult.DEVICE_UPDATED, device, () -> {
            synchronized (mMultiplexerLock) {
                // Update the new device to discovered devices after reporting device is
                // discovered.
                mDiscoveredDevices.put(deviceId, device);
                scheduleOnLostAlarm(device);
            }
        });

    }

    /** Reports an error to the client. */
    public ListenerOperation<IScanListener> reportError(@ScanCallback.ErrorCode int errorCode) {
        return listener -> listener.onError(errorCode);
    }

    @Nullable
    ListenerOperation<IScanListener> reportResult(@DiscoveryResult int result,
            NearbyDeviceParcelable device, @Nullable Runnable successReportCallback) {
        // Report the operation to AppOps.
        // NOTE: AppOps report has to be the last operation before delivering the result. Otherwise
        // we may over-report when the discovery result doesn't end up being delivered.
        if (!checkIdentity()) {
            return reportError(ScanCallback.ERROR_PERMISSION_DENIED);
        }

        return new ListenerOperation<>() {

            @Override
            public void operate(IScanListener listener) throws Exception {
                switch (result) {
                    case DiscoveryResult.DEVICE_DISCOVERED:
                        listener.onDiscovered(device);
                        break;
                    case DiscoveryResult.DEVICE_UPDATED:
                        listener.onUpdated(device);
                        break;
                    case DiscoveryResult.DEVICE_LOST:
                        listener.onLost(device);
                        break;
                }
            }

            @Override
            public void onComplete(boolean success) {
                if (success) {
                    if (successReportCallback != null) {
                        successReportCallback.run();
                        Log.d(TAG, "Successfully delivered result to caller.");
                    }
                }
            }
        };
    }

    private boolean filterCheck(NearbyDeviceParcelable device) {
        if (device.getScanType() != SCAN_TYPE_NEARBY_PRESENCE) {
            return true;
        }
        List<ScanFilter> presenceFilters = mScanRequest.getScanFilters().stream().filter(
                scanFilter -> scanFilter.getType() == SCAN_TYPE_NEARBY_PRESENCE).collect(
                Collectors.toList());
        return presenceFilterMatches(device, presenceFilters);
    }

    private boolean checkIdentity() {
        boolean result = DiscoveryPermissions.noteDiscoveryResultDelivery(mAppOpsManager,
                mCallerIdentity);
        if (!result) {
            Log.w(TAG, "[DiscoveryProviderManager] scan permission revoked "
                    + "- not forwarding results for the registration.");
        }
        return result;
    }

    @GuardedBy("mMultiplexerLock")
    private void scheduleOnLostAlarm(NearbyDeviceParcelable device) {
        long deviceId = device.getDeviceId();
        DeviceOnLostAlarm alarm = mDiscoveryOnLostAlarmPerDevice.get(deviceId);
        if (alarm == null) {
            alarm = new DeviceOnLostAlarm(device, mAlarmExecutor);
            mDiscoveryOnLostAlarmPerDevice.put(deviceId, alarm);
        }
        alarm.start();
        Log.d(TAG, "DiscoveryProviderManager updated state for " + device.getDeviceId());
    }

    /** Status of the discovery result. */
    @IntDef({DiscoveryResult.DEVICE_DISCOVERED, DiscoveryResult.DEVICE_UPDATED,
            DiscoveryResult.DEVICE_LOST})
    public @interface DiscoveryResult {
        int DEVICE_DISCOVERED = 0;
        int DEVICE_UPDATED = 1;
        int DEVICE_LOST = 2;
    }

    private class DeviceOnLostAlarm {

        private static final String NAME = "DeviceOnLostAlarm";
        private final NearbyDeviceParcelable mDevice;
        private final ScheduledExecutorService mAlarmExecutor;
        @Nullable
        private CancelableAlarm mTimeoutAlarm;

        DeviceOnLostAlarm(NearbyDeviceParcelable device, ScheduledExecutorService alarmExecutor) {
            mDevice = device;
            mAlarmExecutor = alarmExecutor;
        }

        synchronized void start() {
            cancel();
            this.mTimeoutAlarm = CancelableAlarm.createSingleAlarm(NAME, () -> {
                Log.d(TAG, String.format("%s timed out after %d ms. Reporting %s on lost.", NAME,
                        ON_LOST_TIME_OUT_MS, mDevice.getName()));
                synchronized (mMultiplexerLock) {
                    executeOperation(reportDeviceLost(mDevice));
                }
            }, ON_LOST_TIME_OUT_MS, mAlarmExecutor);
        }

        synchronized void cancel() {
            if (mTimeoutAlarm != null) {
                mTimeoutAlarm.cancel();
                mTimeoutAlarm = null;
            }
        }
    }
}
