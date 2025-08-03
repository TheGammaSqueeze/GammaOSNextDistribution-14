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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;
import android.app.StatsManager;
import android.app.usage.NetworkStatsManager;
import android.car.telemetry.TelemetryProto;
import android.content.Context;
import android.os.Handler;

import com.android.car.CarPropertyService;
import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.UidPackageMapper;
import com.android.car.telemetry.publisher.net.NetworkStatsManagerProxy;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Lazy factory class for Publishers. It's expected to have a single factory instance. Must be
 * called from the telemetry thread.
 *
 * <p>It doesn't instantiate all the publishers right away, as in some cases some publishers are not
 * needed.
 *
 * <p>Methods in this class must be called on telemetry thread unless specified as thread-safe.
 */
public class PublisherFactory {
    // Some publishers must be initialized as early as possible during boot.
    private static final TelemetryProto.Publisher.PublisherCase[] sForceInitPublishers = {
            TelemetryProto.Publisher.PublisherCase.CONNECTIVITY
    };

    private final Object mLock = new Object();
    private final CarPropertyService mCarPropertyService;
    private final Handler mTelemetryHandler;
    private final Context mContext;  // CarService context
    private final UidPackageMapper mUidMapper;

    private VehiclePropertyPublisher mVehiclePropertyPublisher;
    private CarTelemetrydPublisher mCarTelemetrydPublisher;
    private StatsPublisher mStatsPublisher;
    private ConnectivityPublisher mConnectivityPublisher;
    private MemoryPublisher mMemoryPublisher;
    private AbstractPublisher.PublisherListener mPublisherListener;
    // To enable publishers to subscribe to session updates if needed.
    private final SessionController mSessionController;
    // To enable publishers to store pulled data in the event of suspend-to-RAM or shutdown.
    private final ResultStore mResultStore;

    public PublisherFactory(
            @NonNull CarPropertyService carPropertyService,
            @NonNull Handler handler,
            @NonNull Context context,
            @NonNull SessionController sessionController,
            @NonNull ResultStore resultStore,
            @NonNull UidPackageMapper uidMapper) {
        mCarPropertyService = carPropertyService;
        mTelemetryHandler = handler;
        mContext = context;
        mSessionController = sessionController;
        mResultStore = resultStore;
        mUidMapper = uidMapper;
    }

    /** Returns the publisher by given type. This method is thread-safe. */
    @NonNull
    public AbstractPublisher getPublisher(@NonNull TelemetryProto.Publisher.PublisherCase type) {
        Preconditions.checkState(mPublisherListener != null, "PublisherFactory is not initialized");
        // No need to optimize locks, as this method is infrequently called.
        synchronized (mLock) {
            switch (type.getNumber()) {
                case TelemetryProto.Publisher.VEHICLE_PROPERTY_FIELD_NUMBER:
                    if (mVehiclePropertyPublisher == null) {
                        mVehiclePropertyPublisher = new VehiclePropertyPublisher(
                                mCarPropertyService, mPublisherListener, mTelemetryHandler);
                    }
                    return mVehiclePropertyPublisher;
                case TelemetryProto.Publisher.CARTELEMETRYD_FIELD_NUMBER:
                    if (mCarTelemetrydPublisher == null) {
                        mCarTelemetrydPublisher = new CarTelemetrydPublisher(
                                mPublisherListener, mTelemetryHandler, mSessionController);
                    }
                    return mCarTelemetrydPublisher;
                case TelemetryProto.Publisher.STATS_FIELD_NUMBER:
                    if (mStatsPublisher == null) {
                        StatsManager stats = mContext.getSystemService(StatsManager.class);
                        Preconditions.checkState(stats != null, "StatsManager not found");
                        StatsManagerProxy statsManager = new StatsManagerImpl(stats);
                        mStatsPublisher = new StatsPublisher(
                                mPublisherListener, statsManager, mResultStore, mTelemetryHandler);
                    }
                    return mStatsPublisher;
                case TelemetryProto.Publisher.CONNECTIVITY_FIELD_NUMBER:
                    if (mConnectivityPublisher == null) {
                        NetworkStatsManager networkStatsManager =
                                Objects.requireNonNull(
                                        mContext.getSystemService(NetworkStatsManager.class));
                        mConnectivityPublisher =
                                new ConnectivityPublisher(
                                        mPublisherListener,
                                        new NetworkStatsManagerProxy(networkStatsManager),
                                        mTelemetryHandler, mResultStore, mSessionController,
                                        mUidMapper);
                    }
                    return mConnectivityPublisher;
                case TelemetryProto.Publisher.MEMORY_FIELD_NUMBER:
                    if (mMemoryPublisher == null) {
                        mMemoryPublisher = new MemoryPublisher(
                                mContext, mPublisherListener, mTelemetryHandler, mResultStore,
                                mSessionController);
                    }
                    return mMemoryPublisher;
                default:
                    throw new IllegalArgumentException(
                            "Publisher type " + type + " is not supported");
            }
        }
    }

    /**
     * Removes all {@link com.android.car.telemetry.databroker.DataSubscriber} from all publishers.
     */
    public void removeAllDataSubscribers() {
        if (mVehiclePropertyPublisher != null) {
            mVehiclePropertyPublisher.removeAllDataSubscribers();
        }
        if (mCarTelemetrydPublisher != null) {
            mCarTelemetrydPublisher.removeAllDataSubscribers();
        }
        if (mStatsPublisher != null) {
            mStatsPublisher.removeAllDataSubscribers();
        }
        if (mMemoryPublisher != null) {
            mMemoryPublisher.removeAllDataSubscribers();
        }
    }

    /**
     * Initializes the factory and sets the publisher listener for all the publishers.
     * This is expected to be called before {@link #getPublisher} method. This is not the best
     * approach, but it suits for this case.
     */
    public void initialize(@NonNull AbstractPublisher.PublisherListener listener) {
        Preconditions.checkState(
                mPublisherListener == null, "PublisherFactory is already initialized");
        mPublisherListener = listener;
        for (TelemetryProto.Publisher.PublisherCase publisher : sForceInitPublishers) {
            getPublisher(publisher);
        }
    }
}
