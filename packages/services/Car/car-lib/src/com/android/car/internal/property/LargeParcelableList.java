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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.LargeParcelableBase;

import java.util.List;

/**
 * Generic class for LargeParcelable lists.
 * Caller only needs to override {@code getCreator} method. For example:
 *
 * <pre> {@code
 * public abstract class AList extends LargeParcelableList {
 *      public AList(List<A> aList) {
 *          super(AList)
 *      }
 *
 *      private AList(parcel in) {
 *          super(in)
 *      }
 *
 *      @Override
 *      protected Creator<A> getCreator() {
 *          return A.CREATOR;
 *      }
 * }
 * }
 *
 * @param <T> The type for the element stored in this list
 *
 * @hide
 */
public abstract class LargeParcelableList<T extends Parcelable> extends LargeParcelableBase {
    private List<T> mList;

    public LargeParcelableList(List<T> list) {
        mList = list;
    }

    protected LargeParcelableList(Parcel source) {
        super(source);
    }

    protected abstract Creator<T> getCreator();

    @Override
    protected void serialize(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(mList);
    }

    @Override
    protected void serializeNullPayload(@NonNull Parcel dest) {
        dest.writeTypedList(null);
    }

    @Override
    protected void deserialize(@NonNull Parcel src) {
        mList = src.createTypedArrayList(getCreator());
    }

    public List<T> getList() {
        return mList;
    }
}
