/*
 * Copyright 2014 The Android Open Source Project
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

import android.net.wifi.WifiUsabilityStatsEntry.LinkState;
import android.util.SparseArray;

import java.util.Arrays;

/**
 * A class representing link layer statistics collected over a Wifi Interface.
 */

/**
 * {@hide}
 */
public class WifiLinkLayerStats {
    public static final String V1_0 = "V1_0";
    public static final String V1_3 = "V1_3";
    public static final String V1_5 = "V1_5";

    /** The version of hal StaLinkLayerStats **/
    public String version;

    /**
     * Link specific statistics.
     */
    public static class LinkSpecificStats {

        /** Link identifier of the link */
        public int link_id;

        /** Link state as {@link LinkState} */
        public @LinkState int state;

        /** Identifier of the radio on which link is currently operating */
        public int radio_id;

        /** Frequency of the link in MHz */
        public int frequencyMhz;

        /** Number of beacons received from our own AP */
        public int beacon_rx;

        /** RSSI of management frames */
        public int rssi_mgmt;

        /* Packet counters and contention time stats */

        /** WME Best Effort Access Category received mpdu */
        public long rxmpdu_be;
        /** WME Best Effort Access Category transmitted mpdu */
        public long txmpdu_be;
        /** WME Best Effort Access Category lost mpdu */
        public long lostmpdu_be;
        /** WME Best Effort Access Category number of transmission retries */
        public long retries_be;
        /** WME Best Effort Access Category data packet min contention time in microseconds */
        public long contentionTimeMinBeInUsec;
        /** WME Best Effort Access Category data packet max contention time in microseconds */
        public long contentionTimeMaxBeInUsec;
        /** WME Best Effort Access Category data packet average contention time in microseconds */
        public long contentionTimeAvgBeInUsec;
        /**
         * WME Best Effort Access Category number of data packets used for deriving the min, the
         * max,
         * and the average contention time
         */
        public long contentionNumSamplesBe;

        /** WME Background Access Category received mpdu */
        public long rxmpdu_bk;
        /** WME Background Access Category transmitted mpdu */
        public long txmpdu_bk;
        /** WME Background Access Category lost mpdu */
        public long lostmpdu_bk;
        /** WME Background Access Category number of transmission retries */
        public long retries_bk;
        /** WME Background Access Category data packet min contention time in microseconds */
        public long contentionTimeMinBkInUsec;
        /** WME Background Access Category data packet max contention time in microseconds */
        public long contentionTimeMaxBkInUsec;
        /** WME Background Access Category data packet average contention time in microseconds */
        public long contentionTimeAvgBkInUsec;
        /**
         * WME Background Access Category number of data packets used for deriving the min, the max,
         * and the average contention time
         */
        public long contentionNumSamplesBk;

        /** WME Video Access Category received mpdu */
        public long rxmpdu_vi;
        /** WME Video Access Category transmitted mpdu */
        public long txmpdu_vi;
        /** WME Video Access Category lost mpdu */
        public long lostmpdu_vi;
        /** WME Video Access Category number of transmission retries */
        public long retries_vi;
        /** WME Video Access Category data packet min contention time in microseconds */
        public long contentionTimeMinViInUsec;
        /** WME Video Access Category data packet max contention time in microseconds */
        public long contentionTimeMaxViInUsec;
        /** WME Video Access Category data packet average contention time in microseconds */
        public long contentionTimeAvgViInUsec;
        /**
         * WME Video Access Category number of data packets used for deriving the min, the max, and
         * the average contention time
         */
        public long contentionNumSamplesVi;

        /** WME Voice Access Category received mpdu */
        public long rxmpdu_vo;
        /** WME Voice Access Category transmitted mpdu */
        public long txmpdu_vo;
        /** WME Voice Access Category lost mpdu */
        public long lostmpdu_vo;
        /** WME Voice Access Category number of transmission retries */
        public long retries_vo;
        /** WME Voice Access Category data packet min contention time in microseconds */
        public long contentionTimeMinVoInUsec;
        /** WME Voice Access Category data packet max contention time in microseconds */
        public long contentionTimeMaxVoInUsec;
        /** WME Voice Access Category data packet average contention time in microseconds */
        public long contentionTimeAvgVoInUsec;
        /**
         * WME Voice Access Category number of data packets used for deriving the min, the max, and
         * the average contention time
         */
        public long contentionNumSamplesVo;

        /**
         * Duty cycle of the link.
         * if this link is being served using time slicing on a radio with one or more ifaces
         * (i.e MCC), then the duty cycle assigned to this iface in %.
         * If not using time slicing (i.e SCC or DBS), set to 100.
         */
        public short timeSliceDutyCycleInPercent = -1;

        /**
         * Peer statistics.
         */
        public PeerInfo[] peerInfo;

    }

    public LinkSpecificStats[] links;

    /**
     * The stats below which is already captured in WifiLinkLayerStats#LinkSpecificStats will be
     * having an aggregated value. The aggregation logic is defined at
     * wifiNative#setAggregatedLinkLayerStats().
     */

    /** Number of beacons received from our own AP */
    public int beacon_rx;

    /** RSSI of management frames */
    public int rssi_mgmt;

    /* Packet counters and contention time stats */

    /** WME Best Effort Access Category received mpdu */
    public long rxmpdu_be;
    /** WME Best Effort Access Category transmitted mpdu */
    public long txmpdu_be;
    /** WME Best Effort Access Category lost mpdu */
    public long lostmpdu_be;
    /** WME Best Effort Access Category number of transmission retries */
    public long retries_be;
    /** WME Best Effort Access Category data packet min contention time in microseconds */
    public long contentionTimeMinBeInUsec;
    /** WME Best Effort Access Category data packet max contention time in microseconds */
    public long contentionTimeMaxBeInUsec;
    /** WME Best Effort Access Category data packet average contention time in microseconds */
    public long contentionTimeAvgBeInUsec;
    /**
     * WME Best Effort Access Category number of data packets used for deriving the min, the max,
     * and the average contention time
     */
    public long contentionNumSamplesBe;

    /** WME Background Access Category received mpdu */
    public long rxmpdu_bk;
    /** WME Background Access Category transmitted mpdu */
    public long txmpdu_bk;
    /** WME Background Access Category lost mpdu */
    public long lostmpdu_bk;
    /** WME Background Access Category number of transmission retries */
    public long retries_bk;
    /** WME Background Access Category data packet min contention time in microseconds */
    public long contentionTimeMinBkInUsec;
    /** WME Background Access Category data packet max contention time in microseconds */
    public long contentionTimeMaxBkInUsec;
    /** WME Background Access Category data packet average contention time in microseconds */
    public long contentionTimeAvgBkInUsec;
    /**
     * WME Background Access Category number of data packets used for deriving the min, the max,
     * and the average contention time
     */
    public long contentionNumSamplesBk;

    /** WME Video Access Category received mpdu */
    public long rxmpdu_vi;
    /** WME Video Access Category transmitted mpdu */
    public long txmpdu_vi;
    /** WME Video Access Category lost mpdu */
    public long lostmpdu_vi;
    /** WME Video Access Category number of transmission retries */
    public long retries_vi;
    /** WME Video Access Category data packet min contention time in microseconds */
    public long contentionTimeMinViInUsec;
    /** WME Video Access Category data packet max contention time in microseconds */
    public long contentionTimeMaxViInUsec;
    /** WME Video Access Category data packet average contention time in microseconds */
    public long contentionTimeAvgViInUsec;
    /**
     * WME Video Access Category number of data packets used for deriving the min, the max, and
     * the average contention time
     */
    public long contentionNumSamplesVi;

    /** WME Voice Access Category received mpdu */
    public long rxmpdu_vo;
    /** WME Voice Access Category transmitted mpdu */
    public long txmpdu_vo;
    /** WME Voice Access Category lost mpdu */
    public long lostmpdu_vo;
    /** WME Voice Access Category number of transmission retries */
    public long retries_vo;
    /** WME Voice Access Category data packet min contention time in microseconds */
    public long contentionTimeMinVoInUsec;
    /** WME Voice Access Category data packet max contention time in microseconds */
    public long contentionTimeMaxVoInUsec;
    /** WME Voice Access Category data packet average contention time in microseconds */
    public long contentionTimeAvgVoInUsec;
    /**
     * WME Voice Access Category number of data packets used for deriving the min, the max, and
     * the average contention time
     */
    public long contentionNumSamplesVo;

    /**
     * Cumulative milliseconds when radio is awake
     */
    public int on_time;
    /**
     * Cumulative milliseconds of active transmission
     */
    public int tx_time;
    /**
     * Cumulative milliseconds per radio transmit power level of active transmission
     */
    public int[] tx_time_per_level;
    /**
     * Cumulative milliseconds of active receive
     */
    public int rx_time;
    /**
     * Cumulative milliseconds when radio is awake due to scan
     */
    public int on_time_scan;
    /**
     * Cumulative milliseconds when radio is awake due to nan scan
     */
    public int on_time_nan_scan;
    /**
     * Cumulative milliseconds when radio is awake due to background scan
     */
    public int on_time_background_scan;
    /**
     * Cumulative milliseconds when radio is awake due to roam scan
     */
    public int on_time_roam_scan;
    /**
     * Cumulative milliseconds when radio is awake due to pno scan
     */
    public int on_time_pno_scan;
    /**
     * Cumulative milliseconds when radio is awake due to hotspot 2.0 scan amd GAS exchange
     */
    public int on_time_hs20_scan;

    /**
     * channel stats
     */
    public static class ChannelStats {
        /**
         * Channel frequency in MHz;
         */
        public int frequency;
        /**
         * Cumulative milliseconds radio is awake on this channel
         */
        public int radioOnTimeMs;
        /**
         * Cumulative milliseconds CCA is held busy on this channel
         */
        public int ccaBusyTimeMs;
    }

    /**
     * Channel stats list
     */
    public final SparseArray<ChannelStats> channelStatsMap = new SparseArray<>();

    /**
     * numRadios - Number of radios used for coalescing above radio stats.
     */
    public int numRadios;

    /**
     * TimeStamp - absolute milliseconds from boot when these stats were sampled.
     */
    public long timeStampInMs;

    /**
     * Duty cycle of the iface.
     * if this iface is being served using time slicing on a radio with one or more ifaces
     * (i.e MCC), then the duty cycle assigned to this iface in %.
     * If not using time slicing (i.e SCC or DBS), set to 100.
     */
    public short timeSliceDutyCycleInPercent = -1;

    /**
     * Per rate information and statistics.
     */
    public static class RateStat {
        /**
         * Preamble information. 0: OFDM, 1:CCK, 2:HT 3:VHT 4:HE 5..7 reserved.
         */
        public int preamble;
        /**
         * Number of spatial streams. 0:1x1, 1:2x2, 3:3x3, 4:4x4.
         */
        public int nss;
        /**
         * Bandwidth information. 0:20MHz, 1:40Mhz, 2:80Mhz, 3:160Mhz.
         */
        public int bw;
        /**
         * MCS index. OFDM/CCK rate code would be as per IEEE std in the units of 0.5Mbps.
         * HT/VHT/HE: it would be MCS index.
         */
        public int rateMcsIdx;
        /**
         * Bitrate in units of 100 Kbps.
         */
        public int bitRateInKbps;
        /**
         * Number of successfully transmitted data packets (ACK received).
         */
        public int txMpdu;
        /**
         * Number of received data packets.
         */
        public int rxMpdu;
        /**
         * Number of data packet losses (no ACK).
         */
        public int mpduLost;
        /**
         * Number of data packet retries.
         */
        public int retries;
    }

    /**
     * Per peer statistics.
     */
    public static class PeerInfo {
        /**
         * Station count.
         */
        public short staCount;
        /**
         * Channel utilization.
         */
        public short chanUtil;
        /**
         * Per rate statistics.
         */
        public RateStat[] rateStats;
    }

    /**
     * Peer statistics.
     */
    public PeerInfo[] peerInfo;

    /**
     * Radio stats
     */
    public static class RadioStat {
        /**
         * Radio identifier
         */
        public int radio_id;
        /**
         * Cumulative milliseconds when radio is awake from the last radio chip reset
         */
        public int on_time;
        /**
         * Cumulative milliseconds of active transmission from the last radio chip reset
         */
        public int tx_time;
        /**
         * Cumulative milliseconds of active receive from the last radio chip reset
         */
        public int rx_time;
        /**
         * Cumulative milliseconds when radio is awake due to scan from the last radio chip reset
         */
        public int on_time_scan;
        /**
         * Cumulative milliseconds when radio is awake due to nan scan from the last radio chip
         * reset
         */
        public int on_time_nan_scan;
        /**
         * Cumulative milliseconds when radio is awake due to background scan from the last radio
         * chip reset
         */
        public int on_time_background_scan;
        /**
         * Cumulative milliseconds when radio is awake due to roam scan from the last radio chip
         * reset
         */
        public int on_time_roam_scan;
        /**
         * Cumulative milliseconds when radio is awake due to pno scan from the last radio chip
         * reset
         */
        public int on_time_pno_scan;
        /**
         * Cumulative milliseconds when radio is awake due to hotspot 2.0 scan amd GAS exchange
         * from the last radio chip reset
         */
        public int on_time_hs20_scan;
        /**
         * Channel stats list
         */
        public final SparseArray<ChannelStats> channelStatsMap = new SparseArray<>();
    }

    /**
     * Radio stats of all the radios.
     */
    public RadioStat[] radioStats;

    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append(" WifiLinkLayerStats: ").append('\n');

        sbuf.append(" version of StaLinkLayerStats: ").append(version).append('\n');
        sbuf.append(" my bss beacon rx: ").append(Integer.toString(this.beacon_rx)).append('\n');
        sbuf.append(" RSSI mgmt: ").append(Integer.toString(this.rssi_mgmt)).append('\n');
        sbuf.append(" BE : ").append(" rx=").append(Long.toString(this.rxmpdu_be))
                .append(" tx=").append(Long.toString(this.txmpdu_be))
                .append(" lost=").append(Long.toString(this.lostmpdu_be))
                .append(" retries=").append(Long.toString(this.retries_be)).append('\n')
                .append(" contention_time_min")
                .append(Long.toString(this.contentionTimeMinBeInUsec))
                .append(" contention_time_max")
                .append(Long.toString(this.contentionTimeMaxBeInUsec)).append('\n')
                .append(" contention_time_avg")
                .append(Long.toString(this.contentionTimeAvgBeInUsec))
                .append(" contention_num_samples")
                .append(Long.toString(this.contentionNumSamplesBe)).append('\n');
        sbuf.append(" BK : ").append(" rx=").append(Long.toString(this.rxmpdu_bk))
                .append(" tx=").append(Long.toString(this.txmpdu_bk))
                .append(" lost=").append(Long.toString(this.lostmpdu_bk))
                .append(" retries=").append(Long.toString(this.retries_bk)).append('\n')
                .append(" contention_time_min")
                .append(Long.toString(this.contentionTimeMinBkInUsec))
                .append(" contention_time_max")
                .append(Long.toString(this.contentionTimeMaxBkInUsec)).append('\n')
                .append(" contention_time_avg")
                .append(Long.toString(this.contentionTimeAvgBkInUsec))
                .append(" contention_num_samples")
                .append(Long.toString(this.contentionNumSamplesBk)).append('\n');
        sbuf.append(" VI : ").append(" rx=").append(Long.toString(this.rxmpdu_vi))
                .append(" tx=").append(Long.toString(this.txmpdu_vi))
                .append(" lost=").append(Long.toString(this.lostmpdu_vi))
                .append(" retries=").append(Long.toString(this.retries_vi)).append('\n')
                .append(" contention_time_min")
                .append(Long.toString(this.contentionTimeMinViInUsec))
                .append(" contention_time_max")
                .append(Long.toString(this.contentionTimeMaxViInUsec)).append('\n')
                .append(" contention_time_avg")
                .append(Long.toString(this.contentionTimeAvgViInUsec))
                .append(" contention_num_samples")
                .append(Long.toString(this.contentionNumSamplesVi)).append('\n');
        sbuf.append(" VO : ").append(" rx=").append(Long.toString(this.rxmpdu_vo))
                .append(" tx=").append(Long.toString(this.txmpdu_vo))
                .append(" lost=").append(Long.toString(this.lostmpdu_vo))
                .append(" retries=").append(Long.toString(this.retries_vo)).append('\n')
                .append(" contention_time_min")
                .append(Long.toString(this.contentionTimeMinVoInUsec))
                .append(" contention_time_max")
                .append(Long.toString(this.contentionTimeMaxVoInUsec)).append('\n')
                .append(" contention_time_avg")
                .append(Long.toString(this.contentionTimeAvgVoInUsec))
                .append(" contention_num_samples")
                .append(Long.toString(this.contentionNumSamplesVo)).append('\n');
        if (this.links != null) {
            sbuf.append("Per link stats: Number of links = ").append(this.links.length).append(
                    "\n");
            for (WifiLinkLayerStats.LinkSpecificStats link : this.links) {
                sbuf.append(" link id: ").append(link.link_id).append("\n");
                sbuf.append(" bss beacon rx: ").append(Integer.toString(link.beacon_rx)).append(
                        '\n');
                sbuf.append(" RSSI mgmt: ").append(Integer.toString(link.rssi_mgmt)).append('\n');
                sbuf.append(" BE : ").append(" rx=").append(Long.toString(link.rxmpdu_be))
                        .append(" tx=").append(Long.toString(link.txmpdu_be))
                        .append(" lost=").append(Long.toString(link.lostmpdu_be))
                        .append(" retries=").append(Long.toString(link.retries_be)).append('\n')
                        .append(" contention_time_min")
                        .append(Long.toString(link.contentionTimeMinBeInUsec))
                        .append(" contention_time_max")
                        .append(Long.toString(link.contentionTimeMaxBeInUsec)).append('\n')
                        .append(" contention_time_avg")
                        .append(Long.toString(link.contentionTimeAvgBeInUsec))
                        .append(" contention_num_samples")
                        .append(Long.toString(link.contentionNumSamplesBe)).append('\n');
                sbuf.append(" BK : ").append(" rx=").append(Long.toString(link.rxmpdu_bk))
                        .append(" tx=").append(Long.toString(link.txmpdu_bk))
                        .append(" lost=").append(Long.toString(link.lostmpdu_bk))
                        .append(" retries=").append(Long.toString(link.retries_bk)).append('\n')
                        .append(" contention_time_min")
                        .append(Long.toString(link.contentionTimeMinBkInUsec))
                        .append(" contention_time_max")
                        .append(Long.toString(link.contentionTimeMaxBkInUsec)).append('\n')
                        .append(" contention_time_avg")
                        .append(Long.toString(link.contentionTimeAvgBkInUsec))
                        .append(" contention_num_samples")
                        .append(Long.toString(link.contentionNumSamplesBk)).append('\n');
                sbuf.append(" VI : ").append(" rx=").append(Long.toString(link.rxmpdu_vi))
                        .append(" tx=").append(Long.toString(link.txmpdu_vi))
                        .append(" lost=").append(Long.toString(link.lostmpdu_vi))
                        .append(" retries=").append(Long.toString(link.retries_vi)).append('\n')
                        .append(" contention_time_min")
                        .append(Long.toString(link.contentionTimeMinViInUsec))
                        .append(" contention_time_max")
                        .append(Long.toString(link.contentionTimeMaxViInUsec)).append('\n')
                        .append(" contention_time_avg")
                        .append(Long.toString(link.contentionTimeAvgViInUsec))
                        .append(" contention_num_samples")
                        .append(Long.toString(link.contentionNumSamplesVi)).append('\n');
                sbuf.append(" VO : ").append(" rx=").append(Long.toString(link.rxmpdu_vo))
                        .append(" tx=").append(Long.toString(link.txmpdu_vo))
                        .append(" lost=").append(Long.toString(link.lostmpdu_vo))
                        .append(" retries=").append(Long.toString(link.retries_vo)).append('\n')
                        .append(" contention_time_min")
                        .append(Long.toString(link.contentionTimeMinVoInUsec))
                        .append(" contention_time_max")
                        .append(Long.toString(link.contentionTimeMaxVoInUsec)).append('\n')
                        .append(" contention_time_avg")
                        .append(Long.toString(link.contentionTimeAvgVoInUsec))
                        .append(" contention_num_samples")
                        .append(Long.toString(link.contentionNumSamplesVo)).append('\n');
                sbuf.append(" Duty cycle of the link=").append(
                        Short.toString(timeSliceDutyCycleInPercent)).append("\n");
                if (link.peerInfo != null) {
                    sbuf.append(" Number of peers=").append(link.peerInfo.length).append('\n');
                    for (PeerInfo peer : link.peerInfo) {
                        sbuf.append(" staCount=").append(peer.staCount)
                                .append(" chanUtil=").append(peer.chanUtil).append('\n');
                        if (peer.rateStats != null) {
                            for (RateStat rateStat : peer.rateStats) {
                                sbuf.append(" preamble=").append(rateStat.preamble)
                                        .append(" nss=").append(rateStat.nss)
                                        .append(" bw=").append(rateStat.bw)
                                        .append(" rateMcsIdx=").append(rateStat.rateMcsIdx)
                                        .append(" bitRateInKbps=").append(
                                                rateStat.bitRateInKbps).append(
                                                '\n')
                                        .append(" txMpdu=").append(rateStat.txMpdu)
                                        .append(" rxMpdu=").append(rateStat.rxMpdu)
                                        .append(" mpduLost=").append(rateStat.mpduLost)
                                        .append(" retries=").append(rateStat.retries).append('\n');
                            }
                        }
                    }
                }
            }
        }

        sbuf.append(" numRadios=" + numRadios)
                .append(" on_time= ").append(Integer.toString(this.on_time))
                .append(" tx_time=").append(Integer.toString(this.tx_time))
                .append(" rx_time=").append(Integer.toString(this.rx_time))
                .append(" scan_time=").append(Integer.toString(this.on_time_scan)).append('\n')
                .append(" nan_scan_time=")
                .append(Integer.toString(this.on_time_nan_scan)).append('\n')
                .append(" g_scan_time=")
                .append(Integer.toString(this.on_time_background_scan)).append('\n')
                .append(" roam_scan_time=")
                .append(Integer.toString(this.on_time_roam_scan)).append('\n')
                .append(" pno_scan_time=")
                .append(Integer.toString(this.on_time_pno_scan)).append('\n')
                .append(" hs2.0_scan_time=")
                .append(Integer.toString(this.on_time_hs20_scan)).append('\n')
                .append(" tx_time_per_level=" + Arrays.toString(tx_time_per_level)).append('\n');
        int numChanStats = this.channelStatsMap.size();
        sbuf.append(" Number of channel stats=").append(numChanStats).append('\n');
        for (int i = 0; i < numChanStats; ++i) {
            ChannelStats channelStatsEntry = this.channelStatsMap.valueAt(i);
            sbuf.append(" Frequency=").append(channelStatsEntry.frequency)
                    .append(" radioOnTimeMs=").append(channelStatsEntry.radioOnTimeMs)
                    .append(" ccaBusyTimeMs=").append(channelStatsEntry.ccaBusyTimeMs).append('\n');
        }
        int numRadios = this.radioStats == null ? 0 : this.radioStats.length;
        sbuf.append(" Individual radio stats: numRadios=").append(numRadios).append('\n');
        for (int i = 0; i < numRadios; i++) {
            RadioStat radio = this.radioStats[i];
            sbuf.append(" radio_id=" + radio.radio_id)
                    .append(" on_time=").append(Integer.toString(radio.on_time))
                    .append(" tx_time=").append(Integer.toString(radio.tx_time))
                    .append(" rx_time=").append(Integer.toString(radio.rx_time))
                    .append(" scan_time=").append(Integer.toString(radio.on_time_scan)).append('\n')
                    .append(" nan_scan_time=")
                    .append(Integer.toString(radio.on_time_nan_scan)).append('\n')
                    .append(" g_scan_time=")
                    .append(Integer.toString(radio.on_time_background_scan)).append('\n')
                    .append(" roam_scan_time=")
                    .append(Integer.toString(radio.on_time_roam_scan)).append('\n')
                    .append(" pno_scan_time=")
                    .append(Integer.toString(radio.on_time_pno_scan)).append('\n')
                    .append(" hs2.0_scan_time=")
                    .append(Integer.toString(radio.on_time_hs20_scan)).append('\n');
            int numRadioChanStats = radio.channelStatsMap.size();
            sbuf.append(" Number of channel stats=").append(numRadioChanStats).append('\n');
            for (int j = 0; j < numRadioChanStats; ++j) {
                ChannelStats channelStatsEntry = radio.channelStatsMap.valueAt(j);
                sbuf.append(" Frequency=").append(channelStatsEntry.frequency)
                        .append(" radioOnTimeMs=").append(channelStatsEntry.radioOnTimeMs)
                        .append(" ccaBusyTimeMs=").append(channelStatsEntry.ccaBusyTimeMs)
                        .append('\n');
            }
        }
        sbuf.append(" ts=" + timeStampInMs);
        int numPeers = this.peerInfo == null ? 0 : this.peerInfo.length;
        sbuf.append(" Number of peers=").append(numPeers).append('\n');
        for (int i = 0; i < numPeers; i++) {
            PeerInfo peer = this.peerInfo[i];
            sbuf.append(" staCount=").append(peer.staCount)
                    .append(" chanUtil=").append(peer.chanUtil).append('\n');
            int numRateStats = peer.rateStats == null ? 0 : peer.rateStats.length;
            for (int j = 0; j < numRateStats; j++) {
                RateStat rateStat = peer.rateStats[j];
                sbuf.append(" preamble=").append(rateStat.preamble)
                        .append(" nss=").append(rateStat.nss)
                        .append(" bw=").append(rateStat.bw)
                        .append(" rateMcsIdx=").append(rateStat.rateMcsIdx)
                        .append(" bitRateInKbps=").append(rateStat.bitRateInKbps).append('\n')
                        .append(" txMpdu=").append(rateStat.txMpdu)
                        .append(" rxMpdu=").append(rateStat.rxMpdu)
                        .append(" mpduLost=").append(rateStat.mpduLost)
                        .append(" retries=").append(rateStat.retries).append('\n');
            }
        }
        return sbuf.toString();
    }

    /**
     * Returns the link which has the best (=max) RSSI.
     *
     * @return link index.
     */
    private int getBestLinkIndex() {
        int best = 0;
        for (int i = 1; i < links.length; ++i) {
            if (links[i].rssi_mgmt > links[best].rssi_mgmt) {
                best = i;
            }
        }
        return best;
    }

    private void clearAggregatedPacketStats() {
        rxmpdu_be = 0;
        txmpdu_be = 0;
        lostmpdu_be = 0;
        retries_be = 0;

        rxmpdu_bk = 0;
        txmpdu_bk = 0;
        lostmpdu_bk = 0;
        retries_bk = 0;

        rxmpdu_vi = 0;
        txmpdu_vi = 0;
        lostmpdu_vi = 0;
        retries_vi = 0;

        rxmpdu_vo = 0;
        txmpdu_vo = 0;
        lostmpdu_vo = 0;
        retries_vo = 0;
    }

    /**
     * Add packet stats for all links.
     */
    private void aggregatePacketStats() {
        clearAggregatedPacketStats();
        for (LinkSpecificStats link : links) {
            rxmpdu_be += link.rxmpdu_be;
            txmpdu_be += link.txmpdu_be;
            lostmpdu_be += link.lostmpdu_be;
            retries_be += link.retries_be;

            rxmpdu_bk += link.rxmpdu_bk;
            txmpdu_bk += link.txmpdu_bk;
            lostmpdu_bk += link.lostmpdu_bk;
            retries_bk += link.retries_bk;

            rxmpdu_vi += link.rxmpdu_vi;
            txmpdu_vi += link.txmpdu_vi;
            lostmpdu_vi += link.lostmpdu_vi;
            retries_vi += link.retries_vi;

            rxmpdu_vo += link.rxmpdu_vo;
            txmpdu_vo += link.txmpdu_vo;
            lostmpdu_vo += link.lostmpdu_vo;
            retries_vo += link.retries_vo;
        }
    }

    /**
     * Squash all link peer stats to a single list.
     */
    private void aggregatePeerStats() {
        if (links == null) {
            return;
        }
        int numOfPeers = 0;
        for (LinkSpecificStats link : links) {
            if (link.peerInfo != null) {
                numOfPeers += link.peerInfo.length;
            }
        }
        if (numOfPeers == 0) {
            return;
        }
        peerInfo = new PeerInfo[numOfPeers];
        for (LinkSpecificStats link : links) {
            if (link.peerInfo == null) continue;
            int i = 0;
            for (PeerInfo peer : link.peerInfo) {
                peerInfo[i] = new PeerInfo();
                peerInfo[i].staCount = peer.staCount;
                peerInfo[i].chanUtil = peer.chanUtil;
                if (peer.rateStats == null) continue;
                peerInfo[i].rateStats = new RateStat[peer.rateStats.length];
                int j = 0;
                for (RateStat rateStat : peer.rateStats) {
                    peerInfo[i].rateStats[j] = new RateStat();
                    peerInfo[i].rateStats[j].preamble = rateStat.preamble;
                    peerInfo[i].rateStats[j].nss = rateStat.nss;
                    peerInfo[i].rateStats[j].bw = rateStat.bw;
                    peerInfo[i].rateStats[j].rateMcsIdx = rateStat.rateMcsIdx;
                    peerInfo[i].rateStats[j].bitRateInKbps = rateStat.bitRateInKbps;
                    peerInfo[i].rateStats[j].txMpdu = rateStat.txMpdu;
                    peerInfo[i].rateStats[j].rxMpdu = rateStat.rxMpdu;
                    peerInfo[i].rateStats[j].mpduLost = rateStat.mpduLost;
                    peerInfo[i].rateStats[j].retries = rateStat.retries;
                    j++;
                }
                i++;
            }
        }
    }

    /**
     * Aggregate link layer stats per link. The logic for aggregation is different for each of the
     * stats.
     * - Best link is selected based on rssi.
     * - Use best link for rssi, beacon_rx and dutyCycle and Contention stats.
     * - Packet related stats are added.
     * - Squash all peer stat lists to a single list of peers.
     */
    public void aggregateLinkLayerStats() {
        if (links == null) return;
        int i = getBestLinkIndex();
        rssi_mgmt = links[i].rssi_mgmt;
        beacon_rx = links[i].beacon_rx;
        timeSliceDutyCycleInPercent = links[i].timeSliceDutyCycleInPercent;
        contentionTimeMinBeInUsec = links[i].contentionTimeMinBeInUsec;
        contentionTimeMaxBeInUsec = links[i].contentionTimeMaxBeInUsec;
        contentionTimeAvgBeInUsec = links[i].contentionTimeAvgBeInUsec;
        contentionNumSamplesBe = links[i].contentionNumSamplesBe;
        contentionTimeMinBkInUsec = links[i].contentionTimeMinBkInUsec;
        contentionTimeMaxBkInUsec = links[i].contentionTimeMaxBkInUsec;
        contentionTimeAvgBkInUsec = links[i].contentionTimeAvgBkInUsec;
        contentionNumSamplesBk = links[i].contentionNumSamplesBk;
        contentionTimeMinViInUsec = links[i].contentionTimeMinViInUsec;
        contentionTimeMaxViInUsec = links[i].contentionTimeMaxViInUsec;
        contentionTimeAvgViInUsec = links[i].contentionTimeAvgViInUsec;
        contentionNumSamplesVi = links[i].contentionNumSamplesVi;
        contentionTimeMinVoInUsec = links[i].contentionTimeMinVoInUsec;
        contentionTimeMaxVoInUsec = links[i].contentionTimeMaxVoInUsec;
        contentionTimeAvgVoInUsec = links[i].contentionTimeAvgVoInUsec;
        contentionNumSamplesVo = links[i].contentionNumSamplesVo;
        aggregatePacketStats();
        aggregatePeerStats();
    }
}
