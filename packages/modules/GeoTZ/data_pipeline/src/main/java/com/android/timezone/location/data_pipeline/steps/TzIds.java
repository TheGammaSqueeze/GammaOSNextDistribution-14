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

import java.io.File;

/** Utility methods associated with IANA TZDB time zone IDs. */
public final class TzIds {

    private TzIds() {
    }

    /** Generates a {@link File} for a supplied arguments for writing to. */
    public static File createFile(File outputDir, String tzId, String fileSuffix) {
        String tzIdName = fileNameSafe(tzId);
        return new File(outputDir, tzIdName + fileSuffix);
    }

    /** Turns {@code tzId} into a string that is suitable for use in a file name. */
    public static String fileNameSafe(String tzId) {
        return tzId.replace('/', '_');
    }
}
