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

package android.view.cts.surfacevalidator;

import android.os.IBinder;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.WindowManager.LayoutParams;
import android.view.SurfaceControl;
import android.view.cts.surfacevalidator.IMotionEventReceiver;
import android.window.InputTransferToken;

interface IAttachEmbeddedWindow {
    SurfacePackage attachEmbedded(IBinder hostToken, int width, int height, int displayId, long delayMs);
    void relayout(in LayoutParams lp);
    void sendCrash();
    String attachEmbeddedSurfaceControl(in SurfaceControl sc, int displayId,
            in InputTransferToken hostToken, int width, int height, boolean transferTouchToHost,
            in @nullable IMotionEventReceiver motionEventReceiver);
    InputTransferToken getEmbeddedInputTransferToken();
    void tearDownEmbeddedSurfaceControl();
}