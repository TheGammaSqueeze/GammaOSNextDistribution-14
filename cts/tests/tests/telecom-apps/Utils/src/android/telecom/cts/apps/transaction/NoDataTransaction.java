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

package android.telecom.cts.apps;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class NoDataTransaction extends BaseTransaction implements Parcelable {

    public NoDataTransaction(TestAppTransaction result) {
        mResult = result;
    }

    public NoDataTransaction(TestAppTransaction result, TestAppException exception) {
        mResult = result;
        mException = exception;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mResult, flags);
        dest.writeParcelable(mException, flags);
    }

    public static final Parcelable.Creator<NoDataTransaction> CREATOR = new Parcelable.Creator<>() {
        @Override
        public NoDataTransaction createFromParcel(Parcel source) {

            return new NoDataTransaction(

                    source.readParcelable(getClass().getClassLoader(),
                            TestAppTransaction.class),

                    source.readParcelable(getClass().getClassLoader(),
                            TestAppException.class));
        }

        @Override
        public NoDataTransaction[] newArray(int size) {
            return new NoDataTransaction[size];
        }
    };


}
