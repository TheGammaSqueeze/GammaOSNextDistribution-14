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

package com.google.android.sample.rcsclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.sample.rcsclient.util.NumberUtils;

/** An activity to let user input phone number to chat. */
public class PhoneNumberActivity extends AppCompatActivity {

    private static final String TAG = "TestRcsApp.PhoneNumberActivity";
    private Button mChatButton;
    private EditText mPhoneNumber;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.number_to_chat);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mChatButton = this.findViewById(R.id.launch_chat_btn);
        mPhoneNumber = findViewById(R.id.destNum);
        mChatButton.setOnClickListener(view -> {
            String formattedNumber = NumberUtils.formatNumber(PhoneNumberActivity.this,
                    mPhoneNumber.getText().toString());
            if (formattedNumber != null) {
                Intent intent = new Intent(PhoneNumberActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_REMOTE_PHONE_NUMBER, formattedNumber);
                PhoneNumberActivity.this.startActivity(intent);
            } else {
                Toast.makeText(this, "Invalid Number format!",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onStop();
        Log.i(TAG, "onDestroy");
    }
}
