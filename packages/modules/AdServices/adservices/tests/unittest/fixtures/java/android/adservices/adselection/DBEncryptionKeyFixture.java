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

package android.adservices.adselection;

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;
import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN;

import com.android.adservices.data.adselection.DBEncryptionKey;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class DBEncryptionKeyFixture {

    public static final DBEncryptionKey.Builder ENCRYPTION_KEY_AUCTION_TTL_5SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_4")
                    .setPublicKey("public_key_4")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(5L);

    public static final DBEncryptionKey.Builder ENCRYPTION_KEY_JOIN_TTL_5SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_5")
                    .setPublicKey("public_key_5")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(5L);

    public static List<DBEncryptionKey> getKeysExpiringInTtl(long expiryTtlSeconds) {
        return ImmutableList.of(
                ENCRYPTION_KEY_JOIN_TTL_5SECS.setExpiryTtlSeconds(expiryTtlSeconds).build(),
                ENCRYPTION_KEY_AUCTION_TTL_5SECS.setExpiryTtlSeconds(expiryTtlSeconds).build());
    }
}
