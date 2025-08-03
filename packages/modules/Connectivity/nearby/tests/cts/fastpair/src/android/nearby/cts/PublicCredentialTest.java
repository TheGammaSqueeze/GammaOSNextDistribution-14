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

import static android.nearby.PresenceCredential.CREDENTIAL_TYPE_PUBLIC;
import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.CredentialElement;
import android.nearby.PresenceCredential;
import android.nearby.PublicCredential;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Tests for {@link PresenceCredential}. */
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class PublicCredentialTest {

    private static final byte[] SECRETE_ID = new byte[] {1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[] {0, 1, 1, 1};
    private static final byte[] PUBLIC_KEY = new byte[] {1, 1, 2, 2};
    private static final byte[] ENCRYPTED_METADATA = new byte[] {1, 2, 3, 4, 5};
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG = new byte[] {1, 1, 3, 4, 5};
    private static final String KEY = "KEY";
    private static final byte[] VALUE = new byte[] {1, 2, 3, 4, 5};

    private PublicCredential.Builder mBuilder;

    @Before
    public void setUp() {
        mBuilder =
                new PublicCredential.Builder(
                                SECRETE_ID,
                                AUTHENTICITY_KEY,
                                PUBLIC_KEY,
                                ENCRYPTED_METADATA,
                                METADATA_ENCRYPTION_KEY_TAG)
                        .addCredentialElement(new CredentialElement(KEY, VALUE))
                        .setIdentityType(IDENTITY_TYPE_PRIVATE);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBuilder() {
        PublicCredential credential = mBuilder.build();

        assertThat(credential.getType()).isEqualTo(CREDENTIAL_TYPE_PUBLIC);
        assertThat(credential.getIdentityType()).isEqualTo(IDENTITY_TYPE_PRIVATE);
        assertThat(credential.getCredentialElements().get(0).getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(credential.getSecretId(), SECRETE_ID)).isTrue();
        assertThat(Arrays.equals(credential.getAuthenticityKey(), AUTHENTICITY_KEY)).isTrue();
        assertThat(Arrays.equals(credential.getPublicKey(), PUBLIC_KEY)).isTrue();
        assertThat(Arrays.equals(credential.getEncryptedMetadata(), ENCRYPTED_METADATA)).isTrue();
        assertThat(
                        Arrays.equals(
                                credential.getEncryptedMetadataKeyTag(),
                                METADATA_ENCRYPTION_KEY_TAG))
                .isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWriteParcel() {
        PublicCredential credential = mBuilder.build();

        Parcel parcel = Parcel.obtain();
        credential.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PublicCredential credentialFromParcel = PublicCredential.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(credentialFromParcel.getType()).isEqualTo(CREDENTIAL_TYPE_PUBLIC);
        assertThat(credentialFromParcel.getIdentityType()).isEqualTo(IDENTITY_TYPE_PRIVATE);
        assertThat(Arrays.equals(credentialFromParcel.getSecretId(), SECRETE_ID)).isTrue();
        assertThat(Arrays.equals(credentialFromParcel.getAuthenticityKey(), AUTHENTICITY_KEY))
                .isTrue();
        assertThat(Arrays.equals(credentialFromParcel.getPublicKey(), PUBLIC_KEY)).isTrue();
        assertThat(Arrays.equals(credentialFromParcel.getEncryptedMetadata(), ENCRYPTED_METADATA))
                .isTrue();
        assertThat(
                        Arrays.equals(
                                credentialFromParcel.getEncryptedMetadataKeyTag(),
                                METADATA_ENCRYPTION_KEY_TAG))
                .isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEquals() {
        PublicCredential credentialOne =
                new PublicCredential.Builder(
                                SECRETE_ID,
                                AUTHENTICITY_KEY,
                                PUBLIC_KEY,
                                ENCRYPTED_METADATA,
                                METADATA_ENCRYPTION_KEY_TAG)
                        .addCredentialElement(new CredentialElement(KEY, VALUE))
                        .setIdentityType(IDENTITY_TYPE_PRIVATE)
                        .build();

        PublicCredential credentialTwo =
                new PublicCredential.Builder(
                                SECRETE_ID,
                                AUTHENTICITY_KEY,
                                PUBLIC_KEY,
                                ENCRYPTED_METADATA,
                                METADATA_ENCRYPTION_KEY_TAG)
                        .addCredentialElement(new CredentialElement(KEY, VALUE))
                        .setIdentityType(IDENTITY_TYPE_PRIVATE)
                        .build();
        assertThat(credentialOne.equals((Object) credentialTwo)).isTrue();
        assertThat(credentialOne.equals(null)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testUnEquals() {
        byte[] idOne = new byte[] {1, 2, 3, 4};
        byte[] idTwo = new byte[] {4, 5, 6, 7};
        PublicCredential credentialOne =
                new PublicCredential.Builder(
                                idOne,
                                AUTHENTICITY_KEY,
                                PUBLIC_KEY,
                                ENCRYPTED_METADATA,
                                METADATA_ENCRYPTION_KEY_TAG)
                        .build();

        PublicCredential credentialTwo =
                new PublicCredential.Builder(
                                idTwo,
                                AUTHENTICITY_KEY,
                                PUBLIC_KEY,
                                ENCRYPTED_METADATA,
                                METADATA_ENCRYPTION_KEY_TAG)
                        .build();
        assertThat(credentialOne.equals((Object) credentialTwo)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void describeContents() {
        PublicCredential credential = mBuilder.build();
        assertThat(credential.describeContents()).isEqualTo(0);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCreatorNewArray() {
        PublicCredential[] credentials  =
        PublicCredential.CREATOR.newArray(2);
        assertThat(credentials.length).isEqualTo(2);
    }
}
