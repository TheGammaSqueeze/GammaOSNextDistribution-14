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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.TestFinishActivity;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public abstract class SettingsFragmentTestCase<T extends BaseCarSettingsTestActivity> {
    protected static final String TEST_TAG = "test_tag";

    protected Context mContext = ApplicationProvider.getApplicationContext();
    protected BaseCarSettingsTestActivity mActivity;
    protected FragmentManager mFragmentManager;
    protected SettingsFragment mFragment;

    abstract ActivityTestRule<T> getActivityTestRule();

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = getActivityTestRule().getActivity();
        mFragmentManager = mActivity.getSupportFragmentManager();
        setUpFragment();
    }

    @Test
    @UiThreadTest
    public void use_returnsController() {
        mFragment.onCreate(null);
        assertThat(mFragment.use(FakePreferenceController.class,
                R.string.tpk_fake_controller)).isNotNull();
    }

    @Test
    public void onAttach_registersLifecycleObservers() throws Throwable {
        AtomicReference<FakePreferenceController> controller = new AtomicReference<>();
        getActivityTestRule().runOnUiThread(() -> {
            mFragment.onCreate(null);
            controller.set(mFragment.use(FakePreferenceController.class,
                    R.string.tpk_fake_controller));
            assertThat(controller.get().getOnCreateInternalCallCount()).isEqualTo(1);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        getActivityTestRule().finishActivity();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(controller.get().getOnCreateInternalCallCount()).isEqualTo(1);
    }

    @Test
    @UiThreadTest
    public void onUxRestrictionsChanged_propagatesToControllers() {
        mFragment.onCreate(null);
        FakePreferenceController controller = mFragment.use(FakePreferenceController.class,
                R.string.tpk_fake_controller);
        CarUxRestrictions uxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD, /* timestamp= */ 0).build();
        mFragment.onUxRestrictionsChanged(uxRestrictions);
        assertThat(controller.getUxRestrictions()).isEqualTo(uxRestrictions);
    }

    @Test
    @UiThreadTest
    public void onUxRestrictedPreferenceTapped_showToast() {
        MockitoSession session = ExtendedMockito.mockitoSession().mockStatic(Toast.class,
                withSettings().lenient()).startMocking();
        Toast mockToast = mock(Toast.class);
        ExtendedMockito.when(Toast.makeText(any(), anyString(), anyInt()))
                .thenReturn(mockToast);

        mFragment.onCreate(null);
        FakePreferenceController controller = mFragment.use(FakePreferenceController.class,
                R.string.tpk_fake_controller);
        CarUxRestrictions uxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, /* timestamp= */ 0).build();
        mFragment.onUxRestrictionsChanged(uxRestrictions);
        controller.getPreference().performClick();
        verify(mockToast).show();

        session.finishMocking();
    }

    @Test
    @UiThreadTest
    public void onDisplayPreferenceDialog_unknownPreferenceType_throwsIllegalArgumentException() {
        mFragment.onCreate(null);

        assertThrows(IllegalArgumentException.class,
                () -> mFragment.onDisplayPreferenceDialog(new Preference(mContext)));
    }

    @Test
    @UiThreadTest
    public void launchFragment_dialogFragment_throwsError() {
        mFragment.onCreate(null);
        DialogFragment dialogFragment = new DialogFragment();
        assertThrows(IllegalArgumentException.class,
                () -> mFragment.launchFragment(dialogFragment));
    }

    @Test
    @UiThreadTest
    public void showDialog_noTag_launchesDialogFragment() {
        mFragment.onCreate(null);
        DialogFragment dialogFragment = mock(DialogFragment.class);
        mFragment.showDialog(dialogFragment, /* tag= */ null);
        verify(dialogFragment).show(mFragment.getFragmentManager(), null);
    }

    @Test
    @UiThreadTest
    public void showDialog_withTag_launchesDialogFragment() {
        mFragment.onCreate(null);
        DialogFragment dialogFragment = mock(DialogFragment.class);
        mFragment.showDialog(dialogFragment, TEST_TAG);
        verify(dialogFragment).show(mFragment.getFragmentManager(), TEST_TAG);
    }

    @Test
    public void findDialogByTag_retrieveOriginalDialog_returnsDialog() throws Throwable {
        DialogFragment dialogFragment = new DialogFragment();
        getActivityTestRule().runOnUiThread(() -> {
            mFragment.onCreate(null);
            mFragment.showDialog(dialogFragment, TEST_TAG);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(mFragment.findDialogByTag(TEST_TAG)).isEqualTo(dialogFragment);
    }

    @Test
    @UiThreadTest
    public void findDialogByTag_notDialogFragment_returnsNull() {
        mFragment.onCreate(null);
        TestSettingsFragment
                fragment = new TestSettingsFragment();
        mFragment.getFragmentManager().beginTransaction().add(fragment, TEST_TAG).commit();
        assertThat(mFragment.findDialogByTag(TEST_TAG)).isNull();
    }

    @Test
    @UiThreadTest
    public void findDialogByTag_noSuchFragment_returnsNull() {
        mFragment.onCreate(null);
        assertThat(mFragment.findDialogByTag(TEST_TAG)).isNull();
    }

    @Test
    @UiThreadTest
    public void startActivityForResult_largeRequestCode_throwsError() {
        mFragment.onCreate(null);
        assertThrows(() -> mFragment.startActivityForResult(new Intent(), 0xffff,
                mock(ActivityResultCallback.class)));
    }

    @Test
    @UiThreadTest
    public void startActivityForResult_tooManyRequests_throwsError() {
        mFragment.onCreate(null);
        assertThrows(() -> {
            for (int i = 0; i < 0xff; i++) {
                mFragment.startActivityForResult(new Intent(), i,
                        mock(ActivityResultCallback.class));
            }
        });
    }

    @Test
    @UiThreadTest
    public void startIntentSenderForResult_largeRequestCode_throwsError() {
        mFragment.onCreate(null);
        assertThrows(
                () -> mFragment.startIntentSenderForResult(
                        mock(IntentSender.class), /* requestCode= */ 0xffff,
                        /* fillInIntent= */ null, /* flagsMask= */ 0,
                        /* flagsValues= */ 0, /* options= */ null,
                        mock(ActivityResultCallback.class)));
    }

    @Test
    @UiThreadTest
    public void startIntentSenderForResult_tooManyRequests_throwsError() {
        mFragment.onCreate(null);
        assertThrows(() -> {
            for (int i = 0; i < 0xff; i++) {
                mFragment.startIntentSenderForResult(
                        mock(IntentSender.class), /* requestCode= */ 0xffff,
                        /* fillInIntent= */ null, /* flagsMask= */ 0,
                        /* flagsValues= */ 0, /* options= */ null,
                        mock(ActivityResultCallback.class));
            }
        });
    }

    @Test
    @UiThreadTest
    public void onActivityResult_hasValidRequestCode_triggersOnActivityResult() {
        mFragment.onCreate(null);
        ActivityResultCallback callback = mock(ActivityResultCallback.class);
        Intent intent = new Intent(mContext, TestFinishActivity.class);

        int reqCode = 100;
        int resCode = -1;
        mFragment.startActivityForResult(intent, reqCode, callback);
        int fragmentReqCode = (1 << 8) + reqCode;
        mFragment.onActivityResult(fragmentReqCode, resCode, intent);
        verify(callback).processActivityResult(eq(reqCode), eq(resCode), any(Intent.class));
    }

    @Test
    @UiThreadTest
    public void onActivityResult_wrongRequestCode_doesntTriggerOnActivityResult() {
        mFragment.onCreate(null);
        ActivityResultCallback callback = mock(ActivityResultCallback.class);
        Intent intent = new Intent(mContext, TestFinishActivity.class);

        int reqCode = 100;
        int resCode = -1;
        mFragment.startActivityForResult(intent, reqCode, callback);
        int fragmentReqCode = (2 << 8) + reqCode;
        mFragment.onActivityResult(fragmentReqCode, resCode, intent);
        verify(callback, never()).processActivityResult(anyInt(), anyInt(),
                any(Intent.class));
    }

    protected void setUpFragment() throws Throwable {
        setUpFragment(null);
    }

    protected void setUpFragment(Fragment fragment) throws Throwable {
        String settingsFragmentTag = "settings_fragment";
        getActivityTestRule().runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container,
                            fragment != null ? fragment : new TestSettingsFragment(),
                            settingsFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (SettingsFragment) mFragmentManager.findFragmentByTag(settingsFragmentTag);
    }

    /** Concrete {@link SettingsFragment} for testing. */
    public static class TestSettingsFragment extends SettingsFragment {

        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.test_base_settings_fragment;
        }

    }
}
