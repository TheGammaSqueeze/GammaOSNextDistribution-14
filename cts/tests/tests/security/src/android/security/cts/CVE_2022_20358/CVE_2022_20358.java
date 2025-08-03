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

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.accounts.Account;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ISyncAdapter;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20358 extends StsExtraBusinessLogicTestCase implements ServiceConnection {
    static final int TIMEOUT_SEC = 10;
    Semaphore mWaitResultServiceConn;
    boolean mIsAssumeFail = false;
    String mAssumeFailMsg = "";

    @AsbSecurityTest(cveBugId = 203229608)
    @Test
    public void testPocCVE_2022_20358() {
        try {
            // Bind to the PocSyncService
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            Context context = instrumentation.getContext();
            Intent intent = new Intent(context, PocSyncService.class);
            intent.setAction("android.content.SyncAdapter");
            CompletableFuture<String> callbackReturn = new CompletableFuture<>();
            RemoteCallback cb = new RemoteCallback((Bundle result) -> {
                callbackReturn.complete(result.getString("fail"));
            });
            intent.putExtra("callback", cb);
            context.bindService(intent, this, Context.BIND_AUTO_CREATE);

            // Wait for some result from the PocSyncService
            mWaitResultServiceConn = new Semaphore(0);
            assumeTrue(mWaitResultServiceConn.tryAcquire(TIMEOUT_SEC, TimeUnit.SECONDS));
            assumeTrue(mAssumeFailMsg, !mIsAssumeFail);

            // Wait for a result to be set from onPerformSync() of PocSyncAdapter
            callbackReturn.get(TIMEOUT_SEC, TimeUnit.SECONDS);

            // In presence of vulnerability, the above call succeeds and TimeoutException is not
            // triggered so failing the test
            fail("Vulnerable to b/203229608!!");
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                // The fix is present so returning from here
                return;
            }
            assumeNoException(e);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        try {
            if (mWaitResultServiceConn == null) {
                mWaitResultServiceConn = new Semaphore(0);
            }
            ISyncAdapter adapter = ISyncAdapter.Stub.asInterface(service);
            Account account = new Account("CVE_2022_20358_user", "CVE_2022_20358_acc");
            adapter.startSync(null, "android.security.cts.CVE_2022_20358.provider", account, null);
            mWaitResultServiceConn.release();
        } catch (Exception e) {
            try {
                mWaitResultServiceConn.release();
                mAssumeFailMsg = e.getMessage();
                mIsAssumeFail = true;
            } catch (Exception ex) {
                // ignore all exceptions
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        try {
            mWaitResultServiceConn.release();
        } catch (Exception e) {
            // ignore all exceptions
        }
    }
}
