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

package android.quickaccesswallet;

import android.content.Intent;
import android.os.IBinder;
import android.service.quickaccesswallet.WalletServiceEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

public class TestQuickAccessWalletService extends TestBaseQuickAccessWalletService {

    private static WeakReference<TestQuickAccessWalletService> sServiceRef =
            new WeakReference<>(null);

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        sServiceRef = new WeakReference<>(this);
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sServiceRef.clear();
        return super.onUnbind(intent);
    }

    public static void sendEvent(WalletServiceEvent event) {
        TestQuickAccessWalletService service = sServiceRef.get();
        if (service != null) {
            service.sendWalletServiceEvent(event);
        }
    }
}
