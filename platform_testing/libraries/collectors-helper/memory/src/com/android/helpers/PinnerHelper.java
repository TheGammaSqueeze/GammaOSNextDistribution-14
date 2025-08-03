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

import android.app.pinner.PinnerServiceClient;
import android.app.pinner.PinnedFileStat;
import androidx.annotation.VisibleForTesting;
import androidx.test.uiautomator.UiDevice;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Helper to collect pinned files information from the device using dumpsys.
 */
public class PinnerHelper implements ICollectorHelper<String> {
  public static final String SYSTEM_HEADER_NAME = "system";
  public static final String TOTAL_SIZE_BYTES_KEY = "pinner_total_size_bytes";
  public static final String TOTAL_FILE_COUNT_KEY = "pinner_total_files_count";
  public static final String PINNER_FILES_COUNT_SUFFIX = "files_count";

  // Map to maintain pinned files memory usage.
  private Map<String, String> mPinnerMap = new HashMap<>();

  @Override
  public boolean startCollecting() {
    return true;
  }

  @Override
  public Map<String, String> getMetrics() {
    mPinnerMap = new HashMap<>();

    PinnerServiceClient pinnerClient = new PinnerServiceClient();
    List<PinnedFileStat> stats = pinnerClient.getPinnerStats();

    // Parse the per file memory usage and files count from the pinner details.
    updatePinnerInfo(stats);

    return mPinnerMap;
  }

  @Override
  public boolean stopCollecting() {
    return true;
  }

  private void updatePinnerInfo(List<PinnedFileStat> stats) {
    int totalFilesCount = 0;
    int totalBytes = 0;
    HashSet<String> groups = new HashSet<>();
    for (PinnedFileStat stat : stats) {
      // individual pinned file sizes.
      mPinnerMap.put(
          String.format("pinner_%s_%s_bytes", stat.getGroupName(), stat.getFilename()),
          String.valueOf(stat.getBytesPinned()));
      totalBytes += stat.getBytesPinned();
      totalFilesCount++;
      if (!groups.contains(stat.getGroupName())) {
        groups.add(stat.getGroupName());
      }
    }
    for (String group : groups) {
      long filesInGroup =
          stats.stream().filter(f -> f.getGroupName().equals(group)).count();
      String groupInMetric = group;
      if (groupInMetric.equals("")) {
        // Default group will be system
        groupInMetric = SYSTEM_HEADER_NAME;
      }
      mPinnerMap.put(
          String.format("pinner_%s_%s", groupInMetric, PINNER_FILES_COUNT_SUFFIX),
          String.valueOf(filesInGroup));
    }

    // Update the previous app pinned file count.
    mPinnerMap.put(TOTAL_FILE_COUNT_KEY, String.valueOf(totalFilesCount));
    mPinnerMap.put(TOTAL_SIZE_BYTES_KEY, String.valueOf(totalBytes));
  }

  /* Execute a shell command and return its output. */
  @VisibleForTesting
  public String executeShellCommand(String command) throws IOException {
    return UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        .executeShellCommand(command);
  }
}
