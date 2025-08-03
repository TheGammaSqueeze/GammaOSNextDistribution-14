/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.view.accessibility.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.LocaleSpan;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** Class for testing {@link AccessibilityEvent}. */
// TODO(b/263942937) Re-enable @Presubmit
@RunWith(AndroidJUnit4.class)
public class AccessibilityEventTest {
    private static final long IDLE_TIMEOUT_MS = 500;
    private static final long DEFAULT_TIMEOUT_MS = 2000;

    // From ViewConfiguration.SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS
    private static final long SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS = 100;

    private EventReportingLinearLayout mParentView;
    private View mChildView;
    private TextView mTextView;
    private String mPackageName;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private final ActivityTestRule<DummyActivity> mActivityRule =
            new ActivityTestRule<>(DummyActivity.class, false, false);
    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();
    private InstrumentedAccessibilityServiceTestRule<SpeakingAccessibilityService>
            mInstrumentedAccessibilityServiceRule =
                    new InstrumentedAccessibilityServiceTestRule<>(
                            SpeakingAccessibilityService.class, false);

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mActivityRule)
                    .around(mInstrumentedAccessibilityServiceRule)
                    .around(mDumpOnFailureRule);

    @Before
    public void setUp() throws Throwable {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        final Activity activity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        mPackageName = activity.getApplicationContext().getPackageName();
        mInstrumentedAccessibilityServiceRule.enableService();
        sUiAutomation.executeAndWaitForEvent(() -> {
                    try {
                        mActivityRule.runOnUiThread(() -> {
                            final LinearLayout grandparent = new LinearLayout(activity);
                            activity.setContentView(grandparent);
                            mParentView = new EventReportingLinearLayout(activity);
                            mChildView = new View(activity);
                            mTextView = new TextView(activity);
                            grandparent.addView(mParentView);
                            mParentView.addView(mChildView);
                            mParentView.addView(mTextView);
                        });
                    } catch (Throwable e) {
                        fail(e.toString());
                    }
                },
                // There can be a race where the test Activity gets focus and we start test.
                // Because we don't specify flagRetrieveInteractiveWindows in this test, until
                // the Activity gets focus, no events will be delivered from it.
                // So. this waits for any event from the test activity.
                accessibilityEvent -> mPackageName.equals(accessibilityEvent.getPackageName()),
                DEFAULT_TIMEOUT_MS);
        sUiAutomation.waitForIdle(IDLE_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
    }

    private static class EventReportingLinearLayout extends LinearLayout {
        public List<AccessibilityEvent> mReceivedEvents = new ArrayList<AccessibilityEvent>();

        public EventReportingLinearLayout(Context context) {
            super(context);
        }

        @Override
        public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
            mReceivedEvents.add(AccessibilityEvent.obtain(event));
            return super.requestSendAccessibilityEvent(child, event);
        }
    }

    @Test
    public void testScrollEvent() throws Exception {
        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> mChildView.scrollTo(0, 100)), new ScrollEventFilter(1), DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testScrollEventBurstCombined() throws Exception {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 100);
                            mChildView.scrollTo(0, 125);
                            mChildView.scrollTo(0, 150);
                            mChildView.scrollTo(0, 175);
                        }),
                new ScrollEventFilter(1),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testScrollEventsDeliveredInCorrectInterval() throws Exception {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 25);
                        mChildView.scrollTo(0, 50);
                        mChildView.scrollTo(0, 100);
                    });
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS * 2);
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 150);
                        mChildView.scrollTo(0, 175);
                    });
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS / 2);
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 200);
                    });
                },
                new ScrollEventFilter(2),
                DEFAULT_TIMEOUT_MS);
    }

    class ScrollEventFilter extends AccessibilityEventFilter {
        private int mCount = 0;
        private int mTargetCount;

        ScrollEventFilter(int count) {
            mTargetCount = count;
        }

        public boolean accept(AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                mCount += 1;
                mEvents.add(event);
                return mCount >= mTargetCount;
            }
            return false;
        }
    }

    @Test
    public void testScrollEventsClearedOnDetach() throws Throwable {
        ScrollEventFilter scrollEventFilter = new ScrollEventFilter(1);
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 25);
                            mChildView.scrollTo(5, 50);
                            mChildView.scrollTo(7, 100);
                        }),
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        mActivityRule.runOnUiThread(
                () -> {
                    mParentView.removeView(mChildView);
                    mParentView.addView(mChildView);
                });
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 150);
                        }),
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = scrollEventFilter.getLastEvent();
        assertEquals(-7, event.getScrollDeltaX());
        assertEquals(50, event.getScrollDeltaY());
    }

    @Test
    public void testScrollEventsCaptureTotalDelta() throws Throwable {
        ScrollEventFilter scrollEventFilter = new ScrollEventFilter(1);
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 25);
                            mChildView.scrollTo(5, 50);
                            mChildView.scrollTo(7, 100);
                        }),
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = scrollEventFilter.getLastEvent();
        assertEquals(7, event.getScrollDeltaX());
        assertEquals(100, event.getScrollDeltaY());
    }

    @Test
    public void testScrollEventsClearDeltaAfterSending() throws Throwable {
        ScrollEventFilter scrollEventFilter = new ScrollEventFilter(2);
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 25);
                        mChildView.scrollTo(5, 50);
                        mChildView.scrollTo(7, 100);
                    });
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS * 2);
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 25);
                        mChildView.scrollTo(5, 50);
                        mChildView.scrollTo(7, 100);
                        mChildView.scrollTo(0, 150);
                    });
                },
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = scrollEventFilter.getLastEvent();
        assertEquals(-7, event.getScrollDeltaX());
        assertEquals(50, event.getScrollDeltaY());
    }

    @Test
    public void testEventViewTargetedByScroll() throws Throwable {
        final AccessibilityEvent awaitedEvent = sUiAutomation.executeAndWaitForEvent(
                () -> {
                    AccessibilityEvent event = new AccessibilityEvent(
                            AccessibilityEvent.TYPE_VIEW_TARGETED_BY_SCROLL);
                    event.setAction(ACTION_SCROLL_IN_DIRECTION.getId());
                    mChildView.sendAccessibilityEventUnchecked(event);
                },
                event -> event.getEventType() == AccessibilityEvent.TYPE_VIEW_TARGETED_BY_SCROLL,
                DEFAULT_TIMEOUT_MS);
        assertThat(awaitedEvent.getAction()).isEqualTo(ACTION_SCROLL_IN_DIRECTION.getId());
        assertThat(awaitedEvent.getSource()).isEqualTo(mChildView.createAccessibilityNodeInfo());
    }

    @Test
    public void testStateEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView);
                },
                new StateDescriptionEventFilter(1),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testStateEventBurstCombined() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                },
                new StateDescriptionEventFilter(1),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testStateEventsDeliveredInCorrectInterval() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS * 2);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS / 2);
                    sendStateDescriptionChangedEvent(mChildView);
                },
                new StateDescriptionEventFilter(2),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testStateEventsHaveLastEventText() throws Throwable {
        StateDescriptionEventFilter stateDescriptionEventFilter =
                new StateDescriptionEventFilter(1);
        String expectedState = "Second state";
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView, "First state");
                    sendStateDescriptionChangedEvent(mChildView, expectedState);
                },
                stateDescriptionEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = stateDescriptionEventFilter.getLastEvent();
        assertEquals(expectedState, event.getText().get(0));
    }

    class StateDescriptionEventFilter extends AccessibilityEventFilter {
        private int mCount;
        private int mTargetCount;

        StateDescriptionEventFilter(int count) {
            mTargetCount = count;
        }

        public boolean accept(AccessibilityEvent event) {
            if (event.getContentChangeTypes()
                    == AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) {
                mCount += 1;
                mEvents.add(event);
                return mCount >= mTargetCount;
            }
            return false;
        }
    }
    ;

    private abstract class AccessibilityEventFilter
            implements UiAutomation.AccessibilityEventFilter {
        protected List<AccessibilityEvent> mEvents = new ArrayList<>();

        public abstract boolean accept(AccessibilityEvent event);

        void assertReceivedEventCount(int count) {
            assertEquals(count, mEvents.size());
        }

        AccessibilityEvent getLastEvent() {
            if (mEvents.size() > 0) {
                return mEvents.get(mEvents.size() - 1);
            }
            return null;
        }
    }

    private void sendStateDescriptionChangedEvent(View view) {
        sendStateDescriptionChangedEvent(view, null);
    }

    private void sendStateDescriptionChangedEvent(View view, CharSequence text) {
        AccessibilityEvent event =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION);
        event.getText().add(text);
        view.sendAccessibilityEventUnchecked(event);
    }

    @Test
    public void setTextError_receiveEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setError("error")),
                event -> isExpectedChangeType(event,
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_ERROR
                                | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_INVALID)
                        && event.getSource().getError() != null,
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void setViewEnable_receiveEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> {
                    mChildView.setEnabled(!mChildView.isEnabled());
                }),
                event -> isExpectedChangeType(event,
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_ENABLED),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void setText_unChanged_doNotReceiveEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setText("a")),
                event -> isExpectedChangeType(event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);

        assertThrows(
                TimeoutException.class,
                () ->
                        sUiAutomation.executeAndWaitForEvent(
                                () -> {
                                    sInstrumentation.runOnMainSync(
                                            () -> {
                                                mTextView.setText("a");
                                            });
                                },
                                event -> isExpectedSource(event, mTextView),
                                DEFAULT_TIMEOUT_MS));
    }

    @Test
    public void setText_textChanged_receivesTextEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setText("a")),
                event -> isExpectedChangeType(event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);

        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(
                            () -> {
                                mTextView.setText("b");
                            });
                },
                event ->
                        isExpectedSource(event, mTextView)
                                && isExpectedChangeType(
                                        event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void setText_parcelableSpanChanged_receivesUndefinedEvent() throws Throwable {
        String text = "a";
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setText(text)),
                event -> isExpectedChangeType(event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);

        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(
                            () -> {
                                SpannableString spannableString = new SpannableString(text);
                                spannableString.setSpan(new LocaleSpan(Locale.ENGLISH), 0, 1, 0);
                                mTextView.setText(spannableString);
                            });
                },
                event ->
                        isExpectedSource(event, mTextView)
                                && isExpectedChangeType(
                                        event, AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED),
                DEFAULT_TIMEOUT_MS);
    }

    private static boolean isExpectedSource(AccessibilityEvent event, View view) {
        return TextUtils.equals(view.getContext().getPackageName(), event.getPackageName())
                && TextUtils.equals(view.getAccessibilityClassName(), event.getClassName());
    }

    private static boolean isExpectedChangeType(AccessibilityEvent event, int changeType) {
        return (event.getContentChangeTypes() & changeType) == changeType;
    }

    /**
     * Tests whether accessibility events are correctly written and read from a parcel (version 1).
     */
    @SmallTest
    @Test
    public void testMarshaling() throws Exception {
        // fully populate the event to marshal
        AccessibilityEvent sentEvent = AccessibilityEvent.obtain();
        fullyPopulateAccessibilityEvent(sentEvent);

        // marshal and unmarshal the event
        Parcel parcel = Parcel.obtain();
        sentEvent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityEvent receivedEvent = AccessibilityEvent.CREATOR.createFromParcel(parcel);

        // make sure all fields properly marshaled
        assertEqualsAccessibilityEvent(sentEvent, receivedEvent);

        parcel.recycle();
    }

    /** Tests if {@link AccessibilityEvent} can be acquired through obtain(). */
    @SmallTest
    @Test
    public void testRecycle() {
        // evaluate that recycle() can be called on an event acquired by obtain()
        AccessibilityEvent.obtain().recycle();
    }

    /** Tests whether the event types are correctly converted to strings. */
    @SmallTest
    @Test
    public void testEventTypeToString() {
        assertEquals(
                "TYPE_NOTIFICATION_STATE_CHANGED",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED));
        assertEquals(
                "TYPE_TOUCH_EXPLORATION_GESTURE_END",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END));
        assertEquals(
                "TYPE_TOUCH_EXPLORATION_GESTURE_START",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START));
        assertEquals(
                "TYPE_VIEW_CLICKED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_CLICKED));
        assertEquals(
                "TYPE_VIEW_FOCUSED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_FOCUSED));
        assertEquals(
                "TYPE_VIEW_HOVER_ENTER",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER));
        assertEquals(
                "TYPE_VIEW_HOVER_EXIT",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_HOVER_EXIT));
        assertEquals(
                "TYPE_VIEW_LONG_CLICKED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED));
        assertEquals(
                "TYPE_VIEW_CONTEXT_CLICKED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED));
        assertEquals(
                "TYPE_VIEW_SCROLLED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_SCROLLED));
        assertEquals(
                "TYPE_VIEW_SELECTED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_SELECTED));
        assertEquals(
                "TYPE_VIEW_TEXT_CHANGED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED));
        assertEquals(
                "TYPE_VIEW_TEXT_SELECTION_CHANGED",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED));
        assertEquals(
                "TYPE_WINDOW_CONTENT_CHANGED",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED));
        assertEquals(
                "TYPE_WINDOW_STATE_CHANGED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
    }

    /** Tests whether the event describes its contents consistently. */
    @SmallTest
    @Test
    public void testDescribeContents() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        assertSame(
                "Accessibility events always return 0 for this method.",
                0,
                event.describeContents());
        fullyPopulateAccessibilityEvent(event);
        assertSame(
                "Accessibility events always return 0 for this method.",
                0,
                event.describeContents());
    }

    /**
     * Tests whether accessibility events are correctly written and read from a parcel (version 2).
     */
    @SmallTest
    @Test
    public void testMarshaling2() {
        // fully populate the event to marshal
        AccessibilityEvent marshaledEvent = AccessibilityEvent.obtain();
        fullyPopulateAccessibilityEvent(marshaledEvent);

        // marshal and unmarshal the event
        Parcel parcel = Parcel.obtain();
        marshaledEvent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityEvent unmarshaledEvent = AccessibilityEvent.obtain();
        unmarshaledEvent.initFromParcel(parcel);

        // make sure all fields properly marshaled
        assertEqualsAccessibilityEvent(marshaledEvent, unmarshaledEvent);

        parcel.recycle();
    }

    /**
     * While CharSequence is immutable, some classes implementing it are mutable. Make sure they
     * can't change the object by changing the objects backing CharSequence
     */
    @SmallTest
    @Test
    public void testChangeTextAfterSetting_shouldNotAffectEvent() {
        final String originalText = "Cassowary";
        final String newText = "Hornbill";
        AccessibilityEvent event = AccessibilityEvent.obtain();
        StringBuffer updatingString = new StringBuffer(originalText);
        event.setBeforeText(updatingString);
        event.setContentDescription(updatingString);

        updatingString.delete(0, updatingString.length());
        updatingString.append(newText);

        assertTrue(TextUtils.equals(originalText, event.getBeforeText()));
        assertTrue(TextUtils.equals(originalText, event.getContentDescription()));
    }

    @SmallTest
    @Test
    public void testConstructors() {
        final AccessibilityEvent populatedEvent = new AccessibilityEvent();
        fullyPopulateAccessibilityEvent(populatedEvent);
        final AccessibilityEvent event = new AccessibilityEvent(populatedEvent);

        assertEqualsAccessibilityEvent(event, populatedEvent);

        final AccessibilityEvent firstEvent = new AccessibilityEvent();
        firstEvent.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        final AccessibilityEvent secondEvent =
                new AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);

        assertEqualsAccessibilityEvent(firstEvent, secondEvent);
    }

    /**
     * Fully populates the {@link AccessibilityEvent} to marshal.
     *
     * @param sentEvent The event to populate.
     */
    private void fullyPopulateAccessibilityEvent(AccessibilityEvent sentEvent) {
        sentEvent.setAddedCount(1);
        sentEvent.setBeforeText("BeforeText");
        sentEvent.setChecked(true);
        sentEvent.setClassName("foo.bar.baz.Class");
        sentEvent.setContentDescription("ContentDescription");
        sentEvent.setCurrentItemIndex(1);
        sentEvent.setEnabled(true);
        sentEvent.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        sentEvent.setEventTime(1000);
        sentEvent.setFromIndex(1);
        sentEvent.setFullScreen(true);
        sentEvent.setItemCount(1);
        sentEvent.setPackageName("foo.bar.baz");
        sentEvent.setParcelableData(Message.obtain(null, 1, 2, 3));
        sentEvent.setPassword(true);
        sentEvent.setRemovedCount(1);
        sentEvent.getText().add("Foo");
        sentEvent.setMaxScrollX(1);
        sentEvent.setMaxScrollY(1);
        sentEvent.setScrollX(1);
        sentEvent.setScrollY(1);
        sentEvent.setScrollDeltaX(3);
        sentEvent.setScrollDeltaY(3);
        sentEvent.setToIndex(1);
        sentEvent.setScrollable(true);
        sentEvent.setAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        sentEvent.setMovementGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
        sentEvent.setDisplayId(Display.DEFAULT_DISPLAY);
        sentEvent.setSpeechStateChangeTypes(AccessibilityEvent.SPEECH_STATE_SPEAKING_START);

        AccessibilityRecord record = AccessibilityRecord.obtain();
        AccessibilityRecordTest.fullyPopulateAccessibilityRecord(record);
        sentEvent.appendRecord(record);
    }

    /**
     * Compares all properties of the <code>expectedEvent</code> and the <code>receivedEvent</code>
     * to verify that the received event is the one that is expected.
     */
    private static void assertEqualsAccessibilityEvent(
            AccessibilityEvent expectedEvent, AccessibilityEvent receivedEvent) {
        assertEquals(
                "addedCount has incorrect value",
                expectedEvent.getAddedCount(),
                receivedEvent.getAddedCount());
        assertEquals(
                "beforeText has incorrect value",
                expectedEvent.getBeforeText(),
                receivedEvent.getBeforeText());
        assertEquals(
                "checked has incorrect value",
                expectedEvent.isChecked(),
                receivedEvent.isChecked());
        assertEquals(
                "className has incorrect value",
                expectedEvent.getClassName(),
                receivedEvent.getClassName());
        assertEquals(
                "contentDescription has incorrect value",
                expectedEvent.getContentDescription(),
                receivedEvent.getContentDescription());
        assertEquals(
                "currentItemIndex has incorrect value",
                expectedEvent.getCurrentItemIndex(),
                receivedEvent.getCurrentItemIndex());
        assertEquals(
                "enabled has incorrect value",
                expectedEvent.isEnabled(),
                receivedEvent.isEnabled());
        assertEquals(
                "eventType has incorrect value",
                expectedEvent.getEventType(),
                receivedEvent.getEventType());
        assertEquals(
                "fromIndex has incorrect value",
                expectedEvent.getFromIndex(),
                receivedEvent.getFromIndex());
        assertEquals(
                "fullScreen has incorrect value",
                expectedEvent.isFullScreen(),
                receivedEvent.isFullScreen());
        assertEquals(
                "itemCount has incorrect value",
                expectedEvent.getItemCount(),
                receivedEvent.getItemCount());
        assertEquals(
                "password has incorrect value",
                expectedEvent.isPassword(),
                receivedEvent.isPassword());
        assertEquals(
                "removedCount has incorrect value",
                expectedEvent.getRemovedCount(),
                receivedEvent.getRemovedCount());
        assertSame(
                "maxScrollX has incorrect value",
                expectedEvent.getMaxScrollX(),
                receivedEvent.getMaxScrollX());
        assertSame(
                "maxScrollY has incorrect value",
                expectedEvent.getMaxScrollY(),
                receivedEvent.getMaxScrollY());
        assertSame(
                "scrollX has incorrect value",
                expectedEvent.getScrollX(),
                receivedEvent.getScrollX());
        assertSame(
                "scrollY has incorrect value",
                expectedEvent.getScrollY(),
                receivedEvent.getScrollY());
        assertSame(
                "scrollDeltaX has incorrect value",
                expectedEvent.getScrollDeltaX(),
                receivedEvent.getScrollDeltaX());
        assertSame(
                "scrollDeltaY has incorrect value",
                expectedEvent.getScrollDeltaY(),
                receivedEvent.getScrollDeltaY());
        assertSame(
                "toIndex has incorrect value",
                expectedEvent.getToIndex(),
                receivedEvent.getToIndex());
        assertSame(
                "scrollable has incorrect value",
                expectedEvent.isScrollable(),
                receivedEvent.isScrollable());
        assertSame(
                "granularity has incorrect value",
                expectedEvent.getMovementGranularity(),
                receivedEvent.getMovementGranularity());
        assertSame(
                "action has incorrect value", expectedEvent.getAction(), receivedEvent.getAction());
        assertSame(
                "windowChangeTypes has incorrect value",
                expectedEvent.getWindowChanges(),
                receivedEvent.getWindowChanges());
        assertEquals(
                "speechStateChangeTypes has incorrect value,",
                expectedEvent.getSpeechStateChangeTypes(),
                receivedEvent.getSpeechStateChangeTypes());

        AccessibilityRecordTest.assertEqualsText(expectedEvent.getText(), receivedEvent.getText());
        AccessibilityRecordTest.assertEqualAccessibilityRecord(expectedEvent, receivedEvent);

        assertEqualAppendedRecord(expectedEvent, receivedEvent);
    }

    private static void assertEqualAppendedRecord(
            AccessibilityEvent expectedEvent, AccessibilityEvent receivedEvent) {
        assertEquals(
                "recordCount has incorrect value",
                expectedEvent.getRecordCount(),
                receivedEvent.getRecordCount());
        if (expectedEvent.getRecordCount() != 0 && receivedEvent.getRecordCount() != 0) {
            AccessibilityRecord expectedRecord = expectedEvent.getRecord(0);
            AccessibilityRecord receivedRecord = receivedEvent.getRecord(0);
            AccessibilityRecordTest.assertEqualAccessibilityRecord(expectedRecord, receivedRecord);
        }
    }
}
