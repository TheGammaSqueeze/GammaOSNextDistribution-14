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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.AllowLists.ALLOW_ALL;
import static com.android.adservices.service.common.AllowLists.toHexString;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit tests for {@link com.android.adservices.service.common.AllowLists} */
@SmallTest
public class AllowListsTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String SOME_PACKAGE_NAME = "SomePackageName";
    private static final String EMPTY_LIST = "";

    private static MockitoSession sStaticMockitoSession;
    private static byte[] sSignature1;
    private static byte[] sSignature2;
    private static byte[] sSignature3;
    private static String sHexString1;
    private static String sHexString2;
    private static String sHexString3;

    @Mock PackageManager mMockPackageManager;
    @Mock SigningInfo mMockSigningInfo;
    @Mock Context mMockContext;

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        prepareSignatures();
        MockitoAnnotations.initMocks(this);

        // Mock Signing Info for signatures.
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.signingInfo = mMockSigningInfo;
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(packageInfo)
                .when(mMockPackageManager)
                .getPackageInfo(SOME_PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES);

        sStaticMockitoSession =
                ExtendedMockito.mockitoSession().spyStatic(AllowLists.class).startMocking();
    }

    @After
    public void tearDown() {
        sStaticMockitoSession.finishMocking();
    }

    @Test
    public void testAppCanUsePpapi_allowAll() {
        assertThat(AllowLists.doesAllowListAllowAll(ALLOW_ALL)).isTrue();
        assertThat(AllowLists.isPackageAllowListed(ALLOW_ALL, SOME_PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testAppCanUsePpapi_emptyAllowList() {
        assertThat(AllowLists.doesAllowListAllowAll(EMPTY_LIST)).isFalse();
        assertThat(AllowLists.isPackageAllowListed(EMPTY_LIST, SOME_PACKAGE_NAME)).isFalse();
        assertThat(AllowLists.splitAllowList(EMPTY_LIST)).isEmpty();
    }

    @Test
    public void testAppCanUsePpapi_notEmptyAllowList() {
        String allowList = SOME_PACKAGE_NAME + ",AnotherPackageName";
        assertThat(AllowLists.doesAllowListAllowAll(allowList)).isFalse();
        assertThat(AllowLists.isPackageAllowListed(allowList, "notAllowedPackageName")).isFalse();
        assertThat(AllowLists.isPackageAllowListed(allowList, SOME_PACKAGE_NAME)).isTrue();
        assertThat(AllowLists.isPackageAllowListed(allowList, "AnotherPackageName")).isTrue();
        assertThat(AllowLists.splitAllowList(allowList))
                .containsExactly(SOME_PACKAGE_NAME, "AnotherPackageName");
    }

    @Test
    public void testAppCanUsePpapi_havingSpaces() {
        // Allow list contains leading/trailing spaces
        String listWithSpace =
                SOME_PACKAGE_NAME + ", PackageName1,PackageName2 ,  PackageName3    ";
        assertThat(AllowLists.isPackageAllowListed(listWithSpace, SOME_PACKAGE_NAME)).isTrue();
        assertThat(AllowLists.isPackageAllowListed(listWithSpace, "PackageName1")).isTrue();
        assertThat(AllowLists.isPackageAllowListed(listWithSpace, "PackageName2")).isTrue();
        assertThat(AllowLists.isPackageAllowListed(listWithSpace, "PackageName3")).isTrue();
        assertThat(AllowLists.splitAllowList(listWithSpace))
                .containsExactly(SOME_PACKAGE_NAME, "PackageName1", "PackageName2", "PackageName3");
    }

    @Test
    public void testAppCanUsePpapi_havingLineSeparators() {
        // Allow list contains leading/trailing line separators
        String listWithLineSeparator =
                SOME_PACKAGE_NAME + ",\nPackageName1,PackageName2\n,\n\nPackageName3\n\n\n";
        assertThat(AllowLists.isPackageAllowListed(listWithLineSeparator, SOME_PACKAGE_NAME))
                .isTrue();
        assertThat(AllowLists.isPackageAllowListed(listWithLineSeparator, "PackageName1")).isTrue();
        assertThat(AllowLists.isPackageAllowListed(listWithLineSeparator, "PackageName2")).isTrue();
        assertThat(AllowLists.isPackageAllowListed(listWithLineSeparator, "PackageName3")).isTrue();
        assertThat(AllowLists.splitAllowList(listWithLineSeparator))
                .containsExactly(SOME_PACKAGE_NAME, "PackageName1", "PackageName2", "PackageName3");
    }

    @Test
    public void testSignatureAllowList_allowAll() {
        assertThat(AllowLists.isSignatureAllowListed(mContext, ALLOW_ALL, SOME_PACKAGE_NAME))
                .isTrue();
    }

    @Test
    public void testSignatureAllowList_noSignature() {
        mockSignature(null);
        assertThat(AllowLists.isSignatureAllowListed(mContext, EMPTY_LIST, SOME_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testSignatureAllowList_emptySignatureAllowList() {
        mockSignature(sSignature1);
        assertThat(AllowLists.isSignatureAllowListed(mContext, EMPTY_LIST, SOME_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testSignatureAllowList_nonEmptySignatureAllowList() {
        String signatureAllowList = sHexString1 + "," + sHexString2;
        mockSignature(sSignature1);
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mContext, signatureAllowList, SOME_PACKAGE_NAME))
                .isTrue();
        mockSignature(sSignature2);
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mContext, signatureAllowList, SOME_PACKAGE_NAME))
                .isTrue();
        mockSignature(sSignature3);
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mContext, signatureAllowList, SOME_PACKAGE_NAME))
                .isFalse();
    }

    @Test
    public void testSignatureAllowList_havingSpace() {
        // Allow list contains leading/trailing spaces
        String listWithSpace = sHexString1 + " , " + sHexString2 + ",  " + sHexString3 + "   ";
        mockSignature(sSignature1);
        assertThat(AllowLists.isSignatureAllowListed(mContext, listWithSpace, SOME_PACKAGE_NAME))
                .isTrue();
        mockSignature(sSignature2);
        assertThat(AllowLists.isSignatureAllowListed(mContext, listWithSpace, SOME_PACKAGE_NAME))
                .isTrue();
        mockSignature(sSignature3);
        assertThat(AllowLists.isSignatureAllowListed(mContext, listWithSpace, SOME_PACKAGE_NAME))
                .isTrue();

        // Allow list contains leading/trailing line separators
        String listWithLineSeparator =
                sHexString1 + "\n,\n" + sHexString2 + ",\n\n" + sHexString3 + "\n\n\n";
        mockSignature(sSignature1);
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mContext, listWithLineSeparator, SOME_PACKAGE_NAME))
                .isTrue();
        mockSignature(sSignature2);
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mContext, listWithLineSeparator, SOME_PACKAGE_NAME))
                .isTrue();
        mockSignature(sSignature3);
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mContext, listWithLineSeparator, SOME_PACKAGE_NAME))
                .isTrue();
    }

    @Test
    public void testSignatureAllowList_checkLatestSignature() {
        // If an app has multiple signatures, check the latest one.
        Signature signature1 = new Signature(sHexString1);
        Signature signature2 = new Signature(sHexString2);
        doReturn(new Signature[] {signature1, signature2})
                .when(mMockSigningInfo)
                .getSigningCertificateHistory();

        // Signature String -> digested byte array -> hashed hex string
        // i.e. sHexString1 -> hashedHexString1, sHexString2 -> hashedHexString2.
        String hashedHexString1 =
                "e9ff0e6e6de95da56ff09f4e3e0f481d67585f0a68aafdeef0f86f7b8533ce17";
        String hashedHexString2 =
                "318b8f30815253bcae6eef8ff3dbd52effd2cdc1f68ef6adbf4bd4dbe7646cb0";
        // If allow list has only signature1, the app is not allowed as signature1 is not latest
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mMockContext, hashedHexString1, SOME_PACKAGE_NAME))
                .isFalse();

        // The app is allowed if signature2 is correctly contained in the allow-list.
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mMockContext, hashedHexString2, SOME_PACKAGE_NAME))
                .isTrue();
        assertThat(
                        AllowLists.isSignatureAllowListed(
                                mMockContext,
                                hashedHexString1 + "," + hashedHexString2,
                                SOME_PACKAGE_NAME))
                .isTrue();
    }

    @Test
    public void testToHexString() {
        assertThat(toHexString(sSignature1)).isEqualTo(sHexString1);
        assertThat(toHexString(sSignature2)).isEqualTo(sHexString2);
    }

    @Test
    public void checkGetAppSignatureHashes() {
        Signature signature1 = new Signature(sHexString1);
        doReturn(new Signature[] {signature1})
                .when(mMockSigningInfo)
                .getSigningCertificateHistory();

        // Got this Hashes by adding temporary logging to print and running this test
        byte[] expectedByteHashes1 =
                new byte[] {
                    -23, -1, 14, 110, 109, -23, 93, -91, 111, -16, -97, 78, 62, 15, 72, 29, 103, 88,
                    95, 10, 104, -86, -3, -18, -16, -8, 111, 123, -123, 51, -50, 23
                };

        byte[] actualHash1 = AllowLists.getAppSignatureHash(mMockContext, SOME_PACKAGE_NAME);
        assertThat(actualHash1).isEqualTo(expectedByteHashes1);
    }

    @Test
    public void checkGetAppSignatureHashes_multipleSignatures() {
        // Only the current signature will be checked.
        Signature signature1 = new Signature(sHexString1);
        Signature signature2 = new Signature(sHexString2);
        doReturn(new Signature[] {signature1, signature2})
                .when(mMockSigningInfo)
                .getSigningCertificateHistory();

        // Got this hashes by adding temporary logging to print the hash and running this test
        byte[] expectedByteHashes2 =
                new byte[] {
                    49, -117, -113, 48, -127, 82, 83, -68, -82, 110, -17, -113, -13, -37, -43, 46,
                    -1, -46, -51, -63, -10, -114, -10, -83, -65, 75, -44, -37, -25, 100, 108, -80
                };

        byte[] actualHash2 = AllowLists.getAppSignatureHash(mMockContext, SOME_PACKAGE_NAME);
        assertThat(actualHash2).isEqualTo(expectedByteHashes2);
    }

    private void mockSignature(@NonNull byte[] signature) {
        ExtendedMockito.doReturn(signature)
                .when(() -> AllowLists.getAppSignatureHash(any(Context.class), anyString()));
    }

    private static void prepareSignatures() {
        sSignature1 = new byte[20];
        sSignature2 = new byte[20];
        sSignature3 = new byte[20];
        sSignature1[19] = 1;
        sSignature2[19] = 2;
        sSignature3[19] = 3;

        sHexString1 = "0000000000000000000000000000000000000001";
        sHexString2 = "0000000000000000000000000000000000000002";
        sHexString3 = "0000000000000000000000000000000000000003";
    }
}
