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

package com.google.android.car.kitchensink.touch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for touch point views.
 */
public abstract class AbstractTouchPointView extends View {

    private static final String TOUCH_POINT_VIEW_TAG = "TouchPointView";

    private static final int[] COLORS = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.BLACK,
            Color.DKGRAY
    };

    private final List<Finger> mFingers = new ArrayList<>();

    private final Paint mPaint = new Paint();

    private final boolean mLogOnly;

    private int mRadius;

    public AbstractTouchPointView(Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr, boolean logOnly) {
        super(context, attrs, defStyleAttr);
        mPaint.setStyle(Paint.Style.FILL);
        mLogOnly = logOnly;
    }

    protected void setRadius(int radius) {
        mRadius = radius;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mLogOnly) {
            logTouchEvents(event);
            return true;
        }
        mFingers.clear();
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            invalidate();
            return true;
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            Finger finger = new Finger();
            finger.mPoint = new Point((int) event.getX(pointerIndex),
                    (int) event.getY(pointerIndex));
            finger.mPointerId = pointerId;
            mFingers.add(finger);
        }
        invalidate();
        return true;
    }

    protected void logTouchEvents(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return;
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            long downTime = event.getDownTime();
            long eventTime = event.getEventTime();
            Log.d(TOUCH_POINT_VIEW_TAG,
                    "TouchUp [x=" + event.getX(pointerIndex) + ", y=" + event.getY(pointerIndex)
                            + " , pointerId=" + pointerId + ", pointerIndex=" + pointerIndex
                            + ", duration="
                            + (eventTime - downTime) + "]");
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mLogOnly) {
            return;
        }
        for (int i = 0; i < mFingers.size(); i++) {
            Finger finger = mFingers.get(i);
            Point point = finger.mPoint;
            int color = COLORS[finger.mPointerId % COLORS.length];
            mPaint.setColor(color);
            canvas.drawCircle(point.x, point.y, mRadius, mPaint);
        }
    }

    private static final class Finger {
        Point mPoint;
        int mPointerId;
    }
}
