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

import static android.nearby.PresenceCredential.CREDENTIAL_TYPE_PRIVATE;
import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.CredentialElement;
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

/**
 * Tests for {@link PrivateCredential}.
 */
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class PrivateCredentialTest {
    private static final String DEVICE_NAME = "myDevice";
    private static final byte[] SECRETE_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{0, 1, 1, 1};
    private static final String KEY = "SecreteId";
    private static final byte[] VALUE = new byte[]{1, 2, 3, 4, 5};
    private static final byte[] METADATA_ENCRYPTION_KEY = new byte[]{1, 1, 3, 4, 5};

    private PrivateCredential.Builder mBuilder;

    @Before
    public void setUp() {
        mBuilder = new PrivateCredential.Builder(
                SECRETE_ID, AUTHENTICITY_KEY, METADATA_ENCRYPTION_KEY, DEVICE_NAME)
                .setIdentityType(IDENTITY_TYPE_PRIVATE)
                .addCredentialElement(new CredentialElement(KEY, VALUE));
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testBuilder() {
        PrivateCredential credential = mBuilder.build();

        assertThat(credential.getType()).isEqualTo(CREDENTIAL_TYPE_PRIVATE);
        assertThat(credential.getIdentityType()).isEqualTo(IDENTITY_TYPE_PRIVATE);
        assertThat(credential.getDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(Arrays.equals(credential.getSecretId(), SECRETE_ID)).isTrue();
        assertThat(Arrays.equals(credential.getAuthenticityKey(), AUTHENTICITY_KEY)).isTrue();
        assertThat(Arrays.equals(credential.getMetadataEncryptionKey(),
                METADATA_ENCRYPTION_KEY)).isTrue();
        CredentialElement credentialElement = credential.getCredentialElements().get(0);
        assertThat(credentialElement.getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(credentialElement.getValue(), VALUE)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testWriteParcel() {
        PrivateCredential credential = mBuilder.build();

        Parcel parcel = Parcel.obtain();
        credential.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PrivateCredential credentialFromParcel = PrivateCredential.CREATOR.createFromParcel(
                parcel);
        parcel.recycle();

        assertThat(credentialFromParcel.getType()).isEqualTo(CREDENTIAL_TYPE_PRIVATE);
        assertThat(credentialFromParcel.getIdentityType()).isEqualTo(IDENTITY_TYPE_PRIVATE);
        assertThat(Arrays.equals(credentialFromParcel.getSecretId(), SECRETE_ID)).isTrue();
        assertThat(Arrays.equals(credentialFromParcel.getAuthenticityKey(),
                AUTHENTICITY_KEY)).isTrue();
        assertThat(Arrays.equals(credentialFromParcel.getMetadataEncryptionKey(),
                METADATA_ENCRYPTION_KEY)).isTrue();
        CredentialElement credentialElement = credentialFromParcel.getCredentialElements().get(0);
        assertThat(credentialElement.getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(credentialElement.getValue(), VALUE)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void describeContents() {
        PrivateCredential credential = mBuilder.build();
        assertThat(credential.describeContents()).isEqualTo(0);
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testCreatorNewArray() {
        PrivateCredential[]  credentials =
                PrivateCredential.CREATOR.newArray(2);
        assertThat(credentials.length).isEqualTo(2);
    }
}
