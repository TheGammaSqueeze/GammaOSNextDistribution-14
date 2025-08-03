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

package com.android.server.wifi.util;

import android.annotation.Nullable;
import android.net.wifi.IActionListener;
import android.os.RemoteException;

/**
 * Simple wrapper around {@link IActionListener} that triggers onSuccess/onFailure
 * on a best effort basis.
 */
public class ActionListenerWrapper {
    private final IActionListener mListener;

    public ActionListenerWrapper(@Nullable IActionListener listener) {
        mListener = listener;
    }

    /** Trigger the failure callback with a reason if the listener is non-null. */
    public void sendFailure(int reason) {
        if (mListener != null) {
            try {
                mListener.onFailure(reason);
            } catch (RemoteException e) {
                // no-op (client may be dead, nothing to be done)
            }
        }
    }

    /** Trigger the success callback with a reason if the listener is non-null. */
    public void sendSuccess() {
        if (mListener != null) {
            try {
                mListener.onSuccess();
            } catch (RemoteException e) {
                // no-op (client may be dead, nothing to be done)
            }
        }
    }
}
