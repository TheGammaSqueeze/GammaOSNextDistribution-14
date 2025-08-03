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

package com.android.adservices.service.adselection.encryption;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.DBEncryptionContext;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.EncapsulatedSharedSecret;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;

import com.google.common.io.BaseEncoding;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

public class ObliviousHttpRequestContextMarshallerTest {

    private static final long CONTEXT_ID_1 = 1L;
    private static final long CONTEXT_ID_2 = 2L;
    private static final String SHARED_SECRET_STRING = "1";
    private static final String INPUT_KEY_CONFIG_HEX_WITH_MULTIPLE_ALGORITHMS =
            "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                    + "00080001000100010003";
    private static final byte[] INPUT_KEY_CONFIG_WITH_MULTIPLE_ALGORITHMS_BYTES =
            BaseEncoding.base16().lowerCase().decode(INPUT_KEY_CONFIG_HEX_WITH_MULTIPLE_ALGORITHMS);

    private static final String STORED_KEY_CONFIG_HEX_WITH_SINGLE_ALGORITHM =
            "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                    + "000400010001";
    private static final byte[] STORED_KEY_CONFIG_WITH_SINGLE_ALGORITHM_BYTES =
            BaseEncoding.base16().lowerCase().decode(STORED_KEY_CONFIG_HEX_WITH_SINGLE_ALGORITHM);

    private static final byte[] SHARED_SECRET_BYTES =
            SHARED_SECRET_STRING.getBytes(StandardCharsets.UTF_8);

    private static final byte[] SEED_BYTES =
            "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c"
                    .getBytes(StandardCharsets.UTF_8);
    private EncryptionContextDao mEncryptionContextDao;
    private ObliviousHttpRequestContextMarshaller mObliviousHttpRequestContextMarshaller;
    private Clock mClock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mEncryptionContextDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .encryptionContextDao();

        mObliviousHttpRequestContextMarshaller =
                new ObliviousHttpRequestContextMarshaller(mEncryptionContextDao);
        mClock = CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
    }

    @Test
    public void test_insertContext_success() throws Exception {
        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_1,
                                EncryptionKeyConstants.EncryptionKeyType
                                        .ENCRYPTION_KEY_TYPE_AUCTION))
                .isNull();

        ObliviousHttpRequestContext context =
                ObliviousHttpRequestContext.create(
                        ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                                INPUT_KEY_CONFIG_WITH_MULTIPLE_ALGORITHMS_BYTES),
                        EncapsulatedSharedSecret.create(SHARED_SECRET_BYTES),
                        SEED_BYTES);

        mObliviousHttpRequestContextMarshaller.insertAuctionEncryptionContext(
                CONTEXT_ID_1, context);

        DBEncryptionContext dbEncryptionContext =
                mEncryptionContextDao.getEncryptionContext(
                        CONTEXT_ID_1,
                        EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION);
        assertThat(dbEncryptionContext).isNotNull();
        assertThat(dbEncryptionContext.getContextId()).isEqualTo(CONTEXT_ID_1);
        // Serialize ObliviousHttpKeyConfig selects one algorithm to serialize instead of all
        // of them.
        assertThat(dbEncryptionContext.getKeyConfig())
                .isEqualTo(STORED_KEY_CONFIG_WITH_SINGLE_ALGORITHM_BYTES);
        assertThat(dbEncryptionContext.getSharedSecret()).isEqualTo(SHARED_SECRET_BYTES);
        assertThat(dbEncryptionContext.getSeed()).isEqualTo(SEED_BYTES);
    }

    @Test
    public void test_getContext_success() throws Exception {
        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_2,
                                EncryptionKeyConstants.EncryptionKeyType
                                        .ENCRYPTION_KEY_TYPE_AUCTION))
                .isNull();
        mEncryptionContextDao.insertEncryptionContext(
                DBEncryptionContext.builder()
                        .setContextId(CONTEXT_ID_2)
                        .setEncryptionKeyType(
                                EncryptionKeyConstants.EncryptionKeyType
                                        .ENCRYPTION_KEY_TYPE_AUCTION)
                        .setCreationInstant(mClock.instant())
                        .setKeyConfig(STORED_KEY_CONFIG_WITH_SINGLE_ALGORITHM_BYTES)
                        .setSharedSecret(SHARED_SECRET_BYTES)
                        .setSeed(SEED_BYTES)
                        .build());

        ObliviousHttpRequestContext expectedOhttpContext =
                mObliviousHttpRequestContextMarshaller.getAuctionOblivioushttpRequestContext(
                        CONTEXT_ID_2);

        assertThat(expectedOhttpContext).isNotNull();
        assertThat(expectedOhttpContext.keyConfig())
                .isEqualTo(
                        ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                                STORED_KEY_CONFIG_WITH_SINGLE_ALGORITHM_BYTES));
        assertThat(expectedOhttpContext.encapsulatedSharedSecret())
                .isEqualTo(EncapsulatedSharedSecret.create(SHARED_SECRET_BYTES));
        assertThat(expectedOhttpContext.seed()).isEqualTo(SEED_BYTES);
    }

    /** Test to verify that a getContext call where context Id is absent throws an IAE. */
    @Test
    public void test_getContext_contextMissing_throwsIAE() throws Exception {
        assertThat(
                        mEncryptionContextDao.getEncryptionContext(
                                CONTEXT_ID_2,
                                EncryptionKeyConstants.EncryptionKeyType
                                        .ENCRYPTION_KEY_TYPE_AUCTION))
                .isNull();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mObliviousHttpRequestContextMarshaller
                                .getAuctionOblivioushttpRequestContext(CONTEXT_ID_2));
    }
}
