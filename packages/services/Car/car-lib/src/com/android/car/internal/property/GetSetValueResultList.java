/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.internal.property;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public final class GetSetValueResultList extends LargeParcelableList<GetSetValueResult> {

    public GetSetValueResultList(@NonNull List<GetSetValueResult> getSetValueResultsList) {
        super(getSetValueResultsList);
    }

    private GetSetValueResultList(@NonNull Parcel source) {
        super(source);
    }

    @Override
    protected Creator<GetSetValueResult> getCreator() {
        return GetSetValueResult.CREATOR;
    }

    public static final @NonNull Creator<GetSetValueResultList> CREATOR = new Creator<>() {
        @Override
        public GetSetValueResultList createFromParcel(@Nullable Parcel in) {
            if (in == null) {
                return new GetSetValueResultList(new ArrayList<>());
            }
            return new GetSetValueResultList(in);
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public GetSetValueResultList[] newArray(int size) {
            return new GetSetValueResultList[size];
        }
    };
}
