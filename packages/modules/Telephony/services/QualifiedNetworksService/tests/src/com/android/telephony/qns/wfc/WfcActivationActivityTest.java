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
package com.android.telephony.qns.wfc;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsMmTelManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for class {@link WfcActivationActivity} */
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public final class WfcActivationActivityTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private final Context mAppContext = ApplicationProvider.getApplicationContext();
    private static final int SUB_ID = 1;

    private WfcActivationHelper mWfcActivationHelper;
    private ActivityScenario<WfcActivationActivity> mActivityScenario;
    private TestableLooper mTestableLooper;
    boolean mIsTestExecuted;

    @Mock private ConnectivityManager mockConnectivityManager;
    @Mock private ImsMmTelManager mockImsMmTelManager;
    @Mock private WfcCarrierConfigManager mockCarrierConfigManager;
    @Mock private NetworkInfo mockNetworkInfo;
    @Mock private ActivityResultLauncher<Intent> mockActivityResultLauncher;

    @Before
    public void setUp() {
        when(mockConnectivityManager.getActiveNetworkInfo()).thenReturn(mockNetworkInfo);
        when(mockNetworkInfo.isConnected()).thenReturn(true);
        when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        when(mockCarrierConfigManager.getVowifiEntitlementServerUrl())
                .thenReturn("https://test_rul");
        when(mockCarrierConfigManager.supportJsCallbackForVowifiPortal())
                .thenReturn(true);
        when(mockCarrierConfigManager.isShowVowifiPortalAfterTimeout()).thenReturn(true);
        when(mockCarrierConfigManager.getVowifiRegistrationTimerForVowifiActivation())
                .thenReturn(100);
        mTestableLooper = TestableLooper.get(this);
        mIsTestExecuted = false;
        turnOnAndUnlockScreen();
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void testMessageCheckWifi_pass() {
        mWfcActivationHelper =
                new WfcActivationHelper(
                        mAppContext,
                        SUB_ID,
                        mockConnectivityManager,
                        mockImsMmTelManager,
                        mockCarrierConfigManager,
                        Runnable::run) {};
        WfcUtils.setWfcActivationHelper(mWfcActivationHelper);

        mActivityScenario =
                ActivityScenario.launch(
                        new Intent(mAppContext, WfcActivationActivity.class)
                                .putExtra(
                                        WfcUtils.EXTRA_LAUNCH_CARRIER_APP,
                                        WfcUtils.LAUNCH_APP_ACTIVATE)
                                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID));
        verify(mockConnectivityManager).getActiveNetworkInfo();
    }

    @Test
    public void testMessageCheckWifiDone_wifiConnectionSuccess_showProgressDialog() {
        mWfcActivationHelper =
                new WfcActivationHelper(
                        mAppContext,
                        SUB_ID,
                        mockConnectivityManager,
                        mockImsMmTelManager,
                        mockCarrierConfigManager,
                        Runnable::run);
        WfcUtils.setWfcActivationHelper(mWfcActivationHelper);

        mActivityScenario =
                ActivityScenario.launch(
                        new Intent(mAppContext, WfcActivationActivity.class)
                                .putExtra(
                                        WfcUtils.EXTRA_LAUNCH_CARRIER_APP,
                                        WfcUtils.LAUNCH_APP_ACTIVATE)
                                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID));

        mActivityScenario.onActivity(
                activity -> {
                    assertNotNull(activity.mProgressDialog);
                    assertTrue(activity.mProgressDialog.isShowing());
                    mIsTestExecuted = true;
                });
       assertTrue(mIsTestExecuted);
    }

  @Test
  public void testMessageCheckWifiDone_wifiConnectionError_showWiFiUnavailableDialog() {
      when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_MOBILE);
      mWfcActivationHelper =
              new WfcActivationHelper(
                      mAppContext,
                      SUB_ID,
                      mockConnectivityManager,
                      mockImsMmTelManager,
                      mockCarrierConfigManager,
                      Runnable::run);
      WfcUtils.setWfcActivationHelper(mWfcActivationHelper);

      mActivityScenario =
              ActivityScenario.launch(
                      new Intent(mAppContext, WfcActivationActivity.class)
                              .putExtra(
                                      WfcUtils.EXTRA_LAUNCH_CARRIER_APP,
                                      WfcUtils.LAUNCH_APP_ACTIVATE)
                              .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID));

      mActivityScenario.onActivity(
              activity -> {
                  assertNull(activity.mProgressDialog);
                  assertThat(activity.getSupportFragmentManager().getFragments().get(0))
                          .isInstanceOf(WfcActivationActivity.AlertDialogFragment.class);
                  mIsTestExecuted = true;
              });
     assertTrue(mIsTestExecuted);
  }

    @Test
    public void testMessageTryEpdgConnectionDone_imsMmTelManagerNotNull_epdgConnectionSuccess() {
        mWfcActivationHelper =
                new WfcActivationHelper(
                        mAppContext,
                        SUB_ID,
                        mockConnectivityManager,
                        mockImsMmTelManager,
                        mockCarrierConfigManager,
                        Runnable::run) {
                    @Override
                    public void tryEpdgConnectionOverWiFi(Message msg, int timeout) {
                           FakeEpdgConnectHandler handler =
                                   new FakeEpdgConnectHandler(mTestableLooper.getLooper(), msg);
                           handler.obtainMessage(WfcActivationHelper.EPDG_CONNECTION_SUCCESS)
                                   .sendToTarget();
                    }
                };
        WfcUtils.setWfcActivationHelper(mWfcActivationHelper);
        mActivityScenario =
                ActivityScenario.launch(
                        new Intent(mAppContext, WfcActivationActivity.class)
                                .putExtra(
                                        WfcUtils.EXTRA_LAUNCH_CARRIER_APP,
                                        WfcUtils.LAUNCH_APP_ACTIVATE)
                                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID));
        mTestableLooper.processAllMessages();

        mActivityScenario.onActivity(
                activity -> {
                    assertNull(activity.mProgressDialog);
                    assertThat(activity.getSupportFragmentManager().getFragments()).isEmpty();
                    mIsTestExecuted = true;
                });
        assertTrue(mIsTestExecuted);
    }

    @Test
    public void testMessageShowWebPortal_imsMmTelManagerNull_showWfcWebPortal() {
        mWfcActivationHelper =
                new WfcActivationHelper(
                        mAppContext,
                        SUB_ID,
                        mockConnectivityManager,
                        null,
                        mockCarrierConfigManager,
                        Runnable::run) {};
        WfcUtils.setWfcActivationHelper(mWfcActivationHelper);
        WfcUtils.setWebviewResultLauncher(mockActivityResultLauncher);

        mActivityScenario =
                ActivityScenario.launch(
                        new Intent(mAppContext, WfcActivationActivity.class)
                                .putExtra(
                                        WfcUtils.EXTRA_LAUNCH_CARRIER_APP,
                                        WfcUtils.LAUNCH_APP_ACTIVATE)
                                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID));

        mActivityScenario.onActivity(
                activity -> {
                    assertNull(activity.mServiceConnection);
                    verify(mockActivityResultLauncher).launch(any());
                    mIsTestExecuted = true;
                });
        assertTrue(mIsTestExecuted);
    }

    @Test
    public void startWebPortal_unsupportedJsCallback_launchChrome() {
       when(mockCarrierConfigManager.supportJsCallbackForVowifiPortal())
               .thenReturn(false);
        mWfcActivationHelper =
                new WfcActivationHelper(
                        mAppContext,
                        SUB_ID,
                        mockConnectivityManager,
                        mockImsMmTelManager,
                        mockCarrierConfigManager,
                        Runnable::run) {
                    @Override
                    public void tryEpdgConnectionOverWiFi(Message msg, int timeout) {
                           FakeEpdgConnectHandler handler =
                                   new FakeEpdgConnectHandler(mTestableLooper.getLooper(), msg);
                           handler.obtainMessage(WfcActivationHelper.EPDG_CONNECTION_ERROR)
                                   .sendToTarget();
                    }
                };
        WfcUtils.setWfcActivationHelper(mWfcActivationHelper);

        mActivityScenario =
                ActivityScenario.launch(
                        new Intent(mAppContext, WfcActivationActivity.class)
                                .putExtra(
                                        WfcUtils.EXTRA_LAUNCH_CARRIER_APP,
                                        WfcUtils.LAUNCH_APP_ACTIVATE)
                                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SUB_ID));
        mTestableLooper.processAllMessages();

        mActivityScenario.onActivity(
                activity -> {
                    assertThat(activity.mServiceConnection).isNotNull();
                    verifyNoMoreInteractions(mockActivityResultLauncher);
                    mIsTestExecuted = true;
                });
        assertTrue(mIsTestExecuted);
    }

    private void turnOnAndUnlockScreen() {
        UiAutomation uiAutomation= InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String screenOnCmd = "input keyevent KEYCODE_WAKEUP";
        uiAutomation.executeShellCommand(screenOnCmd);
        waitCmdCompletion();
        String unlockCmd = "input keyevent KEYCODE_MENU";
        uiAutomation.executeShellCommand(unlockCmd);
        waitCmdCompletion();
    }

    private void  waitCmdCompletion() {
          PowerManager powerManager = mAppContext.getSystemService(PowerManager.class);
          int waitTimeMs = 100;
          final long timeoutMs = SystemClock.uptimeMillis() + 10 * 1000;

          while (SystemClock.uptimeMillis() < timeoutMs) {
              if (powerManager.isInteractive()) {
                  return;
              }
              try {
                  Thread.sleep(waitTimeMs);
              } catch (Exception e) {
                  throw new AssertionError("Thread sleep fails", e);
              }
              waitTimeMs = waitTimeMs * 2;
              waitTimeMs = Math.min(1000, waitTimeMs);
          }
    }

    static private class FakeEpdgConnectHandler extends Handler {
          final Message result;

          FakeEpdgConnectHandler(Looper looper, Message result) {
            super(looper);
            this.result = result;
        }

        @Override
        public void handleMessage(Message msg) {
            result.arg1 = msg.what;
            result.sendToTarget();
        }
    }
}
