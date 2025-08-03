/*
 * Copyright 2018 The Android Open Source Project
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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit test for {@link PreferenceController}.
 */
@RunWith(RobolectricTestRunner.class)
public class PreferenceControllerTest {

    private static final CarUxRestrictions NO_SETUP_UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, /* timestamp= */ 0).build();

    private static final CarUxRestrictions BASELINE_UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

    private PreferenceControllerTestHelper<FakePreferenceController> mControllerHelper;
    private FakePreferenceController mController;
    private Context mContext;

    @Mock
    private Preference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                FakePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
    }

    @Test
    public void setPreference_wrongType_throwsIllegalArgumentException() {
        PreferenceControllerTestHelper<WrongTypePreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext, WrongTypePreferenceController.class);

        assertThrows(IllegalArgumentException.class, () -> controllerHelper.setPreference(
                new Preference(mContext)));
    }

    @Test
    public void setPreference_correctType_setsPreference() {
        assertThat(mController.getPreference()).isEqualTo(mPreference);
    }

    @Test
    public void setPreference_callsCheckInitialized() {
        assertThat(mController.getCheckInitializedCallCount()).isEqualTo(1);
    }

    @Test
    public void setPreference_registersOnPreferenceChangeListener() {
        ArgumentCaptor<Preference.OnPreferenceChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(Preference.OnPreferenceChangeListener.class);
        Object newValue = new Object();

        verify(mPreference).setOnPreferenceChangeListener(listenerArgumentCaptor.capture());
        listenerArgumentCaptor.getValue().onPreferenceChange(mPreference, newValue);

        assertThat(mController.getHandlePreferenceChangedCallCount()).isEqualTo(1);
        assertThat(mController.getHandlePreferenceChangedPreferenceArg()).isEqualTo(mPreference);
        assertThat(mController.getHandlePreferenceChangedValueArg()).isEqualTo(newValue);
    }

    @Test
    public void setPreference_registersOnPreferenceClickListener() {
        ArgumentCaptor<Preference.OnPreferenceClickListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(Preference.OnPreferenceClickListener.class);

        verify(mPreference).setOnPreferenceClickListener(listenerArgumentCaptor.capture());
        listenerArgumentCaptor.getValue().onPreferenceClick(mPreference);

        assertThat(mController.getHandlePreferenceClickedCallCount()).isEqualTo(1);
        assertThat(mController.getHandlePreferenceClickedArg()).isEqualTo(mPreference);
    }

    @Test
    public void onUxRestrictionsChanged_updatesUxRestrictions() {
        mController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        assertThat(mController.getUxRestrictions()).isEqualTo(NO_SETUP_UX_RESTRICTIONS);
    }

    @Test
    public void onUxRestrictionsChanged_created_restricted_preferenceDisabled() {
        mControllerHelper.markState(Lifecycle.State.CREATED);

        mController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        verify(mPreference).setEnabled(false);
    }

    @Test
    public void onUxRestrictionsChanged_created_restricted_unrestricted_preferenceEnabled() {
        InOrder orderVerifier = inOrder(mPreference);

        mControllerHelper.markState(Lifecycle.State.CREATED);
        mController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);
        mController.onUxRestrictionsChanged(BASELINE_UX_RESTRICTIONS);

        // setEnabled(true) called on Create.
        orderVerifier.verify(mPreference).setEnabled(true);

        // setEnabled(false) called with the first UXR change event.
        orderVerifier.verify(mPreference).setEnabled(false);

        // setEnabled(true) called with the second UXR change event.
        orderVerifier.verify(mPreference).setEnabled(true);
    }

    @Test
    public void onUxRestrictionsChanged_restricted_allPreferencesIgnore_preferenceEnabled() {
        // mPreference cannot be a Mock here because its real methods need to be invoked.
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                FakePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();

        Set preferencesIgnoringUxRestrictions = new HashSet();
        mController.setUxRestrictionsIgnoredConfig(/* allIgnores= */ true,
                preferencesIgnoringUxRestrictions);
        mControllerHelper.markState(Lifecycle.State.CREATED);
        mController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onUxRestrictionsChanged_restricted_thisPreferenceIgnores_preferenceEnabled() {
        // mPreference cannot be a Mock here because its real methods need to be invoked.
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                FakePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();

        Set preferencesIgnoringUxRestrictions = new HashSet();
        preferencesIgnoringUxRestrictions.add(PreferenceControllerTestHelper.getKey());
        mController.setUxRestrictionsIgnoredConfig(/* allIgnores= */ false,
                preferencesIgnoringUxRestrictions);
        mControllerHelper.markState(Lifecycle.State.CREATED);
        mController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onUxRestrictionsChanged_restricted_uxRestrictionsNotIgnored_preferenceDisabled() {
        // mPreference cannot be a Mock here because its real methods need to be invoked.
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                FakePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();

        Set preferencesIgnoringUxRestrictions = new HashSet();
        mController.setUxRestrictionsIgnoredConfig(/* allIgnores= */ false,
                preferencesIgnoringUxRestrictions);
        mControllerHelper.markState(Lifecycle.State.CREATED);
        mController.onUxRestrictionsChanged(NO_SETUP_UX_RESTRICTIONS);

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailable() {
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailable_zoneWrite() {
        mController.setAvailabilityStatusForZone("write");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailable_zoneRead() {
        mController.setAvailabilityStatusForZone("read");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailable_zoneHidden() {
        mController.setAvailabilityStatusForZone("hidden");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToConditionallyUnavailable_zoneWrite() {
        mController.setAvailabilityStatus(CONDITIONALLY_UNAVAILABLE);
        mController.setAvailabilityStatusForZone("write");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToConditionallyUnavailable_zoneRead() {
        mController.setAvailabilityStatus(CONDITIONALLY_UNAVAILABLE);
        mController.setAvailabilityStatusForZone("read");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToConditionallyUnavailable_zoneHidden() {
        mController.setAvailabilityStatus(CONDITIONALLY_UNAVAILABLE);
        mController.setAvailabilityStatusForZone("hidden");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToUnsupportedOnDevice_zoneWrite() {
        mController.setAvailabilityStatus(UNSUPPORTED_ON_DEVICE);
        mController.setAvailabilityStatusForZone("write");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToUnsupportedOnDevice_zoneRead() {
        mController.setAvailabilityStatus(UNSUPPORTED_ON_DEVICE);
        mController.setAvailabilityStatusForZone("read");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToUnsupportedOnDevice_zoneHidden() {
        mController.setAvailabilityStatus(UNSUPPORTED_ON_DEVICE);
        mController.setAvailabilityStatusForZone("hidden");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToDisabledForProfile_zoneWrite() {
        mController.setAvailabilityStatus(DISABLED_FOR_PROFILE);
        mController.setAvailabilityStatusForZone("write");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToDisabledForProfile_zoneRead() {
        mController.setAvailabilityStatus(DISABLED_FOR_PROFILE);
        mController.setAvailabilityStatusForZone("read");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToDisabledForProfile_zoneHidden() {
        mController.setAvailabilityStatus(DISABLED_FOR_PROFILE);
        mController.setAvailabilityStatusForZone("hidden");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailableForViewing_zoneWrite() {
        mController.setAvailabilityStatus(AVAILABLE_FOR_VIEWING);
        mController.setAvailabilityStatusForZone("write");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailableForViewing_zoneRead() {
        mController.setAvailabilityStatus(AVAILABLE_FOR_VIEWING);
        mController.setAvailabilityStatusForZone("read");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_defaultsToAvailableForViewing_zoneHidden() {
        mController.setAvailabilityStatus(AVAILABLE_FOR_VIEWING);
        mController.setAvailabilityStatusForZone("hidden");
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void lifecycle_unsupportedOnDevice_doesNotCallSubclassHooks() {
        mController.setAvailabilityStatus(UNSUPPORTED_ON_DEVICE);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.markState(Lifecycle.State.DESTROYED);

        assertThat(mController.getOnCreateInternalCallCount()).isEqualTo(0);
        assertThat(mController.getOnStartInternalCallCount()).isEqualTo(0);
        assertThat(mController.getOnResumeInternalCallCount()).isEqualTo(0);
        assertThat(mController.getOnPauseInternalCallCount()).isEqualTo(0);
        assertThat(mController.getOnStopInternalCallCount()).isEqualTo(0);
        assertThat(mController.getOnDestroyInternalCallCount()).isEqualTo(0);
    }

    @Test
    public void onCreate_unsupportedOnDevice_hidesPreference() {
        mController.setAvailabilityStatus(UNSUPPORTED_ON_DEVICE);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        verify(mPreference).setVisible(false);
    }

    @Test
    public void onCreate_callsSubclassHook() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mController.getOnCreateInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void onStart_callsSubclassHook() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mController.getOnStartInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void onResume_callsSubclassHook() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_RESUME);

        assertThat(mController.getOnResumeInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void onPause_callsSubclassHook() {
        mControllerHelper.markState(Lifecycle.State.RESUMED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_PAUSE);

        assertThat(mController.getOnPauseInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void onStop_callsSubclassHook() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(mController.getOnStopInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void onDestroy_callsSubclassHook() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        assertThat(mController.getOnDestroyInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void handlePreferenceChanged_defaultReturnsTrue() {
        assertThat(mController.handlePreferenceChanged(mPreference, new Object())).isTrue();
    }

    @Test
    public void handlePreferenceClicked_defaultReturnsFalse() {
        assertThat(mController.handlePreferenceClicked(mPreference)).isFalse();
    }

    /** For testing passing the wrong type of preference to the controller. */
    private static class WrongTypePreferenceController extends
            PreferenceController<PreferenceGroup> {

        WrongTypePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<PreferenceGroup> getPreferenceType() {
            return PreferenceGroup.class;
        }
    }
}
