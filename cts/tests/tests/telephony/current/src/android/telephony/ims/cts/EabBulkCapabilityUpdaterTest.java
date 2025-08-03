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

package android.telephony.ims.cts;

import static android.provider.ContactsContract.CommonDataKinds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.PersistableBundle;
import android.provider.ContactsContract;
import android.telecom.PhoneAccount;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class EabBulkCapabilityUpdaterTest {
    private static final String TAG = "EabBulkCapabilityUpdaterTest";

    private static Context mContext;
    private static ContentResolver mContentResolver;
    private static int sTestSlot = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static String sTestPhoneNumber;
    private static String sUpdatePhoneNumber;
    private static String sFormattedPhoneNumber;
    private static Uri sTestNumberUri;
    private static boolean mPrevEabSettings = false;
    private static boolean sDeviceUceEnabled;

    private static final int POLLING_RETRY_TIMES = 20;
    private static final int WAITING_IN_MILLI_SEC = 1000;
    private static final String TEST_CONTACT_NAME = "android_cts_eab_test_contact";

    private static final String COMMAND_BASE = "cmd phone ";
    private static final String COMMAND_SLOT_IDENTIFIER = "-s ";
    private static final String COMMAND_GET_EAB_CONTACT = "uce get-eab-contact ";
    private static final String COMMAND_REMOVE_EAB_CONTACT = "uce remove-eab-contact ";
    private static final String TEST_SERVICE_DESCRIPTION = "description_test1";

    private CountDownLatch mWaitNotifyResult;

    private static class CarrierConfigReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForCarrierConfigChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();

        sTestPhoneNumber = generateRandomPhoneNumber();
        sFormattedPhoneNumber = formatNumber(mContext, sTestPhoneNumber);
        sTestNumberUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, sTestPhoneNumber, null);

        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);

        sReceiver = new CarrierConfigReceiver(sTestSub);

        mPrevEabSettings = isUserEnableUce();

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        sServiceConnector.clearAllActiveImsServices(sTestSlot);
        sDeviceUceEnabled = sServiceConnector.getDeviceUceEnabled();
        sServiceConnector.setDeviceUceEnabled(true);

        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        updateEabCarrierConfig(true);
        updateEabUserSettings(true);

        connectTestImsService();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
            sServiceConnector.disconnectServices();
            sServiceConnector.setDeviceUceEnabled(sDeviceUceEnabled);
        }
        sServiceConnector = null;

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }


        updateEabUserSettings(mPrevEabSettings);
        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext()
                    .unregisterReceiver(sReceiver);
        }
    }

    @Before
    public void setup() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!SubscriptionManager.isValidSubscriptionId(sTestSub)) {
            fail("This test requires that there is a SIM in the device!");
        }

        mWaitNotifyResult = new CountDownLatch(1);
        fakeNetworkResult(getPidfXmlData());
    }

    @After
    public void teardown() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        cleanupTestContactFromContactProvider();
        cleanupTestContactInEabProvider();
    }

    @Test
    public void insertNewContactTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Insert contact to contact provider
        ContentProviderResult[] insertResult = insertContactToContactProvider();
        String contactId = String.valueOf(ContentUris.parseId(insertResult[0].uri));
        String dataId = String.valueOf(ContentUris.parseId(insertResult[2].uri));
        // Waiting eab provider insert
        waitingEabProviderInsertContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);

        // Query eab db
        String contactFromEab = getEabContact(sFormattedPhoneNumber);
        //[PHONE_NUMBER], [RAW_CONTACT_ID], [CONTACT_ID], [DATA_ID]
        String[] eabContactInfo = contactFromEab.split(",");
        assertTrue(eabContactInfo.length > 0);
        String eabContactPhoneNumber = eabContactInfo[0];
        String eabContactId = eabContactInfo[2];
        String eabDataId = eabContactInfo[3];

        // Verify the test contact has been inserted into EAB provider
        assertEquals(sFormattedPhoneNumber, eabContactPhoneNumber);
        assertEquals(contactId, eabContactId);
        assertEquals(dataId, eabDataId);
    }

    @Test
    public void deletePhoneNumberOfContactTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        insertContactToContactProvider();
        waitingEabProviderInsertContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);

        // Delete the phone number in contact provider
        deletePhoneNumberOfContact();
        waitingEabProviderDeleteContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);

        // Verify the phone number in EAB provider has been removed
        String contactFromEab = getEabContact(sTestPhoneNumber);
        assertTrue(TextUtils.isEmpty(contactFromEab));
    }

    @Test
    public void updateContactTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        insertContactToContactProvider();
        waitingEabProviderInsertContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);
        sUpdatePhoneNumber = sTestPhoneNumber + "1";

        // Update the phone number in contact provider
        updatePhoneNumber(sUpdatePhoneNumber);
        waitingEabProviderInsertContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);

        // Query eab db
        String contactFromEab = getEabContact(sFormattedPhoneNumber);
        //[PHONE_NUMBER], [RAW_CONTACT_ID], [CONTACT_ID], [DATA_ID]
        String[] eabContactInfo = contactFromEab.split(",");
        assertTrue(eabContactInfo.length > 0);
        String eabContactPhoneNumber = eabContactInfo[0];

        // Verify the test contact has been updated
        assertEquals(sFormattedPhoneNumber, eabContactPhoneNumber);
    }

    @Test
    public void deleteContactTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        insertContactToContactProvider();
        waitingEabProviderInsertContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);

        deleteTestContact();
        waitingEabProviderDeleteContact(sFormattedPhoneNumber, POLLING_RETRY_TIMES);

        // Verify the phone number in EAB provider has been removed
        String contactFromEab = getEabContact(sTestPhoneNumber);
        assertTrue(TextUtils.isEmpty(contactFromEab));
    }

    private void waitingEabProviderInsertContact(
            String phoneNumber, int retryTimes) throws Exception {
        mWaitNotifyResult.await(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        String contactFromEab;
        do {
            contactFromEab = getEabContact(phoneNumber);
            Thread.sleep(WAITING_IN_MILLI_SEC);
            retryTimes--;
        } while (retryTimes > 0 && TextUtils.isEmpty(contactFromEab));
        mWaitNotifyResult = new CountDownLatch(1);
    }

    private void waitingEabProviderDeleteContact(
            String phoneNumber, int retryTimes) throws Exception {
        String contactFromEab;
        do {
            contactFromEab = getEabContact(phoneNumber);
            Thread.sleep(WAITING_IN_MILLI_SEC);
            retryTimes--;
        } while (retryTimes > 0 && !TextUtils.isEmpty(contactFromEab));
    }

    private void updatePhoneNumber(String newNumber) {
        Cursor cursor = mContentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + "=?",
                new String[]{TEST_CONTACT_NAME},
                null);
        ContentValues updateContentValues = new ContentValues();
        updateContentValues.put(CommonDataKinds.Phone.DATA, newNumber);
        while (cursor.moveToNext()) {
            String contactId = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.Contacts.NAME_RAW_CONTACT_ID));
            mContentResolver.update(ContactsContract.Data.CONTENT_URI,
                    updateContentValues,
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND "
                            + ContactsContract.Data.MIMETYPE + "='"
                            + CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'",
                    new String[]{contactId});
        }
        cursor.close();
    }

    private String getEabContact(String phoneNum) throws Exception {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(COMMAND_BASE).append(COMMAND_GET_EAB_CONTACT)
                .append(" ").append(phoneNum);
        return TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());
    }

    private void deleteTestContact() {
        Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(
                ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        operationList.add(ContentProviderOperation.newDelete(uri)
                .withSelection(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY + "=?",
                        new String[]{TEST_CONTACT_NAME}).build());
        try {
            mContentResolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ContentProviderResult[] insertContactToContactProvider() {
        ContentProviderResult[] result = null;

        ArrayList<ContentProviderOperation> contactInfoList = new ArrayList<>();
        contactInfoList.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .build());

        // Add contact name
        contactInfoList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, TEST_CONTACT_NAME)
                .build());

        // Add phone number
        contactInfoList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Phone.NUMBER, sTestPhoneNumber)
                .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                .build());

        try {
            result = mContentResolver.applyBatch(ContactsContract.AUTHORITY, contactInfoList);
        } catch (Exception e) {
            fail("Insert contact failed");
        }
        return result;
    }

    private void deletePhoneNumberOfContact() {
        Cursor cursor = mContentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + "=?",
                new String[]{TEST_CONTACT_NAME},
                null);
        while (cursor.moveToNext()) {
            String contactId = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.Contacts.NAME_RAW_CONTACT_ID));
            mContentResolver.delete(ContactsContract.Data.CONTENT_URI,
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND "
                            + ContactsContract.Data.MIMETYPE + "='"
                            + CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'",
                    new String[]{contactId});
        }
        cursor.close();
    }

    private void cleanupTestContactFromContactProvider() {
        Cursor cursor = mContentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + "=?",
                new String[]{TEST_CONTACT_NAME},
                null);
        while (cursor.moveToNext()) {
            String contactId = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.Contacts.NAME_RAW_CONTACT_ID));
            mContentResolver.delete(ContactsContract.Data.CONTENT_URI,
                    ContactsContract.Data.RAW_CONTACT_ID + "=?",
                    new String[]{contactId});
        }
        cursor.close();
    }

    private void cleanupTestContactInEabProvider() throws Exception {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(COMMAND_BASE).append(COMMAND_REMOVE_EAB_CONTACT)
                .append(COMMAND_SLOT_IDENTIFIER).append(0).append(" ").append(sTestPhoneNumber);
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());
        waitingEabProviderDeleteContact(sTestPhoneNumber, POLLING_RETRY_TIMES);

        cmdBuilder = new StringBuilder();
        cmdBuilder.append(COMMAND_BASE).append(COMMAND_REMOVE_EAB_CONTACT)
                .append(COMMAND_SLOT_IDENTIFIER).append(0).append(" ")
                .append(sUpdatePhoneNumber);
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());
        waitingEabProviderDeleteContact(sUpdatePhoneNumber, POLLING_RETRY_TIMES);
    }

    private static void updateEabCarrierConfig(boolean enable) throws Exception {
        sReceiver.clearQueue();

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                true);
        bundle.putBoolean(CarrierConfigManager.Ims.KEY_RCS_BULK_CAPABILITY_EXCHANGE_BOOL, enable);
        overrideCarrierConfig(bundle);

        sReceiver.waitForCarrierConfigChanged();
    }

    private static void updateEabUserSettings(boolean enable) {
        ImsManager manager = mContext.getSystemService(ImsManager.class);
        try {
            ImsRcsManager rcsManager = manager.getImsRcsManager(sTestSub);
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    rcsManager.getUceAdapter(),
                    (adapter) -> adapter.setUceSettingEnabled(enable),
                    ImsException.class,
                    "android.permission.MODIFY_PHONE_STATE");
        } catch (Exception e) {
            Log.d(TAG, "fail on setting setUceSettingEnabled to true", e);
        }
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) {
        CarrierConfigManager carrierConfigManager = mContext.getSystemService(
                CarrierConfigManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
    }

    private static boolean getBooleanCarrierConfig(String key, int subId) {
        CarrierConfigManager mConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        PersistableBundle b = null;
        if (mConfigManager != null) {
            b = mConfigManager.getConfigForSubId(subId);
        }
        if (b != null) {
            return b.getBoolean(key);
        } else {
            return CarrierConfigManager.getDefaultConfig().getBoolean(key);
        }
    }

    private static boolean isUserEnableUce() {
        ImsManager manager = mContext.getSystemService(ImsManager.class);
        if (manager == null) {
            Log.e(TAG, "ImsManager is null");
            return false;
        }
        try {
            ImsRcsManager rcsManager = manager.getImsRcsManager(sTestSub);
            return (rcsManager != null) && rcsManager.getUceAdapter().isUceSettingEnabled();
        } catch (Exception e) {
            Log.e(TAG, "hasUserEnabledUce: exception = " + e.getMessage());
        }
        return false;
    }

    private static void connectTestImsService() throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_RCS)
                .build()));

        // The RcsFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createRcsFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_RCS));
        assertTrue("Did not receive RcsFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_RCS_READY));
        // Make sure the RcsFeature was created in the test service.
        assertNotNull("Device ImsService created, but TestDeviceImsService#createRcsFeature was not"
                + "called!", sServiceConnector.getCarrierService().getRcsFeature());
        assertTrue("Did not receive RcsFeature#setCapabilityExchangeEventListener",
                sServiceConnector.getCarrierService().waitForLatchCountdown(
                        TestImsService.LATCH_UCE_LISTENER_SET));
        int serviceSlot = sServiceConnector.getCarrierService().getRcsFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated RcsFeature",
                sTestSlot, serviceSlot);
    }

    private static String generateRandomPhoneNumber() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private void fakeNetworkResult(String pidfXml) {
        TestRcsCapabilityExchangeImpl capabilityExchangeImpl = sServiceConnector
                .getCarrierService().getRcsFeature().getRcsCapabilityExchangeImpl();

        ArrayList<String> pidfXmlList = new ArrayList<>(1);
        pidfXmlList.add(pidfXml);

        // Setup the network response is 200 OK and notify capabilities update
        int networkRespCode = 200;
        String networkRespReason = "OK";
        capabilityExchangeImpl.setSubscribeOperation((uris, cb) -> {
            cb.onNetworkResponse(networkRespCode, networkRespReason);
            cb.onNotifyCapabilitiesUpdate(pidfXmlList);
            cb.onTerminated("", 0L);
            mWaitNotifyResult.countDown();
        });
    }

    private String getPidfXmlData() {
        GregorianCalendar date = new GregorianCalendar();
        date.add(Calendar.DATE, 120);

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                .format(date.getTime());

        String pidfBuilder = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
                + "<presence entity=\"" + sTestNumberUri + "\""
                + " xmlns=\"urn:ietf:params:xml:ns:pidf\""
                + " xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\""
                + " xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">"
                + "<tuple id=\"tid0\">"
                // status
                + "<status><basic>"
                + RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN
                + "</basic></status>"
                // timestamp
                + "<timestamp>" + timeStamp + "</timestamp>"

                // service description
                + "<op:service-description>"
                + "<op:service-id>service_id_01</op:service-id>"
                + "<op:version>1.0</op:version>"
                + "<op:description>" + TEST_SERVICE_DESCRIPTION + "</op:description>"
                + "</op:service-description>"

                // service capabilities
                + "<caps:servcaps>"
                // audio capabilities
                + "<caps:audio>" + false + "</caps:audio>"
                // video capabilities
                + "<caps:video>" + true + "</caps:video>"

                // duplex mode
                + "<caps:duplex>"
                // support duplex mode
                + "<caps:supported>"
                + "<caps:" + RcsContactPresenceTuple.ServiceCapabilities.DUPLEX_MODE_RECEIVE_ONLY
                + "/>"
                + "</caps:supported>"

                // unsupported duplex mode
                + "<caps:notsupported>"
                + "<caps:" + RcsContactPresenceTuple.ServiceCapabilities.DUPLEX_MODE_SEND_ONLY
                + "/>"
                + "</caps:notsupported>"
                + "</caps:duplex>"
                + "</caps:servcaps>"
                + "<contact>" + sTestNumberUri + "</contact>"
                + "</tuple>"
                + "</presence>";
        return pidfBuilder;
    }

    private static String formatNumber(Context context, String number) {
        TelephonyManager manager = context.getSystemService(TelephonyManager.class);
        String simCountryIso = manager.getSimCountryIso();
        if (simCountryIso != null) {
            simCountryIso = simCountryIso.toUpperCase();
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            try {
                Phonenumber.PhoneNumber phoneNumber = util.parse(number, simCountryIso);
                return util.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            } catch (NumberParseException e) {
                Log.w(TAG, "formatNumber: could not format " + number + ", error: " + e);
            }
        }
        return number;
    }
}
