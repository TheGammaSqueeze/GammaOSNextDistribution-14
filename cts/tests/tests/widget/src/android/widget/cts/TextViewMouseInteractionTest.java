/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;


import static android.content.pm.PackageManager.FEATURE_TOUCHSCREEN;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.allOf;

import android.view.ActionMode;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextViewMouseInteractionTest {
    private static final String CUSTOM_FLOATING_TOOLBAR_LABEL = "@B(DE";

    @Rule
    public ActivityScenarioRule<TextViewMouseInteractionActivity> rule = new ActivityScenarioRule<>(
            TextViewMouseInteractionActivity.class);

    private static final UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(
                ApplicationProvider.getApplicationContext().getPackageManager()
                        .hasSystemFeature(FEATURE_TOUCHSCREEN));
        sDevice.wakeUp();
        dismissKeyguard();
        closeSystemDialog();
    }

    private void dismissKeyguard() {
        ShellUtils.runShellCommand("wm dismiss-keyguard");
    }

    private static void closeSystemDialog() {
        ShellUtils.runShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
    }

    ActionMode.Callback mTestCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_floating_toolbar, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }
    };

    @Test
    @ApiTest(apis = {"android.widget.TextView#setCustomSelectionActionModeCallback"})
    public void testFloatingToolbarByTouch() {
        final String text = "Android";
        rule.getScenario().onActivity(activity -> {
            final TextView textView = activity.findViewById(R.id.textView);
            textView.setTextIsSelectable(true);
            textView.setCustomSelectionActionModeCallback(mTestCallback);
            textView.setText(text);
        });

        onView(allOf(withId(R.id.textView), withText(text))).check(matches(isDisplayed()));
        onView(withId(R.id.textView)).perform(new GeneralClickAction(
                Tap.LONG, GeneralLocation.CENTER, Press.PINPOINT, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.BUTTON_PRIMARY));

        assertThat(sDevice.hasObject(By.text(CUSTOM_FLOATING_TOOLBAR_LABEL))).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.widget.TextView#setCustomSelectionActionModeCallback"})
    public void testFloatingToolbarByMouse() {
        final String text = "Android";
        rule.getScenario().onActivity(activity -> {
            final TextView textView = activity.findViewById(R.id.textView);
            textView.setTextIsSelectable(true);
            textView.setCustomSelectionActionModeCallback(mTestCallback);
            textView.setText(text);
        });

        onView(allOf(withId(R.id.textView), withText(text))).check(matches(isDisplayed()));
        onView(withId(R.id.textView)).perform(new GeneralClickAction(
                Tap.LONG, GeneralLocation.CENTER, Press.PINPOINT, InputDevice.SOURCE_MOUSE,
                MotionEvent.BUTTON_PRIMARY));

        assertThat(sDevice.hasObject(By.text(CUSTOM_FLOATING_TOOLBAR_LABEL))).isFalse();
    }
}

