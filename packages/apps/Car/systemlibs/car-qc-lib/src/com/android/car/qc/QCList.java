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

package com.android.car.qc;

import android.app.PendingIntent;
import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapping quick controls element that contains QCRow elements.
 */
public class QCList extends QCItem {
    private final List<QCRow> mRows;

    public QCList(@NonNull List<QCRow> rows) {
        super(QC_TYPE_LIST);
        mRows = Collections.unmodifiableList(rows);
    }

    public QCList(@NonNull Parcel in) {
        super(in);
        int rowCount = in.readInt();
        List<QCRow> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(QCRow.CREATOR.createFromParcel(in));
        }
        mRows = Collections.unmodifiableList(rows);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mRows.size());
        for (QCRow row : mRows) {
            row.writeToParcel(dest, flags);
        }
    }

    @Override
    public PendingIntent getPrimaryAction() {
        return null;
    }

    @Override
    public PendingIntent getDisabledClickAction() {
        return null;
    }

    @NonNull
    public List<QCRow> getRows() {
        return mRows;
    }

    public static Creator<QCList> CREATOR = new Creator<QCList>() {
        @Override
        public QCList createFromParcel(Parcel source) {
            return new QCList(source);
        }

        @Override
        public QCList[] newArray(int size) {
            return new QCList[size];
        }
    };

    /**
     * Builder for {@link QCList}.
     */
    public static class Builder {
        private final List<QCRow> mRows = new ArrayList<>();

        /**
         * Adds a {@link QCRow} to the list.
         */
        public Builder addRow(@NonNull QCRow row) {
            mRows.add(row);
            return this;
        }

        /**
         * Builds the final {@link QCList}.
         */
        public QCList build() {
            return new QCList(mRows);
        }
    }
}
