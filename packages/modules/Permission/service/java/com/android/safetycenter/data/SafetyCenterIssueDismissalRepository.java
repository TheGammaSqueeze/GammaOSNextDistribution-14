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

package com.android.safetycenter.data;

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.UserIdInt;
import android.annotation.WorkerThread;
import android.content.ApexEnvironment;
import android.os.Handler;
import android.safetycenter.SafetySourceData;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.modules.utils.BackgroundThread;
import com.android.safetycenter.ApiLock;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.persistence.PersistedSafetyCenterIssue;
import com.android.safetycenter.persistence.PersistenceException;
import com.android.safetycenter.persistence.SafetyCenterIssuesPersistence;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Repository to manage data about all issue dismissals in Safety Center.
 *
 * <p>It stores the state of this class automatically into a file. After the class is first
 * instantiated the user should call {@link
 * SafetyCenterIssueDismissalRepository#loadStateFromFile()} to initialize the state with what was
 * stored in the file.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@NotThreadSafe
final class SafetyCenterIssueDismissalRepository {

    private static final String TAG = "SafetyCenterIssueDis";

    /** The APEX name used to retrieve the APEX owned data directories. */
    private static final String APEX_MODULE_NAME = "com.android.permission";

    /** The name of the file used to persist the {@link SafetyCenterIssueDismissalRepository}. */
    private static final String ISSUE_DISMISSAL_REPOSITORY_FILE_NAME = "safety_center_issues.xml";

    /** The time delay used to throttle and aggregate writes to disk. */
    private static final Duration WRITE_DELAY = Duration.ofMillis(500);

    private final Handler mWriteHandler = BackgroundThread.getHandler();

    private final ApiLock mApiLock;

    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    private final ArrayMap<SafetyCenterIssueKey, IssueData> mIssues = new ArrayMap<>();
    private boolean mWriteStateToFileScheduled = false;

    SafetyCenterIssueDismissalRepository(
            ApiLock apiLock, SafetyCenterConfigReader safetyCenterConfigReader) {
        mApiLock = apiLock;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    /**
     * Returns {@code true} if the issue with the given key and severity level is currently
     * dismissed.
     *
     * <p>An issue which is dismissed at one time may become "un-dismissed" later, after the
     * resurface delay (which depends on severity level) has elapsed.
     *
     * <p>If the given issue key is not found in the repository this method returns {@code false}.
     */
    boolean isIssueDismissed(
            SafetyCenterIssueKey safetyCenterIssueKey,
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "checking if dismissed");
        if (issueData == null) {
            return false;
        }

        Instant dismissedAt = issueData.getDismissedAt();
        boolean isNotCurrentlyDismissed = dismissedAt == null;
        if (isNotCurrentlyDismissed) {
            return false;
        }

        long maxCount = SafetyCenterFlags.getResurfaceIssueMaxCount(safetySourceIssueSeverityLevel);
        Duration delay = SafetyCenterFlags.getResurfaceIssueDelay(safetySourceIssueSeverityLevel);

        boolean hasAlreadyResurfacedTheMaxAllowedNumberOfTimes =
                issueData.getDismissCount() > maxCount;
        if (hasAlreadyResurfacedTheMaxAllowedNumberOfTimes) {
            return true;
        }

        Duration timeSinceLastDismissal = Duration.between(dismissedAt, Instant.now());
        boolean isTimeToResurface = timeSinceLastDismissal.compareTo(delay) >= 0;
        return !isTimeToResurface;
    }

    /**
     * Marks the issue with the given key as dismissed.
     *
     * <p>That issue's notification (if any) is also marked as dismissed.
     */
    void dismissIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "dismissing");
        if (issueData == null) {
            return;
        }
        Instant now = Instant.now();
        issueData.setDismissedAt(now);
        issueData.setDismissCount(issueData.getDismissCount() + 1);
        issueData.setNotificationDismissedAt(now);
        scheduleWriteStateToFile();
    }

    /**
     * Copy dismissal data from one issue to the other.
     *
     * <p>This will align dismissal state of these issues, unless issues are of different
     * severities, in which case they can potentially differ in resurface times.
     */
    void copyDismissalData(SafetyCenterIssueKey keyFrom, SafetyCenterIssueKey keyTo) {
        IssueData dataFrom = getOrWarn(keyFrom, "copying dismissal data");
        IssueData dataTo = getOrWarn(keyTo, "copying dismissal data");
        if (dataFrom == null || dataTo == null) {
            return;
        }

        dataTo.setDismissedAt(dataFrom.getDismissedAt());
        dataTo.setDismissCount(dataFrom.getDismissCount());
        scheduleWriteStateToFile();
    }

    /**
     * Copy notification dismissal data from one issue to the other.
     *
     * <p>This will align notification dismissal state of these issues.
     */
    void copyNotificationDismissalData(SafetyCenterIssueKey keyFrom, SafetyCenterIssueKey keyTo) {
        IssueData dataFrom = getOrWarn(keyFrom, "copying notification dismissal data");
        IssueData dataTo = getOrWarn(keyTo, "copying notification dismissal data");
        if (dataFrom == null || dataTo == null) {
            return;
        }

        dataTo.setNotificationDismissedAt(dataFrom.getNotificationDismissedAt());
        scheduleWriteStateToFile();
    }

    /**
     * Marks the notification (if any) of the issue with the given key as dismissed.
     *
     * <p>The issue itself is <strong>not</strong> marked as dismissed and its warning card can
     * still appear in the Safety Center UI.
     */
    void dismissNotification(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "dismissing notification");
        if (issueData == null) {
            return;
        }
        issueData.setNotificationDismissedAt(Instant.now());
        scheduleWriteStateToFile();
    }

    /**
     * Returns the {@link Instant} when the issue with the given key was first reported to Safety
     * Center.
     */
    @Nullable
    Instant getIssueFirstSeenAt(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "getting first seen");
        if (issueData == null) {
            return null;
        }
        return issueData.getFirstSeenAt();
    }

    @Nullable
    private Instant getNotificationDismissedAt(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "getting notification dismissed");
        if (issueData == null) {
            return null;
        }
        return issueData.getNotificationDismissedAt();
    }

    /** Returns {@code true} if an issue's notification is dismissed now. */
    // TODO(b/259084807): Consider extracting notification dismissal logic to separate class
    boolean isNotificationDismissedNow(
            SafetyCenterIssueKey issueKey, @SafetySourceData.SeverityLevel int severityLevel) {
        // The current code for dismissing an issue/warning card also dismisses any
        // corresponding notification, but it is still necessary to check the issue dismissal
        // status, in addition to the notification dismissal (below) because issues may have been
        // dismissed by an earlier version of the code which lacked this functionality.
        if (isIssueDismissed(issueKey, severityLevel)) {
            return true;
        }

        Instant dismissedAt = getNotificationDismissedAt(issueKey);
        if (dismissedAt == null) {
            // Notification was never dismissed
            return false;
        }

        Duration resurfaceDelay = SafetyCenterFlags.getNotificationResurfaceInterval();
        if (resurfaceDelay == null) {
            // Null resurface delay means notifications may never resurface
            return true;
        }

        Instant canResurfaceAt = dismissedAt.plus(resurfaceDelay);
        return Instant.now().isBefore(canResurfaceAt);
    }

    /**
     * Updates the issue repository to contain exactly the given {@code safetySourceIssueIds} for
     * the supplied source and user.
     */
    void updateIssuesForSource(
            ArraySet<String> safetySourceIssueIds, String safetySourceId, @UserIdInt int userId) {
        boolean someDataChanged = false;

        // Remove issues no longer reported by the source.
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mIssues.keyAt(i);
            boolean doesNotBelongToUserOrSource =
                    issueKey.getUserId() != userId
                            || !Objects.equals(issueKey.getSafetySourceId(), safetySourceId);
            if (doesNotBelongToUserOrSource) {
                continue;
            }
            boolean isIssueNoLongerReported =
                    !safetySourceIssueIds.contains(issueKey.getSafetySourceIssueId());
            if (isIssueNoLongerReported) {
                mIssues.removeAt(i);
                someDataChanged = true;
            }
        }
        // Add newly reported issues.
        for (int i = 0; i < safetySourceIssueIds.size(); i++) {
            SafetyCenterIssueKey issueKey =
                    SafetyCenterIssueKey.newBuilder()
                            .setUserId(userId)
                            .setSafetySourceId(safetySourceId)
                            .setSafetySourceIssueId(safetySourceIssueIds.valueAt(i))
                            .build();
            boolean isIssueNewlyReported = !mIssues.containsKey(issueKey);
            if (isIssueNewlyReported) {
                mIssues.put(issueKey, new IssueData(Instant.now()));
                someDataChanged = true;
            }
        }
        if (someDataChanged) {
            scheduleWriteStateToFile();
        }
    }

    /** Returns whether the issue is currently hidden. */
    boolean isIssueHidden(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "checking if issue hidden");
        if (issueData == null || !issueData.isHidden()) {
            return false;
        }

        Instant timerStart = issueData.getResurfaceTimerStartTime();
        if (timerStart == null) {
            return true;
        }

        Duration delay = SafetyCenterFlags.getTemporarilyHiddenIssueResurfaceDelay();
        Duration timeSinceTimerStarted = Duration.between(timerStart, Instant.now());
        boolean isTimeToResurface = timeSinceTimerStarted.compareTo(delay) >= 0;

        if (isTimeToResurface) {
            issueData.setHidden(false);
            issueData.setResurfaceTimerStartTime(null);
            return false;
        }
        return true;
    }

    /** Hides the issue with the given {@link SafetyCenterIssueKey}. */
    void hideIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "hiding issue");
        if (issueData != null) {
            issueData.setHidden(true);
            // to abide by the method was called last: hideIssue or resurfaceHiddenIssueAfterPeriod
            issueData.setResurfaceTimerStartTime(null);
        }
    }

    /**
     * The issue with the given {@link SafetyCenterIssueKey} will be resurfaced (marked as not
     * hidden) after a period of time defined by {@link
     * SafetyCenterFlags#getTemporarilyHiddenIssueResurfaceDelay()}, such that {@link
     * SafetyCenterIssueDismissalRepository#isIssueHidden} will start returning {@code false} for
     * the given issue.
     *
     * <p>If this method is called multiple times in a row, the period will be set by the first call
     * and all following calls won't have any effect.
     */
    void resurfaceHiddenIssueAfterPeriod(SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "resurfacing hidden issue");
        if (issueData == null) {
            return;
        }

        // if timer already started, we don't want to restart
        if (issueData.getResurfaceTimerStartTime() == null) {
            issueData.setResurfaceTimerStartTime(Instant.now());
        }
    }

    /** Takes a snapshot of the contents of the repository to be written to persistent storage. */
    private List<PersistedSafetyCenterIssue> snapshot() {
        List<PersistedSafetyCenterIssue> persistedIssues = new ArrayList<>();
        for (int i = 0; i < mIssues.size(); i++) {
            String encodedKey = SafetyCenterIds.encodeToString(mIssues.keyAt(i));
            IssueData issueData = mIssues.valueAt(i);
            persistedIssues.add(issueData.toPersistedIssueBuilder().setKey(encodedKey).build());
        }
        return persistedIssues;
    }

    /**
     * Replaces the contents of the repository with the given issues read from persistent storage.
     */
    private void load(List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues) {
        boolean someDataChanged = false;
        mIssues.clear();
        for (int i = 0; i < persistedSafetyCenterIssues.size(); i++) {
            PersistedSafetyCenterIssue persistedIssue = persistedSafetyCenterIssues.get(i);
            SafetyCenterIssueKey key = SafetyCenterIds.issueKeyFromString(persistedIssue.getKey());

            // Only load the issues associated with the "real" config. We do not want to keep on
            // persisting potentially stray issues from tests (they should supposedly be cleared,
            // but may stick around if the data is not cleared after a test run).
            // There is a caveat that if a real source was overridden in tests and the override
            // provided data without clearing it, we will associate this issue with the real source.
            if (!mSafetyCenterConfigReader.isExternalSafetySourceFromRealConfig(
                    key.getSafetySourceId())) {
                someDataChanged = true;
                continue;
            }

            IssueData issueData = IssueData.fromPersistedIssue(persistedIssue);
            mIssues.put(key, issueData);
        }
        if (someDataChanged) {
            scheduleWriteStateToFile();
        }
    }

    /** Clears all the data in the repository. */
    public void clear() {
        mIssues.clear();
        scheduleWriteStateToFile();
    }

    /** Clears all the data in the repository for the given user. */
    void clearForUser(@UserIdInt int userId) {
        boolean someDataChanged = false;
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mIssues.keyAt(i);
            if (issueKey.getUserId() == userId) {
                mIssues.removeAt(i);
                someDataChanged = true;
            }
        }
        if (someDataChanged) {
            scheduleWriteStateToFile();
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(FileDescriptor fd, PrintWriter fout) {
        int issueRepositoryCount = mIssues.size();
        fout.println(
                "ISSUE DISMISSAL REPOSITORY ("
                        + issueRepositoryCount
                        + ", mWriteStateToFileScheduled="
                        + mWriteStateToFileScheduled
                        + ")");
        for (int i = 0; i < issueRepositoryCount; i++) {
            SafetyCenterIssueKey key = mIssues.keyAt(i);
            IssueData data = mIssues.valueAt(i);
            fout.println("\t[" + i + "] " + toUserFriendlyString(key) + " -> " + data);
        }
        fout.println();

        File issueDismissalRepositoryFile = getIssueDismissalRepositoryFile();
        fout.println(
                "ISSUE DISMISSAL REPOSITORY FILE ("
                        + issueDismissalRepositoryFile.getAbsolutePath()
                        + ")");
        fout.flush();
        try {
            Files.copy(issueDismissalRepositoryFile.toPath(), new FileOutputStream(fd));
            fout.println();
        } catch (NoSuchFileException e) {
            fout.println("<No File> (equivalent to empty issue list)");
        } catch (IOException e) {
            printError(e, fout);
        }
        fout.println();
    }

    // We want to dump the stack trace on a specific PrintWriter here, this is a false positive as
    // the warning does not consider the overload that takes a PrintWriter as an argument (yet).
    @SuppressWarnings("CatchAndPrintStackTrace")
    private void printError(Throwable error, PrintWriter fout) {
        error.printStackTrace(fout);
    }

    @Nullable
    private IssueData getOrWarn(SafetyCenterIssueKey issueKey, String reason) {
        IssueData issueData = mIssues.get(issueKey);
        if (issueData == null) {
            Log.w(
                    TAG,
                    "Issue missing when reading from dismissal repository for "
                            + reason
                            + ": "
                            + toUserFriendlyString(issueKey));
            return null;
        }
        return issueData;
    }

    /** Schedule writing the {@link SafetyCenterIssueDismissalRepository} to file. */
    private void scheduleWriteStateToFile() {
        if (!mWriteStateToFileScheduled) {
            mWriteHandler.postDelayed(this::writeStateToFile, WRITE_DELAY.toMillis());
            mWriteStateToFileScheduled = true;
        }
    }

    @WorkerThread
    private void writeStateToFile() {
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues;

        synchronized (mApiLock) {
            mWriteStateToFileScheduled = false;
            persistedSafetyCenterIssues = snapshot();
            // Since all write operations are scheduled in the same background thread, we can safely
            // release the lock after creating a snapshot and know that all snapshots will be
            // written in the correct order even if we are not holding the lock.
        }

        SafetyCenterIssuesPersistence.write(
                persistedSafetyCenterIssues, getIssueDismissalRepositoryFile());
    }

    /** Read the contents of the file and load them into this class. */
    void loadStateFromFile() {
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues = new ArrayList<>();

        try {
            persistedSafetyCenterIssues =
                    SafetyCenterIssuesPersistence.read(getIssueDismissalRepositoryFile());
            Log.d(TAG, "Safety Center persisted issues read successfully");
        } catch (PersistenceException e) {
            Log.w(TAG, "Cannot read Safety Center persisted issues", e);
        }

        load(persistedSafetyCenterIssues);
        scheduleWriteStateToFile();
    }

    private static File getIssueDismissalRepositoryFile() {
        ApexEnvironment apexEnvironment = ApexEnvironment.getApexEnvironment(APEX_MODULE_NAME);
        File dataDirectory = apexEnvironment.getDeviceProtectedDataDir();
        // It should resolve to /data/misc/apexdata/com.android.permission/safety_center_issues.xml
        return new File(dataDirectory, ISSUE_DISMISSAL_REPOSITORY_FILE_NAME);
    }

    /**
     * An internal mutable data structure containing issue metadata which is used to determine
     * whether an issue should be dismissed/hidden from the user.
     */
    private static final class IssueData {

        private static IssueData fromPersistedIssue(PersistedSafetyCenterIssue persistedIssue) {
            IssueData issueData = new IssueData(persistedIssue.getFirstSeenAt());
            issueData.setDismissedAt(persistedIssue.getDismissedAt());
            issueData.setDismissCount(persistedIssue.getDismissCount());
            issueData.setNotificationDismissedAt(persistedIssue.getNotificationDismissedAt());
            return issueData;
        }

        private final Instant mFirstSeenAt;

        @Nullable private Instant mDismissedAt;
        private int mDismissCount;

        @Nullable private Instant mNotificationDismissedAt;

        // TODO(b/270015734): maybe persist those as well
        private boolean mHidden = false;
        // Moment when a theoretical timer starts - when it ends the issue gets unmarked as hidden.
        @Nullable private Instant mResurfaceTimerStartTime;

        private IssueData(Instant firstSeenAt) {
            mFirstSeenAt = firstSeenAt;
        }

        private Instant getFirstSeenAt() {
            return mFirstSeenAt;
        }

        @Nullable
        private Instant getDismissedAt() {
            return mDismissedAt;
        }

        private void setDismissedAt(@Nullable Instant dismissedAt) {
            mDismissedAt = dismissedAt;
        }

        private int getDismissCount() {
            return mDismissCount;
        }

        private void setDismissCount(int dismissCount) {
            mDismissCount = dismissCount;
        }

        @Nullable
        private Instant getNotificationDismissedAt() {
            return mNotificationDismissedAt;
        }

        private void setNotificationDismissedAt(@Nullable Instant notificationDismissedAt) {
            mNotificationDismissedAt = notificationDismissedAt;
        }

        private boolean isHidden() {
            return mHidden;
        }

        private void setHidden(boolean hidden) {
            mHidden = hidden;
        }

        @Nullable
        private Instant getResurfaceTimerStartTime() {
            return mResurfaceTimerStartTime;
        }

        private void setResurfaceTimerStartTime(@Nullable Instant resurfaceTimerStartTime) {
            this.mResurfaceTimerStartTime = resurfaceTimerStartTime;
        }

        private PersistedSafetyCenterIssue.Builder toPersistedIssueBuilder() {
            return new PersistedSafetyCenterIssue.Builder()
                    .setFirstSeenAt(mFirstSeenAt)
                    .setDismissedAt(mDismissedAt)
                    .setDismissCount(mDismissCount)
                    .setNotificationDismissedAt(mNotificationDismissedAt);
        }

        @Override
        public String toString() {
            return "SafetySourceIssueInfo{"
                    + "mFirstSeenAt="
                    + mFirstSeenAt
                    + ", mDismissedAt="
                    + mDismissedAt
                    + ", mDismissCount="
                    + mDismissCount
                    + ", mNotificationDismissedAt="
                    + mNotificationDismissedAt
                    + '}';
        }
    }
}
