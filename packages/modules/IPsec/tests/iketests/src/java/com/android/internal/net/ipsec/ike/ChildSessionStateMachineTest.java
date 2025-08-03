/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike;

import static android.net.ipsec.test.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_KE_PAYLOAD;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_SYNTAX;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_ADDITIONAL_SAS;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_PROPOSAL_CHOSEN;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_TEMPORARY_FAILURE;
import static android.system.OsConstants.AF_INET;

import static com.android.internal.net.TestUtils.createMockRandomFactory;
import static com.android.internal.net.ipsec.test.ike.AbstractSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_CHILD;
import static com.android.internal.net.ipsec.test.ike.AbstractSessionStateMachine.RETRY_INTERVAL_MS;
import static com.android.internal.net.ipsec.test.ike.ChildSessionStateMachine.CMD_FORCE_TRANSITION;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.REKEY_DELETE_TIMEOUT_MS;
import static com.android.internal.net.ipsec.test.ike.message.IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA;
import static com.android.internal.net.ipsec.test.ike.message.IkeHeader.EXCHANGE_TYPE_INFORMATIONAL;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_DELETE_CHILD;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_REKEY_CHILD;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_REKEY_SA;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_CP;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_DELETE;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_KE;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_NONCE;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_NOTIFY;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_SA;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_TS_INITIATOR;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_TS_RESPONDER;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PROTOCOL_ID_ESP;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.InetAddresses;
import android.net.IpSecManager;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.IpSecTransform;
import android.net.LinkAddress;
import android.net.ipsec.test.ike.ChildSaProposal;
import android.net.ipsec.test.ike.ChildSessionCallback;
import android.net.ipsec.test.ike.ChildSessionConfiguration;
import android.net.ipsec.test.ike.ChildSessionParams;
import android.net.ipsec.test.ike.IkeManager;
import android.net.ipsec.test.ike.IkeTrafficSelector;
import android.net.ipsec.test.ike.SaProposal;
import android.net.ipsec.test.ike.TunnelModeChildSessionParams;
import android.net.ipsec.test.ike.exceptions.IkeException;
import android.net.ipsec.test.ike.exceptions.IkeInternalException;
import android.net.ipsec.test.ike.exceptions.InvalidKeException;
import android.net.ipsec.test.ike.exceptions.InvalidSyntaxException;
import android.net.ipsec.test.ike.exceptions.NoAdditionalSasException;
import android.net.ipsec.test.ike.exceptions.NoValidProposalChosenException;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;

import androidx.test.InstrumentationRegistry;

import com.android.internal.net.TestUtils;
import com.android.internal.net.ipsec.test.ike.ChildSessionStateMachine.CreateChildSaHelper;
import com.android.internal.net.ipsec.test.ike.ChildSessionStateMachine.IChildSessionSmCallback;
import com.android.internal.net.ipsec.test.ike.ChildSessionStateMachine.IdleWithDeferredRequest;
import com.android.internal.net.ipsec.test.ike.SaRecord.ChildSaRecord;
import com.android.internal.net.ipsec.test.ike.SaRecord.ChildSaRecordConfig;
import com.android.internal.net.ipsec.test.ike.SaRecord.ISaRecordHelper;
import com.android.internal.net.ipsec.test.ike.SaRecord.SaLifetimeAlarmScheduler;
import com.android.internal.net.ipsec.test.ike.SaRecord.SaRecordHelper;
import com.android.internal.net.ipsec.test.ike.crypto.IkeCipher;
import com.android.internal.net.ipsec.test.ike.crypto.IkeMacIntegrity;
import com.android.internal.net.ipsec.test.ike.crypto.IkeMacPrf;
import com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.ConfigAttribute;
import com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.ConfigAttributeIpv4Address;
import com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.ConfigAttributeIpv4Netmask;
import com.android.internal.net.ipsec.test.ike.message.IkeDeletePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeKePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeNoncePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload;
import com.android.internal.net.ipsec.test.ike.message.IkePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.DhGroupTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.EncryptionTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.IntegrityTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.PrfTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeTestUtils;
import com.android.internal.net.ipsec.test.ike.message.IkeTsPayload;
import com.android.internal.net.ipsec.test.ike.testutils.MockIpSecTestUtils;
import com.android.internal.net.ipsec.test.ike.utils.IState;
import com.android.internal.net.ipsec.test.ike.utils.IkeMetrics;
import com.android.internal.net.ipsec.test.ike.utils.IpSecSpiGenerator;
import com.android.internal.net.ipsec.test.ike.utils.RandomnessFactory;
import com.android.internal.net.utils.test.Log;
import com.android.server.IpSecService;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public final class ChildSessionStateMachineTest extends IkeSessionTestBase {
    private static final String TAG = "ChildSessionStateMachineTest";

    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final Inet4Address LOCAL_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.200");
    private static final Inet4Address UPDATED_LOCAL_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.201");
    private static final Inet4Address REMOTE_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.0.2.100");
    private static final Inet4Address INTERNAL_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("203.0.113.100");
    private static final Inet6Address LOCAL_ADDRESS_6 =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::1");
    private static final Inet6Address REMOTE_ADDRESS_6 =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8::2");

    private static final int IPV4_PREFIX_LEN = 32;

    private static final String IKE_AUTH_RESP_SA_PAYLOAD =
            "2c00002c0000002801030403cae7019f0300000c0100000c800e0080"
                    + "03000008030000020000000805000000";
    private static final String REKEY_CHILD_RESP_SA_PAYLOAD =
            "2800002c0000002801030403cd1736b30300000c0100000c800e0080"
                    + "03000008030000020000000805000000";
    private static final String REKEY_CHILD_REQ_SA_PAYLOAD =
            "2800002c0000002801030403c88336490300000c0100000c800e0080"
                    + "03000008030000020000000805000000";
    private static final String REKEY_CHILD_UNACCEPTABLE_REQ_SA_PAYLOAD =
            "2800002c0000002801030403c88336490300000c0100000c800e00c0"
                    + "03000008030000020000000805000000";

    private static final int CURRENT_CHILD_SA_SPI_IN = 0x2ad4c0a2;
    private static final int CURRENT_CHILD_SA_SPI_OUT = 0xcae7019f;

    private static final int LOCAL_INIT_NEW_CHILD_SA_SPI_IN = 0x57a09b0f;
    private static final int LOCAL_INIT_NEW_CHILD_SA_SPI_OUT = 0xcd1736b3;

    private static final int REMOTE_INIT_NEW_CHILD_SA_SPI_IN = 0xd2d01795;
    private static final int REMOTE_INIT_NEW_CHILD_SA_SPI_OUT = 0xc8833649;

    private static final String IKE_SK_D_HEX_STRING = "C86B56EFCF684DCC2877578AEF3137167FE0EBF6";
    private static final byte[] SK_D = TestUtils.hexStringToByteArray(IKE_SK_D_HEX_STRING);

    private static final int KEY_LEN_IKE_SKD = 20;

    private static final int IKE_SESSION_UNIQUE_ID = 1;
    private static final int IKE_DH_GROUP = SaProposal.DH_GROUP_4096_BIT_MODP;

    private IkeMacPrf mIkePrf;

    private Context mContext;
    private Handler mMockIkeHandler;
    private PackageManager mMockPackageManager;
    private IpSecService mMockIpSecService;
    private IpSecManager mMockIpSecManager;
    private UdpEncapsulationSocket mMockUdpEncapSocket;

    private TestLooper mLooper;
    private RandomnessFactory mMockRandomFactory;
    private IpSecSpiGenerator mIpSecSpiGenerator;
    private ChildSessionStateMachine mChildSessionStateMachine;

    private List<IkePayload> mFirstSaReqPayloads = new ArrayList<>();
    private List<IkePayload> mFirstSaRespPayloads = new ArrayList<>();

    private ChildSaRecord mSpyCurrentChildSaRecord;
    private ChildSaRecord mSpyLocalInitNewChildSaRecord;
    private ChildSaRecord mSpyRemoteInitNewChildSaRecord;

    private Log mSpyIkeLog;

    private ISaRecordHelper mMockSaRecordHelper;

    private ChildSessionParams mChildSessionParams;
    private EncryptionTransform mChildEncryptionTransform;
    private IntegrityTransform mChildIntegrityTransform;
    private DhGroupTransform mChildDhGroupTransform;

    private ChildSaProposal mMockNegotiatedProposal;

    private Executor mSpyUserCbExecutor;
    private ChildSessionCallback mMockChildSessionCallback;
    private IChildSessionSmCallback mMockChildSessionSmCallback;

    private ArgumentCaptor<ChildSaRecordConfig> mChildSaRecordConfigCaptor =
            ArgumentCaptor.forClass(ChildSaRecordConfig.class);
    private ArgumentCaptor<List<IkePayload>> mPayloadListCaptor =
            ArgumentCaptor.forClass(List.class);
    private ArgumentCaptor<ChildSessionConfiguration> mChildConfigCaptor =
            ArgumentCaptor.forClass(ChildSessionConfiguration.class);

    public ChildSessionStateMachineTest() {
        mMockSaRecordHelper = mock(SaRecord.ISaRecordHelper.class);
        mMockChildSessionSmCallback = mock(IChildSessionSmCallback.class);

        mChildEncryptionTransform =
                new EncryptionTransform(
                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128);
        mChildIntegrityTransform =
                new IntegrityTransform(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96);

        mChildDhGroupTransform = new DhGroupTransform(SaProposal.DH_GROUP_1024_BIT_MODP);
    }

    @Before
    public void setup() throws Exception {
        mSpyIkeLog = TestUtils.makeSpyLogThrowExceptionForWtf(TAG);
        IkeManager.setIkeLog(mSpyIkeLog);

        mIkePrf = IkeMacPrf.create(new PrfTransform(SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1));

        mContext = spy(InstrumentationRegistry.getContext());
        mMockIkeHandler = mock(Handler.class);
        when(mMockIkeHandler.obtainMessage(anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(mock(Message.class));

        mMockPackageManager = mock(PackageManager.class);
        doReturn(mMockPackageManager).when(mContext).getPackageManager();

        mMockIpSecService = mock(IpSecService.class);
        mMockIpSecManager = new IpSecManager(mContext, mMockIpSecService);
        mMockUdpEncapSocket = mock(UdpEncapsulationSocket.class);

        mIpSecSpiGenerator = new IpSecSpiGenerator(mMockIpSecManager, createMockRandomFactory());

        mMockNegotiatedProposal = mock(ChildSaProposal.class);

        mSpyUserCbExecutor =
                spy(
                        (command) -> {
                            command.run();
                        });

        mMockChildSessionCallback = mock(ChildSessionCallback.class);
        mChildSessionParams = buildChildSessionParams();

        // Setup thread and looper
        mLooper = new TestLooper();
        mChildSessionStateMachine = buildChildSession(mChildSessionParams);
        mChildSessionStateMachine.setDbg(true);
        SaRecord.setSaRecordHelper(mMockSaRecordHelper);

        setUpFirstSaNegoPayloadLists();
        setUpChildSaRecords();

        mChildSessionStateMachine.start();
    }

    @After
    public void tearDown() {
        mChildSessionStateMachine.setDbg(false);
        IkeManager.resetIkeLog();
        SaRecord.setSaRecordHelper(new SaRecordHelper());
    }

    private ChildSaProposal buildSaProposal() throws Exception {
        return new ChildSaProposal.Builder()
                .addEncryptionAlgorithm(
                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128)
                .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
                .build();
    }

    private ChildSessionParams buildChildSessionParams() throws Exception {
        return new TunnelModeChildSessionParams.Builder()
                .addSaProposal(buildSaProposal())
                .addInternalAddressRequest(AF_INET)
                .addInternalAddressRequest(INTERNAL_ADDRESS)
                .build();
    }

    private void setUpChildSaRecords() {
        mSpyCurrentChildSaRecord =
                makeSpyChildSaRecord(CURRENT_CHILD_SA_SPI_IN, CURRENT_CHILD_SA_SPI_OUT);
        mSpyLocalInitNewChildSaRecord =
                makeSpyChildSaRecord(
                        LOCAL_INIT_NEW_CHILD_SA_SPI_IN, LOCAL_INIT_NEW_CHILD_SA_SPI_OUT);
        mSpyRemoteInitNewChildSaRecord =
                makeSpyChildSaRecord(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_IN, REMOTE_INIT_NEW_CHILD_SA_SPI_OUT);
    }

    private void setUpSpiResource(InetAddress address, int spiRequested) throws Exception {
        when(mMockIpSecService.allocateSecurityParameterIndex(
                        eq(address.getHostAddress()), anyInt(), anyObject()))
                .thenReturn(MockIpSecTestUtils.buildDummyIpSecSpiResponse(spiRequested));
    }

    private void setUpFirstSaNegoPayloadLists() throws Exception {
        // Build locally generated SA payload that has its SPI resource allocated.
        setUpSpiResource(LOCAL_ADDRESS, CURRENT_CHILD_SA_SPI_IN);
        IkeSaPayload reqSaPayload =
                IkeSaPayload.createChildSaRequestPayload(
                        mChildSessionParams.getSaProposalsInternal(),
                        mIpSecSpiGenerator,
                        LOCAL_ADDRESS);
        mFirstSaReqPayloads.add(reqSaPayload);

        // Build a remotely generated SA payload whoes SPI resource has not been allocated.
        setUpSpiResource(REMOTE_ADDRESS, CURRENT_CHILD_SA_SPI_OUT);
        IkeSaPayload respSaPayload =
                (IkeSaPayload)
                        IkeTestUtils.hexStringToIkePayload(
                                IkePayload.PAYLOAD_TYPE_SA, true, IKE_AUTH_RESP_SA_PAYLOAD);
        mFirstSaRespPayloads.add(respSaPayload);

        // Build TS Payloads
        IkeTsPayload tsInitPayload =
                new IkeTsPayload(
                        true /*isInitiator*/,
                        mChildSessionParams.getInboundTrafficSelectorsInternal());
        IkeTsPayload tsRespPayload =
                new IkeTsPayload(
                        false /*isInitiator*/,
                        mChildSessionParams.getOutboundTrafficSelectorsInternal());

        mFirstSaReqPayloads.add(tsInitPayload);
        mFirstSaReqPayloads.add(tsRespPayload);
        mFirstSaRespPayloads.add(tsInitPayload);
        mFirstSaRespPayloads.add(tsRespPayload);

        // Build Nonce Payloads
        mFirstSaReqPayloads.add(new IkeNoncePayload(createMockRandomFactory()));
        mFirstSaRespPayloads.add(new IkeNoncePayload(createMockRandomFactory()));

        // Build Config Request Payload
        List<ConfigAttribute> attrReqList = new ArrayList<>();
        attrReqList.add(new ConfigAttributeIpv4Address(INTERNAL_ADDRESS));
        attrReqList.add(new ConfigAttributeIpv4Netmask());
        mFirstSaReqPayloads.add(new IkeConfigPayload(false /*isReply*/, attrReqList));

        // Build Config Reply Payload
        List<ConfigAttribute> attrRespList = new ArrayList<>();
        attrRespList.add(new ConfigAttributeIpv4Address(INTERNAL_ADDRESS));
        mFirstSaRespPayloads.add(new IkeConfigPayload(true /*isReply*/, attrRespList));
    }

    private ChildSaRecord makeSpyChildSaRecord(int inboundSpi, int outboundSpi) {
        ChildSaRecord child =
                spy(
                        new ChildSaRecord(
                                inboundSpi,
                                outboundSpi,
                                true /*localInit*/,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                mock(IpSecTransform.class),
                                mock(IpSecTransform.class),
                                mock(SaLifetimeAlarmScheduler.class)));
        doNothing().when(child).close();
        return child;
    }

    private void quitAndVerify() {
        mChildSessionStateMachine.mCurrentChildSaRecord = null;
        mChildSessionStateMachine.mLocalInitNewChildSaRecord = null;
        mChildSessionStateMachine.mRemoteInitNewChildSaRecord = null;

        reset(mMockChildSessionSmCallback);
        mChildSessionStateMachine.quit();
        mLooper.dispatchAll();

        verify(mMockChildSessionSmCallback).onProcedureFinished(mChildSessionStateMachine);
        verify(mMockChildSessionSmCallback).onChildSessionClosed(mMockChildSessionCallback);
    }

    private void verifyChildSaRecordConfig(
            ChildSaRecordConfig childSaRecordConfig,
            int initSpi,
            int respSpi,
            boolean isLocalInit) {
        verifyChildSaRecordConfig(
                childSaRecordConfig,
                initSpi,
                respSpi,
                isLocalInit,
                LOCAL_ADDRESS,
                REMOTE_ADDRESS,
                mMockUdpEncapSocket);
    }

    private void verifyChildSaRecordConfig(
            ChildSaRecordConfig childSaRecordConfig,
            int initSpi,
            int respSpi,
            boolean isLocalInit,
            InetAddress localAddress,
            InetAddress remoteAddress,
            UdpEncapsulationSocket newEncapSocket) {
        assertEquals(mContext, childSaRecordConfig.context);
        assertEquals(initSpi, childSaRecordConfig.initSpi.getSpi());
        assertEquals(respSpi, childSaRecordConfig.respSpi.getSpi());

        if (isLocalInit) {
            assertEquals(localAddress, childSaRecordConfig.initAddress);
            assertEquals(remoteAddress, childSaRecordConfig.respAddress);
        } else {
            assertEquals(remoteAddress, childSaRecordConfig.initAddress);
            assertEquals(localAddress, childSaRecordConfig.respAddress);
        }

        assertEquals(newEncapSocket, childSaRecordConfig.udpEncapSocket);
        assertEquals(mIkePrf, childSaRecordConfig.ikePrf);
        assertArrayEquals(SK_D, childSaRecordConfig.skD);
        assertFalse(childSaRecordConfig.isTransport);
        assertEquals(isLocalInit, childSaRecordConfig.isLocalInit);
        assertTrue(childSaRecordConfig.hasIntegrityAlgo);
        assertNotNull(childSaRecordConfig.saLifetimeAlarmScheduler);
    }

    private void verifyNotifyUsersCreateIpSecSa(
            ChildSaRecord childSaRecord, boolean expectInbound) {
        IpSecTransform transform =
                expectInbound
                        ? childSaRecord.getInboundIpSecTransform()
                        : childSaRecord.getOutboundIpSecTransform();
        int direction = expectInbound ? IpSecManager.DIRECTION_IN : IpSecManager.DIRECTION_OUT;

        verify(mMockChildSessionCallback).onIpSecTransformCreated(eq(transform), eq(direction));
    }

    private void verifyInitCreateChildResp(
            List<IkePayload> reqPayloads, List<IkePayload> respPayloads) throws Exception {
        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(
                        mSpyCurrentChildSaRecord.getRemoteSpi(), mChildSessionStateMachine);
        verify(mMockChildSessionSmCallback).onProcedureFinished(mChildSessionStateMachine);
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        // Validate negotiated SA proposal.
        ChildSaProposal negotiatedProposal = mChildSessionStateMachine.mSaProposal;
        assertNotNull(negotiatedProposal);
        assertEquals(
                new EncryptionTransform[] {mChildEncryptionTransform},
                negotiatedProposal.getEncryptionTransforms());
        assertEquals(
                new IntegrityTransform[] {mChildIntegrityTransform},
                negotiatedProposal.getIntegrityTransforms());

        // Validate current ChildSaRecord
        verify(mMockSaRecordHelper)
                .makeChildSaRecord(
                        eq(reqPayloads), eq(respPayloads), mChildSaRecordConfigCaptor.capture());
        ChildSaRecordConfig childSaRecordConfig = mChildSaRecordConfigCaptor.getValue();

        verifyChildSaRecordConfig(
                childSaRecordConfig,
                CURRENT_CHILD_SA_SPI_IN,
                CURRENT_CHILD_SA_SPI_OUT,
                true /*isLocalInit*/);

        assertEquals(mSpyCurrentChildSaRecord, mChildSessionStateMachine.mCurrentChildSaRecord);

        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(anyInt(), eq(mChildSessionStateMachine));
        verify(mMockChildSessionSmCallback).onProcedureFinished(mChildSessionStateMachine);
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        // Verify users have been notified
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verifyNotifyUsersCreateIpSecSa(mSpyCurrentChildSaRecord, true /*expectInbound*/);
        verifyNotifyUsersCreateIpSecSa(mSpyCurrentChildSaRecord, false /*expectInbound*/);
        verify(mMockChildSessionCallback).onOpened(mChildConfigCaptor.capture());

        // Verify Child Session Configuration
        ChildSessionConfiguration sessionConfig = mChildConfigCaptor.getValue();
        verifyTsList(
                Arrays.asList(mChildSessionParams.getInboundTrafficSelectorsInternal()),
                sessionConfig.getInboundTrafficSelectors());
        verifyTsList(
                Arrays.asList(mChildSessionParams.getOutboundTrafficSelectorsInternal()),
                sessionConfig.getOutboundTrafficSelectors());

        List<LinkAddress> addrList = sessionConfig.getInternalAddresses();
        assertEquals(1, addrList.size());
        assertEquals(INTERNAL_ADDRESS, addrList.get(0).getAddress());
        assertEquals(IPV4_PREFIX_LEN, addrList.get(0).getPrefixLength());
    }

    private void verifyTsList(
            List<IkeTrafficSelector> expectedList, List<IkeTrafficSelector> tsList) {
        assertEquals(expectedList.size(), tsList.size());
        for (int i = 0; i < expectedList.size(); i++) {
            assertEquals(expectedList.get(i), tsList.get(i));
        }
    }

    @Ignore
    public void disableTestCreateFirstChild() throws Exception {
        doReturn(mSpyCurrentChildSaRecord)
                .when(mMockSaRecordHelper)
                .makeChildSaRecord(any(), any(), any());

        mChildSessionStateMachine.handleFirstChildExchange(
                mFirstSaReqPayloads,
                mFirstSaRespPayloads,
                LOCAL_ADDRESS,
                REMOTE_ADDRESS,
                mMockUdpEncapSocket,
                mIkePrf,
                IKE_DH_GROUP,
                SK_D);
        mLooper.dispatchAll();

        verifyInitCreateChildResp(mFirstSaReqPayloads, mFirstSaRespPayloads);

        quitAndVerify();
    }

    private void validateCreateChild(boolean isFirstChild) {
        assertEquals(mChildSessionStateMachine.mLocalAddress, LOCAL_ADDRESS);
        assertEquals(mChildSessionStateMachine.mRemoteAddress, REMOTE_ADDRESS);
        assertEquals(mChildSessionStateMachine.mUdpEncapSocket, mMockUdpEncapSocket);
        assertEquals(mChildSessionStateMachine.mIkePrf, mIkePrf);
        assertEquals(mChildSessionStateMachine.mIkeDhGroup, IKE_DH_GROUP);
        assertEquals(mChildSessionStateMachine.mSkD, SK_D);
        assertEquals(mChildSessionStateMachine.mIsFirstChild, isFirstChild);
    }

    @Test
    public void testHandleFirstChildExchange() throws Exception {
        mChildSessionStateMachine.handleFirstChildExchange(
                mFirstSaReqPayloads,
                mFirstSaRespPayloads,
                LOCAL_ADDRESS,
                REMOTE_ADDRESS,
                mMockUdpEncapSocket,
                mIkePrf,
                IKE_DH_GROUP,
                SK_D);
        validateCreateChild(true /* isFirstChild */);
    }

    private void verifyOutboundCreatePayloadTypes(
            List<IkePayload> outboundPayloads, boolean isRekey) {
        assertNotNull(
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_SA, IkeSaPayload.class, outboundPayloads));
        assertNotNull(
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_TS_INITIATOR, IkeTsPayload.class, outboundPayloads));
        assertNotNull(
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_TS_RESPONDER, IkeTsPayload.class, outboundPayloads));
        assertNotNull(
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_NONCE, IkeNoncePayload.class, outboundPayloads));
        assertNull(
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_KE, IkeKePayload.class, outboundPayloads));

        IkeConfigPayload configPayload =
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_CP, IkeConfigPayload.class, outboundPayloads);
        if (isRekey) {
            assertNull(configPayload);
        } else {
            assertNotNull(configPayload);
            assertEquals(IkeConfigPayload.CONFIG_TYPE_REQUEST, configPayload.configType);
        }
    }

    private List<IkePayload> checkCreateChildAndGetRequest() throws Exception {
        doReturn(mSpyCurrentChildSaRecord)
                .when(mMockSaRecordHelper)
                .makeChildSaRecord(any(), any(), any());

        mChildSessionStateMachine.createChildSession(
                LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket, mIkePrf, IKE_DH_GROUP, SK_D);
        mLooper.dispatchAll();

        // Validate outbound payload list
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_CREATE_CHILD_SA),
                        eq(false),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));

        List<IkePayload> reqPayloadList = mPayloadListCaptor.getValue();
        verifyOutboundCreatePayloadTypes(reqPayloadList, false /*isRekey*/);
        assertTrue(
                IkePayload.getPayloadListForTypeInProvidedList(
                                PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class, reqPayloadList)
                        .isEmpty());

        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_CREATE_CHILD_SA, mFirstSaRespPayloads);
        mLooper.dispatchAll();

        return reqPayloadList;
    }

    @Test
    public void testCreateChild() throws Exception {
        List<IkePayload> reqPayloadList = checkCreateChildAndGetRequest();
        validateCreateChild(false /* isFirstChild */);

        verifyInitCreateChildResp(reqPayloadList, mFirstSaRespPayloads);
        quitAndVerify();
    }

    @Test
    public void testCreateChildExecuteCbAfterKillSession() throws Exception {
        mChildSessionStateMachine.quitNow();
        mLooper.dispatchAll();

        LateExecuteExecutor lateExecutor = spy(new LateExecuteExecutor());
        mChildSessionStateMachine = buildAndStartChildSession(lateExecutor);

        List<IkePayload> reqPayloadList = checkCreateChildAndGetRequest();

        mChildSessionStateMachine.killSession();
        mLooper.dispatchAll();

        lateExecutor.actuallyExecute();

        // Verify users have been notified
        verifyNotifyUsersCreateIpSecSa(mSpyCurrentChildSaRecord, true /*expectInbound*/);
        verifyNotifyUsersCreateIpSecSa(mSpyCurrentChildSaRecord, false /*expectInbound*/);
        verify(mMockChildSessionCallback).onOpened(any(ChildSessionConfiguration.class));
    }

    private void verifyChildMetricsLogged(int stateCode, int exceptionCode) {
        verifyMetricsLogged(IkeMetrics.IKE_SESSION_TYPE_CHILD, stateCode, exceptionCode);
    }

    private <T extends IkeException> void verifyHandleFatalErrorAndQuit(
            IState state, Class<T> exceptionClass, int metricsExceptionType) {
        assertNull(mChildSessionStateMachine.getCurrentState());
        verify(mMockChildSessionSmCallback).onProcedureFinished(mChildSessionStateMachine);
        verify(mMockChildSessionSmCallback).onChildSessionClosed(mMockChildSessionCallback);

        verify(mMockChildSessionCallback).onClosedWithException(any(exceptionClass));
        verifyChildMetricsLogged(getStateCode(state), metricsExceptionType);
    }

    private void createChildSessionAndReceiveErrorNotification(int notifyType) throws Exception {
        // Send out Create request
        mChildSessionStateMachine.createChildSession(
                LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket, mIkePrf, IKE_DH_GROUP, SK_D);
        mLooper.dispatchAll();

        // Receive error notification in Create response
        IkeNotifyPayload notifyPayload = new IkeNotifyPayload(notifyType);
        List<IkePayload> respPayloads = new ArrayList<>();
        respPayloads.add(notifyPayload);
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, respPayloads);
        mLooper.dispatchAll();
    }

    @Test
    public void testCreateChildHandlesErrorNotifyResp() throws Exception {
        createChildSessionAndReceiveErrorNotification(ERROR_TYPE_NO_PROPOSAL_CHOSEN);

        // Verify no SPI for provisional Child was registered.
        verify(mMockChildSessionSmCallback, never())
                .onChildSaCreated(anyInt(), eq(mChildSessionStateMachine));

        // Verify user was notified and state machine has quit.
        verifyHandleFatalErrorAndQuit(
                mChildSessionStateMachine.mCreateChildLocalCreate,
                NoValidProposalChosenException.class,
                IkeMetrics.IKE_ERROR_PROTOCOL_NO_PROPOSAL_CHOSEN);
    }

    @Test
    public void testCreateChildHandlesTemporaryFailure() throws Exception {
        createChildSessionAndReceiveErrorNotification(ERROR_TYPE_TEMPORARY_FAILURE);

        // Verify no SPI for provisional Child was registered.
        verify(mMockChildSessionSmCallback, never())
                .onChildSaCreated(anyInt(), eq(mChildSessionStateMachine));

        // Verify that Create Child re-enqueued
        verify(mMockChildSessionSmCallback)
                .scheduleRetryLocalRequest(
                        argThat(
                                childLocalRequest ->
                                        childLocalRequest.procedureType
                                                == CMD_LOCAL_REQUEST_CREATE_CHILD));

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Initial);
    }

    @Test
    public void testCreateChildHandlesRespWithMissingPayload() throws Exception {
        // Send out Create request
        mChildSessionStateMachine.createChildSession(
                LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket, mIkePrf, IKE_DH_GROUP, SK_D);
        mLooper.dispatchAll();

        // Receive response with no Nonce Payload
        List<IkePayload> respPayloads = new ArrayList<>();
        for (IkePayload payload : mFirstSaRespPayloads) {
            if (IkePayload.PAYLOAD_TYPE_NONCE == payload.payloadType) continue;
            respPayloads.add(payload);
        }
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, respPayloads);
        mLooper.dispatchAll();

        // Verify SPI for provisional Child was registered and unregistered.
        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(CURRENT_CHILD_SA_SPI_OUT, mChildSessionStateMachine);
        verify(mMockChildSessionSmCallback).onChildSaDeleted(CURRENT_CHILD_SA_SPI_OUT);

        // Verify user was notified and state machine has quit.
        verifyHandleFatalErrorAndQuit(
                mChildSessionStateMachine.mCreateChildLocalCreate,
                InvalidSyntaxException.class,
                IkeMetrics.IKE_ERROR_PROTOCOL_INVALID_SYNTAX);
    }

    @Test
    public void testCreateChildHandlesKeyCalculationFail() throws Exception {
        // Throw exception when building ChildSaRecord
        when(mMockSaRecordHelper.makeChildSaRecord(any(), any(), any()))
                .thenThrow(
                        new GeneralSecurityException("testCreateChildHandlesKeyCalculationFail"));

        // Send out and receive Create Child message
        mChildSessionStateMachine.createChildSession(
                LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket, mIkePrf, IKE_DH_GROUP, SK_D);
        mLooper.dispatchAll();
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_CREATE_CHILD_SA, mFirstSaRespPayloads);
        mLooper.dispatchAll();

        // Verify SPI for provisional Child was registered and unregistered.
        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(CURRENT_CHILD_SA_SPI_OUT, mChildSessionStateMachine);
        verify(mMockChildSessionSmCallback).onChildSaDeleted(CURRENT_CHILD_SA_SPI_OUT);

        // Verify user was notified and state machine has quit.
        verifyHandleFatalErrorAndQuit(
                mChildSessionStateMachine.mCreateChildLocalCreate,
                IkeInternalException.class,
                IkeMetrics.IKE_ERROR_INTERNAL);
    }

    private void setupIdleStateMachine() throws Exception {
        mChildSessionStateMachine.mLocalAddress = LOCAL_ADDRESS;
        mChildSessionStateMachine.mRemoteAddress = REMOTE_ADDRESS;
        mChildSessionStateMachine.mUdpEncapSocket = mMockUdpEncapSocket;
        mChildSessionStateMachine.mIkePrf = mIkePrf;
        mChildSessionStateMachine.mIkeDhGroup = IKE_DH_GROUP;
        mChildSessionStateMachine.mSkD = SK_D;

        mChildSessionStateMachine.mSaProposal = buildSaProposal();
        mChildSessionStateMachine.mChildCipher = mock(IkeCipher.class);
        mChildSessionStateMachine.mChildIntegrity = mock(IkeMacIntegrity.class);
        mChildSessionStateMachine.mLocalTs =
                mChildSessionParams.getInboundTrafficSelectorsInternal();
        mChildSessionStateMachine.mRemoteTs =
                mChildSessionParams.getOutboundTrafficSelectorsInternal();

        mChildSessionStateMachine.mCurrentChildSaRecord = mSpyCurrentChildSaRecord;

        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mIdle);
        mLooper.dispatchAll();

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);
    }

    private List<IkePayload> makeDeletePayloads(int spi) {
        List<IkePayload> inboundPayloads = new ArrayList<>(1);
        inboundPayloads.add(new IkeDeletePayload(new int[] {spi}));
        return inboundPayloads;
    }

    private void verifyOutboundDeletePayload(int expectedSpi, boolean isResp) {
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        eq(isResp),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));

        List<IkePayload> outPayloadList = mPayloadListCaptor.getValue();
        assertEquals(1, outPayloadList.size());

        List<IkeDeletePayload> deletePayloads =
                IkePayload.getPayloadListForTypeInProvidedList(
                        PAYLOAD_TYPE_DELETE, IkeDeletePayload.class, outPayloadList);
        assertEquals(1, deletePayloads.size());
        IkeDeletePayload deletePayload = deletePayloads.get(0);
        assertEquals(expectedSpi, deletePayload.spisToDelete[0]);
    }

    private void verifyNotifyUserDeleteChildSa(ChildSaRecord childSaRecord) {
        verify(mMockChildSessionCallback)
                .onIpSecTransformDeleted(
                        eq(childSaRecord.getInboundIpSecTransform()),
                        eq(IpSecManager.DIRECTION_IN));
        verify(mMockChildSessionCallback)
                .onIpSecTransformDeleted(
                        eq(childSaRecord.getOutboundIpSecTransform()),
                        eq(IpSecManager.DIRECTION_OUT));
    }

    private void verifyNotifyUsersDeleteSession(IState state) {
        verifyNotifyUsersDeleteSession(mSpyUserCbExecutor, state);
    }

    private void verifyNotifyUsersDeleteSession(Executor spyExecutor, IState state) {
        verifyNotifyUsersDeleteSession(spyExecutor, state, null, IkeMetrics.IKE_ERROR_NONE);
    }

    private void verifyNotifyUsersDeleteSession(
            Executor spyExecutor,
            IState state,
            Class<? extends IkeException> exceptionClass,
            int metricsExceptionType) {
        verify(spyExecutor, atLeastOnce()).execute(any(Runnable.class));
        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);

        InOrder orderVerifier = inOrder(mMockChildSessionCallback);
        orderVerifier
                .verify(mMockChildSessionCallback, times(2))
                .onIpSecTransformDeleted(any(), anyInt());

        if (exceptionClass == null) {
            orderVerifier.verify(mMockChildSessionCallback).onClosed();
        } else {
            orderVerifier
                    .verify(mMockChildSessionCallback)
                    .onClosedWithException(any(exceptionClass));
        }
        verifyChildMetricsLogged(getStateCode(state), metricsExceptionType);
    }

    @Test
    public void testDeleteChildLocal() throws Exception {
        setupIdleStateMachine();

        // Test initiating Delete request
        mChildSessionStateMachine.deleteChildSession();
        mLooper.dispatchAll();

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.DeleteChildLocalDelete);
        verifyOutboundDeletePayload(mSpyCurrentChildSaRecord.getLocalSpi(), false /*isResp*/);

        // Test receiving Delete response
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());

        verifyNotifyUsersDeleteSession(mChildSessionStateMachine.mDeleteChildLocalDelete);
    }

    @Test
    public void testDeleteChildLocalExecuteCbAfterKillSession() throws Exception {
        mChildSessionStateMachine.quitNow();
        mLooper.dispatchAll();

        LateExecuteExecutor lateExecutor = spy(new LateExecuteExecutor());
        mChildSessionStateMachine = buildAndStartChildSession(lateExecutor);

        setupIdleStateMachine();

        mChildSessionStateMachine.deleteChildSession();
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());

        lateExecutor.actuallyExecute();
        verifyNotifyUsersDeleteSession(
                lateExecutor, mChildSessionStateMachine.mDeleteChildLocalDelete);
    }

    @Test
    public void testDeleteChildLocalHandlesInvalidResp() throws Exception {
        setupIdleStateMachine();

        // Test initiating Delete request
        mChildSessionStateMachine.deleteChildSession();
        mLooper.dispatchAll();

        // Test receiving response with no Delete Payload
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_INFORMATIONAL, new ArrayList<>());
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());
        verify(mMockChildSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
    }

    @Test
    public void testDeleteChildLocalInInitial() throws Exception {
        mChildSessionStateMachine.deleteChildSession();
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verify(mMockChildSessionCallback).onClosed();
    }

    @Test
    public void testSimultaneousDeleteChild() throws Exception {
        setupIdleStateMachine();

        mChildSessionStateMachine.deleteChildSession();
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        eq(true),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));
        List<IkePayload> respPayloadList = mPayloadListCaptor.getValue();
        assertTrue(respPayloadList.isEmpty());

        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_INFORMATIONAL, new ArrayList<>());
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());

        verifyNotifyUsersDeleteSession(mChildSessionStateMachine.mDeleteChildLocalDelete);
    }

    @Test
    public void testReplyRekeyRequestDuringDeletion() throws Exception {
        setupIdleStateMachine();

        mChildSessionStateMachine.deleteChildSession();
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_REKEY_CHILD, EXCHANGE_TYPE_CREATE_CHILD_SA, mock(List.class));
        mLooper.dispatchAll();

        // Verify outbound response to Rekey Child request
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        eq(true),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));
        List<IkePayload> respPayloadList = mPayloadListCaptor.getValue();
        assertEquals(1, respPayloadList.size());

        IkeNotifyPayload notifyPayload = (IkeNotifyPayload) respPayloadList.get(0);
        assertEquals(ERROR_TYPE_TEMPORARY_FAILURE, notifyPayload.notifyType);
        assertEquals(0, notifyPayload.notifyData.length);

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.DeleteChildLocalDelete);
    }

    @Test
    public void testDeleteChildRemote() throws Exception {
        setupIdleStateMachine();

        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());
        // Verify response
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        eq(true),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));
        List<IkePayload> respPayloadList = mPayloadListCaptor.getValue();

        assertEquals(1, respPayloadList.size());
        assertArrayEquals(
                new int[] {mSpyCurrentChildSaRecord.getLocalSpi()},
                ((IkeDeletePayload) respPayloadList.get(0)).spisToDelete);

        verifyNotifyUsersDeleteSession(mChildSessionStateMachine.mDeleteChildRemoteDelete);
    }

    private void verifyOutboundRekeySaPayload(List<IkePayload> outboundPayloads, boolean isResp) {
        IkeSaPayload saPayload =
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_SA, IkeSaPayload.class, outboundPayloads);
        assertEquals(isResp, saPayload.isSaResponse);
        assertEquals(1, saPayload.proposalList.size());

        IkeSaPayload.ChildProposal proposal =
                (IkeSaPayload.ChildProposal) saPayload.proposalList.get(0);
        assertEquals(1, proposal.number); // Must be 1-indexed
        assertEquals(mChildSessionStateMachine.mSaProposal, proposal.saProposal);
    }

    private void verifyOutboundRekeyNotifyPayload(List<IkePayload> outboundPayloads) {
        List<IkeNotifyPayload> notifyPayloads =
                IkePayload.getPayloadListForTypeInProvidedList(
                        PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class, outboundPayloads);
        assertEquals(1, notifyPayloads.size());
        IkeNotifyPayload notifyPayload = notifyPayloads.get(0);
        assertEquals(NOTIFY_TYPE_REKEY_SA, notifyPayload.notifyType);
        assertEquals(PROTOCOL_ID_ESP, notifyPayload.protocolId);
        assertEquals(mSpyCurrentChildSaRecord.getLocalSpi(), notifyPayload.spi);
    }

    @Test
    public void testRekeyChildLocalCreateSendsRequest() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalCreate);
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_CREATE_CHILD_SA),
                        eq(false),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));

        // Verify outbound payload list
        List<IkePayload> reqPayloadList = mPayloadListCaptor.getValue();
        verifyOutboundCreatePayloadTypes(reqPayloadList, true /*isRekey*/);

        verifyOutboundRekeySaPayload(reqPayloadList, false /*isResp*/);
        verifyOutboundRekeyNotifyPayload(reqPayloadList);
    }

    private List<IkePayload> makeInboundRekeyChildPayloads(
            int remoteSpi, String inboundSaHexString, boolean isLocalInitRekey) throws Exception {
        IkeSaPayload saPayload =
                (IkeSaPayload)
                        IkeTestUtils.hexStringToIkePayload(
                                IkePayload.PAYLOAD_TYPE_SA, true, inboundSaHexString);

        return makeInboundRekeyChildPayloads(remoteSpi, saPayload, isLocalInitRekey);
    }

    private List<IkePayload> makeInboundRekeyChildPayloads(
            int remoteSpi, IkeSaPayload saPayload, boolean isLocalInitRekey) throws Exception {
        List<IkePayload> inboundPayloads = new ArrayList<>();

        inboundPayloads.add(saPayload);

        // Build TS Payloads
        IkeTrafficSelector[] initTs =
                isLocalInitRekey
                        ? mChildSessionStateMachine.mLocalTs
                        : mChildSessionStateMachine.mRemoteTs;
        IkeTrafficSelector[] respTs =
                isLocalInitRekey
                        ? mChildSessionStateMachine.mRemoteTs
                        : mChildSessionStateMachine.mLocalTs;
        inboundPayloads.add(new IkeTsPayload(true /*isInitiator*/, initTs));
        inboundPayloads.add(new IkeTsPayload(false /*isInitiator*/, respTs));

        // Build Nonce Payloads
        inboundPayloads.add(new IkeNoncePayload(createMockRandomFactory()));

        if (isLocalInitRekey) {
            // Rekey-Create response without Notify-Rekey payload is valid.
            return inboundPayloads;
        }

        // Build Rekey-Notification
        inboundPayloads.add(
                new IkeNotifyPayload(
                        PROTOCOL_ID_ESP,
                        mSpyCurrentChildSaRecord.getRemoteSpi(),
                        NOTIFY_TYPE_REKEY_SA,
                        new byte[0]));

        return inboundPayloads;
    }

    private List<IkePayload> receiveRekeyChildRequest() throws Exception {
        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_REQ_SA_PAYLOAD,
                        false /*isLocalInitRekey*/);
        when(mMockSaRecordHelper.makeChildSaRecord(
                        eq(rekeyReqPayloads), any(List.class), any(ChildSaRecordConfig.class)))
                .thenReturn(mSpyRemoteInitNewChildSaRecord);

        // Receive rekey Child request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_REKEY_CHILD, EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyReqPayloads);
        mLooper.dispatchAll();

        return rekeyReqPayloads;
    }

    private List<IkePayload> receiveRekeyChildResponse() throws Exception {
        List<IkePayload> rekeyRespPayloads =
                makeInboundRekeyChildPayloads(
                        LOCAL_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_RESP_SA_PAYLOAD,
                        true /*isLocalInitRekey*/);
        when(mMockSaRecordHelper.makeChildSaRecord(
                        any(List.class), eq(rekeyRespPayloads), any(ChildSaRecordConfig.class)))
                .thenReturn(mSpyLocalInitNewChildSaRecord);

        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyRespPayloads);
        mLooper.dispatchAll();

        return rekeyRespPayloads;
    }

    private void setupStateMachineAndSpiForLocalRekey() throws Exception {
        setupStateMachineAndSpiForLocalRekey(LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    private void setupStateMachineAndSpiForLocalRekey(
            InetAddress updatedLocalAddress, InetAddress updatedRemoteAddress) throws Exception {
        setupIdleStateMachine();
        setUpSpiResource(updatedLocalAddress, LOCAL_INIT_NEW_CHILD_SA_SPI_IN);
        setUpSpiResource(updatedRemoteAddress, LOCAL_INIT_NEW_CHILD_SA_SPI_OUT);
    }

    @Test
    public void testRekeyChildLocalCreateValidatesResponse() throws Exception {
        setupStateMachineAndSpiForLocalRekey();

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();

        verifyRekeyChildLocalCreateHandlesResponse(
                ChildSessionStateMachine.RekeyChildLocalCreate.class,
                false /* isMobikeRekey */,
                LOCAL_ADDRESS,
                REMOTE_ADDRESS);
    }

    private void verifyRekeyChildLocalCreateHandlesResponse(
            Class<?> expectedState,
            boolean isMobikeRekey,
            InetAddress localAddress,
            InetAddress remoteAddress)
            throws Exception {
        verifyRekeyChildLocalCreateHandlesResponse(
                expectedState, isMobikeRekey, localAddress, remoteAddress, mMockUdpEncapSocket);
    }

    private void verifyRekeyChildLocalCreateHandlesResponse(
            Class<?> expectedState,
            boolean isMobikeRekey,
            InetAddress localAddress,
            InetAddress remoteAddress,
            UdpEncapsulationSocket newEncapSocket)
            throws Exception {
        assertTrue(expectedState.isInstance(mChildSessionStateMachine.getCurrentState()));

        List<IkePayload> rekeyRespPayloads = receiveRekeyChildResponse();
        verifyLocalRekeyCreateIsDone(
                rekeyRespPayloads, isMobikeRekey, localAddress, remoteAddress, newEncapSocket);
    }

    private void verifyLocalRekeyCreateIsDone(
            List<IkePayload> rekeyRespPayloads,
            boolean isMobikeRekey,
            InetAddress localAddress,
            InetAddress remoteAddress)
            throws Exception {
        verifyLocalRekeyCreateIsDone(
                rekeyRespPayloads, isMobikeRekey, localAddress, remoteAddress, mMockUdpEncapSocket);
    }

    private void verifyLocalRekeyCreateIsDone(
            List<IkePayload> rekeyRespPayloads,
            boolean isMobikeRekey,
            InetAddress localAddress,
            InetAddress remoteAddress,
            UdpEncapsulationSocket newEncapSocket)
            throws Exception {
        // Verify state transition
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalDelete);

        // Verify newly created ChildSaRecord
        assertEquals(
                mSpyLocalInitNewChildSaRecord,
                mChildSessionStateMachine.mLocalInitNewChildSaRecord);
        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(
                        eq(mSpyLocalInitNewChildSaRecord.getRemoteSpi()),
                        eq(mChildSessionStateMachine));

        verify(mMockSaRecordHelper)
                .makeChildSaRecord(
                        any(List.class),
                        eq(rekeyRespPayloads),
                        mChildSaRecordConfigCaptor.capture());
        ChildSaRecordConfig childSaRecordConfig = mChildSaRecordConfigCaptor.getValue();
        verifyChildSaRecordConfig(
                childSaRecordConfig,
                LOCAL_INIT_NEW_CHILD_SA_SPI_IN,
                LOCAL_INIT_NEW_CHILD_SA_SPI_OUT,
                true /*isLocalInit*/,
                localAddress,
                remoteAddress,
                newEncapSocket);

        // Verify users have been notified
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));

        if (isMobikeRekey) {
            verify(mMockChildSessionCallback)
                    .onIpSecTransformsMigrated(
                            mSpyLocalInitNewChildSaRecord.getInboundIpSecTransform(),
                            mSpyLocalInitNewChildSaRecord.getOutboundIpSecTransform());
        } else {
            verifyNotifyUsersCreateIpSecSa(mSpyLocalInitNewChildSaRecord, true /*expectInbound*/);
            verifyNotifyUsersCreateIpSecSa(mSpyLocalInitNewChildSaRecord, false /*expectInbound*/);
        }
    }

    @Test
    public void testRekeyLocalCreateHandlesErrorNotifyResp() throws Exception {
        setupIdleStateMachine();
        setUpSpiResource(LOCAL_ADDRESS, LOCAL_INIT_NEW_CHILD_SA_SPI_IN);

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();

        // Receive error notification in Create response
        IkeNotifyPayload notifyPayload = new IkeNotifyPayload(ERROR_TYPE_INTERNAL_ADDRESS_FAILURE);
        List<IkePayload> respPayloads = new ArrayList<>();
        respPayloads.add(notifyPayload);
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, respPayloads);
        mLooper.dispatchAll();

        // Verify rekey has been rescheduled and Child Session is alive
        verify(mSpyCurrentChildSaRecord).rescheduleRekey(eq(RETRY_INTERVAL_MS));
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        // Verify no SPI for provisional Child was registered.
        verify(mMockChildSessionSmCallback, never())
                .onChildSaCreated(anyInt(), eq(mChildSessionStateMachine));
    }

    private void verifySendOutboundIkeDeleteRequest() {
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        eq(false),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));

        List<IkePayload> reqPayloadList = mPayloadListCaptor.getValue();

        List<IkeDeletePayload> deletePayloads =
                IkePayload.getPayloadListForTypeInProvidedList(
                        PAYLOAD_TYPE_DELETE, IkeDeletePayload.class, reqPayloadList);
        assertEquals(deletePayloads.size(), 1);

        IkeDeletePayload deletePayload = deletePayloads.get(0);
        assertEquals(deletePayload.protocolId, IkePayload.PROTOCOL_ID_IKE);
        assertEquals(deletePayload.spisToDelete.length, 0);
    }

    private <T extends IkeException> void verifyIkeSessionFatalErrorAndSendOutboundIkeDeletePayload(
            Class<T> exceptionClass, IState expectedState, int expectedErrorCode) {
        verifySendOutboundIkeDeleteRequest();

        // Verify callback onFatalIkeSessionError() has been invoked
        verify(mMockChildSessionSmCallback).onFatalIkeSessionError(any(exceptionClass));

        // Verify retry was not scheduled
        verify(mMockChildSessionSmCallback, never()).scheduleRetryLocalRequest(any());

        verifyMetricsLogged(
                IkeMetrics.IKE_SESSION_TYPE_CHILD, getStateCode(expectedState), expectedErrorCode);
    }

    @Test
    public void testMobikeRekeyLocalCreateAndHandlesErrorNotifyResp() throws Exception {
        setupStateMachineAndSpiForLocalRekey(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);

        // Send Mobike-Rekey-Create request
        mChildSessionStateMachine.performRekeyMigration(
                UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket);
        mLooper.dispatchAll();

        // Receive error notification in Create response
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_CREATE_CHILD_SA,
                Arrays.asList(new IkeNotifyPayload(ERROR_TYPE_NO_ADDITIONAL_SAS)));
        mLooper.dispatchAll();

        verifyIkeSessionFatalErrorAndSendOutboundIkeDeletePayload(
                NoAdditionalSasException.class,
                mChildSessionStateMachine.mMobikeRekeyChildLocalCreate,
                IkeMetrics.IKE_ERROR_PROTOCOL_NO_ADDITIONAL_SAS);
    }

    @Test
    public void testMobikeRekeyLocalCreateAndHandlesMissingPayloadInResp() throws Exception {
        setupStateMachineAndSpiForLocalRekey(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);

        // Send Mobike-Rekey-Create request
        mChildSessionStateMachine.performRekeyMigration(
                UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket);
        mLooper.dispatchAll();

        // Receive response with no SA Payload
        List<IkePayload> validRekeyRespPayloads =
                makeInboundRekeyChildPayloads(
                        LOCAL_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_RESP_SA_PAYLOAD,
                        true /*isLocalInitRekey*/);
        List<IkePayload> respPayloads = new ArrayList<>();
        for (IkePayload payload : validRekeyRespPayloads) {
            if (IkePayload.PAYLOAD_TYPE_SA == payload.payloadType) continue;
            respPayloads.add(payload);
        }
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, respPayloads);
        mLooper.dispatchAll();

        verifyIkeSessionFatalErrorAndSendOutboundIkeDeletePayload(
                IkeException.class,
                mChildSessionStateMachine.mMobikeRekeyChildLocalCreate,
                IkeMetrics.IKE_ERROR_PROTOCOL_INVALID_SYNTAX);
    }

    @Test
    public void testRekeyLocalCreateHandlesRekeyRequest() throws Exception {
        setupStateMachineAndSpiForLocalRekey();

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();

        receiveRekeyChildRequest();

        // Verify error notification was sent and state machine stays in the same state
        verifyOutboundErrorNotify(EXCHANGE_TYPE_INFORMATIONAL, ERROR_TYPE_TEMPORARY_FAILURE);
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalCreate);

        // Receive Rekey Create response and verify creation is done
        List<IkePayload> rekeyRespPayloads = receiveRekeyChildResponse();
        verifyLocalRekeyCreateIsDone(
                rekeyRespPayloads, false /* isMobikeRekey */, LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    @Test
    public void testRekeyLocalCreateHandlesDeleteRequest() throws Exception {
        setupStateMachineAndSpiForLocalRekey();

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();

        // Receive Delete request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        // Verify Delete response was sent, users were notified and statemachine is still running
        verifyOutboundDeletePayload(mSpyCurrentChildSaRecord.getLocalSpi(), true /*isResp*/);
        verifyNotifyUsersDeleteSession(
                mSpyUserCbExecutor, mChildSessionStateMachine.mRekeyChildLocalCreate);
        assertNotNull(mChildSessionStateMachine.getCurrentState());

        // Receive Rekey Create response and verify Child Session is closed
        List<IkePayload> rekeyRespPayloads = receiveRekeyChildResponse();
        assertNull(mChildSessionStateMachine.getCurrentState());
        verifyNoMoreInteractions(mIkeMetrics);
    }

    @Test
    public void testRekeyLocalCreateHandlesRespWithMissingPayload() throws Exception {
        setupIdleStateMachine();
        setUpSpiResource(LOCAL_ADDRESS, LOCAL_INIT_NEW_CHILD_SA_SPI_IN);
        reset(mMockChildSessionSmCallback);

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();

        // Receive response with no SA Payload
        List<IkePayload> validRekeyRespPayloads =
                makeInboundRekeyChildPayloads(
                        LOCAL_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_RESP_SA_PAYLOAD,
                        true /*isLocalInitRekey*/);
        List<IkePayload> respPayloads = new ArrayList<>();
        for (IkePayload payload : validRekeyRespPayloads) {
            if (IkePayload.PAYLOAD_TYPE_SA == payload.payloadType) continue;
            respPayloads.add(payload);
        }
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, respPayloads);
        mLooper.dispatchAll();

        // Verify user was notified and state machine has quit.
        verifyNotifyUsersDeleteSession(
                mSpyUserCbExecutor,
                mChildSessionStateMachine.mRekeyChildLocalCreate,
                InvalidSyntaxException.class,
                IkeMetrics.IKE_ERROR_PROTOCOL_INVALID_SYNTAX);

        // Verify no SPI for provisional Child was registered.
        verify(mMockChildSessionSmCallback, never())
                .onChildSaCreated(anyInt(), eq(mChildSessionStateMachine));

        // Verify retry was not scheduled
        verify(mMockChildSessionSmCallback, never()).scheduleRetryLocalRequest(any());
    }

    @Ignore
    public void disableTestRekeyLocalCreateChildHandlesKeyCalculationFail() throws Exception {
        // Throw exception when building ChildSaRecord
        when(mMockSaRecordHelper.makeChildSaRecord(any(), any(), any()))
                .thenThrow(
                        new GeneralSecurityException(
                                "testRekeyCreateChildHandlesKeyCalculationFail"));

        // Setup for rekey negotiation
        setupIdleStateMachine();
        setUpSpiResource(LOCAL_ADDRESS, LOCAL_INIT_NEW_CHILD_SA_SPI_IN);
        setUpSpiResource(REMOTE_ADDRESS, LOCAL_INIT_NEW_CHILD_SA_SPI_OUT);
        reset(mMockChildSessionSmCallback);

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalCreate);

        // Receive Rekey response
        List<IkePayload> rekeyRespPayloads =
                makeInboundRekeyChildPayloads(
                        LOCAL_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_RESP_SA_PAYLOAD,
                        true /*isLocalInitRekey*/);
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyRespPayloads);
        mLooper.dispatchAll();

        // Verify user was notified and state machine has quit.
        verifyNotifyUsersDeleteSession(
                mSpyUserCbExecutor,
                mChildSessionStateMachine.mRekeyChildLocalCreate,
                IkeInternalException.class,
                IkeMetrics.IKE_ERROR_INTERNAL);

        // Verify SPI for provisional Child was registered and unregistered.
        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(LOCAL_INIT_NEW_CHILD_SA_SPI_OUT, mChildSessionStateMachine);
        verify(mMockChildSessionSmCallback).onChildSaDeleted(LOCAL_INIT_NEW_CHILD_SA_SPI_OUT);

        // Verify retry was not scheduled
        verify(mMockChildSessionSmCallback, never()).scheduleRetryLocalRequest(any());
    }

    @Test
    public void testRekeyChildLocalDeleteSendsRequest() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildLocalDelete
        mChildSessionStateMachine.mLocalInitNewChildSaRecord = mSpyLocalInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildLocalDelete);
        mLooper.dispatchAll();

        // Verify outbound delete request
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalDelete);
        verifyOutboundDeletePayload(mSpyCurrentChildSaRecord.getLocalSpi(), false /*isResp*/);

        assertEquals(mSpyCurrentChildSaRecord, mChildSessionStateMachine.mCurrentChildSaRecord);
        assertEquals(
                mSpyLocalInitNewChildSaRecord, mChildSessionStateMachine.mChildSaRecordSurviving);
    }

    void verifyChildSaUpdated(ChildSaRecord oldSaRecord, ChildSaRecord newSaRecord) {
        verify(mMockChildSessionSmCallback).onChildSaDeleted(oldSaRecord.getRemoteSpi());
        verify(oldSaRecord).close();

        assertNull(mChildSessionStateMachine.mChildSaRecordSurviving);
        assertEquals(newSaRecord, mChildSessionStateMachine.mCurrentChildSaRecord);
    }

    private void mockRekeyChildLocalCreate() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildLocalDelete
        mChildSessionStateMachine.mLocalInitNewChildSaRecord = mSpyLocalInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildLocalDelete);
        mLooper.dispatchAll();
    }

    @Test
    public void testRekeyChildLocalDeleteValidatesResponse() throws Exception {
        mockRekeyChildLocalCreate();

        // Test receiving Delete response
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        verifyRekeyChildLocalDeleteIsDone();
    }

    private void verifyRekeyChildLocalDeleteIsDone() throws Exception {
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        // First invoked in #setupIdleStateMachine
        verify(mMockChildSessionSmCallback, times(2))
                .onProcedureFinished(mChildSessionStateMachine);

        verifyChildSaUpdated(mSpyCurrentChildSaRecord, mSpyLocalInitNewChildSaRecord);

        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verify(mMockChildSessionCallback, never()).onClosed();
        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
    }

    @Test
    public void testRekeyLocalDeleteHandlesRekeyRequest() throws Exception {
        mockRekeyChildLocalCreate();

        receiveRekeyChildRequest();

        // Verify error notification was sent and state machine stays in the same state
        verifyOutboundErrorNotify(EXCHANGE_TYPE_INFORMATIONAL, ERROR_TYPE_TEMPORARY_FAILURE);
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalDelete);

        // Test receiving Delete response
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();
        verifyRekeyChildLocalDeleteIsDone();
    }

    @Test
    public void testRekeyLocalDeleteHandlesDeleteRequest() throws Exception {
        mockRekeyChildLocalCreate();

        // Test receiving Delete request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        // Verify empty message was sent and state machine stays in the same state
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        eq(true /*isResp*/),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));
        assertTrue(mPayloadListCaptor.getValue().isEmpty());
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalDelete);

        // Test receiving Delete response
        mChildSessionStateMachine.receiveResponse(EXCHANGE_TYPE_INFORMATIONAL, new ArrayList<>());
        mLooper.dispatchAll();
        verifyRekeyChildLocalDeleteIsDone();
    }

    @Test
    public void testRekeyChildLocalDeleteHandlesInvalidResp() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildLocalDelete
        mChildSessionStateMachine.mLocalInitNewChildSaRecord = mSpyLocalInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildLocalDelete);
        mLooper.dispatchAll();

        // Test receiving Delete response with missing Delete payload
        mChildSessionStateMachine.receiveResponse(
                EXCHANGE_TYPE_INFORMATIONAL, new ArrayList<IkePayload>());
        mLooper.dispatchAll();

        // Verify rekey has finished
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);
        verifyChildSaUpdated(mSpyCurrentChildSaRecord, mSpyLocalInitNewChildSaRecord);
        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);

        // First invoked in #setupIdleStateMachine
        verify(mMockChildSessionSmCallback, times(2))
                .onProcedureFinished(mChildSessionStateMachine);
    }

    @Test
    public void testRekeyChildRemoteCreate() throws Exception {
        setupIdleStateMachine();

        // Setup for new Child SA negotiation.
        setUpSpiResource(LOCAL_ADDRESS, REMOTE_INIT_NEW_CHILD_SA_SPI_IN);
        setUpSpiResource(REMOTE_ADDRESS, REMOTE_INIT_NEW_CHILD_SA_SPI_OUT);

        List<IkePayload> rekeyReqPayloads = receiveRekeyChildRequest();

        assertEquals(0, mChildSessionStateMachine.mSaProposal.getDhGroups().size());

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildRemoteDelete);

        // Verify outbound rekey response
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_CREATE_CHILD_SA),
                        eq(true),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));
        List<IkePayload> respPayloadList = mPayloadListCaptor.getValue();
        verifyOutboundCreatePayloadTypes(respPayloadList, true /*isRekey*/);

        verifyOutboundRekeySaPayload(respPayloadList, true /*isResp*/);
        verifyOutboundRekeyNotifyPayload(respPayloadList);

        // Verify new Child SA
        assertEquals(
                mSpyRemoteInitNewChildSaRecord,
                mChildSessionStateMachine.mRemoteInitNewChildSaRecord);

        verify(mMockChildSessionSmCallback)
                .onChildSaCreated(
                        eq(mSpyRemoteInitNewChildSaRecord.getRemoteSpi()),
                        eq(mChildSessionStateMachine));

        verify(mMockSaRecordHelper)
                .makeChildSaRecord(
                        eq(rekeyReqPayloads),
                        any(List.class),
                        mChildSaRecordConfigCaptor.capture());
        ChildSaRecordConfig childSaRecordConfig = mChildSaRecordConfigCaptor.getValue();
        verifyChildSaRecordConfig(
                childSaRecordConfig,
                REMOTE_INIT_NEW_CHILD_SA_SPI_OUT,
                REMOTE_INIT_NEW_CHILD_SA_SPI_IN,
                false /*isLocalInit*/);

        // Verify that users are notified the creation of new inbound IpSecTransform
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verifyNotifyUsersCreateIpSecSa(mSpyRemoteInitNewChildSaRecord, true /*expectInbound*/);
    }

    private void verifyOutboundErrorNotify(int exchangeType, int errorCode) {
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(exchangeType),
                        eq(true),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));
        List<IkePayload> respPayloadList = mPayloadListCaptor.getValue();

        assertEquals(1, respPayloadList.size());
        IkePayload payload = respPayloadList.get(0);
        assertEquals(IkePayload.PAYLOAD_TYPE_NOTIFY, payload.payloadType);
        assertEquals(errorCode, ((IkeNotifyPayload) payload).notifyType);
    }

    @Test
    public void testRekeyChildRemoteCreateHandlesInvalidReq() throws Exception {
        setupIdleStateMachine();

        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_UNACCEPTABLE_REQ_SA_PAYLOAD,
                        false /*isLocalInitRekey*/);

        // Receive rekey Child request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_REKEY_CHILD, EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyReqPayloads);
        mLooper.dispatchAll();

        // Verify error notification was sent and state machind was back to Idle
        verifyOutboundErrorNotify(EXCHANGE_TYPE_CREATE_CHILD_SA, ERROR_TYPE_NO_PROPOSAL_CHOSEN);

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);
    }

    @Test
    public void testRekeyChildRemoteCreateSaCreationFail() throws Exception {
        // Throw exception when building ChildSaRecord
        when(mMockSaRecordHelper.makeChildSaRecord(any(), any(), any()))
                .thenThrow(
                        new GeneralSecurityException("testRekeyChildRemoteCreateSaCreationFail"));

        setupIdleStateMachine();

        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT,
                        REKEY_CHILD_REQ_SA_PAYLOAD,
                        false /*isLocalInitRekey*/);

        // Receive rekey Child request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_REKEY_CHILD, EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyReqPayloads);
        mLooper.dispatchAll();

        // Verify error notification was sent and state machind was back to Idle
        verifyOutboundErrorNotify(EXCHANGE_TYPE_CREATE_CHILD_SA, ERROR_TYPE_NO_PROPOSAL_CHOSEN);

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);
    }

    @Test
    public void testRekeyChildRemoteDelete() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildRemoteDelete
        mChildSessionStateMachine.mRemoteInitNewChildSaRecord = mSpyRemoteInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildRemoteDelete);

        // Test receiving Delete request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyCurrentChildSaRecord.getRemoteSpi()));
        mLooper.dispatchAll();

        // Verify outbound Delete response
        verifyOutboundDeletePayload(mSpyCurrentChildSaRecord.getLocalSpi(), true /*isResp*/);

        // Verify Child SA has been updated
        verifyChildSaUpdated(mSpyCurrentChildSaRecord, mSpyRemoteInitNewChildSaRecord);

        // Verify procedure has been finished. #onProcedureFinished was first invoked in
        // #setupIdleStateMachine
        verify(mMockChildSessionSmCallback, times(2))
                .onProcedureFinished(mChildSessionStateMachine);
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        verify(mSpyUserCbExecutor, times(2)).execute(any(Runnable.class));

        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
        verifyNotifyUsersCreateIpSecSa(mSpyRemoteInitNewChildSaRecord, false /*expectInbound*/);
        verify(mMockChildSessionCallback, never()).onClosed();
    }

    @Test
    public void testRekeyChildLocalDeleteWithReqForNewSa() throws Exception {
        setupIdleStateMachine();
        reset(mMockChildSessionSmCallback);

        // Seed fake rekey data and force transition to RekeyChildLocalDelete
        mChildSessionStateMachine.mLocalInitNewChildSaRecord = mSpyLocalInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildLocalDelete);
        mLooper.dispatchAll();

        // Test receiving Delete new Child SA request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyLocalInitNewChildSaRecord.getRemoteSpi()));

        // Only dispatch the Message of receiving request
        mLooper.dispatchNext();
        assertTrue(mChildSessionStateMachine.getCurrentState() instanceof IdleWithDeferredRequest);
        verify(mMockChildSessionSmCallback, never()).onProcedureFinished(mChildSessionStateMachine);

        // Continue dispatching the deferred request
        mLooper.dispatchAll();

        // Verify outbound Delete response on new Child SA
        verifyOutboundDeletePayload(mSpyLocalInitNewChildSaRecord.getLocalSpi(), true /*isResp*/);
        verify(mMockChildSessionSmCallback)
                .onChildSaDeleted(mSpyLocalInitNewChildSaRecord.getRemoteSpi());
        verify(mSpyLocalInitNewChildSaRecord).close();

        assertNull(mChildSessionStateMachine.getCurrentState());

        verify(mSpyUserCbExecutor, times(2)).execute(any(Runnable.class));

        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
        verifyNotifyUserDeleteChildSa(mSpyLocalInitNewChildSaRecord);

        verify(mMockChildSessionCallback).onClosed();

        // #onProcedureFinished is only called when Child Session is closed
        verify(mMockChildSessionSmCallback).onProcedureFinished(mChildSessionStateMachine);
    }

    @Test
    public void testRekeyChildRemoteDeleteWithReqForNewSa() throws Exception {
        setupIdleStateMachine();
        reset(mMockChildSessionSmCallback);

        // Seed fake rekey data and force transition to RekeyChildRemoteDelete
        mChildSessionStateMachine.mRemoteInitNewChildSaRecord = mSpyRemoteInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildRemoteDelete);
        mLooper.dispatchAll();

        // Test receiving Delete new Child SA request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_DELETE_CHILD,
                EXCHANGE_TYPE_INFORMATIONAL,
                makeDeletePayloads(mSpyRemoteInitNewChildSaRecord.getRemoteSpi()));

        // Only dispatch the Message of receiving request
        mLooper.dispatchNext();
        assertTrue(mChildSessionStateMachine.getCurrentState() instanceof IdleWithDeferredRequest);
        verify(mMockChildSessionSmCallback, never()).onProcedureFinished(mChildSessionStateMachine);

        // Continue dispatching the deferred request
        mLooper.dispatchAll();

        // Verify outbound Delete response on new Child SA
        verifyOutboundDeletePayload(mSpyRemoteInitNewChildSaRecord.getLocalSpi(), true /*isResp*/);
        verify(mMockChildSessionSmCallback)
                .onChildSaDeleted(mSpyRemoteInitNewChildSaRecord.getRemoteSpi());
        verify(mSpyRemoteInitNewChildSaRecord).close();

        assertNull(mChildSessionStateMachine.getCurrentState());

        verify(mSpyUserCbExecutor, times(3)).execute(any(Runnable.class));

        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
        verifyNotifyUserDeleteChildSa(mSpyRemoteInitNewChildSaRecord);
        verifyNotifyUsersCreateIpSecSa(mSpyRemoteInitNewChildSaRecord, false /*expectInbound*/);

        verify(mMockChildSessionCallback).onClosed();

        // #onProcedureFinished is only called when Child Session is closed
        verify(mMockChildSessionSmCallback).onProcedureFinished(mChildSessionStateMachine);
    }

    @Test
    public void testRekeyChildRemoteDeleteTimeout() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildRemoteDelete
        mChildSessionStateMachine.mRemoteInitNewChildSaRecord = mSpyRemoteInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildRemoteDelete);
        mLooper.dispatchAll();

        mLooper.moveTimeForward(REKEY_DELETE_TIMEOUT_MS);
        mLooper.dispatchAll();

        // Verify no response sent.
        verify(mMockChildSessionSmCallback, never())
                .onOutboundPayloadsReady(anyInt(), anyBoolean(), any(List.class), anyObject());

        // Verify Child SA has been renewed
        verifyChildSaUpdated(mSpyCurrentChildSaRecord, mSpyRemoteInitNewChildSaRecord);

        // Verify procedure has been finished. #onProcedureFinished was first invoked in
        // #setupIdleStateMachine
        verify(mMockChildSessionSmCallback, times(2))
                .onProcedureFinished(mChildSessionStateMachine);
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        verify(mSpyUserCbExecutor, times(2)).execute(any(Runnable.class));

        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
        verifyNotifyUsersCreateIpSecSa(mSpyRemoteInitNewChildSaRecord, false /*expectInbound*/);

        verify(mMockChildSessionCallback, never()).onClosed();
    }

    @Test
    public void testRekeyChildRemoteDeleteExitAndRenter() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildRemoteDelete
        mChildSessionStateMachine.mRemoteInitNewChildSaRecord = mSpyRemoteInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildRemoteDelete);
        mLooper.dispatchAll();

        // Trigger a timeout, and immediately re-enter remote-delete
        mLooper.moveTimeForward(REKEY_DELETE_TIMEOUT_MS / 2 + 1);
        mChildSessionStateMachine.sendMessage(ChildSessionStateMachine.TIMEOUT_REKEY_REMOTE_DELETE);
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildRemoteDelete);
        mLooper.dispatchAll();

        // Shift time forward
        mLooper.moveTimeForward(REKEY_DELETE_TIMEOUT_MS / 2 + 1);
        mLooper.dispatchAll();

        // Verify final state has not changed - timeout was not triggered.
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildRemoteDelete);

        verify(mSpyUserCbExecutor, times(2)).execute(any(Runnable.class));

        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
        verifyNotifyUsersCreateIpSecSa(mSpyRemoteInitNewChildSaRecord, false /*expectInbound*/);

        verify(mMockChildSessionCallback, never()).onClosed();
    }

    @Test
    public void testCloseSessionNow() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyChildLocalDelete
        mChildSessionStateMachine.mLocalInitNewChildSaRecord = mSpyLocalInitNewChildSaRecord;
        mChildSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mChildSessionStateMachine.mRekeyChildLocalDelete);

        mChildSessionStateMachine.killSession();
        mLooper.dispatchAll();

        assertNull(mChildSessionStateMachine.getCurrentState());

        verify(mSpyUserCbExecutor, times(3)).execute(any(Runnable.class));

        verifyNotifyUserDeleteChildSa(mSpyCurrentChildSaRecord);
        verifyNotifyUserDeleteChildSa(mSpyLocalInitNewChildSaRecord);

        verify(mMockChildSessionCallback).onClosed();
        verify(mIkeMetrics, never()).logSessionTerminated(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testValidateExpectKeExistCase() throws Exception {
        doReturn(new DhGroupTransform[] {mChildDhGroupTransform})
                .when(mMockNegotiatedProposal)
                .getDhGroupTransforms();
        List<IkePayload> payloadList = new ArrayList<>();
        payloadList.add(
                IkeKePayload.createOutboundKePayload(
                        SaProposal.DH_GROUP_1024_BIT_MODP, createMockRandomFactory()));

        CreateChildSaHelper.validateKePayloads(
                payloadList, true /*isResp*/, mMockNegotiatedProposal);
        CreateChildSaHelper.validateKePayloads(
                payloadList, false /*isResp*/, mMockNegotiatedProposal);
    }

    @Test
    public void testValidateExpectNoKeExistCase() throws Exception {
        doReturn(new DhGroupTransform[0]).when(mMockNegotiatedProposal).getDhGroupTransforms();
        List<IkePayload> payloadList = new ArrayList<>();

        CreateChildSaHelper.validateKePayloads(
                payloadList, true /*isResp*/, mMockNegotiatedProposal);
        CreateChildSaHelper.validateKePayloads(
                payloadList, false /*isResp*/, mMockNegotiatedProposal);
    }

    @Test
    public void testThrowWhenKeMissing() throws Exception {
        doReturn(new DhGroupTransform[] {mChildDhGroupTransform})
                .when(mMockNegotiatedProposal)
                .getDhGroupTransforms();
        List<IkePayload> payloadList = new ArrayList<>();

        try {
            CreateChildSaHelper.validateKePayloads(
                    payloadList, true /*isResp*/, mMockNegotiatedProposal);
            fail("Expected to fail due to the absence of KE Payload");
        } catch (InvalidSyntaxException expected) {
        }

        try {
            CreateChildSaHelper.validateKePayloads(
                    payloadList, false /*isResp*/, mMockNegotiatedProposal);
            fail("Expected to fail due to the absence of KE Payload");
        } catch (InvalidKeException expected) {
        }
    }

    @Test
    public void testThrowWhenKeHasMismatchedDhGroup() throws Exception {
        doReturn(new DhGroupTransform[] {mChildDhGroupTransform})
                .when(mMockNegotiatedProposal)
                .getDhGroupTransforms();
        List<IkePayload> payloadList = new ArrayList<>();
        payloadList.add(
                IkeKePayload.createOutboundKePayload(
                        SaProposal.DH_GROUP_2048_BIT_MODP, createMockRandomFactory()));

        try {
            CreateChildSaHelper.validateKePayloads(
                    payloadList, true /*isResp*/, mMockNegotiatedProposal);
            fail("Expected to fail due to mismatched DH Group");
        } catch (InvalidSyntaxException expected) {
        }

        try {
            CreateChildSaHelper.validateKePayloads(
                    payloadList, false /*isResp*/, mMockNegotiatedProposal);
            fail("Expected to fail due to mismatched DH Group");
        } catch (InvalidKeException expected) {
        }
    }

    @Test
    public void testThrowForUnexpectedKe() throws Exception {
        DhGroupTransform noneGroup = new DhGroupTransform(SaProposal.DH_GROUP_NONE);
        doReturn(new DhGroupTransform[] {noneGroup})
                .when(mMockNegotiatedProposal)
                .getDhGroupTransforms();
        List<IkePayload> payloadList = new ArrayList<>();
        payloadList.add(
                IkeKePayload.createOutboundKePayload(
                        SaProposal.DH_GROUP_2048_BIT_MODP, createMockRandomFactory()));

        try {
            CreateChildSaHelper.validateKePayloads(
                    payloadList, true /*isResp*/, mMockNegotiatedProposal);
            fail("Expected to fail due to unexpected KE payload.");
        } catch (InvalidSyntaxException expected) {
        }

        CreateChildSaHelper.validateKePayloads(
                payloadList, false /*isResp*/, mMockNegotiatedProposal);
    }

    @Test
    public void testHandleUnexpectedException() throws Exception {
        Log spyIkeLog = TestUtils.makeSpyLogDoLogErrorForWtf(TAG);
        IkeManager.setIkeLog(spyIkeLog);

        mChildSessionStateMachine.createChildSession(
                null /*localAddress*/,
                REMOTE_ADDRESS,
                mMockUdpEncapSocket,
                mIkePrf,
                IKE_DH_GROUP,
                SK_D);
        mLooper.dispatchAll();

        verifyHandleFatalErrorAndQuit(
                mChildSessionStateMachine.mCreateChildLocalCreate,
                IkeInternalException.class,
                IkeMetrics.IKE_ERROR_INTERNAL);
        verify(spyIkeLog).wtf(anyString(), anyString(), any(RuntimeException.class));
    }

    @Test
    public void testFirstChildLocalRekey() throws Exception {
        ChildSaProposal saProposal = buildSaProposalWithDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP);
        ChildSessionParams childSessionParams =
                new TunnelModeChildSessionParams.Builder()
                        .addSaProposal(saProposal)
                        .addInternalAddressRequest(AF_INET)
                        .addInternalAddressRequest(INTERNAL_ADDRESS)
                        .build();
        mChildSessionStateMachine = buildChildSession(childSessionParams);
        mChildSessionStateMachine.mIsFirstChild = true;
        mChildSessionStateMachine.setDbg(true);
        mChildSessionStateMachine.start();

        setupIdleStateMachine();

        assertEquals(0, mChildSessionStateMachine.mSaProposal.getDhGroups().size());

        // Send Rekey-Create request
        mChildSessionStateMachine.rekeyChildSession();
        mLooper.dispatchAll();

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildLocalCreate);

        verifyOutboundRekeyKePayload(false /*isResp*/);
    }

    private void verifyOutboundRekeyKePayload(boolean isResp) {
        verify(mMockChildSessionSmCallback)
                .onOutboundPayloadsReady(
                        eq(EXCHANGE_TYPE_CREATE_CHILD_SA),
                        eq(isResp),
                        mPayloadListCaptor.capture(),
                        eq(mChildSessionStateMachine));

        // Verify outbound payload list
        List<IkePayload> reqPayloadList = mPayloadListCaptor.getValue();

        assertNotNull(
                IkePayload.getPayloadForTypeInProvidedList(
                        PAYLOAD_TYPE_KE, IkeKePayload.class, reqPayloadList));
    }

    private ChildSessionStateMachine buildChildSession(
            ChildSessionParams childSessionParams, Executor executor) {
        return new ChildSessionStateMachine(
                new IkeContext(mLooper.getLooper(), mContext, createMockRandomFactory()),
                new ChildSessionStateMachine.Config(
                        IKE_SESSION_UNIQUE_ID,
                        mMockIkeHandler,
                        childSessionParams,
                        mMockIpSecManager,
                        mIpSecSpiGenerator,
                        executor),
                mMockChildSessionCallback,
                mMockChildSessionSmCallback);
    }

    private ChildSessionStateMachine buildChildSession(ChildSessionParams childSessionParams) {
        return buildChildSession(childSessionParams, mSpyUserCbExecutor);
    }

    private ChildSessionStateMachine buildChildSession(Executor executor) {
        return buildChildSession(mChildSessionParams, executor);
    }

    private ChildSessionStateMachine buildAndStartChildSession(Executor executor) {
        ChildSessionStateMachine childSession = buildChildSession(executor);
        childSession.setDbg(true);
        childSession.start();
        mLooper.dispatchAll();

        return childSession;
    }

    private ChildSessionStateMachine buildAndStartStateMachineWithProposal(
            ChildSaProposal childProposal) {
        ChildSessionParams childSessionParams =
                new TunnelModeChildSessionParams.Builder()
                        .addSaProposal(childProposal)
                        .addInternalAddressRequest(AF_INET)
                        .addInternalAddressRequest(INTERNAL_ADDRESS)
                        .build();
        ChildSessionStateMachine childSession = buildChildSession(childSessionParams);
        childSession.setDbg(true);
        childSession.start();
        return childSession;
    }

    private ChildSaProposal buildSaProposalWithDhGroup(int dhGroup) {
        return new ChildSaProposal.Builder()
                .addEncryptionAlgorithm(
                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128)
                .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
                .addDhGroup(dhGroup)
                .build();
    }

    private void verifyRemoteRekeyWithKePayload(ChildSaProposal requestSaProposal, int expectedDh)
            throws Exception {
        // Setup for new Child SA negotiation.
        setUpSpiResource(LOCAL_ADDRESS, REMOTE_INIT_NEW_CHILD_SA_SPI_IN);
        setUpSpiResource(REMOTE_ADDRESS, REMOTE_INIT_NEW_CHILD_SA_SPI_OUT);

        IkeSaPayload saPayload =
                IkeSaPayload.createChildSaRequestPayload(
                        new ChildSaProposal[] {requestSaProposal},
                        mIpSecSpiGenerator,
                        LOCAL_ADDRESS);
        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT, saPayload, false /*isLocalInitRekey*/);

        rekeyReqPayloads.add(
                IkeKePayload.createOutboundKePayload(expectedDh, createMockRandomFactory()));

        when(mMockSaRecordHelper.makeChildSaRecord(
                        eq(rekeyReqPayloads), any(List.class), any(ChildSaRecordConfig.class)))
                .thenReturn(mSpyRemoteInitNewChildSaRecord);

        // Receive rekey Child request
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_REKEY_CHILD, EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyReqPayloads);
        mLooper.dispatchAll();

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.RekeyChildRemoteDelete);

        verifyOutboundRekeyKePayload(true /*isResp*/);

        assertEquals(expectedDh, (int) mChildSessionStateMachine.mSaProposal.getDhGroups().get(0));
    }

    @Test
    public void testRemoteRekeyWithUserSpecifiedKePayload() throws Exception {
        // Use child session params with dh group to initiate the state machine
        ChildSaProposal saProposal = buildSaProposalWithDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP);
        mChildSessionStateMachine.quitNow();
        mChildSessionStateMachine = buildAndStartStateMachineWithProposal(saProposal);

        setupIdleStateMachine();
        assertEquals(0, mChildSessionStateMachine.mSaProposal.getDhGroups().size());

        verifyRemoteRekeyWithKePayload(saProposal, SaProposal.DH_GROUP_2048_BIT_MODP);
    }

    @Test
    public void testRemoteRekeyWithIkeNegotiatedKePayload() throws Exception {
        setupIdleStateMachine();

        assertEquals(0, mChildSessionStateMachine.mSaProposal.getDhGroups().size());
        assertEquals(IKE_DH_GROUP, mChildSessionStateMachine.mIkeDhGroup);
        for (SaProposal userProposal :
                mChildSessionStateMachine.mChildSessionParams.getChildSaProposals()) {
            assertTrue(userProposal.getDhGroups().isEmpty());
        }

        ChildSaProposal saProposal = buildSaProposalWithDhGroup(IKE_DH_GROUP);
        verifyRemoteRekeyWithKePayload(saProposal, IKE_DH_GROUP);
    }

    private void verifyRcvRekeyReqAndRejectWithErrorNotify(
            List<IkePayload> rekeyReqPayloads, int expectedErrorType) {
        mChildSessionStateMachine.receiveRequest(
                IKE_EXCHANGE_SUBTYPE_REKEY_CHILD, EXCHANGE_TYPE_CREATE_CHILD_SA, rekeyReqPayloads);
        mLooper.dispatchAll();

        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        verifyOutboundErrorNotify(EXCHANGE_TYPE_CREATE_CHILD_SA, expectedErrorType);
    }

    @Test
    public void testRemoteRekeyWithInvalidKePayload() throws Exception {
        setupIdleStateMachine();

        assertEquals(0, mChildSessionStateMachine.mSaProposal.getDhGroups().size());
        assertEquals(IKE_DH_GROUP, mChildSessionStateMachine.mIkeDhGroup);
        for (SaProposal userProposal :
                mChildSessionStateMachine.mChildSessionParams.getChildSaProposals()) {
            assertTrue(userProposal.getDhGroups().isEmpty());
        }

        // Build an inbound Rekey Child request
        // Build an SA Payload that includes a Proposal with IKE_DH_GROUP
        IkeSaPayload saPayload =
                IkeSaPayload.createChildSaRequestPayload(
                        new ChildSaProposal[] {buildSaProposalWithDhGroup(IKE_DH_GROUP)},
                        mIpSecSpiGenerator,
                        LOCAL_ADDRESS);
        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT, saPayload, false /*isLocalInitRekey*/);

        // Build a KE Payload that uses a different DH group from the IKE_DH_GROUP
        rekeyReqPayloads.add(
                IkeKePayload.createOutboundKePayload(
                        DH_GROUP_2048_BIT_MODP, createMockRandomFactory()));

        verifyRcvRekeyReqAndRejectWithErrorNotify(rekeyReqPayloads, ERROR_TYPE_INVALID_KE_PAYLOAD);
    }

    @Test
    public void testRejectRemoteRekeyWithoutDhGroupInProposal() throws Exception {
        // Use child session params with dh group to initiate the state machine
        mChildSessionStateMachine.quitNow();
        ChildSaProposal saProposal = buildSaProposalWithDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP);
        mChildSessionStateMachine = buildAndStartStateMachineWithProposal(saProposal);

        setupIdleStateMachine();
        mChildSessionStateMachine.mSaProposal = saProposal;

        // Build a Rekey request that does not propose DH groups.
        IkeSaPayload saPayload =
                IkeSaPayload.createChildSaRequestPayload(
                        new ChildSaProposal[] {buildSaProposal()}, // Proposal with no DH group
                        mIpSecSpiGenerator,
                        LOCAL_ADDRESS);
        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT, saPayload, false /* isLocalInitRekey */);
        rekeyReqPayloads.add(
                IkeKePayload.createOutboundKePayload(
                        DH_GROUP_2048_BIT_MODP, createMockRandomFactory()));

        verifyRcvRekeyReqAndRejectWithErrorNotify(rekeyReqPayloads, ERROR_TYPE_NO_PROPOSAL_CHOSEN);
    }

    @Test
    public void testRejectRemoteRekeyWithoutKePayload() throws Exception {
        // Use child session params with dh group to initiate the state machine
        mChildSessionStateMachine.quitNow();
        ChildSaProposal saProposal = buildSaProposalWithDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP);
        mChildSessionStateMachine = buildAndStartStateMachineWithProposal(saProposal);

        setupIdleStateMachine();
        mChildSessionStateMachine.mSaProposal = saProposal;

        // Build a Rekey request that proposes DH groups but does not include a KE payload
        IkeSaPayload saPayload =
                IkeSaPayload.createChildSaRequestPayload(
                        new ChildSaProposal[] {saProposal}, mIpSecSpiGenerator, LOCAL_ADDRESS);
        List<IkePayload> rekeyReqPayloads =
                makeInboundRekeyChildPayloads(
                        REMOTE_INIT_NEW_CHILD_SA_SPI_OUT, saPayload, false /* isLocalInitRekey */);

        verifyRcvRekeyReqAndRejectWithErrorNotify(rekeyReqPayloads, ERROR_TYPE_INVALID_SYNTAX);
    }

    private void verifyMobikeRekeyFallback(UdpEncapsulationSocket newEncapSocket) throws Exception {
        mLooper.dispatchAll();

        verifyRekeyChildLocalCreateHandlesResponse(
                ChildSessionStateMachine.MobikeRekeyChildLocalCreate.class,
                true /* isMobikeRekey */,
                UPDATED_LOCAL_ADDRESS,
                REMOTE_ADDRESS,
                newEncapSocket);

        assertEquals(UPDATED_LOCAL_ADDRESS, mChildSessionStateMachine.mLocalAddress);
        assertEquals(REMOTE_ADDRESS, mChildSessionStateMachine.mRemoteAddress);
        assertEquals(newEncapSocket, mChildSessionStateMachine.mUdpEncapSocket);
    }

    @Test
    public void testMobikeRekeyChildLocalCreateHandlesResp() throws Exception {
        setupStateMachineAndSpiForLocalRekey(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);

        // Send MOBIKE Rekey-Create request
        mChildSessionStateMachine.performRekeyMigration(
                UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket);
        verifyMobikeRekeyFallback(mMockUdpEncapSocket);
    }

    @Test
    public void testMobikeRekeyChildExecuteCbAfterKillSession() throws Exception {
        mChildSessionStateMachine.quitNow();
        mLooper.dispatchAll();

        LateExecuteExecutor lateExecutor = spy(new LateExecuteExecutor());
        mChildSessionStateMachine = buildAndStartChildSession(lateExecutor);

        setupStateMachineAndSpiForLocalRekey(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);

        // MOBIKE Rekey
        mChildSessionStateMachine.performRekeyMigration(
                UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, mMockUdpEncapSocket);
        mLooper.dispatchAll();
        receiveRekeyChildResponse();
        mLooper.dispatchAll();

        mChildSessionStateMachine.killSession();
        mLooper.dispatchAll();

        lateExecutor.actuallyExecute();
        verify(mMockChildSessionCallback)
                .onIpSecTransformsMigrated(
                        mSpyLocalInitNewChildSaRecord.getInboundIpSecTransform(),
                        mSpyLocalInitNewChildSaRecord.getOutboundIpSecTransform());
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMobike_usesKernelMobikeForSameAddressFamilyAndEncapSocket() throws Exception {
        verifyMobike_usesKernelMobikeForSameEncapSocket(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMobike_usesKernelMobikeForDifferentAddressFamilyAndSameEncapSocket()
            throws Exception {
        verifyMobike_usesKernelMobikeForSameEncapSocket(LOCAL_ADDRESS_6, REMOTE_ADDRESS_6);
    }

    private void verifyMobike_usesKernelMobikeForSameEncapSocket(
            InetAddress newLocalAddress, InetAddress newRemoteAddress) throws Exception {
        doReturn(true)
                .when(mMockPackageManager)
                .hasSystemFeature(PackageManager.FEATURE_IPSEC_TUNNEL_MIGRATION);
        setupIdleStateMachine();

        // Send MOBIKE request
        mChildSessionStateMachine.performMigration(
                newLocalAddress, newRemoteAddress, mMockUdpEncapSocket);
        mLooper.dispatchAll();

        // Verify kernel MOBIKE methods called
        verify(mMockIpSecService)
                .migrateTransform(
                        anyInt(),
                        eq(newLocalAddress.getHostAddress()),
                        eq(newRemoteAddress.getHostAddress()),
                        any());
        verify(mMockIpSecService)
                .migrateTransform(
                        anyInt(),
                        eq(newRemoteAddress.getHostAddress()),
                        eq(newLocalAddress.getHostAddress()),
                        any());

        // Verify callbacks called, and state machine goes back to Idle state
        verify(mMockChildSessionCallback)
                .onIpSecTransformsMigrated(
                        mSpyCurrentChildSaRecord.getInboundIpSecTransform(),
                        mSpyCurrentChildSaRecord.getOutboundIpSecTransform());
        assertTrue(
                mChildSessionStateMachine.getCurrentState()
                        instanceof ChildSessionStateMachine.Idle);

        // Verify addresses and sockets correct
        assertEquals(newLocalAddress, mChildSessionStateMachine.mLocalAddress);
        assertEquals(newRemoteAddress, mChildSessionStateMachine.mRemoteAddress);
        assertEquals(mMockUdpEncapSocket, mChildSessionStateMachine.mUdpEncapSocket);
    }

    private void verifyKernelMobikeFallbackForUnsupportedMigrations(
            UdpEncapsulationSocket newEncapSocket) throws Exception {
        doReturn(true)
                .when(mMockPackageManager)
                .hasSystemFeature(PackageManager.FEATURE_IPSEC_TUNNEL_MIGRATION);
        setupStateMachineAndSpiForLocalRekey(UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS);

        // Send MOBIKE request
        mChildSessionStateMachine.performMigration(
                UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, newEncapSocket);
        verifyMobikeRekeyFallback(newEncapSocket);
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMobike_usesRekeyMobikeFallbackForChangingEncapSocket() throws Exception {
        verifyKernelMobikeFallbackForUnsupportedMigrations(mock(UdpEncapsulationSocket.class));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testMobike_usesRekeyMobikeFallbackForChangingEncapType() throws Exception {
        verifyKernelMobikeFallbackForUnsupportedMigrations(null);
    }

    // TODO (b/277668745): Add tests for 6 -> 4 and 6 -> 6 migrations with and without UDP encap.

    private static class LateExecuteExecutor implements Executor {
        private final List<Runnable> mCommands = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            mCommands.add(command);
        }

        public void actuallyExecute() {
            for (Runnable c : mCommands) {
                c.run();
            }
        }
    }
}
