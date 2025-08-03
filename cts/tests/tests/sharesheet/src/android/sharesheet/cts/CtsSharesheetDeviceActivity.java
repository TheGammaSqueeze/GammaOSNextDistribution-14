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
package android.sharesheet.cts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.function.Consumer;

public class CtsSharesheetDeviceActivity extends Activity {
    private static Consumer<Intent> sOnIntentReceivedConsumer;

    public static void setOnIntentReceivedConsumer(Consumer<Intent> consumer) {
        sOnIntentReceivedConsumer = consumer;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (sOnIntentReceivedConsumer != null) {
            sOnIntentReceivedConsumer.accept(getIntent());
            sOnIntentReceivedConsumer = null;
        }

        // This activity may be opened to ensure click behavior functions properly.
        // To ensure test repeatability do not stay open.
        finish();
    }
}