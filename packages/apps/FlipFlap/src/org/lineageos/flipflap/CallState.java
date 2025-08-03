/*
 * Copyright (c) 2017 The LineageOS Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * Also add information on how to contact you by electronic and paper mail.
 *
 */

package org.lineageos.flipflap;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;

public class CallState {
    private static final String TAG = "CallState";

    private static final String[] DISPLAY_NAME = { ContactsContract.PhoneLookup.DISPLAY_NAME };
    private final int mState;
    private final String mName;
    private final String mNumber;

    public CallState(Context context, String state, String number) {
        this(context, stateStringToInt(state), number);
    }

    public CallState(Context context, int state, String number) {
        mState = state;
        if ("restricted".equalsIgnoreCase(number)) {
            // If call is restricted, don't show a number
            mName = number;
            mNumber = "";
        } else if (number == null) {
            mName = null;
            mNumber = null;
        } else {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            Cursor cursor = context.getContentResolver().query(uri, DISPLAY_NAME, number, null,
                    null);
            if (cursor != null) {
                mName = cursor.moveToFirst() ? cursor.getString(0) : "";
                cursor.close();
            } else {
                mName = "";
            }

            mNumber = number;
        }
    }

    public boolean isRinging() {
        return mState == TelephonyManager.CALL_STATE_RINGING;
    }

    public boolean isActive() {
        return mState != TelephonyManager.CALL_STATE_IDLE;
    }

    public String getName() {
        return mName;
    }

    public String getNumber() {
        return mNumber;
    }

    public String toString() {
        return "CallState[mState=" + mState + ", mName=" + mName + ", mNumber=" + mNumber + "]";
    }

    private static int stateStringToInt(String state) {
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            return TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            return TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            return TelephonyManager.CALL_STATE_RINGING;
        } else {
            throw new IllegalArgumentException("Invalid state " + state);
        }
    }
}
