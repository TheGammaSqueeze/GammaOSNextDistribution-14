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

import static android.adservices.exceptions.AdServicesNetworkException.ERROR_SERVER;

import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.EMPTY_BODY;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_OHTTP_CONTENT_TYPE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.adservices.exceptions.AdServicesNetworkException;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
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
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessage;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.bhttp.Fields;
import com.android.adservices.service.common.bhttp.ResponseControlData;
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
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Before;
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
import java.util.concurrent.ExecutorService;

import private_join_and_compute.anonymous_counting_tokens.AndroidRequestMetadata;
import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.GeneratedTokensRequestProto;
import private_join_and_compute.anonymous_counting_tokens.GetServerPublicParamsResponse;
import private_join_and_compute.anonymous_counting_tokens.GetTokensRequest;
import private_join_and_compute.anonymous_counting_tokens.GetTokensResponse;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientRequest;
import private_join_and_compute.anonymous_counting_tokens.RequestMetadata;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.TokensSet;
import private_join_and_compute.anonymous_counting_tokens.Transcript;

@RunWith(MockitoJUnitRunner.class)
public class KAnonCallerImplTest {

    private static final long MAX_AGE_SECONDS = 1200000;
    private static final long MAX_ENTRIES = 20;

    private ExecutorService mLightweightExecutorService;
    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private ClientParametersDao mClientParametersDao;
    private ServerParametersDao mServerParametersDao;
    private KAnonMessageDao mKAnonMessageDao;
    private KAnonMessageManager mKAnonMessageManager;
    private Flags mFlags;

    private String mServerParamVersion;
    private ClientParameters mClientParameters;
    private ServerPublicParameters mServerPublicParameters;
    private RequestMetadata mRequestMetadata;
    private Transcript mTranscript;

    private final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();

    @Mock private Clock mockClock;
    @Mock private com.android.adservices.shared.util.Clock mAdServicesClock;
    @Mock private UserProfileIdDao mockUserProfileIdDao;
    @Mock private AdServicesHttpsClient mockAdServicesHttpClient;
    @Mock private AnonymousCountingTokens mockAnonymousCountingTokens;
    @Mock private BinaryHttpMessageDeserializer mockBinaryHttpMessageDeserializer;
    @Mock private ObliviousHttpEncryptor mockKAnonOblivivousHttpEncryptorImpl;
    @Mock private AdServicesLogger mockAdServicesLogger;
    @Mock private KeyAttestationFactory mockKeyAttestationFactory;
    private UserProfileIdManager mUserProfileIdManager;
    private KAnonCallerImpl mKAnonCaller;

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
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        KAnonDatabase kAnonDatabase =
                Room.inMemoryDatabaseBuilder(CONTEXT, KAnonDatabase.class).build();
        mClientParametersDao = kAnonDatabase.clientParametersDao();
        mServerParametersDao = kAnonDatabase.serverParametersDao();
        mUserProfileIdManager = new UserProfileIdManager(mockUserProfileIdDao, mAdServicesClock);
        mKAnonMessageDao = kAnonDatabase.kAnonMessageDao();
        mFlags = new KAnonSignAndJoinRunnerTestFlags(32);
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mFlags, mockClock);

        when(mockClock.instant()).thenReturn(FIXED_INSTANT);
        when(mAdServicesClock.currentTimeMillis()).thenReturn(FIXED_INSTANT.toEpochMilli());

        InputStream inputStream = CONTEXT.getAssets().open(GOLDEN_TRANSCRIPT_PATH);
        mTranscript = Transcript.parseDelimitedFrom(inputStream);
        UUID userId = UUID.randomUUID();
        when(mockUserProfileIdDao.getUserProfileId()).thenReturn(userId);
        mKAnonCaller =
                Mockito.spy(
                        new KAnonCallerImpl(
                                mLightweightExecutorService,
                                mockAnonymousCountingTokens,
                                mockAdServicesHttpClient,
                                mClientParametersDao,
                                mServerParametersDao,
                                mUserProfileIdManager,
                                mockBinaryHttpMessageDeserializer,
                                mFlags,
                                mockKAnonOblivivousHttpEncryptorImpl,
                                mKAnonMessageManager,
                                mockAdServicesLogger,
                                mockKeyAttestationFactory));
    }

    @Test
    public void test_signedJoinedSuccessfully_shouldUpdateKAnonMessageStatusInDB()
            throws IOException, InterruptedException {
        KAnonCallerImpl kAnonCaller =
                Mockito.spy(
                        new KAnonCallerImpl(
                                mLightweightExecutorService,
                                mockAnonymousCountingTokens,
                                mockAdServicesHttpClient,
                                mClientParametersDao,
                                mServerParametersDao,
                                mUserProfileIdManager,
                                mockBinaryHttpMessageDeserializer,
                                mFlags,
                                mockKAnonOblivivousHttpEncryptorImpl,
                                mKAnonMessageManager,
                                mockAdServicesLogger,
                                mockKeyAttestationFactory));
        CountDownLatch countdownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countdownLatch);
        when(mockKAnonOblivivousHttpEncryptorImpl.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenReturn(FluentFuture.from(immediateFuture(EMPTY_BODY)));
        createAndPersistKAnonMessages();
        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        kAnonCaller.signAndJoinMessages(kanonMessageList);
        countdownLatch.await();

        List<KAnonMessageEntity> messagesFromDBAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);

        assertThat(messagesFromDBAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void test_multipleBatches_shouldSignAndJoinMessages()
            throws IOException, InterruptedException {
        Flags flagsWithBatchSizeOne = new KAnonSignAndJoinRunnerTestFlags(1);
        KAnonCallerImpl kAnonCaller =
                Mockito.spy(
                        new KAnonCallerImpl(
                                mLightweightExecutorService,
                                mockAnonymousCountingTokens,
                                mockAdServicesHttpClient,
                                mClientParametersDao,
                                mServerParametersDao,
                                mUserProfileIdManager,
                                mockBinaryHttpMessageDeserializer,
                                flagsWithBatchSizeOne,
                                mockKAnonOblivivousHttpEncryptorImpl,
                                mKAnonMessageManager,
                                mockAdServicesLogger,
                                mockKeyAttestationFactory));
        CountDownLatch countdownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countdownLatch);
        when(mockKAnonOblivivousHttpEncryptorImpl.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenReturn(FluentFuture.from(immediateFuture(EMPTY_BODY)));
        // we are persisting 2 messages and setting match size as 1
        createAndPersistKAnonMessages();
        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        kAnonCaller.signAndJoinMessages(kanonMessageList);
        countdownLatch.await();

        List<KAnonMessageEntity> messagesFromDBAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);

        assertThat(messagesFromDBAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void test_signSuccessfulButJoinUnsuccessful_shouldUpdateKAnonMessageStatusToSignedDB()
            throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countDownLatch);
        createAndPersistKAnonMessages();
        when(mockKAnonOblivivousHttpEncryptorImpl.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenReturn(FluentFuture.from(immediateFuture(EMPTY_BODY)));
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(400).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        when(mockBinaryHttpMessageDeserializer.deserialize(any())).thenReturn(binaryHttpMessage);

        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        mKAnonCaller.signAndJoinMessages(kanonMessageList);
        countDownLatch.await();

        List<KAnonMessageEntity> kanonMessageListAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.SIGNED);

        assertThat(kanonMessageListAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void test_signUnsuccessfulVerifyTokenFails_shouldUpdateKAnonMessageStatusToFailedInDB()
            throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countDownLatch);
        when(mockAnonymousCountingTokens.verifyTokensResponse(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        createAndPersistKAnonMessages();
        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        mKAnonCaller.signAndJoinMessages(kanonMessageList);
        countDownLatch.await();

        List<KAnonMessageEntity> kanonMessageListAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);

        assertThat(kanonMessageListAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void signUnsuccessfulCannotRecoverTokens_shouldUpdateKAnonMessageStatusToFailedInDB()
            throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countDownLatch);
        when(mockAnonymousCountingTokens.recoverTokens(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InvalidProtocolBufferException("some error"));
        createAndPersistKAnonMessages();
        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        mKAnonCaller.signAndJoinMessages(kanonMessageList);
        countDownLatch.await();

        List<KAnonMessageEntity> kanonMessageListAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);

        assertThat(kanonMessageListAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void actGenerateClientParamsFails_shouldNotUpdateKAnonMessageStatusInDB()
            throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countDownLatch);
        createAndPersistKAnonMessages();
        mClientParametersDao.deleteAllClientParameters();
        when(mockAnonymousCountingTokens.generateClientParameters(any(), any()))
                .thenThrow(new InvalidProtocolBufferException("some error"));

        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        mKAnonCaller.signAndJoinMessages(kanonMessageList);
        countDownLatch.await();

        List<KAnonMessageEntity> kanonMessageListAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        assertThat(kanonMessageListAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void registerClientUnsuccessful_httpCallFails_shouldNotUpdateKAnonMessageStatusInDB()
            throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countDownLatch);
        createAndPersistKAnonMessages();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();
        mClientParameters = mTranscript.getClientParameters();
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
        when(mockAdServicesHttpClient.performRequestGetResponseInBase64String(
                        registerClientParametersRequest))
                .thenReturn(
                        Futures.immediateFailedFuture(
                                new AdServicesNetworkException(ERROR_SERVER)));

        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        mKAnonCaller.signAndJoinMessages(kanonMessageList);
        countDownLatch.await();

        List<KAnonMessageEntity> kanonMessageListAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        assertThat(kanonMessageListAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void fetchServerParamsUnsuccessful_shouldNotUpdateKAnonMessageStatusInDB()
            throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        setupMockWithCountDownLatch(countDownLatch);
        createAndPersistKAnonMessages();
        mServerParametersDao.deleteAllServerParameters();
        mClientParameters = mTranscript.getClientParameters();
        AdServicesHttpClientRequest fetchServerPublicParametersRequest =
                AdServicesHttpClientRequest.create(
                        Uri.parse(mFlags.getFledgeKAnonFetchServerParamsUrl()),
                        REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE,
                        ImmutableSet.of(),
                        false,
                        DEV_CONTEXT_DISABLED,
                        AdServicesHttpUtil.HttpMethodType.GET,
                        AdServicesHttpUtil.EMPTY_BODY);
        when(mockAdServicesHttpClient.performRequestGetResponseInBase64String(
                        fetchServerPublicParametersRequest))
                .thenReturn(
                        Futures.immediateFailedFuture(
                                new AdServicesNetworkException(ERROR_SERVER)));

        List<KAnonMessageEntity> kanonMessageList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        mKAnonCaller.signAndJoinMessages(kanonMessageList);
        countDownLatch.await();

        List<KAnonMessageEntity> kanonMessageListAfter =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        2, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);

        assertThat(kanonMessageListAfter.size()).isEqualTo(kanonMessageList.size());
    }

    @Test
    public void getPathToJoinInBinaryHttp_shouldReturnCorrectString() {

        String expectedString =
                "/v2/types/fledge/sets/nEW2Xx96S2B1zRqAgXsX4mRl0MAhgKcYZBb-Lsa5djg:join";
        KAnonMessageEntity kAnonMessageEntity =
                KAnonMessageEntity.builder()
                        .setMessageId(1L)
                        .setAdSelectionId(12L)
                        .setCorrespondingClientParametersExpiryInstant(Instant.now())
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .setHashSet("nEW2Xx96S2B1zRqAgXsX4mRl0MAhgKcYZBb-Lsa5djg")
                        .build();

        String actualString = mKAnonCaller.getPathToJoinInBinaryHttp(kAnonMessageEntity);

        assertThat(actualString).isEqualTo(expectedString);
    }

    @Test
    public void signJoinMessages_withEmptyList_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class, () -> mKAnonCaller.signAndJoinMessages(List.of()));
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

    private void setupMockWithCountDownLatch(CountDownLatch countDownLatch) throws IOException {
        doAnswer(
                        (unused) -> {
                            countDownLatch.countDown();
                            return null;
                        })
                .when(mockAdServicesLogger)
                .logKAnonSignJoinStatus();
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();

        ClientParameters clientParameters = mTranscript.getClientParameters();
        String clientParamsVersion = "clientparamsverison";
        long clientParamsId = 123;
        DBClientParameters clientParametersBefore =
                DBClientParameters.builder()
                        .setClientParametersId(clientParamsId)
                        .setClientPrivateParameters(
                                clientParameters.getPrivateParameters().toByteArray())
                        .setClientPublicParameters(
                                clientParameters.getPublicParameters().toByteArray())
                        .setClientId(mUserProfileIdManager.getOrCreateId())
                        .setClientParametersExpiryInstant(Instant.now().plusSeconds(36000))
                        .setClientParamsVersion(clientParamsVersion)
                        .build();
        mClientParametersDao.insertClientParameters(clientParametersBefore);
        persistServerParametersInDB();

        mRequestMetadata =
                RequestMetadata.newBuilder()
                        .setAndroidRequestMetadata(
                                AndroidRequestMetadata.newBuilder()
                                        .setClientId(
                                                ByteString.copyFrom(
                                                        mUserProfileIdManager
                                                                .getOrCreateId()
                                                                .toString()
                                                                .getBytes()))
                                        .build())
                        .setAuthTypeValue(RequestMetadata.AuthType.AUTH_DEVICE_ATTESTATION_VALUE)
                        .build();
        GetTokensRequest getTokensRequest =
                GetTokensRequest.newBuilder()
                        .setRequestMetadata(mRequestMetadata)
                        .setClientParamsVersion(clientParamsVersion)
                        .setTokensRequest(generatedTokensRequestProto.getTokenRequest())
                        .addAllClientFingerprintsBytes(
                                generatedTokensRequestProto.getFingerprintsBytesList())
                        .build();

        AdServicesHttpClientRequest getServerParamsRequest =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(mFlags.getFledgeKAnonFetchServerParamsUrl()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .build();

        AdServicesHttpClientRequest httpGetTokensRequest =
                AdServicesHttpClientRequest.create(
                        Uri.parse(mFlags.getFledgeKAnonGetTokensUrl()),
                        REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE,
                        ImmutableSet.of(),
                        false,
                        DEV_CONTEXT_DISABLED,
                        AdServicesHttpUtil.HttpMethodType.POST,
                        getTokensRequest.toByteArray());
        AdServicesHttpClientRequest joinRequest =
                AdServicesHttpClientRequest.create(
                        Uri.parse(mFlags.getFledgeKAnonJoinUrl()),
                        REQUEST_PROPERTIES_OHTTP_CONTENT_TYPE,
                        ImmutableSet.of(),
                        false,
                        DEV_CONTEXT_DISABLED,
                        AdServicesHttpUtil.HttpMethodType.POST,
                        EMPTY_BODY);

        TokensSet tokensSet =
                TokensSet.newBuilder().addAllTokens(mTranscript.getTokensList()).build();
        when(mockAnonymousCountingTokens.generateClientParameters(any(), any()))
                .thenReturn(mTranscript.getClientParameters());
        when(mockAnonymousCountingTokens.verifyTokensResponse(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        when(mockAnonymousCountingTokens.recoverTokens(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(tokensSet);
        when(mockAnonymousCountingTokens.generateTokensRequest(any(), any(), any(), any(), any()))
                .thenReturn(generatedTokensRequestProto);
        when(mockKAnonOblivivousHttpEncryptorImpl.encryptBytes(
                        any(byte[].class), anyInt(), anyInt(), any()))
                .thenReturn(FluentFuture.from(immediateFuture(new byte[0])));
        when(mockKAnonOblivivousHttpEncryptorImpl.decryptBytes(any(), anyInt()))
                .thenReturn(new byte[0]);
        when(mockAdServicesHttpClient.performRequestGetResponseInBase64String(httpGetTokensRequest))
                .thenReturn(
                        immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(
                                                BaseEncoding.base64()
                                                        .encode(
                                                                GetTokensResponse.newBuilder()
                                                                        .build()
                                                                        .toByteArray()))
                                        .build()));
        when(mockAdServicesHttpClient.performRequestGetResponseInBase64String(
                        getServerParamsRequest))
                .thenReturn(
                        immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(
                                                BaseEncoding.base64()
                                                        .encode(
                                                                GetServerPublicParamsResponse
                                                                        .newBuilder()
                                                                        .build()
                                                                        .toByteArray()))
                                        .build()));
        when(mockAdServicesHttpClient.performRequestGetResponseInBase64String(joinRequest))
                .thenReturn(
                        immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(BaseEncoding.base64().encode(EMPTY_BODY))
                                        .build()));
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        when(mockBinaryHttpMessageDeserializer.deserialize(any())).thenReturn(binaryHttpMessage);
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

    public static class KAnonSignAndJoinRunnerTestFlags implements Flags {
        private int mBatchSize;

        KAnonSignAndJoinRunnerTestFlags(int batchSize) {
            mBatchSize = batchSize;
        }

        @Override
        public int getFledgeKAnonSignBatchSize() {
            return mBatchSize;
        }

        @Override
        public boolean getFledgeKAnonKeyAttestationEnabled() {
            return false;
        }
    }
}
