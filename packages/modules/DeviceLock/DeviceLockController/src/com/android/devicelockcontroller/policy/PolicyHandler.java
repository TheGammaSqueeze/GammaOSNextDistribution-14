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

package com.android.devicelockcontroller.policy;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A PolicyHandler class is responsible for setting certain policy for a certain state.
 */
interface PolicyHandler {

    default ListenableFuture<Boolean> onProvisioned() {
        return Futures.immediateFuture(true);
    }

    default ListenableFuture<Boolean> onProvisionInProgress() {
        return Futures.immediateFuture(true);
    }

    default ListenableFuture<Boolean> onProvisionPaused() {
        return Futures.immediateFuture(true);
    }

    default ListenableFuture<Boolean> onProvisionFailed() {
        return Futures.immediateFuture(true);
    }

    default ListenableFuture<Boolean> onLocked() {
        return Futures.immediateFuture(true);
    }

    default ListenableFuture<Boolean> onUnlocked() {
        return Futures.immediateFuture(true);
    }

    default ListenableFuture<Boolean> onCleared() {
        return Futures.immediateFuture(true);
    }
}
