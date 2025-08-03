/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.car.app;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.Activity;
import android.car.Car;
import android.car.feature.Flags;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceView;

import java.util.function.Consumer;

/**
 * A container that's used by the display compat host.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_DISPLAY_COMPATIBILITY)
@SystemApi
public final class CarDisplayCompatContainer {

    public static final class Builder {
        @NonNull
        private Activity mActivity;
        private int mWidth;
        private int mHeight;
        private int mDensityDpi;
        @Nullable
        private Consumer<SurfaceView> mCallback;

        public Builder(@NonNull Activity activity) {
            mActivity = activity;
        }

        /**
         * set width in px
         */
        @NonNull
        public Builder setWidth(int width) {
            mWidth = width;
            return this;
        }

        /**
         * set height in px
         */
        @NonNull
        public Builder setHeight(int height) {
            mHeight = height;
            return this;
        }

        /**
         * set density in dpi
         */
        @NonNull
        public Builder setDensity(int densityDpi) {
            mDensityDpi = densityDpi;
            return this;
        }

        /**
         * set density in dpi
         */
        @NonNull
        public Builder setSurfaceViewCallback(@Nullable Consumer<SurfaceView> callback) {
            mCallback = callback;
            return this;
        }

        /**
         * Returns a new instance of {@link CarDisplayCompatContainer}
         */
        @NonNull
        CarDisplayCompatContainer build() {
            return new CarDisplayCompatContainer(
                    mActivity, mWidth, mHeight, mDensityDpi, mCallback);
        }
    }

    /**
     * @hide
     */
    CarDisplayCompatContainer(@NonNull Activity activity, int width, int height, int densityDpi,
            @Nullable Consumer<SurfaceView> callback) {
    }

    /**
     * Set bounds of the display compat container
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY)
    @NonNull
    public Rect setWindowBounds(@NonNull Rect windowBounds) {
        return new Rect();
    }

    /**
     * Set the density of the display compat container
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY)
    public void setDensity(int density) {
    }

    /**
     * Set the visibility of the display compat container
     * see {@link android.view.View#setVisibility(int)}
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY)
    public void setVisibility(int visibility) {
    }

    /**
     * Launch an activity on the display compat container
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY)
    public void startActivity(@NonNull Intent intent, @Nullable Bundle bundle) {
    }

    /**
     * Called when the user clicks the back button
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY)
    public void onBackPressed() {
    }
}
