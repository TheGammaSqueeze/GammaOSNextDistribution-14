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
package com.android.tradefed.device.metric;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import com.google.common.collect.ImmutableSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Base implementation of {@link FilePullerDeviceMetricCollector} that allows pulling the showmap
 * files from the device and collect the metrics from it.
 */
@OptionClass(alias = "showmap-metric-collector")
public class ShowmapPullerMetricCollector extends FilePullerDeviceMetricCollector {

    private static final String PROCESS_NAME_REGEX = "(>>>\\s)(\\S+)(\\s.*<<<)";
    private static final String METRIC_START_END_TEXT = "------";
    private static final String METRIC_VALUE_SEPARATOR = "_";
    private static final String METRIC_UNIT = "bytes";
    private static final Set<String> SKIP_COLUMNS = ImmutableSet.of("flags", "object", "locked");

    private Boolean processFound = false;
    private String processName = null;
    private Map<String, Long> mGranularInfo = new HashMap<>();
    private Set<String> mProcessObjInfo = new HashSet<>();
    private Map<String, Integer> mColumnNameToColumnIndex = new HashMap<>();

    @Option(
            name = "showmap-metric-prefix",
            description = "Prefix to be used with the metrics collected from showmap.")
    private String mMetricPrefix = "showmap_granular";

    @Option(
            name = "showmap-process-name",
            description = "Process names to be parsed in showmap file.")
    private Collection<String> mProcessNames = new ArrayList<>();

    /**
     * Process the showmap output file for the additional metrics and add it to final metrics.
     *
     * @param key the option key associated to the file that was pulled from the device.
     * @param metricFile the {@link File} pulled from the device matching the option key.
     * @param data where metrics will be stored.
     */
    @Override
    public void processMetricFile(String key, File metricFile, DeviceMetricData data) {
        String line;
        Boolean metricFound = false;

        if (metricFile != null) {
            List<String> headerList = new ArrayList<>();
            try (BufferedReader mBufferReader = new BufferedReader(new FileReader(metricFile))) {
                while ((line = mBufferReader.readLine()) != null) {
                    if (!processFound) {
                        processFound = isProcessFound(line);
                        continue;
                    }
                    metricFound =
                            metricFound
                                    ? computeGranularMetrics(line, processName)
                                    : isMetricParsingStartEnd(line);
                    // We found the process name but have not found the headers
                    if (mColumnNameToColumnIndex.isEmpty()) {
                        if (!metricFound) {
                            // We have not reached the "----" line yet.
                            // Save the multi-line headers for later.
                            headerList.add(line);
                        } else if (metricFound) {
                            // we reach the "----" line, and the mColumnNameToColumnIndex is empty
                            // So we can get the headers now.
                            extractHeaders(line, headerList);
                        }
                    }
                }
            } catch (IOException e) {
                CLog.e("Error parsing showmap granular metrics");
                CLog.e(e);
            } finally {
                writeGranularMetricData(data);
                uploadMetricFile(metricFile);
            }
        }
    }

    @Override
    public void processMetricDirectory(String key, File metricDirectory, DeviceMetricData runData) {
        // Implement if all the files under specific directory have to be post processed.
    }

    /**
     * Extract the showmap file name used for constructing the output metric file
     *
     * @param showmapFileName
     * @return String name of the showmap file name excluding the UUID.
     */
    private String getShowmapFileName(String showmapFileName) {
        // For example return showmap_<test_name>-1_ from
        // showmap_<test_name>-1_13388308985625987330.txt excluding the UID.
        int lastIndex = showmapFileName.lastIndexOf("_");
        if (lastIndex != -1) {
            return showmapFileName.substring(0, lastIndex + 1);
        }
        return showmapFileName;
    }

    /**
     * Computing granular metrics by adding individual memory values for every object and create
     * final metric value
     *
     * @param line
     * @param processName
     */
    private Boolean computeGranularMetrics(String line, String processName) {
        String objectName;
        long mGranularValue;
        long metricCounter;
        String completeGranularMetric;

        if (isMetricParsingStartEnd(line)) {
            computeObjectsPerProcess(processName);
            processFound = false;
            return false;
        }

        String[] metricLine = line.trim().split("\\s+");
        try {
            objectName = metricLine[mColumnNameToColumnIndex.get("object")];
        } catch (ArrayIndexOutOfBoundsException e) {
            CLog.e("Error parsing granular metrics for %s", processName);
            computeObjectsPerProcess(processName);
            processFound = false;
            return false;
        }

        for (Map.Entry<String, Integer> entry : mColumnNameToColumnIndex.entrySet()) {
            String memName = entry.getKey();
            if (SKIP_COLUMNS.contains(memName)) {
                continue;
            }
            try {
                mGranularValue =
                        Long.parseLong(metricLine[mColumnNameToColumnIndex.get(memName)]) * 1024;
            } catch (NumberFormatException e) {
                CLog.e("Error parsing granular metrics for %s", processName);
                computeObjectsPerProcess(processName);
                processFound = false;
                return false;
            }
            /**
             * final metric will be of following format
             * showmap_granular_<memory>_bytes_<process>_<object></object>
             * showmap_granular_rss_bytes_system_server_/system/fonts/SourceSansPro-Italic.ttf:104
             */
            completeGranularMetric =
                    String.join(
                            METRIC_VALUE_SEPARATOR,
                            mMetricPrefix,
                            memName,
                            METRIC_UNIT,
                            processName,
                            objectName);
            metricCounter =
                    mGranularInfo.containsKey(completeGranularMetric)
                            ? mGranularInfo.get(completeGranularMetric)
                            : 0L;
            mGranularInfo.put(completeGranularMetric, metricCounter + mGranularValue);
        }
        mProcessObjInfo.add(objectName);
        return true;
    }

    /**
     * Append granular metrics to DeviceMetricData object
     *
     * @param data
     */
    private void writeGranularMetricData(DeviceMetricData data) {
        for (Map.Entry<String, Long> granularData : mGranularInfo.entrySet()) {
            MetricMeasurement.Metric.Builder metricBuilder = MetricMeasurement.Metric.newBuilder();
            metricBuilder.getMeasurementsBuilder().setSingleInt(granularData.getValue());
            data.addMetric(
                    String.format("%s", granularData.getKey()),
                    metricBuilder.setType(MetricMeasurement.DataType.RAW));
        }
    }

    /**
     * Uploads showmap text file to artifacts
     *
     * @param uploadFile
     */
    private void uploadMetricFile(File uploadFile) {
        try (InputStreamSource source = new FileInputStreamSource(uploadFile, true)) {
            testLog(getShowmapFileName(uploadFile.getName()), LogDataType.TEXT, source);
        }
    }

    /**
     * Extract the showmap column header used for computeGranularMetrics
     *
     * <p>We first get each hyphens lengths in a row first which indicates the length of the column.
     * and then we split the header string by hyphens length including the empty space. At the end,
     * we concat the split string among multiple rows as a machine-readable header.
     *
     * <p>In the showmap output file, the hyphens/dashes give the most predictable delineation of
     * columns. The showmap output format was meant to be human-readable, not machine-readable, so
     * there will always be some levels of hackiness involved.
     *
     * @param hyphens expect to extract headers when reaching ----
     * @param headerList multi-line headers in a list
     */
    private void extractHeaders(String hyphens, List<String> headerList) {
        List<Integer> steps =
                Stream.of(hyphens.trim().split("\\s"))
                        .map(s -> s.length())
                        .collect(Collectors.toList());
        // For each segment of hyphens, calculate its column's start and end indices.
        int columnStart = 0;
        for (int i = 0; i < steps.size(); i++) {
            int columnEnd = columnStart + steps.get(i) + 1;
            String header = "";
            // Then, for each header row, get the header part with the start and end indices.
            for (String row : headerList) {
                String h = row.toLowerCase();
                header =
                        header.concat(
                                h.substring(
                                                columnStart > h.length() ? h.length() : columnStart,
                                                columnEnd > h.length() ? h.length() : columnEnd)
                                        .trim());
            }
            columnStart = columnEnd;
            mColumnNameToColumnIndex.put(header, i);
        }
    }

    /**
     * Returns if line contains '------' text
     *
     * @param line
     * @return true or false
     */
    private Boolean isMetricParsingStartEnd(String line) {
        if (line.contains(METRIC_START_END_TEXT)) {
            return true;
        }
        return false;
    }

    /**
     * Returns if particular process needs to be parsed
     *
     * @param line
     * @return true or false
     */
    private Boolean isProcessFound(String line) {
        if (mProcessNames.isEmpty()) return false;
        boolean psResult;
        Pattern psPattern = Pattern.compile(PROCESS_NAME_REGEX);
        Matcher psMatcher = psPattern.matcher(line);
        if (psMatcher.find()) {
            processName = psMatcher.group(2);
            psResult = mProcessNames.contains(processName);
            return psResult;
        }
        return false;
    }

    /**
     * Counts total no. of unique objects per process showmap_granular_<process>_total_object_count
     *
     * @param processName
     */
    private void computeObjectsPerProcess(String processName) {
        String objCounterMetric =
                String.join(
                        METRIC_VALUE_SEPARATOR, mMetricPrefix, processName, "total_object_count");
        if (mProcessObjInfo.size() > 0) {
            mGranularInfo.put(objCounterMetric, (long) mProcessObjInfo.size());
            mProcessObjInfo.clear();
        }
    }
}
