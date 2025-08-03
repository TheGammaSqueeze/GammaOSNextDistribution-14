/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.helpers;

import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a collector helper that collects the dumpsys meminfo output for specified services and
 * puts them into files.
 */
public class LyricMemProfilerHelper implements ICollectorHelper<Integer> {

    private static final String TAG = LyricMemProfilerHelper.class.getSimpleName();

    private static final String PID_CMD = "pgrep -f -o ";

    private static final String DUMPSYS_MEMINFO_CMD = "dumpsys meminfo -s ";

    private static final String DMABUF_DUMP_CMD = "dmabuf_dump";

    private static final int MIN_PROFILE_PERIOD_MS = 100;

    private String[] mCameraProcNameList;

    @VisibleForTesting String[] mCameraPidList;

    private String[] mMetricNameList;

    // Extract value of "Native Heap:" and  "TOTAL PSS:" from command: "dumpsys meminfo -s [pid]"
    // example of "dumpsys meminfo -s [pid]":
    // Applications Memory Usage (in Kilobytes):
    // Uptime: 2649336 Realtime: 3041976
    // ** MEMINFO in pid 14612 [android.hardwar] **
    // App Summary
    //                       Pss(KB)                        Rss(KB)
    //                        ------                         ------
    //           Java Heap:        0                              0
    //         Native Heap:   377584                         377584
    //                Code:    79008                         117044
    //               Stack:     3364                           3364
    //            Graphics:    47672                          47672
    //       Private Other:    37188
    //              System:     5307
    //             Unknown:                                   39136
    //
    //           TOTAL PSS:   550123            TOTAL RSS:   584800      TOTAL      SWAP (KB):
    //  0
    //
    // Above string example will be remove "\n" first  and then extracted
    // "377584" right after "Native Heap" and "550123" right after "TOTAL PSS"
    // by following Regexes:
    private static final Pattern METRIC_MEMINFO_PATTERN =
            Pattern.compile(".+Native Heap:\\s*(\\d+)\\s*.+TOTAL PSS:\\s*(\\d+)\\s*.+");

    // extrace value after "PROCESS TOTAL" in camera provider section
    // of string from command "dmabuf_dump"
    // Example:
    //          PROCESS TOTAL          1752 kB          1873 kB
    // Above string example will be extracted 1752 as group(1) and 1832 as group(2)
    private static final Pattern METRIC_DMABUF_PSS_PATTERN =
            Pattern.compile("\\s*PROCESS TOTAL\\s*(\\d+)\\s*kB\\s*(\\d+)\\s*kB");

    // Folling Regexes is for removing "\n" in string
    private static final Pattern REMOVE_CR_PATTERN = Pattern.compile("\n");

    // List of regexes which are to match string format as:
    //   [Camera process Name]xxx:[pid of camera process]
    //
    // Use above pattern to find data section of camera process
    // in output string from command "dmabuf_dump"
    private Pattern[] mCameraDmabufPatternList;

    private UiDevice mUiDevice;

    private int mProfilePeriodMs = 0;

    private Timer mTimer;

    private static class MemInfo {
        int mNativeHeap;
        int mTotalPss;

        public MemInfo() {}

        public MemInfo(int nativeHeap, int totalPss) {
            mNativeHeap = nativeHeap;
            mTotalPss = totalPss;
        }
    }

    private MemInfo[] mMaxCameraMemInfoList;

    private int[] mMaxCameraDmabufList;

    private int mMaxTotalCameraDmabuf = 0;

    private int mMaxTotalCameraMemory = 0;

    private synchronized void setMaxResult(
            MemInfo[] memInfoList, int[] dmabufList, int totalDmabuf, int totalMemory) {
        for (int i = 0; i < mMaxCameraMemInfoList.length; i++) {
            mMaxCameraMemInfoList[i].mNativeHeap =
                    Math.max(mMaxCameraMemInfoList[i].mNativeHeap, memInfoList[i].mNativeHeap);
            mMaxCameraMemInfoList[i].mTotalPss =
                    Math.max(mMaxCameraMemInfoList[i].mTotalPss, memInfoList[i].mTotalPss);
            mMaxCameraDmabufList[i] = Math.max(mMaxCameraDmabufList[i], dmabufList[i]);
        }
        mMaxTotalCameraDmabuf = Math.max(mMaxTotalCameraDmabuf, totalDmabuf);
        mMaxTotalCameraMemory = Math.max(mMaxTotalCameraMemory, totalMemory);
    }

    @VisibleForTesting
    protected UiDevice initUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Override
    public boolean startCollecting() {
        if (null == mUiDevice) {
            mUiDevice = initUiDevice();
        }
        if (null != mCameraProcNameList
                && 0 < mCameraProcNameList.length
                && null != mMetricNameList
                && mCameraProcNameList.length == mMetricNameList.length) {
            mCameraPidList = new String[mCameraProcNameList.length];
            mCameraDmabufPatternList = new Pattern[mCameraProcNameList.length];
            for (int i = 0; i < mCameraProcNameList.length; i++) {
                mCameraPidList[i] = getProcPid(mCameraProcNameList[i]);
                // This Regexes is to match string format such as:
                //   [camera process name]xxx:[pid of camera process]
                //   ex: if "provider@" is camera process name, the pattern is:
                //   provider@xxx:[pid of camera process]
                // Use following pattern to find data section of camera prcoess
                // of output string from command "dmabuf_dump"
                mCameraDmabufPatternList[i] =
                        Pattern.compile("\\s*.*:" + mCameraPidList[i] + "\\s*");
            }
            // To avoid frequnce of polling memory data too high and interference test case
            // Set minimum polling period to MIN_PROFILE_PERIOD_MS (100), period profile only
            // enable when MIN_PROFILE_PERIOD_MS <= configured polling period
            if (MIN_PROFILE_PERIOD_MS <= mProfilePeriodMs) {
                if (null == mTimer) {
                    mMaxCameraMemInfoList = new MemInfo[mCameraProcNameList.length];
                    for (int i = 0; i < mMaxCameraMemInfoList.length; i++)
                        mMaxCameraMemInfoList[i] = new MemInfo();
                    mMaxCameraDmabufList = new int[mCameraProcNameList.length];
                    mTimer = new Timer();
                    abstract class MyTimerTask extends TimerTask {
                        MemInfo[] mTimerMemInfoList;
                        int[] mTimerDmabufList;
                        String[] mMemInfoStringList;

                        public MyTimerTask(int procNumber) {
                            mTimerMemInfoList = new MemInfo[procNumber];
                            mTimerDmabufList = new int[procNumber];
                            mMemInfoStringList = new String[procNumber];
                        }
                    }
                    mTimer.schedule(
                            new MyTimerTask(mCameraProcNameList.length) {
                                @Override
                                public void run() {
                                    int i, totalDmabuf = 0, totalMemory = 0;
                                    String dmabufString = getDmabufDumpString();
                                    for (i = 0; i < mTimerMemInfoList.length; i++) {
                                        mMemInfoStringList[i] = getMemInfoString(mCameraPidList[i]);
                                    }
                                    processDmabufDump(dmabufString, mTimerDmabufList);
                                    for (i = 0; i < mTimerMemInfoList.length; i++) {
                                        mTimerMemInfoList[i] =
                                                processMemInfo(mMemInfoStringList[i]);
                                        totalMemory += mTimerMemInfoList[i].mTotalPss;
                                        totalDmabuf += mTimerDmabufList[i];
                                    }
                                    totalMemory += totalDmabuf;
                                    setMaxResult(
                                            mTimerMemInfoList,
                                            mTimerDmabufList,
                                            totalDmabuf,
                                            totalMemory);
                                }
                            },
                            MIN_PROFILE_PERIOD_MS,
                            mProfilePeriodMs);
                }
            }
        }
        return true;
    }

    public void setProfilePeriodMs(int periodMs) {
        mProfilePeriodMs = periodMs;
    }

    public void setProfileCameraProcName(String[] pidNameList) {
        mCameraProcNameList = pidNameList;
    }

    public void setProfileMetricName(String[] metricNameList) {
        mMetricNameList = metricNameList;
    }

    @Override
    public Map<String, Integer> getMetrics() {
        if (null == mCameraPidList && 0 == mCameraPidList.length) {
            return new HashMap<>();
        }
        String[] memInfoStringList = new String[mCameraPidList.length];
        String dmabufDumpString = getDmabufDumpString();
        int i;
        for (i = 0; i < memInfoStringList.length; i++) {
            memInfoStringList[i] = getMemInfoString(mCameraPidList[i]);
        }
        Map<String, Integer> metrics = processOutput(memInfoStringList, dmabufDumpString);

        if (MIN_PROFILE_PERIOD_MS <= mProfilePeriodMs) {
            for (i = 0; i < mMetricNameList.length; i++) {
                metrics.put(
                        "max" + mMetricNameList[i] + "NativeHeap",
                        mMaxCameraMemInfoList[i].mNativeHeap);
                metrics.put(
                        "max" + mMetricNameList[i] + "TotalPss",
                        mMaxCameraMemInfoList[i].mTotalPss);
                metrics.put("max" + mMetricNameList[i] + "Dmabuf", mMaxCameraDmabufList[i]);
            }
            metrics.put("maxNativeHeap", mMaxCameraMemInfoList[0].mNativeHeap);
            metrics.put("maxTotalPss", mMaxCameraMemInfoList[0].mTotalPss);
            metrics.put("maxDmabuf", mMaxCameraDmabufList[0]);

            metrics.put("maxTotalCameraDmabuf", mMaxTotalCameraDmabuf);
            metrics.put("maxTotalCameraMemory", mMaxTotalCameraMemory);
        }
        return metrics;
    }

    @Override
    public boolean stopCollecting() {
        if (null != mTimer) {
            mTimer.cancel();
        }
        return true;
    }

    private MemInfo processMemInfo(String memInfoString) {
        int nativeHeap = 0, totalPss = 0;
        Matcher matcher =
                METRIC_MEMINFO_PATTERN.matcher(
                        REMOVE_CR_PATTERN.matcher(memInfoString).replaceAll(""));
        if (matcher.find()) {
            nativeHeap = Integer.parseInt(matcher.group(1));
            totalPss = Integer.parseInt(matcher.group(2));
        } else {
            Log.e(TAG, "Failed to collect Lyric Native Heap or TOTAL PSS metrics.");
        }
        return new MemInfo(nativeHeap, totalPss);
    }

    private void processDmabufDump(String dmabufDumpString, int[] dmabufList) {
        Pattern[] procPatternList =
                Arrays.copyOf(mCameraDmabufPatternList, mCameraDmabufPatternList.length);
        int matchCount = 0;
        Integer matchIndex = null;
        Matcher matcher;
        for (String line : dmabufDumpString.split("\n")) {
            if (null == matchIndex) {
                for (int i = 0; i < procPatternList.length; i++) {
                    if (procPatternList[i] != null) {
                        matcher = procPatternList[i].matcher(line);
                        if (matcher.find()) {
                            matchIndex = i;
                            procPatternList[i] = null;
                            break;
                        }
                    }
                }
            } else {
                matcher = METRIC_DMABUF_PSS_PATTERN.matcher(line);
                if (matcher.find()) {
                    dmabufList[matchIndex] = Integer.parseInt(matcher.group(2));
                    matchIndex = null;
                    matchCount++;
                    if (matchCount == procPatternList.length) break;
                }
            }
        }
    }

    @VisibleForTesting
    Map<String, Integer> processOutput(String[] memInfoStringList, String dmabufDumpString) {
        Map<String, Integer> metrics = new HashMap<>();
        MemInfo[] memInfoList = new MemInfo[mCameraProcNameList.length];
        int[] dmabufList = new int[mCameraProcNameList.length];
        int totalDmabuf = 0, totalMemory = 0, i;
        processDmabufDump(dmabufDumpString, dmabufList);
        for (i = 0; i < mCameraProcNameList.length; i++) {
            memInfoList[i] = processMemInfo(memInfoStringList[i]);
            totalDmabuf += dmabufList[i];
            totalMemory += memInfoList[i].mTotalPss;
        }
        totalMemory += totalDmabuf;
        if (null != mTimer) {
            setMaxResult(memInfoList, dmabufList, totalDmabuf, totalMemory);
        }
        for (i = 0; i < mMetricNameList.length; i++) {
            metrics.put(mMetricNameList[i] + "NativeHeap", memInfoList[i].mNativeHeap);
            metrics.put(mMetricNameList[i] + "TotalPss", memInfoList[i].mTotalPss);
            metrics.put(mMetricNameList[i] + "Dmabuf", dmabufList[i]);
        }
        metrics.put("nativeHeap", memInfoList[0].mNativeHeap);
        metrics.put("totalPss", memInfoList[0].mTotalPss);
        metrics.put("dmabuf", dmabufList[0]);

        metrics.put("totalCameraDmabuf", totalDmabuf);
        metrics.put("totalCameraMemory", totalMemory);
        return metrics;
    }

    @VisibleForTesting
    String getProcPid(String procName) {
        String procPid;
        try {
            procPid = mUiDevice.executeShellCommand(PID_CMD + procName).trim();
        } catch (IOException e) {
            Log.e(TAG, "Failed to get PID of " + procName);
            procPid = "";
        }
        return procPid;
    }

    @VisibleForTesting
    String getMemInfoString(String pidString) {
        if (!pidString.isEmpty()) {
            try {
                String cmdString = DUMPSYS_MEMINFO_CMD + pidString;
                return mUiDevice.executeShellCommand(cmdString).trim();
            } catch (IOException e) {
                Log.e(TAG, "Failed to get Mem info string ");
            }
        }
        return "";
    }

    @VisibleForTesting
    synchronized String getDmabufDumpString() {
        if (null != mUiDevice) {
            try {
                final int minDmabufStringLen = 100;
                final int maxDmabufRetryCount = 5;
                String dmabufString;
                for (int retryCount = 0; retryCount < maxDmabufRetryCount; retryCount++) {
                    dmabufString = mUiDevice.executeShellCommand(DMABUF_DUMP_CMD).trim();
                    // "dmabuf_dump" may not get dmabuf size information but get following string:
                    // "debugfs entry for dmabuf not available, using /proc/<pid>/fdinfo instead"
                    // Here use string length to detected above condition and retry.
                    // Normal dmabuf size string should larger than 100 characters.
                    if (minDmabufStringLen < dmabufString.length()) {
                        return dmabufString;
                    }
                    Log.w(TAG, "dmabuf_dump return abnormal:" + dmabufString + ",retry");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get DMA buf dump string");
            }
        }
        return "";
    }
}
