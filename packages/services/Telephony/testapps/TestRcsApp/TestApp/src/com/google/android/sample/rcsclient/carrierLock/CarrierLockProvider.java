/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.sample.rcsclient.carrierLock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;


import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CarrierLockProvider extends ContentProvider {

    public static final String AUTHORITY = "com.sample.lockProvider";
    public static final String TAG = "TestCarrierLockProvider";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/carrierLock");
    // content://com.sample.lockProvider/carrierLock

    private static CarrierRestriction mLockMode = CarrierRestriction.UNLOCKED;
    private static final ArrayList<Integer> mCarrierIds = new ArrayList<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String args, Bundle extras) {
        Bundle result = new Bundle();
        Log.d(TAG, "call query STARTED on method = " + method);
        switch (method) {
            case "getCarrierRestrictionStatus":
                try {
                    if (mLockMode == CarrierRestriction.UNLOCKED) {
                        result.putInt("restriction_status", 0); // Unlocked
                    } else {
                        result.putInt("restriction_status", 2); // Locked/Restricted
                    }
                    mCarrierIds.clear();
                    Log.d(TAG, "Query come : Lock mode set to " + mLockMode);
                    switch (mLockMode) {
                        case UNLOCKED:
                            // Do Nothing
                            break;
                        case LOCK_TO_VZW:
                            mCarrierIds.add(1839);
                            break;
                        case LOCK_TO_ATT:
                            mCarrierIds.add(1187);
                            mCarrierIds.add(10021);
                            mCarrierIds.add(2119);
                            mCarrierIds.add(2120);
                            mCarrierIds.add(1779);
                            mCarrierIds.add(10028);
                            break;
                        case LOCK_TO_TMO:
                            mCarrierIds.add(1);
                            break;
                        case LOCK_TO_KOODO:
                            mCarrierIds.add(2020);
                            break;
                        case LOCK_TO_TELUS:
                            mCarrierIds.add(1404);
                            break;
                        default:
                            // Nothing
                    }
                    StringJoiner joiner = new StringJoiner(", ");
                    if (!mCarrierIds.isEmpty()) {
                        result.putIntegerArrayList("allowed_carrier_ids", mCarrierIds);
                        for (Integer num : mCarrierIds) {
                            joiner.add(num.toString());
                        }
                        result.putString("PrintableCarrierIds", joiner.toString());
                        Log.d(TAG, "Locked to carrierIds = " + joiner.toString());
                    } else {
                        result.putString("allowed_carrier_ids", "");
                        result.putString("PrintableCarrierIds", "");
                    }

                } catch (Exception e) {
                    Log.e(TAG, " call :: query :: exception = " + e.getMessage());
                }
                return result;

            case "getList:":
                String list = String.valueOf(
                        mCarrierIds.size());
                result.putString("carrierList", list);
                return result;
            default:
                return null;
        }
    }

    private void updateLockValue(int lockValue) {
        Log.d(TAG, "updateLockValue through ADB to = " + lockValue);
        switch (lockValue) {
            case 1:
                mLockMode = CarrierRestriction.LOCK_TO_VZW;
                break;
            case 2:
                mLockMode = CarrierRestriction.LOCK_TO_ATT;
                break;
            case 3:
                mLockMode = CarrierRestriction.LOCK_TO_TMO;
                break;
            case 4:
                mLockMode = CarrierRestriction.LOCK_TO_KOODO;
                break;
            case 5:
                mLockMode = CarrierRestriction.LOCK_TO_TELUS;
                break;
            default:
                mLockMode = CarrierRestriction.UNLOCKED;
                break;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.d(TAG, "CarrierLockProvider Query");
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".books";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "CarrierLockProvider insert START");
        assert values != null;
        int newValue = values.getAsInteger("newValue");
        updateLockValue(newValue);
        return CONTENT_URI;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public void setLockMode(CarrierRestriction lockMode) {
        mLockMode = lockMode;
        Log.d(TAG, "Setting lockMode to " + mLockMode);
    }
}