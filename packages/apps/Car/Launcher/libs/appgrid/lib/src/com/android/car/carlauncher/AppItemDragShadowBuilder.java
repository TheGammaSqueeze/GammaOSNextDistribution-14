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

package com.android.car.carlauncher;

import android.graphics.Canvas;
import android.graphics.Point;
import android.view.View;

/**
 * Custom View.DragShadowBuilder that handles the drawing and deploying of drag shadow when an
 * app icon is long pressed and dragged.
 */
public class AppItemDragShadowBuilder extends View.DragShadowBuilder{
    private final View mAppIcon;
    private final int mSize;
    private final int mScaledSize;
    private final float mTouchPointX;
    private final float mTouchPointY;

    public AppItemDragShadowBuilder(View view, float touchPointX, float touchPointY,
            int size, int scaledSize) {
        super(view);
        mAppIcon = view;
        mSize = size;
        mScaledSize = scaledSize;
        mTouchPointX = touchPointX;
        mTouchPointY = touchPointY;
    }

    @Override
    public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
        if (mAppIcon != null) {
            outShadowSize.set(mScaledSize, mScaledSize);
            outShadowTouchPoint.set((int) mTouchPointX, (int) mTouchPointY);
        }
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        canvas.scale(/* scaleX */ mScaledSize / (float) mSize,
                /* scaleY */  mScaledSize / (float) mSize);
        getView().draw(canvas);
    }
}
