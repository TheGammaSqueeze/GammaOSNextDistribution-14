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

package com.android.car.settings.sound;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.sound.VolumeSeekBarPreference.SavedVolumeState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class VolumeSeekBarPreferenceTest {

    private Context mContext = ApplicationProvider.getApplicationContext();

    private VolumeSeekBarPreference mVolumeSeekBarPreference;

    @Mock
    private Drawable mMockMutedIconDrawable;

    @Mock
    private Drawable mMockUnMutedIconDrawable;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mVolumeSeekBarPreference = new VolumeSeekBarPreference(mContext);
    }

    @Test
    public void constructor_failOnNullContext() {
        NullPointerException thrown =
                expectThrows(NullPointerException.class, () -> new VolumeSeekBarPreference(null));
        assertWithMessage("Constructor Exception").that(thrown).hasMessageThat()
                .contains("Context");
    }

    @Test
    public void setMutedIcon_succeedsInSettingMutedIcon() {
        mVolumeSeekBarPreference.setMutedIcon(mMockMutedIconDrawable);

        assertWithMessage("Volume muted icon")
                .that(mVolumeSeekBarPreference.getMutedIcon()).isEqualTo(mMockMutedIconDrawable);
    }

    @Test
    public void setUnMutedIcon_succeedsInSettingUnMutedIcon() {
        mVolumeSeekBarPreference.setUnMutedIcon(mMockUnMutedIconDrawable);

        assertWithMessage("Volume un-muted icon")
                .that(mVolumeSeekBarPreference.getUnMutedIcon())
                .isEqualTo(mMockUnMutedIconDrawable);
    }

    @Test
    public void setMutedIcon_withDefaults_setsIconToUnMuted() {
        mVolumeSeekBarPreference.setUnMutedIcon(mMockUnMutedIconDrawable);
        mVolumeSeekBarPreference.setMutedIcon(mMockMutedIconDrawable);

        assertWithMessage("Preference icon")
                .that(mVolumeSeekBarPreference.getIcon()).isEqualTo(mMockUnMutedIconDrawable);
    }

    @Test
    public void setMutedIcon_afterSetIsMuteToTrue_setsIconToMuted() {
        mVolumeSeekBarPreference.setIsMuted(true);

        mVolumeSeekBarPreference.setMutedIcon(mMockMutedIconDrawable);

        assertWithMessage("Preference icon")
                .that(mVolumeSeekBarPreference.getIcon()).isEqualTo(mMockMutedIconDrawable);
    }

    @Test
    public void setMutedIcon_afterSetIsMuteToFalse_setsIconToUnMuted() {
        mVolumeSeekBarPreference.setIsMuted(true);

        mVolumeSeekBarPreference.setMutedIcon(mMockMutedIconDrawable);

        assertWithMessage("Preference icon")
                .that(mVolumeSeekBarPreference.getIcon()).isEqualTo(mMockMutedIconDrawable);
    }

    @Test
    public void setIsMuted_withMuted_succeeds() {
        mVolumeSeekBarPreference.setIsMuted(true);

        assertWithMessage("Is muted state")
                .that(mVolumeSeekBarPreference.isMuted()).isEqualTo(true);
    }

    @Test
    public void setIsMuted_withUnMuted_succeeds() {
        mVolumeSeekBarPreference.setIsMuted(false);

        assertWithMessage("Is muted state")
                .that(mVolumeSeekBarPreference.isMuted()).isEqualTo(false);
    }

    @Test
    public void setIsMuted_withMutedState_changesIconToMutedIcon() {
        mVolumeSeekBarPreference.setMutedIcon(mMockMutedIconDrawable);
        mVolumeSeekBarPreference.setUnMutedIcon(mMockUnMutedIconDrawable);
        mVolumeSeekBarPreference.setIsMuted(false);

        mVolumeSeekBarPreference.setIsMuted(true);

        assertWithMessage("Preference icon")
                .that(mVolumeSeekBarPreference.getIcon()).isEqualTo(mMockMutedIconDrawable);
    }

    @Test
    public void setIsMuted_withUnMutedState_changesIconToUnMutedIcon() {
        mVolumeSeekBarPreference.setMutedIcon(mMockMutedIconDrawable);
        mVolumeSeekBarPreference.setUnMutedIcon(mMockUnMutedIconDrawable);
        mVolumeSeekBarPreference.setIsMuted(true);

        mVolumeSeekBarPreference.setIsMuted(false);

        assertWithMessage("Preference icon")
                .that(mVolumeSeekBarPreference.getIcon()).isEqualTo(mMockUnMutedIconDrawable);
    }

    @Test
    public void onSaveInstanceState_withPersistent_doesNotReturnVolumeSavedState() {
        mVolumeSeekBarPreference.setPersistent(true);
        Parcelable parcelable = mVolumeSeekBarPreference.onSaveInstanceState();

        assertWithMessage("Saved state").that(parcelable.getClass())
                .isNotEqualTo(SavedVolumeState.class);
    }

    @Test
    public void onSaveInstanceState_withoutPersistent_doesReturnVolumeSavedState() {
        mVolumeSeekBarPreference.setPersistent(false);

        Parcelable parcelable = mVolumeSeekBarPreference.onSaveInstanceState();

        assertWithMessage("Saved state").that(parcelable.getClass())
                .isEqualTo(SavedVolumeState.class);
    }

    @Test
    public void onSaveInstanceState_withPersistent_returnsLastSetIsMuted() {
        mVolumeSeekBarPreference.setPersistent(false);
        mVolumeSeekBarPreference.setIsMuted(true);

        Parcelable parcelable = mVolumeSeekBarPreference.onSaveInstanceState();

        SavedVolumeState state =
                ((SavedVolumeState) parcelable);

        assertWithMessage("Saved state is muted").that(state.isMuted())
                .isEqualTo(true);
    }

    @Test
    public void onRestoreInstanceState_withMutedState_loadsIsUnMuted() {
        Parcel parcel = mock(Parcel.class);
        when(parcel.readBoolean()).thenReturn(false);
        SavedVolumeState savedState = new SavedVolumeState(parcel);
        mVolumeSeekBarPreference.setIsMuted(true);

        mVolumeSeekBarPreference.onRestoreInstanceState(savedState);

        assertWithMessage("Loaded is un-muted")
                .that(mVolumeSeekBarPreference.isMuted())
                .isEqualTo(false);
    }

    @Test
    public void onRestoreInstanceState_withMutedState_loadsIsMuted() {
        Parcel parcel = mock(Parcel.class);
        when(parcel.readBoolean()).thenReturn(true);
        SavedVolumeState savedState = new SavedVolumeState(parcel);
        mVolumeSeekBarPreference.setIsMuted(false);

        mVolumeSeekBarPreference.onRestoreInstanceState(savedState);

        assertWithMessage("Loaded is muted")
                .that(mVolumeSeekBarPreference.isMuted())
                .isEqualTo(true);
    }

    @Test
    public void writeToParcel_withMuted_writesMutedToParcel() {
        Parcel parcel = mock(Parcel.class);
        SavedVolumeState savedState = new SavedVolumeState(parcel);
        savedState.setIsMuted(true);

        savedState.writeToParcel(parcel, /* flags= */ 0);

        verify(parcel).writeBoolean(true);
    }

    @Test
    public void isMuted_forSaveVolumeState_readMutedState() {
        Parcel parcel = mock(Parcel.class);
        SavedVolumeState savedState = new SavedVolumeState(parcel);
        savedState.setIsMuted(true);

        assertWithMessage("Set is muted")
                .that(savedState.isMuted())
                .isEqualTo(true);
    }

    @Test
    public void isMuted_loadsIsMutedFromParcel() {
        Parcel parcel = mock(Parcel.class);
        when(parcel.readBoolean()).thenReturn(true);
        SavedVolumeState savedState = new SavedVolumeState(parcel);


        assertWithMessage("Loaded is muted")
                .that(savedState.isMuted())
                .isEqualTo(true);
    }

}
