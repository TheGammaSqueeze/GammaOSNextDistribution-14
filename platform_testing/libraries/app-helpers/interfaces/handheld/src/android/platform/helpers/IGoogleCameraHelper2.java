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

package android.platform.helpers;

public interface IGoogleCameraHelper2 extends IAppHelper {

    /** Setup expectations: Starts taking multiple photos on camera. */
    public void takeMultiplePhotos(int count, long takePhotoDelay);

    /** Setup expectations: Switch to video record mode. */
    public void clickVideoTab();

    /** Setup expectations: Click camera video button to start recording or stop recording. */
    public void clickCameraVideoButton();
}
