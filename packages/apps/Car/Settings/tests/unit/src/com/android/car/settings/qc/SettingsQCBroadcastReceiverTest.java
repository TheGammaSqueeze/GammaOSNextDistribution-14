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

package com.android.car.settings.qc;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsQCBroadcastReceiverTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private SettingsQCBroadcastReceiver mReceiver;
    private TestSettingsQCItem mQCItem;

    @Before
    public void setUp() {
        SettingsQCRegistry.sUriToQC.clear();
        SettingsQCRegistry.sUriToQC.put(QCTestUtils.URI, TestSettingsQCItem.class);
        mReceiver = new TestSettingsQCBroadcastReceiver();
        mQCItem = spy(new TestSettingsQCItem(mContext));
    }

    @Test
    public void onReceive_notifiesChange() {
        Intent intent = new Intent();
        intent.setAction(QCTestUtils.URI.toString());
        mReceiver.onReceive(mContext, intent);
        verify(mQCItem).onNotifyChange(intent);
    }

    private class TestSettingsQCBroadcastReceiver extends SettingsQCBroadcastReceiver {
        @Override
        SettingsQCItem createQCItemInstance(Context context,
                Class<? extends SettingsQCItem> qcItem) {
            return mQCItem;
        }
    }
}
