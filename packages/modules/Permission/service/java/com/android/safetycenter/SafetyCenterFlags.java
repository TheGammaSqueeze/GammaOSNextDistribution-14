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

import static android.safetycenter.SafetyCenterManager.RefreshReason;

import android.os.Binder;
import android.provider.DeviceConfig;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A class to access the Safety Center {@link DeviceConfig} flags.
 *
 * @hide
 */
public final class SafetyCenterFlags {

    private static final String TAG = "SafetyCenterFlags";

    /** {@link DeviceConfig} property name for {@link #getSafetyCenterEnabled()}. */
    static final String PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled";

    private static final String PROPERTY_NOTIFICATIONS_ENABLED =
            "safety_center_notifications_enabled";

    private static final String PROPERTY_NOTIFICATIONS_ALLOWED_SOURCES =
            "safety_center_notifications_allowed_sources";

    private static final String PROPERTY_NOTIFICATIONS_MIN_DELAY =
            "safety_center_notifications_min_delay";

    private static final String PROPERTY_NOTIFICATIONS_IMMEDIATE_BEHAVIOR_ISSUES =
            "safety_center_notifications_immediate_behavior_issues";

    private static final String PROPERTY_NOTIFICATION_RESURFACE_INTERVAL =
            "safety_center_notification_resurface_interval";

    private static final String PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION =
            "safety_center_replace_lock_screen_icon_action";

    private static final String PROPERTY_RESOLVING_ACTION_TIMEOUT_MILLIS =
            "safety_center_resolve_action_timeout_millis";

    private static final String PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS =
            "safety_center_refresh_fgs_allowlist_duration_millis";

    private static final String PROPERTY_RESURFACE_ISSUE_MAX_COUNTS =
            "safety_center_resurface_issue_max_counts";

    private static final String PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS =
            "safety_center_resurface_issue_delays_millis";

    private static final String PROPERTY_UNTRACKED_SOURCES = "safety_center_untracked_sources";

    private static final String PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES =
            "safety_center_background_refresh_denied_sources";

    private static final String PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS =
            "safety_center_refresh_sources_timeouts_millis";

    private static final String PROPERTY_ISSUE_CATEGORY_ALLOWLISTS =
            "safety_center_issue_category_allowlists";

    private static final String PROPERTY_ALLOW_STATSD_LOGGING =
            "safety_center_allow_statsd_logging";

    private static final String PROPERTY_SHOW_SUBPAGES = "safety_center_show_subpages";

    private static final String PROPERTY_OVERRIDE_REFRESH_ON_PAGE_OPEN_SOURCES =
            "safety_center_override_refresh_on_page_open_sources";

    private static final String PROPERTY_ADDITIONAL_ALLOW_PACKAGE_CERTS =
            "safety_center_additional_allow_package_certs";

    private static final Duration FGS_ALLOWLIST_DEFAULT_DURATION = Duration.ofSeconds(20);

    private static final String PROPERTY_TEMP_HIDDEN_ISSUE_RESURFACE_DELAY_MILLIS =
            "safety_center_temp_hidden_issue_resurface_delay_millis";

    private static final String PROPERTY_ACTIONS_TO_OVERRIDE_WITH_DEFAULT_INTENT =
            "safety_center_actions_to_override_with_default_intent";

    private static final Duration RESOLVING_ACTION_TIMEOUT_DEFAULT_DURATION =
            Duration.ofSeconds(10);

    private static final Duration NOTIFICATIONS_MIN_DELAY_DEFAULT_DURATION = Duration.ofDays(180);

    private static final String REFRESH_SOURCES_TIMEOUT_DEFAULT =
            "100:15000,200:60000,300:30000,400:30000,500:30000,600:3600000";
    private static final Duration REFRESH_SOURCES_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(15);

    private static final String RESURFACE_ISSUE_MAX_COUNT_DEFAULT = "200:0,300:1,400:1";
    private static final long RESURFACE_ISSUE_MAX_COUNT_DEFAULT_COUNT = 0;

    private static final String RESURFACE_ISSUE_DELAYS_DEFAULT = "";
    private static final Duration RESURFACE_ISSUE_DELAYS_DEFAULT_DURATION = Duration.ofDays(180);

    private static final ArraySet<String> sAllowedNotificationSourcesUPlus =
            new ArraySet<>(new String[] {"GoogleBackupAndRestore"});

    private static volatile String sUntrackedSourcesDefault =
            "AndroidAccessibility,AndroidBackgroundLocation,"
                    + "AndroidNotificationListener,AndroidPermissionAutoRevoke";

    private static volatile String sBackgroundRefreshDenyDefault = "";

    private static volatile String sIssueCategoryAllowlistDefault = "";

    private static volatile String sRefreshOnPageOpenSourcesDefault = "AndroidBiometrics";

    private static volatile String sActionsToOverrideWithDefaultIntentDefault = "";

    static void init(SafetyCenterResourcesApk safetyCenterResourcesApk) {
        String untrackedSourcesDefault =
                safetyCenterResourcesApk.getOptionalStringByName("config_defaultUntrackedSources");
        if (untrackedSourcesDefault != null) {
            sUntrackedSourcesDefault = untrackedSourcesDefault;
        }
        String backgroundRefreshDenyDefault =
                safetyCenterResourcesApk.getOptionalStringByName(
                        "config_defaultBackgroundRefreshDeny");
        if (backgroundRefreshDenyDefault != null) {
            sBackgroundRefreshDenyDefault = backgroundRefreshDenyDefault;
        }
        String issueCategoryAllowlistDefault =
                safetyCenterResourcesApk.getOptionalStringByName(
                        "config_defaultIssueCategoryAllowlist");
        if (issueCategoryAllowlistDefault != null) {
            sIssueCategoryAllowlistDefault = issueCategoryAllowlistDefault;
        }
        String refreshOnPageOpenSourcesDefault =
                safetyCenterResourcesApk.getOptionalStringByName(
                        "config_defaultRefreshOnPageOpenSources");
        if (refreshOnPageOpenSourcesDefault != null) {
            sRefreshOnPageOpenSourcesDefault = refreshOnPageOpenSourcesDefault;
        }
        String actionsToOverrideWithDefaultIntentDefault =
                safetyCenterResourcesApk.getOptionalStringByName(
                        "config_defaultActionsToOverrideWithDefaultIntent");
        if (actionsToOverrideWithDefaultIntentDefault != null) {
            sActionsToOverrideWithDefaultIntentDefault = actionsToOverrideWithDefaultIntentDefault;
        }
    }

    private static final Duration TEMP_HIDDEN_ISSUE_RESURFACE_DELAY_DEFAULT_DURATION =
            Duration.ofDays(2);

    /** Dumps state for debugging purposes. */
    static void dump(PrintWriter fout) {
        fout.println("FLAGS");
        printFlag(fout, PROPERTY_SAFETY_CENTER_ENABLED, getSafetyCenterEnabled());
        printFlag(fout, PROPERTY_NOTIFICATIONS_ENABLED, getNotificationsEnabled());
        printFlag(
                fout,
                PROPERTY_NOTIFICATIONS_ALLOWED_SOURCES,
                getNotificationsAllowedSourceIdsFlag());
        printFlag(fout, PROPERTY_NOTIFICATIONS_MIN_DELAY, getNotificationsMinDelay());
        printFlag(
                fout,
                PROPERTY_NOTIFICATIONS_IMMEDIATE_BEHAVIOR_ISSUES,
                getImmediateNotificationBehaviorIssues());
        printFlag(
                fout, PROPERTY_NOTIFICATION_RESURFACE_INTERVAL, getNotificationResurfaceInterval());
        printFlag(fout, PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION, getReplaceLockScreenIconAction());
        printFlag(fout, PROPERTY_RESOLVING_ACTION_TIMEOUT_MILLIS, getResolvingActionTimeout());
        printFlag(fout, PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS, getFgsAllowlistDuration());
        printFlag(fout, PROPERTY_UNTRACKED_SOURCES, getUntrackedSourceIds());
        printFlag(fout, PROPERTY_RESURFACE_ISSUE_MAX_COUNTS, getResurfaceIssueMaxCounts());
        printFlag(fout, PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS, getResurfaceIssueDelaysMillis());
        printFlag(
                fout,
                PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES,
                getBackgroundRefreshDeniedSourceIds());
        printFlag(
                fout, PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, getRefreshSourcesTimeoutsMillis());
        printFlag(fout, PROPERTY_ISSUE_CATEGORY_ALLOWLISTS, getIssueCategoryAllowlists());
        printFlag(fout, PROPERTY_ALLOW_STATSD_LOGGING, getAllowStatsdLogging());
        printFlag(fout, PROPERTY_SHOW_SUBPAGES, getShowSubpages());
        printFlag(
                fout,
                PROPERTY_OVERRIDE_REFRESH_ON_PAGE_OPEN_SOURCES,
                getOverrideRefreshOnPageOpenSourceIds());
        printFlag(
                fout,
                PROPERTY_ADDITIONAL_ALLOW_PACKAGE_CERTS,
                getAdditionalAllowedPackageCertsString());
        fout.println();
    }

    private static void printFlag(PrintWriter pw, String key, @Nullable Duration duration) {
        if (duration == null) {
            printFlag(pw, key, "null");
        } else {
            printFlag(pw, key, duration.toMillis() + " (" + duration + ")");
        }
    }

    private static void printFlag(PrintWriter pw, String key, Object value) {
        pw.println("\t" + key + "=" + value);
    }

    /** Returns whether Safety Center is enabled. */
    public static boolean getSafetyCenterEnabled() {
        return getBoolean(PROPERTY_SAFETY_CENTER_ENABLED, SdkLevel.isAtLeastU());
    }

    /** Returns whether Safety Center notifications are enabled. */
    public static boolean getNotificationsEnabled() {
        return getBoolean(PROPERTY_NOTIFICATIONS_ENABLED, SdkLevel.isAtLeastU());
    }

    /**
     * Returns the IDs of sources that Safety Center can send notifications about, in addition to
     * those permitted by the current XML config.
     *
     * <p>If the ID of a source appears on this list then Safety Center may send notifications about
     * issues from that source, regardless of (overriding) the XML config. If the ID of a source is
     * absent from this list, then Safety Center may send such notifications only if the XML config
     * allows it.
     *
     * <p>Note that the {@code areNotificationsAllowed} config attribute is only available on API U+
     * and therefore this is the only way to enable notifications for sources on Android T.
     */
    public static ArraySet<String> getNotificationsAllowedSourceIds() {
        ArraySet<String> sources = getNotificationsAllowedSourceIdsFlag();
        if (SdkLevel.isAtLeastU()) {
            // This is a hack to update the flag value via mainline update. Reasons why we can't do
            // this via:
            // remote flag update - these are generally avoided and considered risky
            // XML config - it would break GTS tests for OEMs that have a separate config copy
            // default flag value - it would also require a remote flag update
            sources.addAll(sAllowedNotificationSourcesUPlus);
        }

        return sources;
    }

    private static ArraySet<String> getNotificationsAllowedSourceIdsFlag() {
        return getCommaSeparatedStrings(PROPERTY_NOTIFICATIONS_ALLOWED_SOURCES);
    }

    /**
     * Returns the minimum delay before Safety Center can send a notification for an issue with
     * {@link SafetySourceIssue#NOTIFICATION_BEHAVIOR_DELAYED}.
     *
     * <p>The actual delay used may be longer.
     */
    public static Duration getNotificationsMinDelay() {
        return getDuration(
                PROPERTY_NOTIFICATIONS_MIN_DELAY, NOTIFICATIONS_MIN_DELAY_DEFAULT_DURATION);
    }

    /**
     * Returns the issue type IDs for which, if otherwise undefined, Safety Center should use {@link
     * SafetySourceIssue#NOTIFICATION_BEHAVIOR_IMMEDIATELY}.
     *
     * <p>If a safety source specifies the notification behavior of an issue explicitly this flag
     * has no effect, even if the issue matches one of the entries in this flag.
     *
     * <p>Entries in this set should be strings of the form "safety_source_id/issue_type_id".
     */
    public static ArraySet<String> getImmediateNotificationBehaviorIssues() {
        return getCommaSeparatedStrings(PROPERTY_NOTIFICATIONS_IMMEDIATE_BEHAVIOR_ISSUES);
    }

    /**
     * Returns the minimum interval that must elapse before Safety Center can resurface a
     * notification after it was dismissed, or {@code null} (the default) if dismissed notifications
     * cannot resurface.
     *
     * <p>Returns {@code null} if the underlying device config flag is either unset or is set to a
     * negative value.
     *
     * <p>There may be other conditions for resurfacing a notification and the actual delay may be
     * longer than this.
     */
    @Nullable
    public static Duration getNotificationResurfaceInterval() {
        long millis = getLong(PROPERTY_NOTIFICATION_RESURFACE_INTERVAL, -1);
        if (millis < 0) {
            return null;
        } else {
            return Duration.ofMillis(millis);
        }
    }

    /**
     * Returns whether we should replace the lock screen source's {@link
     * android.safetycenter.SafetySourceStatus.IconAction}.
     */
    public static boolean getReplaceLockScreenIconAction() {
        return getBoolean(PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION, true);
    }

    /**
     * Returns the time for which Safety Center will wait for a source to respond to a resolving
     * action before timing out.
     */
    static Duration getResolvingActionTimeout() {
        return getDuration(
                PROPERTY_RESOLVING_ACTION_TIMEOUT_MILLIS,
                RESOLVING_ACTION_TIMEOUT_DEFAULT_DURATION);
    }

    /**
     * Returns the time for which an app, upon receiving a Safety Center refresh broadcast, will be
     * placed on a temporary power allowlist allowing it to start a foreground service from the
     * background.
     */
    static Duration getFgsAllowlistDuration() {
        return getDuration(PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS, FGS_ALLOWLIST_DEFAULT_DURATION);
    }

    /**
     * Returns the IDs of sources that should not be tracked, for example because they are
     * mid-rollout. Broadcasts are still sent to these sources.
     */
    static ArraySet<String> getUntrackedSourceIds() {
        return getCommaSeparatedStrings(PROPERTY_UNTRACKED_SOURCES, sUntrackedSourcesDefault);
    }

    /**
     * Returns the IDs of sources that should only be refreshed when Safety Center is on screen. We
     * will refresh these sources only on page open and when the scan button is clicked.
     */
    static ArraySet<String> getBackgroundRefreshDeniedSourceIds() {
        return getCommaSeparatedStrings(
                PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES, sBackgroundRefreshDenyDefault);
    }

    /**
     * Returns the time for which a Safety Center refresh is allowed to wait for a source to respond
     * to a refresh request before timing out and marking the refresh as completed, based on the
     * reason for the refresh.
     */
    static Duration getRefreshSourcesTimeout(@RefreshReason int refreshReason) {
        String refreshSourcesTimeouts = getRefreshSourcesTimeoutsMillis();
        Long timeout = getLongValueFromStringMapping(refreshSourcesTimeouts, refreshReason);
        if (timeout != null) {
            return Duration.ofMillis(timeout);
        }
        return REFRESH_SOURCES_TIMEOUT_DEFAULT_DURATION;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is a {@link
     * RefreshReason} and the right value is the refresh timeout applied for each source in case of
     * a refresh.
     */
    private static String getRefreshSourcesTimeoutsMillis() {
        return getString(PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, REFRESH_SOURCES_TIMEOUT_DEFAULT);
    }

    /**
     * Returns the number of times an issue of the given {@link SafetySourceData.SeverityLevel}
     * should be resurfaced.
     */
    public static long getResurfaceIssueMaxCount(
            @SafetySourceData.SeverityLevel int severityLevel) {
        String maxCountsConfigString = getResurfaceIssueMaxCounts();
        Long maxCount = getLongValueFromStringMapping(maxCountsConfigString, severityLevel);
        if (maxCount != null) {
            return maxCount;
        }
        return RESURFACE_ISSUE_MAX_COUNT_DEFAULT_COUNT;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is an issue
     * {@link SafetySourceData.SeverityLevel} and the right value is the number of times an issue of
     * this {@link SafetySourceData.SeverityLevel} should be resurfaced.
     */
    private static String getResurfaceIssueMaxCounts() {
        return getString(PROPERTY_RESURFACE_ISSUE_MAX_COUNTS, RESURFACE_ISSUE_MAX_COUNT_DEFAULT);
    }

    /**
     * Returns the time after which a dismissed issue of the given {@link
     * SafetySourceData.SeverityLevel} will resurface if it has not reached the maximum count for
     * which a dismissed issue of the given {@link SafetySourceData.SeverityLevel} should be
     * resurfaced.
     */
    public static Duration getResurfaceIssueDelay(
            @SafetySourceData.SeverityLevel int severityLevel) {
        String delaysConfigString = getResurfaceIssueDelaysMillis();
        Long delayMillis = getLongValueFromStringMapping(delaysConfigString, severityLevel);
        if (delayMillis != null) {
            return Duration.ofMillis(delayMillis);
        }
        return RESURFACE_ISSUE_DELAYS_DEFAULT_DURATION;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is an issue
     * {@link SafetySourceData.SeverityLevel} and the right value is the time after which a
     * dismissed issue of this safety source severity level will resurface if it has not reached the
     * maximum count for which a dismissed issue of this {@link SafetySourceData.SeverityLevel}
     * should be resurfaced.
     */
    private static String getResurfaceIssueDelaysMillis() {
        return getString(PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS, RESURFACE_ISSUE_DELAYS_DEFAULT);
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs of SourceId:ActionId. The action IDs
     * listed by this flag should have their {@code PendingIntent}s overridden with the source's
     * default intent drawn from Safety Center's config file, if available.
     */
    private static String getActionsToOverrideWithDefaultIntent() {
        return getString(
                PROPERTY_ACTIONS_TO_OVERRIDE_WITH_DEFAULT_INTENT,
                sActionsToOverrideWithDefaultIntentDefault);
    }

    /** Returns a duration after which a temporarily hidden issue will resurface. */
    public static Duration getTemporarilyHiddenIssueResurfaceDelay() {
        return getDuration(
                PROPERTY_TEMP_HIDDEN_ISSUE_RESURFACE_DELAY_MILLIS,
                TEMP_HIDDEN_ISSUE_RESURFACE_DELAY_DEFAULT_DURATION);
    }

    /**
     * Returns whether a safety source is allowed to send issues for the given {@link
     * SafetySourceIssue.IssueCategory}.
     */
    public static boolean isIssueCategoryAllowedForSource(
            @SafetySourceIssue.IssueCategory int issueCategory, String safetySourceId) {
        List<String> allowlist =
                getStringListValueFromStringMapping(
                        getIssueCategoryAllowlists(), Integer.toString(issueCategory));
        return allowlist.isEmpty() || allowlist.contains(safetySourceId);
    }

    /** Returns a set of package certificates allowlisted for the given package name. */
    public static ArraySet<String> getAdditionalAllowedPackageCerts(String packageName) {
        String property = getAdditionalAllowedPackageCertsString();
        String allowlistedCertString = getStringValueFromStringMapping(property, packageName);
        if (allowlistedCertString == null) {
            return new ArraySet<>();
        }
        return new ArraySet<>(allowlistedCertString.split("\\|"));
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is an issue
     * {@link SafetySourceIssue.IssueCategory} and the right value is a vertical-bar-delimited list
     * of IDs of safety sources that are allowed to send issues with this category.
     */
    private static String getIssueCategoryAllowlists() {
        return getString(PROPERTY_ISSUE_CATEGORY_ALLOWLISTS, sIssueCategoryAllowlistDefault);
    }

    private static String getAdditionalAllowedPackageCertsString() {
        return getString(PROPERTY_ADDITIONAL_ALLOW_PACKAGE_CERTS, "");
    }

    /** Returns whether we allow statsd logging. */
    public static boolean getAllowStatsdLogging() {
        return getBoolean(PROPERTY_ALLOW_STATSD_LOGGING, true);
    }

    /**
     * Returns a list of action IDs that should be overridden with the source's default intent drawn
     * from the config for a given source.
     */
    public static List<String> getActionsToOverrideWithDefaultIntentForSource(
            String safetySourceId) {
        return getStringListValueFromStringMapping(
                getActionsToOverrideWithDefaultIntent(), safetySourceId);
    }

    /**
     * Returns whether to show subpages in the Safety Center UI for Android-U instead of the
     * expand-and-collapse list implementation.
     */
    static boolean getShowSubpages() {
        return SdkLevel.isAtLeastU() && getBoolean(PROPERTY_SHOW_SUBPAGES, true);
    }

    /**
     * Returns an array of safety source Ids that will be refreshed on page open, even if
     * refreshOnPageOpenAllowed is false (the default) in the XML config.
     */
    static ArraySet<String> getOverrideRefreshOnPageOpenSourceIds() {
        return getCommaSeparatedStrings(
                PROPERTY_OVERRIDE_REFRESH_ON_PAGE_OPEN_SOURCES, sRefreshOnPageOpenSourcesDefault);
    }

    private static Duration getDuration(String property, Duration defaultValue) {
        return Duration.ofMillis(getLong(property, defaultValue.toMillis()));
    }

    private static boolean getBoolean(String property, boolean defaultValue) {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY, property, defaultValue);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private static long getLong(String property, long defaultValue) {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getLong(DeviceConfig.NAMESPACE_PRIVACY, property, defaultValue);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private static ArraySet<String> getCommaSeparatedStrings(String property) {
        return getCommaSeparatedStrings(property, "");
    }

    private static ArraySet<String> getCommaSeparatedStrings(String property, String defaultValue) {
        return new ArraySet<>(getString(property, defaultValue).split(","));
    }

    private static String getString(String property, String defaultValue) {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getString(DeviceConfig.NAMESPACE_PRIVACY, property, defaultValue);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Gets a long value for the provided integer key in a comma separated list of colon separated
     * pairs of integers and longs.
     */
    @Nullable
    private static Long getLongValueFromStringMapping(String mapping, int key) {
        String valueString = getStringValueFromStringMapping(mapping, key);
        if (valueString == null) {
            return null;
        }
        try {
            return Long.parseLong(valueString);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Badly formatted string mapping: " + mapping, e);
            return null;
        }
    }

    /**
     * Gets a value for the provided integer key in a comma separated list of colon separated pairs
     * of integers and strings.
     */
    @Nullable
    private static String getStringValueFromStringMapping(String mapping, int key) {
        return getStringValueFromStringMapping(mapping, Integer.toString(key));
    }

    /**
     * Gets a value for the provided key in a comma separated list of colon separated key-value
     * string pairs.
     */
    @Nullable
    private static String getStringValueFromStringMapping(String mapping, String key) {
        if (mapping.isEmpty()) {
            return null;
        }
        String[] pairsList = mapping.split(",");
        for (int i = 0; i < pairsList.length; i++) {
            String[] pair = pairsList[i].split(":", -1 /* allow trailing empty strings */);
            if (pair.length != 2) {
                Log.w(TAG, "Badly formatted string mapping: " + mapping);
                continue;
            }
            if (pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }

    private static List<String> getStringListValueFromStringMapping(String mapping, String key) {
        String value = getStringValueFromStringMapping(mapping, key);
        if (value == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(value.split("\\|"));
    }

    private SafetyCenterFlags() {}
}
