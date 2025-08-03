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

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.widget.LockscreenCredential;

public class LockscreenCredentialCompat {
    LockscreenCredential mLockscreenCredential;

    LockscreenCredentialCompat(LockscreenCredential lockscreenCredential) {
        mLockscreenCredential = lockscreenCredential;
    }

    public static LockscreenCredentialCompat createPinOrNone(@Nullable CharSequence pin) {
        return new LockscreenCredentialCompat(LockscreenCredential.createPinOrNone(pin));
    }

    public static LockscreenCredentialCompat createPin(@NonNull CharSequence pin) {
        return new LockscreenCredentialCompat(LockscreenCredential.createPin(pin));
    }

    public static LockscreenCredentialCompat createNone() {
        return new LockscreenCredentialCompat(LockscreenCredential.createNone());
    }

    public boolean isNone() {
        return mLockscreenCredential.isNone();
    }
}
