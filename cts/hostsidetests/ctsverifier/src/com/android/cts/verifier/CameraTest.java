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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CameraTest extends CtsVerifierTest {

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.hardware.Camera#getParameters",
                "android.hardware.Camera#setParameters",
                "android.hardware.Camera#setDisplayOrientation",
                "android.hardware.Camera#setPreviewCallback",
                "android.hardware.Camera#stopPreview",
                "android.hardware.Camera#release",
                "android.hardware.Camera#setPreviewTexture",
                "android.hardware.Camera#startPreview",
                "android.hardware.Camera.Parameters#setPreviewFormat",
                "android.hardware.Camera.Parameters#setPreviewSize",
                "android.hardware.Camera.Parameters#getSupportedPreviewFormats",
                "android.hardware.Camera.Parameters#getSupportedPreviewSizes",
                "android.hardware.Camera.PreviewCallback#onPreviewFrame"
            })
    public void CameraFormatsTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.formats.CameraFormatsActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.hardware.Camera#ACTION_NEW_PICTURE",
                "android.hardware.Camera#ACTION_NEW_VIDEO"
            })
    public void CameraIntentsTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures(
                "android.hardware.type.automotive",
                "android.hardware.type.television",
                "android.software.leanback");

        runTest(".camera.intents.CameraIntentsActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @ApiTest(
            apis = {
                "android.hardware.Camera#getNumberOfCameras",
                "android.hardware.Camera#setPreviewDisplay",
                "android.hardware.Camera.Parameters#setPictureFormat",
                "android.hardware.Camera.Parameters#setPictureSize",
                "android.hardware.Camera#setDisplayOrientation",
                "android.hardware.Camera#takePicture"
            })
    public void CameraOrientationTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.orientation.CameraOrientationActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void PhotoCaptureTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.fov.PhotoCaptureActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void CameraVideoTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.video.CameraVideoActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void ItsTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.its.ItsTestActivity", "config_no_emulator");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.hardware.camera2.CameraCharacteristics#FLASH_INFO_AVAILABLE",
                "android.hardware.camera2.CameraManager#setTorchMode",
                "android.hardware.camera2.CameraManager#registerTorchCallback",
                "android.hardware.camera2.CameraManager.TorchCallback#onTorchModeChanged"
            })
    public void CameraFlashlightTest() throws Exception {
        requireFeatures("android.hardware.camera.flash");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.flashlight.CameraFlashlightActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    public void CameraPerformanceTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.performance.CameraPerformanceActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    @ApiTest(
            apis = {
                "android.hardware.camera2.CameraMetadata#controlExtendedSceneModeBokehStillCapture",
                "android.hardware.camera2.CameraMetadata#controlExtendedSceneModeBokehContinuous",
                "android.hardware.camera2.CameraCharacteristics#"
                        + "controlAvailableExtendedSceneModeCapabilities",
                "android.hardware.camera2.CameraCharacteristics#scalerStreamConfigurationMap",
                "android.hardware.camera2.CaptureRequest#controlExtendedSceneMode"
            })
    public void CameraBokehTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.bokeh.CameraBokehActivity");
    }

    @Interactive
    @Test
    // SingleDisplayMode
    @CddTest(requirements = "9.8.13/C-1-3")
    public void CameraMuteToggleTest() throws Exception {
        requireFeatures("android.hardware.camera.any");
        excludeFeatures("android.hardware.type.automotive");

        runTest(".camera.its.CameraMuteToggleActivity", "config_has_camera_toggle");
    }
}
