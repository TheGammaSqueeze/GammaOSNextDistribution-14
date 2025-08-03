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

package com.google.android.car.kitchensink.biometrics;

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static com.google.android.car.kitchensink.KitchenSinkActivity.DUMP_ARG_CMD;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * This uses {@link BiometricPrompt} API to verify the device screen lock UI.
 *
 * <p>Once the activity hosting this fragment is launched, it can be controlled using {@code adb}.
 * Example:
 *
 * <pre><code>
 adb shell 'am start -n com.google.android.car.kitchensink/.KitchenSinkActivity \
    --es select "BiometricPrompt"'
 adb shell 'dumpsys activity com.google.android.car.kitchensink/.KitchenSinkActivity \
    fragment "BiometricPrompt" cmd device'
 * </code></pre>
 */
public final class BiometricPromptTestFragment extends Fragment {

    private static final String TAG = BiometricPromptTestFragment.class.getSimpleName();

    public static final String FRAGMENT_NAME = "BiometricPrompt";

    private static final String CMD_HELP = "help";
    private static final String CMD_DEVICE = "device";
    private static final String CMD_BIOMETRIC = "biometric";
    private static final String CMD_INTENT = "intent";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final Executor mExecutor = mHandler::post;
    private Editable mEditable;

    private final BiometricPrompt.AuthenticationCallback mAuthenticationCallback =
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    mLatch.countDown();
                    logMessage("onAuthenticationError: " + errorCode);
                }

                @Override
                public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                    logMessage("onAuthenticationHelp: " + helpString);
                }

                @Override
                public void onAuthenticationFailed() {
                    logMessage("onAuthenticationFailed");
                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    logMessage("onAuthenticationSucceeded: " + result);
                }
            };

    private final ActivityResultLauncher<Intent> mStartForResult =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent intent = result.getData();
                            logMessage("ConfirmDeviceCredential OK: " + intent);
                        } else {
                            logMessage("ConfirmDeviceCredential not OK: " + result.getResultCode());
                        }
                    });

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.biometric_prompt_fragment, container, false);
        TextView textView = (TextView) view.findViewById(R.id.messages);
        assert textView != null;
        textView.setMovementMethod(new ScrollingMovementMethod());
        mEditable = textView.getEditableText();

        view.findViewById(R.id.button_auth_device_credential)
                .setOnClickListener(v -> authenticate(DEVICE_CREDENTIAL | BIOMETRIC_WEAK));
        view.findViewById(R.id.button_auth_biometric)
                .setOnClickListener(v -> authenticate(BIOMETRIC_WEAK));
        view.findViewById(R.id.button_auth_device_credential_intent)
                .setOnClickListener(v -> confirmWithDeviceCredentialIntent());
        return view;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        Log.v(TAG, "dump(): " + Arrays.toString(args));

        if (args != null && args.length > 0 && args[0].equals(DUMP_ARG_CMD)) {
            runCmd(writer, args);
            return;
        }
    }

    // Authentication by BiometricPrompt API
    private void authenticate(int authenticators) {
        String title = "Title";
        String subtitle = "Subtitle";
        String description = "Description";
        String negativeButtonText = "Negative Button";
        BiometricPrompt.Builder builder =
                new BiometricPrompt.Builder(getContext())
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setDescription(description)
                        .setConfirmationRequired(false)
                        .setAllowedAuthenticators(authenticators);

        if ((authenticators & DEVICE_CREDENTIAL) != DEVICE_CREDENTIAL) {
            // Can't have both negative button behavior and device credential enabled
            builder.setNegativeButton(
                    negativeButtonText,
                    mExecutor,
                    (dialog, which) -> {
                        Log.d(TAG, "No opt on NegativeButton.");
                    });
        }
        BiometricPrompt prompt = builder.build();
        CancellationSignal cancellationSignal = new CancellationSignal();
        logMessage("BiometricPrompt.authenticate with: " + prompt);
        prompt.authenticate(cancellationSignal, mExecutor, mAuthenticationCallback);
    }

    // Authentication by Keyguard API for deprecated in API 29
    private void confirmWithDeviceCredentialIntent() {
        KeyguardManager keyguardManager;
        keyguardManager = getContext().getSystemService(KeyguardManager.class);
        if (keyguardManager == null) {
            logMessage("Failed to get the KeyguardManager service.");
            return;
        }
        Intent intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                        "Title", "createConfirmDeviceCredentialIntent");
        if (intent == null) {
            logMessage("Failed to get the KeyguardManager service.");
            return;
        }
        mStartForResult.launch(intent);
    }

    private void logMessage(CharSequence message) {
        mEditable.insert(0, message + "\n");
        Log.d(TAG, message.toString());
    }

    private void runCmd(PrintWriter writer, String[] args) {
        if (args.length < 2) {
            writer.println("missing command\n");
            return;
        }
        String cmd = args[1];
        switch (cmd) {
            case CMD_HELP:
                cmdShowHelp(writer);
                break;
            case CMD_DEVICE:
                authenticate(DEVICE_CREDENTIAL | BIOMETRIC_WEAK);
                break;
            case CMD_BIOMETRIC:
                authenticate(BIOMETRIC_WEAK);
                break;
            case CMD_INTENT:
                confirmWithDeviceCredentialIntent();
                break;

            default:
                cmdShowHelp(writer);
                writer.printf("Invalid cmd: %s\n", Arrays.toString(args));
        }
        return;
    }

    private void cmdShowHelp(PrintWriter writer) {
        writer.println("Available commands:\n");
        showCommandHelp(writer, "Shows this help message.", CMD_HELP);
        showCommandHelp(writer,
                "BiometricPrompt#authenticate by DEVICE_CREDENTIAL | BIOMETRIC_WEAK.",
                CMD_DEVICE);
        showCommandHelp(writer,
                "BiometricPrompt#authenticate by BIOMETRIC_WEAK.",
                CMD_BIOMETRIC);
        showCommandHelp(writer,
                "Authenticates by KeyguardManager#createConfirmDeviceCredentialIntent.",
                CMD_INTENT);
    }

    private void showCommandHelp(PrintWriter writer, String description, String cmd,
            String... args) {
        writer.printf("%s", cmd);
        if (args != null) {
            for (String arg : args) {
                writer.printf(" %s", arg);
            }
        }
        writer.println(":");
        writer.printf("  %s\n\n", description);
    }
}
