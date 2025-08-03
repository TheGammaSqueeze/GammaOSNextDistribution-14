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

package android.virtualdevice.cts.applaunch;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;

import android.annotation.NonNull;
import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.flags.Flags;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.ConditionVariable;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class StreamedAppBehaviorTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_CAMERA)
    @Test
    public void appsInVirtualDevice_shouldNotHaveAccessToCamera() throws Exception {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        CameraManager manager = getApplicationContext().getSystemService(CameraManager.class);
        String[] cameras = manager.getCameraIdList();
        assumeNotNull((Object) cameras);

        // An activity from this UID is running on the virtual device, so camera access is blocked.
        AppComponents.EmptyActivity activity = mRule.startActivityOnDisplaySync(
                virtualDisplay, AppComponents.EmptyActivity.class);

        for (String cameraId : cameras) {
            assertThat(accessCameraFromActivity(activity, cameraId)).isGreaterThan(0);
        }
    }

    private int accessCameraFromActivity(Activity activity, String cameraId) {
        ConditionVariable cond = new ConditionVariable();
        final CameraManager cameraManager = activity.getSystemService(CameraManager.class);
        final int[] cameraError = {0};
        final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {}

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                cameraError[0] = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;
                cond.open();
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                cameraError[0] = error;
                cond.open();
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                cameraManager.openCamera(cameraId, cameraCallback, null);
            } catch (CameraAccessException e) {
                // ok to ignore - we should get one of the onDisconnected or onError callbacks above
            }
        });
        cond.block(TIMEOUT_MILLIS);
        return cameraError[0];
    }
}
