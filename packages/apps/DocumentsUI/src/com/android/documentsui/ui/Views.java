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

package com.android.documentsui.ui;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * A utility class for working with Views.
 */
public final class Views {

    private Views() {}

    /**
     *
     * Return whether the event is in the view's region. We determine it with in the coordinate
     * of the parent view that dispatches the motion event.
     * @param event the motion event
     * @param eventSource the view dispatching the motion events.
     * @param view the view to check the selection region
     * @return True, if the event is in the region. Otherwise, return false.
     */

    public static boolean isEventOver(MotionEvent event, ViewParent eventSource, View view) {
        if (view == null || event == null || !view.isAttachedToWindow()) {
            return false;
        }

        View parent = null;
        if (eventSource instanceof ViewGroup) {
            parent = (View) eventSource;
        }

        final Rect viewBoundsOnGlobalCoordinate = getBoundsOnScreen(view);

        // If the parent is null, it means view is the view root of the window, so the event
        // should be from view itself, in this case we don't need any offset.
        final int[] viewParentCoord = new int[2];
        if (parent != null) {
            parent.getLocationOnScreen(viewParentCoord);
        }

        Rect viewBoundsOnParentViewCoordinate = new Rect(viewBoundsOnGlobalCoordinate);
        viewBoundsOnParentViewCoordinate.offset(-viewParentCoord[0], -viewParentCoord[1]);
        return viewBoundsOnParentViewCoordinate.contains((int) event.getX(), (int) event.getY());
    }

    private static Rect getBoundsOnScreen(View view) {
        final int[] coord = new int[2];
        view.getLocationOnScreen(coord);

        return new Rect(coord[0], coord[1], coord[0] + view.getMeasuredWidth(),
                coord[1] + view.getMeasuredHeight());
    }
}
