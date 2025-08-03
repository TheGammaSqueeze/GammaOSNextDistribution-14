/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.text.Spannable;
import android.text.format.DateUtils;

public class HistoryItem {

    private long mEvaluatorIndex;
    /** Date in millis */
    private long mTimeInMillis;
    private Spannable mFormula;

    /** This is true only for the "empty history" view. */
    private final boolean mIsEmpty;

    public HistoryItem(long evaluatorIndex, long millis, Spannable formula) {
        mEvaluatorIndex = evaluatorIndex;
        mTimeInMillis = millis;
        mFormula = formula;
        mIsEmpty = false;
    }

    public long getEvaluatorIndex() {
        return mEvaluatorIndex;
    }

    public HistoryItem() {
        mIsEmpty = true;
    }

    public boolean isEmptyView() {
        return mIsEmpty;
    }

    /**
     * @return String in format "n days ago"
     * For n > 7, the date is returned.
     */
    public CharSequence getDateString() {
        return DateUtils.getRelativeTimeSpanString(mTimeInMillis, System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    public long getTimeInMillis() {
        return mTimeInMillis;
    }

    public Spannable getFormula() {
        return mFormula;
    }
}
