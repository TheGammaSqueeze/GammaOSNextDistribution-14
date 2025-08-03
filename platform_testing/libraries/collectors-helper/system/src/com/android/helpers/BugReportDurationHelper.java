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

package com.android.helpers;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BugReportDurationHelper is used to collect the durations of a bug report's component sections
 * during a test.
 */
public class BugReportDurationHelper implements ICollectorHelper<Double> {

    private static final String TAG = BugReportDurationHelper.class.getSimpleName();

    // Commands that will run on the test device.
    private static final String LS_CMD = "ls %s";
    private static final String UNZIP_EXTRACT_CMD = "unzip -p %s %s";
    private static final String UNZIP_CONTENTS_CMD = "unzip -l %s";

    // Filters for selecting or omitting lines from the raw bug report.
    private static final String DUMPSTATE_DURATION_FILTER = "was the duration of \'";
    private static final String DUMPSYS_DURATION_FILTER = "was the duration of dumpsys";
    private static final String DUMPSTATE_TIMEOUT_FILTER = "timed out after";
    private static final String DUMPSTATE_LOG_TAG_FILTER = "dumpstate:";
    private static final String SHOWMAP_FILTER = "SHOW MAP";

    // Filters for selecting lines from dumpstate_board.txt. Unlike raw bug reports, dumpstate_board
    // sections and durations are contained on separate lines.
    private static final String DUMPSTATE_BOARD_SECTION_FILTER = "------ Section end:";
    private static final String DUMPSTATE_BOARD_DURATION_FILTER = "Elapsed msec:";

    // This pattern will match a group of characters representing a number with a decimal point.
    private Pattern decimalDurationPattern = Pattern.compile("[0-9]+\\.[0-9]+");
    // This pattern will match a group of characters representing a number without a decimal point.
    private Pattern integerDurationPattern = Pattern.compile("[0-9]+");

    // This pattern will match a group of characters enclosed by \'.
    private Pattern dumpstateKeyPattern = Pattern.compile("'(.+)'");
    // This pattern will match a group of characters surrounded by "dumpsys " and ','.
    private Pattern dumpsysKeyPattern = Pattern.compile("dumpsys (.+),");
    // This pattern will match a group of characters surrounded by ": " and " -".
    private Pattern dumpstateBoardKeyPattern = Pattern.compile(": (.+) -");

    private String bugReportDir;

    private UiDevice device;

    public BugReportDurationHelper(String dir) {
        super();
        // The helper methods assume that the directory path is terminated by '/'.
        if (dir.charAt(dir.length() - 1) != '/') {
            dir += '/';
        }
        bugReportDir = dir;
    }

    @Override
    public boolean startCollecting() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Log.d(TAG, "Started collecting for BugReportDuration.");
        return true;
    }

    /** Convenience class for returning separate lists of lines from the raw bug report. */
    @VisibleForTesting
    public class BugReportDurationLines {
        public ArrayList<String> dumpstateLines;
        public ArrayList<String> dumpsysLines;
        public ArrayList<String> timeoutLines;

        public BugReportDurationLines() {
            dumpstateLines = new ArrayList<>();
            dumpsysLines = new ArrayList<>();
            timeoutLines = new ArrayList<>();
        }

        public boolean isEmpty() {
            return dumpstateLines.isEmpty() && dumpsysLines.isEmpty() && timeoutLines.isEmpty();
        }

        // Only used in testing.
        public boolean contains(String s) {
            return dumpstateLines.contains(s)
                    || dumpsysLines.contains(s)
                    || timeoutLines.contains(s);
        }
    }

    /** Convenience class for returning section name/duration lines from dumpstate_board.txt. */
    @VisibleForTesting
    public class DumpstateBoardLines {
        public ArrayList<String> sectionNameLines;
        public ArrayList<String> durationLines;

        public DumpstateBoardLines() {
            sectionNameLines = new ArrayList<>();
            durationLines = new ArrayList<>();
        }

        public boolean isValid() {
            return sectionNameLines.size() == durationLines.size();
        }

        public boolean isEmpty() {
            return isValid() && sectionNameLines.isEmpty();
        }

        public int size() {
            return sectionNameLines.size();
        }

        // Only used in testing.
        public boolean contains(String s) {
            return sectionNameLines.contains(s) || durationLines.contains(s);
        }
    }

    @Override
    public Map<String, Double> getMetrics() {
        Log.d(TAG, "Grabbing metrics for BugReportDuration.");
        Map<String, Double> metrics = new HashMap<>();
        String archive = getLatestBugReport();
        // No bug report was found, so there are no metrics to return.
        if (archive == null) {
            Log.w(TAG, "No bug report was found in directory: " + bugReportDir);
            return metrics;
        }

        BugReportDurationLines bugReportDurationLines = extractAndFilterBugReport(archive);
        // No lines relevant to bug report durations were found, so there are no metrics to return.
        if (bugReportDurationLines == null || bugReportDurationLines.isEmpty()) {
            Log.w(TAG, "No lines relevant to bug report durations were found.");
            return metrics;
        }

        /*
         * Some examples of section duration-relevant lines are:
         *     ------ 44.619s was the duration of 'dumpstate_board()' ------
         *     ------ 21.397s was the duration of 'DUMPSYS' ------
         */
        for (String line : bugReportDurationLines.dumpstateLines) {
            String dumpstateSection = parseDumpstateSection(line);
            double duration = parseDecimalDuration(line);
            // The line doesn't contain the expected dumpstate section name or duration value.
            if (dumpstateSection == null || duration == -1) {
                Log.e(TAG, "Dumpstate section name or duration could not be parsed from: " + line);
                continue;
            }
            String key = convertDumpstateSectionToKey(dumpstateSection);
            // Some sections are collected multiple times (e.g. trusty version, system log).
            metrics.put(key, duration + metrics.getOrDefault(key, 0.0));
        }

        /*
         * Some examples of dumpsys duration-relevant lines are:
         * --------- 20.865s was the duration of dumpsys meminfo, ending at: 2023-04-24 19:53:46
         * --------- 0.316s was the duration of dumpsys gfxinfo, ending at: 2023-04-24 19:54:06
         */
        for (String line : bugReportDurationLines.dumpsysLines) {
            String dumpsysSection = parseDumpsysSection(line);
            double duration = parseDecimalDuration(line);
            // The line doesn't contain the expected dumpsys section name or duration value.
            if (dumpsysSection == null || duration == -1) {
                Log.e(TAG, "Dumpstate section name or duration could not be parsed from: " + line);
                continue;
            }
            metrics.put(convertDumpsysSectionToKey(dumpsysSection), duration);
        }

        metrics.put(
                "dumpstate-section-timeout-count",
                (double) bugReportDurationLines.timeoutLines.size());

        DumpstateBoardLines dumpstateBoardLines = extractAndFilterDumpstateBoard(archive);
        // No lines relevant to dumpstate board durations were found, but we should return the
        // previously-inserted metrics anyways.
        if (dumpstateBoardLines == null || dumpstateBoardLines.isEmpty()) {
            Log.w(TAG, "No lines relevant to dumpstate board durations were found.");
            return metrics;
        } else if (!dumpstateBoardLines.isValid()) {
            Log.w(TAG, "Mismatch in the number of dumpstate_board section names and durations.");
            return metrics;
        }

        /*
         * Some examples of dumpstate_board section lines are:
         *     ------ Section end: dump_display ------
         *     ------ Section end: dump_modem.sh ------
         *     ------ Section end: dump_modemlog ------
         *
         * Some examples of dumpstate_board duration lines are:
         *     Elapsed msec: 103
         *     Elapsed msec: 89
         *     Elapsed msec: 12532
         */
        for (int i = 0; i < dumpstateBoardLines.size(); i++) {
            String dumpstateBoardSection =
                    parseDumpstateBoardSection(dumpstateBoardLines.sectionNameLines.get(i));
            double duration = parseIntegerDuration(dumpstateBoardLines.durationLines.get(i));
            if (dumpstateBoardSection == null || duration == -1) {
                Log.e(
                        TAG,
                        String.format(
                                "Section name or duration could not be parsed from (%s, %s)",
                                dumpstateBoardLines.sectionNameLines.get(i),
                                dumpstateBoardLines.durationLines.get(i)));
                continue;
            }
            metrics.put(convertDumpstateBoardSectionToKey(dumpstateBoardSection), duration);
        }
        return metrics;
    }

    @Override
    public boolean stopCollecting() {
        Log.d(TAG, "Stopped collecting for BugReportDuration.");
        return true;
    }

    // Returns the name of the most recent bug report .zip in bugReportDir.
    @VisibleForTesting
    public String getLatestBugReport() {
        try {
            // executeShellCommand will return files (separated by '\n') in a single String.
            String[] files =
                    device.executeShellCommand(String.format(LS_CMD, bugReportDir)).split("\n");
            HashSet<String> bugreports = new HashSet<>();
            for (String file : files) {
                if (file.contains("bugreport") && file.endsWith("zip")) {
                    // We don't want to keep track of wifi or telephony bug reports because they
                    // break the assumption that lexicographically-greater bug reports are also
                    // more-recent.
                    if (file.contains("wifi") || file.contains("telephony")) {
                        Log.w(TAG, "Wifi or telephony bug report found and skipped: " + file);
                    } else {
                        bugreports.add(file);
                    }
                }
            }
            if (bugreports.size() == 0) {
                Log.e(TAG, "Failed to find a bug report in " + bugReportDir);
                return null;
            } else if (bugreports.size() > 1) {
                Log.w(TAG, "There are multiple bug reports in " + bugReportDir + ":");
                for (String bugreport : bugreports) {
                    Log.w(TAG, "    " + bugreport);
                }
            }
            // Returns the newest bug report. Bug report names contain a timestamp, so the
            // lexicographically-greatest name will correspond to the most recent bug report.
            return Collections.max(bugreports);
        } catch (IOException e) {
            Log.e(TAG, "Failed to find a bug report in  " + bugReportDir + ": " + e.getMessage());
            return null;
        }
    }

    // Extracts a bug report .txt to stdout and returns a BugReportDurationLines object containing
    // lines with dumpstate/dumpsys sections and durations.
    @VisibleForTesting
    public BugReportDurationLines extractAndFilterBugReport(String archive) {
        Path archivePath = Paths.get(bugReportDir, archive);
        String entry = archive.replace("zip", "txt");
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String cmd = String.format(UNZIP_EXTRACT_CMD, archivePath.toString(), entry);
        Log.d(TAG, "The unzip command that will be run is: " + cmd);

        // We keep track of whether the buffered reader was empty because this probably indicates an
        // issue in unzipping (e.g. a mismatch in the bugreport's .zip name and .txt entry name).
        boolean bufferedReaderNotEmpty = false;
        BugReportDurationLines bugReportDurationLines = new BugReportDurationLines();
        try (InputStream is =
                        new ParcelFileDescriptor.AutoCloseInputStream(
                                automation.executeShellCommand(cmd));
                BufferedReader br = new BufferedReader(new InputStreamReader(is)); ) {
            String line;
            while ((line = br.readLine()) != null) {
                bufferedReaderNotEmpty = true;
                if (line.contains(DUMPSTATE_DURATION_FILTER) && !line.contains(SHOWMAP_FILTER)) {
                    bugReportDurationLines.dumpstateLines.add(line);
                } else if (line.contains(DUMPSYS_DURATION_FILTER)) {
                    bugReportDurationLines.dumpsysLines.add(line);
                } else if (line.contains(DUMPSTATE_LOG_TAG_FILTER)
                        && line.contains(DUMPSTATE_TIMEOUT_FILTER)) {
                    bugReportDurationLines.timeoutLines.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract and parse the raw bug report: " + e.getMessage());
            return null;
        }
        if (!bufferedReaderNotEmpty) {
            Log.e(
                    TAG,
                    String.format(
                            "The buffered reader for file %s in archive %s was empty.",
                            entry, archivePath.toString()));
            dumpBugReportEntries(archivePath);
        }
        return bugReportDurationLines;
    }

    // Extracts a dumpstate_board.txt file to stdout and returns a DumpstateBoardLines object
    // containing lines with dumpstate_board sections and lines with durations.
    @VisibleForTesting
    public DumpstateBoardLines extractAndFilterDumpstateBoard(String archive) {
        Path archivePath = Paths.get(bugReportDir, archive);
        String entry = "dumpstate_board.txt";
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String cmd = String.format(UNZIP_EXTRACT_CMD, archivePath.toString(), entry);
        Log.d(TAG, "The unzip command that will be run is: " + cmd);

        // We keep track of whether the buffered reader was empty because this may indicate an issue
        // in unzipping (e.g. dumpstate_board.txt doesn't exist for some reason).
        boolean bufferedReaderNotEmpty = false;
        DumpstateBoardLines dumpstateBoardLines = new DumpstateBoardLines();
        try (InputStream is =
                        new ParcelFileDescriptor.AutoCloseInputStream(
                                automation.executeShellCommand(cmd));
                BufferedReader br = new BufferedReader(new InputStreamReader(is)); ) {
            String line;
            while ((line = br.readLine()) != null) {
                bufferedReaderNotEmpty = true;
                if (line.contains(DUMPSTATE_BOARD_SECTION_FILTER)) {
                    dumpstateBoardLines.sectionNameLines.add(line);
                } else if (line.contains(DUMPSTATE_BOARD_DURATION_FILTER)) {
                    dumpstateBoardLines.durationLines.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract and parse dumpstate_board.txt: " + e.getMessage());
            return null;
        }
        if (!bufferedReaderNotEmpty) {
            Log.e(
                    TAG,
                    String.format(
                            "The buffered reader for file %s in archive %s was empty.",
                            entry, archivePath.toString()));
            dumpBugReportEntries(archivePath);
        }
        return dumpstateBoardLines;
    }

    // Prints out every entry contained in the zip archive at archivePath.
    private void dumpBugReportEntries(Path archivePath) {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String cmd = String.format(UNZIP_CONTENTS_CMD, archivePath.toString());
        Log.d(TAG, "The list-contents command that will be run is: " + cmd);
        try (InputStream is =
                        new ParcelFileDescriptor.AutoCloseInputStream(
                                automation.executeShellCommand(cmd));
                BufferedReader br = new BufferedReader(new InputStreamReader(is)); ) {
            String line;
            Log.d(TAG, "Dumping list of entries in " + archivePath);
            while ((line = br.readLine()) != null) {
                Log.d(TAG, "    " + line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to list the contents of the bug report: " + e.getMessage());
        }
    }

    // Parses a decimal duration from the input duration-relevant log line. This should only be
    // called for dumpstate/dumpsys lines.
    @VisibleForTesting
    public double parseDecimalDuration(String line) {
        Matcher m = decimalDurationPattern.matcher(line);
        if (m.find()) {
            return Double.parseDouble(m.group());
        } else {
            return -1;
        }
    }

    // Parses an integer duration from the input duration-relevant line. This should only be called
    // for dumpstate_board duration-specific lines.
    @VisibleForTesting
    public double parseIntegerDuration(String line) {
        Matcher m = integerDurationPattern.matcher(line);
        if (m.find()) {
            return Double.parseDouble(m.group());
        } else {
            return -1;
        }
    }

    private String parseSection(String line, Pattern p) {
        Matcher m = p.matcher(line);
        if (m.find()) {
            // m.group(0) corresponds to the entire match; m.group(1) is the substring within the
            // first set of parentheses.
            return m.group(1);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public String parseDumpstateSection(String line) {
        return parseSection(line, dumpstateKeyPattern);
    }

    @VisibleForTesting
    public String parseDumpsysSection(String line) {
        return parseSection(line, dumpsysKeyPattern);
    }

    @VisibleForTesting
    public String parseDumpstateBoardSection(String line) {
        return parseSection(line, dumpstateBoardKeyPattern);
    }

    // Converts a dumpstate section to a key by replacing spaces with '-', lowercasing, and
    // prepending "bugreport-duration-".
    @VisibleForTesting
    public String convertDumpstateSectionToKey(String dumpstateSection) {
        return "bugreport-duration-" + dumpstateSection.replace(" ", "-").toLowerCase();
    }

    // Converts a dumpsys section to a key by prepending "bugreport-dumpsys-duration-".
    //
    // Spaces aren't replaced because dumpsys sections shouldn't contain spaces. Lowercasing is not
    // done either because dumpsys section names are case-sensitive, and can be run on-device as-is
    // (e.g. "dumpsys SurfaceFlinger").
    @VisibleForTesting
    public String convertDumpsysSectionToKey(String dumpsysSection) {
        return "bugreport-dumpsys-duration-" + dumpsysSection;
    }

    // Converts a dumpstate_board section name to a key by prepending
    // "bugreport-dumpstateboard-duration-".
    //
    // Spaces aren't replaced and lowercasing is not done because dumpstate_board section names are
    // binary/script names.
    @VisibleForTesting
    public String convertDumpstateBoardSectionToKey(String dumpstateBoardSection) {
        return "bugreport-dumpstate_board-duration-" + dumpstateBoardSection;
    }
}
