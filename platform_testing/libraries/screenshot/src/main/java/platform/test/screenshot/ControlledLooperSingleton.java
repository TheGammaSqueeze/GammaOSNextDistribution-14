/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.test.internal.platform.os;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.test.internal.platform.ServiceLoaderWrapper;

/** Helper class for retrieving the ControlledLooper instance to use */
@RestrictTo(Scope.LIBRARY_GROUP)
public class ControlledLooperSingleton {

    private ControlledLooperSingleton() {}

    private static class Holder {
        private static final ControlledLooper INSTANCE =
                ServiceLoaderWrapper.loadSingleService(
                        ControlledLooper.class, () -> ControlledLooper.NO_OP_CONTROLLED_LOOPER);
    }

    public static ControlledLooper getInstance() {
        return Holder.INSTANCE;
    }
}
