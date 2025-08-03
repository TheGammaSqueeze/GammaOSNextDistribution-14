/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.timezone.location.storage.tzs2range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable data structure for storing sets of TZ IDs to be referenced by geo data. The
 * structure is banked so that a limited range of IDs can be used to access the sets. i.e. a chunk
 * of geo data can associated with a specific bank and then all individual set IDs are scoped to
 * that bank. This reduces the number of bits needed to store sets of TZ IDs.
 */
public final class BankedTzIdSets {

    private final List<String> mStringsByIndex;

    private final List<Bank> mBanks;

    private BankedTzIdSets(List<String> stringsByIndex, List<Bank> banks) {
        mStringsByIndex = Collections.unmodifiableList(new ArrayList<>(stringsByIndex));
        mBanks = Collections.unmodifiableList(new ArrayList<>(banks));
    }

    /** Returns the bank associated with the ID. */
    public Bank getBank(int bankId) {
        return mBanks.get(bankId);
    }

    /** Returns the number of banks. */
    public int getBankCount() {
        return mBanks.size();
    }

    /** Returns the Strings referenced by every bank. */
    public List<String> getStringsByIndex() {
        return mStringsByIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BankedTzIdSets that = (BankedTzIdSets) o;
        return mStringsByIndex.equals(that.mStringsByIndex)
                && mBanks.equals(that.mBanks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStringsByIndex, mBanks);
    }

    @Override
    public String toString() {
        return "BankedTzIdSets{"
                + "mStringsByIndex=" + mStringsByIndex
                + ", mBanks=" + mBanks
                + '}';
    }

    /** A builder of {@link BankedTzIdSets}. */
    public static class Builder {

        private final List<String> mStringsByIndex = new ArrayList<>();

        private final List<BankBuilder> mBankBuilders = new ArrayList<>();

        private int mNextBankId = 0;

        /**
         * Sets the time zone ID strings.
         */
        public Builder setStringsByIndex(List<String> stringsByIndex) {
            if (!this.mStringsByIndex.isEmpty()) {
                throw new IllegalStateException("stringsByIndex is already set");
            }
            mStringsByIndex.addAll(stringsByIndex);
            return this;
        }

        /**
         * Adds a new Bank, returns the {@link BankBuilder} to use to populate it. The
         * {@link Builder} keeps track of each {@link BankBuilder} it generates and will call
         * {@link BankBuilder#build()} from {@link #build()}.
         */
        public BankBuilder newBank() {
            BankBuilder bankBuilder = new BankBuilder(mNextBankId++);
            mBankBuilders.add(bankBuilder);
            return bankBuilder;
        }

        /**
         * Builds the {@link BankedTzIdSets}.
         */
        public BankedTzIdSets build() {
            List<Bank> banks = new ArrayList<>(mBankBuilders.size());
            for (BankBuilder bankBuilder : mBankBuilders) {
                banks.add(bankBuilder.build());
            }
            return new BankedTzIdSets(mStringsByIndex, banks);
        }

        /**
         * A builder of {@link Bank} instances.
         */
        public final class BankBuilder {

            private final List<TzIdSet> mTzIdSets = new ArrayList<>();

            private final int mId;

            BankBuilder(int id) {
                mId = id;
            }

            /**
             * Adds a new set of IDs to the bank. The IDs are the indexes of strings held at the
             * {@link BankedTzIdSets} level (see {@link BankedTzIdSets#getStringsByIndex()}.
             */
            public BankBuilder addTzIdSet(List<Integer> stringIds) {
                List<String> strings = new ArrayList<>(stringIds.size());
                for (int stringId : stringIds) {
                    String string = mStringsByIndex.get(stringId);
                    strings.add(string);
                }
                BankedTzIdSets.TzIdSet tzIdSet = new TzIdSet(stringIds, strings);
                mTzIdSets.add(tzIdSet);
                return this;
            }

            /** Builds and returns the {@link Bank}. */
            public Bank build() {
                return new Bank(mId, mTzIdSets);
            }
        }
    }

    /** An immutable bank of sets of time zone IDs. */
    public static class Bank {

        private final int mId;

        private final List<TzIdSet> mTzIdSets;

        Bank(int id, List<TzIdSet> tzIdSets) {
            mId = id;
            mTzIdSets = Collections.unmodifiableList(new ArrayList<>(tzIdSets));
        }

        public int getId() {
            return mId;
        }

        /**
         * Returns the {@link TzIdSet} with the specified index.
         */
        public TzIdSet getTzIdSet(int index) {
            return mTzIdSets.get(index);
        }

        public int getTzIdSetCount() {
            return mTzIdSets.size();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Bank bank = (Bank) o;
            return mId == bank.mId
                    && mTzIdSets.equals(bank.mTzIdSets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mId, mTzIdSets);
        }

        @Override
        public String toString() {
            return "Bank{"
                    + "mId=" + mId
                    + ", mTzIdSets=" + mTzIdSets
                    + '}';
        }
    }

    /** A set of time zone IDs. */
    public static class TzIdSet {

        private final List<Integer> mStringIds;

        private final List<String> mStrings;

        TzIdSet(List<Integer> stringIds, List<String> strings) {
            mStringIds = Collections.unmodifiableList(new ArrayList<>(stringIds));
            mStrings = Collections.unmodifiableList(new ArrayList<>(strings));
        }

        /**
         * Returns the IDs of the strings in this set, referencing
         * {@link BankedTzIdSets#getStringsByIndex()}.
         */
        public List<Integer> getStringIds() {
            return mStringIds;
        }

        /**
         * Returns the strings in this set.
         */
        public List<String> getTzIds() {
            return mStrings;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TzIdSet tzIdSet = (TzIdSet) o;
            return mStringIds.equals(tzIdSet.mStringIds)
                    && mStrings.equals(tzIdSet.mStrings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mStringIds, mStrings);
        }
    }
}
