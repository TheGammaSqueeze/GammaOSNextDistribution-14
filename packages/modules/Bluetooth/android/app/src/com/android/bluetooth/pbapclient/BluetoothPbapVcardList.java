/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.bluetooth.pbapclient;

import android.accounts.Account;
import android.util.Log;

import com.android.vcard.VCardConfig;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardVersionException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

class BluetoothPbapVcardList {
    private static final String TAG = BluetoothPbapVcardList.class.getSimpleName();
    // {@link BufferedInputStream#DEFAULT_BUFFER_SIZE} is not public
    private static final int BIS_DEFAULT_BUFFER_SIZE = 8192;

    private final ArrayList<VCardEntry> mCards = new ArrayList<VCardEntry>();
    private final Account mAccount;

    class CardEntryHandler implements VCardEntryHandler {
        @Override
        public void onStart() {
        }

        @Override
        public void onEntryCreated(VCardEntry entry) {
            mCards.add(entry);
        }

        @Override
        public void onEnd() {
        }
    }

    BluetoothPbapVcardList(Account account, InputStream in, byte format) throws IOException {
        if (format != PbapClientConnectionHandler.VCARD_TYPE_21
                && format != PbapClientConnectionHandler.VCARD_TYPE_30) {
            throw new IllegalArgumentException("Unsupported vCard version.");
        }
        mAccount = account;
        parse(in, format);
    }

    private void parse(InputStream in, byte format) throws IOException {
        VCardParser parser;

        if (format == PbapClientConnectionHandler.VCARD_TYPE_30) {
            parser = new VCardParser_V30();
        } else {
            parser = new VCardParser_V21();
        }

        VCardEntryConstructor constructor =
                new VCardEntryConstructor(VCardConfig.VCARD_TYPE_V21_GENERIC, mAccount);

        CardEntryHandler handler = new CardEntryHandler();
        constructor.addEntryHandler(handler);

        parser.addInterpreter(constructor);

        // {@link BufferedInputStream} supports the {@link InputStream#mark} and
        // {@link InputStream#reset} methods.
        BufferedInputStream bufferedInput = new BufferedInputStream(in);
        bufferedInput.mark(BIS_DEFAULT_BUFFER_SIZE /* readlimit */);

        // If there is a {@link VCardVersionException}, try parsing again with a different
        // version. Otherwise, parsing either succeeds (i.e., no {@link VCardException}) or it
        // fails with a different {@link VCardException}.
        if (parsedWithVcardVersionException(parser, bufferedInput)) {
            // PBAP v1.2.3 only supports vCard versions 2.1 and 3.0; it's one or the other
            if (format == PbapClientConnectionHandler.VCARD_TYPE_21) {
                parser = new VCardParser_V30();
                Log.w(TAG, "vCard version and Parser mismatch; expected v2.1, switching to v3.0");
            } else {
                parser = new VCardParser_V21();
                Log.w(TAG, "vCard version and Parser mismatch; expected v3.0, switching to v2.1");
            }
            // reset and try again
            bufferedInput.reset();
            mCards.clear();
            constructor.clear();
            parser.addInterpreter(constructor);
            if (parsedWithVcardVersionException(parser, bufferedInput)) {
                Log.e(TAG, "unsupported vCard version, neither v2.1 nor v3.0");
            }
        }
    }

    /**
     * Attempts to parse, with an eye on whether the correct version of Parser is used.
     *
     * @param parser -- the {@link VCardParser} to use.
     * @param in -- the {@link InputStream} to parse.
     * @return {@code true} if there was a {@link VCardVersionException}; {@code false} if there
     *         is any other {@link VCardException} or succeeds (i.e., no {@link VCardException}).
     * @throws IOException if there's an issue reading the {@link InputStream}.
     */
    private boolean parsedWithVcardVersionException(VCardParser parser, InputStream in)
            throws IOException {
        try {
            parser.parse(in);
        } catch (VCardVersionException e1) {
            Log.w(TAG, "vCard version and Parser mismatch", e1);
            return true;
        } catch (VCardException e2) {
            Log.e(TAG, "vCard exception", e2);
        }
        return false;
    }

    public int getCount() {
        return mCards.size();
    }

    public ArrayList<VCardEntry> getList() {
        return mCards;
    }

    public VCardEntry getFirst() {
        return mCards.get(0);
    }
}
