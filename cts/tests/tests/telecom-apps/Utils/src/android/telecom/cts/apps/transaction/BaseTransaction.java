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

import java.util.Objects;


public abstract class BaseTransaction {
    public TestAppTransaction getResult() {
        return mResult;
    }

    public TestAppException getTestAppException() {
        return mException;
    }

    public boolean isTransactionSuccessful() {
        return mResult.equals(TestAppTransaction.Success);
    }

    TestAppTransaction mResult;
    TestAppException mException;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{ NoDataTransaction: [mResult: ")
                .append(mResult)
                .append("], [mException: ")
                .append(mException)
                .append("]  }");

        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof BaseTransaction)) {
            return false;
        }
        BaseTransaction that = (BaseTransaction) obj;
        return this.mResult == that.mResult
                && this.mException == that.mException;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(mResult, mException);
    }
}
