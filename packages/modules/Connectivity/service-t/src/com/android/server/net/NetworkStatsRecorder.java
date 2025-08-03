/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.net;

import static android.net.NetworkStats.TAG_NONE;
import static android.net.TrafficStats.KB_IN_BYTES;
import static android.net.TrafficStats.MB_IN_BYTES;
import static android.text.format.DateUtils.YEAR_IN_MILLIS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkIdentitySet;
import android.net.NetworkStats;
import android.net.NetworkStats.NonMonotonicObserver;
import android.net.NetworkStatsAccess;
import android.net.NetworkStatsCollection;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.os.Binder;
import android.os.DropBoxManager;
import android.os.SystemClock;
import android.service.NetworkStatsRecorderProto;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.internal.util.FileRotator;
import com.android.metrics.NetworkStatsMetricsLogger;
import com.android.net.module.util.NetworkStatsUtils;

import libcore.io.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Logic to record deltas between periodic {@link NetworkStats} snapshots into
 * {@link NetworkStatsHistory} that belong to {@link NetworkStatsCollection}.
 * Keeps pending changes in memory until they pass a specific threshold, in
 * bytes. Uses {@link FileRotator} for persistence logic if present.
 * <p>
 * Not inherently thread safe.
 */
public class NetworkStatsRecorder {
    private static final String TAG = "NetworkStatsRecorder";
    private static final boolean LOGD = false;
    private static final boolean LOGV = false;

    private static final String TAG_NETSTATS_DUMP = "netstats_dump";

    /** Dump before deleting in {@link #recoverAndDeleteData()}. */
    private static final boolean DUMP_BEFORE_DELETE = true;

    private final FileRotator mRotator;
    private final NonMonotonicObserver<String> mObserver;
    private final DropBoxManager mDropBox;
    private final String mCookie;

    private final long mBucketDuration;
    private final boolean mOnlyTags;
    private final boolean mWipeOnError;
    private final boolean mUseFastDataInput;

    private long mPersistThresholdBytes = 2 * MB_IN_BYTES;
    private NetworkStats mLastSnapshot;

    private final NetworkStatsCollection mPending;
    private final NetworkStatsCollection mSinceBoot;

    private final CombiningRewriter mPendingRewriter;

    private WeakReference<NetworkStatsCollection> mComplete;
    private final NetworkStatsMetricsLogger mMetricsLogger = new NetworkStatsMetricsLogger();
    @Nullable
    private final File mStatsDir;

    /**
     * Non-persisted recorder, with only one bucket. Used by {@link NetworkStatsObservers}.
     */
    public NetworkStatsRecorder() {
        mRotator = null;
        mObserver = null;
        mDropBox = null;
        mCookie = null;

        // set the bucket big enough to have all data in one bucket, but allow some
        // slack to avoid overflow
        mBucketDuration = YEAR_IN_MILLIS;
        mOnlyTags = false;
        mWipeOnError = true;
        mUseFastDataInput = false;

        mPending = null;
        mSinceBoot = new NetworkStatsCollection(mBucketDuration);

        mPendingRewriter = null;
        mStatsDir = null;
    }

    /**
     * Persisted recorder.
     */
    public NetworkStatsRecorder(FileRotator rotator, NonMonotonicObserver<String> observer,
            DropBoxManager dropBox, String cookie, long bucketDuration, boolean onlyTags,
            boolean wipeOnError, boolean useFastDataInput, @Nullable File statsDir) {
        mRotator = Objects.requireNonNull(rotator, "missing FileRotator");
        mObserver = Objects.requireNonNull(observer, "missing NonMonotonicObserver");
        mDropBox = Objects.requireNonNull(dropBox, "missing DropBoxManager");
        mCookie = cookie;

        mBucketDuration = bucketDuration;
        mOnlyTags = onlyTags;
        mWipeOnError = wipeOnError;
        mUseFastDataInput = useFastDataInput;

        mPending = new NetworkStatsCollection(bucketDuration);
        mSinceBoot = new NetworkStatsCollection(bucketDuration);

        mPendingRewriter = new CombiningRewriter(mPending);
        mStatsDir = statsDir;
    }

    public void setPersistThreshold(long thresholdBytes) {
        if (LOGV) Log.v(TAG, "setPersistThreshold() with " + thresholdBytes);
        mPersistThresholdBytes = NetworkStatsUtils.constrain(
                thresholdBytes, 1 * KB_IN_BYTES, 100 * MB_IN_BYTES);
    }

    public void resetLocked() {
        mLastSnapshot = null;
        if (mPending != null) {
            mPending.reset();
        }
        if (mSinceBoot != null) {
            mSinceBoot.reset();
        }
        if (mComplete != null) {
            mComplete.clear();
        }
    }

    public NetworkStats.Entry getTotalSinceBootLocked(NetworkTemplate template) {
        return mSinceBoot.getSummary(template, Long.MIN_VALUE, Long.MAX_VALUE,
                NetworkStatsAccess.Level.DEVICE, Binder.getCallingUid()).getTotal(null);
    }

    public NetworkStatsCollection getSinceBoot() {
        return mSinceBoot;
    }

    public long getBucketDuration() {
        return mBucketDuration;
    }

    @NonNull
    public String getCookie() {
        return mCookie;
    }

    /**
     * Load complete history represented by {@link FileRotator}. Caches
     * internally as a {@link WeakReference}, and updated with future
     * {@link #recordSnapshotLocked(NetworkStats, Map, long)} snapshots as long
     * as reference is valid.
     */
    public NetworkStatsCollection getOrLoadCompleteLocked() {
        Objects.requireNonNull(mRotator, "missing FileRotator");
        NetworkStatsCollection res = mComplete != null ? mComplete.get() : null;
        if (res == null) {
            final long readStart = SystemClock.elapsedRealtime();
            res = loadLocked(Long.MIN_VALUE, Long.MAX_VALUE);
            mComplete = new WeakReference<NetworkStatsCollection>(res);
            final long readEnd = SystemClock.elapsedRealtime();
            // For legacy recorders which are used for data integrity check, which
            // have wipeOnError flag unset, skip reporting metrics.
            if (mWipeOnError) {
                mMetricsLogger.logRecorderFileReading(mCookie, (int) (readEnd - readStart),
                        mStatsDir, res, mUseFastDataInput);
            }
        }
        return res;
    }

    public NetworkStatsCollection getOrLoadPartialLocked(long start, long end) {
        Objects.requireNonNull(mRotator, "missing FileRotator");
        NetworkStatsCollection res = mComplete != null ? mComplete.get() : null;
        if (res == null) {
            res = loadLocked(start, end);
        }
        return res;
    }

    private NetworkStatsCollection loadLocked(long start, long end) {
        if (LOGD) {
            Log.d(TAG, "loadLocked() reading from disk for " + mCookie
                    + " useFastDataInput: " + mUseFastDataInput);
        }
        final NetworkStatsCollection res =
                new NetworkStatsCollection(mBucketDuration, mUseFastDataInput);
        try {
            mRotator.readMatching(res, start, end);
            res.recordCollection(mPending);
        } catch (IOException e) {
            Log.wtf(TAG, "problem completely reading network stats", e);
            recoverAndDeleteData();
        } catch (OutOfMemoryError e) {
            Log.wtf(TAG, "problem completely reading network stats", e);
            recoverAndDeleteData();
        }
        return res;
    }

    /**
     * Record any delta that occurred since last {@link NetworkStats} snapshot, using the given
     * {@link Map} to identify network interfaces. First snapshot is considered bootstrap, and is
     * not counted as delta.
     */
    public void recordSnapshotLocked(NetworkStats snapshot,
            Map<String, NetworkIdentitySet> ifaceIdent, long currentTimeMillis) {
        final HashSet<String> unknownIfaces = new HashSet<>();

        // skip recording when snapshot missing
        if (snapshot == null) return;

        // assume first snapshot is bootstrap and don't record
        if (mLastSnapshot == null) {
            mLastSnapshot = snapshot;
            return;
        }

        final NetworkStatsCollection complete = mComplete != null ? mComplete.get() : null;

        final NetworkStats delta = NetworkStats.subtract(
                snapshot, mLastSnapshot, mObserver, mCookie);
        final long end = currentTimeMillis;
        final long start = end - delta.getElapsedRealtime();

        NetworkStats.Entry entry = null;
        for (int i = 0; i < delta.size(); i++) {
            entry = delta.getValues(i, entry);

            // As a last-ditch check, report any negative values and
            // clamp them so recording below doesn't croak.
            if (entry.isNegative()) {
                if (mObserver != null) {
                    mObserver.foundNonMonotonic(delta, i, mCookie);
                }
                entry.rxBytes = Math.max(entry.rxBytes, 0);
                entry.rxPackets = Math.max(entry.rxPackets, 0);
                entry.txBytes = Math.max(entry.txBytes, 0);
                entry.txPackets = Math.max(entry.txPackets, 0);
                entry.operations = Math.max(entry.operations, 0);
            }

            final NetworkIdentitySet ident = ifaceIdent.get(entry.iface);
            if (ident == null) {
                unknownIfaces.add(entry.iface);
                continue;
            }

            // skip when no delta occurred
            if (entry.isEmpty()) continue;

            // only record tag data when requested
            if ((entry.tag == TAG_NONE) != mOnlyTags) {
                if (mPending != null) {
                    mPending.recordData(ident, entry.uid, entry.set, entry.tag, start, end, entry);
                }

                // also record against boot stats when present
                if (mSinceBoot != null) {
                    mSinceBoot.recordData(ident, entry.uid, entry.set, entry.tag, start, end, entry);
                }

                // also record against complete dataset when present
                if (complete != null) {
                    complete.recordData(ident, entry.uid, entry.set, entry.tag, start, end, entry);
                }
            }
        }

        mLastSnapshot = snapshot;

        if (LOGV && unknownIfaces.size() > 0) {
            Log.w(TAG, "unknown interfaces " + unknownIfaces + ", ignoring those stats");
        }
    }

    /**
     * Consider persisting any pending deltas, if they are beyond
     * {@link #mPersistThresholdBytes}.
     */
    public void maybePersistLocked(long currentTimeMillis) {
        Objects.requireNonNull(mRotator, "missing FileRotator");
        final long pendingBytes = mPending.getTotalBytes();
        if (pendingBytes >= mPersistThresholdBytes) {
            forcePersistLocked(currentTimeMillis);
        } else {
            mRotator.maybeRotate(currentTimeMillis);
        }
    }

    /**
     * Force persisting any pending deltas.
     */
    public void forcePersistLocked(long currentTimeMillis) {
        Objects.requireNonNull(mRotator, "missing FileRotator");
        if (mPending.isDirty()) {
            if (LOGD) Log.d(TAG, "forcePersistLocked() writing for " + mCookie);
            try {
                mRotator.rewriteActive(mPendingRewriter, currentTimeMillis);
                mRotator.maybeRotate(currentTimeMillis);
                mPending.reset();
            } catch (IOException e) {
                Log.wtf(TAG, "problem persisting pending stats", e);
                recoverAndDeleteData();
            } catch (OutOfMemoryError e) {
                Log.wtf(TAG, "problem persisting pending stats", e);
                recoverAndDeleteData();
            }
        }
    }

    /**
     * Remove the given UID from all {@link FileRotator} history, migrating it
     * to {@link TrafficStats#UID_REMOVED}.
     */
    public void removeUidsLocked(int[] uids) {
        if (mRotator != null) {
            try {
                // Rewrite all persisted data to migrate UID stats
                mRotator.rewriteAll(new RemoveUidRewriter(mBucketDuration, uids));
            } catch (IOException e) {
                Log.wtf(TAG, "problem removing UIDs " + Arrays.toString(uids), e);
                recoverAndDeleteData();
            } catch (OutOfMemoryError e) {
                Log.wtf(TAG, "problem removing UIDs " + Arrays.toString(uids), e);
                recoverAndDeleteData();
            }
        }

        // Remove any pending stats
        if (mPending != null) {
            mPending.removeUids(uids);
        }
        if (mSinceBoot != null) {
            mSinceBoot.removeUids(uids);
        }

        // Clear UID from current stats snapshot
        if (mLastSnapshot != null) {
            mLastSnapshot.removeUids(uids);
        }

        final NetworkStatsCollection complete = mComplete != null ? mComplete.get() : null;
        if (complete != null) {
            complete.removeUids(uids);
        }
    }

    /**
     * Rewriter that will combine current {@link NetworkStatsCollection} values
     * with anything read from disk, and write combined set to disk.
     */
    private static class CombiningRewriter implements FileRotator.Rewriter {
        private final NetworkStatsCollection mCollection;

        public CombiningRewriter(NetworkStatsCollection collection) {
            mCollection = Objects.requireNonNull(collection, "missing NetworkStatsCollection");
        }

        @Override
        public void reset() {
            // ignored
        }

        @Override
        public void read(InputStream in) throws IOException {
            mCollection.read(in);
        }

        @Override
        public boolean shouldWrite() {
            return true;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            mCollection.write(out);
        }
    }

    /**
     * Rewriter that will remove any {@link NetworkStatsHistory} attributed to
     * the requested UID, only writing data back when modified.
     */
    public static class RemoveUidRewriter implements FileRotator.Rewriter {
        private final NetworkStatsCollection mTemp;
        private final int[] mUids;

        public RemoveUidRewriter(long bucketDuration, int[] uids) {
            mTemp = new NetworkStatsCollection(bucketDuration);
            mUids = uids;
        }

        @Override
        public void reset() {
            mTemp.reset();
        }

        @Override
        public void read(InputStream in) throws IOException {
            mTemp.read(in);
            mTemp.clearDirty();
            mTemp.removeUids(mUids);
        }

        @Override
        public boolean shouldWrite() {
            return mTemp.isDirty();
        }

        @Override
        public void write(OutputStream out) throws IOException {
            mTemp.write(out);
        }
    }

    /**
     * Import a specified {@link NetworkStatsCollection} instance into this recorder,
     * and write it into a standalone file.
     * @param collection The target {@link NetworkStatsCollection} instance to be imported.
     */
    public void importCollectionLocked(@NonNull NetworkStatsCollection collection)
            throws IOException {
        if (mRotator != null) {
            mRotator.rewriteSingle(new CombiningRewriter(collection), collection.getStartMillis(),
                    collection.getEndMillis());
        }

        if (mComplete != null) {
            throw new IllegalStateException("cannot import data when data already loaded");
        }
    }

    /**
     * Rewriter that will remove any histories or persisted data points before the
     * specified cutoff time, only writing data back when modified.
     */
    public static class RemoveDataBeforeRewriter implements FileRotator.Rewriter {
        private final NetworkStatsCollection mTemp;
        private final long mCutoffMills;

        public RemoveDataBeforeRewriter(long bucketDuration, long cutoffMills) {
            mTemp = new NetworkStatsCollection(bucketDuration);
            mCutoffMills = cutoffMills;
        }

        @Override
        public void reset() {
            mTemp.reset();
        }

        @Override
        public void read(InputStream in) throws IOException {
            mTemp.read(in);
            mTemp.clearDirty();
            mTemp.removeHistoryBefore(mCutoffMills);
        }

        @Override
        public boolean shouldWrite() {
            return mTemp.isDirty();
        }

        @Override
        public void write(OutputStream out) throws IOException {
            mTemp.write(out);
        }
    }

    /**
     * Remove persisted data which contains or is before the cutoff timestamp.
     */
    public void removeDataBefore(long cutoffMillis) throws IOException {
        if (mRotator != null) {
            try {
                mRotator.rewriteAll(new RemoveDataBeforeRewriter(
                        mBucketDuration, cutoffMillis));
            } catch (IOException e) {
                Log.wtf(TAG, "problem importing netstats", e);
                recoverAndDeleteData();
            } catch (OutOfMemoryError e) {
                Log.wtf(TAG, "problem importing netstats", e);
                recoverAndDeleteData();
            }
        }

        // Clean up any pending stats
        if (mPending != null) {
            mPending.removeHistoryBefore(cutoffMillis);
        }
        if (mSinceBoot != null) {
            mSinceBoot.removeHistoryBefore(cutoffMillis);
        }

        final NetworkStatsCollection complete = mComplete != null ? mComplete.get() : null;
        if (complete != null) {
            complete.removeHistoryBefore(cutoffMillis);
        }
    }

    public void dumpLocked(IndentingPrintWriter pw, boolean fullHistory) {
        if (mPending != null) {
            pw.print("Pending bytes: "); pw.println(mPending.getTotalBytes());
        }
        if (fullHistory) {
            pw.println("Complete history:");
            getOrLoadCompleteLocked().dump(pw);
        } else {
            pw.println("History since boot:");
            mSinceBoot.dump(pw);
        }
    }

    public void dumpDebugLocked(ProtoOutputStream proto, long tag) {
        final long start = proto.start(tag);
        if (mPending != null) {
            proto.write(NetworkStatsRecorderProto.PENDING_TOTAL_BYTES,
                    mPending.getTotalBytes());
        }
        getOrLoadCompleteLocked().dumpDebug(proto,
                NetworkStatsRecorderProto.COMPLETE_HISTORY);
        proto.end(start);
    }

    public void dumpCheckin(PrintWriter pw, long start, long end) {
        // Only load and dump stats from the requested window
        getOrLoadPartialLocked(start, end).dumpCheckin(pw, start, end);
    }

    /**
     * Recover from {@link FileRotator} failure by dumping state to
     * {@link DropBoxManager} and deleting contents if this recorder
     * sets {@code mWipeOnError} to true, otherwise keep the contents.
     */
    void recoverAndDeleteData() {
        if (DUMP_BEFORE_DELETE) {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                mRotator.dumpAll(os);
            } catch (IOException e) {
                // ignore partial contents
                os.reset();
            } finally {
                IoUtils.closeQuietly(os);
            }
            mDropBox.addData(TAG_NETSTATS_DUMP, os.toByteArray(), 0);
        }
        // Delete all files if this recorder is set wipe on error.
        if (mWipeOnError) {
            mRotator.deleteAll();
        }
    }
}
