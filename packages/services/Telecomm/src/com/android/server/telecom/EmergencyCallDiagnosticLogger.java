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

package com.android.server.telecom;

import static android.telephony.TelephonyManager.EmergencyCallDiagnosticData;

import android.os.BugreportManager;
import android.os.DropBoxManager;
import android.provider.DeviceConfig;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telephony.TelephonyManager;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * The EmergencyCallDiagnosticsLogger monitors information required to diagnose potential outgoing
 * ecall failures on the device. When a potential failure is detected, it calls a Telephony API to
 * persist relevant information (dumpsys, logcat etc.) to the dropbox. This acts as a central place
 * to determine when and what to collect.
 *
 * <p>When a bugreport is triggered, this module will read the dropbox entries and add them to the
 * telecom dump.
 */
public class EmergencyCallDiagnosticLogger extends CallsManagerListenerBase
        implements Call.Listener {

    public static final int REPORT_REASON_RANGE_START = -1; //!!DO NOT CHANGE
    public static final int REPORT_REASON_RANGE_END = 5; //increment this and add new reason above
    public static final int COLLECTION_TYPE_BUGREPORT = 10;
    public static final int COLLECTION_TYPE_TELECOM_STATE = 11;
    public static final int COLLECTION_TYPE_TELEPHONY_STATE = 12;
    public static final int COLLECTION_TYPE_LOGCAT_BUFFERS = 13;
    private static final int REPORT_REASON_STUCK_CALL_DETECTED = 0;
    private static final int REPORT_REASON_INACTIVE_CALL_TERMINATED_BY_USER_AFTER_DELAY = 1;
    private static final int REPORT_REASON_CALL_FAILED = 2;
    private static final int REPORT_REASON_CALL_CREATED_BUT_NEVER_ADDED = 3;
    private static final int REPORT_REASON_SHORT_DURATION_AFTER_GOING_ACTIVE = 4;
    private static final String DROPBOX_TAG = "ecall_diagnostic_data";
    private static final String ENABLE_BUGREPORT_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS =
            "enable_bugreport_collection_for_emergency_call_diagnostics";
    private static final String ENABLE_TELECOM_DUMP_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS =
            "enable_telecom_dump_collection_for_emergency_call_diagnostics";

    private static final String ENABLE_LOGCAT_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS =
            "enable_logcat_collection_for_emergency_call_diagnostics";
    private static final String ENABLE_TELEPHONY_DUMP_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS =
            "enable_telephony_dump_collection_for_emergency_call_diagnostics";

    private static final String DUMPSYS_ARG_FOR_DIAGNOSTICS = "EmergencyDiagnostics";

    // max text size to read from dropbox entry
    private static final int DEFAULT_MAX_READ_BYTES_PER_DROP_BOX_ENTRY = 500000;
    private static final String MAX_BYTES_PER_DROP_BOX_ENTRY = "max_bytes_per_dropbox_entry";
    private static final int MAX_DROPBOX_ENTRIES_TO_DUMP = 6;

    private final Timeouts.Adapter mTimeoutAdapter;
    // This map holds all calls, but keeps pruning non-emergency calls when we can determine it
    private final Map<Call, CallEventTimestamps> mEmergencyCallsMap = new ConcurrentHashMap<>(2);
    private final DropBoxManager mDropBoxManager;
    private final LocalLog mLocalLog = new LocalLog(10);
    private final TelephonyManager mTelephonyManager;
    private final BugreportManager mBugreportManager;
    private final Executor mAsyncTaskExecutor;
    private final ClockProxy mClockProxy;

    public EmergencyCallDiagnosticLogger(
            TelephonyManager tm,
            BugreportManager brm,
            Timeouts.Adapter timeoutAdapter, DropBoxManager dropBoxManager,
            Executor asyncTaskExecutor, ClockProxy clockProxy) {
        mTimeoutAdapter = timeoutAdapter;
        mDropBoxManager = dropBoxManager;
        mTelephonyManager = tm;
        mBugreportManager = brm;
        mAsyncTaskExecutor = asyncTaskExecutor;
        mClockProxy = clockProxy;
    }

    // this calculates time from ACTIVE --> removed
    private static long getCallTimeInActiveStateSec(CallEventTimestamps ts) {
        if (ts.getCallActiveTime() == 0 || ts.getCallRemovedTime() == 0) {
            return 0;
        } else {
            return (ts.getCallRemovedTime() - ts.getCallActiveTime()) / 1000;
        }
    }

    // this calculates time from call created --> removed
    private static long getTotalCallTimeSec(CallEventTimestamps ts) {
        if (ts.getCallRemovedTime() == 0 || ts.getCallCreatedTime() == 0) {
            return 0;
        } else {
            return (ts.getCallRemovedTime() - ts.getCallCreatedTime()) / 1000;
        }
    }

    //determines what to collect based on fail reason
    //if COLLECTION_TYPE_BUGREPORT is present in the returned list, then that
    //should be the only collection type in the list
    @VisibleForTesting
    public static List<Integer> getDataCollectionTypes(int reason) {
        switch (reason) {
            case REPORT_REASON_SHORT_DURATION_AFTER_GOING_ACTIVE:
                return Arrays.asList(COLLECTION_TYPE_TELECOM_STATE);
            case REPORT_REASON_CALL_CREATED_BUT_NEVER_ADDED:
                return Arrays.asList(
                        COLLECTION_TYPE_TELECOM_STATE, COLLECTION_TYPE_TELEPHONY_STATE);
            case REPORT_REASON_CALL_FAILED:
            case REPORT_REASON_INACTIVE_CALL_TERMINATED_BY_USER_AFTER_DELAY:
            case REPORT_REASON_STUCK_CALL_DETECTED:
                return Arrays.asList(
                        COLLECTION_TYPE_TELECOM_STATE,
                        COLLECTION_TYPE_TELEPHONY_STATE,
                        COLLECTION_TYPE_LOGCAT_BUFFERS);
            default:
        }
        return new ArrayList<>();
    }

    private int getMaxBytesPerDropboxEntry() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_TELEPHONY,
                MAX_BYTES_PER_DROP_BOX_ENTRY, DEFAULT_MAX_READ_BYTES_PER_DROP_BOX_ENTRY);
    }

    @VisibleForTesting
    public Map<Call, CallEventTimestamps> getEmergencyCallsMap() {
        return mEmergencyCallsMap;
    }

    private void triggerDiagnosticsCollection(Call call, int reason) {
        Log.i(this, "Triggering diagnostics for call %s reason: %d", call.getId(), reason);
        List<Integer> dataCollectionTypes = getDataCollectionTypes(reason);
        boolean invokeTelephonyPersistApi = false;
        CallEventTimestamps ts = mEmergencyCallsMap.get(call);
        EmergencyCallDiagnosticData.Builder callDiagnosticBuilder =
                new EmergencyCallDiagnosticData.Builder();
        for (Integer dataCollectionType : dataCollectionTypes) {
            switch (dataCollectionType) {
                case COLLECTION_TYPE_TELECOM_STATE:
                    if (isTelecomDumpCollectionEnabled()) {
                        callDiagnosticBuilder.setTelecomDumpsysCollectionEnabled(true);
                        invokeTelephonyPersistApi = true;
                    }
                    break;
                case COLLECTION_TYPE_TELEPHONY_STATE:
                    if (isTelephonyDumpCollectionEnabled()) {
                        callDiagnosticBuilder.setTelephonyDumpsysCollectionEnabled(true);
                        invokeTelephonyPersistApi = true;
                    }
                    break;
                case COLLECTION_TYPE_LOGCAT_BUFFERS:
                    if (isLogcatCollectionEnabled()) {
                        callDiagnosticBuilder.setLogcatCollectionStartTimeMillis(
                                ts.getCallCreatedTime());
                        invokeTelephonyPersistApi = true;
                    }
                    break;
                case COLLECTION_TYPE_BUGREPORT:
                    if (isBugreportCollectionEnabled()) {
                        mAsyncTaskExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                persistBugreport();
                            }
                        });
                    }
                    break;
                default:
            }
        }
        EmergencyCallDiagnosticData ecdData = callDiagnosticBuilder.build();
        if (invokeTelephonyPersistApi) {
            mAsyncTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.i(this, "Requesting Telephony to persist data %s", ecdData.toString());
                    try {
                        mTelephonyManager.persistEmergencyCallDiagnosticData(DROPBOX_TAG, ecdData);
                    } catch (Exception e) {
                        Log.w(this,
                                "Exception while invoking "
                                        + "Telephony#persistEmergencyCallDiagnosticData  %s",
                                e.toString());
                    }
                }
            });
        }
    }

    private boolean isBugreportCollectionEnabled() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_TELEPHONY,
                ENABLE_BUGREPORT_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS,
                false);
    }

    private boolean isTelecomDumpCollectionEnabled() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_TELEPHONY,
                ENABLE_TELECOM_DUMP_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS,
                true);
    }

    private boolean isLogcatCollectionEnabled() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_TELEPHONY,
                ENABLE_LOGCAT_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS,
                true);
    }

    private boolean isTelephonyDumpCollectionEnabled() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_TELEPHONY,
                ENABLE_TELEPHONY_DUMP_COLLECTION_FOR_EMERGENCY_CALL_DIAGNOSTICS,
                true);
    }

    private void persistBugreport() {
        if (isBugreportCollectionEnabled()) {
            // TODO:
        }
    }

    private boolean shouldTrackCall(Call call) {
        return (call != null && call.isEmergencyCall() && call.isOutgoing());
    }

    public void reportStuckCall(Call call) {
        if (shouldTrackCall(call)) {
            Log.i(this, "Triggering diagnostics for stuck call %s", call.getId());
            triggerDiagnosticsCollection(call, REPORT_REASON_STUCK_CALL_DETECTED);
            call.removeListener(this);
            mEmergencyCallsMap.remove(call);
        }
    }

    @Override
    public void onStartCreateConnection(Call call) {
        if (shouldTrackCall(call)) {
            long currentTime = mClockProxy.currentTimeMillis();
            call.addListener(this);
            Log.i(this, "Tracking call %s timestamp: %d", call.getId(), currentTime);
            mEmergencyCallsMap.put(call, new CallEventTimestamps(currentTime));
        }
    }

    @Override
    public void onCreateConnectionFailed(Call call) {
        if (shouldTrackCall(call)) {
            Log.i(this, "Triggering diagnostics for  call %s that was never added", call.getId());
            triggerDiagnosticsCollection(call, REPORT_REASON_CALL_CREATED_BUT_NEVER_ADDED);
            call.removeListener(this);
            mEmergencyCallsMap.remove(call);
        }
    }

    /**
     * Override of {@link CallsManagerListenerBase} to track when calls are removed
     *
     * @param call the call
     */
    @Override
    public void onCallRemoved(Call call) {
        if (call != null && (mEmergencyCallsMap.get(call) != null)) {
            call.removeListener(this);

            CallEventTimestamps ts = mEmergencyCallsMap.get(call);
            long currentTime = mClockProxy.currentTimeMillis();
            ts.setCallRemovedTime(currentTime);

            maybeTriggerDiagnosticsCollection(call, ts);
            mEmergencyCallsMap.remove(call);
        }
    }

    // !NOTE!: this method should only be called after we get onCallRemoved
    private void maybeTriggerDiagnosticsCollection(Call removedCall, CallEventTimestamps ts) {
        Log.i(this, "Evaluating emergency call for diagnostic logging: %s", removedCall.getId());
        boolean wentActive = (ts.getCallActiveTime() != 0);
        long callActiveTimeSec = (wentActive ? getCallTimeInActiveStateSec(ts) : 0);
        long timeSinceCallCreatedSec = getTotalCallTimeSec(ts);
        int dc = removedCall.getDisconnectCause().getCode();

        if (wentActive) {
            if (callActiveTimeSec
                    < mTimeoutAdapter.getEmergencyCallActiveTimeThresholdMillis() / 1000) {
                // call connected but did not go on for long
                triggerDiagnosticsCollection(
                        removedCall, REPORT_REASON_SHORT_DURATION_AFTER_GOING_ACTIVE);
            }
        } else {

            if (dc == DisconnectCause.LOCAL
                    && timeSinceCallCreatedSec
                    > mTimeoutAdapter.getEmergencyCallTimeBeforeUserDisconnectThresholdMillis()
                    / 1000) {
                // call was disconnected by the user (but not immediately)
                triggerDiagnosticsCollection(
                        removedCall, REPORT_REASON_INACTIVE_CALL_TERMINATED_BY_USER_AFTER_DELAY);
            } else if (dc != DisconnectCause.LOCAL) {
                // this can be a case for a full bugreport
                triggerDiagnosticsCollection(removedCall, REPORT_REASON_CALL_FAILED);
            }
        }
    }

    /**
     * Override of {@link com.android.server.telecom.CallsManager.CallsManagerListener} to track
     * call state changes.
     *
     * @param call     the call
     * @param oldState its old state
     * @param newState the new state
     */
    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {

        CallEventTimestamps ts = mEmergencyCallsMap.get(call);
        if (call != null && ts != null && newState == CallState.ACTIVE
                && ts.getCallActiveTime() == 0) {
            long currentTime = mClockProxy.currentTimeMillis();
            ts.setCallActiveTime(currentTime);
        }
    }

    private void dumpDiagnosticDataFromDropbox(IndentingPrintWriter pw) {
        pw.increaseIndent();
        pw.println("PERSISTED DIAGNOSTIC DATA FROM DROP BOX");
        int totalEntriesDumped = 0;
        long currentTime = mClockProxy.currentTimeMillis();
        long entriesAfterTime =
                currentTime - (mTimeoutAdapter.getDaysBackToSearchEmergencyDiagnosticEntries() * 24
                        * 60L * 60L * 1000L);
        Log.i(this, "current time: %d entriesafter: %d", currentTime, entriesAfterTime);
        DropBoxManager.Entry entry;
        entry = mDropBoxManager.getNextEntry(DROPBOX_TAG, entriesAfterTime);
        while (entry != null) {
            Log.i(this, "found entry with ts: %d", entry.getTimeMillis());
            String content[] = entry.getText(getMaxBytesPerDropboxEntry()).split(
                    System.lineSeparator());
            long entryTime = entry.getTimeMillis();
            if (content != null) {
                pw.increaseIndent();
                pw.println("------------BEGIN ENTRY (" + entryTime + ")--------");
                for (String line : content) {
                    pw.println(line);
                }
                pw.println("--------END ENTRY--------");
                pw.decreaseIndent();
                totalEntriesDumped++;
            }
            entry = mDropBoxManager.getNextEntry(DROPBOX_TAG, entryTime);
            if (totalEntriesDumped > MAX_DROPBOX_ENTRIES_TO_DUMP) {
                /*
                Since Emergency calls are a rare/once in a lifetime time occurrence for most users,
                we should not be seeing too many entries. This code just guards against edge case
                like load testing, b2b failures etc. We may accumulate a lot of dropbox entries in
                such cases, but we limit to dumping only MAX_DROPBOX_ENTRIES_TO_DUMP in the
                bugreport

                The Dropbox API in its current state does not allow to query Entries in reverse
                chronological order efficiently.
                 */

                Log.i(this, "Skipping dump for remaining entries. dumped :%d", totalEntriesDumped);
                break;
            }
        }
        pw.println("END OF PERSISTED DIAGNOSTIC DATA FROM DROP BOX");
        pw.decreaseIndent();
    }

    public void dump(IndentingPrintWriter pw, String[] args) {
        pw.increaseIndent();
        mLocalLog.dump(pw);
        pw.decreaseIndent();
        if (args != null && args.length > 0 && args[0].equals(DUMPSYS_ARG_FOR_DIAGNOSTICS)) {
            //dont read dropbox entries since this dump is triggered by telephony for diagnostics
            Log.i(this, "skipped dumping diagnostic data");
            return;
        }
        try {
            dumpDiagnosticDataFromDropbox(pw);
        } catch (Exception e) {
            pw.println("Exception was thrown while dumping diagnostic data from DropBox");
            e.printStackTrace();
        }
    }

    private static class CallEventTimestamps {

        private final long mCallCreatedTime;
        private long mCallActiveTime;
        private long mCallRemovedTime;

        public CallEventTimestamps(long createdTime) {
            mCallCreatedTime = createdTime;
        }

        public long getCallActiveTime() {
            return mCallActiveTime;
        }

        public void setCallActiveTime(long callActiveTime) {
            this.mCallActiveTime = callActiveTime;
        }

        public long getCallCreatedTime() {
            return mCallCreatedTime;
        }

        public long getCallRemovedTime() {
            return mCallRemovedTime;
        }

        public void setCallRemovedTime(long callRemovedTime) {
            this.mCallRemovedTime = callRemovedTime;
        }
    }
}
