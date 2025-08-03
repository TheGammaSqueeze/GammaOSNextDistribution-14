/*
 * Copyright (C) 2024 The Android Open Source Project
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

// This CTS test has been created taking reference from the tests present in
// frameworks/base/core/tests/coretests/src/android/app/NotificationChannelGroupTest.java
// frameworks/base/core/tests/coretests/src/android/app/NotificationChannelTest.java

package android.security.cts;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.text.TextUtils;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20485 extends StsExtraBusinessLogicTestCase {
    private static final int INPUT_STRING_LENGTH = 2000;
    private static final int INPUT_VIBRATION_LENGTH = 2000;
    private static final String NOTIFICATION_CHANNEL_GROUPID = "groupId";
    private static final String NOTIFICATION_CHANNEL_GROUPNAME = "groupName";
    private static final String NOTIFICATION_CHANNEL_ID = "id";
    private static final String NOTIFICATION_CHANNEL_NAME = "name";
    private static final String mLongString =
            String.join("", Collections.nCopies(INPUT_STRING_LENGTH, "A"));
    private Uri mLongUri;

    private List<String> testLongStringFieldsNotificationChannelGroup(
            String mLongString, boolean checkId, boolean checkName, boolean checkDesc)
            throws Exception {
        List<String> violations = new ArrayList<String>();
        String tag = "testLongStringFieldsNotificationChannelGroup() ";
        String className = "android.app.NotificationChannelGroup";
        NotificationChannelGroup group =
                new NotificationChannelGroup(
                        NOTIFICATION_CHANNEL_GROUPID, NOTIFICATION_CHANNEL_GROUPNAME);
        if (checkId) {
            Field id = Class.forName(className).getDeclaredField("mId");
            id.setAccessible(true);
            id.set(group, mLongString);
        }
        if (checkName) {
            Field name = Class.forName(className).getDeclaredField("mName");
            name.setAccessible(true);
            name.set(group, mLongString);
        }
        if (checkDesc) {
            Field description = Class.forName(className).getDeclaredField("mDescription");
            description.setAccessible(true);
            description.set(group, mLongString);
        }
        Parcel parcel = Parcel.obtain();
        group.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NotificationChannelGroup fromParcel =
                NotificationChannelGroup.CREATOR.createFromParcel(parcel);
        if (checkId && INPUT_STRING_LENGTH <= fromParcel.getId().length()) {
            violations.add(tag + "input string length <= Parcel ID length");
        }
        if (checkName && INPUT_STRING_LENGTH <= fromParcel.getName().length()) {
            violations.add(tag + "input string length <= Parcel name length");
        }
        if (checkDesc && INPUT_STRING_LENGTH <= fromParcel.getDescription().length()) {
            violations.add(tag + "input string length <= Parcel Description length");
        }
        return violations;
    }

    private List<String> testNullableFields(boolean checkId, boolean checkName) throws Exception {
        String tag = "testNullableFields() ";
        List<String> violations = new ArrayList<String>();
        NotificationChannelGroup group =
                new NotificationChannelGroup(NOTIFICATION_CHANNEL_GROUPID, null);
        Parcel parcel = Parcel.obtain();
        group.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NotificationChannelGroup fromParcel =
                NotificationChannelGroup.CREATOR.createFromParcel(parcel);
        if (checkId && group.getId() == fromParcel.getId()) {
            violations.add(tag + "group ID == Parcel ID");
        }
        if (checkName && !TextUtils.isEmpty(fromParcel.getName())) {
            violations.add(tag + "parcel name is not empty");
        }
        return violations;
    }

    private List<String> testLongStringFieldsNotificationChannel(
            String mLongString,
            boolean checkId,
            boolean checkName,
            boolean checkDesc,
            boolean checkGroup,
            boolean checkConvId)
            throws Exception {
        List<String> violations = new ArrayList<String>();
        String tag = "testLongStringFieldsNotificationChannel() ";
        String className = "android.app.NotificationChannel";
        NotificationChannel channel =
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
        Field parentId = Class.forName(className).getDeclaredField("mParentId");
        parentId.setAccessible(true);
        parentId.set(channel, mLongString);
        if (checkId) {
            Field id = Class.forName(className).getDeclaredField("mId");
            id.setAccessible(true);
            id.set(channel, mLongString);
        }
        if (checkName) {
            Field name = Class.forName(className).getDeclaredField("mName");
            name.setAccessible(true);
            name.set(channel, mLongString);
        }
        if (checkDesc) {
            Field desc = Class.forName(className).getDeclaredField("mDesc");
            desc.setAccessible(true);
            desc.set(channel, mLongString);
        }
        if (checkGroup) {
            Field group = Class.forName(className).getDeclaredField("mGroup");
            group.setAccessible(true);
            group.set(channel, mLongString);
        }
        if (checkConvId) {
            Field conversationId = Class.forName(className).getDeclaredField("mConversationId");
            conversationId.setAccessible(true);
            conversationId.set(channel, mLongString);
        }
        Parcel parcel = Parcel.obtain();
        channel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NotificationChannel fromParcel = NotificationChannel.CREATOR.createFromParcel(parcel);
        if (checkId && INPUT_STRING_LENGTH <= fromParcel.getId().length()) {
            violations.add(tag + "input string length <= Parcel ID length");
        }
        if (checkName && INPUT_STRING_LENGTH <= fromParcel.getName().length()) {
            violations.add(tag + "input string length <= Parcel name length");
        }
        if (checkDesc && INPUT_STRING_LENGTH <= fromParcel.getDescription().length()) {
            violations.add(tag + "input string length <= Parcel Description length");
        }
        if (checkGroup && INPUT_STRING_LENGTH <= fromParcel.getGroup().length()) {
            violations.add(tag + "input string length <= Parcel group length");
        }
        if (checkConvId && INPUT_STRING_LENGTH <= fromParcel.getConversationId().length()) {
            violations.add(tag + "input string length <= Parcel conversationId length");
        }
        return violations;
    }

    private List<String> testLongAlertFields(
            String mLongString, boolean checkVibration, boolean checkSound) throws Exception {
        String tag = "testLongAlertFields() ";
        List<String> violations = new ArrayList<String>();
        NotificationChannel channel =
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
        if (checkVibration) {
            channel.setVibrationPattern(new long[INPUT_VIBRATION_LENGTH]);
        }
        if (checkSound) {
            channel.setSound(mLongUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        }
        Parcel parcel = Parcel.obtain();
        channel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NotificationChannel fromParcel = NotificationChannel.CREATOR.createFromParcel(parcel);
        if (checkVibration && INPUT_STRING_LENGTH <= fromParcel.getVibrationPattern().length) {
            violations.add(tag + "input string length <= Parcel vibration length");
        }
        if (checkSound
                && mLongUri.toString().length() <= fromParcel.getSound().toString().length()) {
            violations.add(tag + "longUri string length <= Parcel sound length");
        }
        return violations;
    }

    @AsbSecurityTest(cveBugId = 241764135)
    @Test
    public void testPocCVE_2022_20478() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannelGroup(
                            mLongString,
                            true /* checkId */,
                            false /* checkName */,
                            false /* checkDesc */);
            List<String> violationsTestNullableFields =
                    testNullableFields(true /* checkId */, false /* checkName */);
            violations.addAll(violationsTestNullableFields);
            assertWithMessage(
                            "Device is vulnerable to : b/241764135(CVE-2022-20478) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 241764340)
    @Test
    public void testPocCVE_2022_20479() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannelGroup(
                            mLongString,
                            false /* checkId */,
                            false /* checkName */,
                            true /* checkDesc */);
            assertWithMessage(
                            "Device is vulnerable to : b/241764340 (CVE-2022-20479) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 241764350)
    @Test
    public void testPocCVE_2022_20480() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannelGroup(
                            mLongString,
                            false /* checkId */,
                            true /* checkName */,
                            false /* checkDesc */);
            List<String> violationsTestNullableFields =
                    testNullableFields(false /* checkId */, true /* checkName */);
            violations.addAll(violationsTestNullableFields);
            assertWithMessage(
                            "Device is vulnerable to : b/241764350(CVE-2022-20480) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 242702851)
    @Test
    public void testPocCVE_2022_20484() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannel(
                            mLongString,
                            false /* checkId */,
                            true /* checkName */,
                            false /* checkDesc */,
                            false /* checkGroup */,
                            false /* checkConvId */);
            assertWithMessage(
                            "Device is vulnerable to : b/242702851(CVE-2022-20484) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 242702935)
    @Test
    public void testPocCVE_2022_20485() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannel(
                            mLongString,
                            false /* checkId */,
                            false /* checkName */,
                            false /* checkDesc */,
                            false /* checkGroup */,
                            true /* checkConvId */);
            assertWithMessage(
                            "Device is vulnerable to : b/242702935(CVE-2022-20485) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 242703118)
    @Test
    public void testPocCVE_2022_20486() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannel(
                            mLongString,
                            false /* checkId */,
                            false /* checkName */,
                            true /* checkDesc */,
                            false /* checkGroup */,
                            false /* checkConvId */);
            assertWithMessage(
                            "Device is vulnerable to : b/242703118(CVE-2022-20486) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 242703202)
    @Test
    public void testPocCVE_2022_20487() {
        try {
            mLongUri = Uri.parse("condition://" + mLongString);
            List<String> violations =
                    testLongAlertFields(
                            mLongString, false /* checkVibration */, true /* checkSound */);
            assertWithMessage(
                            "Device is vulnerable to : b/242703202(CVE-2022-20487) due to :"
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 242703217)
    @Test
    public void testPocCVE_2022_20488() {
        try {
            List<String> violations =
                    testLongStringFieldsNotificationChannel(
                            mLongString,
                            true /* checkId */,
                            false /* checkName */,
                            false /* checkDesc */,
                            false /* checkGroup */,
                            false /* checkConvId */);
            assertWithMessage(
                            "Device is vulnerable to : b/242703217(CVE-2022-20488) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    @AsbSecurityTest(cveBugId = 242703556)
    @Test
    public void testPocCVE_2022_20491() {
        try {
            mLongUri = Uri.parse("condition://" + mLongString);
            List<String> violations =
                    testLongAlertFields(
                            mLongString, true /* checkVibration */, false /* checkSound */);
            assertWithMessage(
                            "Device is vulnerable to : b/242703556(CVE-2022-20491) due to : "
                                    + violations)
                    .that(violations.isEmpty())
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
