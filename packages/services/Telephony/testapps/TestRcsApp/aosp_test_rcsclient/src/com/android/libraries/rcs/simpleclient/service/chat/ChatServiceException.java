/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.service.chat;

import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class defines an exception that can be thrown during the operation in {@link
 * MinimalCpmChatService}
 */
public final class ChatServiceException extends Exception {

    public static final int CODE_ERROR_UNSPECIFIED = 0;
    public static final int CODE_ERROR_SEND_MESSAGE_FAILED = 1;
    private int mCode = CODE_ERROR_UNSPECIFIED;

    /**
     * A new {@link ChatServiceException} with an unspecified {@link ErrorCode} code.
     *
     * @param message an optional message to detail the error condition more specifically.
     */
    public ChatServiceException(@Nullable String message) {
        super(getMessage(message, CODE_ERROR_UNSPECIFIED));
    }

    /**
     * A new {@link ChatServiceException} that includes an {@link ErrorCode} error code.
     *
     * @param message an optional message to detail the error condition more specifically.
     */
    public ChatServiceException(@Nullable String message, @ErrorCode int code) {
        super(getMessage(message, code));
        mCode = code;
    }

    /**
     * A new {@link ChatServiceException} that includes an {@link ErrorCode} error code and a {@link
     * Throwable} that contains the original error that was thrown to lead to this Exception.
     *
     * @param message an optional message to detail the error condition more specifically.
     * @param cause   the {@link Throwable} that caused this {@link ChatServiceException} to be
     *                created.
     */
    public ChatServiceException(
            @Nullable String message, @ErrorCode int code, @Nullable Throwable cause) {
        super(getMessage(message, code), cause);
        mCode = code;
    }

    private static String getMessage(String message, int code) {
        StringBuilder builder;
        if (!TextUtils.isEmpty(message)) {
            builder = new StringBuilder(message);
            builder.append(" (code: ");
            builder.append(code);
            builder.append(")");
            return builder.toString();
        } else {
            return "code: " + code;
        }
    }

    @ErrorCode
    public int getCode() {
        return mCode;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            CODE_ERROR_UNSPECIFIED,
    })
    public @interface ErrorCode {
    }
}
