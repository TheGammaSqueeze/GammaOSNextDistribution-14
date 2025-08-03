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

package com.android.car.watchdog;

import static android.car.watchdog.CarWatchdogManager.TIMEOUT_CRITICAL;
import static android.car.watchdog.CarWatchdogManager.TIMEOUT_MODERATE;
import static android.car.watchdog.CarWatchdogManager.TIMEOUT_NORMAL;

import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.ProcessIdentifier;
import android.car.builtin.util.Slogf;
import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarServiceHelperWrapper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles clients' health status checking and reporting the statuses to the watchdog daemon.
 */
public final class WatchdogProcessHandler {
    static final String PROPERTY_RO_CLIENT_HEALTHCHECK_INTERVAL =
            "ro.carwatchdog.client_healthcheck.interval";
    static final int MISSING_INT_PROPERTY_VALUE = -1;

    private static final int[] ALL_TIMEOUTS =
            { TIMEOUT_CRITICAL, TIMEOUT_MODERATE, TIMEOUT_NORMAL };

    private final ICarWatchdogServiceForSystem mWatchdogServiceForSystem;
    private final CarWatchdogDaemonHelper mCarWatchdogDaemonHelper;
    private final PackageInfoHandler mPackageInfoHandler;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Handler mServiceHandler = new Handler(getHandlerThread(
            CarWatchdogService.class.getSimpleName()).getLooper());
    private final Object mLock = new Object();
    /*
     * Keeps the list of car watchdog client according to timeout:
     * key => timeout, value => ClientInfo list.
     * The value of SparseArray is guarded by mLock.
     */
    @GuardedBy("mLock")
    private final SparseArray<ArrayList<ClientInfo>> mClientMap = new SparseArray<>();
    /*
     * Keeps the map of car watchdog client being checked by CarWatchdogService according to
     * timeout: key => timeout, value => ClientInfo map.
     * The value is also a map: key => session id, value => ClientInfo.
     */
    @GuardedBy("mLock")
    private final SparseArray<SparseArray<ClientInfo>> mPingedClientMap = new SparseArray<>();
    /*
     * Keeps whether client health checking is being performed according to timeout:
     * key => timeout, value => boolean (whether client health checking is being performed).
     * The value of SparseArray is guarded by mLock.
     */
    @GuardedBy("mLock")
    private final SparseArray<Boolean> mClientCheckInProgress = new SparseArray<>();
    @GuardedBy("mLock")
    private final ArrayList<ClientInfo> mClientsNotResponding = new ArrayList<>();
    // mLastSessionId should only be accessed from the main thread.
    @GuardedBy("mLock")
    private int mLastSessionId;
    @GuardedBy("mLock")
    private final SparseBooleanArray mStoppedUser = new SparseBooleanArray();

    private long mOverriddenClientHealthCheckWindowMs = MISSING_INT_PROPERTY_VALUE;

    public WatchdogProcessHandler(ICarWatchdogServiceForSystem serviceImpl,
            CarWatchdogDaemonHelper daemonHelper, PackageInfoHandler packageInfoHandler) {
        mWatchdogServiceForSystem = serviceImpl;
        mCarWatchdogDaemonHelper = daemonHelper;
        mPackageInfoHandler = packageInfoHandler;
    }

    /** Initializes the handler. */
    public void init() {
        synchronized (mLock) {
            for (int timeout : ALL_TIMEOUTS) {
                mClientMap.put(timeout, new ArrayList<ClientInfo>());
                mPingedClientMap.put(timeout, new SparseArray<ClientInfo>());
                mClientCheckInProgress.put(timeout, false);
            }
        }
        // Overridden timeout value must be greater than  or equal to the maximum possible timeout
        // value. Otherwise, clients will be pinged more frequently than the guaranteed timeout
        // duration.
        int clientHealthCheckWindowSec = SystemProperties.getInt(
                PROPERTY_RO_CLIENT_HEALTHCHECK_INTERVAL, MISSING_INT_PROPERTY_VALUE);
        if (clientHealthCheckWindowSec != MISSING_INT_PROPERTY_VALUE) {
            mOverriddenClientHealthCheckWindowMs = Math.max(clientHealthCheckWindowSec * 1000L,
                    getTimeoutDurationMs(TIMEOUT_NORMAL));
        }
        if (CarWatchdogService.DEBUG) {
            Slogf.d(CarWatchdogService.TAG, "WatchdogProcessHandler is initialized");
        }
    }

    /** Dumps its state. */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("Registered clients");
            writer.increaseIndent();
            int count = 1;
            for (int timeout : ALL_TIMEOUTS) {
                ArrayList<ClientInfo> clients = mClientMap.get(timeout);
                String timeoutStr = timeoutToString(timeout);
                for (ClientInfo clientInfo : clients) {
                    writer.printf("client #%d: timeout = %s, pid = %d\n", count++, timeoutStr,
                            clientInfo.pid);
                }
            }
            writer.printf("Stopped users: ");
            int size = mStoppedUser.size();
            if (size > 0) {
                writer.printf("%d", mStoppedUser.keyAt(0));
                for (int i = 1; i < size; i++) {
                    writer.printf(", %d", mStoppedUser.keyAt(i));
                }
                writer.println();
            } else {
                writer.println("none");
            }
            writer.decreaseIndent();
        }
    }

    /** Dumps its state in proto format. */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            long systemHealthDumpToken = proto.start(
                    CarWatchdogDumpProto.SYSTEM_HEALTH_DUMP);
            for (int timeout : ALL_TIMEOUTS) {
                ArrayList<ClientInfo> clients = mClientMap.get(timeout);
                for (ClientInfo clientInfo : clients) {
                    long registeredClientsToken = proto.start(
                            CarWatchdogDumpProto.SystemHealthDump.REGISTERED_CLIENTS);
                    proto.write(CarWatchdogDumpProto.RegisteredClient.PID, clientInfo.pid);
                    long userPackageInfoToken = proto.start(
                            CarWatchdogDumpProto.RegisteredClient.USER_PACKAGE_INFO);
                    proto.write(UserPackageInfo.USER_ID, clientInfo.userId);
                    proto.write(UserPackageInfo.PACKAGE_NAME, clientInfo.packageName);
                    proto.end(userPackageInfoToken);
                    proto.write(CarWatchdogDumpProto.RegisteredClient.HEALTH_CHECK_TIMEOUT,
                            timeout + 1);
                    proto.end(registeredClientsToken);
                }
            }
            for (int i = 0; i < mStoppedUser.size(); i++) {
                proto.write(CarWatchdogDumpProto.SystemHealthDump.STOPPED_USERS,
                        mStoppedUser.keyAt(i));
            }
            proto.end(systemHealthDumpToken);
        }
    }

    /** Registers the client callback */
    public void registerClient(ICarWatchdogServiceCallback client, int timeout) {
        synchronized (mLock) {
            ArrayList<ClientInfo> clients = mClientMap.get(timeout);
            if (clients == null) {
                Slogf.w(CarWatchdogService.TAG, "Cannot register the client: invalid timeout");
                return;
            }
            IBinder binder = client.asBinder();
            for (int i = 0; i < clients.size(); i++) {
                ClientInfo clientInfo = clients.get(i);
                if (binder == clientInfo.client.asBinder()) {
                    throw new IllegalStateException(
                            "Cannot register the client: the client(pid:" + clientInfo.pid
                                    + ") has been already registered");
                }
            }
            int pid = Binder.getCallingPid();
            int userId = Binder.getCallingUserHandle().getIdentifier();
            int callingUid = Binder.getCallingUid();
            ClientInfo clientInfo = new ClientInfo(client, pid, userId, timeout);
            // PackageInfoHandler may need to retrieve the packageName from system server
            // using a binder call. Thus, retrieving the packageName from PackageInfoHandler is
            // posted on the looper and is resolved asynchronously.
            mServiceHandler.post(() -> {
                clientInfo.packageName = mPackageInfoHandler.getNamesForUids(
                        new int[]{callingUid}).get(callingUid, null);
            });
            try {
                clientInfo.linkToDeath();
            } catch (RemoteException e) {
                Slogf.w(CarWatchdogService.TAG,
                        "Cannot register the client: linkToDeath to the client failed");
                return;
            }
            clients.add(clientInfo);
            if (CarWatchdogService.DEBUG) {
                Slogf.d(CarWatchdogService.TAG, "Registered client: %s", clientInfo);
            }
        }
    }

    /** Unregisters the previously registered client callback */
    public void unregisterClient(ICarWatchdogServiceCallback client) {
        ClientInfo clientInfo;
        synchronized (mLock) {
            IBinder binder = client.asBinder();
            // Even if a client did not respond to the latest ping, CarWatchdogService should honor
            // the unregister request at this point and remove it from all internal caches.
            // Otherwise, the client might be killed even after unregistering.
            Optional<ClientInfo> optionalClientInfo = removeFromClientMapsLocked(binder);
            if (optionalClientInfo.isEmpty()) {
                Slogf.w(CarWatchdogService.TAG,
                        "Cannot unregister the client: the client has not been registered before");
                return;
            }
            clientInfo = optionalClientInfo.get();
            for (int i = 0; i < mClientsNotResponding.size(); i++) {
                ClientInfo notRespondingClientInfo = mClientsNotResponding.get(i);
                if (binder == notRespondingClientInfo.client.asBinder()) {
                    mClientsNotResponding.remove(i);
                    break;
                }
            }
        }
        if (CarWatchdogService.DEBUG) {
            Slogf.d(CarWatchdogService.TAG, "Unregistered client: %s", clientInfo);
        }
    }

    @GuardedBy("mLock")
    private Optional<ClientInfo> removeFromClientMapsLocked(IBinder binder) {
        for (int timeout : ALL_TIMEOUTS) {
            ArrayList<ClientInfo> clients = mClientMap.get(timeout);
            for (int i = 0; i < clients.size(); i++) {
                ClientInfo clientInfo = clients.get(i);
                if (binder != clientInfo.client.asBinder()) {
                    continue;
                }
                clientInfo.unlinkToDeath();
                clients.remove(i);
                SparseArray<ClientInfo> pingedClients = mPingedClientMap.get(timeout);
                if (pingedClients != null) {
                    pingedClients.remove(clientInfo.sessionId);
                }
                return Optional.of(clientInfo);
            }
        }
        return Optional.empty();
    }

    /** Tells the handler that the client is alive. */
    public void tellClientAlive(ICarWatchdogServiceCallback client, int sessionId) {
        synchronized (mLock) {
            for (int timeout : ALL_TIMEOUTS) {
                if (!mClientCheckInProgress.get(timeout)) {
                    continue;
                }
                SparseArray<ClientInfo> pingedClients = mPingedClientMap.get(timeout);
                ClientInfo clientInfo = pingedClients.get(sessionId);
                if (clientInfo != null && clientInfo.client.asBinder() == client.asBinder()) {
                    pingedClients.remove(sessionId);
                    return;
                }
            }
        }
    }

    /** Updates the user stopped state */
    public void updateUserState(@UserIdInt int userId, boolean isStopped) {
        synchronized (mLock) {
            if (isStopped) {
                mStoppedUser.put(userId, true);
            } else {
                mStoppedUser.delete(userId);
            }
        }
    }

    /** Posts health check message */
    public void postHealthCheckMessage(int sessionId) {
        mMainHandler.postAtFrontOfQueue(() -> doHealthCheck(sessionId));
    }

    /** Returns the registered and alive client count. */
    public int getClientCount(int timeout) {
        synchronized (mLock) {
            ArrayList<ClientInfo> clients = mClientMap.get(timeout);
            return clients != null ? clients.size() : 0;
        }
    }

    /** Resets pinged clients before health checking */
    public void prepareHealthCheck() {
        synchronized (mLock) {
            for (int timeout : ALL_TIMEOUTS) {
                SparseArray<ClientInfo> pingedClients = mPingedClientMap.get(timeout);
                pingedClients.clear();
            }
        }
    }

    /**
     * Asynchronously fetches the AIDL VHAL pid from SystemServer.
     *
     * On fetching the AIDL VHAL pid, car watchdog daemon is updated via an async callback.
     */
    public void asyncFetchAidlVhalPid() {
        mServiceHandler.post(() -> {
            int pid = CarServiceHelperWrapper.getInstance().fetchAidlVhalPid();
            if (pid < 0) {
                Slogf.e(CarWatchdogService.TAG, "Failed to fetch AIDL VHAL pid from"
                        + " CarServiceHelperService");
                return;
            }
            try {
                mCarWatchdogDaemonHelper.onAidlVhalPidFetched(pid);
            } catch (RemoteException e) {
                Slogf.e(CarWatchdogService.TAG,
                        "Failed to notify car watchdog daemon of the AIDL VHAL pid");
            }
        });
    }

    /** Enables/disables the watchdog daemon client health check process. */
    void controlProcessHealthCheck(boolean enable) {
        try {
            mCarWatchdogDaemonHelper.controlProcessHealthCheck(enable);
        } catch (RemoteException e) {
            Slogf.w(CarWatchdogService.TAG,
                    "Cannot enable/disable the car watchdog daemon health check process: %s", e);
        }
    }

    private void onClientDeath(ICarWatchdogServiceCallback client, int timeout) {
        synchronized (mLock) {
            removeClientLocked(client.asBinder(), timeout);
        }
    }

    private void doHealthCheck(int sessionId) {
        // For critical clients, the response status are checked just before reporting to car
        // watchdog daemon. For moderate and normal clients, the status are checked after allowed
        // delay per timeout.
        analyzeClientResponse(TIMEOUT_CRITICAL);
        reportHealthCheckResult(sessionId);
        sendPingToClients(TIMEOUT_CRITICAL);
        sendPingToClientsAndCheck(TIMEOUT_MODERATE);
        sendPingToClientsAndCheck(TIMEOUT_NORMAL);
    }

    private void analyzeClientResponse(int timeout) {
        // Clients which are not responding are stored in mClientsNotResponding, and will be dumped
        // and killed at the next response of CarWatchdogService to car watchdog daemon.
        synchronized (mLock) {
            SparseArray<ClientInfo> pingedClients = mPingedClientMap.get(timeout);
            for (int i = 0; i < pingedClients.size(); i++) {
                ClientInfo clientInfo = pingedClients.valueAt(i);
                if (mStoppedUser.get(clientInfo.userId)) {
                    continue;
                }
                mClientsNotResponding.add(clientInfo);
                removeClientLocked(clientInfo.client.asBinder(), timeout);
            }
            mClientCheckInProgress.setValueAt(timeout, false);
        }
    }

    private void sendPingToClients(int timeout) {
        ArrayList<ClientInfo> clientsToCheck;
        synchronized (mLock) {
            SparseArray<ClientInfo> pingedClients = mPingedClientMap.get(timeout);
            pingedClients.clear();
            clientsToCheck = new ArrayList<>(mClientMap.get(timeout));
            for (int i = 0; i < clientsToCheck.size(); i++) {
                ClientInfo clientInfo = clientsToCheck.get(i);
                if (mStoppedUser.get(clientInfo.userId)) {
                    continue;
                }
                int sessionId = getNewSessionId();
                clientInfo.sessionId = sessionId;
                pingedClients.put(sessionId, clientInfo);
            }
            mClientCheckInProgress.setValueAt(timeout, true);
        }

        for (int i = 0; i < clientsToCheck.size(); i++) {
            ClientInfo clientInfo = clientsToCheck.get(i);
            try {
                clientInfo.client.onCheckHealthStatus(clientInfo.sessionId, timeout);
            } catch (RemoteException e) {
                Slogf.w(CarWatchdogService.TAG,
                        "Sending a ping message to client(pid: %d) failed: %s",
                        clientInfo.pid, e);
                synchronized (mLock) {
                    mPingedClientMap.get(timeout).remove(clientInfo.sessionId);
                }
            }
        }
    }

    private void sendPingToClientsAndCheck(int timeout) {
        synchronized (mLock) {
            if (mClientCheckInProgress.get(timeout)) {
                return;
            }
        }
        sendPingToClients(timeout);
        mMainHandler.postDelayed(
                () -> analyzeClientResponse(timeout), getTimeoutDurationMs(timeout));
    }

    private int getNewSessionId() {
        synchronized (mLock) {
            if (++mLastSessionId <= 0) {
                mLastSessionId = 1;
            }
            return mLastSessionId;
        }
    }

    @GuardedBy("mLock")
    private void removeClientLocked(IBinder clientBinder, int timeout) {
        ArrayList<ClientInfo> clients = mClientMap.get(timeout);
        for (int i = 0; i < clients.size(); i++) {
            ClientInfo clientInfo = clients.get(i);
            if (clientBinder == clientInfo.client.asBinder()) {
                clients.remove(i);
                return;
            }
        }
    }

    private void reportHealthCheckResult(int sessionId) {
        List<ProcessIdentifier> clientsNotResponding;
        ArrayList<ClientInfo> clientsToNotify;
        synchronized (mLock) {
            clientsNotResponding = toProcessIdentifierList(mClientsNotResponding);
            clientsToNotify = new ArrayList<>(mClientsNotResponding);
            mClientsNotResponding.clear();
        }
        for (int i = 0; i < clientsToNotify.size(); i++) {
            ClientInfo clientInfo = clientsToNotify.get(i);
            try {
                clientInfo.client.onPrepareProcessTermination();
            } catch (RemoteException e) {
                Slogf.w(CarWatchdogService.TAG,
                        "Notifying onPrepareProcessTermination to client(pid: %d) failed: %s",
                        clientInfo.pid, e);
            }
        }

        try {
            mCarWatchdogDaemonHelper.tellCarWatchdogServiceAlive(
                    mWatchdogServiceForSystem, clientsNotResponding, sessionId);
        } catch (RemoteException | RuntimeException e) {
            Slogf.w(CarWatchdogService.TAG,
                    "Cannot respond to car watchdog daemon (sessionId=%d): %s", sessionId, e);
        }
    }

    @NonNull
    private List<ProcessIdentifier> toProcessIdentifierList(
            @NonNull ArrayList<ClientInfo> clientInfos) {
        List<ProcessIdentifier> processIdentifiers = new ArrayList<>(clientInfos.size());
        for (int i = 0; i < clientInfos.size(); i++) {
            ClientInfo clientInfo = clientInfos.get(i);
            ProcessIdentifier processIdentifier = new ProcessIdentifier();
            processIdentifier.pid = clientInfo.pid;
            processIdentifier.startTimeMillis = clientInfo.startTimeMillis;
            processIdentifiers.add(processIdentifier);
        }
        return processIdentifiers;
    }

    private String timeoutToString(int timeout) {
        switch (timeout) {
            case TIMEOUT_CRITICAL:
                return "critical";
            case TIMEOUT_MODERATE:
                return "moderate";
            case TIMEOUT_NORMAL:
                return "normal";
            default:
                Slogf.w(CarWatchdogService.TAG, "Unknown timeout value");
                return "unknown";
        }
    }

    private long getTimeoutDurationMs(int timeout) {
        if (mOverriddenClientHealthCheckWindowMs != MISSING_INT_PROPERTY_VALUE) {
            return mOverriddenClientHealthCheckWindowMs;
        }
        switch (timeout) {
            case TIMEOUT_CRITICAL:
                return 3000L;
            case TIMEOUT_MODERATE:
                return 5000L;
            case TIMEOUT_NORMAL:
                return 10000L;
            default:
                Slogf.w(CarWatchdogService.TAG, "Unknown timeout value");
                return 10000L;
        }
    }

    private final class ClientInfo implements IBinder.DeathRecipient {
        public final ICarWatchdogServiceCallback client;
        public final int pid;
        public final long startTimeMillis;
        @UserIdInt public final int userId;
        public final int timeout;
        public volatile int sessionId;
        public String packageName;

        ClientInfo(ICarWatchdogServiceCallback client, int pid, @UserIdInt int userId,
                int timeout) {
            this.client = client;
            this.pid = pid;
            // CarService doesn't have sepolicy access to read per-pid proc files, so it cannot
            // fetch the pid's actual start time. When a client process registers with
            // the CarService, it is safe to assume the process is still alive. So, populate
            // elapsed real time and the consumer (CarServiceHelperService) of this data should
            // verify that the actual start time is less than the reported start time.
            this.startTimeMillis = SystemClock.elapsedRealtime();
            this.userId = userId;
            this.timeout = timeout;
        }

        @Override
        public void binderDied() {
            Slogf.w(CarWatchdogService.TAG, "Client(pid: %d) died", pid);
            unlinkToDeath();
            onClientDeath(client, timeout);
        }

        private void linkToDeath() throws RemoteException {
            client.asBinder().linkToDeath(this, 0);
        }

        private void unlinkToDeath() {
            client.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public String toString() {
            return "ClientInfo{client=" + client + ", pid=" + pid + ", startTimeMillis="
                    + startTimeMillis + ", userId=" + userId + ", timeout=" + timeout
                    + ", sessionId=" + sessionId + '}';
        }
    }
}
