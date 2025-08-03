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

package android.telephonyprovider.cts;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.provider.Telephony.ServiceStateTable.DATA_NETWORK_TYPE;
import static android.provider.Telephony.ServiceStateTable.DATA_REG_STATE;
import static android.provider.Telephony.ServiceStateTable.DUPLEX_MODE;
import static android.provider.Telephony.ServiceStateTable.VOICE_REG_STATE;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;


import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SmallTest
public class ServiceStateTest {

    private static final int DEFAULT_TIMEOUT = 1000;
    // Keep the same as ServiceStateProvider#SERVICE_STATE which is NOT same as
    // Telephony.ServiceStateTable.AUTHORITY;
    private static final String SERVICE_STATE = "service_state";

    private ContentResolver mContentResolver;
    private TelephonyManager mTelephonyManager;
    private int mSubId;
    private @Nullable ServiceState mInitialServiceState;

    @Before
    public void setUp() {
        assumeTrue(hasTelephonyFeature());

        mContentResolver = getInstrumentation().getContext().getContentResolver();
        mTelephonyManager =
                getInstrumentation().getContext().getSystemService(TelephonyManager.class);
        mSubId = SubscriptionManager.getDefaultSubscriptionId();
        mInitialServiceState = mTelephonyManager.getServiceState();
    }

    @After
    public void tearDown() {
        if (!hasTelephonyFeature()) {
            return;
        }

        // Recover the initial ServiceState to remove the impact of manual ServiceState insertion.
        if (mInitialServiceState != null) {
            insertServiceState(mInitialServiceState);
        }
    }

    /**
     * Verifies that the ServiceStateTable CONTENT_URI and AUTHORITY is valid.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#AUTHORITY",
            "android.provider.Telephony.ServiceStateTable#AUTHORCONTENT_URI"})
    public void testUriAndAuthority() {
        Uri uri = Telephony.ServiceStateTable.CONTENT_URI;
        assertThat(uri).isEqualTo(Uri.parse("content://service-state/"));

        String authority = Telephony.ServiceStateTable.AUTHORITY;
        assertThat(authority).isEqualTo("service-state");
    }

    /**
     * Verifies that the voice reg state is valid and matches ServiceState#getState().
     * This test case requires an active subscription.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#VOICE_REG_STATE"})
    public void testGetVoiceRegState_query() {
        if (!isSubscriptionActive(mSubId)) {
            return;
        }

        try (Cursor cursor = mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                new String[]{VOICE_REG_STATE}, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();

            int voiceRegState = cursor.getInt(cursor.getColumnIndex(VOICE_REG_STATE));
            assertThat(voiceRegState).isEqualTo(mTelephonyManager.getServiceState().getState());
        }
    }

    /**
     * Verifies that when voice reg state did not change, the observer should not receive the
     * notification.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#VOICE_REG_STATE"})
    public void testGetVoiceRegState_noChangeObserved() throws Exception {
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setState(ServiceState.STATE_OUT_OF_SERVICE);

        ServiceState copyOfOldSS = new ServiceState();
        copyOfOldSS.setStateOutOfService();
        copyOfOldSS.setState(ServiceState.STATE_OUT_OF_SERVICE);
        // set additional fields which is not related to voice reg state
        copyOfOldSS.setChannelNumber(65536);
        copyOfOldSS.setIsManualSelection(true);

        verifyNotificationObservedWhenFieldChanged(
                VOICE_REG_STATE, oldSS, copyOfOldSS, false /*expectChange*/);
    }

    /**
     * Verifies that when voice reg state changed, the observer should receive the notification.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#VOICE_REG_STATE"})
    public void testGetVoiceRegState_changeObserved() throws Exception {
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setState(ServiceState.STATE_OUT_OF_SERVICE);

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();
        newSS.setState(ServiceState.STATE_POWER_OFF);

        verifyNotificationObservedWhenFieldChanged(
                VOICE_REG_STATE, oldSS, newSS, true /*expectChange*/);
    }

    /**
     * Verifies that the data network type is valid and matches ServiceState#getDataNetworkType().
     * This test case requires an active subscription.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DATA_NETWORK_TYPE"})
    public void testGetDataNetworkType_query() {
        if (!isSubscriptionActive(mSubId)) {
            return;
        }

        try (Cursor cursor = mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                new String[]{DATA_NETWORK_TYPE}, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();

            int dataNetworkType = cursor.getInt(cursor.getColumnIndex(DATA_NETWORK_TYPE));
            assertThat(dataNetworkType).isEqualTo(
                    mTelephonyManager.getServiceState().getDataNetworkType());
        }
    }

    /**
     * Verifies that when data network type did not change, the observer should not receive the
     * notification.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DATA_NETWORK_TYPE"})
    public void testDataNetworkType_noChangeObserved() throws Exception {
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();

        ServiceState copyOfOldSS = new ServiceState();
        copyOfOldSS.setStateOutOfService();

        // Add a DOMAIN_CS NRI which should not update DataNetworkType
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_GPRS)
                .setRegistrationState(REGISTRATION_STATE_HOME)
                .build();
        copyOfOldSS.addNetworkRegistrationInfo(nri);

        verifyNotificationObservedWhenFieldChanged(
                DATA_NETWORK_TYPE, oldSS, copyOfOldSS, false /*expectChange*/);
    }

    /**
     * Verifies that when data network type changed, the observer should receive the notification.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DATA_NETWORK_TYPE"})
    public void testDataNetworkType_changeObserved() throws Exception {
        // While we don't have a method to directly set dataNetworkType, we emulate a ServiceState
        // change that will trigger the change of dataNetworkType, according to the logic in
        // ServiceState#getDataNetworkType()
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();

        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(REGISTRATION_STATE_HOME)
                .build();
        newSS.addNetworkRegistrationInfo(nri);

        verifyNotificationObservedWhenFieldChanged(
                DATA_NETWORK_TYPE, oldSS, newSS, true /*expectChange*/);
    }

    /**
     * Verifies that the duplex mode is valid and matches ServiceState#getDuplexMode().
     * This test case requires an active subscription.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DUPLEX_MODE"})
    public void testGetDuplexMode_query() {
        if (!isSubscriptionActive(mSubId)) {
            return;
        }

        try (Cursor cursor = mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                new String[]{DUPLEX_MODE}, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();

            int duplexMode = cursor.getInt(cursor.getColumnIndex(DUPLEX_MODE));
            assertThat(duplexMode).isEqualTo(
                    mTelephonyManager.getServiceState().getDuplexMode());
        }
    }

    /**
     * Verifies that even we have duplex mode change, the observer should not receive the
     * notification (duplex mode is a poll-only field).
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DUPLEX_MODE"})
    public void testGetDuplexMode_noChangeObserved() throws Exception {
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();

        // Add NRI to trigger SS with duplex mode updated
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();
        newSS.addNetworkRegistrationInfo(nri);
        newSS.setChannelNumber(65536); // EutranBand.BAND_65, DUPLEX_MODE_FDD

        verifyNotificationObservedWhenFieldChanged(
                DUPLEX_MODE, oldSS, newSS, false /*expectChange*/);
    }

    /**
     * Verifies that the data reg state is valid and matches ServiceState#getDataRegState()
     * This test case requires an active subscription.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DATA_REG_STATE"})
    public void testGetDataRegState_query() {
        if (!isSubscriptionActive(mSubId)) {
            return;
        }

        try (Cursor cursor = mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                new String[]{DATA_REG_STATE}, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();

            int dataRegState = cursor.getInt(cursor.getColumnIndex(DATA_REG_STATE));
            assertThat(dataRegState).isEqualTo(
                    mTelephonyManager.getServiceState().getDataRegState());
        }
    }

    /**
     * Verifies that when data reg state did not change, the observer should not receive the
     * notification.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DATA_REG_STATE"})
    public void testGetDataRegState_noChangeObserved() throws Exception {
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setState(ServiceState.STATE_OUT_OF_SERVICE);

        ServiceState copyOfOldSS = new ServiceState();
        copyOfOldSS.setStateOutOfService();
        copyOfOldSS.setState(ServiceState.STATE_OUT_OF_SERVICE);
        // set additional fields which is not related to data reg state
        copyOfOldSS.setChannelNumber(65536);
        copyOfOldSS.setIsManualSelection(true);

        verifyNotificationObservedWhenFieldChanged(
                DATA_REG_STATE, oldSS, copyOfOldSS, false /*expectChange*/);
    }

    /**
     * Verifies that when data reg state changed, the observer should receive the notification.
     */
    @Test
    @ApiTest(apis = {"android.provider.Telephony.ServiceStateTable#DATA_REG_STATE"})
    public void testGetDataRegState_changeObserved() throws Exception {
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();
        newSS.setStateOff();

        verifyNotificationObservedWhenFieldChanged(
                DATA_REG_STATE, oldSS, newSS, true /*expectChange*/);
    }

    /**
     * Verifies that when query location protected fields without permissions, those fields are not
     * available to apps with targetSdkVersion S+.
     */
    @Test
    public void testLocationProtectedFields_noPermission() {
        try (Cursor cursor = mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                null, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();

            assertThat(cursor.getColumnIndex("network_id")).isEqualTo(-1);
            assertThat(cursor.getColumnIndex("system_id")).isEqualTo(-1);

            assertThrows(IllegalArgumentException.class,
                    () -> mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                            new String[]{"network_id"}, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> mContentResolver.query(Telephony.ServiceStateTable.CONTENT_URI,
                            new String[]{"system_id"}, null, null));
        }
    }

    /**
     * Insert new ServiceState over the old ServiceState and expect the observer receiving the
     * notification over the observed field change.
     */
    private void verifyNotificationObservedWhenFieldChanged(String field, ServiceState oldSS,
            ServiceState newSS, boolean expectChange) throws Exception {
        final Uri uriForSubAndField =
                Telephony.ServiceStateTable.getUriForSubscriptionIdAndField(mSubId, field);
        insertServiceState(oldSS);

        RecordingContentObserver observer = new RecordingContentObserver();
        mContentResolver.registerContentObserver(uriForSubAndField, false, observer);
        assertWithMessage("Observer is NOT empty in the beginning.").that(
                observer.mObserved).isEmpty();

        insertServiceState(newSS);

        if (expectChange) {
            // Only verify we did receive the notification for the expected field, instead of the
            // number of notifications we received to remove flakiness for different cases.
            PollingCheck.check(
                    "Expect notification when " + field + " updated.",
                    DEFAULT_TIMEOUT, () -> observer.mObserved.contains(uriForSubAndField));
        } else {
            // Let the bullets fly for a while before we check the target.
            try {
                Thread.sleep(DEFAULT_TIMEOUT);
            } catch (InterruptedException ignored) {
            }

            // Fields in ServiceState are not orthogonal. In case we do receive notification(s),
            // further check if it is for the expected field.
            assertWithMessage("Unexpected notification for " + field).that(
                    observer.mObserved).doesNotContain(uriForSubAndField);

        }

        mContentResolver.unregisterContentObserver(observer);
    }

    // Manually insert the ServiceState into table to test the notification.
    private void insertServiceState(ServiceState state) {
        ContentValues values = getContentValuesForServiceState(state);
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContentResolver.insert(
                        Telephony.ServiceStateTable.getUriForSubscriptionId(mSubId), values),
                Manifest.permission.MODIFY_PHONE_STATE);
    }

    private boolean isSubscriptionActive(int subId) {
        SubscriptionManager sm = getInstrumentation().getContext().getSystemService(
                SubscriptionManager.class);
        return sm != null && sm.isActiveSubscriptionId(subId);
    }

    // Copied from ServiceStateProvider#getContentValuesForServiceState
    private static ContentValues getContentValuesForServiceState(ServiceState state) {
        ContentValues values = new ContentValues();
        final Parcel p = Parcel.obtain();
        state.writeToParcel(p, 0);
        values.put(SERVICE_STATE, p.marshall());
        return values;
    }

    private static class RecordingContentObserver extends ContentObserver {
        List<Uri> mObserved = new CopyOnWriteArrayList<>();

        RecordingContentObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange, @Nullable Uri uri) {
            mObserved.add(uri);
        }
    }

    private static boolean hasTelephonyFeature() {
        return getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                FEATURE_TELEPHONY);
    }

    // org.junit.Assume.assertThrows is not available until JUnit 4.13
    private static void assertThrows(Class<? extends Exception> exceptionClass, Runnable r) {
        try {
            r.run();
            Assert.fail("Expected " + exceptionClass + " to be thrown.");
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(exceptionClass);
        }
    }
}
