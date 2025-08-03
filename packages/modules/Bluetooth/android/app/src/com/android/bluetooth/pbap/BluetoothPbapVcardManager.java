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
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindowAllocationException;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContactsEntity;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.R;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.bluetooth.util.DevicePolicyUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;
import com.android.obex.ServerOperation;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardPhoneNumberTranslationCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

// Next tag value for ContentProfileErrorReportUtils.report(): 22
public class BluetoothPbapVcardManager {
    private static final String TAG = "BluetoothPbapVcardManager";

    private static final boolean V = BluetoothPbapService.VERBOSE;

    private ContentResolver mResolver;

    private Context mContext;

    private static final int PHONE_NUMBER_COLUMN_INDEX = 3;

    static final String SORT_ORDER_PHONE_NUMBER = CommonDataKinds.Phone.NUMBER + " ASC";

    static final String[] PHONES_CONTACTS_PROJECTION = new String[]{
            Phone.CONTACT_ID, // 0
            Phone.DISPLAY_NAME, // 1
    };

    static final String[] PHONE_LOOKUP_PROJECTION = new String[]{
            PhoneLookup._ID, PhoneLookup.DISPLAY_NAME
    };

    static final int CONTACTS_ID_COLUMN_INDEX = 0;

    static final int CONTACTS_NAME_COLUMN_INDEX = 1;

    static long sLastFetchedTimeStamp;

    // call histories use dynamic handles, and handles should order by date; the
    // most recently one should be the first handle. In table "calls", _id and
    // date are consistent in ordering, to implement simply, we sort by _id
    // here.
    static final String CALLLOG_SORT_ORDER = Calls._ID + " DESC";

    private static final int NEED_SEND_BODY = -1;

    public BluetoothPbapVcardManager(final Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
        sLastFetchedTimeStamp = System.currentTimeMillis();
    }

    /**
     * Create an owner vcard from the configured profile
     * @param vcardType21
     * @return
     */
    private String getOwnerPhoneNumberVcardFromProfile(final boolean vcardType21,
            final byte[] filter) {
        // Currently only support Generic Vcard 2.1 and 3.0
        int vcardType;
        if (vcardType21) {
            vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
        } else {
            vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
        }

        if (!BluetoothPbapConfig.includePhotosInVcard()) {
            vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }

        return BluetoothPbapUtils.createProfileVCard(mContext, vcardType, filter);
    }

    public final String getOwnerPhoneNumberVcard(final boolean vcardType21, final byte[] filter) {
        //Owner vCard enhancement: Use "ME" profile if configured
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            String vcard = getOwnerPhoneNumberVcardFromProfile(vcardType21, filter);
            if (vcard != null && vcard.length() != 0) {
                return vcard;
            }
        }
        //End enhancement

        String name = BluetoothPbapService.getLocalPhoneName();
        String number = BluetoothPbapService.getLocalPhoneNum();
        String vcard = BluetoothPbapCallLogComposer.composeVCardForPhoneOwnNumber(
                Phone.TYPE_MOBILE, name, number, vcardType21);
        return vcard;
    }

    public final int getPhonebookSize(final int type,
            BluetoothPbapSimVcardManager vCardSimManager) {
        int size;
        switch (type) {
            case BluetoothPbapObexServer.ContentType.PHONEBOOK:
            case BluetoothPbapObexServer.ContentType.FAVORITES:
                size = getContactsSize(type);
                break;
            case BluetoothPbapObexServer.ContentType.SIM_PHONEBOOK:
                size = vCardSimManager.getSIMContactsSize();
                break;
            default:
                size = getCallHistorySize(type);
                break;
        }
        if (V) {
            Log.v(TAG, "getPhonebookSize size = " + size + " type = " + type);
        }
        return size;
    }

    /**
     * Returns the number of contacts (i.e., vcf) in a phonebook object.
     * @param type specifies which phonebook object, e.g., pb, fav
     * @return
     */
    private int getContactsSize(final int type) {
        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        String selectionClause = null;
        if (type == BluetoothPbapObexServer.ContentType.FAVORITES) {
            selectionClause = Phone.STARRED + " = 1";
        }
        try {
            contactCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, new String[]{Phone.CONTACT_ID}, selectionClause,
                    null, Phone.CONTACT_ID);
            if (contactCursor == null) {
                return 0;
            }
            int contactsSize = getDistinctContactIdSize(contactCursor);
            if (type == BluetoothPbapObexServer.ContentType.PHONEBOOK) {
                contactsSize += 1; // pb has the 0.vcf owner's card
            }
            return contactsSize;
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    0);
            Log.e(TAG, "CursorWindowAllocationException while getting Contacts size");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }
        return 0;
    }

    private int getCallHistorySize(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        int size = 0;
        Cursor callCursor = null;
        try {
            callCursor =
                    BluetoothMethodProxy.getInstance()
                            .contentResolverQuery(
                                    mResolver,
                                    myUri,
                                    null,
                                    selection,
                                    null,
                                    CallLog.Calls.DEFAULT_SORT_ORDER);
            if (callCursor != null) {
                size = callCursor.getCount();
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    1);
            Log.e(TAG, "CursorWindowAllocationException while getting CallHistory size");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }
        return size;
    }

    @VisibleForTesting
    static final int CALLS_NUMBER_COLUMN_INDEX = 0;
    @VisibleForTesting
    static final int CALLS_NAME_COLUMN_INDEX = 1;
    @VisibleForTesting
    static final int CALLS_NUMBER_PRESENTATION_COLUMN_INDEX = 2;

    public final ArrayList<String> loadCallHistoryList(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        String[] projection = new String[]{
                Calls.NUMBER, Calls.CACHED_NAME, Calls.NUMBER_PRESENTATION
        };


        Cursor callCursor = null;
        ArrayList<String> list = new ArrayList<String>();
        try {
            callCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, projection, selection, null, CALLLOG_SORT_ORDER);
            if (callCursor != null) {
                for (callCursor.moveToFirst(); !callCursor.isAfterLast(); callCursor.moveToNext()) {
                    String name = callCursor.getString(CALLS_NAME_COLUMN_INDEX);
                    if (TextUtils.isEmpty(name)) {
                        // name not found, use number instead
                        final int numberPresentation =
                                callCursor.getInt(CALLS_NUMBER_PRESENTATION_COLUMN_INDEX);
                        if (numberPresentation != Calls.PRESENTATION_ALLOWED) {
                            name = mContext.getString(R.string.unknownNumber);
                        } else {
                            name = callCursor.getString(CALLS_NUMBER_COLUMN_INDEX);
                        }
                    }
                    list.add(name);
                }
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    2);
            Log.e(TAG, "CursorWindowAllocationException while loading CallHistory");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }
        return list;
    }

    public final ArrayList<String> getPhonebookNameList(final int orderByWhat) {
        ArrayList<String> nameList = new ArrayList<String>();
        //Owner vCard enhancement. Use "ME" profile if configured
        String ownerName = null;
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            ownerName = BluetoothPbapUtils.getProfileName(mContext);
        }
        if (ownerName == null || ownerName.length() == 0) {
            ownerName = BluetoothPbapService.getLocalPhoneName();
        }
        if (ownerName != null) {
            nameList.add(ownerName);
        }
        //End enhancement

        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        // By default order is indexed
        String orderBy = Phone.CONTACT_ID;
        try {
            if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                orderBy = Phone.DISPLAY_NAME;
            }
            contactCursor =
                    BluetoothMethodProxy.getInstance()
                            .contentResolverQuery(
                                    mResolver,
                                    myUri,
                                    PHONES_CONTACTS_PROJECTION,
                                    null,
                                    null,
                                    orderBy);
            if (contactCursor != null) {
                appendDistinctNameIdList(nameList, mContext.getString(android.R.string.unknownName),
                        contactCursor);
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    3);
            Log.e(TAG, "CursorWindowAllocationException while getting phonebook name list");
        } catch (Exception e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    4);
            Log.e(TAG, "Exception while getting phonebook name list", e);
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        return nameList;
    }

    final ArrayList<String> getSelectedPhonebookNameList(final int orderByWhat,
            final boolean vcardType21, int needSendBody, int pbSize, byte[] selector,
            String vCardSelectorOperator) {
        ArrayList<String> nameList = new ArrayList<String>();
        PropertySelector vcardselector = new PropertySelector(selector);
        VCardComposer composer = null;
        int vcardType;

        if (vcardType21) {
            vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
        } else {
            vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
        }

        composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, null);
        composer.setPhoneNumberTranslationCallback(new VCardPhoneNumberTranslationCallback() {

            @Override
            public String onValueReceived(String rawValue, int type, String label,
                    boolean isPrimary) {
                String numberWithControlSequence = rawValue.replace(PhoneNumberUtils.PAUSE, 'p')
                        .replace(PhoneNumberUtils.WAIT, 'w');
                return numberWithControlSequence;
            }
        });

        // Owner vCard enhancement. Use "ME" profile if configured
        String ownerName = null;
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            ownerName = BluetoothPbapUtils.getProfileName(mContext);
        }
        if (ownerName == null || ownerName.length() == 0) {
            ownerName = BluetoothPbapService.getLocalPhoneName();
        }
        nameList.add(ownerName);
        // End enhancement

        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        try {
            contactCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, PHONES_CONTACTS_PROJECTION, null, null,
                    Phone.CONTACT_ID);

            ArrayList<String> contactNameIdList = new ArrayList<String>();
            appendDistinctNameIdList(contactNameIdList,
                    mContext.getString(android.R.string.unknownName), contactCursor);

            if (contactCursor != null) {
                if (!composer.init(contactCursor)) {
                    return nameList;
                }
                int idColumn = contactCursor.getColumnIndex(Data.CONTACT_ID);
                if (idColumn < 0) {
                    idColumn = contactCursor.getColumnIndex(Contacts._ID);
                }

                int i = 0;
                contactCursor.moveToFirst();
                while (!contactCursor.isAfterLast()) {
                    String vcard = composer.buildVCard(RawContactsEntity.queryRawContactEntity(
                                mResolver, contactCursor.getLong(idColumn)));
                    if (!contactCursor.moveToNext()) {
                        Log.i(TAG, "Cursor#moveToNext() returned false");
                    }
                    if (vcard == null) {
                        Log.e(TAG, "Failed to read a contact.");
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                                5);
                        return nameList;
                    } else if (vcard.isEmpty()) {
                        Log.i(TAG, "Contact may have been deleted during operation");
                        continue;
                    }
                    if (V) {
                        Log.v(TAG, "Checking selected bits in the vcard composer" + vcard);
                    }

                    if (!vcardselector.checkVCardSelector(vcard, vCardSelectorOperator)) {
                        Log.e(TAG, "vcard selector check fail");
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                                6);
                        vcard = null;
                        pbSize--;
                        continue;
                    } else {
                        String name = getNameFromVCard(vcard);
                        if (TextUtils.isEmpty(name)) {
                            name = mContext.getString(android.R.string.unknownName);
                        }
                        nameList.add(contactNameIdList.get(i));
                    }
                    i++;
                }
                if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_INDEXED) {
                    if (V) {
                        Log.v(TAG, "getPhonebookNameList, order by index");
                    }
                    // Do not need to do anything, as we sort it by index already
                } else if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                    if (V) {
                        Log.v(TAG, "getPhonebookNameList, order by alpha");
                    }
                    Collections.sort(nameList);
                }
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    7);
            Log.e(TAG, "CursorWindowAllocationException while getting Phonebook name list");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        return nameList;
    }

    public final ArrayList<String> getContactNamesByNumber(final String phoneNumber) {
        ArrayList<String> nameList = new ArrayList<String>();

        Cursor contactCursor = null;
        Uri uri = null;
        String[] projection = null;

        if (TextUtils.isEmpty(phoneNumber)) {
            uri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
            projection = PHONES_CONTACTS_PROJECTION;
        } else {
            uri = Uri.withAppendedPath(getPhoneLookupFilterUri(), Uri.encode(phoneNumber));
            projection = PHONE_LOOKUP_PROJECTION;
        }

        try {
            contactCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    uri, projection, null, null, Phone.CONTACT_ID);

            if (contactCursor != null) {
                appendDistinctNameIdList(nameList, mContext.getString(android.R.string.unknownName),
                        contactCursor);
                if (V) {
                    for (String nameIdStr : nameList) {
                        Log.v(TAG, "got name " + nameIdStr + " by number " + phoneNumber);
                    }
                }
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    8);
            Log.e(TAG, "CursorWindowAllocationException while getting contact names");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }

        return nameList;
    }

    byte[] getCallHistoryPrimaryFolderVersion(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        selection = selection + " AND date >= " + sLastFetchedTimeStamp;

        Log.d(TAG, "LAST_FETCHED_TIME_STAMP is " + sLastFetchedTimeStamp);
        Cursor callCursor = null;
        long count = 0;
        long primaryVcMsb = 0;
        ArrayList<String> list = new ArrayList<String>();
        try {
            callCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, null, selection, null, null);
            while (callCursor != null && callCursor.moveToNext()) {
                count = count + 1;
            }
        } catch (Exception e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    9);
            Log.e(TAG, "exception while fetching callHistory pvc");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }

        sLastFetchedTimeStamp = System.currentTimeMillis();
        Log.d(TAG, "getCallHistoryPrimaryFolderVersion count is " + count + " type is " + type);
        ByteBuffer pvc = ByteBuffer.allocate(16);
        pvc.putLong(primaryVcMsb);
        Log.d(TAG, "primaryVersionCounter is " + BluetoothPbapUtils.sPrimaryVersionCounter);
        pvc.putLong(count);
        return pvc.array();
    }

    private static final String[] CALLLOG_PROJECTION = new String[]{
            CallLog.Calls._ID, // 0
    };
    private static final int ID_COLUMN_INDEX = 0;

    final int composeAndSendSelectedCallLogVcards(
            final int type,
            Operation op,
            final int startPoint,
            final int endPoint,
            final boolean vcardType21,
            int needSendBody,
            int pbSize,
            boolean ignorefilter,
            byte[] filter,
            byte[] vcardselector,
            String vcardselectorop,
            boolean vcardselect) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    10);
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        String typeSelection = BluetoothPbapObexServer.createSelectionPara(type);

        final Uri myUri = CallLog.Calls.CONTENT_URI;
        Cursor callsCursor = null;
        long startPointId = 0;
        long endPointId = 0;
        try {
            // Need test to see if order by _ID is ok here, or by date?
            callsCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, CALLLOG_PROJECTION, typeSelection, null,
                    CALLLOG_SORT_ORDER);
            if (callsCursor != null) {
                callsCursor.moveToPosition(startPoint - 1);
                startPointId = callsCursor.getLong(ID_COLUMN_INDEX);
                if (V) {
                    Log.v(TAG, "Call Log query startPointId = " + startPointId);
                }
                if (startPoint == endPoint) {
                    endPointId = startPointId;
                } else {
                    callsCursor.moveToPosition(endPoint - 1);
                    endPointId = callsCursor.getLong(ID_COLUMN_INDEX);
                }
                if (V) {
                    Log.v(TAG, "Call log query endPointId = " + endPointId);
                }
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    11);
            Log.e(TAG, "CursorWindowAllocationException while composing calllog vcards");
        } finally {
            if (callsCursor != null) {
                callsCursor.close();
                callsCursor = null;
            }
        }

        String recordSelection;
        if (startPoint == endPoint) {
            recordSelection = Calls._ID + "=" + startPointId;
        } else {
            // The query to call table is by "_id DESC" order, so change
            // correspondingly.
            recordSelection =
                    Calls._ID + ">=" + endPointId + " AND " + Calls._ID + "<=" + startPointId;
        }

        String selection;
        if (typeSelection == null) {
            selection = recordSelection;
        } else {
            selection = "(" + typeSelection + ") AND (" + recordSelection + ")";
        }

        if (V) {
            Log.v(TAG, "Call log query selection is: " + selection);
        }

        return composeCallLogsAndSendSelectedVCards(op, selection, vcardType21, needSendBody,
                pbSize, null, ignorefilter, filter, vcardselector, vcardselectorop, vcardselect);
    }

    final int composeAndSendPhonebookVcards(Operation op, final int startPoint, final int endPoint,
            final boolean vcardType21, String ownerVCard, int needSendBody, int pbSize,
            boolean ignorefilter, byte[] filter, byte[] vcardselector, String vcardselectorop,
            boolean vcardselect, boolean favorites) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    12);
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        Cursor contactIdCursor = new MatrixCursor(new String[]{
                Phone.CONTACT_ID
        });

        String selectionClause = null;
        if (favorites) {
            selectionClause = Phone.STARRED + " = 1";
        }

        try {
            contactCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, PHONES_CONTACTS_PROJECTION, selectionClause,
                    null, Phone.CONTACT_ID);
            if (contactCursor != null) {
                contactIdCursor =
                        ContactCursorFilter.filterByRange(contactCursor, startPoint, endPoint);
            }
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    13);
            Log.e(TAG, "CursorWindowAllocationException while composing phonebook vcards");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }

        if (vcardselect) {
            return composeContactsAndSendSelectedVCards(op, contactIdCursor, vcardType21,
                    ownerVCard, needSendBody, pbSize, ignorefilter, filter, vcardselector,
                    vcardselectorop);
        } else {
            return composeContactsAndSendVCards(op, contactIdCursor, vcardType21, ownerVCard,
                    ignorefilter, filter);
        }
    }

    final int composeAndSendPhonebookOneVcard(Operation op, final int offset,
            final boolean vcardType21, String ownerVCard, int orderByWhat, boolean ignorefilter,
            byte[] filter) {
        if (offset < 1) {
            Log.e(TAG, "Internal error: offset is not correct.");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    14);
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);

        Cursor contactCursor = null;
        Cursor contactIdCursor = new MatrixCursor(new String[]{
                Phone.CONTACT_ID
        });
        // By default order is indexed
        String orderBy = Phone.CONTACT_ID;
        try {
            if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                orderBy = Phone.DISPLAY_NAME;
            }
            contactCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(mResolver,
                    myUri, PHONES_CONTACTS_PROJECTION, null, null, orderBy);
        } catch (CursorWindowAllocationException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    15);
            Log.e(TAG, "CursorWindowAllocationException while composing phonebook one vcard");
        } finally {
            if (contactCursor != null) {
                contactIdCursor = ContactCursorFilter.filterByOffset(contactCursor, offset);
                contactCursor.close();
                contactCursor = null;
            }
        }
        return composeContactsAndSendVCards(op, contactIdCursor, vcardType21, ownerVCard,
                ignorefilter, filter);
    }

    /**
     * Filter contact cursor by certain condition.
     */
    static final class ContactCursorFilter {
        /**
         *
         * @param contactCursor
         * @param offset
         * @return a cursor containing contact id of {@code offset} contact.
         */
        static Cursor filterByOffset(Cursor contactCursor, int offset) {
            return filterByRange(contactCursor, offset, offset);
        }

        /**
         *
         * @param contactCursor
         * @param startPoint
         * @param endPoint
         * @return a cursor containing contact ids of {@code startPoint}th to {@code endPoint}th
         * contact. (i.e. [startPoint, endPoint], both points should be greater than 0)
         */
        static Cursor filterByRange(Cursor contactCursor, int startPoint, int endPoint) {
            final int contactIdColumn = contactCursor.getColumnIndex(Data.CONTACT_ID);
            long previousContactId = -1;
            // As startPoint, endOffset index starts from 1 to n, we set
            // currentPoint base as 1 not 0
            int currentOffset = 1;
            final MatrixCursor contactIdsCursor = new MatrixCursor(new String[]{
                    Phone.CONTACT_ID
            });
            while (contactCursor.moveToNext() && currentOffset <= endPoint) {
                long currentContactId = contactCursor.getLong(contactIdColumn);
                if (previousContactId != currentContactId) {
                    previousContactId = currentContactId;
                    if (currentOffset >= startPoint) {
                        contactIdsCursor.addRow(new Long[]{currentContactId});
                        if (V) {
                            Log.v(TAG, "contactIdsCursor.addRow: " + currentContactId);
                        }
                    }
                    currentOffset++;
                }
            }
            return contactIdsCursor;
        }
    }

    private int composeContactsAndSendVCards(Operation op, final Cursor contactIdCursor,
            final boolean vcardType21, String ownerVCard, boolean ignorefilter, byte[] filter) {
        long timestamp = 0;
        if (V) {
            timestamp = System.currentTimeMillis();
        }

        VCardComposer composer = null;
        VCardFilter vcardfilter = new VCardFilter(ignorefilter ? null : filter);

        HandlerForStringBuffer buffer = null;
        try {
            // Currently only support Generic Vcard 2.1 and 3.0
            int vcardType;
            if (vcardType21) {
                vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
                vcardType |= VCardConfig.FLAG_CONVERT_PHONETIC_NAME_STRINGS;
                vcardType |= VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES;
            } else {
                vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
            }
            if (!vcardfilter.isPhotoEnabled()) {
                vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
            }

            // Enhancement: customize Vcard based on preferences/settings and
            // input from caller
            composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, null);
            // End enhancement

            // BT does want PAUSE/WAIT conversion while it doesn't want the
            // other formatting
            // done by vCard library by default.
            composer.setPhoneNumberTranslationCallback(new VCardPhoneNumberTranslationCallback() {
                @Override
                public String onValueReceived(String rawValue, int type, String label,
                        boolean isPrimary) {
                    // 'p' and 'w' are the standard characters for pause and
                    // wait
                    // (see RFC 3601)
                    // so use those when exporting phone numbers via vCard.
                    String numberWithControlSequence = rawValue.replace(PhoneNumberUtils.PAUSE, 'p')
                            .replace(PhoneNumberUtils.WAIT, 'w');
                    return numberWithControlSequence;
                }
            });
            buffer = new HandlerForStringBuffer(op, ownerVCard);
            Log.v(TAG, "contactIdCursor size: " + contactIdCursor.getCount());
            if (!composer.init(contactIdCursor) || !buffer.init()) {
                return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
            }
            int idColumn = contactIdCursor.getColumnIndex(Data.CONTACT_ID);
            if (idColumn < 0) {
                idColumn = contactIdCursor.getColumnIndex(Contacts._ID);
            }

            while (!contactIdCursor.isAfterLast()) {
                if (BluetoothPbapObexServer.sIsAborted) {
                    ((ServerOperation) op).setAborted(true);
                    BluetoothPbapObexServer.sIsAborted = false;
                    break;
                }
                String vcard = composer.buildVCard(RawContactsEntity.queryRawContactEntity(
                            mResolver, contactIdCursor.getLong(idColumn)));
                if (!contactIdCursor.moveToNext()) {
                    Log.i(TAG, "Cursor#moveToNext() returned false");
                }
                if (vcard == null) {
                    Log.e(TAG, "Failed to read a contact.");
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                            16);
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                } else if (vcard.isEmpty()) {
                    Log.i(TAG, "Contact may have been deleted during operation");
                    continue;
                }
                if (V) {
                    Log.v(TAG, "vCard from composer: " + vcard);
                }

                vcard = vcardfilter.apply(vcard, vcardType21);
                vcard = stripTelephoneNumber(vcard);

                if (V) {
                    Log.v(TAG, "vCard after cleanup: " + vcard);
                }

                if (!buffer.writeVCard(vcard)) {
                    // onEntryCreate() already emits error.
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }
            }
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (buffer != null) {
                buffer.terminate();
            }
        }

        if (V) {
            Log.v(TAG, "Total vcard composing and sending out takes " + (System.currentTimeMillis()
                    - timestamp) + " ms");
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int composeContactsAndSendSelectedVCards(Operation op, final Cursor contactIdCursor,
            final boolean vcardType21, String ownerVCard, int needSendBody, int pbSize,
            boolean ignorefilter, byte[] filter, byte[] selector, String vcardselectorop) {
        long timestamp = 0;
        if (V) {
            timestamp = System.currentTimeMillis();
        }

        VCardComposer composer = null;
        VCardFilter vcardfilter = new VCardFilter(ignorefilter ? null : filter);
        PropertySelector vcardselector = new PropertySelector(selector);

        HandlerForStringBuffer buffer = null;

        try {
            // Currently only support Generic Vcard 2.1 and 3.0
            int vcardType;
            if (vcardType21) {
                vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
            } else {
                vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
            }
            if (!vcardfilter.isPhotoEnabled()) {
                vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
            }

            // Enhancement: customize Vcard based on preferences/settings and
            // input from caller
            composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, null);
            // End enhancement

            /* BT does want PAUSE/WAIT conversion while it doesn't want the
             * other formatting done by vCard library by default. */
            composer.setPhoneNumberTranslationCallback(new VCardPhoneNumberTranslationCallback() {
                @Override
                public String onValueReceived(String rawValue, int type, String label,
                        boolean isPrimary) {
                    /* 'p' and 'w' are the standard characters for pause and wait
                     * (see RFC 3601) so use those when exporting phone numbers via vCard.*/
                    String numberWithControlSequence = rawValue.replace(PhoneNumberUtils.PAUSE, 'p')
                            .replace(PhoneNumberUtils.WAIT, 'w');
                    return numberWithControlSequence;
                }
            });
            buffer = new HandlerForStringBuffer(op, ownerVCard);
            Log.v(TAG, "contactIdCursor size: " + contactIdCursor.getCount());
            if (!composer.init(contactIdCursor) || !buffer.init()) {
                return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
            }
            int idColumn = contactIdCursor.getColumnIndex(Data.CONTACT_ID);
            if (idColumn < 0) {
                idColumn = contactIdCursor.getColumnIndex(Contacts._ID);
            }

            while (!contactIdCursor.isAfterLast()) {
                if (BluetoothPbapObexServer.sIsAborted) {
                    ((ServerOperation) op).setAborted(true);
                    BluetoothPbapObexServer.sIsAborted = false;
                    break;
                }
                String vcard = composer.buildVCard(RawContactsEntity.queryRawContactEntity(
                            mResolver, contactIdCursor.getLong(idColumn)));
                if (!contactIdCursor.moveToNext()) {
                    Log.i(TAG, "Cursor#moveToNext() returned false");
                }
                if (vcard == null) {
                    Log.e(TAG, "Failed to read a contact.");
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                            17);
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                } else if (vcard.isEmpty()) {
                    Log.i(TAG, "Contact may have been deleted during operation");
                    continue;
                }
                if (V) {
                    Log.v(TAG, "Checking selected bits in the vcard composer" + vcard);
                }

                if (!vcardselector.checkVCardSelector(vcard, vcardselectorop)) {
                    Log.e(TAG, "vcard selector check fail");
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                            18);
                    vcard = null;
                    pbSize--;
                    continue;
                }

                Log.i(TAG, "vcard selector check pass");

                if (needSendBody == NEED_SEND_BODY) {
                    vcard = vcardfilter.apply(vcard, vcardType21);
                    vcard = stripTelephoneNumber(vcard);

                    if (V) {
                        Log.v(TAG, "vCard after cleanup: " + vcard);
                    }

                    if (!buffer.writeVCard(vcard)) {
                        // onEntryCreate() already emits error.
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                }
            }

            if (needSendBody != NEED_SEND_BODY) {
                return pbSize;
            }
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (buffer != null) {
                buffer.terminate();
            }
        }

        if (V) {
            Log.v(TAG, "Total vcard composing and sending out takes " + (System.currentTimeMillis()
                    - timestamp) + " ms");
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int composeCallLogsAndSendSelectedVCards(Operation op, final String selection,
            final boolean vcardType21, int needSendBody, int pbSize, String ownerVCard,
            boolean ignorefilter, byte[] filter, byte[] selector, String vcardselectorop,
            boolean vCardSelct) {
        long timestamp = 0;
        if (V) {
            timestamp = System.currentTimeMillis();
        }

        BluetoothPbapCallLogComposer composer = null;
        HandlerForStringBuffer buffer = null;

        try {
            VCardFilter vcardfilter = new VCardFilter(ignorefilter ? null : filter);
            PropertySelector vcardselector = new PropertySelector(selector);
            composer = new BluetoothPbapCallLogComposer(mContext);
            buffer = new HandlerForStringBuffer(op, ownerVCard);
            if (!composer.init(CallLog.Calls.CONTENT_URI, selection, null, CALLLOG_SORT_ORDER)
                    || !buffer.init()) {
                return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
            }

            while (!composer.isAfterLast()) {
                if (BluetoothPbapObexServer.sIsAborted) {
                    ((ServerOperation) op).setAborted(true);
                    BluetoothPbapObexServer.sIsAborted = false;
                    break;
                }
                String vcard = composer.createOneEntry(vcardType21);
                if (vCardSelct) {
                    if (!vcardselector.checkVCardSelector(vcard, vcardselectorop)) {
                        Log.e(TAG, "Checking vcard selector for call log");
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                                19);
                        vcard = null;
                        pbSize--;
                        continue;
                    }
                    if (needSendBody == NEED_SEND_BODY) {
                        if (vcard == null) {
                            Log.e(TAG, "Failed to read a contact. Error reason: "
                                    + composer.getErrorReason());
                            ContentProfileErrorReportUtils.report(
                                    BluetoothProfile.PBAP,
                                    BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                                    BluetoothStatsLog
                                            .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                                    20);
                            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                        } else if (vcard.isEmpty()) {
                            Log.i(TAG, "Call Log may have been deleted during operation");
                            continue;
                        }
                        vcard = vcardfilter.apply(vcard, vcardType21);

                        if (V) {
                            Log.v(TAG, "Vcard Entry:");
                            Log.v(TAG, vcard);
                        }
                        buffer.writeVCard(vcard);
                    }
                } else {
                    if (vcard == null) {
                        Log.e(TAG, "Failed to read a contact. Error reason: "
                                + composer.getErrorReason());
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_VCARD_MANAGER,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                                21);
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                    if (V) {
                        Log.v(TAG, "Vcard Entry:");
                        Log.v(TAG, vcard);
                    }
                    buffer.writeVCard(vcard);
                }
            }
            if (needSendBody != NEED_SEND_BODY && vCardSelct) {
                return pbSize;
            }
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (buffer != null) {
                buffer.terminate();
            }
        }

        if (V) {
            Log.v(TAG, "Total vcard composing and sending out takes " + (System.currentTimeMillis()
                    - timestamp) + " ms");
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    public String stripTelephoneNumber(String vCard) {
        String separator = System.getProperty("line.separator");
        String[] attr = vCard.split(separator);
        String stripedVCard = "";
        for (int i = 0; i < attr.length; i++) {
            if (attr[i].startsWith("TEL")) {
                String[] vTagAndTel = attr[i].split(":", 2);
                int telLenBefore = vTagAndTel[1].length();
                // Remove '-', '(', ')' or ' ' from TEL number
                vTagAndTel[1] = vTagAndTel[1].replace("-", "")
                                             .replace("(", "")
                                             .replace(")", "")
                                             .replace(" ", "");
                if (vTagAndTel[1].length() < telLenBefore) {
                    if (V) {
                        Log.v(TAG, "Fixing vCard TEL to " + vTagAndTel[1]);
                    }
                    attr[i] = new StringBuilder().append(vTagAndTel[0]).append(":")
                                                 .append(vTagAndTel[1]).toString();
                }
            }
        }

        for (int i = 0; i < attr.length; i++) {
            if (!attr[i].isEmpty()) {
                stripedVCard = stripedVCard.concat(attr[i] + separator);
            }
        }
        if (V) {
            Log.v(TAG, "vCard with stripped telephone no.: " + stripedVCard);
        }
        return stripedVCard;
    }

    public static class VCardFilter {
        private enum FilterBit {
            //       bit  property                  onlyCheckV21  excludeForV21
            FN(1, "FN", true, false),
            PHOTO(3, "PHOTO", false, false),
            BDAY(4, "BDAY", false, false),
            ADR(5, "ADR", false, false),
            EMAIL(8, "EMAIL", false, false),
            TITLE(12, "TITLE", false, false),
            ORG(16, "ORG", false, false),
            NOTE(17, "NOTE", false, false),
            SOUND(19, "SOUND", false, false),
            URL(20, "URL", false, false),
            NICKNAME(23, "NICKNAME", false, true),
            DATETIME(28, "X-IRMC-CALL-DATETIME", false, false);

            public final int pos;
            public final String prop;
            public final boolean onlyCheckV21;
            public final boolean excludeForV21;

            FilterBit(int pos, String prop, boolean onlyCheckV21, boolean excludeForV21) {
                this.pos = pos;
                this.prop = prop;
                this.onlyCheckV21 = onlyCheckV21;
                this.excludeForV21 = excludeForV21;
            }
        }

        private static final String SEPARATOR = System.getProperty("line.separator");
        private final byte[] mFilter;

        //This function returns true if the attributes needs to be included in the filtered vcard.
        private boolean isFilteredIn(FilterBit bit, boolean vCardType21) {
            final int offset = (bit.pos / 8) + 1;
            final int bitPos = bit.pos % 8;
            if (!vCardType21 && bit.onlyCheckV21) {
                return true;
            }
            if (vCardType21 && bit.excludeForV21) {
                return false;
            }
            if (mFilter == null || offset > mFilter.length) {
                return true;
            }
            return ((mFilter[mFilter.length - offset] >> bitPos) & 0x01) != 0;
        }

        VCardFilter(byte[] filter) {
            this.mFilter = filter;
        }

        public boolean isPhotoEnabled() {
            return isFilteredIn(FilterBit.PHOTO, false);
        }

        public String apply(String vCard, boolean vCardType21) {
            if (mFilter == null) {
                return vCard;
            }
            String[] lines = vCard.split(SEPARATOR);
            StringBuilder filteredVCard = new StringBuilder();
            boolean filteredIn = false;

            for (String line : lines) {
                // Check whether the current property is changing (ignoring multi-line properties)
                // and determine if the current property is filtered in.
                if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                    String currentProp = line.split("[;:]")[0];
                    filteredIn = true;

                    for (FilterBit bit : FilterBit.values()) {
                        if (bit.prop.equals(currentProp)) {
                            filteredIn = isFilteredIn(bit, vCardType21);
                            break;
                        }
                    }

                    // Since PBAP does not have filter bits for IM and SIP,
                    // exclude them by default. Easiest way is to exclude all
                    // X- fields, except date time....
                    if (currentProp.startsWith("X-")) {
                        filteredIn = false;
                        if (currentProp.equals("X-IRMC-CALL-DATETIME")) {
                            filteredIn = true;
                        }
                    }
                }

                // Build filtered vCard
                if (filteredIn) {
                    filteredVCard.append(line + SEPARATOR);
                }
            }

            return filteredVCard.toString();
        }
    }

    @VisibleForTesting
    static class PropertySelector {
        private enum PropertyMask {
            //               bit    property
            VERSION(0, "VERSION"),
            FN(1, "FN"),
            NAME(2, "N"),
            PHOTO(3, "PHOTO"),
            BDAY(4, "BDAY"),
            ADR(5, "ADR"),
            LABEL(6, "LABEL"),
            TEL(7, "TEL"),
            EMAIL(8, "EMAIL"),
            TITLE(12, "TITLE"),
            ORG(16, "ORG"),
            NOTE(17, "NOTE"),
            URL(20, "URL"),
            NICKNAME(23, "NICKNAME"),
            DATETIME(28, "DATETIME");

            public final int mBitPosition;
            public final String mProperty;

            PropertyMask(int bitPosition, String property) {
                this.mBitPosition = bitPosition;
                this.mProperty = property;
            }
        }

        private static final String SEPARATOR = System.getProperty("line.separator");
        private final byte[] mSelector;

        PropertySelector(byte[] selector) {
            this.mSelector = selector;
        }

        boolean checkVCardSelector(String vCard, String vCardSelectorOperator) {
            Log.d(TAG, "vCardSelectorOperator=" + vCardSelectorOperator);

            final boolean checkAtLeastOnePropertyExists = vCardSelectorOperator.equals("0");
            final boolean checkAllPropertiesExist = vCardSelectorOperator.equals("1");

            boolean result = true;

            if (checkAtLeastOnePropertyExists) {
                for (PropertyMask mask : PropertyMask.values()) {
                    if (!checkBit(mask.mBitPosition, mSelector)) {
                        continue;
                    }
                    Log.d(TAG, "checking for prop :" + mask.mProperty);

                    if (doesVCardHaveProperty(vCard, mask.mProperty)) {
                        Log.d(TAG, "mask.prop.equals current prop :" + mask.mProperty);
                        return true;
                    } else {
                        result = false;
                    }
                }
            } else if (checkAllPropertiesExist) {
                for (PropertyMask mask : PropertyMask.values()) {
                    if (!checkBit(mask.mBitPosition, mSelector)) {
                        continue;
                    }
                    Log.d(TAG, "checking for prop :" + mask.mProperty);

                    if (!doesVCardHaveProperty(vCard, mask.mProperty)) {
                        Log.d(TAG, "mask.prop.notequals current prop" + mask.mProperty);
                        return false;
                    }
                }
            }

            return result;
        }

        private boolean checkBit(int attrBit, byte[] selector) {
            int offset = (attrBit / 8) + 1;
            if (mSelector == null || offset > mSelector.length) {
                return false;
            }
            return ((selector[mSelector.length - offset] >> (attrBit % 8)) & 0x01) != 0;
        }
    }

    private static Uri getPhoneLookupFilterUri() {
        return PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI;
    }

    /**
     * Get size of the cursor without duplicated contact id. This assumes the
     * given cursor is sorted by CONTACT_ID.
     */
    private static int getDistinctContactIdSize(Cursor cursor) {
        final int contactIdColumn = cursor.getColumnIndex(Data.CONTACT_ID);
        final int idColumn = cursor.getColumnIndex(Data._ID);
        long previousContactId = -1;
        int count = 0;
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long contactId =
                    cursor.getLong(contactIdColumn != -1 ? contactIdColumn : idColumn);
            if (previousContactId != contactId) {
                count++;
                previousContactId = contactId;
            }
        }
        if (V) {
            Log.i(TAG, "getDistinctContactIdSize result: " + count);
        }
        return count;
    }

    /**
     * Append "display_name,contact_id" string array from cursor to ArrayList.
     * This assumes the given cursor is sorted by CONTACT_ID.
     */
    private static void appendDistinctNameIdList(ArrayList<String> resultList, String defaultName,
            Cursor cursor) {
        final int contactIdColumn = cursor.getColumnIndex(Data.CONTACT_ID);
        final int idColumn = cursor.getColumnIndex(Data._ID);
        final int nameColumn = cursor.getColumnIndex(Data.DISPLAY_NAME);
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long contactId =
                    cursor.getLong(contactIdColumn != -1 ? contactIdColumn : idColumn);
            String displayName = nameColumn != -1 ? cursor.getString(nameColumn) : defaultName;
            if (TextUtils.isEmpty(displayName)) {
                displayName = defaultName;
            }

            String newString = displayName + "," + contactId;
            if (!resultList.contains(newString)) {
                resultList.add(newString);
            }
        }
        if (V) {
            for (String nameId : resultList) {
                Log.i(TAG, "appendDistinctNameIdList result: " + nameId);
            }
        }
    }

    @VisibleForTesting
    static String getNameFromVCard(String vCard) {
        String[] lines = vCard.split(PropertySelector.SEPARATOR);
        String name = "";
        for (String line : lines) {
            if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                if (line.startsWith("N:")) {
                    name = line.substring(line.lastIndexOf(':') + 1);
                }
            }
        }
        Log.d(TAG, "returning name: " + name);
        return name;
    }

    private static boolean doesVCardHaveProperty(String vCard, String property) {
        String[] lines = vCard.split(PropertySelector.SEPARATOR);
        for (String line : lines) {
            if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                String currentProperty = line.split("[;:]")[0];
                if (property.equals(currentProperty)) {
                    return true;
                }
            }
        }
        return false;
    }
}
