/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.graphics;

import android.annotation.Nullable;

public class LayoutlibRenderer extends HardwareRenderer {

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    /**
     * We are overriding this method in order to call {@link Canvas#enableZ} (for shadows) and set
     * the scale
     */
    @Override
    public void setContentRoot(@Nullable RenderNode content) {
        RecordingCanvas canvas = mRootNode.beginRecording();
        canvas.scale(scaleX, scaleY);
        canvas.enableZ();
        // This way we clear the native image buffer before drawing
        canvas.drawColor(0, BlendMode.CLEAR);
        if (content != null) {
            canvas.drawRenderNode(content);
        }
        canvas.disableZ();
        mRootNode.endRecording();
    }

    public void setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }
}
