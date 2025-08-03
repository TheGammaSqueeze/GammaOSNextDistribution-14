/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.compatibility.common.util;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Contains helper functions and shared constants for crash parsing. */
public class CrashUtils {
    // used to only detect actual addresses instead of nullptr and other unlikely values
    public static final BigInteger MIN_CRASH_ADDR = new BigInteger("8000", 16);
    // Matches the end of a crash
    public static final Pattern sEndofCrashPattern =
            Pattern.compile("DEBUG\\s+?:\\s+?backtrace:");
    public static final String DEVICE_PATH = "/data/local/tmp/CrashParserResults/";
    public static final String LOCK_FILENAME = "lockFile.loc";
    public static final String UPLOAD_REQUEST = "Please upload a result file to stagefright";
    public static final Pattern sUploadRequestPattern =
            Pattern.compile(UPLOAD_REQUEST);
    public static final String NEW_TEST_ALERT = "New test starting with name: ";
    public static final Pattern sNewTestPattern =
            Pattern.compile(NEW_TEST_ALERT + "(\\w+?)\\(.*?\\)");
    public static final String SIGNAL = "signal";
    public static final String ABORT_MESSAGE = "abortmessage";
    public static final String NAME = "name";
    public static final String PROCESS = "process";
    public static final String PID = "pid";
    public static final String TID = "tid";
    public static final String FAULT_ADDRESS = "faultaddress";
    public static final String FILENAME = "filename";
    public static final String METHOD = "method";
    public static final String BACKTRACE = "backtrace";
    // Matches the smallest blob that has the appropriate header and footer
    private static final Pattern sCrashBlobPattern =
            Pattern.compile("DEBUG\\s+?:( [*]{3})+?.*?DEBUG\\s+?:\\s+?backtrace:", Pattern.DOTALL);
    // Matches process id and name line and captures them
    private static final Pattern sPidtidNamePattern =
            Pattern.compile("pid: (\\d+?), tid: (\\d+?), name: ([^\\s]+?\\s+?)*?>>> (.*?) <<<");
    // Matches fault address and signal type line
    private static final Pattern sFaultLinePattern =
            Pattern.compile(
                    "\\w+? \\d+? \\((.*?)\\), code -*?\\d+? \\(.*?\\), fault addr "
                            + "(?:0x(\\p{XDigit}+)|-+)");
    // Matches the abort message line
    private static Pattern sAbortMessagePattern = Pattern.compile("(?i)Abort message: (.*)");
    // Matches one backtrace NOTE line, exactly as tombstone_proto_to_text's print_thread_backtrace
    private static Pattern sBacktraceNotePattern =
            Pattern.compile("[0-9\\-\\s:.]+[A-Z] DEBUG\\s+:\\s+NOTE: .*");
    // Matches one backtrace frame, exactly as tombstone_proto_to_text's print_backtrace
    // Two versions, because we want to exclude the BuildID section if it exists
    private static Pattern sBacktraceFrameWithBuildIdPattern =
            Pattern.compile(
                    "[0-9\\-\\s:.]+[A-Z] DEBUG\\s+:\\s+#[0-9]+ pc [0-9a-fA-F]+  "
                            + "(?<filename>[^\\s]+)(\\s+\\((?<method>.*)\\))?"
                            + "\\s+\\(BuildId: .*\\)");
    private static Pattern sBacktraceFrameWithoutBuildIdPattern =
            Pattern.compile(
                    "[0-9\\-\\s:.]+[A-Z] DEBUG\\s+:\\s+#[0-9]+ pc [0-9a-fA-F]+  "
                            + "(?<filename>[^\\s]+)(\\s+\\((?<method>.*)\\))?");

    public static final String SIGSEGV = "SIGSEGV";
    public static final String SIGBUS = "SIGBUS";
    public static final String SIGABRT = "SIGABRT";

    /**
     * returns the filename of the process.
     * e.g. "/system/bin/mediaserver" returns "mediaserver"
     */
    public static String getProcessFileName(JSONObject crash) throws JSONException {
        return new File(crash.getString(PROCESS)).getName();
    }

    /**
     * Determines if the given input has a {@link com.android.compatibility.common.util.Crash} that
     * should fail an sts test
     *
     * @param crashes list of crashes to check
     * @param config crash detection configuration object
     * @return if a crash is serious enough to fail an sts test
     */
    public static boolean securityCrashDetected(JSONArray crashes, Config config) {
        return matchSecurityCrashes(crashes, config).length() > 0;
    }

    public static BigInteger getBigInteger(JSONObject source, String name) throws JSONException {
        if (source.isNull(name)) {
            return null;
        }
        String intString = source.getString(name);
        BigInteger value = null;
        try {
            value = new BigInteger(intString, 16);
        } catch (NumberFormatException e) {}
        return value;
    }

    /**
     * Determines which given inputs have a {@link com.android.compatibility.common.util.Crash} that
     * should fail an sts test
     *
     * @param crashes list of crashes to check
     * @param config crash detection configuration object
     * @return the list of crashes serious enough to fail an sts test
     */
    public static JSONArray matchSecurityCrashes(JSONArray crashes, Config config) {
        JSONArray securityCrashes = new JSONArray();
        for (int i = 0; i < crashes.length(); i++) {
            try {
                JSONObject crash = crashes.getJSONObject(i);

                // match process patterns
                if (!matchesAny(getProcessFileName(crash), config.processPatterns)) {
                    continue;
                }

                // match signal
                String crashSignal = crash.getString(SIGNAL);
                if (!config.signals.contains(crashSignal)) {
                    continue;
                }

                if (crash.has(ABORT_MESSAGE)) {
                    String crashAbortMessage = crash.getString(ABORT_MESSAGE);
                    if (!config.abortMessageIncludes.isEmpty()) {
                        if (!config.abortMessageIncludes.stream()
                                .filter(p -> p.matcher(crashAbortMessage).find())
                                .findFirst()
                                .isPresent()) {
                            continue;
                        }
                    }
                    if (config.abortMessageExcludes.stream()
                            .filter(p -> p.matcher(crashAbortMessage).find())
                            .findFirst()
                            .isPresent()) {
                        continue;
                    }
                }

                // if check specified, reject crash if address is unlikely to be security-related
                if (config.checkMinAddress) {
                    BigInteger faultAddress = getBigInteger(crash, FAULT_ADDRESS);
                    if (faultAddress != null
                            && faultAddress.compareTo(config.minCrashAddress) < 0) {
                        continue;
                    }
                }

                JSONArray backtrace = crash.getJSONArray(BACKTRACE);

                /* if backtrace "includes" patterns are present, ignore this crash if there is no
                 * frame that matches any of the patterns
                 */
                List<Config.BacktraceFilterPattern> backtraceIncludes =
                        config.getBacktraceIncludes();
                if (!backtraceIncludes.isEmpty()) {
                    if (!IntStream.range(0, backtrace.length())
                            .mapToObj(j -> backtrace.optJSONObject(j))
                            .flatMap(frame -> backtraceIncludes.stream().map(p -> p.match(frame)))
                            .anyMatch(matched -> matched)) {
                        continue;
                    }
                }

                /* if backtrace "excludes" patterns are present, ignore this crash if there is any
                 * frame that matches any of the patterns
                 */
                List<Config.BacktraceFilterPattern> backtraceExcludes =
                        config.getBacktraceExcludes();
                if (!backtraceExcludes.isEmpty()) {
                    if (IntStream.range(0, backtrace.length())
                            .mapToObj(j -> backtrace.optJSONObject(j))
                            .flatMap(frame -> backtraceExcludes.stream().map(p -> p.match(frame)))
                            .anyMatch(matched -> matched)) {
                        continue;
                    }
                }

                securityCrashes.put(crash);
            } catch (JSONException | NullPointerException e) {}
        }
        return securityCrashes;
    }

    /**
     * returns true if the input matches any of the patterns.
     */
    private static boolean matchesAny(String input, Collection<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(input).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Adds all crashes found in the input as JSONObjects to the given JSONArray */
    public static JSONArray addAllCrashes(String input, JSONArray crashes) {
        Matcher crashBlobFinder = sCrashBlobPattern.matcher(input);
        while (crashBlobFinder.find()) {
            String crashStr = crashBlobFinder.group(0);
            int tid = 0;
            int pid = 0;
            BigInteger faultAddress = null;
            String name = null;
            String process = null;
            String signal = null;
            String abortMessage = null;
            List<BacktraceFrameInfo> backtraceFrames = new ArrayList<BacktraceFrameInfo>();

            Matcher pidtidNameMatcher = sPidtidNamePattern.matcher(crashStr);
            if (pidtidNameMatcher.find()) {
                try {
                    pid = Integer.parseInt(pidtidNameMatcher.group(1));
                } catch (NumberFormatException e) {
                }
                try {
                    tid = Integer.parseInt(pidtidNameMatcher.group(2));
                } catch (NumberFormatException e) {
                }
                name = pidtidNameMatcher.group(3).trim();
                process = pidtidNameMatcher.group(4).trim();
            }

            Matcher faultLineMatcher = sFaultLinePattern.matcher(crashStr);
            if (faultLineMatcher.find()) {
                signal = faultLineMatcher.group(1);
                String faultAddrMatch = faultLineMatcher.group(2);
                if (faultAddrMatch != null) {
                    try {
                        faultAddress = new BigInteger(faultAddrMatch, 16);
                    } catch (NumberFormatException e) {
                    }
                }
            }

            Matcher abortMessageMatcher = sAbortMessagePattern.matcher(crashStr);
            if (abortMessageMatcher.find()) {
                abortMessage = abortMessageMatcher.group(1);
            }

            // Continue on after the crash block to find all the stacktrace entries.
            // The format is from tombstone_proto_to_text.cpp's print_thread_backtrace()
            // This will scan the logcat lines until it finds a line that does not match,
            // or end of log.
            int currentIndex = crashBlobFinder.end();
            while (true) {
                int firstEndline = input.indexOf('\n', currentIndex);
                int secondEndline = input.indexOf('\n', firstEndline + 1);
                currentIndex = secondEndline;
                if (firstEndline == -1 || secondEndline == -1) break;

                String nextLine = input.substring(firstEndline + 1, secondEndline);

                Matcher backtraceNoteMatcher = sBacktraceNotePattern.matcher(nextLine);
                if (backtraceNoteMatcher.matches()) {
                    continue;
                }

                Matcher backtraceFrameWithBuildIdMatcher =
                        sBacktraceFrameWithBuildIdPattern.matcher(nextLine);
                Matcher backtraceFrameWithoutBuildIdMatcher =
                        sBacktraceFrameWithoutBuildIdPattern.matcher(nextLine);

                Matcher backtraceFrameMatcher = null;
                if (backtraceFrameWithBuildIdMatcher.matches()) {
                    backtraceFrameMatcher = backtraceFrameWithBuildIdMatcher;

                } else if (backtraceFrameWithoutBuildIdMatcher.matches()) {
                    backtraceFrameMatcher = backtraceFrameWithoutBuildIdMatcher;

                } else {
                    break;
                }

                backtraceFrames.add(
                        new BacktraceFrameInfo(
                                backtraceFrameMatcher.group("filename"),
                                backtraceFrameMatcher.group("method")));
            }

            try {
                JSONObject crash = new JSONObject();
                crash.put(PID, pid);
                crash.put(TID, tid);
                crash.put(NAME, name);
                crash.put(PROCESS, process);
                crash.put(FAULT_ADDRESS, faultAddress == null ? null : faultAddress.toString(16));
                crash.put(SIGNAL, signal);
                crash.put(ABORT_MESSAGE, abortMessage);
                JSONArray backtrace = new JSONArray();
                for (BacktraceFrameInfo frame : backtraceFrames) {
                    backtrace.put(
                            new JSONObject()
                                    .put(FILENAME, frame.getFilename())
                                    .put(METHOD, frame.getMethod()));
                }
                crash.put(BACKTRACE, backtrace);
                crashes.put(crash);
            } catch (JSONException e) {}
        }
        return crashes;
    }

    public static class BacktraceFrameInfo {
        private final String filename;
        private final String method;

        public BacktraceFrameInfo(String filename, String method) {
            this.filename = filename;
            this.method = method;
        }

        public String getFilename() {
            return this.filename;
        }

        public String getMethod() {
            return this.method;
        }
    }

    public static class Config {
        private boolean checkMinAddress;
        private BigInteger minCrashAddress;
        private List<String> signals;
        private List<Pattern> processPatterns;
        private List<Pattern> abortMessageIncludes;
        private List<Pattern> abortMessageExcludes;
        private List<BacktraceFilterPattern> backtraceIncludes;
        private List<BacktraceFilterPattern> backtraceExcludes;

        public Config() {
            checkMinAddress = true;
            minCrashAddress = MIN_CRASH_ADDR;
            setSignals(SIGSEGV, SIGBUS);
            abortMessageIncludes = new ArrayList<>();
            setAbortMessageExcludes("CHECK_", "CANNOT LINK EXECUTABLE");
            processPatterns = new ArrayList();
            backtraceIncludes = new ArrayList();
            backtraceExcludes = new ArrayList();
        }

        /** Sets the min address. */
        @CanIgnoreReturnValue
        public Config setMinAddress(BigInteger minCrashAddress) {
            this.minCrashAddress = minCrashAddress;
            return this;
        }

        /** Check the min address. */
        @CanIgnoreReturnValue
        public Config checkMinAddress(boolean checkMinAddress) {
            this.checkMinAddress = checkMinAddress;
            return this;
        }

        /** Set the signals. */
        @CanIgnoreReturnValue
        public Config setSignals(String... signals) {
            this.signals = new ArrayList(Arrays.asList(signals));
            return this;
        }

        /** Appends signals. */
        @CanIgnoreReturnValue
        public Config appendSignals(String... signals) {
            Collections.addAll(this.signals, signals);
            return this;
        }

        /** Set the abort message includes. */
        @CanIgnoreReturnValue
        public Config setAbortMessageIncludes(String... abortMessages) {
            this.abortMessageIncludes = new ArrayList<>(toPatterns(abortMessages));
            return this;
        }

        /** Set the abort message includes. */
        @CanIgnoreReturnValue
        public Config setAbortMessageIncludes(Pattern... abortMessages) {
            this.abortMessageIncludes = new ArrayList<>(Arrays.asList(abortMessages));
            return this;
        }

        /** Appends the abort message includes. */
        @CanIgnoreReturnValue
        public Config appendAbortMessageIncludes(String... abortMessages) {
            this.abortMessageIncludes.addAll(toPatterns(abortMessages));
            return this;
        }

        /** Appends the abort message includes. */
        @CanIgnoreReturnValue
        public Config appendAbortMessageIncludes(Pattern... abortMessages) {
            Collections.addAll(this.abortMessageIncludes, abortMessages);
            return this;
        }

        /** Sets the abort message excludes. */
        @CanIgnoreReturnValue
        public Config setAbortMessageExcludes(String... abortMessages) {
            this.abortMessageExcludes = new ArrayList<>(toPatterns(abortMessages));
            return this;
        }

        /** Sets the abort message excludes. */
        @CanIgnoreReturnValue
        public Config setAbortMessageExcludes(Pattern... abortMessages) {
            this.abortMessageExcludes = new ArrayList<>(Arrays.asList(abortMessages));
            return this;
        }

        /** Appends the process patterns. */
        @CanIgnoreReturnValue
        public Config appendAbortMessageExcludes(String... abortMessages) {
            this.abortMessageExcludes.addAll(toPatterns(abortMessages));
            return this;
        }

        /** Appends the abort message excludes. */
        @CanIgnoreReturnValue
        public Config appendAbortMessageExcludes(Pattern... abortMessages) {
            Collections.addAll(this.abortMessageExcludes, abortMessages);
            return this;
        }

        /** Sets the process patterns. */
        @CanIgnoreReturnValue
        public Config setProcessPatterns(String... processPatternStrings) {
            this.processPatterns = new ArrayList<>(toPatterns(processPatternStrings));
            return this;
        }

        /** Sets the process patterns. */
        @CanIgnoreReturnValue
        public Config setProcessPatterns(Pattern... processPatterns) {
            this.processPatterns = new ArrayList(Arrays.asList(processPatterns));
            return this;
        }

        public List<Pattern> getProcessPatterns() {
            return Collections.unmodifiableList(processPatterns);
        }

        /** Appends the process patterns. */
        @CanIgnoreReturnValue
        public Config appendProcessPatterns(String... processPatternStrings) {
            this.processPatterns.addAll(toPatterns(processPatternStrings));
            return this;
        }

        /** Appends the process patterns. */
        @CanIgnoreReturnValue
        public Config appendProcessPatterns(Pattern... processPatterns) {
            Collections.addAll(this.processPatterns, processPatterns);
            return this;
        }

        /** Sets which backtraces should be included. */
        @CanIgnoreReturnValue
        public Config setBacktraceIncludes(BacktraceFilterPattern... patterns) {
            this.backtraceIncludes = new ArrayList<>(Arrays.asList(patterns));
            return this;
        }

        public List<BacktraceFilterPattern> getBacktraceIncludes() {
            return Collections.unmodifiableList(this.backtraceIncludes);
        }

        /** Append which backtraces should be included. */
        @CanIgnoreReturnValue
        public Config appendBacktraceIncludes(BacktraceFilterPattern... patterns) {
            Collections.addAll(this.backtraceIncludes, patterns);
            return this;
        }

        /** Sets which backtraces should be excluded. */
        @CanIgnoreReturnValue
        public Config setBacktraceExcludes(BacktraceFilterPattern... patterns) {
            this.backtraceExcludes = new ArrayList<>(Arrays.asList(patterns));
            return this;
        }

        public List<BacktraceFilterPattern> getBacktraceExcludes() {
            return Collections.unmodifiableList(this.backtraceExcludes);
        }

        /** Appends which backtraces should be excluded. */
        @CanIgnoreReturnValue
        public Config appendBacktraceExcludes(BacktraceFilterPattern... patterns) {
            Collections.addAll(this.backtraceExcludes, patterns);
            return this;
        }

        /**
         * A utility class that contains patterns to filter backtraces on.
         *
         * <p>A filter matches if any of the backtrace frame matches any of the patterns.
         *
         * <p>Either filenamePattern or methodPattern can be null, in which case it will act like a
         * wildcard pattern and matches anything.
         *
         * <p>A null filename or method name will not match any non-null pattern.
         */
        public static class BacktraceFilterPattern {
            private final Pattern filenamePattern;
            private final Pattern methodPattern;

            /**
             * Constructs a BacktraceFilterPattern with the given file and method name patterns.
             *
             * <p>Null patterns are interpreted as wildcards and match anything.
             *
             * @param filenamePattern Regex string for the filename pattern. Can be null.
             * @param methodPattern Regex string for the method name pattern. Can be null.
             */
            public BacktraceFilterPattern(String filenamePattern, String methodPattern) {
                if (filenamePattern == null) {
                    this.filenamePattern = null;
                } else {
                    this.filenamePattern = Pattern.compile(filenamePattern);
                }

                if (methodPattern == null) {
                    this.methodPattern = null;
                } else {
                    this.methodPattern = Pattern.compile(methodPattern);
                }
            }

            /** Returns true if the current patterns match a backtrace frame. */
            public boolean match(JSONObject frame) {
                if (frame == null) return false;

                String filename = frame.optString(FILENAME);
                String method = frame.optString(METHOD);

                boolean filenameMatches =
                        (filenamePattern == null
                                || (filename != null && filenamePattern.matcher(filename).find()));
                boolean methodMatches =
                        (methodPattern == null
                                || (method != null && methodPattern.matcher(method).find()));
                return filenameMatches && methodMatches;
            }
        }
    }

    private static List<Pattern> toPatterns(String... patternStrings) {
        return Stream.of(patternStrings).map(Pattern::compile).collect(Collectors.toList());
    }
}
