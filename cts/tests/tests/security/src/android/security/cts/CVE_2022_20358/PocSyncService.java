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

package android.security.cts.CVE_2022_20358;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;

public class PocSyncService extends Service {
    private static PocSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();
    RemoteCallback mCb;

    @Override
    public void onCreate() {
        try {
            synchronized (sSyncAdapterLock) {
                if (sSyncAdapter == null) {
                    sSyncAdapter = new PocSyncAdapter(this);
                }
            }
        } catch (Exception e) {
            // ignore all exceptions
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        try {
            mCb = (RemoteCallback) intent.getExtra("callback");
        } catch (Exception e) {
            // ignore all exceptions
        }
        return sSyncAdapter.getSyncAdapterBinder();
    }

    public class PocSyncAdapter extends AbstractThreadedSyncAdapter {

        public PocSyncAdapter(Context context) {
            super(context, false);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult) {
            try {
                if (account.type.equals("CVE_2022_20358_acc")
                        && account.name.equals("CVE_2022_20358_user")) {
                    Bundle res = new Bundle();
                    res.putString("fail", "");
                    mCb.sendResult(res);
                }
            } catch (Exception e) {
                // ignore all exceptions
            }
        }
    }
}
