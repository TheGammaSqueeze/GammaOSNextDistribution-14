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

package android.app.cts;

import android.app.Instrumentation;
import android.app.stubs.DialogStubActivity;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WindowUtil;

import org.junit.Before;
import org.junit.Rule;

/**
 * Base class for AlertDialog_Builder tests.
 */
public abstract class AlertDialog_BuilderTestBase {

    private static final long TOUCH_MODE_PROPAGATION_TIMEOUT_MILLIS = 5_000L;

    protected Instrumentation mInstrumentation;
    protected DialogStubActivity mDialogActivity;

    @Rule
    public ActivityScenarioRule<DialogStubActivity> mActivityRule =
            new ActivityScenarioRule<>(DialogStubActivity.class);

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityRule.getScenario().onActivity(a -> mDialogActivity = a);
        WindowUtil.waitForFocus(mDialogActivity);
    }

    /**
     * Re-attaches the floating {@link ListView} passed as argument to the activity started by the
     * test's activity scenario, and then sets the adapter again. This will ensure that
     * {@link ListView#lookForSelectablePosition} will be able to properly position its cursor.
     *
     * When setting the adapter, {@link ListView#lookForSelectablePosition} won't be able to
     * position its cursor if the ListView is in touch mode (#lookForSelectablePosition requires the
     * {@link ListView} to not be in touch mode).
     */
    void reAttachListViewAdapter(ListView listView) {
        final ListAdapter[] adapter = new ListAdapter[1];
        mActivityRule.getScenario().onActivity(a -> {
            adapter[0] = listView.getAdapter();
            ((ViewGroup) listView.getParent()).removeView(listView);
            a.addContentView(listView, new ViewGroup.LayoutParams(1, 1));
        });
        mInstrumentation.setInTouchMode(false);
        PollingCheck.waitFor(TOUCH_MODE_PROPAGATION_TIMEOUT_MILLIS,
                () -> !listView.isInTouchMode());
        mActivityRule.getScenario().onActivity(a -> listView.setAdapter(adapter[0]));
    }
}
