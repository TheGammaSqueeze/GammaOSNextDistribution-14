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
package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Helper.newImportantView;

import android.content.ComponentName;
import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.View;
import android.view.contentcapture.ContentCaptureSessionId;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.atomic.AtomicReference;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class PartialNotImportantActivityTest extends
        AbstractContentCaptureIntegrationTest {

    private static final String TAG = PartialNotImportantActivityTest.class.getSimpleName();

    ActivityScenario<PartialNotImportantActivity> mScenario;

    @NotNull
    @Override
    protected TestRule getMainTestRule() {
        return (base, description) -> base;
    }

    @Before
    @After
    public void resetActivityStaticState() {
        if (mScenario != null) {
            mScenario.close();
            mScenario = null;
        }
        PartialNotImportantActivity.onRootView((activity, view) -> { /* do nothing */ });
    }

    private ComponentName getComponentName() {
        return new ComponentName(
                PartialNotImportantActivity.class.getPackageName(),
                PartialNotImportantActivity.class.getName());
    }

    @Test
    public void testAddAndRemoveNoImportantChild() throws Exception {
        final CtsContentCaptureService service = enableService();

        // Child must be created inside the lambda because it needs to use the Activity context.
        final AtomicReference<TextView> childRef = new AtomicReference<>();
        final AtomicReference<LinearLayout> containerRef = new AtomicReference<>();
        PartialNotImportantActivity.onRootView((activity, container) -> {
            final TextView child = new TextView(activity);
            child.setText("VIEW, Y U NO IMPORTANT?");
            child.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);
            childRef.set(child);
            container.addView(child);
            containerRef.set(container);
        });

        mScenario = ActivityScenario.launch(PartialNotImportantActivity.class);

        // Remove view
        final LinearLayout container = containerRef.get();
        final TextView child = childRef.get();
        mScenario.onActivity((activity) -> container.removeView(child));

        // Don't use mScenario.moveToState(State.DESTROYED) because InstrumentationActivityInvoker
        // will start an empty activity to stop the activity before calling Activity#finish()
        // as a workaround for the framework bug where the framework may not call #onStop and
        // #onDestroy if you call Activity#finish() while it is resumed. A new session is started
        // for the empty activity.
        mScenario.onActivity((activity) -> activity.finish());

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, getComponentName());

        // Assert just the relevant events
        // This test case will doesn't have an event for the view that
        // id is parent_not_important before the textView.
        new EventsAssertor(session.getEvents())
                .assertNoEvent(child)
                .assertNoEvent(container);
    }

    @Test
    public void testAddAndRemoveImportantChild() throws Exception {
        final CtsContentCaptureService service = enableService();

        // Child must be created inside the lambda because it needs to use the Activity context.
        final AtomicReference<TextView> childRef = new AtomicReference<>();
        final AtomicReference<LinearLayout> containerRef = new AtomicReference<>();
        PartialNotImportantActivity.onRootView((activity, container) -> {
            final TextView text = newImportantView(activity, "Important I am");
            childRef.set(text);
            container.addView(text);
            containerRef.set(container);
        });

        mScenario = ActivityScenario.launch(PartialNotImportantActivity.class);

        // Remove view
        final LinearLayout container = containerRef.get();
        final TextView child = childRef.get();
        mScenario.onActivity((activity) -> container.removeView(child));

        mScenario.onActivity((activity) -> activity.finish());

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, getComponentName());

        final View decorView = container.getRootView();
        final View root = decorView.findViewById(R.id.root_view);
        final View notImportantParentView = decorView.findViewById(R.id.parent_not_important);

        // Assert just the relevant events
        // Because the child view is important for Content Capture, then its parent
        // is also become important for Content Capture. So the view that id is
        // parent_not_important should have an appeared event.
        new EventsAssertor(session.getEvents())
                .assertViewTreeStarted()
                .assertViewAppeared(notImportantParentView, root.getAutofillId())
                .assertViewAppeared(container, notImportantParentView.getAutofillId())
                .assertViewAppeared(child, container.getAutofillId())
                .assertViewDisappeared(child.getAutofillId());
    }

    @Test
    public void testAddImportantChildAfterSessionStarted() throws Exception {
        final CtsContentCaptureService service = enableService();

        // Child must be created inside the lambda because it needs to use the Activity context.
        final AtomicReference<TextView> childRef = new AtomicReference<>();
        final AtomicReference<LinearLayout> containerRef = new AtomicReference<>();
        PartialNotImportantActivity.onRootView((activity, container) -> {
            containerRef.set(container);
        });

        mScenario = ActivityScenario.launch(PartialNotImportantActivity.class);

        final LinearLayout container = containerRef.get();
        mScenario.onActivity((activity) -> {
            final TextView child = newImportantView(activity, "Important I am");
            childRef.set(child);
            container.addView(child);
        });

        mScenario.onActivity((activity) -> activity.finish());

        // Add an important view
        final TextView child = childRef.get();

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, getComponentName());

        final View decorView = container.getRootView();
        final View root = decorView.findViewById(R.id.root_view);
        final View notImportantParentView = decorView.findViewById(R.id.parent_not_important);

        // Assert just the relevant events
        new EventsAssertor(session.getEvents())
                .assertViewTreeStarted()
                .assertViewTreeFinished()
                .assertViewTreeStarted()
                .assertViewAppeared(notImportantParentView, root.getAutofillId())
                .assertViewAppeared(container, notImportantParentView.getAutofillId())
                .assertViewAppeared(child, container.getAutofillId());
    }

}
