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

package android.telephony.gba.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.gba.UaSecurityProtocolIdentifier;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class GbaServiceTest {

    private static final String TAG = "GbaServiceTest";

    private static final String COMMAND_UPDATE_PACKAGE = "cmd phone gba set-service ";
    private static final String COMMAND_UPDATE_RELEASE = "cmd phone gba set-release ";
    private static final String COMMAND_GET_PACKAGE = "cmd phone gba get-service";
    private static final String COMMAND_GET_RELEASE = "cmd phone gba get-release";
    private static final String SERVICE_PACKAGE = "android.telephony.cts";
    private static final String NAF = "3GPP-bootstrapping@naf1.operator.com";
    private static final String BTID = "(B-TID)";
    private static final int REQ_TIMEOUT = 5000;
    private static final int RELEASE_DEFAULT = 0;

    private static int sSubId;
    private static Instrumentation sInstrumentation;
    private static TelephonyManager sTm;
    private static TestGbaConfig sConfig;
    private static String sServiceConfig;
    private static int sReleaseTimeConfig;

    @BeforeClass
    public static void init() throws Exception {
        if (!isFeatureSupported()) {
            return;
        }

        sConfig = TestGbaConfig.getInstance();
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sTm = sInstrumentation.getContext().getSystemService(TelephonyManager.class);
        sServiceConfig = TelephonyUtils.executeShellCommand(
                sInstrumentation, COMMAND_GET_PACKAGE);
        sReleaseTimeConfig = Integer.parseInt(TelephonyUtils.executeShellCommand(
                sInstrumentation, COMMAND_GET_RELEASE));
    }

    @AfterClass
    public static void release() throws Exception {
        if (!isFeatureSupported()) {
            return;
        }

        setService(sServiceConfig);
        setReleaseTime(sReleaseTimeConfig);
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(isFeatureSupported());

        setService(SERVICE_PACKAGE);
        setReleaseTime(RELEASE_DEFAULT);
    }

    @Test (expected = SecurityException.class)
    public void testPermissions() {
        runGbaFailCase(TelephonyManager.GBA_FAILURE_REASON_FEATURE_NOT_SUPPORTED,
                android.Manifest.permission.READ_PHONE_STATE);
    }

    @Test
    public void testAuthSuccess() {
        Random rand = new Random();

        for (int i = 0; i < 20; i++) {
            Log.d(TAG, "testAuthSuccess[" + i + "]");
            byte[] key = new byte[16];
            rand.nextBytes(key);
            sConfig.setConfig(true, key, BTID, TelephonyManager.GBA_FAILURE_REASON_UNKNOWN);
            final AtomicBoolean isSuccess = new AtomicBoolean(false);
            final AtomicBoolean isFail = new AtomicBoolean(false);
            TelephonyManager.BootstrapAuthenticationCallback cb = new
                      TelephonyManager.BootstrapAuthenticationCallback() {
                @Override
                public void onKeysAvailable(byte[] gbaKey, String btId) {
                    super.onKeysAvailable(gbaKey, btId);
                    assertNotNull(gbaKey);
                    assertNotNull(btId);
                    assertArrayEquals(key, gbaKey);
                    assertEquals(BTID, btId);
                    synchronized (isSuccess) {
                        isSuccess.set(true);
                        isSuccess.notify();
                    }
                }

                @Override
                public void onAuthenticationFailure(int reason) {
                    super.onAuthenticationFailure(reason);
                    synchronized (isSuccess) {
                        isFail.set(true);
                        isSuccess.notify();
                    }
                }
            };
            UaSecurityProtocolIdentifier.Builder builder =
                    new UaSecurityProtocolIdentifier.Builder();
            builder.setOrg(UaSecurityProtocolIdentifier.ORG_3GPP).setProtocol(
                    UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_HTTP_BASED_MBMS);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTm,
                    (tm) -> tm.bootstrapAuthenticationRequest(TelephonyManager.APPTYPE_USIM,
                    Uri.parse(NAF), builder.build(), true, AsyncTask.SERIAL_EXECUTOR, cb),
                    android.Manifest.permission.MODIFY_PHONE_STATE);
            waitForMs(isSuccess, REQ_TIMEOUT);

            assertTrue(isSuccess.get());
            assertFalse(isFail.get());
        }
    }

    @Test
    public void testGbaNotSupported() throws Exception {
        setService("");
        sConfig.setConfig(true, new byte[16], BTID, TelephonyManager.GBA_FAILURE_REASON_UNKNOWN);

        runGbaFailCase(TelephonyManager.GBA_FAILURE_REASON_FEATURE_NOT_SUPPORTED,
                android.Manifest.permission.MODIFY_PHONE_STATE);

        assertTrue(setService(SERVICE_PACKAGE));
    }

    @Test
    public void testAuthFail() {
        for (int r = TelephonyManager.GBA_FAILURE_REASON_UNKNOWN;
                r <= TelephonyManager.GBA_FAILURE_REASON_SECURITY_PROTOCOL_NOT_SUPPORTED; r++) {
            sConfig.setConfig(false, new byte[16], BTID, r);
            runGbaFailCase(r, android.Manifest.permission.MODIFY_PHONE_STATE);
        }
    }

    private void runGbaFailCase(int r, String permission) {
        final AtomicBoolean isSuccess = new AtomicBoolean(false);
        final AtomicBoolean isFail = new AtomicBoolean(false);
        TelephonyManager.BootstrapAuthenticationCallback cb = new
                  TelephonyManager.BootstrapAuthenticationCallback() {
            @Override
            public void onKeysAvailable(byte[] gbaKey, String btId) {
                synchronized (isFail) {
                    isSuccess.set(true);
                    isFail.notify();
                }
            }

            @Override
            public void onAuthenticationFailure(int reason) {
                assertEquals(reason, r);
                synchronized (isFail) {
                    isFail.set(true);
                    isFail.notify();
                }
            }
        };
        UaSecurityProtocolIdentifier.Builder builder = new UaSecurityProtocolIdentifier.Builder();
        builder.setOrg(UaSecurityProtocolIdentifier.ORG_3GPP).setProtocol(
                UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_HTTP_BASED_MBMS);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTm,
                (tm) -> tm.bootstrapAuthenticationRequest(TelephonyManager.APPTYPE_USIM,
                Uri.parse(NAF), builder.build(), true, AsyncTask.SERIAL_EXECUTOR, cb), permission);
        waitForMs(isFail, REQ_TIMEOUT);

        assertTrue(isFail.get());
        assertFalse(isSuccess.get());
    }

    @Test
    public void testServiceReleaseDefault() throws Exception {
        int ss = sConfig.getServiceState();
        boolean isExpected = (ss == TestGbaConfig.STATE_UNKNOWN
                || ss == TestGbaConfig.STATE_REMOVED
                || ss == TestGbaConfig.STATE_UNBOUND);
        assertTrue(isExpected);
        sConfig.setConfig(false, new byte[16], BTID,
                TelephonyManager.GBA_FAILURE_REASON_UNKNOWN);

        runGbaFailCase(TelephonyManager.GBA_FAILURE_REASON_UNKNOWN,
                android.Manifest.permission.MODIFY_PHONE_STATE);
        waitForMs(sConfig, 500);

        assertFalse(TestGbaConfig.STATE_BOUND == sConfig.getServiceState());
    }

    public static void waitForMs(Object obj, long ms) {
        synchronized (obj) {
            try {
                obj.wait(ms);
            } catch (InterruptedException e) {
                Log.d(TAG, "InterruptedException while waiting: " + e);
            }
        }
    }

    private static boolean setService(String packageName) throws Exception {
        String result = TelephonyUtils.executeShellCommand(sInstrumentation,
                COMMAND_UPDATE_PACKAGE + packageName);
        return "true".equals(result);
    }

    private static boolean setReleaseTime(int interval) throws Exception {
        String result = TelephonyUtils.executeShellCommand(sInstrumentation,
                COMMAND_UPDATE_RELEASE + interval);
        return "true".equals(result);
    }

    private static boolean isFeatureSupported() {
        if (!InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            return false;
        }

        SubscriptionManager subscriptionManager = InstrumentationRegistry.getContext()
                .getSystemService(SubscriptionManager.class);
        int[] activeSubs = ShellIdentityUtils.invokeMethodWithShellPermissions(subscriptionManager,
                (sm) -> sm.getActiveSubscriptionIdList());
        if (activeSubs.length == 0) {
            return false;
        }
        sSubId = activeSubs[0];
        return true;
    }
}
