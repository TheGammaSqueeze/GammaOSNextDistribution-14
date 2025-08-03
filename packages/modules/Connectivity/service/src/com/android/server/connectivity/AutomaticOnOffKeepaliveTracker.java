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

package com.android.server.connectivity;

import static android.net.SocketKeepalive.ERROR_INVALID_SOCKET;
import static android.net.SocketKeepalive.MIN_INTERVAL_SEC;
import static android.net.SocketKeepalive.SUCCESS;
import static android.net.SocketKeepalive.SUCCESS_PAUSED;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_SNDTIMEO;

import static com.android.net.module.util.netlink.NetlinkUtils.IO_TIMEOUT_MS;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.content.Context;
import android.net.INetd;
import android.net.ISocketKeepaliveCallback;
import android.net.MarkMaskParcel;
import android.net.Network;
import android.net.SocketKeepalive.InvalidSocketException;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.net.module.util.BinderUtils;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.HexDump;
import com.android.net.module.util.SocketUtils;
import com.android.net.module.util.netlink.InetDiagMessage;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNlAttr;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages automatic on/off socket keepalive requests.
 *
 * Provides methods to stop and start automatic keepalive requests, and keeps track of keepalives
 * across all networks. This class is tightly coupled to ConnectivityService. It is not
 * thread-safe and its handle* methods must be called only from the ConnectivityService handler
 * thread.
 */
public class AutomaticOnOffKeepaliveTracker {
    private static final String TAG = "AutomaticOnOffKeepaliveTracker";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int[] ADDRESS_FAMILIES = new int[] {AF_INET6, AF_INET};
    private static final long LOW_TCP_POLLING_INTERVAL_MS = 1_000L;
    private static final int ADJUST_TCP_POLLING_DELAY_MS = 2000;
    private static final String AUTOMATIC_ON_OFF_KEEPALIVE_DISABLE_FLAG =
            "automatic_on_off_keepalive_disable_flag";
    public static final long METRICS_COLLECTION_DURATION_MS = 24 * 60 * 60 * 1_000L;

    // ConnectivityService parses message constants from itself and AutomaticOnOffKeepaliveTracker
    // with MessageUtils for debugging purposes, and crashes if some messages have the same values.
    private static final int BASE = 2000;
    /**
     * Sent by AutomaticOnOffKeepaliveTracker periodically (when relevant) to trigger monitor
     * automatic keepalive request.
     *
     * NATT keepalives have an automatic mode where the system only sends keepalive packets when
     * TCP sockets are open over a VPN. The system will check periodically for presence of
     * such open sockets, and this message is what triggers the re-evaluation.
     *
     * obj = A Binder object associated with the keepalive.
     */
    public static final int CMD_MONITOR_AUTOMATIC_KEEPALIVE = BASE + 1;

    /**
     * Sent by AutomaticOnOffKeepaliveTracker to ConnectivityService to start a keepalive.
     *
     * obj = AutomaticKeepaliveInfo object
     */
    public static final int CMD_REQUEST_START_KEEPALIVE = BASE + 2;

    /**
     * States for {@code #AutomaticOnOffKeepalive}.
     *
     * If automatic mode is off for this keepalive, the state is STATE_ALWAYS_ON and it stays
     * so for the entire lifetime of this object.
     *
     * If enabled, a new AutomaticOnOffKeepalive starts with STATE_ENABLED. The system will monitor
     * the TCP sockets on VPN networks running on top of the specified network, and turn off
     * keepalive if there is no TCP socket any of the VPN networks. Conversely, it will turn
     * keepalive back on if any TCP socket is open on any of the VPN networks.
     *
     * When there is no TCP socket on any of the VPN networks, the state becomes STATE_SUSPENDED.
     * The {@link KeepaliveTracker.KeepaliveInfo} object is kept to remember the parameters so it
     * is possible to resume keepalive later with the same parameters.
     *
     * When the system detects some TCP socket is open on one of the VPNs while in STATE_SUSPENDED,
     * this AutomaticOnOffKeepalive goes to STATE_ENABLED again.
     *
     * When finishing keepalive, this object is deleted.
     */
    private static final int STATE_ENABLED = 0;
    private static final int STATE_SUSPENDED = 1;
    private static final int STATE_ALWAYS_ON = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "STATE_" }, value = {
            STATE_ENABLED,
            STATE_SUSPENDED,
            STATE_ALWAYS_ON
    })
    private @interface AutomaticOnOffState {}

    @NonNull
    private final Handler mConnectivityServiceHandler;
    @NonNull
    private final KeepaliveTracker mKeepaliveTracker;
    @NonNull
    private final Context mContext;
    @NonNull
    private final AlarmManager mAlarmManager;

    /**
     * The {@code inetDiagReqV2} messages for different IP family.
     *
     *   Key: Ip family type.
     * Value: Bytes array represent the {@code inetDiagReqV2}.
     *
     * This should only be accessed in the connectivity service handler thread.
     */
    private final SparseArray<byte[]> mSockDiagMsg = new SparseArray<>();
    private final Dependencies mDependencies;
    private final INetd mNetd;
    /**
     * Keeps track of automatic on/off keepalive requests.
     * This should be only updated in ConnectivityService handler thread.
     */
    private final ArrayList<AutomaticOnOffKeepalive> mAutomaticOnOffKeepalives = new ArrayList<>();
    // TODO: Remove this when TCP polling design is replaced with callback.
    private long mTestLowTcpPollingTimerUntilMs = 0;

    private static final int MAX_EVENTS_LOGS = 40;
    private final LocalLog mEventLog = new LocalLog(MAX_EVENTS_LOGS);

    private final KeepaliveStatsTracker mKeepaliveStatsTracker;

    private final long mMetricsWriteTimeBase;

    /**
     * Information about a managed keepalive.
     *
     * The keepalive in mKi is managed by this object. This object can be in one of three states
     * (in mAutomatiOnOffState) :
     * • STATE_ALWAYS_ON : this keepalive is always on
     * • STATE_ENABLED : this keepalive is currently on, and monitored for possibly being turned
     *                   off if no TCP socket is open on the VPN.
     * • STATE_SUSPENDED : this keepalive is currently off, and monitored for possibly being
     *                     resumed if a TCP socket is open on the VPN.
     * See the documentation for the states for more detail.
     */
    public class AutomaticOnOffKeepalive implements IBinder.DeathRecipient {
        @NonNull
        private final KeepaliveTracker.KeepaliveInfo mKi;
        @NonNull
        private final ISocketKeepaliveCallback mCallback;
        @Nullable
        private final FileDescriptor mFd;
        @Nullable
        private final AlarmManager.OnAlarmListener mAlarmListener;
        @AutomaticOnOffState
        private int mAutomaticOnOffState;
        @Nullable
        private final Network mUnderpinnedNetwork;

        AutomaticOnOffKeepalive(@NonNull final KeepaliveTracker.KeepaliveInfo ki,
                final boolean autoOnOff, @Nullable Network underpinnedNetwork)
                throws InvalidSocketException {
            this.mKi = Objects.requireNonNull(ki);
            mCallback = ki.mCallback;
            mUnderpinnedNetwork = underpinnedNetwork;
            // Reading DeviceConfig will check if the calling uid and calling package name are the
            // same. Clear calling identity to align the calling uid and package
            final boolean enabled = BinderUtils.withCleanCallingIdentity(
                    () -> mDependencies.isTetheringFeatureNotChickenedOut(
                            AUTOMATIC_ON_OFF_KEEPALIVE_DISABLE_FLAG));
            if (autoOnOff && enabled) {
                mAutomaticOnOffState = STATE_ENABLED;
                if (null == ki.mFd) {
                    throw new IllegalArgumentException("fd can't be null with automatic "
                            + "on/off keepalives");
                }
                mAlarmListener = () -> mConnectivityServiceHandler.obtainMessage(
                        CMD_MONITOR_AUTOMATIC_KEEPALIVE, mCallback.asBinder())
                        .sendToTarget();
            } else {
                mAutomaticOnOffState = STATE_ALWAYS_ON;
                mAlarmListener = null;
            }

            // A null fd is acceptable in KeepaliveInfo for backward compatibility of
            // PacketKeepalive API, but it must never happen with automatic keepalives.
            // TODO : remove mFd from KeepaliveInfo.
            mFd = dupFd(ki.mFd);
        }

        private FileDescriptor dupFd(FileDescriptor fd) throws InvalidSocketException {
            try {
                if (fd == null) return null;
                return Os.dup(fd);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot dup fd: ", e);
                throw new InvalidSocketException(ERROR_INVALID_SOCKET, e);
            }
        }

        @VisibleForTesting
        public ISocketKeepaliveCallback getCallback() {
            return mCallback;
        }

        public Network getNetwork() {
            return mKi.getNai().network();
        }

        @Nullable
        public Network getUnderpinnedNetwork() {
            return mUnderpinnedNetwork;
        }

        public boolean match(Network network, int slot) {
            return mKi.getNai().network().equals(network) && mKi.getSlot() == slot;
        }

        @Override
        public void binderDied() {
            mEventLog.log("Binder died : " + mCallback);
            mConnectivityServiceHandler.post(() -> cleanupAutoOnOffKeepalive(this));
        }

        /** Close this automatic on/off keepalive */
        public void close() {
            // Close the duplicated fd that maintains the lifecycle of socket. If this fd was
            // not duplicated this is a no-op.
            FileUtils.closeQuietly(mFd);
        }

        private String getAutomaticOnOffStateName(int state) {
            switch (state) {
                case STATE_ENABLED:
                    return "STATE_ENABLED";
                case STATE_SUSPENDED:
                    return "STATE_SUSPENDED";
                case STATE_ALWAYS_ON:
                    return "STATE_ALWAYS_ON";
                default:
                    Log.e(TAG, "Get unexpected state:" + state);
                    return Integer.toString(state);
            }
        }

        /**
         * Construct a new AutomaticOnOffKeepalive from existing AutomaticOnOffKeepalive with a
         * new KeepaliveInfo.
         */
        public AutomaticOnOffKeepalive withKeepaliveInfo(KeepaliveTracker.KeepaliveInfo ki)
                throws InvalidSocketException {
            return new AutomaticOnOffKeepalive(
                    ki,
                    mAutomaticOnOffState != STATE_ALWAYS_ON /* autoOnOff */,
                    mUnderpinnedNetwork);
        }

        @Override
        public String toString() {
            return "AutomaticOnOffKeepalive [ "
                    + mKi
                    + ", state=" + getAutomaticOnOffStateName(mAutomaticOnOffState)
                    + " ]";
        }
    }

    public AutomaticOnOffKeepaliveTracker(@NonNull Context context, @NonNull Handler handler) {
        this(context, handler, new Dependencies(context));
    }

    @VisibleForTesting
    public AutomaticOnOffKeepaliveTracker(@NonNull Context context, @NonNull Handler handler,
            @NonNull Dependencies dependencies) {
        mContext = Objects.requireNonNull(context);
        mDependencies = Objects.requireNonNull(dependencies);
        mConnectivityServiceHandler = Objects.requireNonNull(handler);
        mNetd = mDependencies.getNetd();
        mKeepaliveTracker = mDependencies.newKeepaliveTracker(
                mContext, mConnectivityServiceHandler);

        mAlarmManager = mDependencies.getAlarmManager(context);
        mKeepaliveStatsTracker =
                mDependencies.newKeepaliveStatsTracker(context, handler);

        final long time = mDependencies.getElapsedRealtime();
        mMetricsWriteTimeBase = time % METRICS_COLLECTION_DURATION_MS;
        if (mKeepaliveStatsTracker.isEnabled()) {
            final long triggerAtMillis = mMetricsWriteTimeBase + METRICS_COLLECTION_DURATION_MS;
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, TAG,
                    this::writeMetricsAndRescheduleAlarm, handler);
        }
    }

    private void writeMetricsAndRescheduleAlarm() {
        // If the metrics is disabled, skip writing and scheduling the next alarm.
        if (!mKeepaliveStatsTracker.isEnabled()) {
            return;
        }
        mKeepaliveStatsTracker.writeAndResetMetrics();

        final long time = mDependencies.getElapsedRealtime();
        final long triggerAtMillis =
                mMetricsWriteTimeBase
                        + (time - time % METRICS_COLLECTION_DURATION_MS)
                        + METRICS_COLLECTION_DURATION_MS;
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, TAG,
                this::writeMetricsAndRescheduleAlarm, mConnectivityServiceHandler);
    }

    private void startTcpPollingAlarm(@NonNull AutomaticOnOffKeepalive ki) {
        if (ki.mAlarmListener == null) return;

        final long triggerAtMillis =
                mDependencies.getElapsedRealtime() + getTcpPollingIntervalMs(ki);
        // Setup a non-wake up alarm.
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME, triggerAtMillis, null /* tag */,
                ki.mAlarmListener, mConnectivityServiceHandler);
    }

    /**
     * Determine if any state transition is needed for the specific automatic keepalive.
     */
    public void handleMonitorAutomaticKeepalive(@NonNull final AutomaticOnOffKeepalive ki,
            final int vpnNetId) {
        // Might happen if the automatic keepalive was removed by the app just as the alarm fires.
        if (!mAutomaticOnOffKeepalives.contains(ki)) return;
        if (STATE_ALWAYS_ON == ki.mAutomaticOnOffState) {
            throw new IllegalStateException("Should not monitor non-auto keepalive");
        }

        handleMonitorTcpConnections(ki, vpnNetId);
    }

    /**
     * Determine if disable or re-enable keepalive is needed or not based on TCP sockets status.
     */
    private void handleMonitorTcpConnections(@NonNull AutomaticOnOffKeepalive ki, int vpnNetId) {
        // Might happen if the automatic keepalive was removed by the app just as the alarm fires.
        if (!mAutomaticOnOffKeepalives.contains(ki)) return;
        if (STATE_ALWAYS_ON == ki.mAutomaticOnOffState) {
            throw new IllegalStateException("Should not monitor non-auto keepalive");
        }
        if (!isAnyTcpSocketConnected(vpnNetId)) {
            // No TCP socket exists. Stop keepalive if ENABLED, and remain SUSPENDED if currently
            // SUSPENDED.
            if (ki.mAutomaticOnOffState == STATE_ENABLED) {
                ki.mAutomaticOnOffState = STATE_SUSPENDED;
                handlePauseKeepalive(ki.mKi);
            }
        } else {
            handleMaybeResumeKeepalive(ki);
        }
        // TODO: listen to socket status instead of periodically check.
        startTcpPollingAlarm(ki);
    }

    /**
     * Resume an auto on/off keepalive, unless it's already resumed
     * @param autoKi the keepalive to resume
     */
    public void handleMaybeResumeKeepalive(@NonNull AutomaticOnOffKeepalive autoKi) {
        mEventLog.log("Resume keepalive " + autoKi.mCallback + " on " + autoKi.getNetwork());
        // Might happen if the automatic keepalive was removed by the app just as the alarm fires.
        if (!mAutomaticOnOffKeepalives.contains(autoKi)) return;
        if (STATE_ALWAYS_ON == autoKi.mAutomaticOnOffState) {
            throw new IllegalStateException("Should not resume non-auto keepalive");
        }
        if (autoKi.mAutomaticOnOffState == STATE_ENABLED) return;
        KeepaliveTracker.KeepaliveInfo newKi;
        try {
            // Get fd from AutomaticOnOffKeepalive since the fd in the original
            // KeepaliveInfo should be closed.
            newKi = autoKi.mKi.withFd(autoKi.mFd);
        } catch (InvalidSocketException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Fail to construct keepalive", e);
            mKeepaliveTracker.notifyErrorCallback(autoKi.mCallback, ERROR_INVALID_SOCKET);
            return;
        }
        autoKi.mAutomaticOnOffState = STATE_ENABLED;
        final int error = handleResumeKeepalive(newKi);
        if (error != SUCCESS) {
            // Failed to start the keepalive
            cleanupAutoOnOffKeepalive(autoKi);
        }
    }

    /**
     * Find the AutomaticOnOffKeepalive associated with a given callback.
     * @return the keepalive associated with this callback, or null if none
     */
    @Nullable
    public AutomaticOnOffKeepalive getKeepaliveForBinder(@NonNull final IBinder token) {
        ensureRunningOnHandlerThread();

        return CollectionUtils.findFirst(mAutomaticOnOffKeepalives,
                it -> it.mCallback.asBinder().equals(token));
    }

    /**
     * Handle keepalive events from lower layer.
     *
     * Forward to KeepaliveTracker.
     */
    public void handleEventSocketKeepalive(@NonNull NetworkAgentInfo nai, int slot, int reason) {
        if (mKeepaliveTracker.handleEventSocketKeepalive(nai, slot, reason)) return;

        // The keepalive was stopped and so the autoKi should be cleaned up.
        final AutomaticOnOffKeepalive autoKi =
                CollectionUtils.findFirst(
                        mAutomaticOnOffKeepalives, it -> it.match(nai.network(), slot));
        if (autoKi == null) {
            // This may occur when the autoKi gets cleaned up elsewhere (i.e
            // handleCheckKeepalivesStillValid) while waiting for the network agent to
            // start the keepalive and the network agent returns an error event.
            Log.e(TAG, "Attempt cleanup on unknown network, slot");
            return;
        }
        cleanupAutoOnOffKeepalive(autoKi);
    }

    /**
     * Handle stop all keepalives on the specific network.
     */
    public void handleStopAllKeepalives(NetworkAgentInfo nai, int reason) {
        mEventLog.log("Stop all keepalives on " + nai.network + " because " + reason);
        mKeepaliveTracker.handleStopAllKeepalives(nai, reason);
        final List<AutomaticOnOffKeepalive> matches =
                CollectionUtils.filter(mAutomaticOnOffKeepalives, it -> it.mKi.getNai() == nai);
        for (final AutomaticOnOffKeepalive ki : matches) {
            if (ki.mAutomaticOnOffState == STATE_SUSPENDED) {
                mKeepaliveTracker.finalizePausedKeepalive(ki.mKi, reason);
            }
            cleanupAutoOnOffKeepalive(ki);
        }
    }

    /**
     * Handle start keepalive contained within a message.
     *
     * The message is expected to contain a KeepaliveTracker.KeepaliveInfo.
     */
    public void handleStartKeepalive(Message message) {
        final AutomaticOnOffKeepalive target = (AutomaticOnOffKeepalive) message.obj;
        final Pair<Integer, KeepaliveTracker.KeepaliveInfo> res =
                mKeepaliveTracker.handleStartKeepalive(target.mKi);
        final int error = res.first;
        if (error != SUCCESS) {
            mEventLog.log("Failed to start keepalive " + target.mCallback + " on "
                    + target.getNetwork() + " with error " + error);
            return;
        }
        // Generate a new auto ki with the started keepalive info.
        final AutomaticOnOffKeepalive autoKi;
        try {
            autoKi = target.withKeepaliveInfo(res.second);
            target.close();
        } catch (InvalidSocketException e) {
            Log.wtf(TAG, "Fail to create AutomaticOnOffKeepalive", e);
            return;
        }

        mEventLog.log("Start keepalive " + autoKi.mCallback + " on " + autoKi.getNetwork());
        mKeepaliveStatsTracker.onStartKeepalive(
                autoKi.getNetwork(),
                autoKi.mKi.getSlot(),
                autoKi.mKi.getNai().networkCapabilities,
                autoKi.mKi.getKeepaliveIntervalSec(),
                autoKi.mKi.getUid(),
                STATE_ALWAYS_ON != autoKi.mAutomaticOnOffState);

        // Add automatic on/off request into list to track its life cycle.
        try {
            autoKi.mKi.mCallback.asBinder().linkToDeath(autoKi, 0);
        } catch (RemoteException e) {
            // The underlying keepalive performs its own cleanup
            autoKi.binderDied();
            return;
        }
        mAutomaticOnOffKeepalives.add(autoKi);
        if (STATE_ALWAYS_ON != autoKi.mAutomaticOnOffState) {
            startTcpPollingAlarm(autoKi);
        }
    }

    /**
     * Handle resume keepalive with the given KeepaliveInfo
     *
     * @return SUCCESS if the keepalive is successfully starting and the error reason otherwise.
     */
    private int handleResumeKeepalive(@NonNull final KeepaliveTracker.KeepaliveInfo ki) {
        final Pair<Integer, KeepaliveTracker.KeepaliveInfo> res =
                mKeepaliveTracker.handleStartKeepalive(ki);
        final KeepaliveTracker.KeepaliveInfo startedKi = res.second;
        final int error = res.first;
        if (error != SUCCESS) {
            mEventLog.log("Failed to resume keepalive " + startedKi.mCallback + " on "
                    + startedKi.mNai + " with error " + error);
            return error;
        }

        mKeepaliveStatsTracker.onResumeKeepalive(startedKi.getNai().network(), startedKi.getSlot());
        mEventLog.log("Resumed successfully keepalive " + startedKi.mCallback
                + " on " + startedKi.mNai);

        return SUCCESS;
    }

    private void handlePauseKeepalive(@NonNull final KeepaliveTracker.KeepaliveInfo ki) {
        mEventLog.log("Suspend keepalive " + ki.mCallback + " on " + ki.mNai);
        mKeepaliveStatsTracker.onPauseKeepalive(ki.getNai().network(), ki.getSlot());
        // TODO : mKT.handleStopKeepalive should take a KeepaliveInfo instead
        mKeepaliveTracker.handleStopKeepalive(ki.getNai(), ki.getSlot(), SUCCESS_PAUSED);
    }

    /**
     * Handle stop keepalives on the specific network with given slot.
     */
    public void handleStopKeepalive(@NonNull final AutomaticOnOffKeepalive autoKi, int reason) {
        mEventLog.log("Stop keepalive " + autoKi.mCallback + " because " + reason);
        // Stop the keepalive unless it was suspended. This includes the case where it's managed
        // but enabled, and the case where it's always on.
        if (autoKi.mAutomaticOnOffState != STATE_SUSPENDED) {
            final KeepaliveTracker.KeepaliveInfo ki = autoKi.mKi;
            mKeepaliveTracker.handleStopKeepalive(ki.getNai(), ki.getSlot(), reason);
        } else {
            mKeepaliveTracker.finalizePausedKeepalive(autoKi.mKi, reason);
        }

        cleanupAutoOnOffKeepalive(autoKi);
    }

    private void cleanupAutoOnOffKeepalive(@NonNull final AutomaticOnOffKeepalive autoKi) {
        ensureRunningOnHandlerThread();
        mKeepaliveStatsTracker.onStopKeepalive(autoKi.getNetwork(), autoKi.mKi.getSlot());
        autoKi.close();
        if (null != autoKi.mAlarmListener) mAlarmManager.cancel(autoKi.mAlarmListener);

        // If the KI is not in the array, it's because it was already removed, or it was never
        // added ; the only ways this can happen is if the keepalive is stopped by the app and the
        // app dies immediately, or if the app died before the link to death could be registered.
        if (!mAutomaticOnOffKeepalives.remove(autoKi)) return;

        autoKi.mKi.mCallback.asBinder().unlinkToDeath(autoKi, 0);
    }

    /**
     * Called when requesting that keepalives be started on a IPsec NAT-T socket. See
     * {@link android.net.SocketKeepalive}.
     *
     * Forward to KeepaliveTracker.
     **/
    public void startNattKeepalive(@Nullable NetworkAgentInfo nai,
            @Nullable FileDescriptor fd,
            int intervalSeconds,
            @NonNull ISocketKeepaliveCallback cb,
            @NonNull String srcAddrString,
            int srcPort,
            @NonNull String dstAddrString,
            int dstPort, boolean automaticOnOffKeepalives, @Nullable Network underpinnedNetwork) {
        final KeepaliveTracker.KeepaliveInfo ki = mKeepaliveTracker.makeNattKeepaliveInfo(nai, fd,
                intervalSeconds, cb, srcAddrString, srcPort, dstAddrString, dstPort);
        if (null == ki) return;
        try {
            final AutomaticOnOffKeepalive autoKi = new AutomaticOnOffKeepalive(ki,
                    automaticOnOffKeepalives, underpinnedNetwork);
            mEventLog.log("Start natt keepalive " + cb + " on " + nai.network
                    + " " + srcAddrString + ":" + srcPort
                    + " → " + dstAddrString + ":" + dstPort
                    + " auto=" + autoKi
                    + " underpinned=" + underpinnedNetwork);
            mConnectivityServiceHandler.obtainMessage(CMD_REQUEST_START_KEEPALIVE, autoKi)
                    .sendToTarget();
        } catch (InvalidSocketException e) {
            mKeepaliveTracker.notifyErrorCallback(cb, e.error);
        }
    }

    /**
     * Called when requesting that keepalives be started on a IPsec NAT-T socket. See
     * {@link android.net.SocketKeepalive}.
     *
     * Forward to KeepaliveTracker.
     **/
    public void startNattKeepalive(@Nullable NetworkAgentInfo nai,
            @Nullable FileDescriptor fd,
            int resourceId,
            int intervalSeconds,
            @NonNull ISocketKeepaliveCallback cb,
            @NonNull String srcAddrString,
            @NonNull String dstAddrString,
            int dstPort,
            boolean automaticOnOffKeepalives,
            @Nullable Network underpinnedNetwork) {
        final KeepaliveTracker.KeepaliveInfo ki = mKeepaliveTracker.makeNattKeepaliveInfo(nai, fd,
                resourceId, intervalSeconds, cb, srcAddrString, dstAddrString, dstPort);
        if (null == ki) return;
        try {
            final AutomaticOnOffKeepalive autoKi = new AutomaticOnOffKeepalive(ki,
                    automaticOnOffKeepalives, underpinnedNetwork);
            mEventLog.log("Start natt keepalive " + cb + " on " + nai.network
                    + " " + srcAddrString
                    + " → " + dstAddrString + ":" + dstPort
                    + " auto=" + autoKi
                    + " underpinned=" + underpinnedNetwork);
            mConnectivityServiceHandler.obtainMessage(CMD_REQUEST_START_KEEPALIVE, autoKi)
                    .sendToTarget();
        } catch (InvalidSocketException e) {
            mKeepaliveTracker.notifyErrorCallback(cb, e.error);
        }
    }

    /**
     * Called by ConnectivityService to start TCP keepalive on a file descriptor.
     *
     * In order to offload keepalive for application correctly, sequence number, ack number and
     * other fields are needed to form the keepalive packet. Thus, this function synchronously
     * puts the socket into repair mode to get the necessary information. After the socket has been
     * put into repair mode, the application cannot access the socket until reverted to normal.
     * See {@link android.net.SocketKeepalive}.
     *
     * Forward to KeepaliveTracker.
     **/
    public void startTcpKeepalive(@Nullable NetworkAgentInfo nai,
            @NonNull FileDescriptor fd,
            int intervalSeconds,
            @NonNull ISocketKeepaliveCallback cb) {
        final KeepaliveTracker.KeepaliveInfo ki = mKeepaliveTracker.makeTcpKeepaliveInfo(nai, fd,
                intervalSeconds, cb);
        if (null == ki) return;
        try {
            final AutomaticOnOffKeepalive autoKi = new AutomaticOnOffKeepalive(ki,
                    false /* autoOnOff, tcp keepalives are never auto on/off */,
                    null /* underpinnedNetwork, tcp keepalives do not refer to this */);
            mConnectivityServiceHandler.obtainMessage(CMD_REQUEST_START_KEEPALIVE, autoKi)
                    .sendToTarget();
        } catch (InvalidSocketException e) {
            mKeepaliveTracker.notifyErrorCallback(cb, e.error);
        }
    }

    /**
     * Dump AutomaticOnOffKeepaliveTracker state.
     * This should be only be called in ConnectivityService handler thread.
     */
    public void dump(IndentingPrintWriter pw) {
        ensureRunningOnHandlerThread();
        mKeepaliveTracker.dump(pw);
        // Reading DeviceConfig will check if the calling uid and calling package name are the same.
        // Clear calling identity to align the calling uid and package so that it won't fail if cts
        // would like to call dump()
        final boolean featureEnabled = BinderUtils.withCleanCallingIdentity(
                () -> mDependencies.isTetheringFeatureNotChickenedOut(
                        AUTOMATIC_ON_OFF_KEEPALIVE_DISABLE_FLAG));
        pw.println("AutomaticOnOff enabled: " + featureEnabled);
        pw.increaseIndent();
        for (AutomaticOnOffKeepalive autoKi : mAutomaticOnOffKeepalives) {
            pw.println(autoKi.toString());
        }
        pw.decreaseIndent();

        pw.println("Events (most recent first):");
        pw.increaseIndent();
        mEventLog.reverseDump(pw);
        pw.decreaseIndent();

        pw.println();
        mKeepaliveStatsTracker.dump(pw);
    }

    /**
     * Check all keepalives on the network are still valid.
     *
     * Forward to KeepaliveTracker.
     */
    public void handleCheckKeepalivesStillValid(NetworkAgentInfo nai) {
        ArrayList<Pair<AutomaticOnOffKeepalive, Integer>> invalidKeepalives = null;

        for (final AutomaticOnOffKeepalive autoKi : mAutomaticOnOffKeepalives) {
            if (!nai.equals(autoKi.mKi.mNai)) continue;
            final int error = autoKi.mKi.isValid();
            if (error != SUCCESS) {
                if (invalidKeepalives == null) {
                    invalidKeepalives = new ArrayList<>();
                }
                invalidKeepalives.add(Pair.create(autoKi, error));
            }
        }
        if (invalidKeepalives == null) return;
        for (final Pair<AutomaticOnOffKeepalive, Integer> keepaliveAndError : invalidKeepalives) {
            handleStopKeepalive(keepaliveAndError.first, keepaliveAndError.second);
        }
    }

    @VisibleForTesting
    boolean isAnyTcpSocketConnected(int netId) {
        FileDescriptor fd = null;

        try {
            fd = mDependencies.createConnectedNetlinkSocket();

            // Get network mask
            final MarkMaskParcel parcel = mNetd.getFwmarkForNetwork(netId);
            final int networkMark = (parcel != null) ? parcel.mark : NetlinkUtils.UNKNOWN_MARK;
            final int networkMask = (parcel != null) ? parcel.mask : NetlinkUtils.NULL_MASK;

            // Send request for each IP family
            for (final int family : ADDRESS_FAMILIES) {
                if (isAnyTcpSocketConnectedForFamily(fd, family, networkMark, networkMask)) {
                    return true;
                }
            }
        } catch (ErrnoException | SocketException | InterruptedIOException | RemoteException e) {
            Log.e(TAG, "Fail to get socket info via netlink.", e);
        } finally {
            SocketUtils.closeSocketQuietly(fd);
        }

        return false;
    }

    private boolean isAnyTcpSocketConnectedForFamily(FileDescriptor fd, int family, int networkMark,
            int networkMask)
            throws ErrnoException, InterruptedIOException {
        ensureRunningOnHandlerThread();
        // Build SocketDiag messages and cache it.
        if (mSockDiagMsg.get(family) == null) {
            mSockDiagMsg.put(family, InetDiagMessage.buildInetDiagReqForAliveTcpSockets(family));
        }
        mDependencies.sendRequest(fd, mSockDiagMsg.get(family));

        // Iteration limitation as a protection to avoid possible infinite loops.
        // DEFAULT_RECV_BUFSIZE could read more than 20 sockets per time. Max iteration
        // should be enough to go through reasonable TCP sockets in the device.
        final int maxIteration = 100;
        int parsingIteration = 0;
        while (parsingIteration < maxIteration) {
            final ByteBuffer bytes = mDependencies.recvSockDiagResponse(fd);

            try {
                while (NetlinkUtils.enoughBytesRemainForValidNlMsg(bytes)) {
                    // NetlinkMessage.parse() will move the byte buffer position.
                    // TODO: Parse dst address information to filter socket.
                    final NetlinkMessage nlMsg = NetlinkMessage.parse(
                            bytes, OsConstants.NETLINK_INET_DIAG);
                    if (!(nlMsg instanceof InetDiagMessage)) {
                        if (DBG) Log.e(TAG, "Not a SOCK_DIAG_BY_FAMILY msg");
                        return false;
                    }

                    final InetDiagMessage diagMsg = (InetDiagMessage) nlMsg;
                    if (isTargetTcpSocket(diagMsg, networkMark, networkMask)) {
                        if (DBG) {
                            Log.d(TAG, String.format("Found open TCP connection by uid %d to %s"
                                            + " cookie %d",
                                    diagMsg.inetDiagMsg.idiag_uid,
                                    diagMsg.inetDiagMsg.id.remSocketAddress,
                                    diagMsg.inetDiagMsg.id.cookie));
                        }
                        return true;
                    }
                }
            } catch (BufferUnderflowException e) {
                // The exception happens in random place in either header position or any data
                // position. Partial bytes from the middle of the byte buffer may not be enough to
                // clarify, so print out the content before the error to possibly prevent printing
                // the whole 8K buffer.
                final int exceptionPos = bytes.position();
                final String hex = HexDump.dumpHexString(bytes.array(), 0, exceptionPos);
                Log.e(TAG, "Unexpected socket info parsing: " + hex, e);
            }

            parsingIteration++;
        }
        return false;
    }

    private boolean isTargetTcpSocket(@NonNull InetDiagMessage diagMsg,
            int networkMark, int networkMask) {
        final int mark = readSocketDataAndReturnMark(diagMsg);
        return (mark & networkMask) == networkMark;
    }

    private int readSocketDataAndReturnMark(@NonNull InetDiagMessage diagMsg) {
        int mark = NetlinkUtils.INIT_MARK_VALUE;
        // Get socket mark
        for (StructNlAttr attr : diagMsg.nlAttrs) {
            if (attr.nla_type == NetlinkUtils.INET_DIAG_MARK) {
                // The netlink attributes should contain only one INET_DIAG_MARK for each socket.
                mark = attr.getValueAsInteger();
                break;
            }
        }
        return mark;
    }

    private void ensureRunningOnHandlerThread() {
        if (mConnectivityServiceHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on handler thread: " + Thread.currentThread().getName());
        }
    }

    private long getTcpPollingIntervalMs(@NonNull AutomaticOnOffKeepalive ki) {
        final boolean useLowTimer = mTestLowTcpPollingTimerUntilMs > System.currentTimeMillis();
        // Adjust the polling interval to be smaller than the keepalive delay to preserve
        // some time for the system to restart the keepalive.
        final int timer = ki.mKi.getKeepaliveIntervalSec() * 1000 - ADJUST_TCP_POLLING_DELAY_MS;
        if (timer < MIN_INTERVAL_SEC) {
            Log.wtf(TAG, "Unreasonably low keepalive delay: " + ki.mKi.getKeepaliveIntervalSec());
        }
        return useLowTimer ? LOW_TCP_POLLING_INTERVAL_MS : Math.max(timer, MIN_INTERVAL_SEC);
    }

    /**
     * Temporarily use low TCP polling timer for testing.
     * The value works when the time set is more than {@link System.currentTimeMillis()}.
     */
    public void handleSetTestLowTcpPollingTimer(long timeMs) {
        Log.d(TAG, "handleSetTestLowTcpPollingTimer: " + timeMs);
        mTestLowTcpPollingTimerUntilMs = timeMs;
    }

    /**
     * Dependencies class for testing.
     */
    @VisibleForTesting
    public static class Dependencies {
        private final Context mContext;

        public Dependencies(final Context context) {
            mContext = context;
        }

        /**
         * Create a netlink socket connected to the kernel.
         *
         * @return fd the fileDescriptor of the socket.
         */
        public FileDescriptor createConnectedNetlinkSocket()
                throws ErrnoException, SocketException {
            final FileDescriptor fd = NetlinkUtils.createNetLinkInetDiagSocket();
            NetlinkUtils.connectToKernel(fd);
            Os.setsockoptTimeval(fd, SOL_SOCKET, SO_SNDTIMEO,
                    StructTimeval.fromMillis(IO_TIMEOUT_MS));
            return fd;
        }

        /**
         * Send composed message request to kernel.
         *
         * The given FileDescriptor is expected to be created by
         * {@link #createConnectedNetlinkSocket} or equivalent way.
         *
         * @param fd a netlink socket {@code FileDescriptor} connected to the kernel.
         * @param msg the byte array representing the request message to write to kernel.
         */
        public void sendRequest(@NonNull final FileDescriptor fd,
                @NonNull final byte[] msg)
                throws ErrnoException, InterruptedIOException {
            Os.write(fd, msg, 0 /* byteOffset */, msg.length);
        }

        /**
         * Get an INetd connector.
         */
        public INetd getNetd() {
            return INetd.Stub.asInterface(
                    (IBinder) mContext.getSystemService(Context.NETD_SERVICE));
        }

        /**
         * Get an instance of AlarmManager
         */
        public AlarmManager getAlarmManager(@NonNull final Context ctx) {
            return ctx.getSystemService(AlarmManager.class);
        }

        /**
         * Receive the response message from kernel via given {@code FileDescriptor}.
         * The usage should follow the {@code #sendRequest} call with the same
         * FileDescriptor.
         *
         * The overall response may be large but the individual messages should not be
         * excessively large(8-16kB) because trying to get the kernel to return
         * everything in one big buffer is inefficient as it forces the kernel to allocate
         * large chunks of linearly physically contiguous memory. The usage should iterate the
         * call of this method until the end of the overall message.
         *
         * The default receiving buffer size should be small enough that it is always
         * processed within the {@link NetlinkUtils#IO_TIMEOUT_MS} timeout.
         */
        public ByteBuffer recvSockDiagResponse(@NonNull final FileDescriptor fd)
                throws ErrnoException, InterruptedIOException {
            return NetlinkUtils.recvMessage(
                    fd, NetlinkUtils.DEFAULT_RECV_BUFSIZE, NetlinkUtils.IO_TIMEOUT_MS);
        }

        /**
         * Construct a new KeepaliveTracker.
         */
        public KeepaliveTracker newKeepaliveTracker(@NonNull Context context,
                @NonNull Handler connectivityserviceHander) {
            return new KeepaliveTracker(mContext, connectivityserviceHander);
        }

        /**
         * Construct a new KeepaliveStatsTracker.
         */
        public KeepaliveStatsTracker newKeepaliveStatsTracker(@NonNull Context context,
                @NonNull Handler connectivityserviceHander) {
            return new KeepaliveStatsTracker(context, connectivityserviceHander);
        }

        /**
         * Find out if a feature is not disabled from DeviceConfig.
         *
         * @param name The name of the property to look up.
         * @return whether the feature is enabled
         */
        public boolean isTetheringFeatureNotChickenedOut(@NonNull final String name) {
            return DeviceConfigUtils.isTetheringFeatureNotChickenedOut(mContext, name);
        }

        /**
         * Returns milliseconds since boot, including time spent in sleep.
         *
         * @return elapsed milliseconds since boot.
         */
        public long getElapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }
    }
}
