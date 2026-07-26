/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import androidx.recyclerview.selection.SelectionTracker;

import com.android.documentsui.base.Events;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Procedure;
import com.android.documentsui.dirlist.FocusHandler;

/**
 * Handle common input events.
 */
public class SharedInputHandler {

    private static final String TAG = "SharedInputHandler";

    private final FocusHandler mFocusManager;
    private final Procedure mSearchCanceler;
    private final Procedure mDirPopper;
    private final Runnable mSearchExecutor;
    private final Features mFeatures;
    private final SelectionTracker<String> mSelectionMgr;
    private final DrawerController mDrawer;
    // Owning activity, used for gamepad shortcuts (new folder, focus traversal, paging). May be
    // null (e.g. in unit tests), so every use must be null-guarded.
    private final BaseActivity mActivity;

    public SharedInputHandler(
            FocusHandler focusHandler,
            SelectionTracker<String> selectionMgr,
            Procedure searchCanceler,
            Procedure dirPopper,
            Features features,
            DrawerController drawer,
            Runnable searchExcutor) {
        this(focusHandler, selectionMgr, searchCanceler, dirPopper, features, drawer,
                searchExcutor, null);
    }

    public SharedInputHandler(
            FocusHandler focusHandler,
            SelectionTracker<String> selectionMgr,
            Procedure searchCanceler,
            Procedure dirPopper,
            Features features,
            DrawerController drawer,
            Runnable searchExcutor,
            BaseActivity activity) {
        mFocusManager = focusHandler;
        mSearchCanceler = searchCanceler;
        mSelectionMgr = selectionMgr;
        mDirPopper = dirPopper;
        mFeatures = features;
        mDrawer = drawer;
        mSearchExecutor = searchExcutor;
        mActivity = activity;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            // Unhandled ESC keys end up being rethrown back at us as BACK keys. So by returning
            // true, we make sure it always does no-op.
            case KeyEvent.KEYCODE_ESCAPE:
                return onEscape();

            case KeyEvent.KEYCODE_DEL:
                return onDelete();

            // This is the Android back button, not backspace.
            case KeyEvent.KEYCODE_BACK:
                return onBack();

            case KeyEvent.KEYCODE_TAB:
                return onTab();

            // Gamepad shortcuts, so the whole UI is usable with just a controller.
            case KeyEvent.KEYCODE_BUTTON_L1:
                // Toggle focus between the roots (storage) sidebar and the directory list.
                mFocusManager.advanceFocusArea();
                return true;

            case KeyEvent.KEYCODE_BUTTON_R1:
                // Advance focus forward through the on-screen focusable areas, like Tab.
                return advanceFocusForward();

            case KeyEvent.KEYCODE_BUTTON_L2:
                // Page up within whichever list currently has focus.
                return dispatchToFocusedView(KeyEvent.KEYCODE_PAGE_UP);

            case KeyEvent.KEYCODE_BUTTON_R2:
                // Page down within whichever list currently has focus.
                return dispatchToFocusedView(KeyEvent.KEYCODE_PAGE_DOWN);

            case KeyEvent.KEYCODE_BUTTON_X:
                mSearchExecutor.run();
                return true;

            case KeyEvent.KEYCODE_BUTTON_Y:
                if (mActivity != null) {
                    mActivity.newFolder();
                }
                return true;

            case KeyEvent.KEYCODE_SEARCH:
                mSearchExecutor.run();
                return true;

            default:
                // Instead of duplicating the switch-case in #isNavigationKeyCode, best just to
                // leave it here.
                if (Events.isNavigationKeyCode(keyCode)) {
                    // Forward all unclaimed navigation keystrokes to the directory list.
                    // This causes any stray navigation keystrokes to focus the content pane,
                    // which is probably what the user is trying to do.
                    mFocusManager.focusDirectoryList();
                    return true;
                }
                return false;
        }
    }

    /**
     * Moves focus forward through the on-screen focusable areas, mirroring what pressing Tab does
     * with a keyboard. Runs entirely in-process, so it needs no INJECT_EVENTS permission and does
     * not spawn a shell.
     */
    private boolean advanceFocusForward() {
        if (mActivity == null || mActivity.getWindow() == null) {
            return false;
        }
        final View current = mActivity.getCurrentFocus();
        final View from = (current != null) ? current : mActivity.getWindow().getDecorView();
        final View next = from.focusSearch(View.FOCUS_FORWARD);
        return next != null && next.requestFocus(View.FOCUS_FORWARD);
    }

    /**
     * Delivers a synthetic key press to the currently focused view without leaving the process.
     * This reuses the existing per-list page-scroll handling (RecyclerView/ListView) for the
     * gamepad triggers instead of shelling out to {@code input keyevent}.
     */
    private boolean dispatchToFocusedView(int keyCode) {
        if (mActivity == null || mActivity.getWindow() == null) {
            return false;
        }
        final Window window = mActivity.getWindow();
        final long now = SystemClock.uptimeMillis();
        window.superDispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        window.superDispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        return true;
    }

    private boolean onTab() {
        if (!mFeatures.isSystemKeyboardNavigationEnabled()) {
            // Tab toggles focus on the navigation drawer.
            // This should only be called in pre-O devices, since O has built-in keyboard
            // navigation
            // support.
            mFocusManager.advanceFocusArea();
            return true;
        }

        return false;
    }

    private boolean onDelete() {
        mDirPopper.run();
        return true;
    }

    private boolean onBack() {
        if (mDrawer.isPresent() && mDrawer.isOpen()) {
            mDrawer.setOpen(false);
            return true;
        }

        if (mSearchCanceler.run()) {
            return true;
        }

        if (mSelectionMgr.hasSelection()) {
            if (DEBUG) {
                Log.d(TAG, "Back pressed. Clearing existing selection.");
            }
            mSelectionMgr.clearSelection();
            return true;
        }

        return mDirPopper.run();
    }

    private boolean onEscape() {
        if (mSearchCanceler.run()) {
            return true;
        }

        if (mSelectionMgr.hasSelection()) {
            if (DEBUG) {
                Log.d(TAG, "ESC pressed. Clearing existing selection.");
            }
            mSelectionMgr.clearSelection();
            return true;
        }

        return true;
    }
}
