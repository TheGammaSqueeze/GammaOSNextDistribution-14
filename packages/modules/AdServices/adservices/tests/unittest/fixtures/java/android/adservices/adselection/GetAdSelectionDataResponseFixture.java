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

package android.adservices.adselection;

import android.adservices.common.AssetFileDescriptorUtil;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class GetAdSelectionDataResponseFixture {
    /**
     * Returns a {@link GetAdSelectionDataResponse} with an {@link
     * android.content.res.AssetFileDescriptor} set up with the provided buffer.
     */
    public static GetAdSelectionDataResponse getAdSelectionDataResponseWithAssetFileDescriptor(
            int adSelectionId, byte[] buffer, ExecutorService executorService) throws IOException {
        return new GetAdSelectionDataResponse.Builder()
                .setAdSelectionId(adSelectionId)
                .setAssetFileDescriptor(
                        AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(
                                buffer, executorService))
                .build();
    }

    /**
     * Returns a {@link GetAdSelectionDataResponse} while setting {@code buffer} as the
     * adSelectionData
     */
    public static GetAdSelectionDataResponse getAdSelectionDataResponseWithByteArray(
            int adSelectionId, byte[] buffer) throws IOException {
        return new GetAdSelectionDataResponse.Builder()
                .setAdSelectionId(adSelectionId)
                .setAdSelectionData(buffer)
                .build();
    }
}
