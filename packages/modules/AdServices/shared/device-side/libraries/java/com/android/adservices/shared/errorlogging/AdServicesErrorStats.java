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

package com.android.adservices.shared.errorlogging;

import com.google.auto.value.AutoValue;

/**
 * Class which contains data for AdServices Error logging. This is used by AdServicesErrorReported
 * atom.
 */
@AutoValue
public abstract class AdServicesErrorStats {
    /** @return error/exception code. */
    public abstract int getErrorCode();

    /** @return name of the PPAPI where the error occurred. */
    public abstract int getPpapiName();

    /** @return name of the class where we catch the exception or log the error. */
    public abstract String getClassName();

    /** @return name of the method where we catch the exception or log the error. */
    public abstract String getMethodName();

    /** @return line number where we catch the exception or log the error. */
    public abstract int getLineNumber();

    /** @return the fully qualified name of the last encountered exception. */
    public abstract String getLastObservedExceptionName();

    /** @return builder populating default values */
    public static AdServicesErrorStats.Builder builder() {
        return new AutoValue_AdServicesErrorStats.Builder()
                .setClassName("")
                .setMethodName("")
                .setLastObservedExceptionName("")
                .setLineNumber(0)
                .setPpapiName(0);
    }

    /** Builder class for {@link AdServicesErrorStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets error/exception code. */
        public abstract AdServicesErrorStats.Builder setErrorCode(int errorCode);

        /** Sets name of the PPAPI where the error occurred. */
        public abstract AdServicesErrorStats.Builder setPpapiName(int ppapiName);

        /** Sets name of the class where we catch the exception or log the error. */
        public abstract AdServicesErrorStats.Builder setClassName(String className);

        /** Sets name of the method where we catch the exception or log the error. */
        public abstract AdServicesErrorStats.Builder setMethodName(String methodName);

        /** Sets line number where we catch the exception or log the error. */
        public abstract AdServicesErrorStats.Builder setLineNumber(int lineNumber);

        /** Sets the fully qualified name of the last encountered exception. */
        public abstract AdServicesErrorStats.Builder setLastObservedExceptionName(
                String lastExceptionName);

        /** build for {@link AdServicesErrorStats}. */
        public abstract AdServicesErrorStats build();
    }
}
