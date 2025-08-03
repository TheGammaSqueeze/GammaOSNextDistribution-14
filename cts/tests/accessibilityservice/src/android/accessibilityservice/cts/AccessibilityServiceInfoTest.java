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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.accessibility.AccessibilityEvent;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.server.accessibility.Flags;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Class for testing {@link AccessibilityServiceInfo}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
public class AccessibilityServiceInfoTest extends StsExtraBusinessLogicTestCase {

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @MediumTest
    @Test
    public void testMarshalling() throws Exception {

        // fully populate the service info to marshal
        AccessibilityServiceInfo sentInfo = new AccessibilityServiceInfo();
        fullyPopulateSentAccessibilityServiceInfo(sentInfo);

        // marshal and unmarshal the service info
        Parcel parcel = Parcel.obtain();
        sentInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityServiceInfo receivedInfo = AccessibilityServiceInfo.CREATOR
                .createFromParcel(parcel);

        // make sure all fields properly marshaled
        assertAllFieldsProperlyMarshalled(sentInfo, receivedInfo);
    }

    /**
     * Tests whether the service info describes its contents consistently.
     */
    @MediumTest
    @Test
    public void testDescribeContents() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        assertSame("Accessibility service info always return 0 for this method.", 0,
                info.describeContents());
        fullyPopulateSentAccessibilityServiceInfo(info);
        assertSame("Accessibility service infos always return 0 for this method.", 0,
                info.describeContents());
    }

    /**
     * Tests whether a feedback type is correctly transformed to a string.
     */
    @MediumTest
    @Test
    public void testFeedbackTypeToString() {
        assertEquals("[FEEDBACK_AUDIBLE]", AccessibilityServiceInfo.feedbackTypeToString(
                AccessibilityServiceInfo.FEEDBACK_AUDIBLE));
        assertEquals("[FEEDBACK_GENERIC]", AccessibilityServiceInfo.feedbackTypeToString(
                AccessibilityServiceInfo.FEEDBACK_GENERIC));
        assertEquals("[FEEDBACK_HAPTIC]", AccessibilityServiceInfo.feedbackTypeToString(
                AccessibilityServiceInfo.FEEDBACK_HAPTIC));
        assertEquals("[FEEDBACK_SPOKEN]", AccessibilityServiceInfo.feedbackTypeToString(
                AccessibilityServiceInfo.FEEDBACK_SPOKEN));
        assertEquals("[FEEDBACK_VISUAL]", AccessibilityServiceInfo.feedbackTypeToString(
                AccessibilityServiceInfo.FEEDBACK_VISUAL));
        assertEquals("[FEEDBACK_BRAILLE]", AccessibilityServiceInfo.feedbackTypeToString(
                AccessibilityServiceInfo.FEEDBACK_BRAILLE));
        assertEquals("[FEEDBACK_SPOKEN, FEEDBACK_HAPTIC, FEEDBACK_AUDIBLE, FEEDBACK_VISUAL,"
                + " FEEDBACK_GENERIC, FEEDBACK_BRAILLE]",
                AccessibilityServiceInfo.feedbackTypeToString(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK));
    }

    /**
     * Tests whether a flag is correctly transformed to a string.
     */
    @MediumTest
    @Test
    public void testFlagToString() {
        assertEquals("DEFAULT", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.DEFAULT));
        assertEquals("FLAG_INCLUDE_NOT_IMPORTANT_VIEWS", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS));
        assertEquals("FLAG_REPORT_VIEW_IDS", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS));
        assertEquals("FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY", AccessibilityServiceInfo
                .flagToString(AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY));
        assertEquals("FLAG_REQUEST_FILTER_KEY_EVENTS", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS));
        assertEquals("FLAG_REQUEST_TOUCH_EXPLORATION_MODE", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE));
        assertEquals("FLAG_RETRIEVE_INTERACTIVE_WINDOWS", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS));
        assertEquals("FLAG_ENABLE_ACCESSIBILITY_VOLUME", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME));
        assertEquals("FLAG_REQUEST_ACCESSIBILITY_BUTTON", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON));
        assertEquals("FLAG_REQUEST_FINGERPRINT_GESTURES", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES));
        assertEquals("FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK", AccessibilityServiceInfo.flagToString(
                AccessibilityServiceInfo.FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK));

    }

    @Test
    @AsbSecurityTest(cveBugId = {277072324})
    public void testSetServiceInfo_throwsForLargeServiceInfo_calledUsingReflection()
            throws Exception {
        try {
            final InstrumentedAccessibilityService service =
                    InstrumentedAccessibilityService.enableService(
                            InstrumentedAccessibilityService.class);
            final AccessibilityServiceInfo info = service.getServiceInfo();
            setLargePackageNames(info);

            // NOTE: Using reflection requires setting the HIDDEN_API_POLICY global setting.
            // This is done in AndroidTest.xml for this test because it must be set before
            // the test process is started.
            final Field connectionIdField = AccessibilityService.class.getDeclaredField(
                    "mConnectionId");
            connectionIdField.setAccessible(true); // Allow the test to read this private field.
            final Method getConnection =
                    Class.forName("android.view.accessibility.AccessibilityInteractionClient")
                            .getDeclaredMethod("getConnection", int.class);
            final Object connection = getConnection.invoke(null, connectionIdField.get(service));
            final Method setServiceInfo =
                    Class.forName("android.accessibilityservice.IAccessibilityServiceConnection")
                            .getDeclaredMethod("setServiceInfo", AccessibilityServiceInfo.class);

            InvocationTargetException exception = assertThrows(
                    InvocationTargetException.class, () -> setServiceInfo.invoke(connection, info));
            assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        } finally {
            InstrumentedAccessibilityService.disableAllServices();
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = {261589597})
    public void testSetServiceInfo_throwsForLargeServiceInfo() {
        try {
            final InstrumentedAccessibilityService service =
                    InstrumentedAccessibilityService.enableService(
                            InstrumentedAccessibilityService.class);
            final AccessibilityServiceInfo info = service.getServiceInfo();
            setLargePackageNames(info);

            assertThrows(IllegalStateException.class, () -> service.setServiceInfo(info));
        } finally {
            InstrumentedAccessibilityService.disableAllServices();
        }
    }

    @Test
    public void testDefaultConstructor() throws Exception {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();

        assertWithMessage("info.getId()").that(info.getId()).isNull();
        assertWithMessage("info.toString()").that(info.toString()).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RESETTABLE_DYNAMIC_PROPERTIES)
    public void testDynamicallyConfigurableProperties_doNotPersist() {
        try {
            final int flag = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            final InstrumentedAccessibilityService service =
                    InstrumentedAccessibilityService.enableService(
                            InstrumentedAccessibilityService.class);
            final AccessibilityServiceInfo info = service.getServiceInfo();
            assertThat(info.flags & flag).isEqualTo(0);
            info.flags |= flag;
            service.setServiceInfo(info);
            assertThat(service.getServiceInfo().flags & flag).isEqualTo(flag);
            InstrumentedAccessibilityService.disableAllServices();

            final InstrumentedAccessibilityService reenabledService =
                    InstrumentedAccessibilityService.enableService(
                            InstrumentedAccessibilityService.class);
            assertThat(reenabledService.getServiceInfo().flags & flag).isEqualTo(0);
        } finally {
            InstrumentedAccessibilityService.disableAllServices();
        }
    }

    /**
     * Fully populates the {@link AccessibilityServiceInfo} to marshal.
     *
     * @param sentInfo The service info to populate.
     */
    private void fullyPopulateSentAccessibilityServiceInfo(AccessibilityServiceInfo sentInfo) {
        sentInfo.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED;
        sentInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        sentInfo.flags = AccessibilityServiceInfo.DEFAULT;
        sentInfo.notificationTimeout = 1000;
        sentInfo.packageNames = new String[] {
            "foo.bar.baz"
        };
        sentInfo.setInteractiveUiTimeoutMillis(2000);
        sentInfo.setNonInteractiveUiTimeoutMillis(4000);
        sentInfo.setAccessibilityTool(true);
    }

    /**
     * Compares all properties of the <code>sentInfo</code> and the
     * <code>receviedInfo</code> to make sure marshaling is correctly
     * implemented.
     */
    private void assertAllFieldsProperlyMarshalled(AccessibilityServiceInfo sentInfo,
            AccessibilityServiceInfo receivedInfo) {
        assertEquals("eventTypes not marshalled properly", sentInfo.eventTypes,
                receivedInfo.eventTypes);
        assertEquals("feedbackType not marshalled properly", sentInfo.feedbackType,
                receivedInfo.feedbackType);
        assertEquals("flags not marshalled properly", sentInfo.flags, receivedInfo.flags);
        assertEquals("notificationTimeout not marshalled properly", sentInfo.notificationTimeout,
                receivedInfo.notificationTimeout);
        assertEquals("packageNames not marshalled properly", sentInfo.packageNames.length,
                receivedInfo.packageNames.length);
        assertEquals("packageNames not marshalled properly", sentInfo.packageNames[0],
                receivedInfo.packageNames[0]);
        assertEquals("interactiveUiTimeout not marshalled properly",
                sentInfo.getInteractiveUiTimeoutMillis(),
                receivedInfo.getInteractiveUiTimeoutMillis());
        assertEquals("nonInteractiveUiTimeout not marshalled properly",
                sentInfo.getNonInteractiveUiTimeoutMillis(),
                receivedInfo.getNonInteractiveUiTimeoutMillis());
        assertEquals("isAccessibilityTool not marshalled properly",
                sentInfo.isAccessibilityTool(), receivedInfo.isAccessibilityTool());
    }

    private static void setLargePackageNames(AccessibilityServiceInfo info) {
        // android_util_Binder.cpp says that very large transactions (above 200*1024 bytes)
        // will fail with TransactionTooLargeException, but the accessibility framework uses the
        // more aggressive suggested individual parcel size limit of IBinder.java's
        // MAX_IPC_SIZE (64*1024 bytes).
        info.packageNames = new String[]{"A".repeat(1024 * 32)};
    }
}
