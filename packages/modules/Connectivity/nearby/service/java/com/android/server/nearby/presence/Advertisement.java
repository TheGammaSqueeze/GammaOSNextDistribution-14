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

package com.android.server.nearby.presence;

import android.annotation.Nullable;
import android.nearby.BroadcastRequest;
import android.nearby.PresenceCredential;

import java.util.ArrayList;
import java.util.List;

/** A Nearby Presence advertisement to be advertised. */
public abstract class Advertisement {

    @BroadcastRequest.BroadcastVersion
    int mVersion = BroadcastRequest.PRESENCE_VERSION_UNKNOWN;
    int mLength;
    @PresenceCredential.IdentityType int mIdentityType;
    byte[] mIdentity;
    byte[] mSalt;
    List<Integer> mActions;

    /** Serialize an {@link Advertisement} object into bytes. */
    @Nullable
    public byte[] toBytes() {
        return new byte[0];
    }

    /** Returns the length of the advertisement. */
    public int getLength() {
        return mLength;
    }

    /** Returns the version in the advertisement. */
    @BroadcastRequest.BroadcastVersion
    public int getVersion() {
        return mVersion;
    }

    /** Returns the identity type in the advertisement. */
    @PresenceCredential.IdentityType
    public int getIdentityType() {
        return mIdentityType;
    }

    /** Returns the identity bytes in the advertisement. */
    public byte[] getIdentity() {
        return mIdentity.clone();
    }

    /** Returns the salt of the advertisement. */
    public byte[] getSalt() {
        return mSalt.clone();
    }

    /** Returns the actions in the advertisement. */
    public List<Integer> getActions() {
        return new ArrayList<>(mActions);
    }
}
