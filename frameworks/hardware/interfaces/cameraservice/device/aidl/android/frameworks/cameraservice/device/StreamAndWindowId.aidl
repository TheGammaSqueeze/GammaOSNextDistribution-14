/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.frameworks.cameraservice.device;

/**
 * Data structure tying stream id and window id for a native window.
 */
@VintfStability
parcelable StreamAndWindowId {
    /**
     * This must be the stream id corresponding to the native window (the streamId
     * returned from the createStream() method, which took in the
     * OutputConfiguration which contained this native window)
     */
    int streamId;
    /**
     * This must be the array index of the of the window handle corresponding to
     * the native window, which was packaged with the OutputConfiguration.
     */
    int windowId;
}
