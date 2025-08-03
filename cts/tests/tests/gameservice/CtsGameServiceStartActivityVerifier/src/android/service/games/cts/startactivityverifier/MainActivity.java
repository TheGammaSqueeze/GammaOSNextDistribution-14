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

package android.service.games.cts.startactivityverifier;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public final class MainActivity extends AppCompatActivity {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_layout);

        final EditText resultCodeEditable = findViewById(R.id.result_code_edit_text);
        final EditText resultDataEditable = findViewById(R.id.result_data_edit_text);
        final Button sendResultButton = findViewById(R.id.send_result_button);
        sendResultButton.setOnClickListener(unused -> {
            int resultCode;
            try {
                resultCode = Integer.parseInt(resultCodeEditable.getText().toString());
            } catch (NumberFormatException e) {
                Log.w("StartActivityVerifier", "Failed to parse result code", e);
                finish();
                return;
            }
            final String resultData = resultDataEditable.getText().toString();
            if (TextUtils.isEmpty(resultData)) {
                setResult(resultCode);
            } else {
                final Intent data = new Intent();
                data.putExtra("data", resultData);
                setResult(resultCode, data);
            }

            finish();
        });
    }
}
