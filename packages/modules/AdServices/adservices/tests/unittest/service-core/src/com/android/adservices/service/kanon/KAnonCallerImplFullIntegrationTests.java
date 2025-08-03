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

import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.data.common.UserProfileIdDao;
import com.android.adservices.data.kanon.ClientParametersDao;
import com.android.adservices.data.kanon.DBClientParameters;
import com.android.adservices.data.kanon.DBKAnonMessage;
import com.android.adservices.data.kanon.DBServerParameters;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.data.kanon.ServerParametersDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.KAnonObliviousHttpEncryptorImpl;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.cache.CacheDatabase;
import com.android.adservices.service.common.cache.CacheEntryDao;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import private_join_and_compute.anonymous_counting_tokens.AndroidRequestMetadata;
import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientRequest;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientResponse;
import private_join_and_compute.anonymous_counting_tokens.RequestMetadata;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.Transcript;

// All the tests of this CL are ignored because they make calls to the actual server and these will
// fail
// in presubmits, but these tests must be run locally while development to make sure that the
// feature is working as intended.
@RunWith(MockitoJUnitRunner.class)
public class KAnonCallerImplFullIntegrationTests {

    private static final long MAX_AGE_SECONDS = 1200000;
    private static final long MAX_ENTRIES = 20;

    private ExecutorService mLightweightExecutorService;
    private AnonymousCountingTokens mAnonymousCountingTokens;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private ClientParametersDao mClientParametersDao;
    private ServerParametersDao mServerParametersDao;
    private KAnonMessageDao mKAnonMessageDao;
    private KAnonObliviousHttpEncryptorImpl mKAnonObliviousHttpEncryptor;
    private BinaryHttpMessageDeserializer mBinaryHttpMessageDeserializer;
    private KAnonMessageManager mKAnonMessageManager;
    private Flags mFlags;
    private EncryptionKeyDao mEncryptionKeyDao;

    private String mServerParamVersion;
    private ClientParameters mClientParameters;
    private String mClientParamsVersion;
    private ServerPublicParameters mServerPublicParameters;
    private RequestMetadata mRequestMetadata;
    private Transcript mTranscript;

    private final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();

    @Mock private Clock mockClock;
    @Mock private com.android.adservices.shared.util.Clock mAdServicesClock;

    @Mock private UserProfileIdDao mockUserProfileIdDao;
    @Mock private AdServicesLogger mockAdServicesLogger;
    @Mock private KeyAttestationFactory mockKeyAttestationFactory;
    private UserProfileIdManager mUserProfileIdManager;

    private final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String GOLDEN_TRANSCRIPT_PATH = "act/golden_transcript_1";

    private Instant FIXED_INSTANT = Instant.now();

    @Before
    public void setup() throws IOException {
        CacheEntryDao cacheEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();

        HttpCache cache = new FledgeHttpCache(cacheEntryDao, MAX_AGE_SECONDS, MAX_ENTRIES);
        mAdServicesHttpsClient = new AdServicesHttpsClient(mExecutorService, cache);
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mAnonymousCountingTokens = new AnonymousCountingTokensImpl();
        KAnonDatabase kAnonDatabase =
                Room.inMemoryDatabaseBuilder(CONTEXT, KAnonDatabase.class).build();
        mClientParametersDao = kAnonDatabase.clientParametersDao();
        mServerParametersDao = kAnonDatabase.serverParametersDao();
        when(mAdServicesClock.currentTimeMillis()).thenReturn(FIXED_INSTANT.toEpochMilli());
        mUserProfileIdManager = new UserProfileIdManager(mockUserProfileIdDao, mAdServicesClock);
        mKAnonMessageDao = kAnonDatabase.kAnonMessageDao();
        mFlags = new KAnonSignAndJoinRunnerTestFlags();
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mFlags, mockClock);
        mEncryptionKeyDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionServerDatabase.class)
                        .build()
                        .encryptionKeyDao();
        mKAnonObliviousHttpEncryptor =
                new KAnonObliviousHttpEncryptorImpl(
                        new AdSelectionEncryptionKeyManager(
                                mEncryptionKeyDao,
                                mFlags,
                                mAdServicesHttpsClient,
                                AdServicesExecutors.getLightWeightExecutor()),
                        AdServicesExecutors.getLightWeightExecutor());
        mBinaryHttpMessageDeserializer = new BinaryHttpMessageDeserializer();

        when(mockClock.instant()).thenReturn(FIXED_INSTANT);

        InputStream inputStream = CONTEXT.getAssets().open(GOLDEN_TRANSCRIPT_PATH);
        mTranscript = Transcript.parseDelimitedFrom(inputStream);
    }

    public static class KAnonSignAndJoinRunnerTestFlags implements Flags {}

    @Test
    @Ignore // ignoring this message because this is one of the test which makes call to
    // actual
    // server.
    public void testKAnonCallerImpl_runsSuccessfully_joinsMessagesCorrectly()
            throws InterruptedException, IOException {
        UUID uuid = UUID.randomUUID();
        when(mockUserProfileIdDao.getUserProfileId()).thenReturn(uuid);
        createAndPersistKAnonMessages();
        KAnonMessageManager kAnonMessageManager =
                new KAnonMessageManager(mKAnonMessageDao, mFlags, mockClock);
        List<KAnonMessageEntity> messageEntities =
                kAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        KAnonCallerImpl kAnonCaller =
                Mockito.spy(
                        new KAnonCallerImpl(
                                mLightweightExecutorService,
                                mAnonymousCountingTokens,
                                mAdServicesHttpsClient,
                                mClientParametersDao,
                                mServerParametersDao,
                                mUserProfileIdManager,
                                mBinaryHttpMessageDeserializer,
                                mFlags,
                                mKAnonObliviousHttpEncryptor,
                                mKAnonMessageManager,
                                mockAdServicesLogger,
                                mockKeyAttestationFactory));
        CountDownLatch countDownLatch = new CountDownLatch(1);
        kAnonCaller.signAndJoinMessages(messageEntities);

        countDownLatch.await();

        List<KAnonMessageEntity> messageEntitiesAfterProcessing =
                kAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);

        assertThat(messageEntitiesAfterProcessing.size()).isEqualTo(messageEntities.size());
    }

    @Test
    @Ignore // ignoring this message because this is one of the test which makes call to actual
    // server.
    public void testKAnonCallerImpl_clientParamsAlreadyPresent_doesntFetchClientParams()
            throws InterruptedException, IOException, ExecutionException {
        UUID uuid = UUID.randomUUID();
        when(mockUserProfileIdDao.getUserProfileId()).thenReturn(uuid);
        createAndPersistKAnonMessages();
        generateAndPersistNewClientParametersInDB(uuid);
        List<KAnonMessageEntity> messageEntities =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        List<DBClientParameters> clientParametersInDbBefore =
                mClientParametersDao.getActiveClientParameters(Instant.now());
        KAnonCallerImpl runner =
                new KAnonCallerImpl(
                        mLightweightExecutorService,
                        mAnonymousCountingTokens,
                        mAdServicesHttpsClient,
                        mClientParametersDao,
                        mServerParametersDao,
                        mUserProfileIdManager,
                        mBinaryHttpMessageDeserializer,
                        mFlags,
                        mKAnonObliviousHttpEncryptor,
                        mKAnonMessageManager,
                        mockAdServicesLogger,
                        mockKeyAttestationFactory);
        CountDownLatch countdownLatch = new CountDownLatch(1);
        runner.signAndJoinMessages(messageEntities);
        countdownLatch.await();

        List<KAnonMessageEntity> messageEntitiesAfterProcessing =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);
        List<DBClientParameters> clientParametersInDBAfter =
                mClientParametersDao.getActiveClientParameters(Instant.now());
        assertThat(clientParametersInDBAfter.size()).isEqualTo(1);
        assertThat(clientParametersInDbBefore.get(0).getClientParametersId())
                .isEqualTo(clientParametersInDBAfter.get(0).getClientParametersId());
        assertThat(messageEntitiesAfterProcessing.size()).isEqualTo(messageEntities.size());
    }

    private void createAndPersistKAnonMessages() {
        DBKAnonMessage dbKAnonMessage =
                DBKAnonMessage.builder()
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setCorrespondingClientParametersExpiryInstant(null)
                        .setKanonHashSet(
                                "types/fledge/sets/nEW2Xx96S2B1zRqAgXsX4mRl0MAhgKcYZBb-Lsa5djg")
                        .setExpiryInstant(Instant.now().plusSeconds(7200))
                        .setCreatedAt(Instant.now())
                        .setAdSelectionId(123)
                        .build();
        DBKAnonMessage dbKAnonMessageSecond =
                DBKAnonMessage.builder()
                        .setStatus(KAnonMessageConstants.MessageStatus.NOT_PROCESSED)
                        .setCorrespondingClientParametersExpiryInstant(null)
                        .setKanonHashSet(
                                "types/fledge/sets/mEW2Xx96S2B1zRqAgXsX4mRl0MAhgKcYZBb-Lsa5djg")
                        .setExpiryInstant(Instant.now().plusSeconds(7200))
                        .setCreatedAt(Instant.now())
                        .setAdSelectionId(1234)
                        .build();
        mKAnonMessageDao.insertAllKAnonMessages(List.of(dbKAnonMessage, dbKAnonMessageSecond));
    }

    private RegisterClientResponse generateAndPersistNewClientParametersInDB(UUID userId)
            throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        mClientParameters =
                mAnonymousCountingTokens.generateClientParameters(
                        KAnonUtil.getSchemeParameters(), mServerPublicParameters);

        mRequestMetadata =
                RequestMetadata.newBuilder()
                        .setAndroidRequestMetadata(
                                AndroidRequestMetadata.newBuilder()
                                        .setClientId(
                                                ByteString.copyFrom(userId.toString().getBytes()))
                                        .build())
                        .setAuthTypeValue(RequestMetadata.AuthType.AUTH_DEVICE_ATTESTATION_VALUE)
                        .build();
        RegisterClientRequest registerClientRequest =
                RegisterClientRequest.newBuilder()
                        .setClientPublicParams(mClientParameters.getPublicParameters())
                        .setRequestMetadata(mRequestMetadata)
                        .setServerParamsVersion(mServerParamVersion)
                        .build();

        AdServicesHttpClientRequest registerClientParametersRequest =
                AdServicesHttpClientRequest.create(
                        Uri.parse(mFlags.getFledgeKAnonRegisterClientParametersUrl()),
                        REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE,
                        ImmutableSet.of(),
                        false,
                        DEV_CONTEXT_DISABLED,
                        AdServicesHttpUtil.HttpMethodType.POST,
                        registerClientRequest.toByteArray());

        AdServicesHttpClientResponse response =
                mAdServicesHttpsClient
                        .performRequestGetResponseInBase64String(registerClientParametersRequest)
                        .get();
        RegisterClientResponse registerClientResponse =
                RegisterClientResponse.parseFrom(
                        BaseEncoding.base64().decode(response.getResponseBody()));
        mClientParamsVersion = registerClientResponse.getClientParamsVersion();

        DBClientParameters clientParametersBefore =
                DBClientParameters.builder()
                        .setClientPrivateParameters(
                                mClientParameters.getPrivateParameters().toByteArray())
                        .setClientPublicParameters(
                                mClientParameters.getPublicParameters().toByteArray())
                        .setClientId(mUserProfileIdManager.getOrCreateId())
                        .setClientParametersExpiryInstant(Instant.now().plusSeconds(36000))
                        .setClientParamsVersion(registerClientResponse.getClientParamsVersion())
                        .build();
        mClientParametersDao.insertClientParameters(clientParametersBefore);
        return registerClientResponse;
    }

    private void persistServerParametersInDB() {
        mServerParamVersion = "serverParamVersion";
        mServerPublicParameters = mTranscript.getServerParameters().getPublicParameters();
        DBServerParameters serverParametersToSave =
                DBServerParameters.builder()
                        .setServerPublicParameters(mServerPublicParameters.toByteArray())
                        .setCreationInstant(Instant.now())
                        .setServerParamsJoinExpiryInstant(Instant.now().plusSeconds(3600))
                        .setServerParamsSignExpiryInstant(Instant.now().plusSeconds(3600))
                        .setServerParamsVersion(mServerParamVersion)
                        .build();
        mServerParametersDao.insertServerParameters(serverParametersToSave);
    }
}
