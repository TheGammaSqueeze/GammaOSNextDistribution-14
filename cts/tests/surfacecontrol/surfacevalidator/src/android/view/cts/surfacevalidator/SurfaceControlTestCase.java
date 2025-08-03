/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

public class SurfaceControlTestCase implements ISurfaceValidatorTestCase {
    private final SurfaceViewFactory mViewFactory;
    private final FrameLayout.LayoutParams mLayoutParams;
    private final PixelChecker mPixelChecker;
    protected View mSurfaceView;
    private final int mBufferWidth;
    private final int mBufferHeight;

    private FrameLayout mParent;

    public SurfaceControlTestCase(SurfaceHolder.Callback callback, PixelChecker pixelChecker,
            int layoutWidth, int layoutHeight, int bufferWidth, int bufferHeight) {
        mViewFactory = new SurfaceViewFactory(callback);
        mLayoutParams =
                new FrameLayout.LayoutParams(layoutWidth, layoutHeight, Gravity.LEFT | Gravity.TOP);
        mPixelChecker = pixelChecker;
        mBufferWidth = bufferWidth;
        mBufferHeight = bufferHeight;
    }

    public PixelChecker getChecker() {
        return mPixelChecker;
    }

    public void start(Context context, FrameLayout parent) {
        View view = mViewFactory.createView(context);
        mSurfaceView = view;
        mParent = parent;
        mParent.addView(view, mLayoutParams);
    }

    public void end() {
        mParent.removeAllViews();
    }

    @Override
    public Rect getBoundsToCheck(FrameLayout parent) {
        View boundsView = mSurfaceView;
        Rect boundsToCheck = new Rect(0, 0, boundsView.getWidth(), boundsView.getHeight());
        int[] topLeft = new int[2];
        boundsView.getLocationOnScreen(topLeft);
        boundsToCheck.offset(topLeft[0], topLeft[1]);
        return boundsToCheck;
    }

    private class SurfaceViewFactory implements ViewFactory {
        private final SurfaceHolder.Callback mCallback;

        SurfaceViewFactory(SurfaceHolder.Callback callback) {
            mCallback = callback;
        }

        public View createView(Context context) {
            SurfaceView surfaceView = new SurfaceView(context);
            // prevent transparent region optimization, which is invalid for a SurfaceView moving
            // around
            surfaceView.setWillNotDraw(false);

            surfaceView.getHolder().setFixedSize(mBufferWidth, mBufferHeight);
            surfaceView.getHolder().addCallback(mCallback);

            return surfaceView;
        }
    }
}
