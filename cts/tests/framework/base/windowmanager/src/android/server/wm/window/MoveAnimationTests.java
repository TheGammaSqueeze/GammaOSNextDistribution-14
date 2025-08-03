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

package android.server.wm.window;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.server.wm.Condition;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.server.wm.settings.SettingsSession;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * Test the following APIs for controlling FLAG_NO_MOVE_ANIMATION:
 * <ul>
 * <li>{@code WindowManager.LayoutParams.setCanPlayMoveAnimation(boolean)}
 * <li>{@code WindowManager.LayoutParams.canPlayMoveAnimation()}
 * <li>{@code android:style/windowNoMoveAnimation}
 * </ul>
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:MoveAnimationTests
 */
public class MoveAnimationTests extends WindowManagerTestBase {

    /**
     * All tests in this class run with window animation scaling set to 20.0f
     */
    @ClassRule
    public static final TestRule sWindowAnimationRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE),
            Settings.Global::getFloat,
            Settings.Global::putFloat,
            20.0f);

    /**
     * Activity with a theme setting {@code windowIsFloating} as {@code true} to get the default
     * behavior of floating window move animations.
     */
    public static class FloatingActivity extends FocusableActivity {

        protected View mContentView;

        /**
         * Instance of {@link FloatingActivity} with a theme setting {@code windowNoMoveAnimation}
         * as {@code true}.
         */
        public static class NoMove extends FloatingActivity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mContentView = new View(this);
            mContentView.setBackgroundColor(Color.BLUE);

            setContentSquare(40);
        }

        public void setContentSquare(int size) {
            getWindow().setContentView(mContentView, new ViewGroup.LayoutParams(size, size));

            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.gravity = Gravity.RIGHT | Gravity.BOTTOM;
            getWindow().setAttributes(attrs);
        }
    }

    @Test
    public void testCanPlayMoveAnimationByDefault() {
        final FloatingActivity activity =
                startActivityInWindowingModeFullScreen(FloatingActivity.class);
        final Window window = activity.getWindow();

        // Default state should be TRUE because the activity has done nothing to override it.
        assertTrue("Floating windows should play move animations by default",
                window.getAttributes().canPlayMoveAnimation());
        assertPlaysMoveAnimation(activity, true);
    }

    @Test
    public void testCanOverrideTheme() {
        final FloatingActivity.NoMove activity =
                startActivityInWindowingModeFullScreen(FloatingActivity.NoMove.class);
        final Window window = activity.getWindow();

        // Default state should be FALSE because this Activity uses a theme with no move animation.
        assertFalse("Themes should be able to prevent move animations via windowNoMoveAnimation",
                window.getAttributes().canPlayMoveAnimation());

        // Window API should be able to override theme defaults from FALSE to TRUE.
        getInstrumentation().runOnMainSync(() -> {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.setCanPlayMoveAnimation(true);
            window.setAttributes(attrs);
        });

        assertTrue("Window should know that it can play a move animation",
                window.getAttributes().canPlayMoveAnimation());
        assertPlaysMoveAnimation(activity, true);
    }

    @Test
    public void testThemeCanDisable() {
        final FloatingActivity.NoMove activity =
                startActivityInWindowingModeFullScreen(FloatingActivity.NoMove.class);
        final Window window = activity.getWindow();

        // Window API should be able to override theme defaults from TRUE to FALSE.
        getInstrumentation().runOnMainSync(() -> {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.setCanPlayMoveAnimation(false);
            window.setAttributes(attrs);
        });

        assertFalse("Window should know that it can NOT play a move animation",
                window.getAttributes().canPlayMoveAnimation());
        assertPlaysMoveAnimation(activity, false);
    }

    private void assertPlaysMoveAnimation(final FloatingActivity activity, final boolean isPlayed) {
        mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY);

        activity.mContentView.post(() -> activity.setContentSquare(200));

        Condition isAnimating = new Condition("Window is animating",
                () -> {
                        mWmState.computeState();
                        WindowManagerState.Activity ar =
                                mWmState.getActivity(activity.getComponentName());
                        return ar != null && ar.isAnimating();
                })
                .setRetryIntervalMs(16)
                .setRetryLimit(50);
        assertEquals(isPlayed, Condition.waitFor(isAnimating));
    }
}
