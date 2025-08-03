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

package android.hdmicec.cts.error;

/**
 * CecClientWrapperException to be thrown when there are any issues related with the usage of {@link
 * HdmiCecClientWrapper}.
 */
public class CecClientWrapperException extends Exception {

    private ErrorCodes errorCode;

    public CecClientWrapperException(ErrorCodes errorCode) {
        super(errorCode.getExceptionMessage());
        this.errorCode = errorCode;
    }

    public CecClientWrapperException(ErrorCodes errorCode, String messageToBeAppend) {
        super(errorCode.getExceptionMessage() + messageToBeAppend);
        this.errorCode = errorCode;
    }

    public CecClientWrapperException(
            ErrorCodes errorCode, Throwable cause, String messageToBeAppend) {
        super(errorCode.getExceptionMessage() + messageToBeAppend, cause);
        this.errorCode = errorCode;
    }

    public CecClientWrapperException(ErrorCodes errorCode, Throwable cause) {
        super(errorCode.getExceptionMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCodes getErrorCode() {
        return this.errorCode;
    }
}
