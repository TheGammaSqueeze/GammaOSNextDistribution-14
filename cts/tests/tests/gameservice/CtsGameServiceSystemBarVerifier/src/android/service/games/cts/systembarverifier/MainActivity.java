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

package android.service.games.cts.systembarverifier;

import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public final class MainActivity extends AppCompatActivity {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Button button = new Button(this);
        button.setText("Show system bars permanently");
        button.setAllCaps(false);
        setContentView(button);

        final WindowInsetsController windowInsetsController = button.getWindowInsetsController();
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsets.Type.systemBars());

        button.setOnClickListener(v -> {
            windowInsetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
            windowInsetsController.show(WindowInsets.Type.systemBars());
        });
    }
}
