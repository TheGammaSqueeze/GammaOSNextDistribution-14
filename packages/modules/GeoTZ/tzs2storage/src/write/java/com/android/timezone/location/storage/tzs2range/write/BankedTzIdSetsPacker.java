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

package com.android.timezone.location.storage.tzs2range.write;

import com.android.timezone.location.storage.tzs2range.BankedTzIdSets;
import com.android.timezone.location.storage.tzs2range.TzS2Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A simple generator of {@link BankedTzIdSets}.
 */
public final class BankedTzIdSetsPacker {

    private final Map<String, Integer> mStringToStringId = new HashMap<>();

    private final List<String> mStringIdToString = new ArrayList<>();

    private final List<BankHelper> mBankHelpers = new ArrayList<>();

    private final int mMaxBankSize;

    private int mNextBankId = 0;

    private int mNextStringId = 0;

    /**
     * Creates a {@link BankedTzIdSetsPacker} that keeps banks to {@code maxBankSize} or smaller.
     */
    public BankedTzIdSetsPacker(int maxBankSize) {
        mMaxBankSize = maxBankSize;
    }

    /**
     * Adds multiple TZ ID sets for a chunk of time zone geo data. The sets are already expected to
     * be unique. The sets are ordered so differently ordered sets containing the same strings will
     * be kept distinct. The packer will record any hitherto unknown IDs and attempt to pack the
     * sets into an existing bank or will create a new one. The {@link BankHelper} for the bank
     * found / created will be returned.
     *
     * <p>If there are too many sets or too many banks, an {@link IllegalArgumentException} will be
     * thrown.
     */
    public BankHelper addTzIdSets(List<List<String>> uniqueIdSets) {
        if (uniqueIdSets.size() < 1 || uniqueIdSets.size() > mMaxBankSize) {
            throw new IllegalArgumentException(
                    "uniqueIdSets.size()=" + uniqueIdSets.size() + " < 1 or > " + mMaxBankSize);
        }

        // Add new strings to the string pool.
        for (List<String> stringSets : uniqueIdSets) {
            for (String string : stringSets) {
                if (!mStringToStringId.containsKey(string)) {
                    mStringToStringId.put(string, mNextStringId++);
                    mStringIdToString.add(string);
                }
            }
        }

        // Packing could be better, but it doesn't need to be optimal.
        for (BankHelper bankHelper : mBankHelpers) {
            if (bankHelper.offer(uniqueIdSets)) {
                return bankHelper;
            }
        }

        // None of the existing banks can accept the sets.
        BankHelper bankHelper = new BankHelper(mNextBankId++);
        mBankHelpers.add(bankHelper);
        if (!bankHelper.offer(uniqueIdSets)) {
            // A new bank should be able to accept the sets.
            throw new IllegalStateException(
                    "uniqueIdSets=" + uniqueIdSets + " cannot be stored in a new bank");
        }
        return bankHelper;
    }

    /**
     * Creates the {@link BankedTzIdSets}.
     */
    public BankedTzIdSets create() {
        BankedTzIdSets.Builder builder = new BankedTzIdSets.Builder();
        builder.setStringsByIndex(mStringIdToString);
        for (BankHelper bankHelper : mBankHelpers) {
            BankedTzIdSets.Builder.BankBuilder bank = builder.newBank();
            List<List<Integer>> tzIdSets = bankHelper.getTzIdSets();
            for (List<Integer> tzIdSet : tzIdSets) {
                bank.addTzIdSet(tzIdSet);
            }
        }
        return builder.build();
    }

    /**
     * Information about a Bank that will be used to store the TZ ID sets passed to
     * {@link BankedTzIdSetsPacker#addTzIdSets(List)}. It can be used to obtain the bank's ID and
     * the set ID for a given TZ ID set.
     */
    public final class BankHelper {

        private final int mId;

        private final Map<List<Integer>, Integer> mSetToSetId = new HashMap<>();

        private final List<List<Integer>> mSetIdToSet = new ArrayList<>();

        private int mNextSetId = 0;

        BankHelper(int id) {
            mId = id;
        }

        public int getId() {
            return mId;
        }

        private void addAll(List<List<String>> uniqueTzIdStringSets) {
            for (List<String> uniqueTzIdStringIdSet : uniqueTzIdStringSets) {
                List<Integer> uniqueTzIdIntSet = getStringIdList(uniqueTzIdStringIdSet);

                if (mSetToSetId.containsKey(uniqueTzIdIntSet)) {
                    throw new IllegalArgumentException("setToSetId=" + mSetIdToSet
                            + " already contains uniqueTzIdIntSet=" + uniqueTzIdIntSet);
                }
                mSetToSetId.put(uniqueTzIdIntSet, mNextSetId++);
                mSetIdToSet.add(uniqueTzIdIntSet);
                if (mNextSetId > mMaxBankSize) {
                    throw new IllegalStateException("nextSetId=" + mNextSetId
                            + " exceeds maxBankSize=" + mMaxBankSize);
                }
            }
        }

        private List<Integer> getStringIdList(List<String> uniqueTzIdStringIdSet) {
            if (uniqueTzIdStringIdSet.size() < 1) {
                throw new IllegalArgumentException("uniqueTzIdStringIdSet.size() must >= 1");
            }
            List<Integer> uniqueTzIdIntSet = new ArrayList<>(uniqueTzIdStringIdSet.size());
            for (String string : uniqueTzIdStringIdSet) {
                Integer stringId = mStringToStringId.get(string);
                if (stringId == null) {
                    throw new IllegalStateException("Unable to find index for string=" + string);
                }
                uniqueTzIdIntSet.add(stringId);
            }
            return uniqueTzIdIntSet;
        }

        int getTzIdSetId(List<String> tzIdStringSet) {
            List<Integer> tzIdIntSet = getStringIdList(tzIdStringSet);
            Integer setId = mSetToSetId.get(tzIdIntSet);
            if (setId == null) {
                return -1;
            }
            return setId;
        }

        List<List<Integer>> getTzIdSets() {
            return Collections.unmodifiableList(new ArrayList<>(mSetIdToSet));
        }

        boolean offer(List<List<String>> uniqueIdSets) {
            List<List<String>> newSets = new ArrayList<>();
            for (List<String> uniqueIdSet : uniqueIdSets) {
                List<Integer> uniqueIdSetIntegers = getStringIdList(uniqueIdSet);
                if (!mSetToSetId.containsKey(uniqueIdSetIntegers)) {
                    newSets.add(uniqueIdSet);
                }
            }
            if (mSetToSetId.size() + newSets.size() > mMaxBankSize) {
                return false;
            }
            addAll(newSets);
            return true;
        }
    }

    /**
     * Obtains the unique TZ ID sets from a list of {@link TzS2Range} files. TZ ID sets are
     * considered ordered so two sets containing the same IDs in different orders are considered
     * distinct.
     */
    public static List<List<String>> extractUniqueTzIdSets(List<TzS2Range> ranges) {
        Set<List<String>> sets = new HashSet<>();
        for (TzS2Range range : ranges) {
            sets.add(range.getTzIdSet());
        }
        return new ArrayList<>(sets);
    }
}
