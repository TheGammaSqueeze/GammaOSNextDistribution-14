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
import com.android.timezone.location.data_pipeline.steps.Types.TzS2CellUnion;

import com.google.common.geometry.S2CellId;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * An executable class that prints basic stats about all "_tzs2cellunion.prototxt" files in a
 * directory. See {@link #main(String[])} for usage.
 */
public final class PrintTzS2CellUnionStats {

    private final File mInputDir;

    private final ProtoStorageFormat mStorageFormat;

    private PrintTzS2CellUnionStats(File inputDir, ProtoStorageFormat storageFormat) {
        this.mInputDir = Objects.requireNonNull(inputDir);
        this.mStorageFormat = Objects.requireNonNull(storageFormat);
    }

    /**
     * See {@link PrintTzS2CellUnionStats} for the purpose of this class.
     *
     * <p>Arguments:
     * <ol>
     *     <li>The input directory containing {@link TzS2CellUnion} files</li>
     * </ol>
     */
    public static void main(String[] args) throws Exception {
        File inputDir = new File(args[0]);
        ProtoStorageFormat storageFormat = Types.DEFAULT_PROTO_STORAGE_FORMAT;

        new PrintTzS2CellUnionStats(inputDir, storageFormat).printS2CellStats();
    }

    private boolean printS2CellStats() {
        List<TzS2CellUnion> cellUnions = createTzS2CellUnions(mInputDir, mStorageFormat);
        List<String> strings = new ArrayList<>();
        for (TzS2CellUnion cellUnion : cellUnions) {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add(cellUnion.tzId);
            int[] cellLevelCount = new int[30];
            for (S2CellId cellId : cellUnion.s2CellUnion.cellIds()) {
                cellLevelCount[cellId.level()]++;
            }
            for (int i = 0; i < cellLevelCount.length; i++) {
                joiner.add(Integer.toString(cellLevelCount[i]));
            }
            strings.add(joiner.toString());
        }
        strings.sort(Comparator.naturalOrder());
        strings.forEach(System.out::println);
        return true;
    }

    private List<TzS2CellUnion> createTzS2CellUnions(File dir, ProtoStorageFormat storageFormat) {
        List<File> files = TzS2CellUnion.listFiles(dir, storageFormat);
        List<TzS2CellUnion> tzS2CellUnions = new ArrayList<>();
        for (File file : files) {
            tzS2CellUnions.add(TzS2CellUnion.load(file, storageFormat));
        }
        return tzS2CellUnions;
    }
}
