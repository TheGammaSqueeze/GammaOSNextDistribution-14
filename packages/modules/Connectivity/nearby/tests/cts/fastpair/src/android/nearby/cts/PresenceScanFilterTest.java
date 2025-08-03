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

import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.DataElement;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanRequest;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.nearby.PresenceScanFilter}.
 */
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class PresenceScanFilterTest {

    private static final int RSSI = -40;
    private static final int ACTION = 123;
    private static final byte[] SECRETE_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{0, 1, 1, 1};
    private static final byte[] PUBLIC_KEY = new byte[]{1, 1, 2, 2};
    private static final byte[] ENCRYPTED_METADATA = new byte[]{1, 2, 3, 4, 5};
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG = new byte[]{1, 1, 3, 4, 5};
    private static final int KEY = 3;
    private static final byte[] VALUE = new byte[]{1, 1, 1, 1};

    private PublicCredential mPublicCredential =
            new PublicCredential.Builder(SECRETE_ID, AUTHENTICITY_KEY, PUBLIC_KEY,
                    ENCRYPTED_METADATA, METADATA_ENCRYPTION_KEY_TAG)
                    .setIdentityType(IDENTITY_TYPE_PRIVATE)
                    .build();
    private PresenceScanFilter.Builder mBuilder = new PresenceScanFilter.Builder()
            .setMaxPathLoss(RSSI)
            .addCredential(mPublicCredential)
            .addPresenceAction(ACTION)
            .addExtendedProperty(new DataElement(KEY, VALUE));

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBuilder() {
        PresenceScanFilter filter = mBuilder.build();

        assertThat(filter.getMaxPathLoss()).isEqualTo(RSSI);
        assertThat(filter.getCredentials().get(0).getIdentityType()).isEqualTo(
                IDENTITY_TYPE_PRIVATE);
        assertThat(filter.getPresenceActions()).containsExactly(ACTION);
        assertThat(filter.getExtendedProperties().get(0).getKey()).isEqualTo(KEY);

    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWriteParcel() {
        PresenceScanFilter filter = mBuilder.build();

        Parcel parcel = Parcel.obtain();
        filter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PresenceScanFilter parcelFilter = PresenceScanFilter.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(parcelFilter.getType()).isEqualTo(ScanRequest.SCAN_TYPE_NEARBY_PRESENCE);
        assertThat(parcelFilter.getMaxPathLoss()).isEqualTo(RSSI);
        assertThat(parcelFilter.getPresenceActions()).containsExactly(ACTION);
        assertThat(parcelFilter.getExtendedProperties().get(0).getKey()).isEqualTo(KEY);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void describeContents() {
        PresenceScanFilter filter = mBuilder.build();
        assertThat(filter.describeContents()).isEqualTo(0);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCreatorNewArray() {
        PresenceScanFilter[] filters =
                PresenceScanFilter.CREATOR.newArray(2);
        assertThat(filters.length).isEqualTo(2);
    }
}
