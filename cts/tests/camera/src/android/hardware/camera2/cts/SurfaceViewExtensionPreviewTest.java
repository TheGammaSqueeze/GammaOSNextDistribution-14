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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraExtensionSession;
import android.hardware.camera2.params.ExtensionSessionConfiguration;

import com.android.ex.camera2.blocking.BlockingExtensionSessionCallback;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Size;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.OutputConfiguration;
import android.util.Log;
import android.view.SurfaceView;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import org.junit.Test;

/**
 * Camera extension preview test by using SurfaceView.
 */

@RunWith(Parameterized.class)
public class SurfaceViewExtensionPreviewTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "SurfaceViewExtensionPreviewTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int FRAME_TIMEOUT_MS = 1000;

    @Test
    public void testExtensionPreview() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            if (!mAllStaticInfo.get(id).isColorOutputSupported()) {
                Log.i(TAG, "Camera " + id +
                        " does not support color outputs, skipping");
                continue;
            }

            CameraExtensionCharacteristics extensionChars =
                    mCameraManager.getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        SurfaceView.class);
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                updatePreviewSurface(maxSize);

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(mPreviewSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mHandler), sessionListener);

                boolean captureResultsSupported =
                        !extensionChars.getAvailableCaptureResultKeys(extension).isEmpty();

                try {
                    openDevice(id);
                    mCamera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder = mCamera.createCaptureRequest(
                            android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(mPreviewSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    CaptureRequest request = captureBuilder.build();
                    int sequenceId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mHandler), captureCallbackMock);

                    verify(captureCallbackMock,
                            timeout(FRAME_TIMEOUT_MS).atLeastOnce())
                            .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                    verify(captureCallbackMock,
                            timeout(FRAME_TIMEOUT_MS).atLeastOnce())
                            .onCaptureProcessStarted(extensionSession, request);
                    if (captureResultsSupported) {
                        verify(captureCallbackMock,
                                timeout(FRAME_TIMEOUT_MS).atLeastOnce())
                                .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                        any(TotalCaptureResult.class));
                    }

                    extensionSession.stopRepeating();

                    verify(captureCallbackMock,
                            timeout(FRAME_TIMEOUT_MS).times(1))
                            .onCaptureSequenceCompleted(extensionSession, sequenceId);

                    verify(captureCallbackMock, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    closeDevice();
                }
            }
        }
    }
}
