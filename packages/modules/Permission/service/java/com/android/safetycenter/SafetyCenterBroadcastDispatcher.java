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

package com.android.safetycenter;

import static android.Manifest.permission.READ_SAFETY_CENTER_STATUS;
import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.os.PowerExemptionManager.REASON_REFRESH_SAFETY_SOURCES;
import static android.os.PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;
import static android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES;
import static android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_SAFETY_CENTER_ENABLED;

import static java.util.Collections.unmodifiableList;

import android.annotation.UserIdInt;
import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetySourceData;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.android.permission.util.PackageUtils;
import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;
import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.data.SafetyCenterDataManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class that dispatches SafetyCenter broadcasts.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@NotThreadSafe
final class SafetyCenterBroadcastDispatcher {
    private static final String TAG = "SafetyCenterBroadcastDi";

    private final Context mContext;
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;
    private final SafetyCenterDataManager mSafetyCenterDataManager;

    SafetyCenterBroadcastDispatcher(
            Context context,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterRefreshTracker safetyCenterRefreshTracker,
            SafetyCenterDataManager safetyCenterDataManager) {
        mContext = context;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
        mSafetyCenterDataManager = safetyCenterDataManager;
    }

    /**
     * Triggers a refresh of safety sources by sending them broadcasts with action {@link
     * SafetyCenterManager#ACTION_REFRESH_SAFETY_SOURCES}, and returns the associated broadcast id.
     *
     * <p>Returns {@code null} if no broadcast was sent.
     *
     * @param safetySourceIds list of IDs to specify the safety sources to be refreshed or a {@code
     *     null} value to refresh all safety sources.
     */
    @Nullable
    String sendRefreshSafetySources(
            @RefreshReason int refreshReason,
            UserProfileGroup userProfileGroup,
            @Nullable List<String> safetySourceIds) {
        List<Broadcast> broadcasts = mSafetyCenterConfigReader.getBroadcasts();
        BroadcastOptions broadcastOptions = createBroadcastOptions();

        String broadcastId =
                mSafetyCenterRefreshTracker.reportRefreshInProgress(
                        refreshReason, userProfileGroup);
        boolean hasSentAtLeastOneBroadcast = false;

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            hasSentAtLeastOneBroadcast |=
                    sendRefreshSafetySourcesBroadcast(
                            broadcast,
                            broadcastOptions,
                            refreshReason,
                            userProfileGroup,
                            broadcastId,
                            safetySourceIds);
        }

        if (!hasSentAtLeastOneBroadcast) {
            mSafetyCenterRefreshTracker.clearRefresh(broadcastId);
            return null;
        }

        return broadcastId;
    }

    private boolean sendRefreshSafetySourcesBroadcast(
            Broadcast broadcast,
            BroadcastOptions broadcastOptions,
            @RefreshReason int refreshReason,
            UserProfileGroup userProfileGroup,
            String broadcastId,
            @Nullable List<String> requiredSourceIds) {
        boolean hasSentAtLeastOneBroadcast = false;
        String packageName = broadcast.getPackageName();
        Set<String> deniedSourceIds = getRefreshDeniedSourceIds(refreshReason);
        SparseArray<List<String>> userIdsToSourceIds =
                getUserIdsToSourceIds(broadcast, userProfileGroup, refreshReason);

        for (int i = 0; i < userIdsToSourceIds.size(); i++) {
            int userId = userIdsToSourceIds.keyAt(i);
            List<String> sourceIds = userIdsToSourceIds.valueAt(i);

            if (!deniedSourceIds.isEmpty()) {
                sourceIds = new ArrayList<>(sourceIds);
                sourceIds.removeAll(deniedSourceIds);
            }

            if (requiredSourceIds != null) {
                sourceIds = new ArrayList<>(sourceIds);
                sourceIds.retainAll(requiredSourceIds);
            }

            if (sourceIds.isEmpty()) {
                continue;
            }

            Intent intent = createRefreshIntent(refreshReason, packageName, sourceIds, broadcastId);
            boolean broadcastWasSent =
                    sendBroadcastIfResolves(intent, UserHandle.of(userId), broadcastOptions);
            if (broadcastWasSent) {
                mSafetyCenterRefreshTracker.reportSourceRefreshesInFlight(
                        broadcastId, sourceIds, userId);
            }
            hasSentAtLeastOneBroadcast |= broadcastWasSent;
        }

        return hasSentAtLeastOneBroadcast;
    }

    /**
     * Triggers an {@link SafetyCenterManager#ACTION_SAFETY_CENTER_ENABLED_CHANGED} broadcast for
     * all safety sources.
     *
     * <p>This method also sends an implicit broadcast globally (which requires the {@link
     * android.Manifest.permission#READ_SAFETY_CENTER_STATUS} permission).
     */
    // TODO(b/227310195): Consider adding a boolean extra to the intent instead of having clients
    //  rely on SafetyCenterManager#isSafetyCenterEnabled()?
    void sendEnabledChanged() {
        List<Broadcast> broadcasts = mSafetyCenterConfigReader.getBroadcasts();
        BroadcastOptions broadcastOptions = createBroadcastOptions();
        List<UserProfileGroup> userProfileGroups =
                UserProfileGroup.getAllUserProfileGroups(mContext);

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendEnabledChangedBroadcast(broadcast, broadcastOptions, userProfileGroups);
        }

        Intent implicitIntent = createImplicitEnabledChangedIntent();
        sendBroadcast(
                implicitIntent,
                UserHandle.SYSTEM,
                READ_SAFETY_CENTER_STATUS,
                /* broadcastOptions= */ null);
    }

    private void sendEnabledChangedBroadcast(
            Broadcast broadcast,
            BroadcastOptions broadcastOptions,
            List<UserProfileGroup> userProfileGroups) {
        Intent intent = createExplicitEnabledChangedIntent(broadcast.getPackageName());

        for (int i = 0; i < userProfileGroups.size(); i++) {
            UserProfileGroup userProfileGroup = userProfileGroups.get(i);
            SparseArray<List<String>> userIdsToSourceIds =
                    getUserIdsToSourceIds(
                            broadcast,
                            userProfileGroup,
                            // The same ENABLED reason is used here for both enable and disable
                            // events. It is not sent externally and is only used internally to
                            // filter safety sources in the methods of the Broadcast class.
                            REFRESH_REASON_SAFETY_CENTER_ENABLED);

            for (int j = 0; j < userIdsToSourceIds.size(); j++) {
                int userId = userIdsToSourceIds.keyAt(j);

                sendBroadcastIfResolves(intent, UserHandle.of(userId), broadcastOptions);
            }
        }
    }

    private boolean sendBroadcastIfResolves(
            Intent intent, UserHandle userHandle, @Nullable BroadcastOptions broadcastOptions) {
        if (!doesBroadcastResolve(intent, userHandle)) {
            Log.w(
                    TAG,
                    "No receiver for intent targeting: "
                            + intent.getPackage()
                            + ", and user id: "
                            + userHandle.getIdentifier());
            return false;
        }
        Log.v(
                TAG,
                "Found receiver for intent targeting: "
                        + intent.getPackage()
                        + ", and user id: "
                        + userHandle.getIdentifier());
        sendBroadcast(intent, userHandle, SEND_SAFETY_CENTER_UPDATE, broadcastOptions);
        return true;
    }

    private void sendBroadcast(
            Intent intent,
            UserHandle userHandle,
            String permission,
            @Nullable BroadcastOptions broadcastOptions) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(
                    intent,
                    userHandle,
                    permission,
                    broadcastOptions == null ? null : broadcastOptions.toBundle());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private boolean doesBroadcastResolve(Intent broadcastIntent, UserHandle userHandle) {
        return !PackageUtils.queryUnfilteredBroadcastReceiversAsUser(
                        broadcastIntent, /* flags= */ 0, userHandle.getIdentifier(), mContext)
                .isEmpty();
    }

    private static Intent createExplicitEnabledChangedIntent(String packageName) {
        return createImplicitEnabledChangedIntent().setPackage(packageName);
    }

    private static Intent createImplicitEnabledChangedIntent() {
        return createBroadcastIntent(ACTION_SAFETY_CENTER_ENABLED_CHANGED);
    }

    private static Intent createRefreshIntent(
            @RefreshReason int refreshReason,
            String packageName,
            List<String> sourceIdsToRefresh,
            String broadcastId) {
        String[] sourceIdsArray = sourceIdsToRefresh.toArray(new String[0]);
        int requestType = RefreshReasons.toRefreshRequestType(refreshReason);
        Intent refreshIntent =
                createBroadcastIntent(ACTION_REFRESH_SAFETY_SOURCES)
                        .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                        .putExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS, sourceIdsArray)
                        .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, broadcastId)
                        .setPackage(packageName);
        boolean isUserInitiated = !RefreshReasons.isBackgroundRefresh(refreshReason);
        if (isUserInitiated) {
            return refreshIntent.addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
        }
        return refreshIntent;
    }

    private static Intent createBroadcastIntent(String intentAction) {
        return new Intent(intentAction).addFlags(FLAG_RECEIVER_FOREGROUND);
    }

    private static BroadcastOptions createBroadcastOptions() {
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        Duration allowListDuration = SafetyCenterFlags.getFgsAllowlistDuration();
        // This call requires the START_FOREGROUND_SERVICES_FROM_BACKGROUND permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            broadcastOptions.setTemporaryAppAllowlist(
                    allowListDuration.toMillis(),
                    TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                    REASON_REFRESH_SAFETY_SOURCES,
                    "Safety Center is requesting data from safety sources");
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return broadcastOptions;
    }

    /** Returns the list of source IDs for which refreshing is denied for the given reason. */
    private static Set<String> getRefreshDeniedSourceIds(@RefreshReason int refreshReason) {
        if (RefreshReasons.isBackgroundRefresh(refreshReason)) {
            return SafetyCenterFlags.getBackgroundRefreshDeniedSourceIds();
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Returns a flattened mapping from user IDs to lists of source IDs for those users. The map is
     * in the form of a {@link SparseArray} where the int keys are user IDs and the values are the
     * lists of source IDs.
     *
     * <p>The set of user IDs (keys) is the profile parent user ID of {@code userProfileGroup} plus
     * all the other types of running profiles:
     * <ol>
     *     <li>The (possibly empty) set of running managed profile user IDs in that group.
     *     <li>The (possibly empty) set of running private profile user ID in that group.
     * </ol>
     * <p>Every value present is a non-empty list, but the overall result may be empty.
     */
    private SparseArray<List<String>> getUserIdsToSourceIds(
            Broadcast broadcast,
            UserProfileGroup userProfileGroup,
            @RefreshReason int refreshReason) {
        SparseArray<List<String>> result =
                new SparseArray<>(userProfileGroup.getNumRunningProfiles());
        for (int profilTypeIdx = 0;
                profilTypeIdx < ProfileType.ALL_PROFILE_TYPES.length;
                ++profilTypeIdx) {
            @ProfileType int profileType = ProfileType.ALL_PROFILE_TYPES[profilTypeIdx];
            int[] runningProfiles = userProfileGroup.getRunningProfilesOfType(profileType);
            for (int profileIdx = 0; profileIdx < runningProfiles.length; ++profileIdx) {
                List<String> profileSources =
                        getSourceIdsForRefreshReason(
                                refreshReason,
                                broadcast.getSourceIdsForProfileType(profileType),
                                broadcast.getSourceIdsOnPageOpenForProfileType(profileType),
                                runningProfiles[profileIdx]);

                if (!profileSources.isEmpty()) {
                    result.put(runningProfiles[profileIdx], profileSources);
                }
            }
        }

        return result;
    }

    /**
     * Returns the sources to refresh for the given {@code refreshReason}.
     *
     * <p>For {@link SafetyCenterManager#REFRESH_REASON_PAGE_OPEN}, returns a copy of {@code
     * allSourceIds} filtered to contain only sources that have refreshOnPageOpenAllowed in the XML
     * config, or are in the safety_center_override_refresh_on_page_open_sources flag, or don't have
     * any {@link SafetySourceData} provided.
     */
    private List<String> getSourceIdsForRefreshReason(
            @RefreshReason int refreshReason,
            List<String> allSourceIds,
            List<String> pageOpenSourceIds,
            @UserIdInt int userId) {
        if (refreshReason != REFRESH_REASON_PAGE_OPEN) {
            return allSourceIds;
        }

        List<String> sourceIds = new ArrayList<>();

        ArraySet<String> flagAllowListedSourceIds =
                SafetyCenterFlags.getOverrideRefreshOnPageOpenSourceIds();

        for (int i = 0; i < allSourceIds.size(); i++) {
            String sourceId = allSourceIds.get(i);
            if (pageOpenSourceIds.contains(sourceId)
                    || flagAllowListedSourceIds.contains(sourceId)
                    || mSafetyCenterDataManager.getSafetySourceDataInternal(
                                    SafetySourceKey.of(sourceId, userId))
                            == null) {
                sourceIds.add(sourceId);
            }
        }

        return unmodifiableList(sourceIds);
    }
}
