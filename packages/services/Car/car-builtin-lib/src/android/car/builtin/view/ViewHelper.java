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

package android.car.builtin.view;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.graphics.Rect;
import android.view.SurfaceView;
import android.view.View;

/**
 * Provides access to a view {@link View} related hidden APIs.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ViewHelper {
    /**
     * See {@link View#getBoundsOnScreen(Rect)}}.
     */
    public static void getBoundsOnScreen(@NonNull View v, @NonNull Rect outRect) {
        v.getBoundsOnScreen(outRect);
    }

    /**
     * See {@link SurfaceView#setResizeBackgroundColor(int)}}.
     */
    public static void seResizeBackgroundColor(@NonNull SurfaceView surfaceView, int color) {
        surfaceView.setResizeBackgroundColor(color);
    }

    private ViewHelper() {
        throw new UnsupportedOperationException();
    }
}
