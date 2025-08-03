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
package com.android.server.uwb.secure.iso7816;

import static com.android.server.uwb.secure.iso7816.StatusWord.SW_APPLET_SELECT_FAILED;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_CLA_NOT_SUPPORTED;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_CONDITIONS_NOT_SATISFIED;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_FILE_NOT_FOUND;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_INCORRECT_P1P2;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_INS_NOT_SUPPORTED;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_ERROR;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_UNKNOWN_ERROR;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_WRONG_DATA;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_WRONG_LE;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_WRONG_LENGTH;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_WRONG_P1P2;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.util.Hex;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


/** A class that represents the data contained in an ISO/IEC 7816-4 Response APDU. */
public class ResponseApdu {

    public static final ResponseApdu SW_CONDITIONS_NOT_SATISFIED_APDU =
            ResponseApdu.fromStatusWord(SW_CONDITIONS_NOT_SATISFIED);

    public static final ResponseApdu SW_INCORRECT_P1P2_APDU =
            ResponseApdu.fromStatusWord(SW_INCORRECT_P1P2);

    public static final ResponseApdu SW_FILE_NOT_FOUND_APDU =
            ResponseApdu.fromStatusWord(SW_FILE_NOT_FOUND);

    public static final ResponseApdu SW_WRONG_P1P2_APDU =
            ResponseApdu.fromStatusWord(SW_WRONG_P1P2);

    public static final ResponseApdu SW_WRONG_LE_APDU =
            ResponseApdu.fromStatusWord(SW_WRONG_LE);

    public static final ResponseApdu SW_WRONG_DATA_APDU =
            ResponseApdu.fromStatusWord(SW_WRONG_DATA);

    public static final ResponseApdu SW_WRONG_LENGTH_APDU =
            ResponseApdu.fromStatusWord(SW_WRONG_LENGTH);

    public static final ResponseApdu SW_CLA_NOT_SUPPORTED_APDU =
            ResponseApdu.fromStatusWord(SW_CLA_NOT_SUPPORTED);

    public static final ResponseApdu SW_INS_NOT_SUPPORTED_APDU =
            ResponseApdu.fromStatusWord(SW_INS_NOT_SUPPORTED);

    public static final ResponseApdu SW_WRONG_FILE_APDU =
            ResponseApdu.fromStatusWord(SW_FILE_NOT_FOUND);

    public static final ResponseApdu SW_UNKNOWN_APDU = ResponseApdu.fromStatusWord(
            SW_UNKNOWN_ERROR);

    public static final ResponseApdu SW_SUCCESS_APDU = ResponseApdu.fromStatusWord(SW_NO_ERROR);

    public static final ResponseApdu SW_APPLET_SELECT_FAILED_APDU =
            ResponseApdu.fromStatusWord(SW_APPLET_SELECT_FAILED);

    private static final long NO_TIME_RECORDED = -1L;

    private static final int SIZE_OF_SW = 2;

    private static final int MASK_OF_SW = 0xffff;

    private final byte[] mRdata;

    private final int mSw;

    private final long mCmdTimeMillis;

    @VisibleForTesting
    ResponseApdu(byte[] rdata, int sw, long cmdTimeMillis) {
        this.mRdata = rdata;
        this.mSw = sw;
        this.mCmdTimeMillis = cmdTimeMillis;
    }

    /**
     * Parses a raw APDU response to set the response data and status word. A response consists of
     * at
     * least a two byte status word and any number of data bytes. A standard length APDU supports
     * 256
     * bytes of data and 2 bytes of status word while and extended length APDU supports 32KB of
     * response data. A minimum response is simply a status word.
     *
     * @param response The raw response from the card to parse.
     * @throws IllegalArgumentException if the response is less than 2 bytes long.
     */
    public static ResponseApdu fromResponse(byte[] response) {
        return fromResponse(response, NO_TIME_RECORDED, TimeUnit.MILLISECONDS);
    }

  /**
   * Generate the ResponseApdu from the byte array(data) and status word.
   */
    public static ResponseApdu fromDataAndStatusWord(byte[] data, int sw) {
        Preconditions.checkArgument((sw >> Short.SIZE) == 0);
        return fromResponse(
                Bytes.concat(data == null ? new byte[]{} : data, Shorts.toByteArray((short) sw)));
    }

  /**
   * Generate the ResponseApdu form the list of TlvDatum and status word.
   */
    public static ResponseApdu fromDataAndStatusWord(List<TlvDatum> data, int sw) {
        byte[] dataBytes = new byte[]{};
        for (TlvDatum tlvDatum : data) {
            dataBytes = Bytes.concat(dataBytes, tlvDatum.toBytes());
        }
        return fromDataAndStatusWord(dataBytes, sw);
    }

  /**
   * Generate the ResponseApdu form the status word.
   */
    public static ResponseApdu fromStatusWord(StatusWord sw) {
        return fromResponse(sw.toBytes());
    }

    /**
     * Parses a raw APDU response to set the response data and status word. A response consists of
     * at
     * least a two byte status word and any number of data bytes. A standard length APDU supports
     * 256
     * bytes of data and 2 bytes of status word while and extended length APDU supports 32KB of
     * response data. A minimum response is simply a status word.
     *
     * @param response The raw response from the card to parse.
     * @param time     the time for the command to execute.
     * @param timeUnit the {@link TimeUnit} of the execution time.
     * @throws IllegalArgumentException if the response is less than 2 bytes long.
     */
    public static ResponseApdu fromResponse(byte[] response, long time, TimeUnit timeUnit) {
        Preconditions.checkNotNull(response);
        int len = response.length;
        long cmdTimeMillis = timeUnit.toMillis(time);

        // A response must at least have a status word (2 bytes).
        Preconditions.checkArgument(
                len >= SIZE_OF_SW,
                "Invalid response APDU after %sms. Must be at least 2 bytes long: [%s]",
                cmdTimeMillis,
                Hex.encode(response));

        ByteBuffer buffer = ByteBuffer.wrap(response);

        // Extract and store any response data.
        int rdataLen = len - SIZE_OF_SW;
        byte[] rdata = new byte[rdataLen];
        buffer.get(rdata, 0, rdataLen);

        // Extract and set the status word.
        int sw = buffer.getShort() & MASK_OF_SW;

        return new ResponseApdu(rdata, sw, cmdTimeMillis);
    }

    /**
     * Returns a copy of the response data for the APDU. Updates to this copy will not affect the
     * internal copy in this instance.
     */
    public byte[] getResponseData() {
        return mRdata.clone();
    }

    /**
     * Gets the status word.
     */
    public int getStatusWord() {
        return mSw;
    }

    /**
     * Convert the ResponseApdu to the byte array.
     */
    public byte[] toByteArray() {
        return Bytes.concat(mRdata, Shorts.toByteArray((short) mSw));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Response: ");

        if (mRdata != null && mRdata.length > 0) {
            sb.append(Hex.encode(mRdata)).append(", ");
        }

        sb.append(String.format("SW=%04x", mSw));

        if (mCmdTimeMillis > NO_TIME_RECORDED) {
            sb.append(String.format(Locale.US, ", elapsed: %dms", mCmdTimeMillis));
        }

        return sb.toString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This ignores the time the APDU took to complete and only compares the response data and
     * status word.
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }

        if (this.getClass() == obj.getClass()) {
            ResponseApdu other = (ResponseApdu) obj;
            return Arrays.equals(this.mRdata, other.mRdata) && this.mSw == other.mSw;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(Arrays.hashCode(mRdata), mSw);
    }
}
