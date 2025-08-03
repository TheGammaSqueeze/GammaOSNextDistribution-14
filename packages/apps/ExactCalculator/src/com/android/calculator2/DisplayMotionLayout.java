/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;

public class DisplayMotionLayout extends MotionLayout {
    private int mPointerId;
    private boolean mIsScrolling;
    private PointF mPreviousPoint;
    private MotionEvent mPreviousEvent;
    private final int mTouchSlop;
    private boolean mOutOfBounds;

    public DisplayMotionLayout(@NonNull Context context) {
        this(context, null);
    }

    public DisplayMotionLayout(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public DisplayMotionLayout(@NonNull Context context, @Nullable AttributeSet attributeSet,
                               int i) {
        super(context, attributeSet, i);
        mPointerId = -1;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public final boolean onInterceptTouchEvent(@NonNull MotionEvent motionEvent) {
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                Rect hitRect = new Rect();

                findViewById(R.id.display).getHitRect(hitRect);
                if (hitRect.contains((int) motionEvent.getX(), (int) motionEvent.getY())) {
                    mPreviousPoint = new PointF(motionEvent.getX(),motionEvent.getY());
                    mPointerId = motionEvent.getPointerId(0);
                    mIsScrolling = false;
                    mOutOfBounds = false;
                    saveLastMotion(motionEvent);
                } else {
                    mPointerId = -1;
                    mIsScrolling = false;
                    mOutOfBounds = true;
                    clearLastMotion();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                mIsScrolling = false;
                mOutOfBounds = false;
                mPointerId = -1;
                clearLastMotion();
                break;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = motionEvent.findPointerIndex(mPointerId);
                if (mPointerId != -1 && pointerIndex != -1 && !mOutOfBounds) {
                    float y = Math.abs(motionEvent.getY(pointerIndex) - mPreviousPoint.y);
                    if (y > mTouchSlop) {
                        mIsScrolling = true;
                        onTouchEvent(mPreviousEvent);
                    }
                }
                break;
        }
        if (super.onInterceptTouchEvent(motionEvent)) {
            return true;
        }
        return mIsScrolling & !mOutOfBounds;
    }

    private void saveLastMotion(@NonNull MotionEvent motionEvent) {
        if (mPreviousEvent != null) {
            mPreviousEvent.recycle();
        }
        mPreviousEvent = MotionEvent.obtain(motionEvent);
    }

    private void clearLastMotion()
    {
        if (mPreviousEvent != null) {
            mPreviousEvent.recycle();
            mPreviousEvent = null;
        }
    }
}
