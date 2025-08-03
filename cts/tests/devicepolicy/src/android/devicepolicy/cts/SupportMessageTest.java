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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.SupportMessage;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SupportMessageTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int MAX_MSG_LEN = 65535;

    private static final int REASONABLE_SHORT_MSG_LEN = 150;
    private static final int REASONABLE_LONG_MSG_LEN = 4000;
    private static final int UNREASONABLE_MSG_LEN = 100000;

    private static final String VALID_SHORT_MSG = stringOfLength(REASONABLE_SHORT_MSG_LEN);
    private static final String VALID_LONG_MSG = stringOfLength(REASONABLE_LONG_MSG_LEN);
    private static final String TOO_LONG_MSG = stringOfLength(UNREASONABLE_MSG_LEN);

    private static final String EMPTY_MSG = "";

    private RemoteDevicePolicyManager mDevicePolicyManager;
    private ComponentName mAdmin;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDevicePolicyManager = dpc.devicePolicyManager();
    }

    @After
    public void tearDown() {
        try {
            mDevicePolicyManager.setShortSupportMessage(mAdmin, /* charSequence= */ null);
            mDevicePolicyManager.setLongSupportMessage(mAdmin, /* charSequence= */ null);
        } catch (SecurityException e) {
            // Expected when testing lack-of-permission
        }
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_validText_works() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SHORT_MSG);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo(VALID_SHORT_MSG);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_validText_works() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_LONG_MSG);

        assertThat(mDevicePolicyManager.getLongSupportMessage(mAdmin).toString())
                .isEqualTo(VALID_LONG_MSG);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_emptyText_works() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, EMPTY_MSG);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin).toString())
                .isEqualTo(EMPTY_MSG);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    @Ignore("b/278717644")
    public void setLongSupportMessage_nullText_clearsOldText() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_LONG_MSG);
        mDevicePolicyManager.setLongSupportMessage(mAdmin, /* charSequence= */ null);

        assertThat(mDevicePolicyManager.getLongSupportMessage(mAdmin)).isNull();
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    @Ignore("b/278717644")
    public void setShortSupportMessage_nullText_clearsOldText() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SHORT_MSG);
        mDevicePolicyManager.setShortSupportMessage(mAdmin, /* charSequence= */ null);

        assertThat(mDevicePolicyManager.getShortSupportMessage(mAdmin)).isNull();
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_emptyText_works() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, EMPTY_MSG);

        assertThat(mDevicePolicyManager.getLongSupportMessage(mAdmin).toString()).isEmpty();
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.setShortSupportMessage(
                        /* componentName= */ null, VALID_SHORT_MSG));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.setLongSupportMessage(
                        /* componentName= */ null, VALID_LONG_MSG));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void getShortSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.getShortSupportMessage(
                        /* componentName= */ null));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void getLongSupportMessage_nullAdmin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                mDevicePolicyManager.getLongSupportMessage(/* componentName= */ null));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void getLongSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.getLongSupportMessage(mAdmin));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_LONG_MSG));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void getShortSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.getShortSupportMessage(mAdmin));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = SupportMessage.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SHORT_MSG));
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_validText_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            mDevicePolicyManager.setShortSupportMessage(mAdmin, VALID_SHORT_MSG);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SHORT_SUPPORT_MESSAGE_VALUE)
                    .whereAdminPackageName().isEqualTo(mAdmin.getPackageName())
                    .poll())
                    .isNotNull();
        }
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_validText_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            mDevicePolicyManager.setLongSupportMessage(mAdmin, VALID_LONG_MSG);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_LONG_SUPPORT_MESSAGE_VALUE)
                    .whereAdminPackageName().isEqualTo(mAdmin.getPackageName())
                    .poll())
                    .isNotNull();
        }
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setShortSupportMessage_tooLongText_isTruncated() {
        mDevicePolicyManager.setShortSupportMessage(mAdmin, TOO_LONG_MSG);

        String effectiveMessage = mDevicePolicyManager.getShortSupportMessage(mAdmin).toString();

        assertThat(effectiveMessage.length()).isAtMost(MAX_MSG_LEN);
        assertThat(TOO_LONG_MSG).startsWith(effectiveMessage);
    }

    @PolicyAppliesTest(policy = SupportMessage.class)
    @Postsubmit(reason = "new test")
    public void setLongSupportMessage_tooLongText_isTruncated() {
        mDevicePolicyManager.setLongSupportMessage(mAdmin, TOO_LONG_MSG);

        String effectiveMessage = mDevicePolicyManager.getLongSupportMessage(mAdmin).toString();

        assertThat(effectiveMessage.length()).isAtMost(MAX_MSG_LEN);
        assertThat(TOO_LONG_MSG).startsWith(effectiveMessage);
    }

    private static String stringOfLength(int length) {
        return new String(new char[length]).replace('\0', 'A');
    }
}
