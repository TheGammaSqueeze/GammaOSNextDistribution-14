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

package android.server.biometrics;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * Test app that invokes authentication in onCreate
 */
public class Class2BiometricOrCredentialActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final BiometricCallbackHelper callbackHelper = new BiometricCallbackHelper(this);

        final BiometricPrompt bp = new BiometricPrompt.Builder(this)
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .setDescription("Description")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        bp.authenticate(new CancellationSignal(), executor, callbackHelper);
    }
}
