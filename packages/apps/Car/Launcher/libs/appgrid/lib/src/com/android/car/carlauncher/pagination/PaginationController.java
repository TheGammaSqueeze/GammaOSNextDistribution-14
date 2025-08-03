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

package com.android.car.carlauncher.pagination;

import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import com.android.car.carlauncher.pagination.PageMeasurementHelper.GridDimensions;
import com.android.car.carlauncher.pagination.PageMeasurementHelper.PageDimensions;

import java.util.HashSet;
import java.util.Set;

/**
 * Controller class that handling all pagination related logic.
 */
public class PaginationController {
    private final PageMeasurementHelper mPageMeasurementHelper;
    private final DimensionUpdateCallback mCallback;

    public PaginationController(View windowBackground, DimensionUpdateCallback callback) {
        mCallback = callback;
        mPageMeasurementHelper = new PageMeasurementHelper(windowBackground);
        windowBackground.getViewTreeObserver().addOnGlobalLayoutListener(
                new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int windowHeight = windowBackground.getMeasuredHeight();
                        int windowWidth = windowBackground.getMeasuredWidth();
                        maybeHandleWindowResize(windowWidth, windowHeight);
                    }
                });
    }

    private void maybeHandleWindowResize(int windowWidth, int windowHeight) {
        boolean consumed = mPageMeasurementHelper.handleWindowSizeChange(windowWidth, windowHeight);
        if (consumed) {
            mCallback.notifyDimensionsUpdated(mPageMeasurementHelper.getPageDimensions(),
                    mPageMeasurementHelper.getGridDimensions());
        }
    }

    /**
     * Callback contract between this controller and its {@link DimensionUpdateListener} classes.
     *
     * When {@link PageMeasurementHelper#handleWindowSizeChange} returns {@code true}, the callback
     * will notify its listeners that the window size has changed.
     */
    public static class DimensionUpdateCallback {
        Set<DimensionUpdateListener> mListeners = new HashSet<>();

        /**
         * Adds an {@link DimensionUpdateListener} to
         */
        public void addListener(DimensionUpdateListener listener) {
            mListeners.add(listener);
        }

        /**
         * Updates all listeners with the new measured dimensions.
         */
        public void notifyDimensionsUpdated(PageDimensions pageDimens, GridDimensions gridDimens) {
            for (DimensionUpdateListener listener : mListeners) {
                listener.onDimensionsUpdated(pageDimens, gridDimens);
            }
        }
    }

    /**
     * Listener interface for {@link DimensionUpdateCallback}.
     *
     * Classes that implement the listener should use the measured dimensions from
     * {@link DimensionUpdateListener#onDimensionsUpdated} to update relevant layout params.
     */
    public interface DimensionUpdateListener {
        /**
         * Updates layout params from the updated dimensions measurements in {@link PageDimensions}
         * and {@link GridDimensions}*/
        void onDimensionsUpdated(PageDimensions pageDimens, GridDimensions gridDimens);
    }
}
