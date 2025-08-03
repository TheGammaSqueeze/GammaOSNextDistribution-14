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

import static com.android.car.telemetry.CarTelemetryService.DEBUG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.usage.NetworkStats;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher.OemType;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher.Transport;
import android.car.telemetry.TelemetryProto.Publisher.PublisherCase;
import android.net.NetworkTemplate;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArrayMap;

import com.android.car.CarLog;
import com.android.car.telemetry.ResultStore;
import com.android.car.telemetry.UidPackageMapper;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.publisher.net.NetworkStatsManagerProxy;
import com.android.car.telemetry.publisher.net.NetworkStatsWrapper;
import com.android.car.telemetry.publisher.net.RefinedStats;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.internal.util.Preconditions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publisher implementation for {@link TelemetryProto.ConnectivityPublisher}.
 *
 * <p>This publisher pulls netstats periodically from NetworkStatsService. It publishes statistics
 * between the last pull and now. The {@link NetworkStats} are stored in NetworkStatsService in 2
 * hours buckets, we won't be able to get precise netstats if we use the buckets mechanism, that's
 * why we will be storing baseline (or previous) netstats in ConnectivityPublisher to find netstats
 * diff between now and the last pull.
 *
 * <p>Schema for this publisher data is defined and documented in the
 * {@link RefinedStats#toPersistableBundle} method.
 */
public class ConnectivityPublisher extends AbstractPublisher {
    // The default bucket duration used when query a snapshot from NetworkStatsService. The value
    // should be sync with NetworkStatsService#DefaultNetworkStatsSettings#getUidConfig.
    private static final long NETSTATS_UID_DEFAULT_BUCKET_DURATION_MILLIS =
            Duration.ofHours(2).toMillis();

    // Use ArrayMap instead of HashMap to improve memory usage. It doesn't store more than 100s
    // of items, and it's good enough performance-wise.
    private final ArrayMap<QueryParam, ArrayList<DataSubscriber>> mSubscribers = new ArrayMap<>();

    // Stores previous netstats for computing network usage since the last pull.
    private final ArrayMap<QueryParam, RefinedStats> mTransportPreviousNetstats = new ArrayMap<>();

    // All the methods in this class are expected to be called on this handler's thread.
    private final Handler mTelemetryHandler;

    private final UidPackageMapper mUidMapper;
    private final TimingsTraceLog mTraceLog;
    private final ResultStore mResultStore;

    private NetworkStatsManagerProxy mNetworkStatsManager;

    ConnectivityPublisher(
            @NonNull PublisherListener listener,
            @NonNull NetworkStatsManagerProxy networkStatsManager,
            @NonNull Handler telemetryHandler,
            @NonNull ResultStore resultStore,
            @NonNull SessionController sessionController, @NonNull UidPackageMapper uidMapper) {
        super(listener);
        mNetworkStatsManager = networkStatsManager;
        mTelemetryHandler = telemetryHandler;
        mResultStore = resultStore;
        mUidMapper = uidMapper;
        mTraceLog = new TimingsTraceLog(CarLog.TAG_TELEMETRY, TraceHelper.TRACE_TAG_CAR_SERVICE);
        for (Transport transport : Transport.values()) {
            if (transport.equals(Transport.TRANSPORT_UNDEFINED)) {
                continue;
            }
            for (OemType oemType : OemType.values()) {
                if (oemType.equals(OemType.OEM_UNDEFINED)) {
                    continue;
                }
                mSubscribers.put(QueryParam.build(transport, oemType), new ArrayList<>());
            }
        }
        // Subscribes the publisher to driving session updates by SessionController.
        sessionController.registerCallback(this::handleSessionStateChange);
    }

    @Override
    protected void handleSessionStateChange(SessionAnnotation annotation) {
        if (annotation.sessionState == SessionController.STATE_ENTER_DRIVING_SESSION) {
            processPreviousSession();
            pullInitialNetstats();
        } else if (annotation.sessionState == SessionController.STATE_EXIT_DRIVING_SESSION) {
            PersistableBundle resultsToStore = new PersistableBundle();
            // Pull data and calculate difference per each distinct QueryParam.
            // Each QueryParam is a combination of transport/oem_managed keys.
            for (int i = 0; i < mSubscribers.size(); i++) {
                if (mSubscribers.valueAt(i).isEmpty()) {
                    // no need to pull data if there are no subscribers
                    continue;
                }
                PersistableBundle bundle = pullNetstatsAndCalculateDiff(mSubscribers.keyAt(i));
                if (bundle == null) {
                    continue;
                }
                annotation.addAnnotationsToBundle(bundle);
                resultsToStore.putPersistableBundle(mSubscribers.keyAt(i).toString(), bundle);
            }
            mResultStore.putPublisherData(ConnectivityPublisher.class.getSimpleName(),
                    resultsToStore);
        }
    }

    @Override
    public void addDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        Preconditions.checkArgument(
                publisherParam.getPublisherCase() == PublisherCase.CONNECTIVITY,
                "Subscribers only with ConnectivityPublisher are supported by this class.");

        mSubscribers.get(QueryParam.forSubscriber(subscriber)).add(subscriber);
    }

    @Override
    public void removeDataSubscriber(@NonNull DataSubscriber subscriber) {
        mSubscribers.get(QueryParam.forSubscriber(subscriber)).remove(subscriber);
    }

    @Override
    public void removeAllDataSubscribers() {
        for (int i = 0; i < mSubscribers.size(); i++) {
            mSubscribers.valueAt(i).clear();
        }
    }

    @Override
    public boolean hasDataSubscriber(@NonNull DataSubscriber subscriber) {
        return mSubscribers.get(QueryParam.forSubscriber(subscriber)).contains(subscriber);
    }

    private void pullInitialNetstats() {
        mTraceLog.traceBegin("ConnectivityPublisher.pullInitialNetstats");
        if (DEBUG) {
            Slogf.d(CarLog.TAG_TELEMETRY, "ConnectivityPublisher is pulling initial netstats");
        }
        try {
            for (QueryParam param : mSubscribers.keySet()) {
                RefinedStats summary = getSummaryForAllUid(param);
                if (summary == null) {
                    continue;
                }
                mTransportPreviousNetstats.put(param, summary);
            }
        } catch (RemoteException e) {
            // Can't do much if the NetworkStatsService is not available. Next netstats pull
            // will update the baseline netstats.
            Slogf.w(CarLog.TAG_TELEMETRY, e);
        }
        mTraceLog.traceEnd();
    }

    /**
     * Retrieves data from a previous session, i.e. pulled and stored in ResultStore, if any. Adds
     * attributes of the previous session to a {@link PersistableBundle} that contains the data and
     * pushes it to subscribers.
     */
    private void processPreviousSession() {
        PersistableBundle previousSessionData = mResultStore.getPublisherData(
                ConnectivityPublisher.class.getSimpleName(), true);
        if (previousSessionData == null) {
            Slogf.d(CarLog.TAG_TELEMETRY, "Data from the previous session is not found. Quitting.");
            return;
        }
        for (String key : previousSessionData.keySet()) {
            QueryParam queryParam = QueryParam.fromString(key);
            if (queryParam == null) {
                Slogf.e(CarLog.TAG_TELEMETRY,
                        "Failed to convert ResultStore key to QueryParam. This is an unexpected "
                                + "error. Halting any further processing of the session data");
                continue;
            }
            PersistableBundle data = previousSessionData.getPersistableBundle(key);
            if (!data.containsKey(Constants.ANNOTATION_BUNDLE_KEY_SESSION_ID)) {
                Slogf.e(CarLog.TAG_TELEMETRY,
                        "Session annotations is unexpectedly missing. Skipping this batch.");
                continue;
            }
            ArrayList<DataSubscriber> subscribers = mSubscribers.get(queryParam);
            for (DataSubscriber subscriber : subscribers) {
                subscriber.push(data);
            }
        }
    }

    @Nullable
    private PersistableBundle pullNetstatsAndCalculateDiff(@NonNull QueryParam param) {
        mTraceLog.traceBegin("ConnectivityPublisher.pullNetstatsAndCalculateDiff");

        RefinedStats previous = mTransportPreviousNetstats.get(param);
        if (previous == null) {
            Slogf.w(
                    CarLog.TAG_TELEMETRY,
                    "Previous stats is null for param %s. Will try again in the next pull.",
                    param);
            mTraceLog.traceEnd();
            return null;
        }

        RefinedStats current;
        try {
            current = getSummaryForAllUid(param);
        } catch (RemoteException e) {
            // If the NetworkStatsService is not available, it retries in the next pull.
            Slogf.w(CarLog.TAG_TELEMETRY, e);
            mTraceLog.traceEnd();
            return null;
        }

        if (current == null) {
            mTraceLog.traceEnd();
            return null;
        }

        // By subtracting, it calculates network usage since the last pull.
        RefinedStats diff = RefinedStats.subtract(current, previous);
        PersistableBundle data = diff.toPersistableBundle(mUidMapper);

        mTraceLog.traceEnd();

        return data;
    }

    /**
     * Creates a snapshot of NetworkStats since boot for the given QueryParam, but adds 1 bucket
     * duration before boot as a buffer to ensure at least one full bucket will be included. Note
     * that this should be only used to calculate diff since the snapshot might contains some
     * traffic before boot.
     *
     * <p>This method might block the thread for several seconds.
     *
     * <p>TODO(b/218529196): run this method on a separate thread for better performance.
     */
    @Nullable
    private RefinedStats getSummaryForAllUid(@NonNull QueryParam param) throws RemoteException {
        if (DEBUG) {
            Slogf.d(CarLog.TAG_TELEMETRY, "getSummaryForAllUid " + param);
        }
        long currentTimeInMillis = System.currentTimeMillis();
        long elapsedMillisSinceBoot = SystemClock.elapsedRealtime(); // including sleep
        // TODO(b/197905656): consider using the current netstats bucket value
        //                    from Settings.Global.NETSTATS_UID_BUCKET_DURATION.
        long startMillis = currentTimeInMillis
                - elapsedMillisSinceBoot
                - NETSTATS_UID_DEFAULT_BUCKET_DURATION_MILLIS;

        NetworkStatsWrapper nonTaggedStats;
        NetworkStatsWrapper taggedStats;
        // querySummary and queryTaggedSummary may throw NPE propagated from NetworkStatsService
        // when its NetworkStatsRecorder failed to initialize and
        // NetworkStatsRecorder.getOrLoadCompleteLocked() is called.
        try {
            nonTaggedStats =
                    mNetworkStatsManager.querySummary(
                            param.buildNetworkTemplate(), startMillis, currentTimeInMillis);
            taggedStats =
                    mNetworkStatsManager.queryTaggedSummary(
                            param.buildNetworkTemplate(), startMillis, currentTimeInMillis);
        } catch (NullPointerException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, e);
            return null;
        }

        RefinedStats result = new RefinedStats(startMillis, currentTimeInMillis);
        result.addNetworkStats(nonTaggedStats);
        result.addNetworkStats(taggedStats);
        return result;
    }

    /**
     * Parameters to query data from NetworkStatsService. Converts {@link Transport} and {@link
     * OemType} values into NetworkStatsService supported values.
     */
    private static class QueryParam {
        private int mMatchRule; // Must be one of NetworkTemplate.TemplateMatchRule
        private int mOemManaged; // Must be one of NetworkTemplate.OemManaged

        @NonNull
        static QueryParam forSubscriber(@NonNull DataSubscriber subscriber) {
            TelemetryProto.ConnectivityPublisher publisher =
                    subscriber.getPublisherParam().getConnectivity();
            return build(publisher.getTransport(), publisher.getOemType());
        }

        @NonNull
        static QueryParam build(@NonNull Transport transport, @NonNull OemType oemType) {
            return new QueryParam(getMatchRule(transport), getNetstatsOemManaged(oemType));
        }

        private QueryParam(int transport, int oemManaged) {
            mMatchRule = transport;
            mOemManaged = oemManaged;
        }

        @NonNull
        NetworkTemplate buildNetworkTemplate() {
            return new NetworkTemplate.Builder(mMatchRule).setOemManaged(mOemManaged).build();
        }

        private static int getMatchRule(@NonNull Transport transport) {
            switch (transport) {
                case TRANSPORT_CELLULAR:
                    return NetworkTemplate.MATCH_MOBILE;
                case TRANSPORT_ETHERNET:
                    return NetworkTemplate.MATCH_ETHERNET;
                case TRANSPORT_WIFI:
                    return NetworkTemplate.MATCH_WIFI;
                case TRANSPORT_BLUETOOTH:
                    return NetworkTemplate.MATCH_BLUETOOTH;
                default:
                    throw new IllegalArgumentException("Unexpected transport " + transport.name());
            }
        }

        private static int getNetstatsOemManaged(@NonNull OemType oemType) {
            switch (oemType) {
                case OEM_NONE:
                    return NetworkTemplate.OEM_MANAGED_NO;
                case OEM_MANAGED:
                    return NetworkTemplate.OEM_MANAGED_YES;
                default:
                    throw new IllegalArgumentException("Unexpected oem_type " + oemType.name());
            }
        }

        @Override
        public String toString() {
            return "QueryParam(matchRule=" + mMatchRule + ", oemManaged=" + mOemManaged + ")";
        }

        public static QueryParam fromString(String input) {
            Pattern pattern = Pattern.compile(
                    "QueryParam\\(matchRule=(\\d+),\\s*oemManaged=(-?\\d+)\\)");
            Matcher matcher = pattern.matcher(input);
            if (!matcher.matches()) {
                return null;
            }
            if (matcher.groupCount() != 2) {
                return null;
            }
            return new QueryParam(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMatchRule, mOemManaged);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof QueryParam)) {
                return false;
            }
            QueryParam other = (QueryParam) obj;
            return mMatchRule == other.mMatchRule && mOemManaged == other.mOemManaged;
        }
    }
}
