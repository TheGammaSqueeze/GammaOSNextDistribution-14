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

package com.android.intentresolver.contentpreview;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;

@Retention(SOURCE)
@IntDef({ContentPreviewType.CONTENT_PREVIEW_FILE,
        ContentPreviewType.CONTENT_PREVIEW_IMAGE,
        ContentPreviewType.CONTENT_PREVIEW_TEXT,
        ContentPreviewType.CONTENT_PREVIEW_PAYLOAD_SELECTION})
public @interface ContentPreviewType {
    // Starting at 1 since 0 is considered "undefined" for some of the database transformations
    // of tron logs.
    int CONTENT_PREVIEW_IMAGE = 1;
    int CONTENT_PREVIEW_FILE = 2;
    int CONTENT_PREVIEW_TEXT = 3;
    int CONTENT_PREVIEW_PAYLOAD_SELECTION = 4;
}
