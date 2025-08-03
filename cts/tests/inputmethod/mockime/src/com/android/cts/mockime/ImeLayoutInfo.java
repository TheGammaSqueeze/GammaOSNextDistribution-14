/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.mockime;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Display;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A collection of layout-related information when
 * {@link View.OnLayoutChangeListener#onLayoutChange(View, int, int, int, int, int, int, int, int)}
 * is called back for the input view (the view returned from {@link MockIme#onCreateInputView()}).
 */
public final class ImeLayoutInfo {

    private static final String NEW_LAYOUT_KEY = "newLayout";
    private static final String OLD_LAYOUT_KEY = "oldLayout";
    private static final String VIEW_ORIGIN_ON_SCREEN_KEY = "viewOriginOnScreen";
    private static final String DISPLAY_SIZE_KEY = "displaySize";

    @NonNull
    private final Rect mNewLayout;
    @NonNull
    private final Rect mOldLayout;
    @Nullable
    private Point mViewOriginOnScreen;
    @Nullable
    private Point mDisplaySize;

    /**
     * Returns the bounding box of the {@link View} passed to
     * {@link android.inputmethodservice.InputMethodService#onCreateInputView()} in screen
     * coordinates.
     *
     * <p>Currently this method assumes that no {@link View} in the hierarchy uses
     * transformations such as {@link View#setRotation(float)}.</p>
     *
     * @return Region in screen coordinates.
     */
    @Nullable
    public Rect getInputViewBoundsInScreen() {
        return new Rect(
                mViewOriginOnScreen.x, mViewOriginOnScreen.y,
                mViewOriginOnScreen.x + mNewLayout.width(),
                mViewOriginOnScreen.y + mNewLayout.height());
    }

    ImeLayoutInfo(@NonNull Rect newLayout, @NonNull Rect oldLayout,
            @NonNull Point viewOriginOnScreen, @Nullable Point displaySize) {
        mNewLayout = new Rect(newLayout);
        mOldLayout = new Rect(oldLayout);
        mViewOriginOnScreen = new Point(viewOriginOnScreen);
        mDisplaySize = new Point(displaySize);
    }

    void writeToBundle(@NonNull Bundle bundle) {
        bundle.putParcelable(NEW_LAYOUT_KEY, mNewLayout);
        bundle.putParcelable(OLD_LAYOUT_KEY, mOldLayout);
        bundle.putParcelable(VIEW_ORIGIN_ON_SCREEN_KEY, mViewOriginOnScreen);
        bundle.putParcelable(DISPLAY_SIZE_KEY, mDisplaySize);
    }

    static ImeLayoutInfo readFromBundle(@NonNull Bundle bundle) {
        final Rect newLayout = bundle.getParcelable(NEW_LAYOUT_KEY);
        final Rect oldLayout = bundle.getParcelable(OLD_LAYOUT_KEY);
        final Point viewOrigin = bundle.getParcelable(VIEW_ORIGIN_ON_SCREEN_KEY);
        final Point displaySize = bundle.getParcelable(DISPLAY_SIZE_KEY);

        return new ImeLayoutInfo(newLayout, oldLayout, viewOrigin, displaySize);
    }

    static ImeLayoutInfo fromLayoutListenerCallback(View v, int left, int top, int right,
            int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        final Rect newLayout = new Rect(left, top, right, bottom);
        final Rect oldLayout = new Rect(oldLeft, oldTop, oldRight, oldBottom);
        final int[] viewOriginArray = new int[2];
        v.getLocationOnScreen(viewOriginArray);
        final Point viewOrigin = new Point(viewOriginArray[0], viewOriginArray[1]);
        final Display display = v.getDisplay();
        final Point displaySize;
        if (display != null) {
            displaySize = new Point();
            display.getRealSize(displaySize);
        } else {
            displaySize = null;
        }
        return new ImeLayoutInfo(newLayout, oldLayout, viewOrigin, displaySize);
    }
}
