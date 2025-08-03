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

package com.android.phone.callcomposer;

public class ImageData {
    private final byte[] mImageBytes;
    private final String mMimeType;

    private String mId;

    public ImageData(byte[] imageBytes, String mimeType, String id) {
        mImageBytes = imageBytes;
        mMimeType = mimeType;
        mId = id;
    }

    public byte[] getImageBytes() {
        return mImageBytes;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }
}
