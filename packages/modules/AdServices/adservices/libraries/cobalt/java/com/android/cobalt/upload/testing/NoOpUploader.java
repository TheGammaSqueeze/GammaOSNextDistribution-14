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

package com.android.cobalt.upload.testing;

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableList;

import android.annotation.NonNull;

import com.android.cobalt.upload.Uploader;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * An uploader that doesn't upload, just tracks EncryptedMessages it receives for the sake of
 * testing.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public final class NoOpUploader implements Uploader {
    private final ImmutableList.Builder<EncryptedMessage> mEncryptedMessages;

    /* The number of  times uploadDone was called. */
    private int mUploadDoneCount;

    public NoOpUploader() {
        mEncryptedMessages = ImmutableList.builder();
        mUploadDoneCount = 0;
    }

    /** Store the provided encrypted message. */
    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void upload(@NonNull EncryptedMessage encryptedMessage) {
        mEncryptedMessages.add(encryptedMessage);
    }

    /** Record the method was called. */
    @Override
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void uploadDone() {
        ++mUploadDoneCount;
    }

    /**
     * Get the EncryptedMessages sent through the logger as Envelopes.
     *
     * <p>This assumes encryption was a no-op, though this class could be amended to take a
     * decryption implementation to avoid making this assumption.
     */
    public ImmutableList<Envelope> getSentEnvelopes() {
        return mEncryptedMessages.build().stream()
                .map(
                        e -> {
                            try {
                                return Envelope.parseFrom(e.getCiphertext());
                            } catch (InvalidProtocolBufferException x) {
                                return Envelope.getDefaultInstance();
                            }
                        })
                .collect(toImmutableList());
    }

    public int getUploadDoneCount() {
        return mUploadDoneCount;
    }
}
