/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.nene.logcat;

import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Retry;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * TestApis related to logcat.
 */
public final class Logcat {

    private static final String LOG_TAG = "Nene.Logcat";

    public static final Logcat sInstance = new Logcat();

    private Logcat() {

    }

    /** Clear the logcat buffer. */
    @Experimental
    public void clear() {
        try {
            Retry.logic(() ->
                            ShellCommand.builder("logcat")
                                    .addOperand("-c")
                                    .validate(String::isEmpty)
                                    .executeOrThrowNeneException("Error clearing logcat buffer"))
                    .timeout(Duration.ofSeconds(10))
                    .run();
        } catch (Throwable e) {
            // Clearing is best effort - don't disrupt the test because we can't
            Log.e(LOG_TAG, "Error clearing logcat", e);
        }
    }

    /**
     * Get an instant dump from logcat, filtered by {@code lineFilter}.
     */
    public String dump(Predicate<String> lineFilter) {
        try (ShellCommandUtils.StreamingShellOutput sso = dump()){
            String log = sso.stream().filter(lineFilter)
                    .collect(Collectors.joining("\n"));

            // We only take the last 500 characters - this can be relaxed once we properly block
            // out the start and end of the time we care about
            return log.substring(Math.max(0, log.length() - 500));
        } catch (IOException e) {
            throw new NeneException("Error dumping logcat", e);
        }
    }

    /**
     * Get an instant dump from logcat.
     *
     * <p>Note that this might include a lot of data which could cause memory issues if stored
     * in a String.
     *
     * <p>Make sure you close the {@link ShellCommandUtils.StreamingShellOutput} after reading
     */
    public ShellCommandUtils.StreamingShellOutput dump() {
        try {
            return ShellCommandUtils.executeCommandForStream(
                    "logcat -d", /* /* stdInBytes= */ null
            );
        } catch (AdbException e) {
            throw new NeneException("Error dumping logcat", e);
        }
    }

    /**
     * Find a system server exception in logcat matching the passed in {@link Throwable}.
     *
     * <p>If there is any problem finding a matching exception, or if the exception is not found,
     * then {@code null} will be returned.
     */
    public SystemServerException findSystemServerException(Throwable t) {
        List<SystemServerException> exceptions = findSystemServerExceptions(t);
        if (exceptions.isEmpty()) {
            return null;
        }
        return exceptions.get(exceptions.size() - 1);
    }

    /**
     * Get the most recent log from logcat matching the {@code lineFilter}.
     */
    public String recent(Predicate<String> lineFilter) {
        String[] logs = dump(lineFilter).split("\n");
        return logs[logs.length - 1];
    }

    private List<SystemServerException> findSystemServerExceptions(Throwable t) {
        List<SystemServerException> exceptions = new ArrayList<>();

        try (ShellCommandUtils.StreamingShellOutput sso = dump()){
            Iterator<String> lines = sso.stream().iterator();

            while (true) {
                String nextline = lines.next();
                if (nextline.contains(
                        "Caught a RuntimeException from the binder stub implementation.")) {

                    String binderPrefix = lines.next();

                    // First split to remove the Binder prefix
                    String exceptionTitle = binderPrefix.split(": ", 2)[1];
                    String[] exceptionTitlePaths = exceptionTitle.split(": ", 2);
                    String exceptionClass = exceptionTitlePaths[0];
                    String exceptionMessage = exceptionTitlePaths[1];

                    if (exceptionClass.equals(t.getClass().getName())) {
                        if (exceptionMessage.equals(t.getMessage())) {
                            List<String> traceLines = new ArrayList<>();

                            while (true) {
                                String traceLine = lines.next();

                                if (traceLine.contains("W Binder  : ")) {
                                    traceLines.add(traceLine.split(
                                            "W Binder  : ", 2)[1].strip());
                                } else {
                                    // This means we will miss if two traces are right after each
                                    // other as we lose this line - but probably not a huge deal...
                                    break;
                                }
                            }

                            exceptions.add(extractStackTraceFromStrings(
                                    exceptionClass, exceptionMessage, traceLines, t));
                        }
                    }
                }
            }
        } catch (NoSuchElementException e) {
            // Finished reading
            return exceptions;
        } catch (RuntimeException | IOException e) {
            // Any issues we will just return nothing so we don't hide a real exception
            Log.e(LOG_TAG, "Error finding system server exception", e);
            return exceptions;
        }
    }

    private SystemServerException extractStackTraceFromStrings(
            String exceptionClass, String exceptionMessage, List<String> traceLines,
            Throwable cause) {
            StackTraceElement[] traceElements =
                    traceLines.stream().map(
                            this::extractStackTraceElement).toArray(StackTraceElement[]::new);
            return new SystemServerException(
                    exceptionClass, exceptionMessage, traceElements, cause);
    }

    private StackTraceElement extractStackTraceElement(String line) {
        String element = line.split("at ", 2)[1];
        String[] elementParts = element.split("\\(");
        String className = elementParts[0];
        int methodNameSeparator = className.lastIndexOf(".");
        if (methodNameSeparator == -1) {
            throw new IllegalStateException("Could not parse " + line);
        }
        String methodName = className.substring(methodNameSeparator + 1);
        className = className.substring(0, methodNameSeparator);

        String[] fileParts =
                elementParts[1].substring(
                        0, elementParts[1].length() - 1).split(":", 2);

        return new StackTraceElement(
                className, methodName, fileParts[0], Integer.parseInt(fileParts[1]));
    }

    /**
     * Begin listening for a particular entry in logcat.
     *
     * <p>Example usage:
     *
     * try (BlockingLogcatListener l = TestApis.logcat().listen(l -> l.contains("line")) {
     *     // Some code which will cause line to appear in the output
     * } // this will block until line appears
     */
    @Experimental
    public BlockingLogcatListener listen(Predicate<String> lineFilter) {
        // TODO: Replace this with actually filtering on an ongoing basis - so we don't clear
        // the logcat and so it can run longer than a single logcat buffer
        TestApis.logcat().clear();

        return new BlockingLogcatListener(lineFilter);
    }
}
