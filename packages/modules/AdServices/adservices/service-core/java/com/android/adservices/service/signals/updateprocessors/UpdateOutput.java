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

package com.android.adservices.service.signals.updateprocessors;

import androidx.annotation.NonNull;

import com.android.adservices.data.signals.DBProtectedSignal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The output of an UpdateProcessor strategy */
public class UpdateOutput {
    private final List<DBProtectedSignal.Builder> mToAdd = new ArrayList<>();
    private final List<DBProtectedSignal> mToRemove = new ArrayList<>();
    private final Set<ByteBuffer> mKeysTouched = new HashSet<>();
    private UpdateEncoderEvent mUpdateEncoderEvent;

    /** The list of signals from the JSON to add. */
    public List<DBProtectedSignal.Builder> getToAdd() {
        return mToAdd;
    }

    /** The list of signals from the JSON to remove. */
    public List<DBProtectedSignal> getToRemove() {
        return mToRemove;
    }

    /**
     * The list of keys this process modified or could have modified. It is important that
     * processors add keys that they considered, but did not take action on, here to prevent
     * conflicts with other processors.
     */
    public Set<ByteBuffer> getKeysTouched() {
        return mKeysTouched;
    }

    /** Sets the {@link UpdateEncoderEvent} for the UpdateOutput, there can be only one */
    public void setUpdateEncoderEvent(@NonNull UpdateEncoderEvent event) {
        mUpdateEncoderEvent = event;
    }

    /**
     * @return an {@link UpdateEncoderEvent} for the UpdateOutput, there can be only one
     */
    public UpdateEncoderEvent getUpdateEncoderEvent() {
        return mUpdateEncoderEvent;
    }
}
