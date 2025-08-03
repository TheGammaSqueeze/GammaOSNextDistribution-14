/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.verifier.sharesheet;

import android.net.Uri;

public interface TestContract {
    interface Keys {
        String AdditionalContent = "com.android.cts.verifier.sharesheet.ADDITIONAL_CONTENT";
        String CursorStartPos = "com.android.cts.verifier.sharesheet.CURSOR_START_POS";
        String LaunchId = "com.android.cts.verifier.sharesheet.LAUNCH_ID";
        String Result = "com.android.cts.verifier.sharesheet.RESULT";
    }

    interface UriParams {
        String Name = "name";
        String Type = "type";
    }

    interface Uris {
        Uri ImageBaseUri = Uri.parse("content://com.android.cts.verifier.sharesheet.images/image");
        Uri ExtraContentUri =
                Uri.parse("content://com.android.cts.verifier.sharesheet.extracontent");
    }

    interface LogTags {
        String TAG = "SharesheetTest";
    }
}
