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

package com.google.android.iwlan.epdg;

import com.google.android.iwlan.IwlanError;

/** A state machine that infers the current IkeSession state. */
enum IkeSessionState {
    NO_IKE_SESSION {
        @Override
        public int getErrorType() {
            return IwlanError.NO_ERROR;
        }
    },
    IKE_SESSION_INIT_IN_PROGRESS {
        @Override
        public int getErrorType() {
            return IwlanError.IKE_INIT_TIMEOUT;
        }
    },
    IKE_MOBILITY_IN_PROGRESS {
        @Override
        public int getErrorType() {
            return IwlanError.IKE_MOBILITY_TIMEOUT;
        }
    },
    CHILD_SESSION_OPENED {
        @Override
        public int getErrorType() {
            return IwlanError.IKE_DPD_TIMEOUT;
        }
    };

    /**
     * Called when IkeSession report error with IkeIOException, check current IkeSession state and
     * return corresponding time out error.
     *
     * @return NO_ERROR or IWLAN IKE time out error
     */
    public abstract int getErrorType();
}
