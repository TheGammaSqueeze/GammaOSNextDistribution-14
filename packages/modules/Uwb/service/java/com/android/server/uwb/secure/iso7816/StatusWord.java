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

import androidx.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Shorts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Representation of some common ISO7816-4 and GlobalPlatform status words. */
public final class StatusWord {

    public static final StatusWord SW_NO_ERROR =
            new StatusWord(0x9000, "no error");

    public static final StatusWord SW_RESPONSE_BYTES_STILL_AVAILABLE =
            new StatusWord(0x6100, "Response bytes still available");

    public static final StatusWord SW_WARNING_STATE_UNCHANGED =
            new StatusWord(0x6200, "Warning: State unchanged");

    public static final StatusWord SW_CARD_MANAGER_LOCKED =
            new StatusWord(0x6283, "Warning: Card Manager is locked");

    public static final StatusWord SW_WARNING_NO_INFO_GIVEN =
            new StatusWord(0x6300, "Warning: State changed (no information given)");

    public static final StatusWord SW_WARNING_MORE_DATA =
            new StatusWord(0x6310, "more data");

    public static final StatusWord SW_VERIFY_FAILED =
            new StatusWord(0x63C0, "PIN authentication failed.");

    public static final StatusWord SW_NO_SPECIFIC_DIAGNOSTIC =
            new StatusWord(0x6400, "No specific diagnostic");

    public static final StatusWord SW_REQUESTED_ELEMENTS_NOT_AVAILABLE =
            new StatusWord(0x6402, "Requested elements not available");

    public static final StatusWord SW_ICA_ALREADY_EXISTS =
            new StatusWord(0x6409, "ICA Already Exists");

    public static final StatusWord SW_WRONG_LENGTH =
            new StatusWord(0x6700, "Wrong length");

    public static final StatusWord SW_SECURITY_STATUS_NOT_SATISFIED =
            new StatusWord(0x6982, "Security status not satisfied");

    public static final StatusWord SW_FILE_INVALID =
            new StatusWord(0x6983, "File invalid");

    public static final StatusWord SW_REFERENCE_DATA_NOT_USABLE =
            new StatusWord(0x6984, "Reference data not usable");

    public static final StatusWord SW_CONDITIONS_NOT_SATISFIED =
            new StatusWord(0x6985, "Conditions of use not satisfied");

    public static final StatusWord SW_COMMAND_NOT_ALLOWED =
            new StatusWord(0x6986, "Command not allowed");

    public static final StatusWord SW_APPLET_SELECT_FAILED =
            new StatusWord(0x6999, "Applet selection failed");

    public static final StatusWord SW_WRONG_DATA =
            new StatusWord(0x6A80, "Wrong data");

    public static final StatusWord SW_FUNCTION_NOT_SUPPORTED =
            new StatusWord(0x6A81, "Function not supported");

    public static final StatusWord SW_FILE_NOT_FOUND =
            new StatusWord(0x6A82, "File not found");

    public static final StatusWord SW_RECORD_NOT_FOUND =
            new StatusWord(0x6A83, "Record not found");

    public static final StatusWord SW_NOT_ENOUGH_MEMORY =
            new StatusWord(0x6A84, "Not enough memory");

    public static final StatusWord SW_NC_INCONSISTENT_WITH_TLV =
            new StatusWord(0x6A85, "Nc inconsistent with TLV structure");

    public static final StatusWord SW_INCORRECT_P1P2 =
            new StatusWord(0x6A86, "Incorrect P1 or P2");

    public static final StatusWord SW_DATA_NOT_FOUND =
            new StatusWord(0x6A88, "Referenced data not found");

    public static final StatusWord SW_FILE_ALREADY_EXISTS =
            new StatusWord(0x6A89, "File already exists");

    public static final StatusWord SW_WRONG_P1P2 =
            new StatusWord(0x6B00, "Wrong P1 or P2");

    public static final StatusWord SW_WRONG_LE =
            new StatusWord(0x6C00, "Wrong Le");

    public static final StatusWord SW_INS_NOT_SUPPORTED =
            new StatusWord(0x6D00, "Instruction not supported or invalid");

    public static final StatusWord SW_CLA_NOT_SUPPORTED =
            new StatusWord(0x6E00, "Class not supported");

    public static final StatusWord SW_UNKNOWN_ERROR =
            new StatusWord(0x6F00, "Unknown error (no precise diagnosis)");

    private static final String UNKNOWN_STATUS_WORD_MESSAGE = "Unknown status word";

    public static final ImmutableSet<StatusWord> ALL_KNOWN_STATUS_WORDS =
            ImmutableSet.of(
                    SW_NO_ERROR,
                    SW_RESPONSE_BYTES_STILL_AVAILABLE,
                    SW_WARNING_STATE_UNCHANGED,
                    SW_CARD_MANAGER_LOCKED,
                    SW_WARNING_NO_INFO_GIVEN,
                    SW_WARNING_MORE_DATA,
                    SW_VERIFY_FAILED,
                    SW_NO_SPECIFIC_DIAGNOSTIC,
                    SW_REQUESTED_ELEMENTS_NOT_AVAILABLE,
                    SW_ICA_ALREADY_EXISTS,
                    SW_WRONG_LENGTH,
                    SW_SECURITY_STATUS_NOT_SATISFIED,
                    SW_FILE_INVALID,
                    SW_REFERENCE_DATA_NOT_USABLE,
                    SW_CONDITIONS_NOT_SATISFIED,
                    SW_COMMAND_NOT_ALLOWED,
                    SW_APPLET_SELECT_FAILED,
                    SW_WRONG_DATA,
                    SW_FUNCTION_NOT_SUPPORTED,
                    SW_FILE_NOT_FOUND,
                    SW_RECORD_NOT_FOUND,
                    SW_NOT_ENOUGH_MEMORY,
                    SW_NC_INCONSISTENT_WITH_TLV,
                    SW_INCORRECT_P1P2,
                    SW_DATA_NOT_FOUND,
                    SW_FILE_ALREADY_EXISTS,
                    SW_WRONG_P1P2,
                    SW_WRONG_LE,
                    SW_INS_NOT_SUPPORTED,
                    SW_CLA_NOT_SUPPORTED,
                    SW_UNKNOWN_ERROR);

    /** A meessage that is used to construct an exception to represent this status word. */
    private final String mMessage;

    /** The actual status word (2 bytes). */
    private final int mStatusWord;

    /** Map status words to values for fast lookup. */
    private static final Map<Integer, StatusWord> STATUS_WORD_MAP;

    static {
        // Map all the values to their code.
        Map<Integer, StatusWord> statusWordMap = new LinkedHashMap<>(ALL_KNOWN_STATUS_WORDS.size());
        for (StatusWord value : ALL_KNOWN_STATUS_WORDS) {
            statusWordMap.put(Integer.valueOf(value.mStatusWord), value);
        }
        STATUS_WORD_MAP = Collections.unmodifiableMap(statusWordMap);
    }

    private StatusWord(int sw, String message) {
        mStatusWord = sw;
        this.mMessage = message;
    }

    /** Lookup a {@link StatusWord} from the status word value. */
    public static StatusWord fromInt(int sw) {
        Preconditions.checkArgument((sw >> Short.SIZE) == 0);
        StatusWord statusWord = STATUS_WORD_MAP.get(Integer.valueOf(sw));
        if (statusWord != null) {
            return statusWord;
        }
        return new StatusWord(sw, UNKNOWN_STATUS_WORD_MESSAGE);
    }

    /**
     * Gets the byte array form of the status word.
     */
    public byte[] toBytes() {
        Preconditions.checkState((mStatusWord >> Short.SIZE) == 0);
        return Shorts.toByteArray((short) mStatusWord);
    }

    /**
     * Gets the int value of the status word.
     */
    public int toInt() {
        return mStatusWord;
    }

    /**
     * Gets the description message of the status word.
     */
    public String getMessage() {
        return mMessage;
    }

    /**
     * Checks if this status word is known.
     */
    public boolean isKnown() {
        return ALL_KNOWN_STATUS_WORDS.contains(this);
    }

    /**
     * Checks if the given status words are known.
     */
    public static boolean areAllKnown(Iterable<StatusWord> statusWords) {
        for (StatusWord word : statusWords) {
            if (!word.isKnown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("'%04X': %s", mStatusWord, mMessage);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        StatusWord other = (StatusWord) obj;
        return other.mStatusWord == this.mStatusWord;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mStatusWord);
    }
}
