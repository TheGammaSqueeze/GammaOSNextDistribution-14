/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.cts.smsretriever;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.telephony.SmsManager;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String SMS_RETRIEVER_ACTION = "CTS_SMS_RETRIEVER_ACTION";
    private static String sToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction() == null) {
            Intent intent = new Intent("android.telephony.cts.action.SMS_RETRIEVED")
                            .setComponent(new ComponentName(
                                    getApplicationContext(),
                                    SmsRetrieverBroadcastReceiver.class));
            PendingIntent pIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
            try {
                sToken = SmsManager.getDefault().createAppSpecificSmsTokenWithPackageInfo(
                        "testprefix1,testprefix2", pIntent);
            } catch (Exception e) {
                Log.w("MainActivity", "received Exception e:" + e);
            }

            Bundle result = new Bundle();
            result.putString("class", getClass().getName());
            result.putString("token", sToken);
            sendResult(result);
        } else {
            // Launched by broadcast receiver
            assertThat(getIntent().getStringExtra("message"),
                    equalTo("testprefix1This is a test message" + sToken));
            Intent bIntent = new Intent(SMS_RETRIEVER_ACTION);
            sendBroadcast(bIntent);
            finish();
        }
    }

    public void sendResult(Bundle result) {
        getIntent().<RemoteCallback>getParcelableExtra("callback").sendResult(result);
    }
}
