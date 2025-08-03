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
import android.telecom.CallEndpoint;

import androidx.annotation.NonNull;

public class CallEndpointTransaction extends BaseTransaction implements Parcelable {

    public CallEndpoint getCallEndpoint() {
        return mCallEndpoint;
    }

    CallEndpoint mCallEndpoint;

    public CallEndpointTransaction(TestAppTransaction result, CallEndpoint callEndpoint) {
        mResult = result;
        mCallEndpoint = callEndpoint;
    }

    public CallEndpointTransaction(TestAppTransaction result, TestAppException exception) {
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
        if (isTransactionSuccessful()) {
            dest.writeParcelable(mCallEndpoint, flags);
        } else {
            dest.writeParcelable(mException, flags);
        }
    }

    public static final Creator<CallEndpointTransaction> CREATOR = new Creator<>() {
        @Override
        public CallEndpointTransaction createFromParcel(Parcel source) {
            TestAppTransaction transactionResult =
                    source.readParcelable(getClass().getClassLoader(),
                            TestAppTransaction.class);

            if (transactionResult != null
                    && transactionResult.equals(TestAppTransaction.Success)) {
                return new CallEndpointTransaction(
                        transactionResult,
                        source.readParcelable(getClass().getClassLoader(),
                                CallEndpoint.class));
            } else {
                return new CallEndpointTransaction(
                        transactionResult,
                        source.readParcelable(getClass().getClassLoader(),
                                TestAppException.class));
            }
        }

        @Override
        public CallEndpointTransaction[] newArray(int size) {
            return new CallEndpointTransaction[size];
        }
    };


}
