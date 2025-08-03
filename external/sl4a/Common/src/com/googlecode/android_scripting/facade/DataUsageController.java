/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.googlecode.android_scripting.facade;

import static android.app.usage.NetworkStats.Bucket.METERED_YES;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkTemplate.MATCH_MOBILE;
import static android.net.NetworkTemplate.MATCH_WIFI;
import static android.telephony.TelephonyManager.SIM_STATE_READY;
import static android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;
import static android.text.format.DateUtils.FORMAT_SHOW_TIME;
import static android.text.format.DateUtils.FORMAT_SHOW_YEAR;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * DataUsageController.
 */
public class DataUsageController {

    private static final String TAG = "DataUsageController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final StringBuilder PERIOD_BUILDER = new StringBuilder(50);
    private static final java.util.Formatter PERIOD_FORMATTER = new java.util.Formatter(
            PERIOD_BUILDER, Locale.getDefault());

    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;
    private final NetworkPolicyManager mPolicyManager;

    private Callback mCallback;
    private NetworkNameProvider mNetworkController;

    public DataUsageController(Context context) {
        mContext = context;
        mTelephonyManager = TelephonyManager.from(context);
        mConnectivityManager = ConnectivityManager.from(context);
        mPolicyManager = NetworkPolicyManager.from(mContext);
    }

    public void setNetworkController(NetworkNameProvider networkController) {
        mNetworkController = networkController;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private DataUsageInfo warn(String msg) {
        Log.w(TAG, "Failed to get data usage, " + msg);
        return null;
    }

    /**
     * Get mobile data usage info.
     * @return DataUsageInfo: The Mobile data usage information.
     */
    public DataUsageInfo getMobileDataUsageInfoForSubscriber(String subscriberId) {
        if (subscriberId == null) {
            return warn("no subscriber id");
        }
        NetworkTemplate template = new NetworkTemplate.Builder(MATCH_MOBILE)
                .setMeteredness(METERED_YES).setSubscriberIds(Set.of(subscriberId)).build();
        template = NetworkTemplate.normalize(template, mTelephonyManager.getMergedSubscriberIds());

        return getDataUsageInfo(template);
    }

    /**
     * Get mobile data usage info.
     * @return DataUsageInfo: The Mobile data usage information.
     */
    public DataUsageInfo getMobileDataUsageInfoForUid(Integer uId, String subscriberId) {
        if (subscriberId == null) {
            return warn("no subscriber id");
        }
        NetworkTemplate template = new NetworkTemplate.Builder(MATCH_MOBILE)
                .setMeteredness(METERED_YES).setSubscriberIds(Set.of(subscriberId)).build();
        template = NetworkTemplate.normalize(template, mTelephonyManager.getMergedSubscriberIds());

        return getDataUsageInfo(template, uId);
    }

    /**
     * Get wifi data usage info.
     * @return DataUsageInfo: The Wifi data usage information.
     */
    public DataUsageInfo getWifiDataUsageInfo() {
        NetworkTemplate template = new NetworkTemplate.Builder(MATCH_WIFI).build();
        return getDataUsageInfo(template);
    }

    /**
     * Get data usage info for a given template.
     * @param template A given template.
     * @return DataUsageInfo: The data usage information.
     */
    public DataUsageInfo getDataUsageInfo(NetworkTemplate template) {
        return getDataUsageInfo(template, UID_ALL);
    }

    private static long getTotalBytesForUid(NetworkStats stats, int uid) {
        if (!stats.hasNextBucket()) {
            return 0;
        }
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        long rx = 0;
        long tx = 0;
        while (stats.hasNextBucket() && stats.getNextBucket(bucket)) {
            if (uid != bucket.getUid())  continue;
            rx += bucket.getRxBytes();
            tx += bucket.getTxBytes();
        }
        return rx + tx;
    }

    /**
     * Get data usage info for a given template.
     * @param template A given template.
     * @param uid UID of app, uid = -1 {@link NetworkStats#UID_ALL} value indicates summarised
     *        data usage without UID details.
     * @return DataUsageInfo: The data usage information.
     */
    public DataUsageInfo getDataUsageInfo(NetworkTemplate template, int uid) {
        final NetworkPolicy policy = findNetworkPolicy(template);
        final long totalBytes;
        final long now = System.currentTimeMillis();
        final long start, end;
        final NetworkStatsManager mNetworkStatsManager =
                    mContext.getSystemService(NetworkStatsManager.class);
        if (policy != null) {
            final Pair<ZonedDateTime, ZonedDateTime> cycle = NetworkPolicyManager
                    .cycleIterator(policy).next();
            start = cycle.first.toInstant().toEpochMilli();
            end = cycle.second.toInstant().toEpochMilli();
        } else {
            // period = last 4 wks
            end = now;
            start = now - DateUtils.WEEK_IN_MILLIS * 4;
        }

        if (uid == UID_ALL) {
            final NetworkStats.Bucket ret = mNetworkStatsManager
                    .querySummaryForDevice(template, start, end);
            totalBytes = ret.getRxBytes() + ret.getTxBytes();
        } else {
            final NetworkStats ret = mNetworkStatsManager.querySummary(template, start, end);
            totalBytes = getTotalBytesForUid(ret, uid);
        }

        final DataUsageInfo usage = new DataUsageInfo();
        usage.subscriberId = template.getSubscriberId();
        usage.startEpochMilli = start;
        usage.endEpochMilli = end;
        usage.usageLevel = totalBytes;
        usage.period = formatDateRange(start, end);
        usage.cycleStart = DateUtils.formatDateTime(mContext, start,
                FORMAT_SHOW_DATE + FORMAT_SHOW_YEAR + FORMAT_SHOW_TIME);
        usage.cycleEnd = DateUtils.formatDateTime(mContext, end,
                FORMAT_SHOW_DATE + FORMAT_SHOW_YEAR + FORMAT_SHOW_TIME);

        if (policy != null) {
            usage.limitLevel = policy.limitBytes > 0 ? policy.limitBytes : -1;
            usage.warningLevel = policy.warningBytes > 0 ? policy.warningBytes : -1;
        }
        if (uid != UID_ALL) {
            usage.uId = uid;
        }
        return usage;
    }

    private NetworkPolicy findNetworkPolicy(NetworkTemplate template) {
        if (mPolicyManager == null || template == null) return null;
        final NetworkPolicy[] policies = mPolicyManager.getNetworkPolicies();
        if (policies == null) return null;
        final int mLength = policies.length;
        for (int i = 0; i < mLength; i++) {
            final NetworkPolicy policy = policies[i];
            if (policy != null && template.equals(policy.template)) {
                return policy;
            }
        }
        return null;
    }

    private static String historyEntryToString(NetworkStatsHistory.Entry entry) {
        return entry == null ? null : new StringBuilder("Entry[")
                .append("bucketDuration=").append(entry.bucketDuration)
                .append(",bucketStart=").append(entry.bucketStart)
                .append(",activeTime=").append(entry.activeTime)
                .append(",rxBytes=").append(entry.rxBytes)
                .append(",rxPackets=").append(entry.rxPackets)
                .append(",txBytes=").append(entry.txBytes)
                .append(",txPackets=").append(entry.txPackets)
                .append(",operations=").append(entry.operations)
                .append(']').toString();
    }

    /**
     * Enable or disable mobile data.
     * @param enabled Enable data: True: Disable data: False.
     */
    public void setMobileDataEnabled(boolean enabled) {
        Log.d(TAG, "setMobileDataEnabled: enabled=" + enabled);
        mTelephonyManager.setDataEnabled(enabled);
        if (mCallback != null) {
            mCallback.onMobileDataEnabled(enabled);
        }
    }

    /**
     * Check if mobile data is supported.
     * @return True supported, False: Not supported.
     */
    public boolean isMobileDataSupported() {
        // require both supported network and ready SIM
        return mConnectivityManager.isNetworkSupported(TYPE_MOBILE)
                && mTelephonyManager.getSimState() == SIM_STATE_READY;
    }

    /**
     * Check if mobile data is enabled.
     * @return True: enabled, False: Not enabled.
     */
    public boolean isMobileDataEnabled() {
        return mTelephonyManager.getDataEnabled();
    }

    private String formatDateRange(long start, long end) {
        final int flags = FORMAT_SHOW_DATE | FORMAT_ABBREV_MONTH;
        synchronized (PERIOD_BUILDER) {
            PERIOD_BUILDER.setLength(0);
            return DateUtils.formatDateRange(mContext, PERIOD_FORMATTER, start, end, flags, null)
                    .toString();
        }
    }

    public interface NetworkNameProvider {
        String getMobileDataNetworkName();
    }

    public static class DataUsageInfo {
        public String subscriberId;
        public String period;
        public Integer uId;
        public long startEpochMilli;
        public long endEpochMilli;
        public long limitLevel;
        public long warningLevel;
        public long usageLevel;
        public String cycleStart;
        public String cycleEnd;
    }

    public interface Callback {
        void onMobileDataEnabled(boolean enabled);
    }
}
