/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.certinstaller;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.security.Credentials;
import android.security.KeyChain;
import android.security.KeyStore;
import android.os.Process;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
public final class CertInstallerRoboTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application.getApplicationContext();
    }

    @Test
    public void testNameNotNullPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);
        intent.putExtra(KeyChain.EXTRA_NAME, "name");
        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(helper.getName()).isEqualTo("name");
    }

    @Test
    public void testReferrerNotNullPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);
        intent.putExtra(Intent.EXTRA_REFERRER, "referrer");
        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(helper.getReferrer()).isEqualTo("referrer");
    }

    /**
     * mUid should be ignored if EXTRA_CERTIFICATE_USAGE is provided.
     * if EXTRA_CERTIFICATE_USAGE equals CERTIFICATE_USAGE_WIFI,
     * mUid should equal Process.WIFI_UID
     */
    @Test
    public void testUidUsageEqualWifiPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, Process.INVALID_UID);
        intent.putExtra(Credentials.EXTRA_CERTIFICATE_USAGE, Credentials.CERTIFICATE_USAGE_WIFI);

        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(helper.getCertUsageSelected()).isEqualTo(Credentials.CERTIFICATE_USAGE_WIFI);
        assertThat(helper.getUid()).isEqualTo(Process.WIFI_UID);

    }

    /**
     * mUid should be ignored if EXTRA_CERTIFICATE_USAGE is provided.
     * if EXTRA_CERTIFICATE_USAGE does not equal CERTIFICATE_USAGE_WIFI,
     * mUid should equal KeyStore.UID_SELF
     */
    @Test
    public void testUidUsageNotNullPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, Process.INVALID_UID);
        intent.putExtra(Credentials.EXTRA_CERTIFICATE_USAGE, "usage");

        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(helper.getUid()).isEqualTo(KeyStore.UID_SELF);
        assertThat(helper.getCertUsageSelected()).isEqualTo("usage");
    }

    /**
     * if EXTRA_CERTIFICATE_USAGE is not provided,
     * mUid should equal the Credentials.EXTRA_INSTALL_AS_UID extra
     */
    @Test
    public void testUidUsageNullPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, 100);

        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(helper.getUid()).isEqualTo(100);
    }

    @Test
    public void testIntentGetsMultipleKeysPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);
        intent.putExtra("key1", CA_CERTIFICATE_1);
        intent.putExtra("key2", CA_CERTIFICATE_2);

        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(helper.getData("key1")).isEqualTo(CA_CERTIFICATE_1);
        assertThat(helper.getData("key2")).isEqualTo(CA_CERTIFICATE_2);
    }
    @Test
    public void testIntentNullPass() throws Exception {
        Intent intent = new Intent(mContext, CertInstaller.class);

        CredentialHelper helper = startActivityAndGetCredentialHelper(intent);

        assertThat(!helper.containsAnyRawData()).isTrue();
        assertThat(helper.getName()).isEqualTo("");
        assertThat(helper.getUid()).isEqualTo(Process.INVALID_UID);
        assertThat(helper.getReferrer()).isEqualTo("");
        assertThat(helper.getCertUsageSelected()).isEqualTo("");
    }

    private CredentialHelper startActivityAndGetCredentialHelper(Intent intent) {
        ActivityController<CertInstaller> controller =
                Robolectric.buildActivity(CertInstaller.class, intent);

        CertInstaller activity = controller
                .create()
                .start()
                .resume()
                .visible()
                .get();
        return activity.getCredentials();
    }

    public static final byte[] CA_CERTIFICATE_1 = new byte[]{
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x8a, (byte) 0x30, (byte) 0x82
    };

    public static final byte[] CA_CERTIFICATE_2 = new byte[]{
            (byte) 0x01, (byte) 0xf3, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01
    };

}