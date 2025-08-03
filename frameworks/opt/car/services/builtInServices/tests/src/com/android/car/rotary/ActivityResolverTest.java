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

package com.android.car.rotary;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Condition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * This test opens KitchenSink and verifies that ActivityResolver is supported by rotary controller.
 * The test injects TAB KeyEvents to simulate rotary rotation events because the way RotaryService
 * handles rotary rotation events is similar to the way Android framework handles TAB KeyEvent.
 */
public final class ActivityResolverTest {

    private static final long WAIT_TIMEOUT_MS = 3_000;
    private static final String TRIGGER_ACTIVITY_RESOLVER_RESOURCE_ID =
            "com.google.android.car.kitchensink:id/trigger_activity_resolver";
    private static final String DISMISS_BUTTON_RESOURCE_ID =
            "com.google.android.car.kitchensink:id/dismiss_button";

    private static final String KITCHEN_SINK_APP = "com.google.android.car.kitchensink";

    private Instrumentation mInstrumentation;
    private UiDevice mDevice;

    @Before
    public void setUp() throws IOException {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        closeKitchenSink();
    }

    @After
    public void tearDown() throws IOException {
        closeKitchenSink();
    }

    private void closeKitchenSink() throws IOException {
        mDevice.executeShellCommand(String.format("am force-stop %s", KITCHEN_SINK_APP));
    }

    @Test
    public void testListItemFocusable_threeItems() throws UiObjectNotFoundException, IOException {
        launchResolverActivity();
        assumeTrue(hasThreeListItems());

        // When the ListView is focusable, it'll be focused after pressing TAB key. In this case,
        // press the TAB key again to get the first list item focused.
        UiObject list = mDevice.findObject(
                new UiSelector().className(android.widget.ListView.class));
        if (list.isFocusable()) {
            mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        }

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem1 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(0));
        waitAndAssertFocused(listItem1);

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem2 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(1));
        waitAndAssertFocused(listItem2);

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem3 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(2));
        waitAndAssertFocused(listItem3);

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        // The focus shouldn't move since it has reached the last item.
        waitAndAssertFocused(listItem3);
    }

    @Test
    public void testListItemFocusable_twoItems() throws UiObjectNotFoundException, IOException {
        launchResolverActivity();
        assumeTrue(!hasThreeListItems());

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        // When the ListView is focusable, it'll be focused after pressing TAB key. In this case,
        // press the TAB key again to get the "Just once" button focused.
        UiObject list = mDevice.findObject(
                new UiSelector().className(android.widget.ListView.class));
        if (list.isFocusable()) {
            mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        }
        UiObject justOnceButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(true).instance(0));
        waitAndAssertFocused(justOnceButton);

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject alwaysButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(true).instance(1));
        waitAndAssertFocused(alwaysButton);


        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem1 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(0));
        waitAndAssertFocused(listItem1);

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem2 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(1));
        waitAndAssertFocused(listItem2);
    }

    @Test
    public void testActionButtonsNotFocusable_threeItems()
            throws UiObjectNotFoundException, IOException {
        launchResolverActivity();
        assumeTrue(hasThreeListItems());

        // The two buttons should be disabled if the test activity is never opened by
        // ActivityResolver.
        UiObject justOnceButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(false).instance(0));
        assertWithMessage("Failed to find the disabled justOnceButton")
                .that(justOnceButton.exists())
                .isTrue();

        UiObject alwaysButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(false).instance(1));
        assertWithMessage("Failed to find the disabled alwaysButton")
                .that(alwaysButton.exists())
                .isTrue();
    }

    @Test
    public void testClickListItem_threeItems() throws UiObjectNotFoundException, IOException {
        launchResolverActivity();
        assumeTrue(hasThreeListItems());

        // Press twice to make sure a list item gets focused.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focused(true));
        waitAndAssertFocused(listItem);

        // Simulate rotary click on the focused listItem.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER);

        // After rotary click, the list will be re-layouted, and the listItem will lose focus.
        // As a result, Android framework will focus on the first focusable & enabled view in app
        // window, which is the justOnceButton.
        UiObject justOnceButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(true).instance(0));
        waitAndAssertFocused(justOnceButton);

        // Simulate rotary clockwise rotation.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject alwaysButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(true).instance(1));
        waitAndAssertFocused(alwaysButton);

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        UiObject listItem1 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(0));
        waitAndAssertFocused(listItem1);

        // Simulate rotary click on the listItem.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER);

        UiObject dismissButton =
                mDevice.findObject(new UiSelector().resourceId(DISMISS_BUTTON_RESOURCE_ID));
        waitAndAssertFocused(dismissButton);
    }

    @Test
    public void testClickListItem_twoItems() throws UiObjectNotFoundException, IOException {
        launchResolverActivity();
        assumeTrue(!hasThreeListItems());


        // When the ListView is focusable, it needs 4 rotations to focus on the list item.
        // Otherwise, it needs 3 rotations.
        UiObject list = mDevice.findObject(
                new UiSelector().className(android.widget.ListView.class));
        if (list.isFocusable()) {
            mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        }
        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);

        UiObject listItem = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focused(true));
        waitAndAssertFocused(listItem);

        // Simulate rotary click on the listItem.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER);

        UiObject dismissButton =
                mDevice.findObject(new UiSelector().resourceId(DISMISS_BUTTON_RESOURCE_ID));
        waitAndAssertFocused(dismissButton);
    }

    @Test
    public void testClickJustOnceButton_twoItems() throws UiObjectNotFoundException, IOException {
        launchResolverActivity();
        assumeTrue(!hasThreeListItems());

        mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        // When the ListView is focusable, it'll be focused after pressing TAB key. In this case,
        // press the TAB key again to get the "Just once" button focused.
        UiObject list = mDevice.findObject(
                new UiSelector().className(android.widget.ListView.class));
        if (list.isFocusable()) {
            mDevice.pressKeyCode(KeyEvent.KEYCODE_TAB);
        }
        UiObject justOnceButton = mDevice.findObject(new UiSelector()
                .className(android.widget.Button.class).focusable(true).enabled(true).instance(0));
        waitAndAssertFocused(justOnceButton);

        // Simulate rotary click on the justOnceButton.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER);

        UiObject dismissButton =
                mDevice.findObject(new UiSelector().resourceId(DISMISS_BUTTON_RESOURCE_ID));
        waitAndAssertFocused(dismissButton);
    }

    private boolean hasThreeListItems() {
        // If a test activity has been launched by ActivityResolver before, the "Just once" button
        // and "Always" button will be enabled, and the list will only show two items.
        // Otherwise, the two buttons will be disabled and the list will show all three items.
        // So the test will be different depending on whether it is the first time to run or not.
        UiObject listItem3 = mDevice.findObject(new UiSelector()
                .className(android.widget.LinearLayout.class).focusable(true).instance(2));
        return listItem3.exists();
    }

    private void launchResolverActivity() throws UiObjectNotFoundException {
        // Open KitchenSink > Activity Resolver
        Intent intent = mInstrumentation
                .getContext()
                .getPackageManager()
                .getLaunchIntentForPackage(KITCHEN_SINK_APP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("select", "activity resolver");
        mInstrumentation.getContext().startActivity(intent);

        UiObject button = mDevice.findObject(new UiSelector().resourceId(
                TRIGGER_ACTIVITY_RESOLVER_RESOURCE_ID));
        button.click();
        mDevice.waitForIdle();
    }

    private void waitAndAssertFocused(UiObject view) throws UiObjectNotFoundException {
        mDevice.wait(isViewFocused(view), WAIT_TIMEOUT_MS);
        assertWithMessage("The view " + view + " should be focused")
                .that(view.isFocused())
                .isTrue();
    }

    private static Condition<UiDevice, Boolean> isViewFocused(UiObject view) {
        return unusedDevice -> {
            try {
                return view.isFocused();
            } catch (UiObjectNotFoundException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
