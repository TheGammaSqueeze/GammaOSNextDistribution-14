/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.data.kanon;

import android.annotation.IntDef;

import com.android.adservices.service.kanon.KAnonMessageEntity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class KAnonMessageConstants {
    /** IntDef to classify different Status. */
    @IntDef(
            value = {
                MessageStatus.NOT_PROCESSED,
                MessageStatus.SIGNED,
                MessageStatus.JOINED,
                MessageStatus.FAILED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MessageStatus {
        int NOT_PROCESSED = 0;
        int SIGNED = 1;
        int JOINED = 2;
        int FAILED = 3;
    }

    /** Converts {@link KAnonMessageEntity.KanonMessageEntityStatus} to {@link MessageStatus}. */
    @MessageStatus
    public static int fromKAnonMessageEntityStatus(
            @KAnonMessageEntity.KanonMessageEntityStatus int status) {
        switch (status) {
            case KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED:
                return MessageStatus.NOT_PROCESSED;
            case KAnonMessageEntity.KanonMessageEntityStatus.SIGNED:
                return MessageStatus.SIGNED;
            case KAnonMessageEntity.KanonMessageEntityStatus.JOINED:
                return MessageStatus.JOINED;
            case KAnonMessageEntity.KanonMessageEntityStatus.FAILED:
                return MessageStatus.FAILED;
            default:
                throw new IllegalStateException("Invalid status: " + status);
        }
    }

    /** Converts {@link MessageStatus} to {@link KAnonMessageEntity.KanonMessageEntityStatus}. */
    @KAnonMessageEntity.KanonMessageEntityStatus
    public static int toKAnonMessageEntityStatus(@MessageStatus int status) {
        switch (status) {
            case MessageStatus.NOT_PROCESSED:
                return KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED;
            case MessageStatus.SIGNED:
                return KAnonMessageEntity.KanonMessageEntityStatus.SIGNED;
            case MessageStatus.JOINED:
                return KAnonMessageEntity.KanonMessageEntityStatus.JOINED;
            case MessageStatus.FAILED:
                return KAnonMessageEntity.KanonMessageEntityStatus.FAILED;
            default:
                throw new IllegalStateException("Invalid status: " + status);
        }
    }
}
