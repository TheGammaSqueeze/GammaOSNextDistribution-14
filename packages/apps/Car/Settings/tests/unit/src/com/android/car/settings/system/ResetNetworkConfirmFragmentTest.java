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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.RecoverySystem;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.PollingCheck;
import com.android.car.ui.toolbar.MenuItem;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ResetNetworkConfirmFragmentTest {
    private static final String SUBSCRIBER_ID = "test_id";

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private ResetNetworkConfirmFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MockitoSession mSession;
    private MenuItem mResetButton;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Mock
    private ConnectivityManager mMockConnectivityManager;
    @Mock
    private WifiManager mMockWifiManager;
    @Mock
    private BluetoothManager mMockBluetoothManager;
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private NetworkPolicyManager mMockNetworkPolicyManager;
    @Mock
    private ContentResolver mMockContentResolver;

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
        when(mFragment.getApplicationContext()).thenReturn(mContext);
        setNetworkSubscriptionId("123");
        mResetButton = mActivity.getToolbar().getMenuItems().get(0);

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                RecoverySystem.class, withSettings().lenient()).startMocking();
        when(mContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(
                mMockConnectivityManager);
        when(mContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mMockWifiManager);
        when(mContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(
                mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(
                mMockTelephonyManager);
        when(mMockTelephonyManager.getSubscriberId(anyInt())).then(invocation -> {
            Object[] args = invocation.getArguments();
            int subId = (Integer) args[0];
            return subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? null : SUBSCRIBER_ID;
        });
        when(mContext.getSystemService(Context.NETWORK_POLICY_SERVICE)).thenReturn(
                mMockNetworkPolicyManager);
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_connectivityManagerReset() {
        mResetButton.performClick();
        verify(mMockConnectivityManager).factoryReset();
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_wifiManagerReset() {
        mResetButton.performClick();
        verify(mMockWifiManager).factoryReset();
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_bluetoothAdapterReset() {
        mResetButton.performClick();
        verify(mMockBluetoothAdapter).clearBluetooth();
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_cleanSmsRawTable() {
        mResetButton.performClick();

        ArgumentCaptor<Uri> argumentCaptor = ArgumentCaptor.forClass(Uri.class);
        verify(mMockContentResolver, times(2)).delete(argumentCaptor.capture(), any(), any());
        List<Uri> capturedUris = argumentCaptor.getAllValues();
        Uri uri = getUriWithGivenPrefix(capturedUris, Telephony.Sms.CONTENT_URI);

        assertThat(uri).isNotNull();
    }

    @Test
    public void testResetButtonClick_euiccResetEnabled_euiccReset() throws Throwable {
        setEuiccResetCheckbox(true);
        mActivityTestRule.runOnUiThread(() -> mResetButton.performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // wait for async task
        PollingCheck.waitFor(
                () -> mFragment.mEraseEsimAsyncTask.getStatus() == AsyncTask.Status.FINISHED);

        ExtendedMockito.verify(
                () -> RecoverySystem.wipeEuiccData(any(), any()));
    }

    @Test
    public void testResetButtonClick_euiccResetDisabled_euiccNotReset() throws Throwable {
        setEuiccResetCheckbox(false);
        mActivityTestRule.runOnUiThread(() -> mResetButton.performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        ExtendedMockito.verify(
                () -> RecoverySystem.wipeEuiccData(any(), any()), never());
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_nonIntegerNetworkSubscriptionId_numberExceptionError() {
        setNetworkSubscriptionId("abc");
        assertThrows(NumberFormatException.class, () -> mResetButton.performClick());
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_emptyNetworkSubscriptionId_telephonyNotReset() {
        setNetworkSubscriptionId("");
        mResetButton.performClick();
        verify(mMockTelephonyManager).factoryReset(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_validNetworkSubscriptionId_telephonyReset() {
        mResetButton.performClick();
        verify(mMockTelephonyManager).factoryReset(123);
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_emptyNetworkSubscriptionId_networkManagerNotReset() {
        setNetworkSubscriptionId("");
        mResetButton.performClick();
        verify(mMockNetworkPolicyManager).factoryReset(null);
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_validNetworkSubscriptionId_networkManagerReset() {
        setNetworkSubscriptionId("123");
        mResetButton.performClick();
        verify(mMockNetworkPolicyManager).factoryReset(SUBSCRIBER_ID);
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_emptyNetworkSubscriptionId_noRestoreDefaultApn() {
        setNetworkSubscriptionId("");
        mResetButton.performClick();

        ArgumentCaptor<Uri> argumentCaptor = ArgumentCaptor.forClass(Uri.class);
        verify(mMockContentResolver, times(2)).delete(argumentCaptor.capture(), any(), any());
        List<Uri> capturedUris = argumentCaptor.getAllValues();
        Uri uri = getUriWithGivenPrefix(capturedUris,
                ResetNetworkConfirmFragment.RESTORE_CARRIERS_URI);

        assertThat(uri).isNotNull();
        assertThat(uri.toString().contains("subId/")).isFalse();
    }

    @Test
    @UiThreadTest
    public void testResetButtonClick_validNetworkSubscriptionId_restoreDefaultApn() {
        setNetworkSubscriptionId("123");
        mResetButton.performClick();

        ArgumentCaptor<Uri> argumentCaptor = ArgumentCaptor.forClass(Uri.class);
        verify(mMockContentResolver, times(2)).delete(argumentCaptor.capture(), any(), any());
        List<Uri> capturedUris = argumentCaptor.getAllValues();
        Uri uri = getUriWithGivenPrefix(capturedUris,
                ResetNetworkConfirmFragment.RESTORE_CARRIERS_URI);

        assertThat(uri).isNotNull();
        assertThat(uri.toString().contains("subId/123")).isTrue();
    }

    private void setUpFragment() throws Throwable {
        String resetNetworkConfirmFragmentTag = "reset_network_confirm_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new ResetNetworkConfirmFragment(),
                            resetNetworkConfirmFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (ResetNetworkConfirmFragment)
                mFragmentManager.findFragmentByTag(resetNetworkConfirmFragmentTag);
        ExtendedMockito.spyOn(mFragment);
    }

    private Uri getUriWithGivenPrefix(List<Uri> uris, String prefix) {
        for (Uri uri : uris) {
            if (uri.toString().startsWith(prefix)) return uri;
        }
        return null;
    }

    private Uri getUriWithGivenPrefix(List<Uri> uris, Uri prefix) {
        for (Uri uri : uris) {
            if (uri.isPathPrefixMatch(prefix)) return uri;
        }
        return null;
    }

    private void setEuiccResetCheckbox(boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(
                mContext.getString(R.string.pk_reset_esim), isChecked).commit();
    }

    private void setNetworkSubscriptionId(String id) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putString(
                mContext.getString(R.string.pk_reset_network_subscription), id).commit();
    }
}
