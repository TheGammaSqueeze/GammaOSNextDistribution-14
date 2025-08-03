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

package com.android.systemui.car.userpicker;

import android.annotation.IntDef;

import androidx.annotation.NonNull;

import com.android.systemui.car.userpicker.UserPickerController.Callbacks;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class HeaderState {
    static final int HEADER_STATE_LOGOUT = 1;
    static final int HEADER_STATE_CHANGE_USER = 2;

    @IntDef(prefix = { "HEADER_STATE_" }, value = {
            HEADER_STATE_LOGOUT,
            HEADER_STATE_CHANGE_USER,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface HeaderStateType {}

    private Callbacks mCallbacks;

    private int mState;

    HeaderState(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    void setState(@HeaderStateType int state) {
        if (mState != state) {
            mState = state;
            mCallbacks.onHeaderStateChanged(this);
        }
    }

    @HeaderStateType int getState() {
        return mState;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append("@").append(Integer.toHexString(hashCode()));
        sb.append(" [").append("mState=").append(stateToString(mState)).append(" mCallbacks=")
                .append(mCallbacks).append("]");
        return sb.toString();
    }

    private String stateToString(int state) {
        switch (state) {
            case HEADER_STATE_CHANGE_USER:
                return "HEADER_STATE_CHANGE_USER";
            case HEADER_STATE_LOGOUT:
                return "HEADER_STATE_LOGOUT";
            default:
                return null;
        }
    }
}
