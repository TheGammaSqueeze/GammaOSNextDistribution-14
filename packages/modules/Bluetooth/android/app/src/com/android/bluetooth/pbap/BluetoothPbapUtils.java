/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.pbap;

import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContactsEntity;
import android.util.Log;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

// Next tag value for ContentProfileErrorReportUtils.report(): 4
class BluetoothPbapUtils {
    private static final String TAG = "BluetoothPbapUtils";
    private static final boolean V = BluetoothPbapService.VERBOSE;

    // Filter constants from Bluetooth PBAP specification
    private static final int FILTER_PHOTO = 3;
    private static final int FILTER_BDAY = 4;
    private static final int FILTER_ADDRESS = 5;
    private static final int FILTER_LABEL = 6;
    private static final int FILTER_EMAIL = 8;
    private static final int FILTER_MAILER = 9;
    private static final int FILTER_ORG = 16;
    private static final int FILTER_NOTE = 17;
    private static final int FILTER_SOUND = 19;
    private static final int FILTER_URL = 20;
    private static final int FILTER_NICKNAME = 23;

    private static final long QUERY_CONTACT_RETRY_INTERVAL = 4000;

    static AtomicLong sDbIdentifier = new AtomicLong();

    static long sPrimaryVersionCounter = 0;
    static long sSecondaryVersionCounter = 0;
    @VisibleForTesting
    static long sTotalContacts = 0;

    /* totalFields and totalSvcFields used to update primary/secondary version
     * counter between pbap sessions*/
    @VisibleForTesting
    static long sTotalFields = 0;
    @VisibleForTesting
    static long sTotalSvcFields = 0;
    @VisibleForTesting
    static long sContactsLastUpdated = 0;

    private static class ContactData {
        private String mName;
        private ArrayList<String> mEmail;
        private ArrayList<String> mPhone;
        private ArrayList<String> mAddress;

        ContactData() {
            mPhone = new ArrayList<>();
            mEmail = new ArrayList<>();
            mAddress = new ArrayList<>();
        }

        ContactData(String name, ArrayList<String> phone, ArrayList<String> email,
                ArrayList<String> address) {
            this.mName = name;
            this.mPhone = phone;
            this.mEmail = email;
            this.mAddress = address;
        }
    }

    @VisibleForTesting
    static HashMap<String, ContactData> sContactDataset = new HashMap<>();

    @VisibleForTesting
    static HashSet<String> sContactSet = new HashSet<>();

    @VisibleForTesting
    static final String TYPE_NAME = "name";
    @VisibleForTesting
    static final String TYPE_PHONE = "phone";
    @VisibleForTesting
    static final String TYPE_EMAIL = "email";
    @VisibleForTesting
    static final String TYPE_ADDRESS = "address";

    private static boolean hasFilter(byte[] filter) {
        return filter != null && filter.length > 0;
    }

    private static boolean isFilterBitSet(byte[] filter, int filterBit) {
        if (hasFilter(filter)) {
            int byteNumber = 7 - filterBit / 8;
            int bitNumber = filterBit % 8;
            if (byteNumber < filter.length) {
                return (filter[byteNumber] & (1 << bitNumber)) > 0;
            }
        }
        return false;
    }

    static VCardComposer createFilteredVCardComposer(final Context ctx, final int vcardType,
            final byte[] filter) {
        int vType = vcardType;
        boolean includePhoto =
                BluetoothPbapConfig.includePhotosInVcard() && (!hasFilter(filter) || isFilterBitSet(
                        filter, FILTER_PHOTO));
        if (!includePhoto) {
            if (V) {
                Log.v(TAG, "Excluding images from VCardComposer...");
            }
            vType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        if (hasFilter(filter)) {
            if (!isFilterBitSet(filter, FILTER_ADDRESS) && !isFilterBitSet(filter, FILTER_LABEL)) {
                Log.i(TAG, "Excluding addresses from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_ADDRESS_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_EMAIL) && !isFilterBitSet(filter, FILTER_MAILER)) {
                Log.i(TAG, "Excluding email addresses from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_EMAIL_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_ORG)) {
                Log.i(TAG, "Excluding organization from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_ORGANIZATION_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_URL)) {
                Log.i(TAG, "Excluding URLS from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_WEBSITES_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_NOTE)) {
                Log.i(TAG, "Excluding notes from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_NOTES_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_NICKNAME)) {
                Log.i(TAG, "Excluding nickname from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_NICKNAME_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_SOUND)) {
                Log.i(TAG, "Excluding phonetic name from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_PHONETIC_NAME_EXPORT;
            }
            if (!isFilterBitSet(filter, FILTER_BDAY)) {
                Log.i(TAG, "Excluding birthday from VCardComposer...");
                vType |= VCardConfig.FLAG_REFRAIN_EVENTS_EXPORT;
            }
        }
        return new VCardComposer(ctx, vType, true);
    }

    public static synchronized String getProfileName(Context context) {
        try (Cursor c =
                BluetoothMethodProxy.getInstance()
                        .contentResolverQuery(
                                context.getContentResolver(),
                                Profile.CONTENT_URI,
                                new String[] {Profile.DISPLAY_NAME},
                                null,
                                null,
                                null)) {
            String ownerName = null;
            if (c != null && c.moveToFirst()) {
                ownerName = c.getString(0);
            }
            return ownerName;
        }
    }

    static String createProfileVCard(Context ctx, final int vcardType, final byte[] filter) {
        VCardComposer composer = null;
        String vcard = null;
        try {
            composer = createFilteredVCardComposer(ctx, vcardType, filter);
            if (composer.init(Profile.CONTENT_URI, null, null, null, null,
                    Uri.withAppendedPath(Profile.CONTENT_URI,
                            RawContactsEntity.CONTENT_URI.getLastPathSegment()))) {
                vcard = composer.createOneEntry();
            } else {
                Log.e(
                        TAG,
                        "Unable to create profile vcard. Error initializing composer: "
                                + composer.getErrorReason());
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.PBAP,
                        BluetoothProtoEnums.BLUETOOTH_PBAP_UTILS,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                        0);
            }
        } catch (Throwable t) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_UTILS,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    1);
            Log.e(TAG, "Unable to create profile vcard.", t);
        }
        if (composer != null) {
            composer.terminate();
        }
        return vcard;
    }

    static void savePbapParams(Context ctx) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        long dbIdentifier = sDbIdentifier.get();
        Editor edit = pref.edit();
        edit.putLong("primary", sPrimaryVersionCounter);
        edit.putLong("secondary", sSecondaryVersionCounter);
        edit.putLong("dbIdentifier", dbIdentifier);
        edit.putLong("totalContacts", sTotalContacts);
        edit.putLong("lastUpdatedTimestamp", sContactsLastUpdated);
        edit.putLong("totalFields", sTotalFields);
        edit.putLong("totalSvcFields", sTotalSvcFields);
        edit.apply();

        if (V) {
            Log.v(TAG, "Saved Primary:" + sPrimaryVersionCounter + ", Secondary:"
                    + sSecondaryVersionCounter + ", Database Identifier: " + dbIdentifier);
        }
    }

    /* fetchPbapParams() loads preserved value of Database Identifiers and folder
     * version counters. Servers using a database identifier 0 or regenerating
     * one at each connection will not benefit from the resulting performance and
     * user experience improvements. So database identifier is set with current
     * timestamp and updated on rollover of folder version counter.*/
    static void fetchPbapParams(Context ctx) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        long timeStamp = Calendar.getInstance().getTimeInMillis();
        BluetoothPbapUtils.sDbIdentifier.set(pref.getLong("DbIdentifier", timeStamp));
        BluetoothPbapUtils.sPrimaryVersionCounter = pref.getLong("primary", 0);
        BluetoothPbapUtils.sSecondaryVersionCounter = pref.getLong("secondary", 0);
        BluetoothPbapUtils.sTotalFields = pref.getLong("totalContacts", 0);
        BluetoothPbapUtils.sContactsLastUpdated = pref.getLong("lastUpdatedTimestamp", timeStamp);
        BluetoothPbapUtils.sTotalFields = pref.getLong("totalFields", 0);
        BluetoothPbapUtils.sTotalSvcFields = pref.getLong("totalSvcFields", 0);
        if (V) {
            Log.v(TAG, " fetchPbapParams " + pref.getAll());
        }
    }

    static void loadAllContacts(Context context, Handler handler) {
        if (V) {
            Log.v(TAG, "Loading Contacts ...");
        }

        String[] projection = {Data.CONTACT_ID, Data.DATA1, Data.MIMETYPE};
        sTotalContacts = fetchAndSetContacts(context, handler, projection, null, null, true);
        if (sTotalContacts < 0) {
            sTotalContacts = 0;
            return;
        }
        handler.sendMessage(handler.obtainMessage(BluetoothPbapService.CONTACTS_LOADED));
    }

    static synchronized void updateSecondaryVersionCounter(Context context, Handler handler) {
            /* updatedList stores list of contacts which are added/updated after
             * the time when contacts were last updated. (contactsLastUpdated
             * indicates the time when contact/contacts were last updated and
             * corresponding changes were reflected in Folder Version Counters).*/
        ArrayList<String> updatedList = new ArrayList<>();
        HashSet<String> currentContactSet = new HashSet<>();

        String[] projection = {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP};
        int currentContactCount = 0;
        try (Cursor c =
                BluetoothMethodProxy.getInstance()
                        .contentResolverQuery(
                                context.getContentResolver(),
                                Contacts.CONTENT_URI,
                                projection,
                                null,
                                null,
                                null)) {

            if (c == null) {
                Log.d(TAG, "Failed to fetch data from contact database");
                return;
            }
            while (c.moveToNext()) {
                String contactId = c.getString(0);
                long lastUpdatedTime = c.getLong(1);
                if (lastUpdatedTime > sContactsLastUpdated) {
                    updatedList.add(contactId);
                }
                currentContactSet.add(contactId);
            }
            currentContactCount = c.getCount();
        }

        if (V) {
            Log.v(TAG, "updated list =" + updatedList);
        }
        String[] dataProjection = {Data.CONTACT_ID, Data.DATA1, Data.MIMETYPE};

        String whereClause = Data.CONTACT_ID + "=?";

            /* code to check if new contact/contacts are added */
        if (currentContactCount > sTotalContacts) {
            for (String contact : updatedList) {
                String[] selectionArgs = {contact};
                fetchAndSetContacts(context, handler, dataProjection, whereClause, selectionArgs,
                        false);
                sSecondaryVersionCounter++;
                sPrimaryVersionCounter++;
                sTotalContacts = currentContactCount;
            }
                /* When contact/contacts are deleted */
        } else if (currentContactCount < sTotalContacts) {
            sTotalContacts = currentContactCount;
            ArrayList<String> svcFields = new ArrayList<>(
                    Arrays.asList(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE,
                            Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE));
            HashSet<String> deletedContacts = new HashSet<>(sContactSet);
            deletedContacts.removeAll(currentContactSet);
            sPrimaryVersionCounter += deletedContacts.size();
            sSecondaryVersionCounter += deletedContacts.size();
            if (V) {
                Log.v(TAG, "Deleted Contacts : " + deletedContacts);
            }

            // to decrement totalFields and totalSvcFields count
            for (String deletedContact : deletedContacts) {
                sContactSet.remove(deletedContact);
                String[] selectionArgs = {deletedContact};
                try (Cursor dataCursor =
                        BluetoothMethodProxy.getInstance()
                                .contentResolverQuery(
                                        context.getContentResolver(),
                                        Data.CONTENT_URI,
                                        dataProjection,
                                        whereClause,
                                        selectionArgs,
                                        null)) {

                    if (dataCursor == null) {
                        Log.d(TAG, "Failed to fetch data from contact database");
                        return;
                    }

                    while (dataCursor.moveToNext()) {
                        if (svcFields.contains(
                                dataCursor.getString(dataCursor.getColumnIndex(Data.MIMETYPE)))) {
                            sTotalSvcFields--;
                        }
                        sTotalFields--;
                    }
                }
            }

                /* When contacts are updated. i.e. Fields of existing contacts are
                 * added/updated/deleted */
        } else {
            for (String contact : updatedList) {
                sPrimaryVersionCounter++;
                ArrayList<String> phoneTmp = new ArrayList<>();
                ArrayList<String> emailTmp = new ArrayList<>();
                ArrayList<String> addressTmp = new ArrayList<>();
                String nameTmp = null;
                boolean updated = false;

                String[] selectionArgs = {contact};
                try (Cursor dataCursor =
                        BluetoothMethodProxy.getInstance()
                                .contentResolverQuery(
                                        context.getContentResolver(),
                                        Data.CONTENT_URI,
                                        dataProjection,
                                        whereClause,
                                        selectionArgs,
                                        null)) {

                    if (dataCursor == null) {
                        Log.d(TAG, "Failed to fetch data from contact database");
                        return;
                    }
                    // fetch all updated contacts and compare with cached copy of contacts
                    int indexData = dataCursor.getColumnIndex(Data.DATA1);
                    int indexMimeType = dataCursor.getColumnIndex(Data.MIMETYPE);
                    String data;
                    String mimeType;
                    while (dataCursor.moveToNext()) {
                        data = dataCursor.getString(indexData);
                        mimeType = dataCursor.getString(indexMimeType);
                        switch (mimeType) {
                            case Email.CONTENT_ITEM_TYPE:
                                emailTmp.add(data);
                                break;
                            case Phone.CONTENT_ITEM_TYPE:
                                phoneTmp.add(data);
                                break;
                            case StructuredPostal.CONTENT_ITEM_TYPE:
                                addressTmp.add(data);
                                break;
                            case StructuredName.CONTENT_ITEM_TYPE:
                                nameTmp = data;
                                break;
                        }
                    }
                }
                ContactData cData = new ContactData(nameTmp, phoneTmp, emailTmp, addressTmp);

                ContactData currentContactData = sContactDataset.get(contact);
                if (currentContactData == null) {
                    Log.e(TAG, "Null contact in the updateList: " + contact);
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_UTILS,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                            2);
                    continue;
                }

                if (!Objects.equals(nameTmp, currentContactData.mName)) {
                    updated = true;
                } else if (checkFieldUpdates(currentContactData.mPhone, phoneTmp)) {
                    updated = true;
                } else if (checkFieldUpdates(currentContactData.mEmail, emailTmp)) {
                    updated = true;
                } else if (checkFieldUpdates(currentContactData.mAddress, addressTmp)) {
                    updated = true;
                }

                if (updated) {
                    sSecondaryVersionCounter++;
                    sContactDataset.put(contact, cData);
                }
            }
        }

        Log.d(TAG,
                "primaryVersionCounter = " + sPrimaryVersionCounter + ", secondaryVersionCounter="
                        + sSecondaryVersionCounter);

        // check if Primary/Secondary version Counter has rolled over
        if (sSecondaryVersionCounter < 0 || sPrimaryVersionCounter < 0) {
            handler.sendMessage(handler.obtainMessage(BluetoothPbapService.ROLLOVER_COUNTERS));
        }
    }

    /* checkFieldUpdates checks update contact fields of a particular contact.
     * Field update can be a field updated/added/deleted in an existing contact.
     * Returns true if any contact field is updated else return false. */
    @VisibleForTesting
    static boolean checkFieldUpdates(ArrayList<String> oldFields,
            ArrayList<String> newFields) {
        if (newFields != null && oldFields != null) {
            if (newFields.size() != oldFields.size()) {
                sTotalSvcFields += Math.abs(newFields.size() - oldFields.size());
                sTotalFields += Math.abs(newFields.size() - oldFields.size());
                return true;
            }
            for (String newField : newFields) {
                if (!oldFields.contains(newField)) {
                    return true;
                }
            }
            /* when all fields of type(phone/email/address) are deleted in a given contact*/
        } else if (newFields == null && oldFields != null && oldFields.size() > 0) {
            sTotalSvcFields += oldFields.size();
            sTotalFields += oldFields.size();
            return true;

            /* when new fields are added for a type(phone/email/address) in a contact
             * for which there were no fields of this type earliar.*/
        } else if (oldFields == null && newFields != null && newFields.size() > 0) {
            sTotalSvcFields += newFields.size();
            sTotalFields += newFields.size();
            return true;
        }
        return false;
    }

    /* fetchAndSetContacts reads contacts and caches them
     * isLoad = true indicates its loading all contacts
     * isLoad = false indiacates its caching recently added contact in database*/
    @VisibleForTesting
    static synchronized int fetchAndSetContacts(
            Context context,
            Handler handler,
            String[] projection,
            String whereClause,
            String[] selectionArgs,
            boolean isLoad) {
        long currentTotalFields = 0, currentSvcFieldCount = 0;
        try (Cursor c =
                BluetoothMethodProxy.getInstance()
                        .contentResolverQuery(
                                context.getContentResolver(),
                                Data.CONTENT_URI,
                                projection,
                                whereClause,
                                selectionArgs,
                                null)) {

            /* send delayed message to loadContact when ContentResolver is unable
             * to fetch data from contact database using the specified URI at that
             * moment (Case: immediate Pbap connect on system boot with BT ON)*/
            if (c == null) {
                Log.d(TAG, "Failed to fetch contacts data from database..");
                if (isLoad) {
                    handler.sendMessageDelayed(
                            handler.obtainMessage(BluetoothPbapService.LOAD_CONTACTS),
                            QUERY_CONTACT_RETRY_INTERVAL);
                }
                return -1;
            }

            int indexCId = c.getColumnIndex(Data.CONTACT_ID);
            int indexData = c.getColumnIndex(Data.DATA1);
            int indexMimeType = c.getColumnIndex(Data.MIMETYPE);
            String contactId, data, mimeType;

            while (c.moveToNext()) {
                if (c.isNull(indexCId)) {
                    Log.w(TAG, "_id column is null. Row was deleted during iteration, skipping");
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_UTILS,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                            3);
                    continue;
                }
                contactId = c.getString(indexCId);
                data = c.getString(indexData);
                mimeType = c.getString(indexMimeType);
                /* fetch phone/email/address/name information of the contact */
                switch (mimeType) {
                    case Phone.CONTENT_ITEM_TYPE:
                        setContactFields(TYPE_PHONE, contactId, data);
                        currentSvcFieldCount++;
                        break;
                    case Email.CONTENT_ITEM_TYPE:
                        setContactFields(TYPE_EMAIL, contactId, data);
                        currentSvcFieldCount++;
                        break;
                    case StructuredPostal.CONTENT_ITEM_TYPE:
                        setContactFields(TYPE_ADDRESS, contactId, data);
                        currentSvcFieldCount++;
                        break;
                    case StructuredName.CONTENT_ITEM_TYPE:
                        setContactFields(TYPE_NAME, contactId, data);
                        currentSvcFieldCount++;
                        break;
                }
                sContactSet.add(contactId);
                currentTotalFields++;
            }
        }

        /* This code checks if there is any update in contacts after last pbap
         * disconnect has happenned (even if BT is turned OFF during this time)*/
        if (isLoad && currentTotalFields != sTotalFields) {
            sPrimaryVersionCounter += Math.abs(sTotalContacts - sContactSet.size());

            if (currentSvcFieldCount != sTotalSvcFields) {
                if (sTotalContacts != sContactSet.size()) {
                    sSecondaryVersionCounter += Math.abs(sTotalContacts - sContactSet.size());
                } else {
                    sSecondaryVersionCounter++;
                }
            }
            if (sPrimaryVersionCounter < 0 || sSecondaryVersionCounter < 0) {
                rolloverCounters();
            }

            sTotalFields = currentTotalFields;
            sTotalSvcFields = currentSvcFieldCount;
            sContactsLastUpdated = System.currentTimeMillis();
            Log.d(TAG, "Contacts updated between last BT OFF and current"
                    + "Pbap Connect, primaryVersionCounter=" + sPrimaryVersionCounter
                    + ", secondaryVersionCounter=" + sSecondaryVersionCounter);
        } else if (!isLoad) {
            sTotalFields++;
            sTotalSvcFields++;
        }
        return sContactSet.size();
    }

    /* setContactFields() is used to store contacts data in local cache (phone,
     * email or address which is required for updating Secondary Version counter).
     * contactsFieldData - List of field data for phone/email/address.
     * contactId - Contact ID, data1 - field value from data table for phone/email/address*/
    @VisibleForTesting
    static void setContactFields(String fieldType, String contactId, String data) {
        ContactData cData;
        if (sContactDataset.containsKey(contactId)) {
            cData = sContactDataset.get(contactId);
        } else {
            cData = new ContactData();
        }

        switch (fieldType) {
            case TYPE_NAME:
                cData.mName = data;
                break;
            case TYPE_PHONE:
                cData.mPhone.add(data);
                break;
            case TYPE_EMAIL:
                cData.mEmail.add(data);
                break;
            case TYPE_ADDRESS:
                cData.mAddress.add(data);
                break;
        }
        sContactDataset.put(contactId, cData);
    }

    /* As per Pbap 1.2 specification, Database Identifies shall be
     * re-generated when a Folder Version Counter rolls over or starts over.*/

    static void rolloverCounters() {
        sDbIdentifier.set(Calendar.getInstance().getTimeInMillis());
        sPrimaryVersionCounter = (sPrimaryVersionCounter < 0) ? 0 : sPrimaryVersionCounter;
        sSecondaryVersionCounter = (sSecondaryVersionCounter < 0) ? 0 : sSecondaryVersionCounter;
        if (V) {
            Log.v(TAG, "DbIdentifier rolled over to:" + sDbIdentifier);
        }
    }
}
