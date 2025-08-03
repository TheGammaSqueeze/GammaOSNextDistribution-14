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

package android.security.cts.TestBluetoothDiscoverable;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.regex.Pattern;

public class PocActivity extends Activity {

    int getInteger(int resId) {
        return getResources().getInteger(resId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String action = getIntent().getStringExtra(getString(R.string.btAction));
            int code = getInteger(R.integer.enable);
            if (action.equals(BluetoothAdapter.ACTION_REQUEST_DISABLE)) {
                code = getInteger(R.integer.disable);
            }
            BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if ((action.equals(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    && !bluetoothAdapter.isEnabled())
                    || (action.equals(BluetoothAdapter.ACTION_REQUEST_DISABLE)
                            && bluetoothAdapter.isEnabled())) {
                Intent btIntent = new Intent(action);
                startActivityForResult(btIntent, code);

                // Wait for the 'Allow' button
                String settingsPackageName = DeviceTest.getSettingsPkgName();
                Resources settingsRes =
                        getPackageManager().getResourcesForApplication(settingsPackageName);
                int resIdentifier = settingsRes.getIdentifier(getString(R.string.allowButtonResKey),
                        getString(R.string.resType), settingsPackageName);
                String allowButtonText = settingsRes.getString(resIdentifier);
                Pattern textPattern = Pattern.compile(allowButtonText, Pattern.CASE_INSENSITIVE);
                BySelector selector = By.text(textPattern);
                UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
                uiDevice.wait(Until.hasObject(selector), getInteger(R.integer.timeoutMs));

                // Click on the 'Allow' button to enable bluetooth as required by test
                UiObject2 uiObject = uiDevice.findObject(selector);
                uiObject.click();
            } else {
                sendTestResult(getInteger(R.integer.success), "");
            }
        } catch (Exception e) {
            sendTestResult(getInteger(R.integer.assumptionFailure), e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode == getInteger(R.integer.enable) && resultCode == Activity.RESULT_OK) {
                sendTestResult(getInteger(R.integer.enable), "");
            } else if (requestCode == getInteger(R.integer.disable)
                    && resultCode == Activity.RESULT_OK) {
                sendTestResult(getInteger(R.integer.disable), "");
            }
        } catch (Exception e) {
            // Ignore exception here
        }
    }

    void sendTestResult(int result, String message) {
        try {
            Intent intent = new Intent(getString(R.string.broadcastAction));
            intent.putExtra(getString(R.string.resultKey), result);
            intent.putExtra(getString(R.string.messageKey), message);
            sendBroadcast(intent);
            finish();
        } catch (Exception e) {
            // Ignore exception here
        }
    }
}
