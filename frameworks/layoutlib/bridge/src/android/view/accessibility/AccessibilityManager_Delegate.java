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

package android.view.accessibility;

import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.content.Context;
import android.graphics.Matrix;
import android.view.MagnificationSpec;
import android.view.accessibility.IAccessibilityManager.WindowTransformationSpec;

public class AccessibilityManager_Delegate {
    private static WindowTransformationSpec sInstance;

    @LayoutlibDelegate
    public static IAccessibilityManager.WindowTransformationSpec getWindowTransformationSpec(
            AccessibilityManager thisManager, int windowId) {
        if (sInstance == null) {
            WindowTransformationSpec spec = new WindowTransformationSpec();
            spec.magnificationSpec = new MagnificationSpec();
            float[] matrix = new float[9];
            Matrix.IDENTITY_MATRIX.getValues(matrix);
            spec.transformationMatrix = matrix;
            sInstance = spec;
        }
        return sInstance;
    }

    @LayoutlibDelegate
    public static AccessibilityManager getInstance(Context context) {
        Context baseContext = BridgeContext.getBaseContext(context);
        return ((BridgeContext)baseContext).getAccessibilityManager();
    }
}
