/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.sharedconnectivity.cts.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.net.wifi.sharedconnectivity.app.SharedConnectivitySettingsState;
import android.os.Build;
import android.os.Parcel;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;

/**
 * CTS tests for {@link SharedConnectivitySettingsState}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@NonMainlineTest
public class SharedConnectivitySettingsStateTest {
    private static final boolean INSTANT_TETHER_STATE = true;
    private static final String INTENT_ACTION = "instant.tether.settings";

    private static final boolean INSTANT_TETHER_STATE_1 = false;
    private static final String INTENT_ACTION_1 = "instant.tether.settings1";

    @Test
    public void pendingIntentMutable_buildShouldThrow() {
        SharedConnectivitySettingsState.Builder builder =
                new SharedConnectivitySettingsState.Builder()
                        .setInstantTetherEnabled(INSTANT_TETHER_STATE)
                        .setInstantTetherSettingsPendingIntent(PendingIntent.getActivity(
                                ApplicationProvider.getApplicationContext(), 0,
                                new Intent(INTENT_ACTION).setComponent(new ComponentName(
                                        "com.test.package", "TestClass")),
                                PendingIntent.FLAG_MUTABLE));

        Exception e = assertThrows(IllegalArgumentException.class, builder::build);
        assertThat(e.getMessage()).contains("Pending intent must be immutable");
    }

    @Test
    public void parcelOperation() {
        SharedConnectivitySettingsState state = buildSettingsStateBuilder(INTENT_ACTION).build();

        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SharedConnectivitySettingsState fromParcel =
                SharedConnectivitySettingsState.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel).isEqualTo(state);
        assertThat(fromParcel.hashCode()).isEqualTo(state.hashCode());
    }

    @Test
    public void parcelOperation_noPendingIntent() {
        SharedConnectivitySettingsState state = buildSettingsStateBuilder(null).build();

        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SharedConnectivitySettingsState fromParcel =
                SharedConnectivitySettingsState.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel).isEqualTo(state);
        assertThat(fromParcel.hashCode()).isEqualTo(state.hashCode());
    }

    @Test
    public void equalsOperation() {
        SharedConnectivitySettingsState state1 = buildSettingsStateBuilder(INTENT_ACTION).build();
        SharedConnectivitySettingsState state2 = buildSettingsStateBuilder(INTENT_ACTION).build();
        assertThat(state1).isEqualTo(state2);

        SharedConnectivitySettingsState.Builder builder = buildSettingsStateBuilder(INTENT_ACTION)
                .setInstantTetherEnabled(INSTANT_TETHER_STATE_1);
        assertThat(builder.build()).isNotEqualTo(state1);

        builder = buildSettingsStateBuilder(INTENT_ACTION_1);
        assertThat(builder.build()).isNotEqualTo(state1);

        builder = buildSettingsStateBuilder(null);
        assertThat(builder.build()).isNotEqualTo(state1);
    }

    @Test
    public void getMethods() {
        SharedConnectivitySettingsState state = buildSettingsStateBuilder(INTENT_ACTION).build();
        SharedConnectivitySettingsState state1 = buildSettingsStateBuilder(null).build();

        assertThat(state.isInstantTetherEnabled()).isEqualTo(INSTANT_TETHER_STATE);
        assertThat(state.getInstantTetherSettingsPendingIntent())
                .isEqualTo(buildPendingIntent(INTENT_ACTION));
        assertThat(state1.getInstantTetherSettingsPendingIntent()).isNull();
    }

    @Test
    public void hashCodeCalculation() {
        SharedConnectivitySettingsState state1 = buildSettingsStateBuilder(INTENT_ACTION).build();
        SharedConnectivitySettingsState state2 = buildSettingsStateBuilder(INTENT_ACTION).build();

        assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
    }

    @Test
    public void hashCodeCalculation_noPendingIntent() {
        SharedConnectivitySettingsState state1 = buildSettingsStateBuilder(null).build();
        SharedConnectivitySettingsState state2 = buildSettingsStateBuilder(null).build();

        assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
    }

    private SharedConnectivitySettingsState.Builder buildSettingsStateBuilder(String intentAction) {
        SharedConnectivitySettingsState.Builder builder =
                new SharedConnectivitySettingsState.Builder()
                        .setInstantTetherEnabled(INSTANT_TETHER_STATE);
        if (intentAction == null) {
            return builder;
        }
        return builder.setInstantTetherSettingsPendingIntent(buildPendingIntent(intentAction));
    }

    private PendingIntent buildPendingIntent(String intentAction) {
        return PendingIntent.getActivity(
                ApplicationProvider.getApplicationContext(), 0,
                new Intent(intentAction), PendingIntent.FLAG_IMMUTABLE);
    }
}
