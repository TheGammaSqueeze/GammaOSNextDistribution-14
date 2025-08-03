/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.KAnonObliviousHttpEncryptorImpl;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import com.google.common.annotations.VisibleForTesting;

import java.time.Clock;

/** A factory class used to create an instance of {@link KAnonSignJoinManager}. */
@RequiresApi(Build.VERSION_CODES.S)
public class KAnonSignJoinFactory {

    private static Context mContext;
    private KAnonSignJoinManager mKAnonSignJoinManager;

    /**
     * Returns an instance for this class. Once you have the instance you can use {@link
     * KAnonSignJoinFactory#getKAnonSignJoinManager()} to create an instance of {@link
     * KAnonSignJoinManager}
     */
    public KAnonSignJoinFactory(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Returns an instance for this class. Once you have the instance you can use {@link
     * KAnonSignJoinFactory#getKAnonSignJoinManager()} to create an instance of {@link
     * KAnonSignJoinManager}. This constructor should only be used for testing.
     */
    @VisibleForTesting
    public KAnonSignJoinFactory(@NonNull KAnonSignJoinManager kAnonSignJoinManager) {
        mKAnonSignJoinManager = kAnonSignJoinManager;
    }

    /** Returns an instance of {@link KAnonSignJoinManager}. */
    public KAnonSignJoinManager getKAnonSignJoinManager() {
        if (mKAnonSignJoinManager == null) {
            AdServicesHttpsClient adServicesHttpsClient =
                    new AdServicesHttpsClient(
                            AdServicesExecutors.getBlockingExecutor(),
                            CacheProviderFactory.create(mContext, FlagsFactory.getFlags()));
            AdSelectionEncryptionKeyManager encryptionKeyManager =
                    new AdSelectionEncryptionKeyManager(
                            AdSelectionServerDatabase.getInstance(mContext).encryptionKeyDao(),
                            FlagsFactory.getFlags(),
                            adServicesHttpsClient,
                            AdServicesExecutors.getLightWeightExecutor());
            KAnonObliviousHttpEncryptorImpl kAnonObliviousHttpEncryptor =
                    new KAnonObliviousHttpEncryptorImpl(
                            encryptionKeyManager, AdServicesExecutors.getLightWeightExecutor());
            KAnonMessageManager kAnonMessageManager =
                    new KAnonMessageManager(
                            KAnonDatabase.getInstance(mContext).kAnonMessageDao(),
                            FlagsFactory.getFlags(),
                            Clock.systemUTC());
            KeyAttestationFactory keyAttestationFactory = new KeyAttestationFactory(mContext);
            KAnonCallerImpl kAnonCaller =
                    new KAnonCallerImpl(
                            AdServicesExecutors.getLightWeightExecutor(),
                            new AnonymousCountingTokensImpl(),
                            adServicesHttpsClient,
                            KAnonDatabase.getInstance(mContext).clientParametersDao(),
                            KAnonDatabase.getInstance(mContext).serverParametersDao(),
                            UserProfileIdManager.getInstance(mContext),
                            new BinaryHttpMessageDeserializer(),
                            FlagsFactory.getFlags(),
                            kAnonObliviousHttpEncryptor,
                            kAnonMessageManager,
                            AdServicesLoggerImpl.getInstance(),
                            keyAttestationFactory);
            mKAnonSignJoinManager =
                    new KAnonSignJoinManager(
                            mContext,
                            kAnonCaller,
                            kAnonMessageManager,
                            FlagsFactory.getFlags(),
                            Clock.systemUTC(),
                            AdServicesLoggerImpl.getInstance());
        }
        return mKAnonSignJoinManager;
    }
}
