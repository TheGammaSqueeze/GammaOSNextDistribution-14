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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws a partial circular shape given some simple parameters:
 * <li>Color via {@link #setColor(int)}</li>
 * <li>Stroke width via {@link #setStrokeWidth(float)}</li>
 * <li>Start angle via {@link #setStartAngle(float)}</li>
 * <li>Sweep angle via {@link #setSweepAngle(float)}</li>
 */
public class PartialCircleView extends View {

    /** Some defaults that should be obvious if not changed. */
    private int mColor = Color.MAGENTA;
    private float mStrokeWidth = 2;
    private float mStartAngle = 0;
    private float mSweepAngle = 360;

    public PartialCircleView(Context context) {
        super(context);
    }

    public PartialCircleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PartialCircleView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PartialCircleView(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /** Sets the color used to draw this partial circle. */
    public void setColor(int color) {
        if (mColor != color) {
            mColor = color;
            invalidate();
        }
    }

    /** Sets the stroke width used to draw this partial circle. */
    public void setStrokeWidth(float strokeWidth) {
        if (mStrokeWidth != strokeWidth) {
            mStrokeWidth = strokeWidth;
            invalidate();
        }
    }

    /** Sets the start angle used to draw this partial circle. */
    public void setStartAngle(float startAngle) {
        if (mStartAngle != startAngle) {
            mStartAngle = startAngle;
            invalidate();
        }
    }

    /** Sets the sweep angle used to draw this partial circle. */
    public void setSweepAngle(float sweepAngle) {
        if (mSweepAngle != sweepAngle) {
            mSweepAngle = sweepAngle;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = (float) getWidth();
        final float height = (float) getHeight();

        // We subtract half stroke width as to fit the outside edge of the circle just inside
        // our bounds.
        final float radius = ((width > height) ? height / 2 : width / 2) - (mStrokeWidth / 2);

        Paint paint = new Paint();
        paint.setColor(mColor);
        paint.setStrokeWidth(mStrokeWidth);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        final RectF oval = new RectF();
        final float x = width / 2;
        final float y = height / 2;
        oval.set(x - radius,
                y - radius,
                x + radius,
                y + radius);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawArc(oval, mStartAngle, mSweepAngle, false, paint);
    }
}
