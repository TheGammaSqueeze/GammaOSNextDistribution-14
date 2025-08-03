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

package android.translation.cts;

import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START;
import static android.translation.cts.Helper.ACTION_REGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.EXTRA_CALL_COUNT;
import static android.translation.cts.Helper.EXTRA_FINISH_COMMAND;
import static android.translation.cts.Helper.EXTRA_PACKAGE_NAME;
import static android.translation.cts.Helper.EXTRA_SOURCE_LOCALE;
import static android.translation.cts.Helper.EXTRA_TARGET_LOCALE;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.translation.UiTranslationManager;
import android.widget.LinearLayout;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link InputMethodService} used in CTS tests.
 */
public final class CtsTestIme extends InputMethodService {

    private static final String TAG = "CtsTestIme";

    static String IME_SERVICE_PACKAGE = "android.translation.cts";

    private Context mContext;
    private FakeTranslationStateCallback mCallback;
    private CommandReceiver mReceiver;

    @Override
    public View onCreateInputView() {
        return new LinearLayout(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        if (mReceiver == null) {
            mReceiver = new CommandReceiver(mContext);
            mReceiver.register();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            mReceiver.unRegister();
            mReceiver = null;
        }
    }

    void registerUiTranslationStateCallback(Intent intent) {
        final UiTranslationManager manager = mContext.getSystemService(UiTranslationManager.class);
        mCallback = new FakeTranslationStateCallback();
        final Executor executor = Executors.newSingleThreadExecutor();
        manager.registerUiTranslationStateCallback(executor, mCallback);

        notifyCommandDone(intent, /* resultIntent= */ null);
    }

    void unregisterUiTranslationStateCallback(Intent intent) {
        final UiTranslationManager manager = mContext.getSystemService(UiTranslationManager.class);
        manager.unregisterUiTranslationStateCallback(mCallback);

        notifyCommandDone(intent, /* resultIntent= */ null);
    }

    void assertOnStart(Intent intent) {
        final Intent result = new Intent();
        result.putExtra(EXTRA_SOURCE_LOCALE, mCallback.getStartedSourceLocale());
        result.putExtra(EXTRA_TARGET_LOCALE, mCallback.getStartedTargetLocale());
        result.putExtra(EXTRA_CALL_COUNT, mCallback.getStartedCallCount());
        result.putExtra(EXTRA_PACKAGE_NAME, mCallback.getStartedPackageName());
        notifyCommandDone(intent, result);
    }

    void assertOnFinish(Intent intent) {
        final Intent result = new Intent();
        result.putExtra(EXTRA_CALL_COUNT, mCallback.getFinishedCallCount());
        result.putExtra(EXTRA_PACKAGE_NAME, mCallback.getFinishedPackageName());
        notifyCommandDone(intent, result);
    }

    void assertOnResume(Intent intent) {
        final Intent result = new Intent();
        result.putExtra(EXTRA_CALL_COUNT, mCallback.getResumedCallCount());
        result.putExtra(EXTRA_PACKAGE_NAME, mCallback.getResumedPackageName());
        notifyCommandDone(intent, result);
    }

    void assertOnPause(Intent intent) {
        final Intent result = new Intent();
        result.putExtra(EXTRA_CALL_COUNT, mCallback.getPausedCallCount());
        result.putExtra(EXTRA_PACKAGE_NAME, mCallback.getPausedPackageName());
        notifyCommandDone(intent, result);
    }

    private void notifyCommandDone(Intent sourceIntent, Intent resultIntent) {
        final PendingIntent pendingIntent = sourceIntent.getParcelableExtra(EXTRA_FINISH_COMMAND,
                PendingIntent.class);
        if (pendingIntent != null) {
            try {
                final String action = sourceIntent.getAction();
                switch (action) {
                    case ACTION_REGISTER_UI_TRANSLATION_CALLBACK:
                    case ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK:
                        pendingIntent.send();
                        break;
                    case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START:
                    case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH:
                    case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME:
                    case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE:
                        if (resultIntent != null) {
                            pendingIntent.send(mContext, 0, resultIntent);
                        } else {
                            Log.w(TAG, "Should have resultIntent for " + action);
                        }
                        break;
                }
            } catch (CanceledException e) {
                Log.w(TAG, "Pending intent " + pendingIntent + " canceled");
            }
        }
        mCallback.resetStates();
    }

    private final class CommandReceiver extends BroadcastReceiver {

        Context mContext;

        CommandReceiver(Context context) {
            mContext = context;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case ACTION_REGISTER_UI_TRANSLATION_CALLBACK:
                    registerUiTranslationStateCallback(intent);
                    break;
                case ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK:
                    unregisterUiTranslationStateCallback(intent);
                    break;
                case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START:
                    assertOnStart(intent);
                    break;
                case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH:
                    assertOnFinish(intent);
                    break;
                case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME:
                    assertOnResume(intent);
                    break;
                case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE:
                    assertOnPause(intent);
                    break;
            }
        }

        void register() {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_REGISTER_UI_TRANSLATION_CALLBACK);
            filter.addAction(ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK);
            filter.addAction(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START);
            filter.addAction(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH);
            filter.addAction(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME);
            filter.addAction(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE);
            mContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        void unRegister() {
            mContext.unregisterReceiver(this);
        }
    }
}
