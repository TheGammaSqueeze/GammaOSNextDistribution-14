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

package android.sensorprivacy.cts

import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.camera2.CameraManager
import com.android.compatibility.common.util.CddTest
import org.junit.Assume

@CddTest(requirement = "9.8.13/C-1-1,C-1-2")
class SensorPrivacyCameraTest : SensorPrivacyBaseTest(CAMERA, USE_CAM_EXTRA) {

    override fun init() {
        val cameraManager: CameraManager = context.getSystemService(CameraManager::class.java)!!
        Assume.assumeTrue("No camera available", cameraManager.cameraIdList.isNotEmpty())
        super.init()
    }
}
