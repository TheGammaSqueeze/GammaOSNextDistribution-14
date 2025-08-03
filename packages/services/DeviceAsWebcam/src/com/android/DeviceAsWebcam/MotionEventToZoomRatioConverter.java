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

package com.android.DeviceAsWebcam;

import android.content.Context;
import android.util.Range;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * A class helps to convert the motion events to the zoom ratio.
 *
 * <p>Callers can register {@link android.view.View.OnTouchListener} to the target view and then
 * pass the received motion events to the {@link #onTouchEvent(MotionEvent)} function in this
 * class. This class uses {@link ScaleGestureDetector} to calculate the zoom ratio and then invoke
 * {@link ZoomRatioUpdatedListener#onZoomRatioUpdated(float)} with the resulting zoom ratio.
 * Callers can use the updated zoom ratio to submit capture request for the zoom function.
 */
public class MotionEventToZoomRatioConverter {
    private Range<Float> mZoomRatioRange;
    private float mCurrentZoomRatio = 1.0F;
    private final ScaleGestureDetector mScaleGestureDetector;
    private final ZoomRatioUpdatedListener mZoomRatioUpdatedListener;
    private ScaleGestureDetector.SimpleOnScaleGestureListener mScaleGestureListener =
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    mZoomRatioUpdatedListener.onZoomRatioUpdated(
                            getAndUpdateScaledZoomRatio(detector.getScaleFactor()));
                    return true;
                }
            };

    /**
     * Creates a MotionEventToZoomRatioConverter instance.
     *
     * @param applicationContext       used to create a ScaleGestureDetector to convert the motion
     *                                 events into scale factors.
     * @param zoomRatioRange           the available zoom ratio range.
     * @param currentZoomRatio         current zoom ratio setting.
     * @param zoomRatioUpdatedListener the listener to receive the updated zoom ratio events.
     */
    public MotionEventToZoomRatioConverter(Context applicationContext,
            Range<Float> zoomRatioRange,
            float currentZoomRatio,
            ZoomRatioUpdatedListener zoomRatioUpdatedListener) {
        mZoomRatioRange = zoomRatioRange;
        mCurrentZoomRatio = currentZoomRatio;
        mScaleGestureDetector = new ScaleGestureDetector(applicationContext, mScaleGestureListener);
        mZoomRatioUpdatedListener = zoomRatioUpdatedListener;
    }

    /**
     * The function to receive the motion events passed from a
     * {@link android.view.View.OnTouchListener} of the target view.
     */
    public boolean onTouchEvent(MotionEvent motionEvent) {
        return mScaleGestureDetector.onTouchEvent(motionEvent);
    }

    /**
     * Sets the zoom ratio value.
     */
    public void setZoomRatio(float zoomRatio) {
        mCurrentZoomRatio = zoomRatio;
    }

    /**
     * Resets the converter with the new zoom ratio range setting.
     */
    public void reset(float currZoomRatio, Range<Float> zoomRatioRange) {
        mCurrentZoomRatio = currZoomRatio;
        mZoomRatioRange = zoomRatioRange;
    }

    private float getAndUpdateScaledZoomRatio(float scaleFactor) {
        mCurrentZoomRatio = Math.max(mZoomRatioRange.getLower(),
                Math.min(mZoomRatioRange.getUpper(), mCurrentZoomRatio * scaleFactor));
        return mCurrentZoomRatio;
    }

    /**
     * An interface to receive the updated zoom ratio.
     */
    interface ZoomRatioUpdatedListener {
        /**
         * The callback function which will be invoked after converting the received motion
         * events to the updated zoom ratio.
         *
         * @param updatedZoomRatio the updated zoom ratio.
         */
        void onZoomRatioUpdated(float updatedZoomRatio);
    }
}
