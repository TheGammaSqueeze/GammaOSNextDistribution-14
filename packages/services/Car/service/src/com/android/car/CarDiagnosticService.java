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

package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.ICarDiagnostic;
import android.car.diagnostic.ICarDiagnosticEventListener;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.proto.ProtoOutputStream;

import com.android.car.Listeners.ClientWithRate;
import com.android.car.hal.DiagnosticHalService;
import com.android.car.hal.DiagnosticHalService.DiagnosticCapabilities;
import com.android.car.internal.CarPermission;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** @hide */
public class CarDiagnosticService extends ICarDiagnostic.Stub
        implements CarServiceBase, DiagnosticHalService.DiagnosticListener {
    /** lock to access diagnostic structures */
    private final Object mLock = new Object();
    /** hold clients callback */
    @GuardedBy("mLock")
    private final LinkedList<DiagnosticClient> mClients = new LinkedList<>();

    /** key: diagnostic type. */
    @GuardedBy("mLock")
    private final HashMap<Integer, Listeners<DiagnosticClient>> mDiagnosticListeners =
        new HashMap<>();

    /** the latest live frame data. */
    @GuardedBy("mLock")
    private final LiveFrameRecord mLiveFrameDiagnosticRecord = new LiveFrameRecord();

    /** the latest freeze frame data (key: DTC) */
    @GuardedBy("mLock")
    private final FreezeFrameRecord mFreezeFrameDiagnosticRecords = new FreezeFrameRecord();

    private final DiagnosticHalService mDiagnosticHal;

    private final Context mContext;

    private final CarPermission mDiagnosticReadPermission;

    private final CarPermission mDiagnosticClearPermission;

    public CarDiagnosticService(Context context, DiagnosticHalService diagnosticHal) {
        mContext = context;
        mDiagnosticHal = diagnosticHal;
        mDiagnosticReadPermission = new CarPermission(mContext,
                Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL);
        mDiagnosticClearPermission = new CarPermission(mContext,
                Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);
    }

    @Override
    public void init() {
        synchronized (mLock) {
            mDiagnosticHal.setDiagnosticListener(this);
            setInitialLiveFrame();
            setInitialFreezeFrames();
        }
    }

    @Nullable
    private CarDiagnosticEvent setInitialLiveFrame() {
        CarDiagnosticEvent liveFrame = null;
        if(mDiagnosticHal.getDiagnosticCapabilities().isLiveFrameSupported()) {
            liveFrame = setRecentmostLiveFrame(mDiagnosticHal.getCurrentLiveFrame());
        }
        return liveFrame;
    }

    private void setInitialFreezeFrames() {
        if(mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameSupported() &&
            mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameInfoSupported()) {
            long[] timestamps = mDiagnosticHal.getFreezeFrameTimestamps();
            if (timestamps != null) {
                for (long timestamp : timestamps) {
                    setRecentmostFreezeFrame(mDiagnosticHal.getFreezeFrame(timestamp));
                }
            }
        }
    }

    @Nullable
    private CarDiagnosticEvent setRecentmostLiveFrame(final CarDiagnosticEvent event) {
        if (event != null) {
            synchronized (mLock) {
                return mLiveFrameDiagnosticRecord.update(event.checkLiveFrame());
            }
        }
        return null;
    }

    @Nullable
    private CarDiagnosticEvent setRecentmostFreezeFrame(final CarDiagnosticEvent event) {
        if (event != null) {
            synchronized (mLock) {
                return mFreezeFrameDiagnosticRecords.update(event.checkFreezeFrame());
            }
        }
        return null;
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mDiagnosticListeners.forEach(
                    (Integer frameType, Listeners diagnosticListeners) ->
                            diagnosticListeners.release());
            mDiagnosticListeners.clear();
            mLiveFrameDiagnosticRecord.disableIfNeeded();
            mFreezeFrameDiagnosticRecords.disableIfNeeded();
            mClients.clear();
        }
    }

    private void processDiagnosticData(List<CarDiagnosticEvent> events) {
        ArrayMap<CarDiagnosticService.DiagnosticClient, List<CarDiagnosticEvent>> eventsByClient =
                new ArrayMap<>();

        Listeners<DiagnosticClient> listeners = null;

        synchronized (mLock) {
            for (int i = 0; i < events.size(); i++) {
                CarDiagnosticEvent event = events.get(i);
                if (event.isLiveFrame()) {
                    // record recent-most live frame information
                    setRecentmostLiveFrame(event);
                    listeners = mDiagnosticListeners.get(CarDiagnosticManager.FRAME_TYPE_LIVE);
                } else if (event.isFreezeFrame()) {
                    setRecentmostFreezeFrame(event);
                    listeners = mDiagnosticListeners.get(CarDiagnosticManager.FRAME_TYPE_FREEZE);
                } else {
                    Slogf.w(CarLog.TAG_DIAGNOSTIC, "received unknown diagnostic event: %s", event);
                    continue;
                }

                if (null != listeners) {
                    for (ClientWithRate<DiagnosticClient> clientWithRate : listeners.getClients()) {
                        DiagnosticClient client = clientWithRate.getClient();
                        List<CarDiagnosticEvent> clientEvents =
                                eventsByClient.computeIfAbsent(client,
                                        (DiagnosticClient diagnosticClient) -> new LinkedList<>());
                        clientEvents.add(event);
                    }
                }
            }
        }

        for (ArrayMap.Entry<CarDiagnosticService.DiagnosticClient, List<CarDiagnosticEvent>> entry :
                eventsByClient.entrySet()) {
            CarDiagnosticService.DiagnosticClient client = entry.getKey();
            List<CarDiagnosticEvent> clientEvents = entry.getValue();

            client.dispatchDiagnosticUpdate(clientEvents);
        }
    }

    /** Received diagnostic data from car. */
    @Override
    public void onDiagnosticEvents(List<CarDiagnosticEvent> events) {
        processDiagnosticData(events);
    }

    @Override
    public boolean registerOrUpdateDiagnosticListener(int frameType, int rate,
                ICarDiagnosticEventListener listener) {
        boolean shouldStartDiagnostics = false;
        CarDiagnosticService.DiagnosticClient diagnosticClient = null;
        Integer oldRate = null;
        Listeners<DiagnosticClient> diagnosticListeners = null;
        synchronized (mLock) {
            mDiagnosticReadPermission.assertGranted();
            diagnosticClient = findDiagnosticClientLocked(listener);
            Listeners.ClientWithRate<DiagnosticClient> diagnosticClientWithRate = null;
            if (diagnosticClient == null) {
                diagnosticClient = new DiagnosticClient(listener);
                try {
                    listener.asBinder().linkToDeath(diagnosticClient, 0);
                } catch (RemoteException e) {
                    Slogf.w(CarLog.TAG_DIAGNOSTIC, "received RemoteException trying to register "
                            + "listener for %s", frameType);
                    return false;
                }
                mClients.add(diagnosticClient);
            }
            diagnosticListeners = mDiagnosticListeners.get(frameType);
            if (diagnosticListeners == null) {
                diagnosticListeners = new Listeners<>(rate);
                mDiagnosticListeners.put(frameType, diagnosticListeners);
                shouldStartDiagnostics = true;
            } else {
                oldRate = diagnosticListeners.getRate();
                diagnosticClientWithRate =
                        diagnosticListeners.findClientWithRate(diagnosticClient);
            }
            if (diagnosticClientWithRate == null) {
                diagnosticClientWithRate =
                        new ClientWithRate<>(diagnosticClient, rate);
                diagnosticListeners.addClientWithRate(diagnosticClientWithRate);
            } else {
                diagnosticClientWithRate.setRate(rate);
            }
            if (diagnosticListeners.getRate() > rate) {
                diagnosticListeners.setRate(rate);
                shouldStartDiagnostics = true;
            }
            diagnosticClient.addDiagnostic(frameType);
        }
        Slogf.i(CarLog.TAG_DIAGNOSTIC, "shouldStartDiagnostics = %s for %s at rate %d",
                shouldStartDiagnostics, frameType, rate);
        // start diagnostic outside lock as it can take time.
        if (shouldStartDiagnostics) {
            if (!startDiagnostic(frameType, rate)) {
                // failed. so remove from active diagnostic list.
                Slogf.w(CarLog.TAG_DIAGNOSTIC, "startDiagnostic failed");
                synchronized (mLock) {
                    diagnosticClient.removeDiagnostic(frameType);
                    if (oldRate != null) {
                        diagnosticListeners.setRate(oldRate);
                    } else {
                        mDiagnosticListeners.remove(frameType);
                    }
                }
                return false;
            }
        }
        return true;
    }

    private boolean startDiagnostic(int frameType, int rate) {
        Slogf.i(CarLog.TAG_DIAGNOSTIC, "starting diagnostic " + frameType + " at rate " + rate);
        DiagnosticHalService diagnosticHal = getDiagnosticHal();
        if (diagnosticHal == null || !diagnosticHal.isReady()) {
            Slogf.w(CarLog.TAG_DIAGNOSTIC, "diagnosticHal not ready");
            return false;
        }
        switch (frameType) {
            case CarDiagnosticManager.FRAME_TYPE_LIVE:
                synchronized (mLock) {
                    if (mLiveFrameDiagnosticRecord.isEnabled()) {
                        return true;
                    }
                }
                if (diagnosticHal.requestDiagnosticStart(
                        CarDiagnosticManager.FRAME_TYPE_LIVE, rate)) {
                    synchronized (mLock) {
                        if (!mLiveFrameDiagnosticRecord.isEnabled()) {
                            mLiveFrameDiagnosticRecord.enable();
                        }
                    }
                    return true;
                }
                break;
            case CarDiagnosticManager.FRAME_TYPE_FREEZE:
                synchronized (mLock) {
                    if (mFreezeFrameDiagnosticRecords.isEnabled()) {
                        return true;
                    }
                }
                if (diagnosticHal.requestDiagnosticStart(
                        CarDiagnosticManager.FRAME_TYPE_FREEZE, rate)) {
                    synchronized (mLock) {
                        if (!mFreezeFrameDiagnosticRecords.isEnabled()) {
                            mFreezeFrameDiagnosticRecords.enable();
                        }
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public void unregisterDiagnosticListener(
            int frameType, ICarDiagnosticEventListener listener) {
        boolean shouldStopDiagnostic = false;
        boolean shouldRestartDiagnostic = false;
        int newRate = 0;
        synchronized (mLock) {
            DiagnosticClient diagnosticClient = findDiagnosticClientLocked(listener);
            if (diagnosticClient == null) {
                Slogf.i(CarLog.TAG_DIAGNOSTIC, "trying to unregister diagnostic client %s for %s "
                        + "which is not registered", listener, frameType);
                // never registered or already unregistered.
                return;
            }
            diagnosticClient.removeDiagnostic(frameType);
            if (diagnosticClient.getNumberOfActiveDiagnostic() == 0) {
                diagnosticClient.release();
                mClients.remove(diagnosticClient);
            }
            Listeners<DiagnosticClient> diagnosticListeners = mDiagnosticListeners.get(frameType);
            if (diagnosticListeners == null) {
                // diagnostic not active
                return;
            }
            ClientWithRate<DiagnosticClient> clientWithRate =
                    diagnosticListeners.findClientWithRate(diagnosticClient);
            if (clientWithRate == null) {
                return;
            }
            diagnosticListeners.removeClientWithRate(clientWithRate);
            if (diagnosticListeners.getNumberOfClients() == 0) {
                shouldStopDiagnostic = true;
                mDiagnosticListeners.remove(frameType);
            } else if (diagnosticListeners.updateRate()) { // rate changed
                newRate = diagnosticListeners.getRate();
                shouldRestartDiagnostic = true;
            }
        }
        Slogf.i(CarLog.TAG_DIAGNOSTIC, "shouldStopDiagnostic = %s, shouldRestartDiagnostic = %s "
                + "for type %s", shouldStopDiagnostic, shouldRestartDiagnostic, frameType);
        if (shouldStopDiagnostic) {
            stopDiagnostic(frameType);
        } else if (shouldRestartDiagnostic) {
            startDiagnostic(frameType, newRate);
        }
    }

    private void stopDiagnostic(int frameType) {
        DiagnosticHalService diagnosticHal = getDiagnosticHal();
        if (diagnosticHal == null || !diagnosticHal.isReady()) {
            Slogf.w(CarLog.TAG_DIAGNOSTIC, "diagnosticHal not ready");
            return;
        }
        synchronized (mLock) {
            switch (frameType) {
                case CarDiagnosticManager.FRAME_TYPE_LIVE:
                    if (mLiveFrameDiagnosticRecord.disableIfNeeded()) {
                        diagnosticHal.requestDiagnosticStop(CarDiagnosticManager.FRAME_TYPE_LIVE);
                    }
                    break;
                case CarDiagnosticManager.FRAME_TYPE_FREEZE:
                    if (mFreezeFrameDiagnosticRecords.disableIfNeeded()) {
                        diagnosticHal.requestDiagnosticStop(CarDiagnosticManager.FRAME_TYPE_FREEZE);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private DiagnosticHalService getDiagnosticHal() {
        return mDiagnosticHal;
    }

    // Expose DiagnosticCapabilities
    public boolean isLiveFrameSupported() {
        return getDiagnosticHal().getDiagnosticCapabilities().isLiveFrameSupported();
    }

    public boolean isFreezeFrameNotificationSupported() {
        return getDiagnosticHal().getDiagnosticCapabilities().isFreezeFrameSupported();
    }

    public boolean isGetFreezeFrameSupported() {
        DiagnosticCapabilities diagnosticCapabilities =
                getDiagnosticHal().getDiagnosticCapabilities();
        return diagnosticCapabilities.isFreezeFrameInfoSupported() &&
                diagnosticCapabilities.isFreezeFrameSupported();
    }

    public boolean isClearFreezeFramesSupported() {
        DiagnosticCapabilities diagnosticCapabilities =
            getDiagnosticHal().getDiagnosticCapabilities();
        return diagnosticCapabilities.isFreezeFrameClearSupported() &&
            diagnosticCapabilities.isFreezeFrameSupported();
    }

    public boolean isSelectiveClearFreezeFramesSupported() {
        DiagnosticCapabilities diagnosticCapabilities =
            getDiagnosticHal().getDiagnosticCapabilities();
        return isClearFreezeFramesSupported() &&
                diagnosticCapabilities.isSelectiveClearFreezeFramesSupported();
    }

    // ICarDiagnostic implementations

    @Override
    public CarDiagnosticEvent getLatestLiveFrame() {
        synchronized (mLock) {
            return mLiveFrameDiagnosticRecord.getLastEvent();
        }
    }

    @Override
    public long[] getFreezeFrameTimestamps() {
        synchronized (mLock) {
            return mFreezeFrameDiagnosticRecords.getFreezeFrameTimestamps();
        }
    }

    @Override
    @Nullable
    public CarDiagnosticEvent getFreezeFrame(long timestamp) {
        synchronized (mLock) {
            return mFreezeFrameDiagnosticRecords.getEvent(timestamp);
        }
    }

    @Override
    public boolean clearFreezeFrames(long... timestamps) {
        mDiagnosticClearPermission.assertGranted();
        if (!isClearFreezeFramesSupported())
            return false;
        if (timestamps != null && timestamps.length != 0) {
            if (!isSelectiveClearFreezeFramesSupported()) {
                return false;
            }
        }
        mDiagnosticHal.clearFreezeFrames(timestamps);
        synchronized (mLock) {
            mFreezeFrameDiagnosticRecords.clearEvents();
        }
        return true;
    }

    /**
     * Find DiagnosticClient from client list and return it. This should be called with mClients
     * locked.
     *
     * @param listener
     * @return null if not found.
     */
    @GuardedBy("mLock")
    private CarDiagnosticService.DiagnosticClient findDiagnosticClientLocked(
            ICarDiagnosticEventListener listener) {
        IBinder binder = listener.asBinder();
        for (DiagnosticClient diagnosticClient : mClients) {
            if (diagnosticClient.isHoldingListenerBinder(binder)) {
                return diagnosticClient;
            }
        }
        return null;
    }

    private void removeClient(DiagnosticClient diagnosticClient) {
        synchronized (mLock) {
            for (int diagnostic : diagnosticClient.getDiagnosticArray()) {
                unregisterDiagnosticListener(
                        diagnostic, diagnosticClient.getICarDiagnosticEventListener());
            }
            mClients.remove(diagnosticClient);
        }
    }

    /** internal instance for pending client request */
    private class DiagnosticClient implements Listeners.IListener {
        /** callback for diagnostic events */
        private final ICarDiagnosticEventListener mListener;

        private final Set<Integer> mActiveDiagnostics = new HashSet<>();

        /** when false, it is already released */
        private volatile boolean mActive = true;

        DiagnosticClient(ICarDiagnosticEventListener listener) {
            this.mListener = listener;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DiagnosticClient)) return false;
            DiagnosticClient that = (DiagnosticClient) o;
            return mListener.asBinder() == (that.mListener.asBinder());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mListener.asBinder());
        }

        boolean isHoldingListenerBinder(IBinder listenerBinder) {
            return mListener.asBinder() == listenerBinder;
        }

        void addDiagnostic(int frameType) {
            mActiveDiagnostics.add(frameType);
        }

        void removeDiagnostic(int frameType) {
            mActiveDiagnostics.remove(frameType);
        }

        int getNumberOfActiveDiagnostic() {
            return mActiveDiagnostics.size();
        }

        int[] getDiagnosticArray() {
            return mActiveDiagnostics.stream().mapToInt(Integer::intValue).toArray();
        }

        ICarDiagnosticEventListener getICarDiagnosticEventListener() {
            return mListener;
        }

        /** Client dead. should remove all diagnostic requests from client */
        @Override
        public void binderDied() {
            mListener.asBinder().unlinkToDeath(this, 0);
            removeClient(this);
        }

        void dispatchDiagnosticUpdate(List<CarDiagnosticEvent> events) {
            if (events.size() != 0 && mActive) {
                try {
                    mListener.onDiagnosticEvents(events);
                } catch (RemoteException e) {
                    //ignore. crash will be handled by death handler
                }
            }
        }

        @Override
        public void release() {
            if (mActive) {
                mListener.asBinder().unlinkToDeath(this, 0);
                mActiveDiagnostics.clear();
                mActive = false;
            }
        }
    }

    private static abstract class DiagnosticRecord {
        protected boolean mEnabled = false;

        boolean isEnabled() {
            return mEnabled;
        }

        void enable() {
            mEnabled = true;
        }

        abstract boolean disableIfNeeded();
        abstract CarDiagnosticEvent update(CarDiagnosticEvent newEvent);
    }

    private static class LiveFrameRecord extends DiagnosticRecord {
        /** Store the most recent live-frame. */
        CarDiagnosticEvent mLastEvent = null;

        @Override
        boolean disableIfNeeded() {
            if (!mEnabled) return false;
            mEnabled = false;
            mLastEvent = null;
            return true;
        }

        @Override
        CarDiagnosticEvent update(@NonNull CarDiagnosticEvent newEvent) {
            Objects.requireNonNull(newEvent);
            if((null == mLastEvent) || mLastEvent.isEarlierThan(newEvent))
                mLastEvent = newEvent;
            return mLastEvent;
        }

        CarDiagnosticEvent getLastEvent() {
            return mLastEvent;
        }
    }

    private static class FreezeFrameRecord extends DiagnosticRecord {
        /** Store the timestamp --> freeze frame mapping. */
        HashMap<Long, CarDiagnosticEvent> mEvents = new HashMap<>();

        @Override
        boolean disableIfNeeded() {
            if (!mEnabled) return false;
            mEnabled = false;
            clearEvents();
            return true;
        }

        void clearEvents() {
            mEvents.clear();
        }

        @Override
        CarDiagnosticEvent update(@NonNull CarDiagnosticEvent newEvent) {
            mEvents.put(newEvent.timestamp, newEvent);
            return newEvent;
        }

        long[] getFreezeFrameTimestamps() {
            return mEvents.keySet().stream().mapToLong(Long::longValue).toArray();
        }

        CarDiagnosticEvent getEvent(long timestamp) {
            return mEvents.get(timestamp);
        }

        Iterable<CarDiagnosticEvent> getEvents() {
            return mEvents.values();
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("*CarDiagnosticService*");
            writer.println("**last events for diagnostics**");
            if (null != mLiveFrameDiagnosticRecord.getLastEvent()) {
                writer.println("last live frame event: ");
                writer.println(mLiveFrameDiagnosticRecord.getLastEvent());
            }
            writer.println("freeze frame events: ");
            mFreezeFrameDiagnosticRecords.getEvents().forEach(writer::println);
            writer.println("**clients**");
            try {
                for (DiagnosticClient client : mClients) {
                    if (client != null) {
                        try {
                            writer.println(
                                    "binder:"
                                            + client.mListener
                                            + " active diagnostics:"
                                            + Arrays.toString(client.getDiagnosticArray()));
                        } catch (ConcurrentModificationException e) {
                            writer.println("concurrent modification happened");
                        }
                    } else {
                        writer.println("null client");
                    }
                }
            } catch (ConcurrentModificationException e) {
                writer.println("concurrent modification happened");
            }
            writer.println("**diagnostic listeners**");
            try {
                for (int diagnostic : mDiagnosticListeners.keySet()) {
                    Listeners diagnosticListeners = mDiagnosticListeners.get(diagnostic);
                    if (diagnosticListeners != null) {
                        writer.println(
                                " Diagnostic:"
                                        + diagnostic
                                        + " num client:"
                                        + diagnosticListeners.getNumberOfClients()
                                        + " rate:"
                                        + diagnosticListeners.getRate());
                    }
                }
            } catch (ConcurrentModificationException e) {
                writer.println("concurrent modification happened");
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}
}
