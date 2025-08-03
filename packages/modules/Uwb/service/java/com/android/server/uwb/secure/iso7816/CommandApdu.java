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

import static com.android.server.uwb.secure.iso7816.Iso7816Constants.OFFSET_CLA;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.OFFSET_INS;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.OFFSET_LC;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.OFFSET_P1;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.OFFSET_P2;

import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.util.Hex;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedBytes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Representation of an ISO7816-4 APDU command. Standard and extended length APDUs are supported.
 * For standard APDUs, the maximum command length is 255 bytes and the maximum response length is
 * 256 bytes. For extended APDUs, the maximum command length is 65535 bytes and the maximum response
 * length is 65536 bytes.
 */
public class CommandApdu {

    /** Sets the Le byte to return as many bytes as possible (all remaining) up to 256. */
    public static final int LE_ANY = 0;

    private static final int NO_EXPECTED_RESPONSE = -1;

    private static final int MAX_CDATA_LEN = 255; // As per ISO7816-4 for standard length APDU's.
    private static final int MAX_RDATA_LEN = 256; // Maximum standard length response.

    // ISO7816-4 APDU components.
    private final byte mCla; // Class byte.

    private final byte mIns; // Instruction byte.

    private final byte mP1; // Parameter 1.

    private final byte mP2; // Parameter 2.

    private final int mLc; // Length of command data.

    private final int mLe; // Expected length of response.

    private final boolean mForceExtended;

    // cdata is always cloned (an alternative immutable class would cost in performance)
    private final byte[] mCdata; // Command data.

    private final ImmutableSet<StatusWord> mExpected;

    /**
     * Constructs a case 4 APDU (Command with data and an expected response).
     *
     * <p>When the {@link CommandApdu} instance is created, only the low order byte is used for the
     * values of cla, ins, p1 and p2. They are int's in the constructor as a convenience only so as
     * to ensure the caller does not need to cast to a byte. The casting and masking is handled
     * internally.
     *
     * <p>The command data in an {@link CommandApdu} instance is immutable. The data passed in will
     * be copied and updates to the original data will not be reflected in the {@link CommandApdu}
     * instance.
     */
    @VisibleForTesting
    CommandApdu(
            int cla,
            int ins,
            int p1,
            int p2,
            @Nullable byte[] cdata,
            int le,
            boolean forceExtended,
            StatusWord... exp) {
        Preconditions.checkArgument(exp.length > 0);

        this.mCla = (byte) (cla & 0xff);
        this.mIns = (byte) (ins & 0xff);
        this.mP1 = (byte) (p1 & 0xff);
        this.mP2 = (byte) (p2 & 0xff);
        this.mCdata = (cdata != null) ? cdata.clone() : new byte[0];
        this.mLc = this.mCdata.length;
        this.mLe = le;
        this.mForceExtended = forceExtended;

        Preconditions.checkArgument((mLc >> Short.SIZE) == 0, "Lc must be between 0 and 65,535: %s",
                mLc);
        Preconditions.checkArgument(
                le == NO_EXPECTED_RESPONSE || (le >> Short.SIZE) == 0,
                "Le must be between 0 and 65,535: %s",
                le);

        mExpected = ImmutableSet.copyOf(exp);
        // for now, don't allow any unlisted status words to be set as expected
        Preconditions.checkArgument(StatusWord.areAllKnown(mExpected));
    }

    /**
     * Gets the encoded byte stream that represents this APDU.
     *
     * <p>The encoded form is: <code>cla |
     * ins | p1 | p2 | lc | data | le</code>
     */
    public byte[] getEncoded() {
        // Minimum APDU length (case 1 APDU's).
        int len = 4;
        boolean extended = mForceExtended;

        // Adjust length for any command data.
        if (mLc > 0) {
            // Add the data length plus make space for Lc.
            len += 1 + mLc;

            if (mLc > MAX_CDATA_LEN) {
                len += 2; // Add room for an extended length APDU.
                extended = true;
            }
        } else {
            if (mLe > MAX_RDATA_LEN) {
                extended = true;
            }
        }

        // Adjust for Le if present.
        if (mLe > NO_EXPECTED_RESPONSE) {
            len++;

            // Check if we need to make Le extended as well.
            if (extended) {
                len += 2;
            }
        }

        // Create the APDU header.
        byte[] apdu = new byte[len];
        apdu[OFFSET_CLA] = mCla;
        apdu[OFFSET_INS] = mIns;
        apdu[OFFSET_P1] = mP1;
        apdu[OFFSET_P2] = mP2;

        int off = OFFSET_LC;

        // Check to see if data needs to be added to the command.
        if (mLc > 0) {
            // Only add Lc if there is data.
            if (extended) {
                apdu[off++] = 0;
                apdu[off++] = (byte) (mLc >> 8);
                apdu[off++] = (byte) (mLc & 0xff);
                System.arraycopy(mCdata, 0, apdu, off, mLc);
                off += mLc;
            } else {
                apdu[off++] = (byte) mLc;
                System.arraycopy(mCdata, 0, apdu, off, mLc);
                off += mLc;
            }
        }

        if (mLe > NO_EXPECTED_RESPONSE) {
            if (extended) {
                apdu[off++] = 0;
                apdu[off++] = (byte) (mLe >> 8);
                apdu[off++] = (byte) (mLe & 0xff);
            } else {
                // When the length is exactly 256, the value is cast to 0x00.
                // A command expecting no data does not send an Le.
                apdu[off] = (byte) mLe;
            }
        }

        return apdu;
    }

    /**
     * Gets the CLA of APDU.
     */
    public byte getCla() {
        return mCla;
    }

    /**
     * Gets the INS of APDU.
     */
    public byte getIns() {
        return mIns;
    }

    /**
     * Gets the P1 of APDU.
     */
    public byte getP1() {
        return mP1;
    }

    /**
     * Gets the P2 of APDU.
     */
    public byte getP2() {
        return mP2;
    }

    /** Returns true if this commands expects data back from the card. i.e. If le is >= 0. */
    public boolean hasLe() {
        return mLe != NO_EXPECTED_RESPONSE;
    }

    /**
     * Gets the LE of APDU.
     */
    public int getLe() {
        Preconditions.checkState(hasLe());
        return mLe;
    }

    /**
     * Returns a copy of the command data for the APDU. Updates to this copy will not affect the
     * internal copy in this instance.
     */
    public byte[] getCommandData() {
        return mCdata.clone();
    }

    /**
     * Returns the expected {@link StatusWord} responses for this {@link CommandApdu}. Any {@link
     * StatusWord} that is expected will not cause an exception to be thrown.
     */
    public ImmutableSet<StatusWord> getExpected() {
        return mExpected;
    }

    /**
     * Check if the given status word is accepted.
     */
    public boolean acceptsStatusWord(StatusWord actual) {
        return mExpected.contains(actual);
    }

    /**
     * check if the status word of the given ResponseApdu is accepted.
     */
    public boolean acceptsResponse(ResponseApdu response) {
        return acceptsStatusWord(StatusWord.fromInt(response.getStatusWord()));
    }

    @Override
    public String toString() {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        printWriter.printf("Command : CLA=%02x, INS=%02x, P1=%02x, P2=%02x", mCla, mIns, mP1, mP2);

        if (mLc > 0) {
            printWriter.printf(", Lc=%04x [%s]", mLc, Hex.encode(mCdata));
        }

        if (mLe > NO_EXPECTED_RESPONSE) {
            printWriter.printf(", Le=%04x", mLe);
        }

        return stringWriter.toString();
    }

    /**
     * Parses a command APDU and returns an {@link CommandApdu} instance. Currently only supports
     * standard length APDU's.
     */
    public static CommandApdu parse(byte[] command) {
        ByteBuffer buf = ByteBuffer.wrap(Preconditions.checkNotNull(command));
        byte cla = buf.get();
        byte ins = buf.get();
        byte p1 = buf.get();
        byte p2 = buf.get();

        Builder builder = builder(cla, ins, p1, p2);

        if (buf.hasRemaining()) {
            int lc = UnsignedBytes.toInt(buf.get());

            if (!buf.hasRemaining()) {
                builder.setLe(lc);
            } else {
                byte[] cdata = new byte[lc];
                buf.get(cdata);
                builder.setCdata(cdata);

                if (buf.hasRemaining()) {
                    builder.setLe(UnsignedBytes.toInt(buf.get()));
                }
            }
        }

        if (buf.hasRemaining()) {
            throw new IllegalArgumentException("Invalid APDU: " + Hex.encode(command));
        }

        return builder.build();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }

        if (this.getClass() == obj.getClass()) {
            CommandApdu other = (CommandApdu) obj;
            // @formatter:off
            return this.mCla == other.mCla
                    && this.mIns == other.mIns
                    && this.mP1 == other.mP1
                    && this.mP2 == other.mP2
                    && Arrays.equals(this.mCdata, other.mCdata)
                    && this.mLe == other.mLe
                    && this.mExpected.equals(other.mExpected);
            // @formatter:on
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mCla, mIns, mP1, mP2, Arrays.hashCode(mCdata), mLe, mExpected);
    }

    /**
     * Help method to get the Builder of the CommandApdu.
     */
    public static Builder builder(int cla, int ins, int p1, int p2) {
        return new Builder(cla, ins, p1, p2);
    }

    /** Builder for {@link CommandApdu} instances. */
    public static class Builder {

        // ISO7816-4 APDU components.
        private final byte mCla; // Class byte.

        private final byte mIns; // Instruction byte.

        private final byte mP1; // Parameter 1.

        private final byte mP2; // Parameter 2.

        private int mLe = NO_EXPECTED_RESPONSE; // Expected length of response.

        private byte[] mCdata = {}; // Command data.

        @Nullable private StatusWord[] mExpected = null;

        private boolean mForceExtended = false;

        private Builder(int cla, int ins, int p1, int p2) {
            this.mCla = (byte) cla;
            this.mIns = (byte) ins;
            this.mP1 = (byte) p1;
            this.mP2 = (byte) p2;
        }

        /**
         * Sets the LE of the CommandApdu.
         */
        public Builder setLe(int le) {
            this.mLe = le;
            return this;
        }

        /**
         * Sets the data field of the CommandApdu.
         */
        public Builder setCdata(byte[] cdata) {
            this.mCdata = cdata;
            return this;
        }

        /**
         * Sets the expected status words of the response for the CommandApdu.
         * Slightly less efficient helper method that makes going from an instance
         * to a builder easier.
         */
        public Builder setExpected(Collection<StatusWord> expected) {
            return setExpected(expected.toArray(new StatusWord[expected.size()]));
        }

        /**
         * Sets the expected status words of the response for the CommandApdu.
         */
        public Builder setExpected(StatusWord... expected) {
            Preconditions.checkArgument(expected.length > 0);
            this.mExpected = expected;
            return this;
        }

        /**
         * Sets the extended length bit of the CommandApdu.
         */
        public Builder setExtendedLength() {
            mForceExtended = true;
            return this;
        }

        /**
         * Builds the instance of CommandApdu.
         */
        public CommandApdu build() {
            return new CommandApdu(
                    mCla,
                    mIns,
                    mP1,
                    mP2,
                    mCdata,
                    mLe,
                    mForceExtended,
                    mExpected != null ? mExpected : new StatusWord[] {StatusWord.SW_NO_ERROR});
        }
    }
}
