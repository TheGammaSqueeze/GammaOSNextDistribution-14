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
package com.android.cts.devicepolicy;

import android.app.admin.DevicePolicyManager;

//TODO(b/174859111): move to automotive-only section
/**
 * Represents an operation safety event received by a
 * {@link android.app.admin.DeviceAdminReceiver#onOperationSafetyStateChanged(
 * android.content.Context, int, boolean)}.
 *
 * <p>NOTE: doesn't implement {@code Parcelable} because it needs to cross process boundaries on
 * automotive, which trows a {@code ClassNotFoundException} in the receiving end - it't not worth
 * to fix that...
 */
public final class OperationSafetyChangedEvent {

    public final int reason;
    public final boolean isSafe;

    public OperationSafetyChangedEvent(int reason, boolean isSafe) {
        this.reason = reason;
        this.isSafe = isSafe;
    }

    @Override
    public String toString() {
        return "OperationSafetyChangedEvent["
            + DevicePolicyManager.operationSafetyReasonToString(reason) + ": "
            + (isSafe ? "SAFE" : "UNSAFE") + ']';
    }
}
