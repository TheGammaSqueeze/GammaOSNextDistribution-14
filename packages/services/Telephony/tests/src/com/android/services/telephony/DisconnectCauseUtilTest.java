/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.services.telephony;

import static android.media.ToneGenerator.TONE_PROP_PROMPT;
import static android.media.ToneGenerator.TONE_SUP_BUSY;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class DisconnectCauseUtilTest extends TelephonyTestBase {

    // constants
    public static final int PHONE_ID = 123;
    public static final String EMPTY_STRING = "";

    // dynamic
    private Context mContext;

    //Mocks
    @Mock
    private GsmCdmaPhone mMockPhone;

    private final FlagsAdapter mFeatureFlags = new FlagsAdapter(){
        @Override
        public boolean doNotOverridePreciseLabel() {
            return true;
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // objects that call static getInstance()
        mMockPhone = mock(GsmCdmaPhone.class);
        mContext = InstrumentationRegistry.getTargetContext();
        // set mocks
        setSinglePhone();
    }

    /**
     * Verifies that a call drop due to loss of WIFI results in a disconnect cause of error and that
     * the label, description and tone are all present.
     */
    @Test
    public void testDropDueToWifiLoss() {
        android.telecom.DisconnectCause tcCause = DisconnectCauseUtil.toTelecomDisconnectCause(
                DisconnectCause.WIFI_LOST);
        assertEquals(android.telecom.DisconnectCause.ERROR, tcCause.getCode());
        assertEquals(TONE_PROP_PROMPT, tcCause.getTone());
        assertNotNull(tcCause.getDescription());
        assertNotNull(tcCause.getReason());
    }

    /**
     *  ensure the default behavior was not changed when a disconnect cause comes in as
     *  DisconnectCause.ERROR_UNSPECIFIED
     */
    @Test
    public void testDefaultDisconnectCauseBehaviorForCauseNotInCarrierBusyToneArray() {
        android.telecom.DisconnectCause tcCause = DisconnectCauseUtil.toTelecomDisconnectCause(
                DisconnectCause.ERROR_UNSPECIFIED, -1, EMPTY_STRING, PHONE_ID, null, mFeatureFlags);
        // CODE
        assertEquals(android.telecom.DisconnectCause.ERROR, tcCause.getCode());
        // LABEL
        safeAssertLabel(null, tcCause);
        // TONE
        assertEquals(TONE_PROP_PROMPT, tcCause.getTone());
    }

    /**
     * verify that if a precise label is given Telephony, the label is not overridden by Telecom
     */
    @Test
    public void testDefaultPhoneConfig_NoPreciseLabelGiven() {
        android.telecom.DisconnectCause tcCause =
                DisconnectCauseUtil.toTelecomDisconnectCause(DisconnectCause.BUSY,
                        -1 /*  precise label is NOT given */,
                        EMPTY_STRING, PHONE_ID, null /* carrier config is NOT set */,
                        mFeatureFlags);
        assertBusyCauseWithTargetLabel(R.string.callFailed_userBusy, tcCause);
    }

    /**
     * verify that if a precise label is given Telephony, the label is not overridden by Telecom
     */
    @Test
    public void testDefaultPhoneConfig_PreciseLabelProvided() {
        android.telecom.DisconnectCause tcCause =
                DisconnectCauseUtil.toTelecomDisconnectCause(DisconnectCause.BUSY,
                        CallFailCause.USER_BUSY /* Telephony defined a precise label */,
                        EMPTY_STRING, PHONE_ID, null /* carrier config is NOT set */,
                        mFeatureFlags);
        // Note: The precise label should not be overridden even though the carrier defined
        // the cause to play a busy tone
        assertBusyCauseWithTargetLabel(R.string.clh_callFailed_user_busy_txt, tcCause);
    }

    /**
     * special case: The Carrier has re-defined a disconnect code that should play a busy tone.
     * Thus, the code, label, and tone should be remapped.
     * <p>
     * <p>
     * Verify that if the disconnect cause is in the carrier busy tone array that the expected
     * label, tone, and code are returned.
     */
    @Test
    public void testCarrierSetBusyToneArray_NoPreciseLabelGiven() {
        android.telecom.DisconnectCause tcCause =
                DisconnectCauseUtil.toTelecomDisconnectCause(
                        DisconnectCause.BUSY, -1 /*  precise label is NOT given */,
                        EMPTY_STRING, PHONE_ID, null, getBundleWithBusyToneArray(), mFeatureFlags);

        assertBusyCauseWithTargetLabel(R.string.callFailed_userBusy, tcCause);
    }

    /**
     * special case: The Carrier has re-defined a disconnect code that should play a busy tone.
     * Thus, the code, label, and tone should be remapped.
     * <p>
     * <p>
     * Verify that if the disconnect cause is in the carrier busy tone array and the Telephony
     * stack has provided a precise label, the label is not overridden.
     */
    @Test
    public void testCarrierSetBusyToneArray_PreciseLabelProvided() {
        android.telecom.DisconnectCause tcCause =
                DisconnectCauseUtil.toTelecomDisconnectCause(DisconnectCause.BUSY,
                        CallFailCause.USER_BUSY /* Telephony defined a precise label */,
                        EMPTY_STRING, PHONE_ID, null, getBundleWithBusyToneArray(), mFeatureFlags);
        // Note: The precise label should not be overridden even though the carrier defined
        // the cause to play a busy tone
        assertBusyCauseWithTargetLabel(R.string.clh_callFailed_user_busy_txt, tcCause);
    }

    /**
     * Ensure the helper doesCarrierClassifyDisconnectCauseAsBusyCause does not hit a NPE if a
     * NULL carrier config is passed in.
     */
    @Test
    public void testDoesCarrierClassifyDisconnectCauseAsBusyCause_nullConfig() {
        assertFalse(DisconnectCauseUtil.doesCarrierClassifyDisconnectCauseAsBusyCause(-1, null));
    }

    /**
     * Ensure the helper doesCarrierClassifyDisconnectCauseAsBusyCause does not hit a NPE if an
     * EMPTY carrier config is passed in.
     */
    @Test
    public void testDoesCarrierClassifyDisconnectCauseAsBusyCause_ConfigDoesNotDefineArray() {
        PersistableBundle config = new PersistableBundle();
        assertFalse(DisconnectCauseUtil.doesCarrierClassifyDisconnectCauseAsBusyCause(-1, config));
    }

    /**
     * Ensure the helper doesCarrierClassifyDisconnectCauseAsBusyCause does not hit a NPE if an
     * EMPTY array is defined for KEY_DISCONNECT_CAUSE_PLAY_BUSYTONE_INT_ARRAY.
     */
    @Test
    public void testDoesCarrierClassifyDisconnectCauseAsBusyCause_ConfigHasEmptyArray() {
        PersistableBundle config = new PersistableBundle();
        int[] carrierBusyArr = {}; // NOTE: This is intentionally let empty

        config.putIntArray(
                CarrierConfigManager.KEY_DISCONNECT_CAUSE_PLAY_BUSYTONE_INT_ARRAY,
                carrierBusyArr);

        assertFalse(DisconnectCauseUtil.doesCarrierClassifyDisconnectCauseAsBusyCause(-1, config));
    }

    /**
     * Ensure {@link DisconnectCauseUtil#doesCarrierClassifyDisconnectCauseAsBusyCause} returns
     * FALSE is the passed in disconnect cause is NOT the busy tone array
     */
    @Test
    public void testDoesCarrierClassifyDisconnectCauseAsBusyCause_ConfigHasBusyToneButNotMatch() {
        assertFalse(DisconnectCauseUtil.doesCarrierClassifyDisconnectCauseAsBusyCause(-1,
                getBundleWithBusyToneArray()));
    }

    /**
     * Ensure {@link DisconnectCauseUtil#doesCarrierClassifyDisconnectCauseAsBusyCause} returns
     * TRUE if the disconnect cause is defined in the busy tone array (by the Carrier)
     */
    @Test
    public void testDoesCarrierClassifyDisconnectCauseAsBusyCause_ConfigHasBusyTone() {
        assertTrue(DisconnectCauseUtil.doesCarrierClassifyDisconnectCauseAsBusyCause(
                DisconnectCause.BUSY, getBundleWithBusyToneArray()));
    }

    private void assertBusyCauseWithTargetLabel(Integer targetLabel,
            android.telecom.DisconnectCause disconnectCause) {
        // CODE: Describes the cause of a disconnected call
        assertEquals(android.telecom.DisconnectCause.BUSY, disconnectCause.getCode());
        // LABEL: This is the label that the user sees
        safeAssertLabel(targetLabel, disconnectCause);
        // TONE: This is the DTMF tone being played to the user
        assertEquals(TONE_SUP_BUSY, disconnectCause.getTone());
    }

    private PersistableBundle getBundleWithBusyToneArray() {
        int[] carrierBusyArr = {DisconnectCause.BUSY};
        PersistableBundle config = new PersistableBundle();

        config.putIntArray(
                CarrierConfigManager.KEY_DISCONNECT_CAUSE_PLAY_BUSYTONE_INT_ARRAY,
                carrierBusyArr);
        return config;
    }

    private void setSinglePhone() throws Exception {
        Phone[] mPhones = new Phone[]{mMockPhone};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
    }

    private Resources getResourcesForLocale(Context context, Locale locale) {
        Configuration config = new Configuration();
        config.setToDefaults();
        config.setLocale(locale);
        Context localeContext = context.createConfigurationContext(config);
        return localeContext.getResources();
    }

    private void safeAssertLabel(Integer resourceId,
            android.telecom.DisconnectCause disconnectCause) {
        Resources r = getResourcesForLocale(mContext, Locale.US);
        if (resourceId == null || r == null) {
            return;
        }
        String label = r.getString(resourceId);
        assertEquals(label, disconnectCause.getLabel());
    }

    /**
     * Verifies that an ICC_ERROR disconnect cause generates a message which mentions there is no
     * SIM.
     */
    @Test
    public void testIccError() {
        android.telecom.DisconnectCause tcCause = DisconnectCauseUtil.toTelecomDisconnectCause(
                DisconnectCause.ICC_ERROR);
        assertEquals(android.telecom.DisconnectCause.ERROR, tcCause.getCode());
        assertNotNull(tcCause.getLabel());
        assertNotNull(tcCause.getDescription());
    }
}
