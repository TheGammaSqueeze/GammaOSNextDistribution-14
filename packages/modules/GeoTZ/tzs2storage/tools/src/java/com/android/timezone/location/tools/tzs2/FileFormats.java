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

package com.android.timezone.location.tools.tzs2;

import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;

/** Some sample file formats. */
final class FileFormats {

    private static final TzS2RangeFileFormat FILE_FORMAT_12 =
            new TzS2RangeFileFormat(12, 11, 16, 1, 32, 11);

    private static final TzS2RangeFileFormat FILE_FORMAT_14 =
            new TzS2RangeFileFormat(14, 13, 18, 1, 32, 12);

    private static final TzS2RangeFileFormat FILE_FORMAT_16 =
            new TzS2RangeFileFormat(16, 13, 22, 1, 40, 12);

    /** Maps an S2 level to one of the file format constants declared on by class. */
    static TzS2RangeFileFormat getFileFormatForLevel(int s2Level) {
        switch (s2Level) {
            case 12:
                return FILE_FORMAT_12;
            case 14:
                return FILE_FORMAT_14;
            case 16:
                return FILE_FORMAT_16;
            default:
                throw new IllegalArgumentException("s2Level=" + s2Level + " not mapped");
        }
    }
}
