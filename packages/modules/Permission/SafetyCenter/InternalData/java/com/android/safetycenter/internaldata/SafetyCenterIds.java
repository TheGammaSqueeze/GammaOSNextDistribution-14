/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.safetycenter.internaldata;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

/** A class to facilitate working with Safety Center IDs. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterIds {

    private static final int ENCODING_FLAGS = Base64.NO_WRAP | Base64.URL_SAFE;

    private SafetyCenterIds() {}

    /**
     * Converts a String to a {@link SafetyCenterEntryId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterEntryId}.
     */
    public static SafetyCenterEntryId entryIdFromString(String encoded) {
        return decodeToProto(SafetyCenterEntryId.parser(), encoded);
    }

    /**
     * Converts a String to a {@link SafetyCenterIssueId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterIssueId}.
     */
    public static SafetyCenterIssueId issueIdFromString(String encoded) {
        return decodeToProto(SafetyCenterIssueId.parser(), encoded);
    }

    /**
     * Converts a String to a {@link SafetyCenterIssueKey}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterIssueKey}.
     */
    public static SafetyCenterIssueKey issueKeyFromString(String encoded) {
        return decodeToProto(SafetyCenterIssueKey.parser(), encoded);
    }

    /**
     * Converts a String to a {@link SafetyCenterIssueActionId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterIssueActionId}.
     */
    public static SafetyCenterIssueActionId issueActionIdFromString(String encoded) {
        return decodeToProto(SafetyCenterIssueActionId.parser(), encoded);
    }

    /** Encodes a Safety Center id to a String. */
    public static String encodeToString(MessageLite message) {
        return Base64.encodeToString(message.toByteArray(), ENCODING_FLAGS);
    }

    /**
     * Converts a {@link SafetyCenterIssueKey} to a readable string.
     *
     * <p>This is necessary as the implementation of {@link #toString()} for Java lite protos is
     * optimized in production builds and does not return a user-readable output.
     */
    public static String toUserFriendlyString(SafetyCenterIssueKey safetyCenterIssueKey) {
        return "SafetyCenterIssueKey{safetySourceId='"
                + safetyCenterIssueKey.getSafetySourceId()
                + "', safetySourceIssueId='"
                + safetyCenterIssueKey.getSafetySourceIssueId()
                + "', userId="
                + safetyCenterIssueKey.getUserId()
                + "}";
    }

    /**
     * Converts a {@link SafetyCenterIssueId} to a readable string.
     *
     * <p>This is necessary as the implementation of {@link #toString()} for Java lite protos is
     * optimized in production builds and does not return a user-readable output.
     */
    public static String toUserFriendlyString(SafetyCenterIssueId safetyCenterIssueId) {
        return "SafetyCenterIssueId{safetyCenterIssueKey="
                + toUserFriendlyString(safetyCenterIssueId.getSafetyCenterIssueKey())
                + ", issueTypeId='"
                + safetyCenterIssueId.getIssueTypeId()
                + "', taskId="
                + safetyCenterIssueId.getTaskId()
                + "}";
    }

    /**
     * Converts a {@link SafetyCenterIssueActionId} to a readable string.
     *
     * <p>This is necessary as the implementation of {@link #toString()} for Java lite protos is
     * optimized in production builds and does not return a user-readable output.
     */
    public static String toUserFriendlyString(SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return "SafetyCenterIssueActionId{safetyCenterIssueKey="
                + toUserFriendlyString(safetyCenterIssueActionId.getSafetyCenterIssueKey())
                + ", safetySourceIssueActionId='"
                + safetyCenterIssueActionId.getSafetySourceIssueActionId()
                + "'}";
    }

    private static <T extends MessageLite> T decodeToProto(Parser<T> parser, String encoded) {
        try {
            return parser.parseFrom(Base64.decode(encoded, ENCODING_FLAGS));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(
                    "Invalid ID: "
                            + encoded
                            + " couldn't be parsed with "
                            + parser.getClass().getSimpleName());
        }
    }
}
