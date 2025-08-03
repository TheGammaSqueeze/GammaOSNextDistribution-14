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

package android.translation.cts;

import static android.translation.cts.Helper.COMMAND_CREATE_TRANSLATOR;

import android.app.Service;
import android.content.Intent;
import android.icu.util.ULocale;
import android.os.IBinder;
import android.util.Log;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationSpec;

/**
 * A Service that running on otherTranslationProcess1 process for testing create translator
 * will generate unique translation session id.
 */
public class OtherProcessService1 extends Service {

    private static final String TAG = "OtherProcessService";

    static final String EXTRA_COMMAND = "android.translation.cts.extra.CREATE_TRANSLATOR";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int command = intent.getIntExtra(EXTRA_COMMAND, -1);
        if (command == COMMAND_CREATE_TRANSLATOR) {
            createOnDeviceTranslator();
        } else {
            Log.e(TAG, "Unknown command: " + command);
        }
        return Service.START_NOT_STICKY;
    }

    private void createOnDeviceTranslator() {
        TranslationManager manager = getSystemService(TranslationManager.class);
        new Thread(() -> {
            TranslationContext translationContext = new TranslationContext.Builder(
                    new TranslationSpec(ULocale.ENGLISH, TranslationSpec.DATA_FORMAT_TEXT),
                    new TranslationSpec(ULocale.FRENCH, TranslationSpec.DATA_FORMAT_TEXT))
                    .build();
            createTranslator(manager, translationContext);
        }).start();
    }

    private void createTranslator(TranslationManager manager,
            TranslationContext translationContext) {
        manager.createOnDeviceTranslator(translationContext, Runnable::run,
                translator -> {
                    if (translator != null) {
                        translator.destroy();
                    }
                });
    }
}
