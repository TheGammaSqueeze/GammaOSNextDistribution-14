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
import android.telecom.PhoneAccount;

import androidx.annotation.NonNull;

public class PhoneAccountTransaction extends BaseTransaction implements Parcelable {

    public PhoneAccount getPhoneAccount() {
        return mPhoneAccount;
    }

    PhoneAccount mPhoneAccount;

    public PhoneAccountTransaction(TestAppTransaction result, PhoneAccount phoneAccount) {
        mResult = result;
        mPhoneAccount = phoneAccount;
    }

    public PhoneAccountTransaction(TestAppTransaction result, TestAppException exception) {
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
            dest.writeParcelable(mPhoneAccount, flags);
        } else {
            dest.writeParcelable(mException, flags);
        }
    }

    public static final Creator<PhoneAccountTransaction> CREATOR = new Creator<>() {
        @Override
        public PhoneAccountTransaction createFromParcel(Parcel source) {
            TestAppTransaction transactionResult =
                    source.readParcelable(getClass().getClassLoader(),
                            TestAppTransaction.class);

            if (transactionResult != null
                    && transactionResult.equals(TestAppTransaction.Success)) {
                return new PhoneAccountTransaction(
                        transactionResult,
                        source.readParcelable(getClass().getClassLoader(),
                                PhoneAccount.class));
            } else {
                return new PhoneAccountTransaction(
                        transactionResult,
                        source.readParcelable(getClass().getClassLoader(),
                                TestAppException.class));
            }
        }

        @Override
        public PhoneAccountTransaction[] newArray(int size) {
            return new PhoneAccountTransaction[size];
        }
    };
}
