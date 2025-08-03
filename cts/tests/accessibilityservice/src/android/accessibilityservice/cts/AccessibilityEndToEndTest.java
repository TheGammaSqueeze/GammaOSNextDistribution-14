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

package android.accessibilityservice.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.accessibility.cts.common.InstrumentedAccessibilityService.TIMEOUT_SERVICE_ENABLE;
import static android.accessibility.cts.common.InstrumentedAccessibilityService.enableService;
import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventType;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventTypeWithAction;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventTypeWithResource;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.getActivityTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.AsyncUtils.await;
import static android.accessibilityservice.cts.utils.GestureUtils.click;
import static android.accessibilityservice.cts.utils.GestureUtils.dispatchGesture;
import static android.accessibilityservice.cts.utils.RunOnMainUtils.getOnMain;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_HIDE_TOOLTIP;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_TOOLTIP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.MagnificationConfig;
import android.accessibilityservice.cts.activities.AccessibilityEndToEndActivity;
import android.accessibilityservice.cts.utils.EventCapturingMotionEventListener;
import android.accessibilityservice.cts.utils.ProviderCustomView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiAutomation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.CtsMouseUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.TestUtils;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class performs end-to-end testing of the accessibility feature by
 * creating an {@link Activity} and poking around so {@link AccessibilityEvent}s
 * are generated and their correct dispatch verified.
 */
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityEndToEndTest extends StsExtraBusinessLogicTestCase {

    private static final String LOG_TAG = "AccessibilityEndToEndTest";

    private static final String GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND =
            "appwidget grantbind --package android.accessibilityservice.cts --user ";

    private static final String REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND =
            "appwidget revokebind --package android.accessibilityservice.cts --user ";

    private static final String APP_WIDGET_PROVIDER_PACKAGE = "foo.bar.baz";

    private static final int TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS = 1000;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private AccessibilityEndToEndActivity mActivity;

    private ActivityTestRule<AccessibilityEndToEndActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEndToEndActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(sUiAutomation);

    private final InstrumentedAccessibilityServiceTestRule<
            StubMotionInterceptingAccessibilityService>
            mMotionInterceptingServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubMotionInterceptingAccessibilityService.class, false);

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mMotionInterceptingServiceRule)
            .around(mDumpOnFailureRule)
            .around(mCheckFlagsRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        sUiAutomation.adoptShellPermissionIdentity(POST_NOTIFICATIONS);
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
    }

    @After
    public void tearDown() throws Exception {
        sUiAutomation.dropShellPermissionIdentity();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#setSelected",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeViewSelectedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_SELECTED);
        expected.setClassName(ListView.class.getName());
        expected.setPackageName(mActivity.getPackageName());
        expected.setDisplayId(mActivity.getDisplayId());
        expected.getText().add(mActivity.getString(R.string.second_list_item));
        expected.setItemCount(2);
        expected.setCurrentItemIndex(1);
        expected.setEnabled(true);
        expected.setScrollable(false);
        expected.setFromIndex(0);
        expected.setToIndex(1);

        final ListView listView = (ListView) mActivity.findViewById(R.id.listview);

        AccessibilityEvent awaitedEvent =
            sUiAutomation.executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listView.setSelection(1);
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#performClick",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeViewClickedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);
        expected.setClassName(Button.class.getName());
        expected.setPackageName(mActivity.getPackageName());
        expected.setDisplayId(mActivity.getDisplayId());
        expected.getText().add(mActivity.getString(R.string.button_title));
        expected.setEnabled(true);

        final Button button = (Button) mActivity.findViewById(R.id.button);

        AccessibilityEvent awaitedEvent =
            sUiAutomation.executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        button.performClick();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#performLongClick",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeViewLongClickedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        expected.setClassName(Button.class.getName());
        expected.setPackageName(mActivity.getPackageName());
        expected.setDisplayId(mActivity.getDisplayId());
        expected.getText().add(mActivity.getString(R.string.button_title));
        expected.setEnabled(true);

        final Button button = (Button) mActivity.findViewById(R.id.button);

        AccessibilityEvent awaitedEvent =
            sUiAutomation.executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        button.performLongClick();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#requestFocus",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeViewFocusedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        expected.setClassName(Button.class.getName());
        expected.setPackageName(mActivity.getPackageName());
        expected.setDisplayId(mActivity.getDisplayId());
        expected.getText().add(mActivity.getString(R.string.button_title));
        expected.setItemCount(6);
        expected.setCurrentItemIndex(4);
        expected.setEnabled(true);

        final Button button = (Button) mActivity.findViewById(R.id.buttonWithTooltip);

        AccessibilityEvent awaitedEvent =
            sUiAutomation.executeAndWaitForEvent(
                    () -> mActivity.runOnUiThread(button::requestFocus),
                    (event) -> equalsAccessiblityEvent(event, expected),
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.text.Editable#replace",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeViewTextChangedAccessibilityEvent() throws Throwable {
        // focus the edit text
        final EditText editText = (EditText) mActivity.findViewById(R.id.edittext);

        AccessibilityEvent awaitedFocusEvent =
            sUiAutomation.executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editText.requestFocus();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED;
                }
            },
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected focuss event.", awaitedFocusEvent);

        final String beforeText = mActivity.getString(R.string.text_input_blah);
        final String newText = mActivity.getString(R.string.text_input_blah_blah);
        final String afterText = beforeText.substring(0, 3) + newText;

        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        expected.setClassName(EditText.class.getName());
        expected.setPackageName(mActivity.getPackageName());
        expected.setDisplayId(mActivity.getDisplayId());
        expected.getText().add(afterText);
        expected.setBeforeText(beforeText);
        expected.setFromIndex(3);
        expected.setAddedCount(9);
        expected.setRemovedCount(1);
        expected.setEnabled(true);

        AccessibilityEvent awaitedTextChangeEvent =
            sUiAutomation.executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editText.getEditableText().replace(3, 4, newText);
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedTextChangeEvent);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.ViewManager#addView",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeWindowStateChangedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        expected.setClassName(AlertDialog.class.getName());
        expected.setPackageName(mActivity.getPackageName());
        expected.setDisplayId(mActivity.getDisplayId());
        expected.getText().add(mActivity.getString(R.string.alert_title));
        expected.getText().add(mActivity.getString(R.string.alert_message));
        expected.setEnabled(true);

        AccessibilityEvent awaitedEvent =
            sUiAutomation.executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        (new AlertDialog.Builder(mActivity).setTitle(R.string.alert_title)
                                .setMessage(R.string.alert_message)).create().show();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.app.Activity#finish",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeWindowsChangedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_WINDOWS_CHANGED);
        expected.setDisplayId(mActivity.getDisplayId());

        // check the received event
        AccessibilityEvent awaitedEvent =
            sUiAutomation.executeAndWaitForEvent(
                    () -> mActivity.runOnUiThread(() -> mActivity.finish()),
                    event -> event.getWindowChanges() == AccessibilityEvent.WINDOWS_CHANGE_REMOVED
                            && equalsAccessiblityEvent(event, expected),
                    DEFAULT_TIMEOUT_MS);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @AppModeFull
    @SuppressWarnings("deprecation")
    @Test
    @ApiTest(apis = {"android.app.NotificationManager#notify",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTypeNotificationStateChangedAccessibilityEvent() throws Throwable {
        // No notification UI on televisions.
        if ((mActivity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            Log.i(LOG_TAG, "Skipping: testTypeNotificationStateChangedAccessibilityEvent" +
                    " - No notification UI on televisions.");
            return;
        }
        PackageManager pm = sInstrumentation.getTargetContext().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            Log.i(LOG_TAG, "Skipping: testTypeNotificationStateChangedAccessibilityEvent" +
                    " - Watches have different notification system.");
            return;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.i(LOG_TAG, "Skipping: testTypeNotificationStateChangedAccessibilityEvent" +
                    " - Automotive handle notifications differently.");
            return;
        }

        String message = mActivity.getString(R.string.notification_message);

        final NotificationManager notificationManager =
                (NotificationManager) mActivity.getSystemService(Service.NOTIFICATION_SERVICE);
        final NotificationChannel channel =
                new NotificationChannel("id", "name", NotificationManager.IMPORTANCE_DEFAULT);
        try {
            // create the notification to send
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setBypassDnd(true);
            notificationManager.createNotificationChannel(channel);
            final int notificationId = 1;
            final Notification notification =
                    new Notification.Builder(mActivity, channel.getId())
                            .setSmallIcon(android.R.drawable.stat_notify_call_mute)
                            .setContentIntent(PendingIntent.getActivity(mActivity, 0,
                                    new Intent(),
            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                            .setTicker(message)
                            .setContentTitle("")
                            .setContentText("")
                            .setPriority(Notification.PRIORITY_MAX)
                            // Mark the notification as "interruptive" by specifying a vibration
                            // pattern. This ensures it's announced properly on watch-type devices.
                            .setVibrate(new long[]{})
                            .build();

            // create and populate the expected event
            final AccessibilityEvent expected = AccessibilityEvent.obtain();
            expected.setEventType(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
            expected.setClassName(Notification.class.getName());
            expected.setPackageName(mActivity.getPackageName());
            expected.getText().add(message);
            expected.setParcelableData(notification);

            AccessibilityEvent awaitedEvent =
                    sUiAutomation.executeAndWaitForEvent(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // trigger the event
                                    mActivity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // trigger the event
                                            notificationManager
                                                    .notify(notificationId, notification);
                                            mActivity.finish();
                                        }
                                    });
                                }
                            },
                            new UiAutomation.AccessibilityEventFilter() {
                                // check the received event
                                @Override
                                public boolean accept(AccessibilityEvent event) {
                                    return equalsAccessiblityEvent(event, expected);
                                }
                            },
                            DEFAULT_TIMEOUT_MS);
            assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
        } finally {
            notificationManager.deleteNotificationChannel(channel.getId());
        }
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#interrupt"})
    public void testInterrupt_notifiesService() {
        sInstrumentation
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        InstrumentedAccessibilityService service =
                enableService(InstrumentedAccessibilityService.class);

        try {
            assertFalse(service.wasOnInterruptCalled());

            mActivity.runOnUiThread(() -> {
                AccessibilityManager accessibilityManager = (AccessibilityManager) mActivity
                        .getSystemService(Service.ACCESSIBILITY_SERVICE);
                accessibilityManager.interrupt();
            });

            Object waitObject = service.getInterruptWaitObject();
            synchronized (waitObject) {
                if (!service.wasOnInterruptCalled()) {
                    try {
                        waitObject.wait(DEFAULT_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                }
            }
            assertTrue(service.wasOnInterruptCalled());
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getPackageName"})
    public void testPackageNameCannotBeFaked() {
        mActivity.runOnUiThread(() -> {
            // Set the activity to report fake package for events and nodes
            mActivity.setReportedPackageName("foo.bar.baz");

            // Make sure node package cannot be faked
            AccessibilityNodeInfo root = sUiAutomation
                    .getRootInActiveWindow();
            assertPackageName(root, mActivity.getPackageName());
        });

        // Make sure event package cannot be faked
        try {
            sUiAutomation.executeAndWaitForEvent(() ->
                sInstrumentation.runOnMainSync(() ->
                    mActivity.findViewById(R.id.button).requestFocus())
                , (AccessibilityEvent event) ->
                    event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
                            && event.getPackageName().equals(mActivity.getPackageName())
                , DEFAULT_TIMEOUT_MS);
        } catch (TimeoutException e) {
            fail("Events from fake package should be fixed to use the correct package");
        }
    }

    @AppModeFull
    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getPackageName"})
    public void testPackageNameCannotBeFakedAppWidget() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        sInstrumentation.runOnMainSync(() -> {
            // Set the activity to report fake package for events and nodes
            mActivity.setReportedPackageName(APP_WIDGET_PROVIDER_PACKAGE);

            // Make sure we cannot report nodes as if from the widget package
            AccessibilityNodeInfo root = sUiAutomation
                    .getRootInActiveWindow();
            assertPackageName(root, mActivity.getPackageName());
        });

        // Make sure we cannot send events as if from the widget package
        try {
            sUiAutomation.executeAndWaitForEvent(() ->
                sInstrumentation.runOnMainSync(() ->
                    mActivity.findViewById(R.id.button).requestFocus())
                , (AccessibilityEvent event) ->
                    event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
                            && event.getPackageName().equals(mActivity.getPackageName())
                , DEFAULT_TIMEOUT_MS);
        } catch (TimeoutException e) {
            fail("Should not be able to send events from a widget package if no widget hosted");
        }

        // Create a host and start listening.
        final AppWidgetHost host = new AppWidgetHost(sInstrumentation.getTargetContext(), 0);
        host.deleteHost();
        host.startListening();

        // Well, app do not have this permission unless explicitly granted
        // by the user. Now we will pretend for the user and grant it.
        grantBindAppWidgetPermission();

        // Allocate an app widget id to bind.
        final int appWidgetId = host.allocateAppWidgetId();
        try {
            // Grab a provider we defined to be bound.
            final AppWidgetProviderInfo provider = getAppWidgetProviderInfo();

            // Bind the widget.
            final boolean widgetBound = getAppWidgetManager().bindAppWidgetIdIfAllowed(
                    appWidgetId, provider.getProfile(), provider.provider, null);
            assertTrue(widgetBound);

            // Make sure the app can use the package of a widget it hosts
            sInstrumentation.runOnMainSync(() -> {
                // Make sure we can report nodes as if from the widget package
                AccessibilityNodeInfo root = sUiAutomation
                        .getRootInActiveWindow();
                assertPackageName(root, APP_WIDGET_PROVIDER_PACKAGE);
            });

            // Make sure we can send events as if from the widget package
            try {
                sUiAutomation.executeAndWaitForEvent(() ->
                    sInstrumentation.runOnMainSync(() ->
                        mActivity.findViewById(R.id.button).performClick())
                    , (AccessibilityEvent event) ->
                            event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED
                                    && event.getPackageName().equals(APP_WIDGET_PROVIDER_PACKAGE)
                    , DEFAULT_TIMEOUT_MS);
            } catch (TimeoutException e) {
                fail("Should be able to send events from a widget package if widget hosted");
            }
        } finally {
            // Clean up.
            host.deleteAppWidgetId(appWidgetId);
            host.deleteHost();
            revokeBindAppWidgetPermission();
        }
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#isHeading"})
    public void testViewHeadingReportedToAccessibility() throws Exception {
        final EditText editText = (EditText) getOnMain(sInstrumentation,
                () -> mActivity.findViewById(R.id.edittext));
        // Make sure the edittext was populated properly from xml
        final boolean editTextIsHeading = getOnMain(sInstrumentation,
                editText::isAccessibilityHeading);
        assertTrue("isAccessibilityHeading not populated properly from xml", editTextIsHeading);

        final AccessibilityNodeInfo editTextNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/edittext")
                .get(0);
        assertTrue("isAccessibilityHeading not reported to accessibility",
                editTextNode.isHeading());

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(() ->
                        editText.setAccessibilityHeading(false)),
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);
        editTextNode.refresh();
        assertFalse("isAccessibilityHeading not reported to accessibility after update",
                editTextNode.isHeading());
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getTooltipText"})
    public void testTooltipTextReportedToAccessibility() {
        final AccessibilityNodeInfo buttonNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/buttonWithTooltip")
                .get(0);
        assertEquals("Tooltip text not reported to accessibility",
                sInstrumentation.getContext().getString(R.string.button_tooltip),
                buttonNode.getTooltipText());
    }

    @MediumTest
    @Test
    public void testAccessibilityActionRetained() throws Exception {
        final AccessibilityNodeInfo sentInfo = new AccessibilityNodeInfo(new View(getContext()));
        sentInfo.addAction(ACTION_SCROLL_IN_DIRECTION);
        final Parcel parcel = Parcel.obtain();
        sentInfo.writeToParcelNoRecycle(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityNodeInfo receivedInfo = AccessibilityNodeInfo.CREATOR.createFromParcel(parcel);

        assertThat(receivedInfo.getActionList()).contains(ACTION_SCROLL_IN_DIRECTION);

        parcel.recycle();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT"})
    public void testActionArgumentScrollAmountFloat() throws Exception {
        class MyView extends TextView {
            MyView(Context context) {
                super(context);
            }

            @Override
            public boolean performAccessibilityAction(int action, Bundle args) {
                final float scrollAmount = args.getFloat(ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, -1F);
                return scrollAmount < 0 ? false : true;
            }
        }

        Bundle bundle = new Bundle();
        bundle.putFloat(ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, -1);
        String text = "action_argument_scroll_amount";

        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> {
                    final MyView myView = new MyView(getContext());
                    myView.setText(text);
                    ((LinearLayout) mActivity.findViewById(R.id.containerView)).addView(myView);
                }),
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);
        AccessibilityNodeInfo myViewNode =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        text).getFirst();

        assertThat(myViewNode.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId(),
                bundle)).isFalse();

        bundle.putFloat(ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, 1);
        assertThat(myViewNode.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId(),
                bundle)).isTrue();
    }

    @MediumTest
    @Test
    public void testCollectionInfoFields() {
        // Collection with 4 items, 1 unimportant.
        AccessibilityNodeInfo.CollectionInfo ci =
                new AccessibilityNodeInfo.CollectionInfo.Builder()
                        .setRowCount(4)
                        .setColumnCount(1)
                        .setHierarchical(false)
                        .setSelectionMode(0)
                        .setItemCount(4)
                        .setImportantForAccessibilityItemCount(3)
                        .build();

        final View listView = mActivity.findViewById(R.id.listview);

        listView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCollectionInfo(ci);
            }
        });

        AccessibilityNodeInfo foundInfo =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                        mActivity.getResources().getResourceName(R.id.listview)).get(0);
        AccessibilityNodeInfo.CollectionInfo foundCi = foundInfo.getCollectionInfo();

        assertThat(foundCi.getRowCount()).isEqualTo(ci.getRowCount());
        assertThat(foundCi.getColumnCount()).isEqualTo(ci.getColumnCount());
        assertThat(foundCi.isHierarchical()).isEqualTo(ci.isHierarchical());
        assertThat(foundCi.getSelectionMode()).isEqualTo(ci.getSelectionMode());
        assertThat(foundCi.getItemCount()).isEqualTo(ci.getItemCount());
        assertThat(foundCi.getImportantForAccessibilityItemCount()).isEqualTo(
                ci.getImportantForAccessibilityItemCount());
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getActionList"})
    public void testTooltipTextActionsReportedToAccessibility() throws Exception {
        final AccessibilityNodeInfo buttonNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/buttonWithTooltip")
                .get(0);
        assertFalse(hasTooltipShowing(R.id.buttonWithTooltip));
        assertThat(buttonNode.getActionList()).contains(ACTION_SHOW_TOOLTIP);
        assertThat(buttonNode.getActionList()).doesNotContain(ACTION_HIDE_TOOLTIP);
        sUiAutomation.executeAndWaitForEvent(
                () -> buttonNode.performAction(ACTION_SHOW_TOOLTIP.getId()),
                filterForEventTypeWithAction(
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                        ACTION_SHOW_TOOLTIP.getId()),
                DEFAULT_TIMEOUT_MS);

        // The button should now be showing the tooltip, so it should have the option to hide it.
        buttonNode.refresh();
        assertThat(buttonNode.getActionList()).contains(ACTION_HIDE_TOOLTIP);
        assertThat(buttonNode.getActionList()).doesNotContain(ACTION_SHOW_TOOLTIP);
        assertTrue(hasTooltipShowing(R.id.buttonWithTooltip));
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getTraversalBefore"})
    public void testTraversalBeforeReportedToAccessibility() throws Exception {
        final AccessibilityNodeInfo buttonNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/buttonWithTooltip")
                .get(0);
        final AccessibilityNodeInfo beforeNode = buttonNode.getTraversalBefore();
        assertThat(beforeNode).isNotNull();
        assertThat(beforeNode.getViewIdResourceName()).isEqualTo(
                "android.accessibilityservice.cts:id/edittext");

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> mActivity.findViewById(R.id.buttonWithTooltip)
                        .setAccessibilityTraversalBefore(View.NO_ID)),
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);

        buttonNode.refresh();
        assertThat(buttonNode.getTraversalBefore()).isNull();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getTraversalAfter"})
    public void testTraversalAfterReportedToAccessibility() throws Exception {
        final AccessibilityNodeInfo editNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/edittext")
                .get(0);
        final AccessibilityNodeInfo afterNode = editNode.getTraversalAfter();
        assertThat(afterNode).isNotNull();
        assertThat(afterNode.getViewIdResourceName()).isEqualTo(
                "android.accessibilityservice.cts:id/buttonWithTooltip");

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(
                () -> mActivity.findViewById(R.id.edittext)
                        .setAccessibilityTraversalAfter(View.NO_ID)),
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);

        editNode.refresh();
        assertThat(editNode.getTraversalAfter()).isNull();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getLabelFor"})
    public void testLabelForReportedToAccessibility() throws Exception {
        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(() -> mActivity
                .findViewById(R.id.edittext).setLabelFor(R.id.buttonWithTooltip)),
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);
        // TODO: b/78022650: This code should move above the executeAndWait event. It's here because
        // the a11y cache doesn't get notified when labelFor changes, so the node with the
        // labledBy isn't updated.
        final AccessibilityNodeInfo editNode = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/edittext")
                .get(0);
        editNode.refresh();
        final AccessibilityNodeInfo labelForNode = editNode.getLabelFor();
        assertThat(labelForNode).isNotNull();
        // Labeled node should indicate that it is labeled by the other one
        assertThat(labelForNode.getLabeledBy()).isEqualTo(editNode);
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#setContextClickable"})
    public void testIsImportantForAccessibility_isContextClickable_isImportant() throws
            TimeoutException {
        sInstrumentation.runOnMainSync(() -> mActivity.findViewById(R.id.autoImportantLinearLayout)
                .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO));

        final String autoImportantLinearLayoutName = mActivity.getResources().getResourceName(
                R.id.autoImportantLinearLayout);
        final AccessibilityNodeInfo autoImportantLinearLayoutNode =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                        autoImportantLinearLayoutName).get(0);

        assertThat(autoImportantLinearLayoutNode.isContextClickable()).isFalse();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isFalse();

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(() ->
                        mActivity.findViewById(R.id.autoImportantLinearLayout)
                                .setContextClickable(true)),
                // Setting clickable sends an event of subtype CONTENT_CHANGE_TYPE_UNDEFINED.
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);

        autoImportantLinearLayoutNode.refresh();
        assertThat(autoImportantLinearLayoutNode.isContextClickable()).isTrue();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isTrue();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#setAccessibilityHeading"})
    public void testIsImportantForAccessibility_isHeading_isImportant() throws
            TimeoutException {
        sInstrumentation.runOnMainSync(() -> mActivity.findViewById(R.id.autoImportantLinearLayout)
                .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO));

        final String autoImportantLinearLayoutName = mActivity.getResources().getResourceName(
                R.id.autoImportantLinearLayout);
        final AccessibilityNodeInfo autoImportantLinearLayoutNode =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                        autoImportantLinearLayoutName).get(0);

        assertThat(autoImportantLinearLayoutNode.isHeading()).isFalse();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isFalse();

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(() ->
                        mActivity.findViewById(R.id.autoImportantLinearLayout)
                                .setAccessibilityHeading(true)),
                // Setting a heading sends an event of subtype CONTENT_CHANGE_TYPE_UNDEFINED.
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);

        autoImportantLinearLayoutNode.refresh();
        assertThat(autoImportantLinearLayoutNode.isHeading()).isTrue();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isTrue();
    }

    @MediumTest

    @Test
    @ApiTest(apis = {"android.view.View#setScreenReaderFocusable"})
    public void testIsImportantForAccessibility_isScreenReaderFocusable_isImportant() throws
            TimeoutException {
        sInstrumentation.runOnMainSync(() -> mActivity.findViewById(R.id.autoImportantLinearLayout)
                .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO));

        final String autoImportantLinearLayoutName = mActivity.getResources().getResourceName(
                R.id.autoImportantLinearLayout);
        final AccessibilityNodeInfo autoImportantLinearLayoutNode =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                        autoImportantLinearLayoutName).get(0);

        assertThat(autoImportantLinearLayoutNode.isScreenReaderFocusable()).isFalse();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isFalse();

        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(() ->
                        mActivity.findViewById(R.id.autoImportantLinearLayout)
                                .setScreenReaderFocusable(true)),
                // Setting focusable sends an event of subtype CONTENT_CHANGE_TYPE_UNDEFINED.
                filterForEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
                DEFAULT_TIMEOUT_MS);

        autoImportantLinearLayoutNode.refresh();
        assertThat(autoImportantLinearLayoutNode.isScreenReaderFocusable()).isTrue();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isTrue();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo"
            + "#isImportantForAccessibility"})
    public void testDelegate_ImportantForAccessibility() throws Exception {
        final View delegateView = mActivity.findViewById(R.id.autoImportantLinearLayout);
        sInstrumentation.runOnMainSync(() ->
                delegateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO));

        final AccessibilityNodeInfo autoImportantLinearLayoutNode =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                        mActivity.getResources().getResourceName(
                                R.id.autoImportantLinearLayout)).get(0);

        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isFalse();

        sInstrumentation.runOnMainSync(() -> delegateView.setAccessibilityDelegate(
                new View.AccessibilityDelegate()));

        autoImportantLinearLayoutNode.refresh();
        assertThat(autoImportantLinearLayoutNode.isImportantForAccessibility()).isTrue();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo"
            + "#isImportantForAccessibility"})
    public void testProviderView_ImportantForAccessibility() {
        final ProviderCustomView customProviderView = mActivity.findViewById(
                R.id.autoImportantProviderView);
        sInstrumentation.runOnMainSync(() ->
                customProviderView.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO));
        // Verify first that the node is not important if there is no provider.
        customProviderView.setReturnProvider(false);
        final AccessibilityNodeInfo autoImportantProviderViewNode =
                sUiAutomation.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                        mActivity.getResources().getResourceName(
                                R.id.autoImportantProviderView)).get(0);
        assertThat(autoImportantProviderViewNode.isImportantForAccessibility()).isFalse();

        customProviderView.setReturnProvider(true);

        autoImportantProviderViewNode.refresh();

        assertThat(autoImportantProviderViewNode.isImportantForAccessibility()).isTrue();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#performAction"})
    public void testA11yActionTriggerMotionEventActionOutside() throws Exception {
        final View.OnTouchListener listener = mock(View.OnTouchListener.class);
        final AccessibilityNodeInfo button = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button")
                .get(0);
        final String title = sInstrumentation.getContext().getString(R.string.alert_title);

        // Add a dialog that is watching outside touch
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> {
                            final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                                    .setTitle(R.string.alert_title)
                                    .setMessage(R.string.alert_message)
                                    .create();
                            final Window window = dialog.getWindow();
                            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                            window.getDecorView().setOnTouchListener(listener);
                            window.setTitle(title);
                            dialog.show();
                    }),
                (event) -> {
                    // Ensure the dialog is shown over the activity
                    final AccessibilityWindowInfo dialog = findWindowByTitle(
                            sUiAutomation, title);
                    final AccessibilityWindowInfo activity = findWindowByTitle(
                            sUiAutomation, getActivityTitle(sInstrumentation, mActivity));
                    return (dialog != null && activity != null)
                            && (dialog.getLayer() > activity.getLayer());
                }, DEFAULT_TIMEOUT_MS);

        // Perform an action and wait for an event
        sUiAutomation.executeAndWaitForEvent(
                () -> button.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                filterForEventTypeWithAction(
                        AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityNodeInfo.ACTION_CLICK),
                DEFAULT_TIMEOUT_MS);

        // Make sure the MotionEvent.ACTION_OUTSIDE is received.
        verify(listener, timeout(DEFAULT_TIMEOUT_MS).atLeastOnce()).onTouch(any(View.class),
                argThat(event -> event.getActionMasked() == MotionEvent.ACTION_OUTSIDE));
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityNodeInfo#getTouchDelegateInfo"})
    public void testTouchDelegateInfoReportedToAccessibility() {
        final Button button = getOnMain(sInstrumentation, () -> mActivity.findViewById(
                R.id.button));
        final View parent = (View) button.getParent();
        final Rect rect = new Rect();
        button.getHitRect(rect);
        parent.setTouchDelegate(new TouchDelegate(rect, button));

        final AccessibilityNodeInfo nodeInfo = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/buttonLayout")
                .get(0);
        AccessibilityNodeInfo.TouchDelegateInfo targetMapInfo =
                nodeInfo.getTouchDelegateInfo();
        assertNotNull("Did not receive TouchDelegate target map", targetMapInfo);
        assertEquals("Incorrect target map size", 1, targetMapInfo.getRegionCount());
        assertEquals("Incorrect target map region", new Region(rect),
                targetMapInfo.getRegionAt(0));
        final AccessibilityNodeInfo node = targetMapInfo.getTargetForRegion(
                targetMapInfo.getRegionAt(0));
        assertEquals("Incorrect target map view",
                "android.accessibilityservice.cts:id/button",
                node.getViewIdResourceName());
        node.recycle();
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#onHoverEvent",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTouchDelegateWithEbtBetweenView_ReHoverDelegate_FocusTargetAgain()
            throws Throwable {
        mActivity.waitForEnterAnimationComplete();

        final Resources resources = sInstrumentation.getTargetContext().getResources();
        final String buttonResourceName = resources.getResourceName(R.id.button);
        final Button button = mActivity.findViewById(R.id.button);
        final int[] buttonLocation = new int[2];
        button.getLocationOnScreen(buttonLocation);
        final int buttonX = button.getWidth() / 2;
        final int buttonY = button.getHeight() / 2;
        final int hoverY = buttonLocation[1] + buttonY;
        final Button buttonWithTooltip = mActivity.findViewById(R.id.buttonWithTooltip);
        final int[] buttonWithTooltipLocation = new int[2];
        buttonWithTooltip.getLocationOnScreen(buttonWithTooltipLocation);
        final int touchableSize = resources.getDimensionPixelSize(
                R.dimen.button_touchable_width_increment_amount);
        final int hoverRight = buttonWithTooltipLocation[0] + touchableSize / 2;
        final int hoverLeft = buttonLocation[0] + button.getWidth() + touchableSize / 2;
        final int hoverMiddle = (hoverLeft + hoverRight) / 2;
        final View.OnHoverListener listener = CtsMouseUtil.installHoverListener(button, false);
        enableTouchExploration(true);

        try {
            // common downTime for touch explorer injected events
            final long downTime = SystemClock.uptimeMillis();
            // hover through delegate, parent, 2nd view, parent and delegate again
            sUiAutomation.executeAndWaitForEvent(
                    () -> injectHoverEvent(downTime, false, hoverLeft, hoverY),
                    filterForEventTypeWithResource(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                            buttonResourceName), DEFAULT_TIMEOUT_MS);
            assertTrue(button.isHovered());
            sUiAutomation.executeAndWaitForEvent(
                    () -> {
                        injectHoverEvent(downTime, true, hoverMiddle, hoverY);
                        injectHoverEvent(downTime, true, hoverRight, hoverY);
                        injectHoverEvent(downTime, true, hoverMiddle, hoverY);
                        injectHoverEvent(downTime, true, hoverLeft, hoverY);
                    },
                    filterForEventTypeWithResource(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                            buttonResourceName), DEFAULT_TIMEOUT_MS);
            // delegate target has a11y focus again
            assertTrue(button.isHovered());

            CtsMouseUtil.clearHoverListener(button);
            View.OnHoverListener verifier = inOrder(listener).verify(listener);
            verifier.onHover(eq(button),
                    matchHover(MotionEvent.ACTION_HOVER_ENTER, buttonX, buttonY));
            verifier.onHover(eq(button),
                    matchHover(MotionEvent.ACTION_HOVER_MOVE, buttonX, buttonY));
            verifier.onHover(eq(button),
                    matchHover(MotionEvent.ACTION_HOVER_MOVE, hoverMiddle, buttonY));
            verifier.onHover(eq(button),
                    matchHover(MotionEvent.ACTION_HOVER_EXIT, buttonX, buttonY));
            verifier.onHover(eq(button),
                    matchHover(MotionEvent.ACTION_HOVER_ENTER, buttonX, buttonY));
            verifier.onHover(eq(button),
                    matchHover(MotionEvent.ACTION_HOVER_MOVE, buttonX, buttonY));
        } catch (TimeoutException e) {
            fail("Accessibility events should be received as expected " + e.getMessage());
        } finally {
            enableTouchExploration(false);
        }
    }

    @MediumTest
    @Test
    @ApiTest(apis = {"android.view.View#onHoverEvent",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testTouchDelegateCoverParentWithEbt_HoverChildAndBack_FocusTargetAgain()
            throws Throwable {
        mActivity.waitForEnterAnimationComplete();

        final Resources resources = sInstrumentation.getTargetContext().getResources();
        final int touchableSize = resources.getDimensionPixelSize(
                R.dimen.button_touchable_width_increment_amount);
        final String targetResourceName = resources.getResourceName(R.id.buttonDelegated);
        final View textView = mActivity.findViewById(R.id.delegateText);
        final Button target = mActivity.findViewById(R.id.buttonDelegated);
        int[] location = new int[2];
        textView.getLocationOnScreen(location);
        final int textX = location[0] + touchableSize/2;
        final int textY = location[1] + textView.getHeight() / 2;
        final int delegateX = location[0] - touchableSize/2;
        final int targetX = target.getWidth() / 2;
        final int targetY = target.getHeight() / 2;
        final View.OnHoverListener listener = CtsMouseUtil.installHoverListener(target, false);
        enableTouchExploration(true);

        try {
            final long downTime = SystemClock.uptimeMillis();
            // Like switch bar, it has a text view, a button and a delegate covers parent layout.
            // hover the delegate, text and delegate again.
            sUiAutomation.executeAndWaitForEvent(
                    () -> injectHoverEvent(downTime, false, delegateX, textY),
                    filterForEventTypeWithResource(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                           targetResourceName), DEFAULT_TIMEOUT_MS);
            assertTrue(target.isHovered());
            sUiAutomation.executeAndWaitForEvent(
                    () -> injectHoverEvent(downTime, true, textX, textY),
                    filterForEventTypeWithResource(AccessibilityEvent.TYPE_VIEW_HOVER_EXIT,
                           targetResourceName), DEFAULT_TIMEOUT_MS);
            sUiAutomation.executeAndWaitForEvent(
                    () -> injectHoverEvent(downTime, true, delegateX, textY),
                    filterForEventTypeWithResource(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                           targetResourceName), DEFAULT_TIMEOUT_MS);
            assertTrue(target.isHovered());

            CtsMouseUtil.clearHoverListener(target);
            View.OnHoverListener verifier = inOrder(listener).verify(listener);
            verifier.onHover(eq(target),
                    matchHover(MotionEvent.ACTION_HOVER_ENTER, targetX, targetY));
            verifier.onHover(eq(target),
                    matchHover(MotionEvent.ACTION_HOVER_MOVE, targetX, targetY));
            verifier.onHover(eq(target),
                    matchHover(MotionEvent.ACTION_HOVER_MOVE, textX, textY));
            verifier.onHover(eq(target),
                    matchHover(MotionEvent.ACTION_HOVER_EXIT, targetX, targetY));
            verifier.onHover(eq(target),
                    matchHover(MotionEvent.ACTION_HOVER_ENTER, targetX, targetY));
            verifier.onHover(eq(target),
                    matchHover(MotionEvent.ACTION_HOVER_MOVE, targetX, targetY));
        } catch (TimeoutException e) {
            fail("Accessibility events should be received as expected " + e.getMessage());
        } finally {
            enableTouchExploration(false);
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_nodeMatchesViewProperty() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(true);
        try {
            final AccessibilityNodeInfo root = service.getRootInActiveWindow();

            final AccessibilityNodeInfo nonAdsNode = root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.containerView)).get(0);
            final AccessibilityNodeInfo adsNode = root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.adsView)).get(0);

            assertThat(nonAdsNode.isAccessibilityDataSensitive()).isFalse();
            assertThat(adsNode.isAccessibilityDataSensitive()).isTrue();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_visibleToAccessibilityTool() throws Throwable {
        // Relevant view structure:
        //   containerView (LinearLayout, accessibilityDataSensitive=auto)
        //     adsView (LinearLayout, accessibilityDataSensitive=true)
        //       innerContainerView (LinearLayout, accessibilityDataSensitive=auto)
        //         innerView (Button, accessibilityDataSensitive=auto)
        // Only adsView sets accessibilityDataSensitive=true in the layout XML.
        // Inner views should inherit true from their (grand)parent view.
        final StubEventCapturingAccessibilityService service = getServiceForA11yToolTests(true);
        try {
            final AccessibilityNodeInfo root = service.getRootInActiveWindow();

            final String containerViewName = mActivity.getResources().getResourceName(
                    R.id.containerView);

            final String adsViewName = mActivity.getResources().getResourceName(R.id.adsView);
            final String adsViewText = mActivity.findViewById(
                    R.id.adsView).getContentDescription().toString();

            final String innerContainerViewName = mActivity.getResources().getResourceName(
                    R.id.innerContainerView);
            final String innerContainerViewText =
                    mActivity.findViewById(
                            R.id.innerContainerView).getContentDescription().toString();

            final String innerViewName = mActivity.getResources().getResourceName(R.id.innerView);
            final String innerViewText = mActivity.findViewById(
                    R.id.innerView).getContentDescription().toString();

            // Search for the Views' nodes using various techniques:

            // ByViewId
            assertThat(root.findAccessibilityNodeInfosByViewId(adsViewName)).hasSize(1);
            assertThat(root.findAccessibilityNodeInfosByViewId(innerContainerViewName)).hasSize(1);
            assertThat(root.findAccessibilityNodeInfosByViewId(innerViewName)).hasSize(1);
            // ByText
            assertThat(root.findAccessibilityNodeInfosByText(adsViewText)).hasSize(1);
            assertThat(root.findAccessibilityNodeInfosByText(innerContainerViewText)).hasSize(1);
            assertThat(root.findAccessibilityNodeInfosByText(innerViewText)).hasSize(1);
            // Event propagation and findFocus
            service.setEventFilter(
                    filterForEventTypeWithResource(TYPE_VIEW_ACCESSIBILITY_FOCUSED, adsViewName));
            assertThat(root.findAccessibilityNodeInfosByViewId(adsViewName).get(0)
                    .performAction(ACTION_ACCESSIBILITY_FOCUS)).isTrue();
            service.waitOnEvent(DEFAULT_TIMEOUT_MS,
                    "Expected TYPE_VIEW_ACCESSIBILITY_FOCUSED event");
            assertThat(service.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY).getContentDescription()).isEqualTo(
                    adsViewText);
            // Parent view's getChild()
            final AccessibilityNodeInfo parent = root.findAccessibilityNodeInfosByViewId(
                    containerViewName).get(0);
            assertThat(parent.getChildCount()).isEqualTo(1);
            assertThat(parent.getChild(0)).isNotNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_canObserveHoverEvent() {
        final StubEventCapturingAccessibilityService service = getServiceForA11yToolTests(true);
        try {
            final long time = SystemClock.uptimeMillis();
            final View view = mActivity.findViewById(R.id.innerView);
            final int[] viewLocation = new int[2];
            view.getLocationOnScreen(viewLocation);
            final int x = viewLocation[0] + view.getWidth() / 2;
            final int y = viewLocation[1] + view.getHeight() / 2;

            service.setEventFilter(
                    filterForEventTypeWithResource(
                            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                            sInstrumentation.getTargetContext().getResources()
                                    .getResourceName(R.id.innerView)));
            injectHoverEvent(time, true, x, y);
            service.waitOnEvent(DEFAULT_TIMEOUT_MS, "Expected TYPE_VIEW_HOVER_ENTER event");
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_checkAdsProperty_topDown() {
        // Accessing the View#isAccessibilityDataSensitive() property causes both the View & its
        // parent hierarchy to cache their values.
        // Assert that the property is as expected when starting from the top-most view.
        assertThat(mActivity.findViewById(R.id.containerView).isAccessibilityDataSensitive())
                .isFalse();
        assertThat(mActivity.findViewById(R.id.adsView).isAccessibilityDataSensitive()).isTrue();
        assertThat(mActivity.findViewById(R.id.innerContainerView).isAccessibilityDataSensitive())
                .isTrue();
        assertThat(mActivity.findViewById(R.id.innerView).isAccessibilityDataSensitive()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_checkAdsProperty_bottomUp() {
        // Accessing the View#isAccessibilityDataSensitive() property causes both the View & its
        // parent hierarchy to cache their values.
        // Assert that the property is as expected when starting from the bottom-most view.
        assertThat(mActivity.findViewById(R.id.innerView).isAccessibilityDataSensitive()).isTrue();
        assertThat(mActivity.findViewById(R.id.innerContainerView).isAccessibilityDataSensitive())
                .isTrue();
        assertThat(mActivity.findViewById(R.id.adsView).isAccessibilityDataSensitive()).isTrue();
        assertThat(mActivity.findViewById(R.id.containerView).isAccessibilityDataSensitive())
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.view.accessibility.AccessibilityNodeInfo#findAccessibilityNodeInfosByViewId",
            "android.view.accessibility.AccessibilityNodeInfo#findAccessibilityNodeInfosByText",
            "android.view.accessibility.AccessibilityNodeInfo#getChild"})
    public void testAccessibilityDataSensitive_hiddenFromSearches() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(false);
        try {
            final AccessibilityNodeInfo root = service.getRootInActiveWindow();
            final String adsViewName = mActivity.getResources().getResourceName(R.id.adsView);
            final String adsViewText = mActivity.getString(R.string.ads_desc);

            assertThat(root.findAccessibilityNodeInfosByViewId(adsViewName)).isEmpty();
            assertThat(root.findAccessibilityNodeInfosByText(adsViewText)).isEmpty();
            Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
            deque.add(root);
            while (!deque.isEmpty()) {
                AccessibilityNodeInfo node = deque.removeFirst();
                assertThat(node.getContentDescription()).isNotEqualTo(adsViewText);
                for (int i = node.getChildCount() - 1; i >= 0; i--) {
                    deque.addLast(node.getChild(i));
                }
            }
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.accessibilityservice.AccessibilityService#findFocus"})
    public void testAccessibilityDataSensitive_hiddenFromFindFocus() {
        StubEventCapturingAccessibilityService toolService = null;
        InstrumentedAccessibilityService nonToolService = null;
        try {
            toolService = getServiceForA11yToolTests(true);
            nonToolService = getServiceForA11yToolTests(false);

            // Set up initial focus on the ADS view.
            toolService.setEventFilter(filterForEventType(TYPE_VIEW_ACCESSIBILITY_FOCUSED));
            assertThat(mActivity.findViewById(R.id.adsView).performAccessibilityAction(
                    ACTION_ACCESSIBILITY_FOCUS, null)).isTrue();
            toolService.waitOnEvent(DEFAULT_TIMEOUT_MS,
                    "Expected TYPE_VIEW_ACCESSIBILITY_FOCUSED event");

            assertThat(toolService.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
                    .isNotNull();
            assertThat(nonToolService.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
                    .isNull();
        } finally {
            if (toolService != null) {
                toolService.disableSelfAndRemove();
            }
            if (nonToolService != null) {
                nonToolService.disableSelfAndRemove();
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.view.accessibility.AccessibilityNodeInfo#findAccessibilityNodeInfosByViewId",
            "android.view.accessibility.AccessibilityNodeInfo#getChild"})
    public void testAccessibilityDataSensitive_excludedFromParent() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(false);
        try {
            final AccessibilityNodeInfo parentContainer =
                    service.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                            mActivity.getResources().getResourceName(R.id.containerView)).get(0);

            assertThat(parentContainer.getChildCount()).isEqualTo(0);
            assertThat(parentContainer.getChild(0)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.view.accessibility.AccessibilityNodeInfo#findAccessibilityNodeInfosByViewId"})
    public void testAccessibilityDataSensitive_innerChildHidden() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(false);

        try {
            assertThat(service.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerView))).isEmpty();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.view.accessibility.AccessibilityManager#sendAccessibilityEvent"})
    public void testAccessibilityDataSensitive_hiddenFromEventPropagation() {
        final StubEventCapturingAccessibilityService service = getServiceForA11yToolTests(false);
        try {
            final View innerView = mActivity.findViewById(R.id.innerView);
            innerView.setOnClickListener(v -> {
                // empty, but necessary for performClick to return true
            });
            assertTrue(innerView.isAccessibilityDataSensitive());
            assertTrue(innerView.isClickable());

            service.setEventFilter(filterForEventType(TYPE_VIEW_CLICKED));
            sInstrumentation.runOnMainSync(() -> assertThat(innerView.performClick()).isTrue());
            assertThrows("Received TYPE_VIEW_CLICKED event from accessibilityDataSensitive view.",
                    AssertionError.class,
                    () -> service.waitOnEvent(DEFAULT_TIMEOUT_MS, "(expected to timeout)"));
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_hiddenIfFilterTouchesWhenObscured() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(false);
        try {
            View containerView = mActivity.findViewById(R.id.containerView);
            assertThat(containerView.isAccessibilityDataSensitive()).isFalse();
            assertThat(containerView.getFilterTouchesWhenObscured()).isFalse();

            mActivity.findViewById(R.id.containerView).setFilterTouchesWhenObscured(true);

            assertThat(containerView.isAccessibilityDataSensitive()).isTrue();
            assertThat(service.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.containerView))).isEmpty();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.view.View#setAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_changingValueUpdatesChildren_noFirst() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(false);
        try {
            final AccessibilityNodeInfo root = service.getRootInActiveWindow();
            // The view starts as ADS=true as defined in the XML.
            View adsView = mActivity.findViewById(R.id.adsView);
            assertThat(adsView.isAccessibilityDataSensitive()).isTrue();

            // Set to NO, ensure we can find this view & all (grand)children.
            adsView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_NO);
            assertThat(adsView.isAccessibilityDataSensitive()).isFalse();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.adsView))).isNotEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(
                            R.id.innerContainerView))).isNotEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerView))).isNotEmpty();

            // Set back to YES, ensure this view & all (grand)children are hidden.
            adsView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
            assertThat(adsView.isAccessibilityDataSensitive()).isTrue();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.adsView))).isEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerContainerView))).isEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerView))).isEmpty();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.View#isAccessibilityDataSensitive",
            "android.view.View#setAccessibilityDataSensitive"})
    public void testAccessibilityDataSensitive_changingValueUpdatesChildren_yesFirst() {
        final InstrumentedAccessibilityService service = getServiceForA11yToolTests(false);
        try {
            final AccessibilityNodeInfo root = service.getRootInActiveWindow();
            // The view starts as AccessibilityDataSensitive=true as defined in the XML.
            View adsView = mActivity.findViewById(R.id.adsView);
            assertThat(adsView.isAccessibilityDataSensitive()).isTrue();

            // Explicitly set to YES, ensure this view & all (grand)children are hidden.
            adsView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
            assertThat(adsView.isAccessibilityDataSensitive()).isTrue();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.adsView))).isEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerContainerView))).isEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerView))).isEmpty();

            // Set to NO, ensure we can find this view & all (grand)children.
            adsView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_NO);
            assertThat(adsView.isAccessibilityDataSensitive()).isFalse();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.adsView))).isNotEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(
                            R.id.innerContainerView))).isNotEmpty();
            assertThat(root.findAccessibilityNodeInfosByViewId(
                    mActivity.getResources().getResourceName(R.id.innerView))).isNotEmpty();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityManager#isRequestFromAccessibilityTool"})
    public void testAccessibilityDataSensitive_requestIsFromAccessibilityTool_TrueForTool() {
        checkIsRequestFromAccessibilityTool(true);
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityManager#isRequestFromAccessibilityTool"})
    public void testAccessibilityDataSensitive_requestIsFromAccessibilityTool_FalseForNonTool() {
        checkIsRequestFromAccessibilityTool(false);
    }

    private void checkIsRequestFromAccessibilityTool(boolean serviceIsAccessibilityTool) {
        final InstrumentedAccessibilityService service =
            getServiceForA11yToolTests(serviceIsAccessibilityTool);
        try {
            final View view = mActivity.findViewById(R.id.listview);
            final String viewId = mActivity.getResources().getResourceName(R.id.listview);
            final AccessibilityManager accessibilityManager =
                    (AccessibilityManager) sInstrumentation.getContext().getSystemService(
                            Service.ACCESSIBILITY_SERVICE);

            final Object waitLock = new Object();
            final AtomicReference<Boolean> fromTool = new AtomicReference<>();
            view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    synchronized (waitLock) {
                        fromTool.set(accessibilityManager.isRequestFromAccessibilityTool());
                        waitLock.notifyAll();
                    }
                }
            });

            // Trigger node creation from the service-under-test.
            service.getRootInActiveWindow().findAccessibilityNodeInfosByViewId(viewId);

            TestUtils.waitOn(waitLock,
                    () -> fromTool.get() != null && fromTool.get() == serviceIsAccessibilityTool,
                    DEFAULT_TIMEOUT_MS,
                    "Expected isRequestFromAccessibilityTool to be "
                        + serviceIsAccessibilityTool);
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#setQueryFromAppProcessEnabled"})
    public void testDirectAccessibilityConnection_NavigateHierarchy() throws Throwable {
        View layoutView = mActivity.findViewById(R.id.buttonLayout);
        AccessibilityNodeInfo layoutNode = layoutView.createAccessibilityNodeInfo();

        assertThat(layoutNode).isNotNull();
        layoutNode.setQueryFromAppProcessEnabled(layoutView.getRootView(), true);

        // Access this node's children.
        assertThat(layoutNode.getChildCount()).isGreaterThan(0);
        for (int i = layoutNode.getChildCount() - 1; i >= 0; i--) {
            assertThat(layoutNode.getChild(i)).isNotNull();
        }

        // Find the root node by accessing parents going up the hierarchy.
        AccessibilityNodeInfo rootNode = layoutNode;
        while (rootNode.getParent() != null) {
            rootNode = rootNode.getParent();
        }
        assertThat(rootNode).isEqualTo(layoutView.getRootView().createAccessibilityNodeInfo());

        // Find more nodes, starting from the root.
        assertThat(rootNode.findAccessibilityNodeInfosByViewId(
                "android.accessibilityservice.cts:id/button")).isNotEmpty();
        assertThat(rootNode.findAccessibilityNodeInfosByText(
                mActivity.getString(R.string.button_title))).isNotEmpty();

        // Find and search the focus.
        try {
            // Enable touch exploration, needed for performAction(ACTION_ACCESSIBILITY_FOCUS).
            enableTouchExploration(true);
            final AccessibilityNodeInfo buttonNode = rootNode.findAccessibilityNodeInfosByViewId(
                    "android.accessibilityservice.cts:id/button").get(0);
            sUiAutomation.executeAndWaitForEvent(
                    () -> assertTrue(
                            buttonNode.performAction(
                                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)),
                    filterForEventType(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED),
                    DEFAULT_TIMEOUT_MS);
            assertThat(rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)).isEqualTo(
                    buttonNode);
            assertThat(rootNode.focusSearch(View.FOCUS_FORWARD)).isNotNull();
        } finally {
            enableTouchExploration(false);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#setQueryFromAppProcessEnabled"})
    public void testDirectAccessibilityConnection_CanPerformAction() {
        View button = mActivity.findViewById(R.id.button);
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.setOnClickListener((view) -> clicked.set(true));
        AccessibilityNodeInfo buttonNode = button.createAccessibilityNodeInfo();

        assertThat(buttonNode).isNotNull();
        buttonNode.setQueryFromAppProcessEnabled(button.getRootView(), true);

        assertThat(buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)).isTrue();
        assertThat(clicked.get()).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#setQueryFromAppProcessEnabled"})
    public void testDirectAccessibilityConnection_CanDisable() {
        View layoutView = mActivity.findViewById(R.id.buttonLayout);
        AccessibilityNodeInfo layoutNode = layoutView.createAccessibilityNodeInfo();
        assertThat(layoutNode).isNotNull();

        layoutNode.setQueryFromAppProcessEnabled(layoutView.getRootView(), true);
        assertThat(layoutNode.getParent()).isNotNull();

        layoutNode.setQueryFromAppProcessEnabled(layoutView.getRootView(), false);
        try {
            layoutNode.getParent();
            fail("Should not be able to navigate node tree on node without any connection.");
        } catch (IllegalStateException e) {
            // expected due to undefined connection ID
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#setQueryFromAppProcessEnabled"})
    public void testDirectAccessibilityConnection_AccessibilityManagerEnabled() {
        // Note: this test checks AM#hasAnyDirectConnection() as a proxy for #isEnabled because
        // #isEnabled is also modified by the UiAutomation used in this test.

        View layoutView = mActivity.findViewById(R.id.buttonLayout);
        AccessibilityNodeInfo layoutNode = layoutView.createAccessibilityNodeInfo();
        final AccessibilityManager accessibilityManager =
                (AccessibilityManager) sInstrumentation.getContext().getSystemService(
                        Service.ACCESSIBILITY_SERVICE);

        // Ensure no DirectConnection to start.
        assertThat(accessibilityManager.hasAnyDirectConnection()).isFalse();

        // Enable app-process querying, which adds a connection for this node.
        layoutNode.setQueryFromAppProcessEnabled(layoutView.getRootView(), true);
        assertThat(accessibilityManager.hasAnyDirectConnection()).isTrue();

        // Disable app-process querying for this node.
        layoutNode.setQueryFromAppProcessEnabled(layoutView.getRootView(), false);
        // The connection should still exist until ViewRootImpl detaches from the window, in case
        // other nodes in this view hierarchy use the connection.
        assertThat(accessibilityManager.hasAnyDirectConnection()).isTrue();

        // Detach the ViewRootImpl from the window by finishing the activity, then wait for the
        // change notification that comes from ViewRootImpl itself, after which the connection
        // should now be gone.
        final Object waitLock = new Object();
        final AtomicBoolean hasAnyDirectConnection = new AtomicBoolean(true);
        accessibilityManager.addAccessibilityStateChangeListener(
                enabled -> {
                    synchronized (waitLock) {
                        hasAnyDirectConnection.set(accessibilityManager.hasAnyDirectConnection());
                        waitLock.notifyAll();
                    }
                });
        mActivity.runOnUiThread(() -> mActivity.finish());
        TestUtils.waitOn(waitLock, () -> !hasAnyDirectConnection.get(), DEFAULT_TIMEOUT_MS,
                "AccessibilityManager#hasAnyDirectConnection() still true");
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#setQueryFromAppProcessEnabled"})
    public void testDirectAccessibilityConnection_UsesCurrentWindowSpec() throws Throwable {
        // Store the initial bounds of the ANI.
        final View layoutView = mActivity.findViewById(R.id.buttonLayout);
        final AccessibilityNodeInfo layoutNode = layoutView.createAccessibilityNodeInfo();
        final Rect initialBounds = new Rect();
        layoutNode.setQueryFromAppProcessEnabled(layoutView, true);
        layoutNode.getBoundsInScreen(initialBounds);

        // Magnify the screen.
        final StubMagnificationAccessibilityService service =
                InstrumentedAccessibilityService.enableService(
                        StubMagnificationAccessibilityService.class);
        try {
            final MagnificationConfig magnificationConfig =
                    new MagnificationConfig.Builder().setMode(MAGNIFICATION_MODE_FULLSCREEN)
                            .setScale(2f).build();
            service.runOnServiceSync(
                    () -> service.getMagnificationController()
                            .setMagnificationConfig(magnificationConfig, false));

            // Check that the ANI bounds have changed.
            TestUtils.waitUntil("Failed to refresh node with updated boundsInScreen",
                    (int) DEFAULT_TIMEOUT_MS / 1000,
                    () -> {
                        final Rect boundsAfterMagnification = new Rect();
                        layoutNode.refresh();
                        layoutNode.getBoundsInScreen(boundsAfterMagnification);
                        return !boundsAfterMagnification.equals(initialBounds);
                    });
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo"
                    + "#setMinDurationBetweenContentChanges",
            "android.view.accessibility.AccessibilityNodeInfo"
                    + "#getMinDurationBetweenContentChanges"})
    public void testSetMinDurationBetweenContentChanges() {
        final View testView = mActivity.findViewById(R.id.buttonLayout);
        final AccessibilityNodeInfo nodeInfo = testView.createAccessibilityNodeInfo();
        nodeInfo.setMinDurationBetweenContentChanges(Duration.ofMillis(200));
        assertThat(nodeInfo.getMinDurationBetweenContentChanges().toMillis()).isEqualTo(200);
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo"
                    + "#setRequestInitialAccessibilityFocus",
            "android.view.accessibility.AccessibilityNodeInfo"
                    + "#hasRequestInitialAccessibilityFocus"})
    public void testSetRequestInitialAccessibilityFocus() {
        final View testView = mActivity.findViewById(R.id.buttonLayout);
        final AccessibilityNodeInfo nodeInfo = testView.createAccessibilityNodeInfo();
        nodeInfo.setRequestInitialAccessibilityFocus(true);
        assertThat(nodeInfo.hasRequestInitialAccessibilityFocus()).isTrue();
    }


    @AsbSecurityTest(cveBugId = {243378132})
    @Test
    public void testUninstallPackage_DisablesMultipleServices() throws Exception {
        final String apkPath =
                "/data/local/tmp/cts/content/CtsAccessibilityMultipleServicesApp.apk";
        final String packageName = "foo.bar.multipleservices";
        final ComponentName service1 = ComponentName.createRelative(packageName, ".StubService1");
        final ComponentName service2 = ComponentName.createRelative(packageName, ".StubService2");
        // Match AccessibilityManagerService#COMPONENT_NAME_SEPARATOR
        final String componentNameSeparator = ":";

        final String originalEnabledServicesSetting = getEnabledServicesSetting();

        try {
            // Install the apk in this test method, instead of as part of the target preparer, to
            // allow repeated --iterations of the test.
            assertThat(ShellUtils.runShellCommand("pm install " + apkPath)).startsWith("Success");

            // Enable the two services and wait until AccessibilityManager reports them as enabled.
            final String servicesToEnable = getEnabledServicesSetting() + componentNameSeparator
                    + service1.flattenToShortString() + componentNameSeparator
                    + service2.flattenToShortString();
            ShellCommandBuilder.create(sInstrumentation)
                    .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            servicesToEnable)
                    .putSecureSetting(Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                    .run();
            TestUtils.waitUntil("Failed to enable 2 services from package " + packageName,
                    (int) TIMEOUT_SERVICE_ENABLE / 1000,
                    () -> getEnabledServices().stream().filter(
                            info -> info.getId().startsWith(packageName)).count() == 2);

            // Uninstall the package that contains the services.
            assertThat(ShellUtils.runShellCommand("pm uninstall " + packageName)).startsWith(
                    "Success");

            // Ensure the uninstall removed the services from the secure setting.
            TestUtils.waitUntil(
                    "Failed to disable services after uninstalling package " + packageName,
                    (int) TIMEOUT_SERVICE_ENABLE / 1000,
                    () -> !getEnabledServicesSetting().contains(packageName));
        } finally {
            ShellCommandBuilder.create(sInstrumentation)
                    .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            originalEnabledServicesSetting)
                    .run();
            ShellUtils.runShellCommand("pm uninstall " + packageName);
        }
    }

    @AsbSecurityTest(cveBugId = {282016107})
    @AppModeFull
    @Test
    public void testInstallAppWithLargeServiceVolume_displaysServicesSuccessfully()
            throws Throwable {

        // The apk used for this test deliberately includes a large amount of junk services,
        // so we're installing/uninstalling it as part of the test instead of leaving it in.
        final String apkPath =
                "/data/local/tmp/cts/content/CtsAccessibilityLargeServiceVolumeApp.apk";
        final String packageName = "foo.bar.multipleservices";
        final int installedServiceCount = 16; // 16 unique services present in manifest.
        AccessibilityManager manager = mActivity.getSystemService(AccessibilityManager.class);

        try {
            assertThat(ShellUtils.runShellCommand(
                    "pm install " + apkPath)).startsWith("Success");
            TestUtils.waitUntil(
                    "Installed services have not appeared on the list.",
                    TIMEOUT_SERVICE_ENABLE / 1000,
                    () -> {
                        List<AccessibilityServiceInfo> installedServices =
                                manager.getInstalledAccessibilityServiceList();
                        int count = 0;
                        for (int i = 0; i < installedServices.size(); i++) {
                            if (installedServices.get(i).getId().contains("JunkService")) {
                                count++;
                            }
                        }
                        return count == installedServiceCount;
                    }
            );
        } finally {
            ShellUtils.runShellCommand("pm uninstall " + packageName);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityNodeInfo#setContainerTitle"})
    public void testSetContainerTitle() {
        View testView = mActivity.findViewById(R.id.buttonLayout);
        AccessibilityNodeInfo nodeInfo = testView.createAccessibilityNodeInfo();
        nodeInfo.setContainerTitle("Container title");
        assertEquals("Container title", nodeInfo.getContainerTitle());

        nodeInfo.setContainerTitle(null);
        assertEquals(null, nodeInfo.getContainerTitle());
    }

    @Test
    @FlakyTest
    @ApiTest(apis = {"android.accessibilityservice.AccessibilityService#onMotionEvent"})
    public void testOnMotionEvent_interceptsEventFromRequestedSource_SetAndUnset() {
        final int requestedSource = InputDevice.SOURCE_JOYSTICK;
        final StubMotionInterceptingAccessibilityService service =
                mMotionInterceptingServiceRule.enableService();
        service.setMotionEventSources(requestedSource);
        assertThat(service.getServiceInfo().getMotionEventSources()).isEqualTo(requestedSource);
        final Object waitObject = new Object();
        final AtomicInteger eventCount = new AtomicInteger(0);
        service.setOnMotionEventListener(motionEvent -> {
            synchronized (waitObject) {
                if (motionEvent.getSource() == requestedSource) {
                    eventCount.incrementAndGet();
                }
                waitObject.notifyAll();
            }
        });

        // Inject 2 events to the input filter.
        sUiAutomation.injectInputEventToInputFilter(createMotionEvent(requestedSource));
        sUiAutomation.injectInputEventToInputFilter(createMotionEvent(requestedSource));
        // We should find 2 events.
        TestUtils.waitOn(waitObject, () -> eventCount.get() == 2,
                TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS,
                "Service did not receive MotionEvent");

        // Stop listening to events for this source, then inject 1 more event to the input filter.
        service.setMotionEventSources(0 /* no sources */);
        assertThat(service.getServiceInfo().getMotionEventSources()).isEqualTo(0);
        sUiAutomation.injectInputEventToInputFilter(createMotionEvent(requestedSource));
        // Assert we only received the original 2.
        try {
            TestUtils.waitOn(waitObject, () -> eventCount.get() == 3,
                    TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS,
                    "(expected)");
        } catch (AssertionError e) {
            // expected
        }
        assertThat(eventCount.get()).isEqualTo(2);
    }

    @Test
    @ApiTest(apis = {"android.accessibilityservice.AccessibilityService#onMotionEvent"})
    public void testOnMotionEvent_ignoresEventFromDifferentSource() {
        final int requestedSource = InputDevice.SOURCE_JOYSTICK;
        final int actualSource = InputDevice.SOURCE_ROTARY_ENCODER;
        final StubMotionInterceptingAccessibilityService service =
                mMotionInterceptingServiceRule.enableService();
        service.setMotionEventSources(requestedSource);
        final Object waitObject = new Object();
        final AtomicBoolean foundEvent = new AtomicBoolean(false);
        service.setOnMotionEventListener(motionEvent -> {
            synchronized (waitObject) {
                if (motionEvent.getSource() == requestedSource) {
                    foundEvent.set(true);
                }
                waitObject.notifyAll();
            }
        });

        sUiAutomation.injectInputEventToInputFilter(createMotionEvent(actualSource));

        try {
            TestUtils.waitOn(waitObject, foundEvent::get, TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS,
                    "(expected)");
        } catch (AssertionError e) {
            // expected
        }
        assertThat(foundEvent.get()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.accessibilityservice.AccessibilityService#onMotionEvent"})
    public void testOnMotionEvent_ignoresTouchscreenEventWhenTouchExplorationEnabled() {
        final int requestedSource = InputDevice.SOURCE_TOUCHSCREEN;
        final StubMotionInterceptingAccessibilityService motionInterceptingService =
                mMotionInterceptingServiceRule.enableService();
        TouchExplorationStubAccessibilityService touchExplorationService =
                enableService(TouchExplorationStubAccessibilityService.class);
        try {
            motionInterceptingService.setMotionEventSources(requestedSource);
            final Object waitObject = new Object();
            final AtomicBoolean foundEvent = new AtomicBoolean(false);
            motionInterceptingService.setOnMotionEventListener(motionEvent -> {
                synchronized (waitObject) {
                    if (motionEvent.getSource() == requestedSource) {
                        foundEvent.set(true);
                    }
                    waitObject.notifyAll();
                }
            });

            sUiAutomation.injectInputEventToInputFilter(createMotionEvent(requestedSource));

            try {
                TestUtils.waitOn(waitObject, foundEvent::get,
                        TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS,
                        "(expected)");
            } catch (AssertionError e) {
                // expected
            }
            assertThat(foundEvent.get()).isFalse();
        } finally {
            touchExplorationService.disableSelfAndRemove();
        }
    }

    /** Test the case where we want to intercept but not consume motion events. */
    @Test
    @FlakyTest
    @ApiTest(apis = {"android.accessibilityservice.AccessibilityService#onMotionEvent"})
    @RequiresFlagsEnabled(android.view.accessibility.Flags.FLAG_MOTION_EVENT_OBSERVING)
    public void testOnMotionEvent_interceptsEventFromRequestedSource_observesMotionEvents() {
        // Don't run this test on systems without a touchscreen.
        PackageManager pm = sInstrumentation.getTargetContext().getPackageManager();
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN));

        sUiAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.ACCESSIBILITY_MOTION_EVENT_OBSERVING);
        final int requestedSource = InputDevice.SOURCE_TOUCHSCREEN;
        final StubMotionInterceptingAccessibilityService service =
                mMotionInterceptingServiceRule.enableService();
        service.setMotionEventSources(requestedSource);
        service.setObservedMotionEventSources(requestedSource);
        assertThat(service.getServiceInfo().getMotionEventSources()).isEqualTo(requestedSource);
        assertThat(service.getServiceInfo().getObservedMotionEventSources())
                .isEqualTo(requestedSource);
        final Object waitObject = new Object();
        final AtomicInteger eventCount = new AtomicInteger(0);
        service.setOnMotionEventListener(
                motionEvent -> {
                    synchronized (waitObject) {
                        if (motionEvent.getSource() == requestedSource) {
                            eventCount.incrementAndGet();
                        }
                        waitObject.notifyAll();
                    }
                });

        // Simulate a tap on the center of the button.
        final Button button = (Button) mActivity.findViewById(R.id.button);
        final EventCapturingMotionEventListener listener = new EventCapturingMotionEventListener();
        button.setOnTouchListener(listener);
        int[] buttonLocation = new int[2];
        final int midX = button.getWidth() / 2;
        final int midY = button.getHeight() / 2;
        button.getLocationOnScreen(buttonLocation);
        PointF tapLocation = new PointF(buttonLocation[0] + midX, buttonLocation[1] + midY);
        try {
            dispatch(service, click(tapLocation));
        } catch (RuntimeException e) {
            // The input filter could have been  rebuilt causing this gesture to cancel.
            // Reset state and try again.
            eventCount.set(0);
            listener.clear();
            dispatch(service, click(tapLocation));
        }

        // We should find 2 events.
        TestUtils.waitOn(
                waitObject,
                () -> eventCount.get() == 2,
                TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS,
                "Service did not receive MotionEvent");

        // The view should still have seen two events.
        listener.assertPropagated(ACTION_DOWN, ACTION_UP);
        // Stop listening to events for this source, then inject 1 more event to the input filter.
        service.setMotionEventSources(0 /* no sources */);
        assertThat(service.getServiceInfo().getMotionEventSources()).isEqualTo(0);
        try {
            dispatch(service, click(tapLocation));
        } catch (RuntimeException e) {
            // The input filter could have been  rebuilt causing this gesture to cancel.
            // Reset state and try again.
            eventCount.set(2);
            listener.clear();
            dispatch(service, click(tapLocation));
        }

        // Assert we only received the original 2.
        try {
            TestUtils.waitOn(
                    waitObject,
                    () -> eventCount.get() == 3,
                    TIMEOUT_FOR_MOTION_EVENT_INTERCEPTION_MS,
                    "(expected)");
        } catch (AssertionError e) {
            // expected
        }
        assertThat(eventCount.get()).isEqualTo(2);
    }

    /**
     * Test the case where we want to intercept but not consume motion events, but another service
     * has already enabled touch exploration. Motion event observing should not work.
     */
    @Test
    @FlakyTest
    @ApiTest(apis = {"android.accessibilityservice.AccessibilityService#onMotionEvent"})
    @RequiresFlagsEnabled(android.view.accessibility.Flags.FLAG_MOTION_EVENT_OBSERVING)
    public void testMotionEventObserving_ignoresTouchscreenEventWhenTouchExplorationEnabled() {
        // Don't run this test on systems without a touchscreen.
        PackageManager pm = sInstrumentation.getTargetContext().getPackageManager();
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN));

        sUiAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.ACCESSIBILITY_MOTION_EVENT_OBSERVING);
        final int requestedSource = InputDevice.SOURCE_TOUCHSCREEN;
        final StubMotionInterceptingAccessibilityService service =
                mMotionInterceptingServiceRule.enableService();
        service.setMotionEventSources(requestedSource);
        service.setObservedMotionEventSources(requestedSource);
        assertThat(service.getServiceInfo().getMotionEventSources()).isEqualTo(requestedSource);
        assertThat(service.getServiceInfo().getObservedMotionEventSources())
                .isEqualTo(requestedSource);
        TouchExplorationStubAccessibilityService touchExplorationService =
                enableService(TouchExplorationStubAccessibilityService.class);
        try {
            final Object waitObject = new Object();
            final AtomicInteger eventCount = new AtomicInteger(0);
            service.setOnMotionEventListener(
                    motionEvent -> {
                        synchronized (waitObject) {
                            if (motionEvent.getSource() == requestedSource) {
                                eventCount.incrementAndGet();
                            }
                            waitObject.notifyAll();
                        }
                    });

            // Simulate a tap on the center of the button.
            final Button button = (Button) mActivity.findViewById(R.id.button);
            final EventCapturingMotionEventListener listener =
                    new EventCapturingMotionEventListener();
            button.setOnTouchListener(listener);
            button.setOnHoverListener(listener);
            int[] buttonLocation = new int[2];
            final int midX = button.getWidth() / 2;
            final int midY = button.getHeight() / 2;
            button.getLocationOnScreen(buttonLocation);
            PointF tapLocation = new PointF(buttonLocation[0] + midX, buttonLocation[1] + midY);
            try {
                dispatch(service, click(tapLocation));
            } catch (RuntimeException e) {
                // The input filter could have been rebuilt causing this gesture to cancel.
                // Reset state and try again.
                eventCount.set(0);
                listener.clear();
                dispatch(service, click(tapLocation));
            }

            // The view should have seen two hover events.
            listener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
            // The observing service shouldn't see any events.
            assertThat(eventCount.get()).isEqualTo(0);
        } finally {
            touchExplorationService.disableSelfAndRemove();
        }
    }

    private MotionEvent createMotionEvent(int source) {
        // Only source is used by these tests, so set other properties to valid defaults.
        final long eventTime = SystemClock.uptimeMillis();
        final MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        return MotionEvent.obtain(eventTime,
                eventTime,
                MotionEvent.ACTION_MOVE,
                1 /* pointerCount */,
                new MotionEvent.PointerProperties[]{props},
                new MotionEvent.PointerCoords[]{new MotionEvent.PointerCoords()},
                0 /* metaState */,
                0 /* buttonState */,
                0 /* xPrecision */,
                0 /* yPrecision */,
                1 /* deviceId */,
                0 /* edgeFlags */,
                source,
                0 /* flags */);
    }

    private List<AccessibilityServiceInfo> getEnabledServices() {
        return ((AccessibilityManager) sInstrumentation.getContext().getSystemService(
                Context.ACCESSIBILITY_SERVICE)).getEnabledAccessibilityServiceList(
                FEEDBACK_ALL_MASK);
    }

    private String getEnabledServicesSetting() {
        final String result = Settings.Secure.getString(
                sInstrumentation.getContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return result != null ? result : "";
    }

    private static void assertPackageName(AccessibilityNodeInfo node, String packageName) {
        if (node == null) {
            return;
        }
        assertEquals(packageName, node.getPackageName());
        final int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                assertPackageName(child, packageName);
            }
        }
    }

    private static void enableTouchExploration(boolean enabled)
            throws InterruptedException {
        final int TIMEOUT_FOR_SERVICE_ENABLE = 10000; // millis; 10s
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(!enabled);
        AccessibilityManager.TouchExplorationStateChangeListener serviceListener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        final AccessibilityManager manager =
                (AccessibilityManager) sInstrumentation.getContext().getSystemService(
                        Service.ACCESSIBILITY_SERVICE);
        manager.addTouchExplorationStateChangeListener(serviceListener);

        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        assert info != null;
        if (enabled) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }
        sUiAutomation.setServiceInfo(info);

        final long timeoutTime = System.currentTimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        synchronized (waitObject) {
            while ((enabled != atomicBoolean.get()) && (System.currentTimeMillis() < timeoutTime)) {
                waitObject.wait(timeoutTime - System.currentTimeMillis());
            }
        }
        if (enabled) {
            assertTrue("Touch exploration state listener not called when services enabled",
                    atomicBoolean.get());
            assertTrue("Timed out enabling accessibility",
                    manager.isEnabled() && manager.isTouchExplorationEnabled());
        } else {
            assertFalse("Touch exploration state listener not called when services disabled",
                    atomicBoolean.get());
            assertFalse("Timed out disabling accessibility",
                    manager.isEnabled() && manager.isTouchExplorationEnabled());
        }
        manager.removeTouchExplorationStateChangeListener(serviceListener);
    }

    /**
     * Returns a service for testing how accessibility tools or non-tools react to the
     * {@link View#isAccessibilityDataSensitive} property.
     *
     * @return {@link StubA11yToolAccessibilityService} when <code>isAccessibilityTool</code> is
     * true, otherwise returns {@link StubNonA11yToolAccessibilityService}.
     */
    private StubEventCapturingAccessibilityService getServiceForA11yToolTests(
            boolean isAccessibilityTool) {
        final StubEventCapturingAccessibilityService service;
        if (isAccessibilityTool) {
            service = InstrumentedAccessibilityService.enableService(
                    StubA11yToolAccessibilityService.class);
        } else {
            service = InstrumentedAccessibilityService.enableService(
                    StubNonA11yToolAccessibilityService.class);
        }
        final AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null || info.isAccessibilityTool() != isAccessibilityTool) {
            service.disableSelfAndRemove();
            fail("Expected service to have isAccessibilityTool=" + isAccessibilityTool);
        }
        return service;
    }

    private static MotionEvent matchHover(int action, int x, int y) {
        return argThat(new CtsMouseUtil.PositionMatcher(action, x, y));
    }

    private static void injectHoverEvent(long downTime, boolean isFirstHoverEvent,
            int xOnScreen, int yOnScreen) {
        final long eventTime = isFirstHoverEvent ? SystemClock.uptimeMillis() : downTime;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_HOVER_MOVE,
                xOnScreen, yOnScreen, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sInstrumentation.sendPointerSync(event);
        event.recycle();
    }

    private AppWidgetProviderInfo getAppWidgetProviderInfo() {
        final ComponentName componentName = new ComponentName(
                "foo.bar.baz", "foo.bar.baz.MyAppWidgetProvider");
        final List<AppWidgetProviderInfo> providers = getAppWidgetManager().getInstalledProviders();
        final int providerCount = providers.size();
        for (int i = 0; i < providerCount; i++) {
            final AppWidgetProviderInfo provider = providers.get(i);
            if (componentName.equals(provider.provider)
                    && Process.myUserHandle().equals(provider.getProfile())) {
                return provider;
            }
        }
        return null;
    }

    private void grantBindAppWidgetPermission() throws Exception {
        ShellCommandBuilder.execShellCommand(sUiAutomation,
                GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND + getCurrentUser());
    }

    private void revokeBindAppWidgetPermission() throws Exception {
        ShellCommandBuilder.execShellCommand(sUiAutomation,
                REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND + getCurrentUser());
    }

    private AppWidgetManager getAppWidgetManager() {
        return (AppWidgetManager) sInstrumentation.getTargetContext()
                .getSystemService(Context.APPWIDGET_SERVICE);
    }

    private boolean hasAppWidgets() {
        return sInstrumentation.getTargetContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS);
    }

    /**
     * Compares all properties of the <code>first</code> and the
     * <code>second</code>.
     */
    private boolean equalsAccessiblityEvent(AccessibilityEvent first, AccessibilityEvent second) {
         return first.getEventType() == second.getEventType()
            && first.isChecked() == second.isChecked()
            && first.getCurrentItemIndex() == second.getCurrentItemIndex()
            && first.isEnabled() == second.isEnabled()
            && first.getFromIndex() == second.getFromIndex()
            && first.getItemCount() == second.getItemCount()
            && first.isPassword() == second.isPassword()
            && first.getRemovedCount() == second.getRemovedCount()
            && first.isScrollable()== second.isScrollable()
            && first.getToIndex() == second.getToIndex()
            && first.getRecordCount() == second.getRecordCount()
            && first.getScrollX() == second.getScrollX()
            && first.getScrollY() == second.getScrollY()
            && first.getAddedCount() == second.getAddedCount()
            && first.getDisplayId() == second.getDisplayId()
            && TextUtils.equals(first.getBeforeText(), second.getBeforeText())
            && TextUtils.equals(first.getClassName(), second.getClassName())
            && TextUtils.equals(first.getContentDescription(), second.getContentDescription())
            && equalsNotificationAsParcelableData(first, second)
            && equalsText(first, second);
    }

    /**
     * Compares the {@link android.os.Parcelable} data of the
     * <code>first</code> and <code>second</code>.
     */
    private boolean equalsNotificationAsParcelableData(AccessibilityEvent first,
            AccessibilityEvent second) {
        Notification firstNotification = (Notification) first.getParcelableData();
        Notification secondNotification = (Notification) second.getParcelableData();
        if (firstNotification == null) {
            return (secondNotification == null);
        } else if (secondNotification == null) {
            return false;
        }
        return TextUtils.equals(firstNotification.tickerText, secondNotification.tickerText);
    }

    /**
     * Compares the text of the <code>first</code> and <code>second</code> text.
     */
    private boolean equalsText(AccessibilityEvent first, AccessibilityEvent second) {
        List<CharSequence> firstText = first.getText();
        List<CharSequence> secondText = second.getText();
        if (firstText.size() != secondText.size()) {
            return false;
        }
        Iterator<CharSequence> firstIterator = firstText.iterator();
        Iterator<CharSequence> secondIterator = secondText.iterator();
        for (int i = 0; i < firstText.size(); i++) {
            if (!firstIterator.next().toString().equals(secondIterator.next().toString())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasTooltipShowing(int id) {
        return getOnMain(sInstrumentation, () -> {
            final View viewWithTooltip = mActivity.findViewById(id);
            if (viewWithTooltip == null) {
                return false;
            }
            final View tooltipView = viewWithTooltip.getTooltipView();
            return (tooltipView != null) && (tooltipView.getParent() != null);
        });
    }

    private void dispatch(
            InstrumentedAccessibilityService service,
            StrokeDescription firstStroke,
            StrokeDescription... rest) {
        GestureDescription.Builder builder =
                new GestureDescription.Builder().addStroke(firstStroke);
        for (StrokeDescription stroke : rest) {
            builder.addStroke(stroke);
        }
        dispatch(service, builder.build());
    }

    private void dispatch(InstrumentedAccessibilityService service, GestureDescription gesture) {
        await(dispatchGesture(service, gesture));
    }

    private static int getCurrentUser() {
        return android.os.Process.myUserHandle().getIdentifier();
    }
}
