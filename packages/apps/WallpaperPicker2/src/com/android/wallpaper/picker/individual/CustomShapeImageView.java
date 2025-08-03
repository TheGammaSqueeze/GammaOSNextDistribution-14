/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.wallpaper.picker.individual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.core.graphics.PathParser;

import com.android.wallpaper.R;

/**
 * A view where the image can be optionally clipped to have a custom border, by default circular.
 */
public class CustomShapeImageView extends ImageView {
    private boolean mClipped;

    private boolean mPathSet;
    private Path mPath;

    public CustomShapeImageView(Context context) {
        super(context);
    }

    public CustomShapeImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomShapeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Returns whether the image is clipped with a circular border.
     */
    public boolean getClipped() {
        return mClipped;
    }

    /**
     * Modifies how the image is clipped. When called with true, the image
     * is clipped with a circular border; with false, the default border.
     *
     * @param clippedValue Whether the image is clipped with a circular
     *                     border.
     */
    public void setClipped(boolean clippedValue) {
        mClipped = clippedValue;
        invalidate();
        requestLayout();
    }

    /**
     * Sets the border of the thumbnail.
     *
     * @param clippingPathResource The resource ID of the path string of the desired
     *                             border.
     */
    public void setClippingPath(int clippingPathResource) {
        String string = getResources().getString(clippingPathResource);
        mPath = PathParser.createPathFromPathData(string);
        mPathSet = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mClipped) {
            if (!mPathSet) {
                setClippingPath(R.string.circular_border);
            }
            float scale = (float) (getHeight() / 100.0);
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            Path scaledPath = new Path(mPath);
            scaledPath.transform(matrix);
            canvas.clipPath(scaledPath);
        }

        super.onDraw(canvas);
    }
}

