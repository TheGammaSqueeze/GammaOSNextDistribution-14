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

package com.android.server.wifi;

import android.net.MacAddress;
import android.net.wifi.WifiConfiguration;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Utilities for storing PMK cache. */
public class PmkCacheManager {
    private static final String TAG = "PmkCacheManager";

    @VisibleForTesting
    static final String PMK_CACHE_EXPIRATION_ALARM_TAG = "PMK_CACHE_EXPIRATION_TIMER";

    private final Clock mClock;
    private final Handler mEventHandler;

    private boolean mVerboseLoggingEnabled = false;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private SparseArray<List<PmkCacheStoreData>> mPmkCacheEntries = new SparseArray<>();

    public PmkCacheManager(Clock clock, Handler eventHandler) {
        mClock = clock;
        mEventHandler = eventHandler;
    }

    /**
     * Add a PMK cache entry to the store.
     *
     * @param macAddress the interface MAC address to connect to the network.
     * @param networkId the network ID of the WifiConfiguration associates with the network.
     * @param bssid BSSID of the access point to which the station is associated
     * @param expirationTimeInSec the expiration time of the PMK cache since boot.
     * @param serializedEntry the opaque data of the PMK cache.
     * @return true when PMK cache is added; otherwise, false.
     */
    public boolean add(MacAddress macAddress, int networkId, MacAddress bssid,
            long expirationTimeInSec, ArrayList<Byte> serializedEntry) {
        synchronized (mLock) {
            if (WifiConfiguration.INVALID_NETWORK_ID == networkId) return false;
            if (macAddress == null) {
                Log.w(TAG, "Omit PMK cache due to no valid MAC address");
                return false;
            }
            if (null == serializedEntry) {
                Log.w(TAG, "Omit PMK cache due to null entry.");
                return false;
            }
            final long elapseTimeInSecond = mClock.getElapsedSinceBootMillis() / 1000;
            if (elapseTimeInSecond >= expirationTimeInSec) {
                Log.w(TAG, "Omit expired PMK cache.");
                return false;
            }

            PmkCacheStoreData newStoreData =
                    new PmkCacheStoreData(macAddress, bssid, serializedEntry, expirationTimeInSec);
            List<PmkCacheStoreData> pmkDataList = mPmkCacheEntries.get(networkId);
            if (pmkDataList == null) {
                pmkDataList = new ArrayList<>();
                mPmkCacheEntries.put(networkId, pmkDataList);
            } else {
                PmkCacheStoreData existStoreData = null;
                if (bssid != null) {
                    // Remove the stored PMK cache if the PMK cache is changed for an existing
                    // BSSID.
                    for (PmkCacheStoreData storeData : pmkDataList) {
                        if (Objects.equals(storeData.bssid, bssid)) {
                            existStoreData = storeData;
                            break;
                        }
                    }
                    if (null != existStoreData) {
                        if (Objects.equals(existStoreData, newStoreData)) {
                            if (mVerboseLoggingEnabled) {
                                Log.d(TAG, "PMK entry exists for the BSSID, skip it.");
                            }
                            return true;
                        }
                        pmkDataList.remove(existStoreData);
                    }
                } else {
                    for (PmkCacheStoreData storeData : pmkDataList) {
                        if (Objects.equals(storeData, newStoreData)) {
                            existStoreData = storeData;
                            break;
                        }
                    }
                    if (null != existStoreData) {
                        if (mVerboseLoggingEnabled) {
                            Log.d(TAG, "PMK entry exists, skip it.");
                        }
                        return true;
                    }
                }
            }

            pmkDataList.add(newStoreData);
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Network " + networkId + " PmkCache Count: " + pmkDataList.size());
            }
            updatePmkCacheExpiration();
            return true;
        }
    }

    /**
     * Remove PMK caches associated with the network ID.
     *
     * @param networkId the network ID of PMK caches to be removed.
     * @return true when PMK caches are removed; otherwise, false.
     */
    public boolean remove(int networkId) {
        synchronized (mLock) {
            if (WifiConfiguration.INVALID_NETWORK_ID == networkId) return false;
            if (!mPmkCacheEntries.contains(networkId)) return false;

            mPmkCacheEntries.remove(networkId);
            updatePmkCacheExpiration();
            return true;
        }
    }

    /**
     * Remove PMK caches associated with the network ID when the interface
     * MAC address is changed.
     *
     * @param networkId the network ID of PMK caches to be removed.
     * @param curMacAddress current interface MAC address.
     * @return true when PMK caches are removed; otherwise, false.
     */

    public boolean remove(int networkId, MacAddress curMacAddress) {
        synchronized (mLock) {
            if (WifiConfiguration.INVALID_NETWORK_ID == networkId) return false;
            List<PmkCacheStoreData> pmkDataList = mPmkCacheEntries.get(networkId);
            if (null == pmkDataList) return false;

            pmkDataList.removeIf(pmkData -> !Objects.equals(curMacAddress, pmkData.macAddress));

            if (pmkDataList.size() == 0) {
                remove(networkId);
            }
            return true;
        }
    }

    /**
     * Get PMK caches associated with the network ID.
     *
     * @param networkId the network ID to be queried.
     * @return A list of PMK caches associated with the network ID.
     *         If none of PMK cache is associated with the network ID, return null.
     */
    public List<ArrayList<Byte>> get(int networkId) {
        synchronized (mLock) {
            List<PmkCacheStoreData> pmkDataList = mPmkCacheEntries.get(networkId);
            if (WifiConfiguration.INVALID_NETWORK_ID == networkId) return null;
            if (null == pmkDataList) return null;

            final long elapseTimeInSecond = mClock.getElapsedSinceBootMillis() / 1000;
            List<ArrayList<Byte>> dataList = new ArrayList<>();
            for (PmkCacheStoreData pmkData : pmkDataList) {
                if (pmkData.isValid(elapseTimeInSecond)) {
                    dataList.add(pmkData.data);
                }
            }
            return dataList;
        }
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    @VisibleForTesting
    void updatePmkCacheExpiration() {
        synchronized (mLock) {
            mEventHandler.removeCallbacksAndMessages(PMK_CACHE_EXPIRATION_ALARM_TAG);

            long elapseTimeInSecond = mClock.getElapsedSinceBootMillis() / 1000;
            long nextUpdateTimeInSecond = Long.MAX_VALUE;
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Update PMK cache expiration at " + elapseTimeInSecond);
            }

            List<Integer> emptyStoreDataList = new ArrayList<>();
            for (int i = 0; i < mPmkCacheEntries.size(); i++) {
                int networkId = mPmkCacheEntries.keyAt(i);
                List<PmkCacheStoreData> list = mPmkCacheEntries.get(networkId);
                list.removeIf(pmkData -> !pmkData.isValid(elapseTimeInSecond));
                if (list.size() == 0) {
                    emptyStoreDataList.add(networkId);
                    continue;
                }
                for (PmkCacheStoreData pmkData : list) {
                    if (nextUpdateTimeInSecond > pmkData.expirationTimeInSec) {
                        nextUpdateTimeInSecond = pmkData.expirationTimeInSec;
                    }
                }
            }
            emptyStoreDataList.forEach(networkId -> mPmkCacheEntries.remove(networkId));

            // No need to arrange next update since there is no valid PMK in the cache.
            if (nextUpdateTimeInSecond == Long.MAX_VALUE) {
                return;
            }

            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "PMK cache next expiration time: " + nextUpdateTimeInSecond);
            }
            long delayedTimeInMs = (nextUpdateTimeInSecond - elapseTimeInSecond) * 1000;
            mEventHandler.postDelayed(
                    () -> {
                        updatePmkCacheExpiration();
                    },
                    PMK_CACHE_EXPIRATION_ALARM_TAG,
                    (delayedTimeInMs > 0) ? delayedTimeInMs : 0);
        }
    }

    private static class PmkCacheStoreData {

        public MacAddress macAddress;
        public MacAddress bssid;
        public ArrayList<Byte> data;
        public long expirationTimeInSec;

        PmkCacheStoreData(MacAddress macAddr, MacAddress bssAddr, ArrayList<Byte> serializedData,
                long timeInSec) {
            macAddress = macAddr;
            bssid = bssAddr;
            data = serializedData;
            expirationTimeInSec = timeInSec;
        }

        /**
         * Validate this PMK cache against the timestamp.
         *
         * @param currentTimeInSec the timestamp to be checked.
         * @return true if this PMK cache is valid against the timestamp; otherwise, false.
         */
        public boolean isValid(long currentTimeInSec) {
            return expirationTimeInSec > 0 && expirationTimeInSec > currentTimeInSec;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PmkCacheStoreData)) return false;
            PmkCacheStoreData storeData = (PmkCacheStoreData) o;
            return expirationTimeInSec == storeData.expirationTimeInSec
                    && Objects.equals(macAddress, storeData.macAddress)
                    && Objects.equals(data, storeData.data)
                    && Objects.equals(bssid, storeData.bssid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(macAddress, data, expirationTimeInSec, bssid);
        }
    }
}
