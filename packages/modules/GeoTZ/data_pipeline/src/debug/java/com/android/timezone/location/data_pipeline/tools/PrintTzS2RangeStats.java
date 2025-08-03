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

package com.android.timezone.location.data_pipeline.tools;

import com.android.timezone.location.data_pipeline.steps.Types;
import com.android.timezone.location.data_pipeline.steps.Types.ProtoStorageFormat;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Range;
import com.android.timezone.location.data_pipeline.steps.Types.TzS2Ranges;

import java.io.File;
import java.util.Objects;

/**
 * An executable class that prints basic stats about all "_tzs2ranges.prototxt" files in a
 * directory. See {@link #main(String[])} for usage.
 */
public final class PrintTzS2RangeStats {

    private final File mInputFile;

    private final ProtoStorageFormat mStorageFormat;

    private PrintTzS2RangeStats(File inputFile, ProtoStorageFormat storageFormat) {
        this.mInputFile = Objects.requireNonNull(inputFile);
        this.mStorageFormat = Objects.requireNonNull(storageFormat);
    }

    /**
     * See {@link PrintTzS2RangeStats} for the purpose of this class.
     *
     * <p>Arguments:
     * <ol>
     *     <li>The input directory containing {@link TzS2Ranges} files</li>
     * </ol>
     */
    public static void main(String[] args) throws Exception {
        File inputFile = new File(args[0]);
        ProtoStorageFormat storageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        new PrintTzS2RangeStats(inputFile, storageFormat).printMergedRangeCounts();
    }

    private void printMergedRangeCounts() {
        TzS2Ranges ranges = TzS2Ranges.load(mInputFile, mStorageFormat);
        printTzS2RangeCounts(ranges);
    }

    private void printTzS2RangeCounts(TzS2Ranges ranges) {
        long singleCellRanges = 0;
        long multipleCellRanges = 0;
        long singleTzCells = 0;
        long multiTzCells = 0;
        for (TzS2Range range : ranges.getAll()) {
            long rangeSize = range.size();
            if (rangeSize == 1) {
                singleCellRanges++;
            } else {
                multipleCellRanges++;
            }

            if (range.tzIds.size() == 1) {
                singleTzCells += rangeSize;
            } else {
                multiTzCells += rangeSize;
            }
        }
        System.out.println("Single cell ranges," + singleCellRanges);
        System.out.println("Multiple cell ranges," + multipleCellRanges);
        System.out.println("Total ranges," + (singleCellRanges + multipleCellRanges));

        System.out.println("Single tz cells, " + singleTzCells);
        System.out.println("Multi tz cells, " + multiTzCells);
    }
}
