/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.musicrecognition.cts2;

import android.media.AudioFormat;
import android.media.musicrecognition.MusicRecognitionService;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

/** No-op implementation of MusicRecognitionService for testing purposes. */
public class OutsideOfPackageService extends MusicRecognitionService {

    @Override
    public void onRecognize(@NonNull ParcelFileDescriptor stream,
            @NonNull AudioFormat audioFormat,
            @NonNull Callback callback) {
        throw new RuntimeException("unexpected call to onRecognize!");
    }
}
