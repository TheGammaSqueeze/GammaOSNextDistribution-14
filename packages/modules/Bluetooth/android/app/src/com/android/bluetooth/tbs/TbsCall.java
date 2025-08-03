/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.tbs;

import android.bluetooth.BluetoothLeCall;
import android.net.Uri;

public class TbsCall {

    public static final int INDEX_UNASSIGNED = 0x00;
    public static final int INDEX_MIN = 0x01;
    public static final int INDEX_MAX = 0xFF;

    private int mState;
    private String mUri;
    private int mFlags;
    private String mFriendlyName;

    /**
     * Converts state value to human readable state string
     *
     * @param state state of call
     * @return converted to string state
     */
    public static String stateToString(Integer state) {
        if (state.equals(BluetoothLeCall.STATE_INCOMING)) {
            return "INCOMING";
        } else if (state.equals(BluetoothLeCall.STATE_DIALING)) {
            return "DIALING";
        } else if (state.equals(BluetoothLeCall.STATE_ALERTING)) {
            return "ALERTING";
        } else if (state.equals(BluetoothLeCall.STATE_ACTIVE)) {
            return "ACTIVE";
        } else if (state.equals(BluetoothLeCall.STATE_LOCALLY_HELD)) {
            return "LOCALLY HELD";
        } else if (state.equals(BluetoothLeCall.STATE_REMOTELY_HELD)) {
            return "REMOTELY HELD";
        } else if (state.equals(BluetoothLeCall.STATE_LOCALLY_AND_REMOTELY_HELD)) {
            return "LOCALLY AND REMOTELY HELD";
        } else {
            return "UNKNOWN(" + state + ")";
        }
    }

    /**
     * Converts call flags value to human readable flag string
     *
     * @param flags call flags
     * @return converted to string flags
     */
    public static String flagsToString(Integer flags) {
        String string = "";

        if (flags.equals(BluetoothLeCall.FLAG_OUTGOING_CALL)) {
            if (string.isEmpty()) {
                string += "OUTGOING";
            }
        }
        if (flags.equals(BluetoothLeCall.FLAG_WITHHELD_BY_SERVER)) {
            if (!string.isEmpty()) {
                string += "|";
            }
            string += "WITHELD BY SERVER";
        }
        if (flags.equals(BluetoothLeCall.FLAG_WITHHELD_BY_NETWORK)) {
            if (!string.isEmpty()) {
                string += "|";
            }
            string += "WITHELD BY NETWORK";
        }

        return string;
    }

    private TbsCall(int state, String uri, int flags, String friendlyName) {
        mState = state;
        mUri = uri;
        mFlags = flags;
        mFriendlyName = friendlyName;
    }

    public static TbsCall create(BluetoothLeCall call) {
        return new TbsCall(call.getState(), call.getUri(), call.getCallFlags(),
                call.getFriendlyName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TbsCall that = (TbsCall) o;
        // check the state only
        return mState == that.mState;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public String getUri() {
        return mUri;
    }

    public String getSafeUri() {
        return Uri.parse(mUri).toSafeString();
    }

    public int getFlags() {
        return mFlags;
    }

    public boolean isIncoming() {
        return (mFlags & BluetoothLeCall.FLAG_OUTGOING_CALL) == 0;
    }

    public String getFriendlyName() {
        return mFriendlyName;
    }

    /**
     * Converts Friendly Name to safe string (every second letter is replaced by '.')
     *
     * @return safe Friendly Name
     */
    public String getSafeFriendlyName() {;
        if (mFriendlyName == null) {
            return null;
        }

        /* Don't anonymize short names */
        if (mFriendlyName.length() < 3) {
            return mFriendlyName;
        }

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mFriendlyName.length(); i++) {
            final char c = mFriendlyName.charAt(i);

            /* Anonymize every second letter */
            if ((i % 2) == 0) {
                builder.append(c);
            } else {
                builder.append('.');
            }
        }
        return builder.toString();
    }
}
