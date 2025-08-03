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

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBEncryptionContext;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.EncapsulatedSharedSecret;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;

import java.security.spec.InvalidKeySpecException;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/** Marshalls DBEncryptionContext into ObliviousHttpRequestContext and vice-versa. */
public class ObliviousHttpRequestContextMarshaller {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final EncryptionContextDao mEncryptionContextDao;
    private final Clock mClock;

    public ObliviousHttpRequestContextMarshaller(EncryptionContextDao encryptionContextDao) {
        Objects.requireNonNull(encryptionContextDao);
        mEncryptionContextDao = encryptionContextDao;
        // TODO(b/235841960): Use the same injected clock as AdSelectionRunner
        //  after aligning on Clock usage
        mClock = Clock.systemUTC();
    }

    /** Fetches from EncryptionContextDao the ObliviousHttpRequestContext for given contextId. */
    public ObliviousHttpRequestContext getAuctionOblivioushttpRequestContext(long contextId)
            throws InvalidKeySpecException {
        DBEncryptionContext dbEncryptionContext =
                mEncryptionContextDao.getEncryptionContext(
                        contextId,
                        EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION);

        if (Objects.isNull(dbEncryptionContext)) {
            String err =
                    String.format(
                            Locale.ENGLISH,
                            "Encryption context cannot be found for the given id: %s",
                            contextId);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
        return ObliviousHttpRequestContext.create(
                ObliviousHttpKeyConfig.fromSerializedKeyConfig(dbEncryptionContext.getKeyConfig()),
                EncapsulatedSharedSecret.create(dbEncryptionContext.getSharedSecret()),
                dbEncryptionContext.getSeed());
    }

    /** Inserts the given ObliviousHttpRequestContext into the DB. */
    public void insertAuctionEncryptionContext(
            long contextId, ObliviousHttpRequestContext requestContext) {
        Objects.requireNonNull(requestContext);
        Objects.requireNonNull(requestContext.seed());
        Objects.requireNonNull(requestContext.keyConfig());
        Objects.requireNonNull(requestContext.keyConfig().serializeKeyConfigToBytes());
        Objects.requireNonNull(requestContext.encapsulatedSharedSecret());
        Objects.requireNonNull(requestContext.encapsulatedSharedSecret().serializeToBytes());

        mEncryptionContextDao.insertEncryptionContext(
                DBEncryptionContext.builder()
                        .setContextId(contextId)
                        .setEncryptionKeyType(EncryptionKeyConstants.from(AUCTION))
                        .setCreationInstant(mClock.instant())
                        .setSeed(requestContext.seed())
                        .setKeyConfig(requestContext.keyConfig().serializeKeyConfigToBytes())
                        .setSharedSecret(
                                requestContext.encapsulatedSharedSecret().serializeToBytes())
                        .build());
    }
}
