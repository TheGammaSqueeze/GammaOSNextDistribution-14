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

package android.nearby.cts;

import static android.nearby.BroadcastRequest.BROADCAST_TYPE_NEARBY_PRESENCE;
import static android.nearby.BroadcastRequest.PRESENCE_VERSION_V0;
import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.DataElement;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PrivateCredential;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests for {@link PresenceBroadcastRequest}.
 */
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class PresenceBroadcastRequestTest {

    private static final int VERSION = PRESENCE_VERSION_V0;
    private static final int TX_POWER = 1;
    private static final byte[] SALT = new byte[]{1, 2};
    private static final int ACTION_ID = 123;
    private static final int BLE_MEDIUM = 1;
    private static final byte[] SECRETE_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{0, 1, 1, 1};
    private static final byte[] METADATA_ENCRYPTION_KEY = new byte[]{1, 1, 3, 4, 5};
    private static final int KEY = 3;
    private static final byte[] VALUE = new byte[]{1, 1, 1, 1};
    private static final String DEVICE_NAME = "test_device";

    private PresenceBroadcastRequest.Builder mBuilder;

    @Before
    public void setUp() {
        PrivateCredential credential = new PrivateCredential.Builder(SECRETE_ID, AUTHENTICITY_KEY,
                METADATA_ENCRYPTION_KEY, DEVICE_NAME)
                .setIdentityType(IDENTITY_TYPE_PRIVATE)
                .build();
        DataElement element = new DataElement(KEY, VALUE);
        mBuilder = new PresenceBroadcastRequest.Builder(Collections.singletonList(BLE_MEDIUM), SALT,
                credential)
                .setTxPower(TX_POWER)
                .setVersion(VERSION)
                .addAction(ACTION_ID)
                .addExtendedProperty(element);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBuilder() {
        PresenceBroadcastRequest broadcastRequest = mBuilder.build();

        assertThat(broadcastRequest.getVersion()).isEqualTo(VERSION);
        assertThat(Arrays.equals(broadcastRequest.getSalt(), SALT)).isTrue();
        assertThat(broadcastRequest.getTxPower()).isEqualTo(TX_POWER);
        assertThat(broadcastRequest.getActions()).containsExactly(ACTION_ID);
        assertThat(broadcastRequest.getExtendedProperties().get(0).getKey()).isEqualTo(
                KEY);
        assertThat(broadcastRequest.getMediums()).containsExactly(BLE_MEDIUM);
        assertThat(broadcastRequest.getCredential().getIdentityType()).isEqualTo(
                IDENTITY_TYPE_PRIVATE);
        assertThat(broadcastRequest.getType()).isEqualTo(BROADCAST_TYPE_NEARBY_PRESENCE);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWriteParcel() {
        PresenceBroadcastRequest broadcastRequest = mBuilder.build();

        Parcel parcel = Parcel.obtain();
        broadcastRequest.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PresenceBroadcastRequest parcelRequest = PresenceBroadcastRequest.CREATOR.createFromParcel(
                parcel);
        parcel.recycle();

        assertThat(parcelRequest.getTxPower()).isEqualTo(TX_POWER);
        assertThat(parcelRequest.getActions()).containsExactly(ACTION_ID);
        assertThat(parcelRequest.getExtendedProperties().get(0).getKey()).isEqualTo(
                KEY);
        assertThat(parcelRequest.getMediums()).containsExactly(BLE_MEDIUM);
        assertThat(parcelRequest.getCredential().getIdentityType()).isEqualTo(
                IDENTITY_TYPE_PRIVATE);
        assertThat(parcelRequest.getType()).isEqualTo(BROADCAST_TYPE_NEARBY_PRESENCE);

    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void describeContents() {
        PresenceBroadcastRequest broadcastRequest = mBuilder.build();
        assertThat(broadcastRequest.describeContents()).isEqualTo(0);
    }

    @Test
    public void testCreatorNewArray() {
        PresenceBroadcastRequest[] presenceBroadcastRequests =
                PresenceBroadcastRequest.CREATOR.newArray(2);
        assertThat(presenceBroadcastRequests.length).isEqualTo(2);
    }
}
