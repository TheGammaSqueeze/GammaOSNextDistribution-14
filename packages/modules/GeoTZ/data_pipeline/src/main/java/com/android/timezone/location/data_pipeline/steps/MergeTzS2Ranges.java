/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.timezone.location.data_pipeline.steps;

import com.android.timezone.location.common.LicenseSupport;
import com.android.timezone.location.common.LicenseSupport.License;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Range;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Ranges;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * An executable class that takes a set of {@link TzS2Ranges} files (usually expected to have a
 * single associated time zone ID) and merges them into a single {@link TzS2Ranges}.
 * See {@link #main(String[])} for usage.
 */
public final class MergeTzS2Ranges {

    private final File mInputDir;

    private final ExecutorService mExecutorService;

    private final File mWorkingDir;

    private final File mOutputFile;

    private final ProtoStorageFormat mProtoStorageFormat;

    private MergeTzS2Ranges(
            File inputDir, ExecutorService executorService, File workingDir, File outputFile,
            ProtoStorageFormat protoStorageFormat) {
        this.mInputDir = Objects.requireNonNull(inputDir);
        this.mExecutorService = Objects.requireNonNull(executorService);
        this.mWorkingDir = Objects.requireNonNull(workingDir);
        this.mOutputFile = Objects.requireNonNull(outputFile);
        this.mProtoStorageFormat = Objects.requireNonNull(protoStorageFormat);
    }

    private static class Arguments {

        @Parameter(names = "--input",
                description = "The input directory containing the TzS2Ranges files",
                required = true,
                converter = FileConverter.class)
        public File inputDir;

        @Parameter(names = "--num-threads",
                description = "The number of threads to use",
                required = true)
        public int numThreads;

        @Parameter(names = "--working-dir",
                description = "A working dir(for storage of intermediate files)",
                required = true,
                converter = FileConverter.class)
        public File workingDir;

        @Parameter(names = "--output-file",
                description = "The output file to store the combined TzS2Ranges file",
                required = true,
                converter = FileConverter.class)
        public File outputFile;
    }

    /**
     * See {@link MergeTzS2Ranges} for the purpose of this class.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        File inputDir = arguments.inputDir;
        int threads = arguments.numThreads;
        File workingDir = arguments.workingDir;
        File outputFile = arguments.outputFile;
        ProtoStorageFormat protoStorageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        MergeTzS2Ranges converter = new MergeTzS2Ranges(
                inputDir, executorService, workingDir, outputFile, protoStorageFormat);

        try {
            converter.execute();
        } finally {
            System.out.println("Waiting for shutdown");
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void execute() throws Exception {
        LicenseSupport.copyLicenseFile(mInputDir, mWorkingDir);

        List<File> tzS2RangeFiles = TzS2Ranges.listFiles(mInputDir, mProtoStorageFormat);
        File combinedFile = createCombinedTzS2Ranges(tzS2RangeFiles);
        Files.copy(combinedFile.toPath(), mOutputFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Output file: " + mOutputFile);
        LicenseSupport.copyLicenseFile(mInputDir, mOutputFile.getParentFile());
    }

    /**
     * Combines all input ranges into a single list, assumes they start out sorted. The output will
     * be sorted.
     */
    private File createCombinedTzS2Ranges(List<File> tzS2RangeFiles) {
        // This method is implemented as a parallel merge sort. All input files are added to the
        // workQueue. Pairs of files are taken off the work queue and submitted to the
        // executorService. When the result of merging the pairs is available, that result is
        // added back to the workQueue. When there's no work in progress and there's a single
        // result, then we're done.

        ArrayDeque<File> workQueue = new ArrayDeque<>();
        for (File tzS2RangesFile : tzS2RangeFiles) {
            workQueue.addLast(tzS2RangesFile);
        }

        // Keep a counter to generate unique IDs for intermediate files.
        int intermediateFileCounter = 0;
        LinkedList<Future<File>> resultFutureList = new LinkedList<>();
        while (!(workQueue.size() == 1 && resultFutureList.isEmpty())) {
            // While workQueue.size() > 1 files to process, grab pairs of files and submit them to
            // the executorService. Put the result future on the resultFutureList.
            while (workQueue.size() > 1) {
                File leftFile = workQueue.removeFirst();
                File rightFile = workQueue.removeFirst();
                int fileNumber = intermediateFileCounter++;
                File mergedFile = new File(
                        mWorkingDir, fileNumber + TzS2Ranges.getFileSuffix(mProtoStorageFormat));
                System.out.println("Left=" + leftFile.getName()
                        + ", right=" + rightFile.getName()
                        + ", mergedFile=" + mergedFile.getName());
                Future<File> resultFuture = mExecutorService.submit(
                        () -> mergeRangeLists(leftFile, rightFile, mergedFile));
                resultFutureList.addLast(resultFuture);
            }

            if (!resultFutureList.isEmpty()) {
                boolean foundResult = false;
                Iterator<Future<File>> resultIterator = resultFutureList.iterator();
                while (resultIterator.hasNext()) {
                    // Check the resultFutureList to see if any of the futures are done. If so,
                    // the result will may mean more work to do, so we add the result to the
                    // workQueue.
                    Future<File> resultFuture = resultIterator.next();
                    if (resultFuture.isDone()) {
                        foundResult = true;
                        File result;
                        try {
                            result = resultFuture.get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new IllegalStateException(e);
                        }
                        resultIterator.remove();
                        workQueue.add(result);
                    }
                }
                if (!foundResult) {
                    // If no results were ready, wait for a while to avoid a tight loop.
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        return workQueue.removeFirst();
    }

    /**
     * A modified merge sort for two lists of ranges. Ranges that overlap are combined and split
     * into up to three sub-ranges as needed.
     */
    private File mergeRangeLists(File leftFile, File rightFile, File outputFile)
            throws IOException {
        TzS2Ranges left = TzS2Ranges.load(leftFile, mProtoStorageFormat);
        TzS2Ranges right = TzS2Ranges.load(rightFile, mProtoStorageFormat);
        List<TzS2Range> outputList = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            TzS2Range leftEntry = left.get(i);
            TzS2Range rightEntry = right.get(j);
            if (leftEntry.rangeEnd.lessOrEquals(rightEntry.rangeStart)) {
                outputList.add(leftEntry);
                i++;
            } else if (rightEntry.rangeEnd.lessOrEquals(leftEntry.rangeStart)) {
                outputList.add(rightEntry);
                j++;
            } else {
                // Two ranges overlap. Merge them.

                // Trim off any leading part that doesn't match.
                if (leftEntry.rangeStart.lessThan(rightEntry.rangeStart)) {
                    TzS2Range leftLead = new TzS2Range(leftEntry.tzIds, leftEntry.rangeStart,
                            rightEntry.rangeStart);
                    outputList.add(leftLead);
                    leftEntry = new TzS2Range(leftEntry.tzIds, rightEntry.rangeStart,
                            leftEntry.rangeEnd);
                } else if (rightEntry.rangeStart.lessThan(leftEntry.rangeStart)) {
                    TzS2Range rightLead = new TzS2Range(rightEntry.tzIds, rightEntry.rangeStart,
                            leftEntry.rangeStart);
                    outputList.add(rightLead);
                    rightEntry = new TzS2Range(rightEntry.tzIds, leftEntry.rangeStart,
                            rightEntry.rangeEnd);
                }

                // Now deal with the parts that overlap.
                List<String> combinedTzIds = new ArrayList<>(
                        leftEntry.tzIds.size() + rightEntry.tzIds.size());
                combinedTzIds.addAll(leftEntry.tzIds);
                combinedTzIds.addAll(rightEntry.tzIds);

                // Sort the combinedTzIds to ensure the output is deterministic.
                Collections.sort(combinedTzIds);

                if (leftEntry.rangeEnd.lessThan(rightEntry.rangeEnd)) {
                    // Consume the left entry entirely. Replace the right with whatever remains.
                    i++;
                    outputList.add(new TzS2Range(combinedTzIds, leftEntry.rangeStart,
                            leftEntry.rangeEnd));
                    right.replace(j, new TzS2Range(rightEntry.tzIds, leftEntry.rangeEnd,
                            rightEntry.rangeEnd));
                } else if (rightEntry.rangeEnd.lessThan(leftEntry.rangeEnd)) {
                    // Consume the right entry entirely. Replace the left with whatever remains.
                    j++;
                    outputList.add(new TzS2Range(combinedTzIds, rightEntry.rangeStart,
                            rightEntry.rangeEnd));
                    left.replace(i, new TzS2Range(leftEntry.tzIds, rightEntry.rangeEnd,
                            leftEntry.rangeEnd));
                } else {
                    // Identical ranges: Consume both.
                    i++;
                    j++;
                    outputList.add(new TzS2Range(combinedTzIds, leftEntry.rangeStart,
                            leftEntry.rangeEnd));
                }
            }
        }

        // Copy any left entries remaining.
        while (i < left.size()) {
            outputList.add(left.get(i));
            i++;
        }

        // Copy any right entries remaining.
        while (j < right.size()) {
            outputList.add(right.get(j));
            j++;
        }

        TzS2Ranges outputRanges = new TzS2Ranges(outputList);
        TzS2Ranges.store(outputRanges, outputFile, mProtoStorageFormat, License.ODBL);
        return outputFile;
    }
}
