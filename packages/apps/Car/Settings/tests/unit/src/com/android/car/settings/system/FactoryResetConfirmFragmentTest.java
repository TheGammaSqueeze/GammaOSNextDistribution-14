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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.Settings;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
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

@RunWith(AndroidJUnit4.class)
public class FactoryResetConfirmFragmentTest {

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private FactoryResetConfirmFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MenuItem mResetButton;

    private int mDeviceProvisioned;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Mock
    private PersistentDataBlockManager mPersistentDataBlockManager;
    @Mock
    private OemLockManager mOemLockManager;
    @Mock
    private FragmentActivity mMockActivity;

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);

        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivity.getSupportFragmentManager();
        mDeviceProvisioned = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0);
        // Default to not provisioned.
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                0);

        setUpFragment();
        when(mFragment.requireContext()).thenReturn(mContext);
        when(mContext.getSystemService(PersistentDataBlockManager.class))
                .thenReturn(mPersistentDataBlockManager);
        when(mContext.getSystemService(OemLockManager.class)).thenReturn(mOemLockManager);
        when(mFragment.requireActivity()).thenReturn(mMockActivity);
        when(mFragment.getActivity()).thenReturn(mMockActivity);
        mResetButton = mActivity.getToolbar().getMenuItems().get(0);
    }

    @After
    public void tearDown() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                mDeviceProvisioned);
    }

    @Test
    public void confirmClicked_sendsResetIntent() throws Throwable {
        triggerFactoryResetConfirmButton();

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivity).sendBroadcast(argumentCaptor.capture());
        Intent resetIntent = argumentCaptor.getValue();
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
        assertThat(resetIntent.getPackage()).isEqualTo("android");
        assertThat(resetIntent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND).isEqualTo(
                Intent.FLAG_RECEIVER_FOREGROUND);
        assertThat(resetIntent.getExtras().getString(Intent.EXTRA_REASON)).isEqualTo(
                "MasterClearConfirm");
    }

    @Test
    public void confirmClicked_resetEsimFalse_resetIntentReflectsChoice() throws Throwable {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(
                mContext.getString(R.string.pk_factory_reset_reset_esim), false).commit();

        triggerFactoryResetConfirmButton();

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivity).sendBroadcast(argumentCaptor.capture());
        Intent resetIntent = argumentCaptor.getValue();
        assertThat(resetIntent.getExtras().getBoolean(Intent.EXTRA_WIPE_ESIMS)).isEqualTo(false);
    }

    @Test
    public void confirmClicked_pdbManagerNull_sendsResetIntent() throws Throwable {
        when(mContext.getSystemService(PersistentDataBlockManager.class)).thenReturn(null);

        triggerFactoryResetConfirmButton();

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivity).sendBroadcast(argumentCaptor.capture());
        Intent resetIntent = argumentCaptor.getValue();
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    @Test
    public void confirmClicked_oemUnlockAllowed_doesNotWipePdb() throws Throwable {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(true);

        triggerFactoryResetConfirmButton();

        verify(mPersistentDataBlockManager, never()).wipe();
    }

    @Test
    public void confirmClicked_oemUnlockAllowed_sendsResetIntent() throws Throwable {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(true);

        triggerFactoryResetConfirmButton();

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivity).sendBroadcast(argumentCaptor.capture());
        Intent resetIntent = argumentCaptor.getValue();
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_notProvisioned_doesNotWipePdb() throws Throwable {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);

        triggerFactoryResetConfirmButton();

        verify(mPersistentDataBlockManager, never()).wipe();
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_notProvisioned_sendsResetIntent()
            throws Throwable {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);

        triggerFactoryResetConfirmButton();

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivity).sendBroadcast(argumentCaptor.capture());
        Intent resetIntent = argumentCaptor.getValue();
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_provisioned_wipesPdb() throws Throwable {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                1);

        triggerFactoryResetConfirmButton();
        // wait for async task
        PollingCheck.waitFor(
                () -> mFragment.mPersistentDataWipeTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mPersistentDataBlockManager).wipe();
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_provisioned_sendsResetIntent() throws Throwable {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                1);

        triggerFactoryResetConfirmButton();
        // wait for async task
        PollingCheck.waitFor(
                () -> mFragment.mPersistentDataWipeTask.getStatus() == AsyncTask.Status.FINISHED);

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivity).sendBroadcast(argumentCaptor.capture());
        Intent resetIntent = argumentCaptor.getValue();
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    private void setUpFragment() throws Throwable {
        String factoryResetConfirmFragmentTag = "factory_reset_confirm_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(
                            R.id.fragment_container, new TestFactoryResetConfirmFragment(),
                            factoryResetConfirmFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (FactoryResetConfirmFragment)
                mFragmentManager.findFragmentByTag(factoryResetConfirmFragmentTag);
        ExtendedMockito.spyOn(mFragment);
    }

    private void triggerFactoryResetConfirmButton() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> mResetButton.performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    public static class TestFactoryResetConfirmFragment extends FactoryResetConfirmFragment {
        @Override
        ProgressDialog getProgressDialog() {
            ProgressDialog dialog = mock(ProgressDialog.class);
            doNothing().when(dialog).show();
            doNothing().when(dialog).hide();
            return dialog;
        }
    }
}
