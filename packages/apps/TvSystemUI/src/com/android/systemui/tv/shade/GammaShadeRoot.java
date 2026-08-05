/*
 * Copyright (C) 2026 The GammaOS Project
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

package com.android.systemui.tv.shade;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Root view for the GammaOS shade. It intercepts a clearly-vertical drag anywhere in the shade
 * (over the notification list, the empty/blank area below it, the dim scrim, the very bottom edge)
 * and hands it to the shade so an up-swipe/flick dismisses the panel, while still letting horizontal
 * gestures (QS paging, the brightness/volume sliders) and taps reach their children.
 */
public class GammaShadeRoot extends FrameLayout {

    public interface DragListener {
        /** Return true if the event was consumed as a shade drag/dismiss. */
        boolean onShadeDrag(MotionEvent ev);
    }

    private DragListener mDragListener;
    private final int mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mIntercepting;

    public GammaShadeRoot(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setDragListener(DragListener l) {
        mDragListener = l;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mIntercepting = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mIntercepting) {
                    final float dy = ev.getY() - mDownY;
                    final float dx = ev.getX() - mDownX;
                    // A deliberate, mostly-vertical drag becomes a shade drag no matter which child
                    // it started over (notifications, blank area, sliders...). Horizontal stays with
                    // the child (paging / slider), taps stay with the child.
                    if (Math.abs(dy) > mTouchSlop && Math.abs(dy) > Math.abs(dx)) {
                        mIntercepting = true;
                        return true;
                    }
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mDragListener != null && mDragListener.onShadeDrag(ev)) {
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
