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

package android.car.builtin.view;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.Activity;
import android.car.builtin.util.Slogf;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.ViewRootImpl;
import android.view.Window;
import android.view.WindowManagerGlobal;

/**
 * Provides access to {@code android.view.SurfaceControl} calls.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class SurfaceControlHelper {
    private static final String TAG = SurfaceControlHelper.class.getSimpleName();
    private SurfaceControlHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a mirrored hierarchy for the mirror of {@link SurfaceControl}.
     *
     * <p>For the detail, see {@link SurfaceControl#mirrorSurface(SurfaceControl)}
     */
    @NonNull
    public static SurfaceControl mirrorSurface(@NonNull SurfaceControl mirrorOf) {
        return SurfaceControl.mirrorSurface(mirrorOf);
    }

    /**
     * Creates a mirrored Surface for the given Display.
     */
    @Nullable
    public static SurfaceControl mirrorDisplay(int displayId) {
        try {
            SurfaceControl outSurfaceControl = new SurfaceControl();
            boolean success = WindowManagerGlobal.getWindowManagerService().mirrorDisplay(displayId,
                    outSurfaceControl);
            return success ? outSurfaceControl : null;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to reach window manager", e);
        }
        return null;
    }

    /**
     * See {@link SurfaceControl(SurfaceControl)}}.
     */
    public static SurfaceControl copy(SurfaceControl source) {
        return new SurfaceControl(source, SurfaceControlHelper.class.getSimpleName());
    }

    private static ViewRootImpl getViewRootImpl(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            Slogf.e(TAG, "Window is not ready yet: %s", activity.getComponentName());
            return null;
        }
        ViewRootImpl viewRoot = window.getDecorView().getViewRootImpl();
        if (viewRoot == null) {
            Slogf.e(TAG, "ViewRoot is not attached to Window yet: %s",
                    activity.getComponentName());
            return null;
        }
        return viewRoot;
    }

    /**
     * Gets {@link SurfaceControl} of given Activity.
     *
     * Note: SurfaceControl is not available during {@code onCreate} and {@code onResume}.
     * You can access it from {@link Activity#onAttachedToWindow()}.
     */
    @Nullable public static SurfaceControl getSurfaceControl(@NonNull Activity activity) {
        ViewRootImpl viewRoot = getViewRootImpl(activity);
        if (viewRoot == null) {
            return null;
        }
        SurfaceControl surface = viewRoot.getSurfaceControl();
        if (surface == null) {
            Slogf.e(TAG, "Surface is not prepared yet");
            return null;
        }
        return surface;
    }
}
