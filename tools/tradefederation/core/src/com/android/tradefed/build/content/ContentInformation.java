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
package com.android.tradefed.build.content;

import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;

/** Represents the content for a given build target of its base and current version. */
public class ContentInformation {
    public final File baseContent;
    public final String baseBuildId;
    public final File currentContent;
    public final String currentBuildId;

    public ContentInformation(
            File baseContent, String baseBuildId, File currentContent, String currentBuildId) {
        this.baseContent = baseContent;
        this.baseBuildId = baseBuildId;
        this.currentContent = currentContent;
        this.currentBuildId = currentBuildId;
    }

    public void clean() {
        FileUtil.deleteFile(baseContent);
        FileUtil.deleteFile(currentContent);
    }

    /** Deep copy the {@link ContentInformation} and the underlying files if they exists. */
    public ContentInformation cloneInformation() throws IOException {
        File baseClone = null;
        if (baseContent != null) {
            baseClone =
                    FileUtil.createTempFile(
                            FileUtil.getBaseName(baseContent.getName()),
                            FileUtil.getExtension(baseContent.getName()));
            baseClone.delete();
            FileUtil.hardlinkFile(baseContent, baseClone);
        }
        File currentClone = null;
        if (currentContent != null) {
            currentClone =
                    FileUtil.createTempFile(
                            FileUtil.getBaseName(currentContent.getName()),
                            FileUtil.getExtension(currentContent.getName()));
            currentClone.delete();
            FileUtil.hardlinkFile(currentContent, currentClone);
        }
        return new ContentInformation(baseClone, baseBuildId, currentClone, currentBuildId);
    }
}
