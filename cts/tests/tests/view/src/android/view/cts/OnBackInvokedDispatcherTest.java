/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Dialog;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.View;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;

/**
 * Test {@link OnBackInvokedDispatcher}.
 */
@MediumTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class OnBackInvokedDispatcherTest {
    private OnBackInvokedDispatcherTestActivity mActivity;
    private Dialog mDialog;

    @Rule
    public ActivityScenarioRule<OnBackInvokedDispatcherTestActivity> mActivityRule =
            new ActivityScenarioRule<>(OnBackInvokedDispatcherTestActivity.class);

    @Before
    public void setUp() {
        mActivityRule.getScenario().moveToState(Lifecycle.State.RESUMED);
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
            mDialog = mActivity.getDialog();
        });
    }

    @Test
    public void testGetDispatcherOnDialog() {
        OnBackInvokedDispatcher dialogDispatcher = mDialog.getOnBackInvokedDispatcher();
        assertNotNull("OnBackInvokedDispatcher on Dialog should not be null", dialogDispatcher);
    }

    @Test
    public void findDispatcherOnView() {
        final OnBackInvokedDispatcher[] windowOnBackInvokedDispatcher = {null};
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
            mDialog = mActivity.getDialog();
            View view = new View(activity) {
                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    windowOnBackInvokedDispatcher[0] = findOnBackInvokedDispatcher();
                }
            };
            assertNull("View is not attached, it should not have an OnBackInvokedDispatcher",
                    view.findOnBackInvokedDispatcher());
            activity.setContentView(view);
        });
        assertNotNull("View is attached, it should have an OnBackInvokedDispatcher",
                windowOnBackInvokedDispatcher[0]);
    }

    @Test
    public void findDispatcherOnViewGroup() {
        final HashMap<String, View> viewMap = new HashMap<>();
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
            mDialog = mActivity.getDialog();
            FrameLayout root = new FrameLayout(activity) {
                @Nullable
                @Override
                public OnBackInvokedDispatcher findOnBackInvokedDispatcherForChild(
                        @NonNull View child, @NonNull View requester) {
                    viewMap.put("root_child", child);
                    viewMap.put("root_requester", requester);
                    return super.findOnBackInvokedDispatcherForChild(child, requester);
                }
            };
            FrameLayout parent = new FrameLayout(activity) {
                @Nullable
                @Override
                public OnBackInvokedDispatcher findOnBackInvokedDispatcherForChild(
                        @NonNull View child, @NonNull View requester) {
                    viewMap.put("parent_child", child);
                    viewMap.put("parent_requester", requester);
                    return super.findOnBackInvokedDispatcherForChild(child, requester);
                }
            };
            View view = new View(activity);
            viewMap.put("root", root);
            viewMap.put("parent", parent);
            viewMap.put("view", view);

            root.addView(parent);
            parent.addView(view);
            activity.setContentView(root);
        });

        View view = viewMap.get("view");
        View parent = viewMap.get("parent");
        assertNotNull("View is attached, it should have an OnBackInvokedDispatcher",
                view.findOnBackInvokedDispatcher());
        assertEquals("Requester from root should be the leaf view",
                view, viewMap.get("root_requester"));
        assertEquals("Child from root should be the direct child",
                parent, viewMap.get("root_child"));
        assertEquals("Requester from direct parent should be the direct child",
                view, viewMap.get("parent_requester"));
        assertEquals("Child from parent should be the direct child",
                view, viewMap.get("parent_child"));
    }

    @Test
    public void testRegisterAndUnregisterCallbacks() {
        OnBackInvokedDispatcher dispatcher = mActivity.getOnBackInvokedDispatcher();
        OnBackInvokedCallback callback1 = createBackCallback();
        OnBackInvokedCallback callback2 = createBackCallback();
        dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback1);
        dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback2);
        dispatcher.unregisterOnBackInvokedCallback(callback2);
        dispatcher.unregisterOnBackInvokedCallback(callback1);
        dispatcher.unregisterOnBackInvokedCallback(callback2);
    }

    private OnBackInvokedCallback createBackCallback() {
        return () -> {};
    }
}
