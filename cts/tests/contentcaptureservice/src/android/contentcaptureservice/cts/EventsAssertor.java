/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.contentcaptureservice.cts.Assertions.assertSessionId;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.ViewNode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Used to assert Content Capture events by the event order.
 *
 * When the type is the same, the assertor will assert the content of the event.
 * If the current event is different type, this event will be skipped, and then
 * try to assert the next event. Through all events, the assertor will report an
 * "not found" error.
 */
public class EventsAssertor {
    private static final String TAG = "CtsContentCapture";
    private final List<ContentCaptureEvent> mEvents;
    private int mNextEvent = 0;

    public EventsAssertor(@NonNull List<ContentCaptureEvent> events) {
        mEvents = Collections.unmodifiableList(events);
    }

    @NonNull
    public EventsAssertor isAtLeast(int size) {
        assertThat(mEvents.size()).isAtLeast(size);
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_SESSION_RESUMED} event.
     */
    @NonNull
    public EventsAssertor assertSessionResumed() {
        assertNextEvent((event) -> assertSessionLevelEvent(event),
                ContentCaptureEvent.TYPE_SESSION_RESUMED, "no SESSION_RESUMED event");
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_SESSION_PAUSED} event.
     */
    @NonNull
    public EventsAssertor assertSessionPaused() {
        assertNextEvent((event) -> assertSessionLevelEvent(event),
                ContentCaptureEvent.TYPE_SESSION_PAUSED, "no SESSION_PAUSED event");
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_TREE_APPEARING} event.
     */
    @NonNull
    public EventsAssertor assertViewTreeStarted() {
        assertNextEvent((event) -> assertSessionLevelEvent(event),
                ContentCaptureEvent.TYPE_VIEW_TREE_APPEARING, "no VIEW_TREE_APPEARING event");
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_TREE_APPEARED} event.
     */
    @NonNull
    public EventsAssertor assertViewTreeFinished() {
        assertNextEvent((event) -> assertSessionLevelEvent(event),
                ContentCaptureEvent.TYPE_VIEW_TREE_APPEARED,
                "no VIEW_TREE_APPEARED event");
        return this;
    }

    /**
     * Asserts the basic contents of a {@link ContentCaptureEvent#TYPE_CONTEXT_UPDATED} event.
     */
    public EventsAssertor assertContextUpdated() {
        assertNextEvent((event) -> assertSessionLevelEvent(event),
                ContentCaptureEvent.TYPE_CONTEXT_UPDATED,
                "no TYPE_CONTEXT_UPDATED event");
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED}
     * event for a decor view.
     *
     * <P>The decor view is typically internal, so there isn't much we can assert,
     * other than its autofill id.
     */
    @NonNull
    public EventsAssertor assertDecorViewAppeared(@NonNull View expectedDecorView) {
        assertNextEvent((event) -> assertEventId(event, expectedDecorView),
                ContentCaptureEvent.TYPE_VIEW_APPEARED,
                "no VIEW_APPEARED event for decor view");
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED}
     * event, without checking for parent id.
     */
    @NonNull
    public EventsAssertor assertViewAppeared(@NonNull View expectedView) {
        assertNextEvent((event) -> assertViewEvent(event, expectedView),
                ContentCaptureEvent.TYPE_VIEW_APPEARED,
                String.format("no VIEW_APPEARED event for %s", expectedView));
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED} event.
     */
    @NonNull
    public EventsAssertor assertViewAppeared(@NonNull View expectedView,
            @NonNull AutofillId expectedParentId) {
        assertNextEvent((event) -> assertViewEvent(event, expectedView, expectedParentId),
                ContentCaptureEvent.TYPE_VIEW_APPEARED,
                String.format("no VIEW_APPEARED event for %s", expectedView));
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED} event.
     */
    @NonNull
    public EventsAssertor assertViewAppeared(@NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull View expectedView, @NonNull AutofillId expectedParentId) {
        assertViewAppeared(expectedView, expectedParentId);
        assertSessionId(expectedSessionId, expectedView);
        return this;
    }


    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED} event.
     */
    @NonNull
    public EventsAssertor assertViewAppeared(@NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull View expectedView) {
        assertViewAppeared(expectedView);
        assertSessionId(expectedSessionId, expectedView);
        return this;
    }

    /**
     * Asserts the {@code text} of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED}
     * event.
     */
    @NonNull
    public EventsAssertor assertViewAppeared(@NonNull String text) {
        assertNextEvent((event) -> assertEventText(event, text),
                ContentCaptureEvent.TYPE_VIEW_APPEARED,
                String.format("no TYPE_VIEW_APPEARED event with text: %s", text));
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_DISAPPEARED}
     * event for multiple views.
     */
    @NonNull
    public EventsAssertor assertViewDisappeared(@NonNull AutofillId autofillId) {
        assertNextEvent((event) -> assertDisappearedEvent(event, autofillId),
                ContentCaptureEvent.TYPE_VIEW_DISAPPEARED,
                String.format("no VIEW_DISAPPEARED event for %s", autofillId));
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_DISAPPEARED}
     * event for multiple views.
     */
    @NonNull
    public EventsAssertor assertViewDisappeared(@NonNull AutofillId... autofillIds) {
        assertNextEvent((event) -> assertDisappearedEvent(event, autofillIds),
                ContentCaptureEvent.TYPE_VIEW_DISAPPEARED,
                String.format("no VIEW_DISAPPEARED event for %s", Arrays.toString(autofillIds)));
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_APPEARED}
     * event for a virtual node.
     */
    @NonNull
    public EventsAssertor assertVirtualViewAppeared(@NonNull ContentCaptureSession session,
            @NonNull AutofillId parentId, int childId, String expectedText) {
        final AutofillId expectedId = session.newAutofillId(parentId, childId);
        assertNextEvent((event) -> assertVirtualViewEvent(event, expectedId, expectedText),
                ContentCaptureEvent.TYPE_VIEW_APPEARED,
                String.format("no VIEW_APPEARED event for %s", expectedId.toString()));
        return this;
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_DISAPPEARED}
     * event for a virtual node.
     */
    @NonNull
    public EventsAssertor assertVirtualViewDisappeared(AutofillId parentId,
            ContentCaptureSession session, int childId) {
        return assertViewDisappeared(session.newAutofillId(parentId, childId));
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_DISAPPEARED}
     * event for many virtual nodes.
     */
    @NonNull
    public EventsAssertor assertVirtualViewsDisappeared(AutofillId parentId,
            ContentCaptureSession session, int... childId) {
        final AutofillId[] ids = new AutofillId[childId.length];
        for (int i = 0; i < childId.length; i++) {
            ids[i] = session.newAutofillId(parentId, childId[i]);
        }
        return assertViewDisappeared(ids);
    }

    /**
     * Asserts the contents of a {@link ContentCaptureEvent#TYPE_VIEW_TEXT_CHANGED} event.
     */
    @NonNull
    public EventsAssertor assertViewTextChanged(AutofillId expectedId, String expectedText) {
        assertNextEvent((event) -> assertTextChangedEvent(event, expectedId, expectedText),
                ContentCaptureEvent.TYPE_VIEW_TEXT_CHANGED,
                String.format("no TYPE_VIEW_TEXT_CHANGED event for %s:%s",
                        expectedId, expectedText));
        return this;
    }

    @Nullable
    private String assertTextChangedEvent(@NonNull ContentCaptureEvent event,
            @NonNull AutofillId expectedId, @NonNull String expectedText) {
        assertWithMessage("Wrong id on %s", event).that(event.getId())
                .isEqualTo(expectedId);
        assertWithMessage("Wrong text on %s", event).that(event.getText().toString())
                .isEqualTo(expectedText);
        return null;
    }

    @Nullable
    private String assertVirtualViewEvent(@NonNull ContentCaptureEvent event,
            @NonNull AutofillId expectedId, @Nullable String expectedText) {
        final ViewNode node = event.getViewNode();
        assertThat(node).isNotNull();
        if (!node.getAutofillId().equals(expectedId)) {
            return String.format("wrong autofill id (expected %s, actual is %s) at %s",
                    expectedId, node.getAutofillId(), event);
        }
        if (expectedText != null && node.getText() != null) {
            assertWithMessage("wrong text on %s", event)
                    .that(node.getText().toString()).isEqualTo(expectedText);
        } else {
            assertWithMessage("%s should not have text", node)
                    .that(node.getText()).isNull();
        }
        return null;
    }

    @Nullable
    private String assertDisappearedEvent(@NonNull ContentCaptureEvent event,
            @NonNull AutofillId expectedId) {
        assertCommonViewDisappearedProperties(event);
        assertWithMessage("wrong autofillId on event %s", event)
                .that(event.getId()).isEqualTo(expectedId);
        assertWithMessage("event %s should not have autofillIds", event)
                .that(event.getIds()).isNull();
        return null;
    }

    @Nullable
    private String assertDisappearedEvent(@NonNull ContentCaptureEvent event,
            @NonNull AutofillId[] expectedIds) {
        final List<AutofillId> ids = event.getIds();

        assertCommonViewDisappearedProperties(event);
        assertWithMessage("no autofillIds on event %s", event).that(ids)
                .isNotNull();
        assertWithMessage("wrong autofillId on event %s", event)
                .that(ids).containsExactly((Object[]) expectedIds);
        assertWithMessage("event %s should not have autofillId", event)
                .that(event.getId()).isNull();
        return null;
    }

    private void assertCommonViewDisappearedProperties(@NonNull ContentCaptureEvent event) {
        assertWithMessage("event %s should not have a ViewNode", event)
                .that(event.getViewNode()).isNull();
        assertWithMessage("event %s should not have text", event)
                .that(event.getText()).isNull();
    }

    @Nullable
    private String assertViewEvent(@NonNull ContentCaptureEvent event, @NonNull View expectedView) {
        return assertViewEvent(event, expectedView, /* expectedParentId */ null);
    }

    @Nullable
    private String assertViewEvent(@NonNull ContentCaptureEvent event, @NonNull View expectedView,
            @Nullable AutofillId expectedParentId) {
        return assertEvent(event, expectedView, expectedParentId, /* expectedText */  null);
    }

    @Nullable
    private String assertViewEvent(@NonNull ContentCaptureEvent event, @NonNull View expectedView,
            AutofillId expectedParentId, String expectedText) {
        return assertEvent(event, expectedView, expectedParentId, expectedText);
    }

    private String assertEventId(@NonNull ContentCaptureEvent event, View expectedView) {
        final ViewNode node = event.getViewNode();

        if (node == null) {
            return String.format("node is null at %s", event);
        }

        if (expectedView != null && !node.getAutofillId().equals(expectedView.getAutofillId())) {
            return String.format("wrong event id (expected %s, actual is %s) at %s",
                    expectedView.getAutofillId(), node.getAutofillId(), event);
        }
        return null;
    }

    private String assertEventText(@NonNull ContentCaptureEvent event, String expectedText) {
        final ViewNode node = event.getViewNode();
        if (node == null) {
            return String.format("node is null at %s", event);
        }

        if (!TextUtils.equals(node.getText(), expectedText)) {
            return String.format("wrong event text (expected %s, actual is %s) at %s",
                    expectedText, node.getText(), event);
        }
        return null;
    }

    private String assertEvent(@NonNull ContentCaptureEvent event, @Nullable View expectedView,
            @Nullable AutofillId expectedParentId, @Nullable String expectedText) {
        final ViewNode node = event.getViewNode();
        if (node == null) {
            return String.format("node is null at %s", event);
        }

        if (expectedView != null && !node.getAutofillId().equals(expectedView.getAutofillId())) {
            return String.format("wrong event id (expected %s, actual is %s) at %s",
                    expectedView.getAutofillId(), node.getAutofillId(), event);
        }

        assertWithMessage("wrong class on %s", event).that(node.getClassName())
                .isEqualTo(expectedView.getClass().getName());

        if (expectedParentId != null) {
            assertWithMessage("wrong parent autofill id on %s", event)
                    .that(node.getParentAutofillId()).isEqualTo(expectedParentId);
        }

        if (expectedText != null) {
            assertWithMessage("wrong text on %s", event).that(node.getText().toString())
                    .isEqualTo(expectedText);
        }
        return null;
    }

    @Nullable
    private String assertSessionLevelEvent(@NonNull ContentCaptureEvent event) {
        assertWithMessage("event %s should not have a ViewNode", event)
                .that(event.getViewNode()).isNull();
        assertWithMessage("event %s should not have text", event)
                .that(event.getText()).isNull();
        assertWithMessage("event %s should not have an autofillId", event)
                .that(event.getId()).isNull();
        assertWithMessage("event %s should not have autofillIds", event)
                .that(event.getIds()).isNull();
        return null;
    }

    private void assertNextEvent(@NonNull EventAssertion assertion, int expectedType,
            @NonNull String errorFormat, @Nullable Object... errorArgs) {
        if (mNextEvent >= mEvents.size()) {
            throw new AssertionError("Reached the end of the events: "
                    + String.format(errorFormat, errorArgs) + "\n. Events("
                    + mEvents.size() + "): " + mEvents);
        }
        do {
            final int index = mNextEvent++;
            final ContentCaptureEvent event = mEvents.get(index);
            if (event.getType() != expectedType) {
                Log.w(TAG, "assertNextEvent(): ignoring event #" + index + "(" + event + ")");
                continue;
            }
            // If returns an error message from getErrorMessage(), means the error can be ignored.
            // The test will assert nex event.
            // If directly throws AssertionError or exception, means the error cannot be ignored.
            // The test will stop immediately.
            final String error = assertion.getErrorMessage(event);
            if (error == null) {
                Log.d(TAG, "assertNextEvent(): match event in #" + index + ".");
                return;
            }
            Log.w(TAG, "assertNextEvent(): ignoring event #" + index + "(" + event + "): "
                    + error);
        } while (mNextEvent < mEvents.size());
        throw new AssertionError(String.format(errorFormat, errorArgs) + "\n. Events("
                + mEvents.size() + "): " + mEvents);
    }

    /**
     * Asserts no event for the view.
     */
    public EventsAssertor assertNoEvent(View expectedView) {
        for (ContentCaptureEvent event : mEvents) {
            final ViewNode node = event.getViewNode();
            if (node == null) {
                continue;
            }

            if (expectedView != null && node.getAutofillId().equals(expectedView.getAutofillId())) {
                throw new AssertionError("wrong event (expected no event for "
                        + expectedView.getAutofillId() + " ) : " + event);
            }
        }
        return this;
    }

    public ContentCaptureEvent getLastEvent() {
        return mEvents.get(mNextEvent - 1);
    }

    private interface EventAssertion {
        @Nullable
        String getErrorMessage(@NonNull ContentCaptureEvent event);
    }
}
