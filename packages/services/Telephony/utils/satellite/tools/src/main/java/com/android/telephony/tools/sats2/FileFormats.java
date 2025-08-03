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

package com.android.telephony.tools.sats2;

import com.android.telephony.sats2range.read.SatS2RangeFileFormat;

/** Some sample file formats. */
public final class FileFormats {

    // level 12: 27 S2 cell ID bits split 11 + 16,
    // suffix table: 24 bits, 16/24 for cell id suffix, 8/24 dedicated to range
    private static final SatS2RangeFileFormat FILE_FORMAT_12_ALLOWED_LIST =
            new SatS2RangeFileFormat(12, 11, 16, 1, 24, true);
    private static final SatS2RangeFileFormat FILE_FORMAT_12_DISALLOWED_LIST =
            new SatS2RangeFileFormat(12, 11, 16, 1, 24, false);

    // level 14: 31 S2 cell ID bits split 13 + 18,
    // suffix table: 32 bits, 18/32 for cell id suffix, 14/32 dedicated to range
    private static final SatS2RangeFileFormat FILE_FORMAT_14_ALLOWED_LIST =
            new SatS2RangeFileFormat(14, 13, 18, 1, 32, true);
    private static final SatS2RangeFileFormat FILE_FORMAT_14_DISALLOWED_LIST =
            new SatS2RangeFileFormat(14, 13, 18, 1, 32, false);

    // level 16: 35 S2 cell ID bits split 13 + 22,
    // suffix table: 32 bits, 22/32 for cell id suffix, 10/32 dedicated to range
    private static final SatS2RangeFileFormat FILE_FORMAT_16_ALLOWED_LIST =
            new SatS2RangeFileFormat(16, 13, 22, 1, 32, true);
    private static final SatS2RangeFileFormat FILE_FORMAT_16_DISALLOWED_LIST =
            new SatS2RangeFileFormat(16, 13, 22, 1, 32, false);

    /** Maps an S2 level to one of the file format constants declared on by class. */
    public static SatS2RangeFileFormat getFileFormatForLevel(int s2Level, boolean isAllowedList) {
        switch (s2Level) {
            case 12:
                return isAllowedList ? FILE_FORMAT_12_ALLOWED_LIST : FILE_FORMAT_12_DISALLOWED_LIST;
            case 14:
                return isAllowedList ? FILE_FORMAT_14_ALLOWED_LIST : FILE_FORMAT_14_DISALLOWED_LIST;
            case 16:
                return isAllowedList ? FILE_FORMAT_16_ALLOWED_LIST : FILE_FORMAT_16_DISALLOWED_LIST;
            default:
                throw new IllegalArgumentException("s2Level=" + s2Level
                        + ", isAllowedList=" + isAllowedList + " not mapped");
        }
    }
}
