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

import static android.net.ipsec.ike.IkeSessionConfiguration.EXTENSION_TYPE_MOBIKE;
import static android.net.ipsec.test.ike.IkeSessionConfiguration.EXTENSION_TYPE_FRAGMENTATION;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_EAP_ONLY_AUTH;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_FORCE_PORT_4500;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_MOBIKE;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_REKEY_MOBILITY;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_AUTHENTICATION_FAILED;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_CHILD_SA_NOT_FOUND;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_KE_PAYLOAD;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_INVALID_SYNTAX;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_ADDITIONAL_SAS;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_PROPOSAL_CHOSEN;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_TEMPORARY_FAILURE;
import static android.net.ipsec.test.ike.exceptions.IkeProtocolException.ERROR_TYPE_UNSUPPORTED_CRITICAL_PAYLOAD;
import static android.net.ipsec.test.ike.ike3gpp.Ike3gppBackoffTimer.ERROR_TYPE_NETWORK_FAILURE;
import static android.net.ipsec.test.ike.ike3gpp.Ike3gppBackoffTimer.ERROR_TYPE_NO_APN_SUBSCRIPTION;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;

import static com.android.internal.net.TestUtils.createMockRandomFactory;
import static com.android.internal.net.eap.test.EapResult.EapResponse.RESPONSE_FLAG_EAP_AKA_SERVER_AUTHENTICATED;
import static com.android.internal.net.ipsec.test.ike.AbstractSessionStateMachine.RETRY_INTERVAL_MS;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_ALARM_FIRED;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_FORCE_TRANSITION;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CMD_SEND_KEEPALIVE;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.IkeAuthData;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.IkeInitData;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.InitialSetupData;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.RETRY_INTERVAL_MS;
import static com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.TEMP_FAILURE_RETRY_TIMEOUT_MS;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_BACKOFF_TIMER;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_DEVICE_IDENTITY;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_N1_MODE_CAPABILITY;
import static com.android.internal.net.ipsec.test.ike.ike3gpp.Ike3gppExtensionExchange.NOTIFY_TYPE_N1_MODE_INFORMATION;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_APPLICATION_VERSION;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_INTERNAL_IP4_ADDRESS;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_INTERNAL_IP4_NETMASK;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_INTERNAL_IP6_ADDRESS;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_IP4_PCSCF;
import static com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload.CONFIG_ATTR_IP6_PCSCF;
import static com.android.internal.net.ipsec.test.ike.message.IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA;
import static com.android.internal.net.ipsec.test.ike.message.IkeHeader.EXCHANGE_TYPE_INFORMATIONAL;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_DELETE_CHILD;
import static com.android.internal.net.ipsec.test.ike.message.IkeMessage.IKE_EXCHANGE_SUBTYPE_REKEY_CHILD;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_COOKIE;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_COOKIE2;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_EAP_ONLY_AUTHENTICATION;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_IKEV2_FRAGMENTATION_SUPPORTED;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_INITIAL_CONTACT;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_MOBIKE_SUPPORTED;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_NAT_DETECTION_DESTINATION_IP;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_NAT_DETECTION_SOURCE_IP;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_SIGNATURE_HASH_ALGORITHMS;
import static com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload.NOTIFY_TYPE_UPDATE_SA_ADDRESSES;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_AUTH;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_KE;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_NONCE;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_NOTIFY;
import static com.android.internal.net.ipsec.test.ike.message.IkePayload.PAYLOAD_TYPE_SA;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_DETECTED;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_TRAVERSAL_SUPPORT_NOT_CHECKED;
import static com.android.internal.net.ipsec.test.ike.net.IkeConnectionController.NAT_TRAVERSAL_UNSUPPORTED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.eap.test.EapAkaInfo;
import android.net.eap.test.EapSessionConfig;
import android.net.ipsec.test.ike.ChildSaProposal;
import android.net.ipsec.test.ike.ChildSessionCallback;
import android.net.ipsec.test.ike.ChildSessionParams;
import android.net.ipsec.test.ike.IkeFqdnIdentification;
import android.net.ipsec.test.ike.IkeIdentification;
import android.net.ipsec.test.ike.IkeIpv4AddrIdentification;
import android.net.ipsec.test.ike.IkeManager;
import android.net.ipsec.test.ike.IkeSaProposal;
import android.net.ipsec.test.ike.IkeSessionCallback;
import android.net.ipsec.test.ike.IkeSessionConfiguration;
import android.net.ipsec.test.ike.IkeSessionConnectionInfo;
import android.net.ipsec.test.ike.IkeSessionParams;
import android.net.ipsec.test.ike.SaProposal;
import android.net.ipsec.test.ike.TransportModeChildSessionParams;
import android.net.ipsec.test.ike.TunnelModeChildSessionParams;
import android.net.ipsec.test.ike.exceptions.AuthenticationFailedException;
import android.net.ipsec.test.ike.exceptions.IkeException;
import android.net.ipsec.test.ike.exceptions.IkeIOException;
import android.net.ipsec.test.ike.exceptions.IkeInternalException;
import android.net.ipsec.test.ike.exceptions.IkeNetworkLostException;
import android.net.ipsec.test.ike.exceptions.IkeProtocolException;
import android.net.ipsec.test.ike.exceptions.IkeTimeoutException;
import android.net.ipsec.test.ike.exceptions.InvalidSyntaxException;
import android.net.ipsec.test.ike.exceptions.NoValidProposalChosenException;
import android.net.ipsec.test.ike.exceptions.UnrecognizedIkeProtocolException;
import android.net.ipsec.test.ike.exceptions.UnsupportedCriticalPayloadException;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppBackoffTimer;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppData;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppExtension.Ike3gppDataListener;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppN1ModeInformation;
import android.net.ipsec.test.ike.ike3gpp.Ike3gppParams;
import android.os.test.TestLooper;
import android.telephony.TelephonyManager;

import androidx.test.filters.SdkSuppress;

import com.android.internal.net.TestUtils;
import com.android.internal.net.eap.test.EapAuthenticator;
import com.android.internal.net.eap.test.IEapCallback;
import com.android.internal.net.ipsec.test.ike.ChildSessionStateMachine.IChildSessionSmCallback;
import com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.ChildLocalRequest;
import com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.IkeLocalRequest;
import com.android.internal.net.ipsec.test.ike.IkeLocalRequestScheduler.LocalRequestFactory;
import com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CreateIkeLocalIkeAuth;
import com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.CreateIkeLocalIkeAuthInEap;
import com.android.internal.net.ipsec.test.ike.IkeSessionStateMachine.ReceivedIkePacket;
import com.android.internal.net.ipsec.test.ike.SaRecord.ISaRecordHelper;
import com.android.internal.net.ipsec.test.ike.SaRecord.IkeSaRecord;
import com.android.internal.net.ipsec.test.ike.SaRecord.IkeSaRecordConfig;
import com.android.internal.net.ipsec.test.ike.SaRecord.SaLifetimeAlarmScheduler;
import com.android.internal.net.ipsec.test.ike.SaRecord.SaRecordHelper;
import com.android.internal.net.ipsec.test.ike.crypto.IkeCipher;
import com.android.internal.net.ipsec.test.ike.crypto.IkeMacIntegrity;
import com.android.internal.net.ipsec.test.ike.crypto.IkeMacPrf;
import com.android.internal.net.ipsec.test.ike.keepalive.IkeNattKeepalive;
import com.android.internal.net.ipsec.test.ike.message.IkeAuthDigitalSignPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeAuthPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeAuthPskPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeCertX509CertPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeConfigPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeDeletePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeEapPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeHeader;
import com.android.internal.net.ipsec.test.ike.message.IkeIdPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeInformationalPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeKePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.DecodeResult;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.DecodeResultOk;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.DecodeResultPartial;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.DecodeResultProtectedError;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.DecodeResultUnprotectedError;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.IIkeMessageHelper;
import com.android.internal.net.ipsec.test.ike.message.IkeMessage.IkeMessageHelper;
import com.android.internal.net.ipsec.test.ike.message.IkeNoncePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeNotifyPayload;
import com.android.internal.net.ipsec.test.ike.message.IkePayload;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.DhGroupTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.EncryptionTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.IntegrityTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.PrfTransform;
import com.android.internal.net.ipsec.test.ike.message.IkeSkfPayload;
import com.android.internal.net.ipsec.test.ike.message.IkeTestUtils;
import com.android.internal.net.ipsec.test.ike.message.IkeTsPayload;
import com.android.internal.net.ipsec.test.ike.net.IkeConnectionController;
import com.android.internal.net.ipsec.test.ike.net.IkeDefaultNetworkCallback;
import com.android.internal.net.ipsec.test.ike.net.IkeNetworkCallbackBase;
import com.android.internal.net.ipsec.test.ike.net.IkeSpecificNetworkCallback;
import com.android.internal.net.ipsec.test.ike.testmode.DeterministicSecureRandom;
import com.android.internal.net.ipsec.test.ike.testutils.CertUtils;
import com.android.internal.net.ipsec.test.ike.utils.IState;
import com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;
import com.android.internal.net.ipsec.test.ike.utils.IkeMetrics;
import com.android.internal.net.ipsec.test.ike.utils.IkeSecurityParameterIndex;
import com.android.internal.net.ipsec.test.ike.utils.IkeSpiGenerator;
import com.android.internal.net.ipsec.test.ike.utils.RandomnessFactory;
import com.android.internal.net.ipsec.test.ike.utils.State;
import com.android.internal.net.utils.test.Log;
import com.android.internal.util.HexDump;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class IkeSessionStateMachineTest extends IkeSessionTestBase {
    private static final String TAG = "IkeSessionStateMachineTest";

    private static final String IKE_INIT_RESP_HEX_STRING =
            "5f54bf6d8b48e6e1909232b3d1edcb5c21202220000000000000014c220000300000"
                    + "002c010100040300000c0100000c800e008003000008030000020300000802000002"
                    + "00000008040000022800008800020000fe014fefed55a4229928bfa3dad1ea6ffaca"
                    + "abfb5f5bdd71790e99a192530e3f849d3a3d96dc6e0a7a10ff6f72a6162103ac573c"
                    + "acd41d08b7a034cad8f5eab09c14ced5a9e4af5692dff028f21c1119dd75226b6af6"
                    + "b2f009245369c9892cc5742e5c94a254ebff052470771fb2cb4f29a35d8953e18a1a"
                    + "6c6fbc56acc188a5290000249756112ca539f5c25abacc7ee92b73091942a9c06950"
                    + "f98848f1af1694c4ddff2900001c00004004c53f054b976a25d75fde72dbf1c7b6c8"
                    + "c9aa9ca12900001c00004005b16d79b21c1bc89ca7350f42de805be0227e2ed62b00"
                    + "00080000401400000014882fe56d6fd20dbc2251613b2ebe5beb";
    private static final String IKE_SA_PAYLOAD_HEX_STRING =
            "220000300000002c010100040300000c0100000c800e00800300000803000002030"
                    + "00008020000020000000804000002";
    private static final String IKE_REKEY_SA_PAYLOAD_HEX_STRING =
            "22000038000000340101080400000000000000FF0300000c0100000c800e0080030"
                    + "000080300000203000008020000020000000804000002";
    private static final String IKE_REKEY_UNACCEPTABLE_SA_PAYLOAD_HEX_STRING =
            "22000038000000340101080400000000000000FF0300000c0100000c800e0080030"
                    + "00008030000020300000802000002000000080400000e";
    private static final int IKE_REKEY_SA_INITIATOR_SPI = 0xff;
    private static final String KE_PAYLOAD_HEX_STRING =
            "2800008800020000b4a2faf4bb54878ae21d638512ece55d9236fc50"
                    + "46ab6cef82220f421f3ce6361faf36564ecb6d28798a94aa"
                    + "d7b2b4b603ddeaaa5630adb9ece8ac37534036040610ebdd"
                    + "92f46bef84f0be7db860351843858f8acf87056e272377f7"
                    + "0c9f2d81e29c7b0ce4f291a3a72476bb0b278fd4b7b0a4c2"
                    + "6bbeb08214c7071376079587";
    private static final String INVALID_KE_PAYLOAD_HEX_STRING = "0000000a00000011000e";
    private static final String NONCE_INIT_PAYLOAD_HEX_STRING =
            "29000024c39b7f368f4681b89fa9b7be6465abd7c5f68b6ed5d3b4c72cb4240eb5c46412";
    private static final String NONCE_RESP_PAYLOAD_HEX_STRING =
            "290000249756112ca539f5c25abacc7ee92b73091942a9c06950f98848f1af1694c4ddff";
    private static final String NONCE_INIT_HEX_STRING =
            "c39b7f368f4681b89fa9b7be6465abd7c5f68b6ed5d3b4c72cb4240eb5c46412";
    private static final String NONCE_RESP_HEX_STRING =
            "9756112ca539f5c25abacc7ee92b73091942a9c06950f98848f1af1694c4ddff";
    private static final String NAT_DETECTION_SOURCE_PAYLOAD_HEX_STRING =
            "2900001c00004004e54f73b7d83f6beb881eab2051d8663f421d10b0";
    private static final String NAT_DETECTION_DESTINATION_PAYLOAD_HEX_STRING =
            "2900001c00004005d915368ca036004cb578ae3e3fb268509aeab190";
    private static final String FRAGMENTATION_SUPPORTED_PAYLOAD_HEX_STRING = "290000080000402e";
    private static final String SIGNATURE_HASH_SUPPORTED_PAYLOAD_HEX_STRING =
            "2b0000100000402f0001000200030004";
    private static final String DELETE_IKE_PAYLOAD_HEX_STRING = "0000000801000000";
    private static final String NOTIFY_REKEY_IKE_PAYLOAD_HEX_STRING = "2100000800004009";
    private static final String ID_PAYLOAD_INITIATOR_HEX_STRING =
            "290000180200000031313233343536373839414243444546";
    private static final String ID_PAYLOAD_RESPONDER_HEX_STRING = "2700000c010000007f000001";
    private static final String ID_PAYLOAD_RESPONDER_FQDN_HEX_STRING =
            "2700001702000000696B652E616E64726F69642E6E6574";
    private static final String PSK_AUTH_RESP_PAYLOAD_HEX_STRING =
            "2100001c0200000058f36412e9b7b38df817a9f7779b7a008dacdd25";
    private static final String GENERIC_DIGITAL_SIGN_AUTH_RESP_HEX_STRING =
            "300000580e0000000f300d06092a864886f70d01010b05006f76af4150d653c5d413"
                    + "6b9f69d905849bf075c563e6d14ccda42361ec3e7d12c72e2dece5711ea1d952f7b8e"
                    + "12c5d982aa4efdaeac36a02b222aa96242cc424";
    private static final String CHILD_SA_PAYLOAD_HEX_STRING =
            "2c00002c0000002801030403cae7019f0300000c0100000c800e008003000008030"
                    + "000020000000805000000";
    private static final String TS_INIT_PAYLOAD_HEX_STRING =
            "2d00001801000000070000100000ffff00000000ffffffff";
    private static final String TS_RESP_PAYLOAD_HEX_STRING =
            "2900001801000000070000100000ffff000000000fffffff";
    private static final String VENDOR_ID_PAYLOAD_HEX_STRING =
            "0000001852656d6f74652056656e646f72204944204f6e65";

    private static final String PSK_HEX_STRING = "6A756E69706572313233";

    private static final String PRF_KEY_INIT_HEX_STRING =
            "094787780EE466E2CB049FA327B43908BC57E485";
    private static final String PRF_KEY_RESP_HEX_STRING =
            "A30E6B08BE56C0E6BFF4744143C75219299E1BEB";

    private static final String CP_PAYLOAD_HEX_STRING =
            "210000810200000000080011260010111067a17d000000000a"
                    + "f68e8640000a0010200148880067ff000643000d000000000"
                    + "00a0010200148880066ff000645000d0000000000150010200"
                    + "148880006713a00f10104000000050015001020014888000671"
                    + "3a00f101040000008900150010200148880005713a00e00104000000c9";
    private static final String PCSCF_IPV6_ADDRESS1 = "2001:4888:6:713a:f1:104:0:5";
    private static final String PCSCF_IPV6_ADDRESS2 = "2001:4888:6:713a:f1:104:0:89";
    private static final String PCSCF_IPV6_ADDRESS3 = "2001:4888:5:713a:e0:104:0:c9";

    private static final byte[] EAP_DUMMY_MSG = "EAP Message".getBytes();
    private static final int EAP_RESPONSE_FLAG_MASK_WITH_EAP_AKA_SERVER_AUTHENTICATED =
            (1 << RESPONSE_FLAG_EAP_AKA_SERVER_AUTHENTICATED);
    private static final int EAP_RESPONSE_FLAGS_NOT_SET = 0;
    private static final byte[] REMOTE_VENDOR_ID_ONE = "Remote Vendor ID One".getBytes();
    private static final byte[] REMOTE_VENDOR_ID_TWO = "Remote Vendor ID Two".getBytes();

    private static final IkeIdentification LOCAL_ID_IPV4 =
            new IkeIpv4AddrIdentification((Inet4Address) LOCAL_ADDRESS);
    private static final IkeIdentification REMOTE_ID_FQDN =
            new IkeFqdnIdentification("server.test.android.net");
    private static final IkeIdentification REMOTE_ID_IPV4 =
            new IkeIpv4AddrIdentification((Inet4Address) REMOTE_ADDRESS);

    private static final byte PDU_SESSION_ID = (byte) 0x7B;
    private static final String N1_MODE_CAPABILITY_PAYLOAD_DATA = "017B";
    private static final byte[] SNSSAI = {(byte) 456};

    private static final byte BACKOFF_TIMER = (byte) 0xAF;
    private static final byte[] BACKOFF_TIMER_DATA = {0x01, BACKOFF_TIMER};

    private static final int KEY_LEN_IKE_INTE = 20;
    private static final int KEY_LEN_IKE_ENCR = 16;
    private static final int KEY_LEN_IKE_PRF = 20;
    private static final int KEY_LEN_IKE_SKD = KEY_LEN_IKE_PRF;

    private static final int CHILD_SPI_LOCAL = 0x2ad4c0a2;
    private static final int CHILD_SPI_REMOTE = 0xcae7019f;

    private static final int EAP_SIM_SUB_ID = 1;

    private static final int PAYLOAD_TYPE_UNSUPPORTED = 127;

    private static final int COOKIE_DATA_LEN = 64;
    private static final int COOKIE2_DATA_LEN = 64;

    private static final byte[] COOKIE_DATA = new byte[COOKIE_DATA_LEN];
    private static final byte[] COOKIE2_DATA = new byte[COOKIE2_DATA_LEN];

    private static final int FAKE_SESSION_ID = 0;

    private static final int NATT_KEEPALIVE_DELAY = 20;

    private static final String DEVICE_IDENTITY_IMEI = "123456789123456";
    private static final byte[] DEVICE_IDENTITY_PAYLOAD_IMEI =
            HexDump.hexStringToByteArray("00090121436587193254F6");

    static {
        new Random().nextBytes(COOKIE_DATA);
        new Random().nextBytes(COOKIE2_DATA);
    }

    private static final long RETRANSMIT_BACKOFF_TIMEOUT_MS = 5000L;

    private static final IkeSpiGenerator IKE_SPI_GENERATOR =
            new IkeSpiGenerator(createMockRandomFactory());

    private static final Ike3gppParams IKE_3GPP_PARAMS =
            new Ike3gppParams.Builder().setPduSessionId(PDU_SESSION_ID).build();

    private static final byte[] REAUTH_ID_BYTES =
            "4OLUpQCqFyhm1/UgD56anTzYTqJDckibqjU6PlS4sZaiuLc=".getBytes(StandardCharsets.UTF_8);

    private IkeUdpEncapSocket mMockIkeUdpEncapSocket;
    private IkeUdp6WithEncapPortSocket mMockIkeUdp6WithEncapPortSocket;
    private IkeUdp4Socket mMockIkeUdp4Socket;
    private IkeUdp6Socket mMockIkeUdp6Socket;
    private IkeSocket mMockCurrentIkeSocket;

    private IkeNattKeepalive mMockIkeNattKeepalive;

    private TestLooper mLooper;
    private IkeSessionStateMachine mIkeSessionStateMachine;

    private byte[] mPsk;

    private ChildSessionParams mChildSessionParams;

    private Executor mSpyUserCbExecutor;
    private IkeSessionCallback mMockIkeSessionCallback;
    private ChildSessionCallback mMockChildSessionCallback;

    private EncryptionTransform mIkeEncryptionTransform;
    private IntegrityTransform mIkeIntegrityTransform;
    private PrfTransform mIkePrfTransform;
    private DhGroupTransform mIkeDhGroupTransform;

    private IIkeMessageHelper mMockIkeMessageHelper;
    private ISaRecordHelper mMockSaRecordHelper;

    private ChildSessionStateMachine mMockChildSessionStateMachine;
    private IChildSessionSmCallback mDummyChildSmCallback;

    private IkeSaRecord mSpyCurrentIkeSaRecord;
    private IkeSaRecord mSpyLocalInitIkeSaRecord;
    private IkeSaRecord mSpyRemoteInitIkeSaRecord;

    private Log mSpyIkeLog;

    private int mExpectedCurrentSaLocalReqMsgId;
    private int mExpectedCurrentSaRemoteReqMsgId;

    private IkeSessionStateMachine.Dependencies mSpyDeps;

    private EapSessionConfig mEapSessionConfig;
    private EapSessionConfig mEapSessionConfigEapAka;
    private EapAuthenticator mMockEapAuthenticator;

    private IkeConnectionController mSpyIkeConnectionCtrl;

    private Ike3gppDataListener mMockIke3gppDataListener;
    private Ike3gppExtension mIke3gppExtension;

    private X509Certificate mRootCertificate;
    private X509Certificate mServerEndCertificate;
    private PrivateKey mUserPrivateKey;
    private X509Certificate mUserEndCert;

    private LocalRequestFactory mLocalRequestFactory;

    private ArgumentCaptor<IkeMessage> mIkeMessageCaptor =
            ArgumentCaptor.forClass(IkeMessage.class);
    private ArgumentCaptor<IkeSaRecordConfig> mIkeSaRecordConfigCaptor =
            ArgumentCaptor.forClass(IkeSaRecordConfig.class);
    private ArgumentCaptor<IChildSessionSmCallback> mChildSessionSmCbCaptor =
            ArgumentCaptor.forClass(IChildSessionSmCallback.class);
    private ArgumentCaptor<List<IkePayload>> mPayloadListCaptor =
            ArgumentCaptor.forClass(List.class);

    private ReceivedIkePacket makeDummyReceivedIkeInitRespPacket(List<IkePayload> payloadList)
            throws Exception {
        long dummyInitSpi = 1L;
        long dummyRespSpi = 2L;

        return makeDummyUnencryptedReceivedIkePacket(
                dummyInitSpi,
                dummyRespSpi,
                IkeHeader.EXCHANGE_TYPE_IKE_SA_INIT,
                true /*isResp*/,
                false /*fromIkeInit*/,
                payloadList);
    }

    private ReceivedIkePacket makeDummyReceivedIkeInitRespPacket(
            List<Integer> payloadTypeList, List<String> payloadHexStringList) throws Exception {

        List<IkePayload> payloadList =
                hexStrListToIkePayloadList(
                        payloadTypeList, payloadHexStringList, true /* isResp */);
        return makeDummyReceivedIkeInitRespPacket(payloadList);
    }

    private ReceivedIkePacket makeDummyUnencryptedReceivedIkePacket(
            long initiatorSpi,
            long responderSpi,
            @IkeHeader.ExchangeType int eType,
            boolean isResp,
            boolean fromIkeInit,
            List<IkePayload> payloadList)
            throws Exception {
        IkeMessage dummyIkeMessage =
                makeDummyIkeMessageForTest(
                        initiatorSpi,
                        responderSpi,
                        eType,
                        isResp,
                        fromIkeInit,
                        0,
                        false /*isEncrypted*/,
                        payloadList);

        byte[] dummyIkePacketBytes = new byte[0];
        doReturn(new DecodeResultOk(dummyIkeMessage, dummyIkePacketBytes))
                .when(mMockIkeMessageHelper)
                .decode(0, dummyIkeMessage.ikeHeader, dummyIkePacketBytes);

        return new ReceivedIkePacket(dummyIkeMessage.ikeHeader, dummyIkePacketBytes);
    }

    private ReceivedIkePacket makeDummyEncryptedReceivedIkePacket(
            IkeSaRecord ikeSaRecord,
            @IkeHeader.ExchangeType int eType,
            boolean isResp,
            List<Integer> payloadTypeList,
            List<String> payloadHexStringList)
            throws Exception {
        List<IkePayload> payloadList =
                hexStrListToIkePayloadList(payloadTypeList, payloadHexStringList, isResp);
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                ikeSaRecord, eType, isResp, payloadList);
    }

    private ReceivedIkePacket makeDummyEncryptedReceivedIkePacketWithPayloadList(
            IkeSaRecord ikeSaRecord,
            @IkeHeader.ExchangeType int eType,
            boolean isResp,
            List<IkePayload> payloadList)
            throws Exception {
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                ikeSaRecord,
                eType,
                isResp,
                isResp
                        ? ikeSaRecord.getLocalRequestMessageId()
                        : ikeSaRecord.getRemoteRequestMessageId(),
                payloadList,
                new byte[0] /*dummyIkePacketBytes*/);
    }

    private ReceivedIkePacket makeDummyEncryptedReceivedIkePacketWithPayloadList(
            IkeSaRecord ikeSaRecord,
            @IkeHeader.ExchangeType int eType,
            boolean isResp,
            int msgId,
            List<IkePayload> payloadList,
            byte[] dummyIkePacketBytes)
            throws Exception {
        boolean fromIkeInit = !ikeSaRecord.isLocalInit;
        IkeMessage dummyIkeMessage =
                makeDummyIkeMessageForTest(
                        ikeSaRecord.getInitiatorSpi(),
                        ikeSaRecord.getResponderSpi(),
                        eType,
                        isResp,
                        fromIkeInit,
                        msgId,
                        true /*isEncyprted*/,
                        payloadList);

        setDecodeEncryptedPacketResult(
                ikeSaRecord,
                dummyIkeMessage.ikeHeader,
                null /*collectedFrags*/,
                new DecodeResultOk(dummyIkeMessage, dummyIkePacketBytes));

        return new ReceivedIkePacket(dummyIkeMessage.ikeHeader, dummyIkePacketBytes);
    }

    private ReceivedIkePacket makeDummyReceivedIkePacketWithInvalidSyntax(
            IkeSaRecord ikeSaRecord, boolean isResp, int eType) {
        return makeDummyReceivedIkePacketWithDecodingError(
                ikeSaRecord, isResp, eType, new InvalidSyntaxException("IkeStateMachineTest"));
    }

    private ReceivedIkePacket makeDummyReceivedIkePacketWithDecodingError(
            IkeSaRecord ikeSaRecord, boolean isResp, int eType, IkeProtocolException exception) {
        IkeHeader header =
                makeDummyIkeHeader(ikeSaRecord, isResp, eType, IkePayload.PAYLOAD_TYPE_SK);
        byte[] dummyPacket = new byte[0];
        doReturn(new DecodeResultProtectedError(exception, dummyPacket))
                .when(mMockIkeMessageHelper)
                .decode(anyInt(), any(), any(), eq(ikeSaRecord), eq(header), any(), any());

        return new ReceivedIkePacket(header, dummyPacket);
    }

    private ReceivedIkePacket makeDummyReceivedIkePacketWithUnprotectedError(
            IkeSaRecord ikeSaRecord, boolean isResp, int eType, IkeException exception) {
        IkeHeader header =
                makeDummyIkeHeader(ikeSaRecord, isResp, eType, IkePayload.PAYLOAD_TYPE_SK);
        byte[] dummyPacket = new byte[0];
        doReturn(new DecodeResultUnprotectedError(exception))
                .when(mMockIkeMessageHelper)
                .decode(anyInt(), any(), any(), eq(ikeSaRecord), eq(header), any(), any());

        return new ReceivedIkePacket(header, dummyPacket);
    }

    private ReceivedIkePacket makeDummyReceivedIkeFragmentPacket(
            IkeSaRecord ikeSaRecord,
            boolean isResp,
            int eType,
            IkeSkfPayload skfPayload,
            int nextPayloadType,
            DecodeResultPartial collectedFrags) {
        IkeHeader header =
                makeDummyIkeHeader(ikeSaRecord, isResp, eType, IkePayload.PAYLOAD_TYPE_SKF);

        byte[] dummyPacket = new byte[0];
        DecodeResultPartial resultFrags =
                new DecodeResultPartial(
                        header, dummyPacket, skfPayload, nextPayloadType, collectedFrags);
        setDecodeEncryptedPacketResult(ikeSaRecord, header, collectedFrags, resultFrags);

        return new ReceivedIkePacket(header, dummyPacket);
    }

    private ReceivedIkePacket makeDummyReceivedLastIkeFragmentPacketOk(
            IkeSaRecord ikeSaRecord,
            boolean isResp,
            int eType,
            DecodeResultPartial collectedFrags,
            List<IkePayload> payloadList,
            byte[] firstFragBytes) {
        IkeHeader header =
                makeDummyIkeHeader(ikeSaRecord, isResp, eType, IkePayload.PAYLOAD_TYPE_SKF);

        IkeMessage completeMessage = new IkeMessage(header, payloadList);

        setDecodeEncryptedPacketResult(
                ikeSaRecord,
                header,
                collectedFrags,
                new DecodeResultOk(completeMessage, firstFragBytes));

        return new ReceivedIkePacket(header, new byte[0] /*dummyIkePacketBytes*/);
    }

    private ReceivedIkePacket makeDummyReceivedLastIkeFragmentPacketError(
            IkeSaRecord ikeSaRecord,
            boolean isResp,
            int eType,
            DecodeResultPartial collectedFrags,
            IkeException exception) {
        IkeHeader header =
                makeDummyIkeHeader(ikeSaRecord, isResp, eType, IkePayload.PAYLOAD_TYPE_SKF);

        byte[] dummyIkePacketBytes = new byte[0];
        setDecodeEncryptedPacketResult(
                ikeSaRecord,
                header,
                collectedFrags,
                new DecodeResultProtectedError(exception, dummyIkePacketBytes));

        return new ReceivedIkePacket(header, dummyIkePacketBytes);
    }

    private IkeHeader makeDummyIkeHeader(
            IkeSaRecord ikeSaRecord, boolean isResp, int eType, int firstPayloadType) {
        return new IkeHeader(
                ikeSaRecord.getInitiatorSpi(),
                ikeSaRecord.getResponderSpi(),
                firstPayloadType,
                eType,
                isResp,
                !ikeSaRecord.isLocalInit,
                isResp
                        ? ikeSaRecord.getLocalRequestMessageId()
                        : ikeSaRecord.getRemoteRequestMessageId());
    }

    private void setDecodeEncryptedPacketResult(
            IkeSaRecord ikeSaRecord,
            IkeHeader header,
            DecodeResultPartial collectedFrags,
            DecodeResult result) {
        doReturn(result)
                .when(mMockIkeMessageHelper)
                .decode(
                        anyInt(),
                        any(),
                        any(),
                        eq(ikeSaRecord),
                        eq(header),
                        any(),
                        eq(collectedFrags));
    }

    private IkeMessage makeDummyIkeMessageForTest(
            long initSpi,
            long respSpi,
            @IkeHeader.ExchangeType int eType,
            boolean isResp,
            boolean fromikeInit,
            int messageId,
            boolean isEncrypted,
            List<IkePayload> payloadList)
            throws Exception {
        int firstPayloadType =
                isEncrypted ? IkePayload.PAYLOAD_TYPE_SK : IkePayload.PAYLOAD_TYPE_NO_NEXT;

        IkeHeader header =
                new IkeHeader(
                        initSpi, respSpi, firstPayloadType, eType, isResp, fromikeInit, messageId);

        return new IkeMessage(header, payloadList);
    }

    private static List<IkePayload> hexStrListToIkePayloadList(
            List<Integer> payloadTypeList, List<String> payloadHexStringList, boolean isResp)
            throws Exception {
        List<IkePayload> payloadList = new ArrayList<>();
        for (int i = 0; i < payloadTypeList.size(); i++) {
            payloadList.add(
                    IkeTestUtils.hexStringToIkePayload(
                            payloadTypeList.get(i), isResp, payloadHexStringList.get(i)));
        }
        return payloadList;
    }

    private void verifyDecodeEncryptedMessage(IkeSaRecord record, ReceivedIkePacket rcvPacket)
            throws Exception {
        verify(mMockIkeMessageHelper)
                .decode(
                        anyInt(),
                        any(),
                        any(),
                        eq(record),
                        eq(rcvPacket.ikeHeader),
                        eq(rcvPacket.ikePacketBytes),
                        eq(null));
    }

    private static IkeSaRecord makeDummyIkeSaRecord(long initSpi, long respSpi, boolean isLocalInit)
            throws IOException {
        Inet4Address initAddress = isLocalInit ? LOCAL_ADDRESS : REMOTE_ADDRESS;
        Inet4Address respAddress = isLocalInit ? REMOTE_ADDRESS : LOCAL_ADDRESS;

        return new IkeSaRecord(
                IKE_SPI_GENERATOR.allocateSpi(initAddress, initSpi),
                IKE_SPI_GENERATOR.allocateSpi(respAddress, respSpi),
                isLocalInit,
                TestUtils.hexStringToByteArray(NONCE_INIT_HEX_STRING),
                TestUtils.hexStringToByteArray(NONCE_RESP_HEX_STRING),
                new byte[KEY_LEN_IKE_SKD],
                new byte[KEY_LEN_IKE_INTE],
                new byte[KEY_LEN_IKE_INTE],
                new byte[KEY_LEN_IKE_ENCR],
                new byte[KEY_LEN_IKE_ENCR],
                TestUtils.hexStringToByteArray(PRF_KEY_INIT_HEX_STRING),
                TestUtils.hexStringToByteArray(PRF_KEY_RESP_HEX_STRING),
                mock(SaLifetimeAlarmScheduler.class));
    }

    private void mockScheduleRekey(SaLifetimeAlarmScheduler mockSaLifetimeAlarmScheduler) {
        IkeLocalRequest rekeyReq =
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE);
        doAnswer(
                (invocation) -> {
                        mIkeSessionStateMachine.sendMessageDelayed(
                                IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE,
                                rekeyReq,
                                mIkeSessionStateMachine.mIkeSessionParams
                                        .getSoftLifetimeMsInternal());
                        return null;
                })
                .when(mockSaLifetimeAlarmScheduler)
                .scheduleLifetimeExpiryAlarm(anyString());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mSpyIkeLog = TestUtils.makeSpyLogThrowExceptionForWtf(TAG);
        IkeManager.setIkeLog(mSpyIkeLog);

        mEapSessionConfig =
                new EapSessionConfig.Builder()
                        .setEapSimConfig(EAP_SIM_SUB_ID, TelephonyManager.APPTYPE_USIM)
                        .build();
        setupLocalAddressForNetwork(mMockDefaultNetwork, LOCAL_ADDRESS, LOCAL_ADDRESS_V6);

        mMockEapAuthenticator = mock(EapAuthenticator.class);
        mMockChildSessionStateMachine = mock(ChildSessionStateMachine.class);

        mRootCertificate = CertUtils.createCertFromPemFile("self-signed-ca-a.pem");
        mServerEndCertificate = CertUtils.createCertFromPemFile("end-cert-a.pem");
        mUserEndCert = CertUtils.createCertFromPemFile("end-cert-b.pem");
        mUserPrivateKey = CertUtils.createRsaPrivateKeyFromKeyFile("end-cert-key-b.key");

        mPsk = TestUtils.hexStringToByteArray(PSK_HEX_STRING);

        mChildSessionParams = buildChildSessionParams();

        mIkeEncryptionTransform =
                new EncryptionTransform(
                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128);
        mIkeIntegrityTransform =
                new IntegrityTransform(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96);
        mIkePrfTransform = new PrfTransform(SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1);
        mIkeDhGroupTransform = new DhGroupTransform(SaProposal.DH_GROUP_1024_BIT_MODP);

        mSpyUserCbExecutor =
                spy(
                        (command) -> {
                            command.run();
                        });

        mMockIkeSessionCallback = mock(IkeSessionCallback.class);
        mMockChildSessionCallback = mock(ChildSessionCallback.class);
        mLocalRequestFactory = new LocalRequestFactory();

        mLooper = new TestLooper();

        // Setup state machine
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsPsk(mPsk));

        mMockIkeMessageHelper = mock(IkeMessage.IIkeMessageHelper.class);
        IkeMessage.setIkeMessageHelper(mMockIkeMessageHelper);
        resetMockIkeMessageHelper();

        mMockSaRecordHelper = mock(SaRecord.ISaRecordHelper.class);
        SaRecord.setSaRecordHelper(mMockSaRecordHelper);

        mSpyCurrentIkeSaRecord = spy(makeDummyIkeSaRecord(11, 12, true));
        mSpyLocalInitIkeSaRecord = spy(makeDummyIkeSaRecord(21, 22, true));
        mSpyRemoteInitIkeSaRecord = spy(makeDummyIkeSaRecord(31, 32, false));

        mExpectedCurrentSaLocalReqMsgId = 0;
        mExpectedCurrentSaRemoteReqMsgId = 0;

        mMockIke3gppDataListener = mock(Ike3gppDataListener.class);
    }

    @After
    public void tearDown() throws Exception {
        mIkeSessionStateMachine.killSession();
        mLooper.dispatchAll();
        mIkeSessionStateMachine.setDbg(false);

        mSpyCurrentIkeSaRecord.close();
        mSpyLocalInitIkeSaRecord.close();
        mSpyRemoteInitIkeSaRecord.close();

        IkeManager.resetIkeLog();
        IkeMessage.setIkeMessageHelper(new IkeMessageHelper());
        SaRecord.setSaRecordHelper(new SaRecordHelper());
    }

    private void injectChildSessionInSpyDeps(
            IkeSessionStateMachine.Dependencies spyDeps,
            ChildSessionStateMachine child,
            ChildSessionCallback childCb) {
        doReturn(child)
                .when(spyDeps)
                .newChildSessionStateMachine(
                        any(IkeContext.class),
                        any(ChildSessionStateMachine.Config.class),
                        eq(childCb),
                        any(IChildSessionSmCallback.class));
    }

    private IkeSessionStateMachine.Dependencies buildSpyDepsWithChildSession(
            ChildSessionStateMachine child, ChildSessionCallback childCb) {
        IkeSessionStateMachine.Dependencies spyDeps =
                spy(new IkeSessionStateMachine.Dependencies());

        doReturn(mMockEapAuthenticator)
                .when(spyDeps)
                .newEapAuthenticator(
                        any(IkeContext.class),
                        any(IEapCallback.class),
                        any(EapSessionConfig.class));
        doReturn(mSpyIkeConnectionCtrl)
                .when(spyDeps)
                .newIkeConnectionController(
                        any(IkeContext.class), any(IkeConnectionController.Config.class));
        injectChildSessionInSpyDeps(spyDeps, child, childCb);


        return spyDeps;
    }

    private IkeSessionStateMachine makeAndStartIkeSession(IkeSessionParams ikeParams)
            throws Exception {
        return makeAndStartIkeSession(ikeParams, LOCAL_ADDRESS, REMOTE_ADDRESS);
    }

    private void setupIkeConnectionCtrlCbWithIkeSession(
            IkeConnectionController.Callback mockCallback, IkeSessionStateMachine ikeSession) {
        doAnswer(
                (invocation) -> {
                        ikeSession.onUnderlyingNetworkUpdated();
                        return null;
                })
                .when(mockCallback)
                .onUnderlyingNetworkUpdated();

        doAnswer(
                (invocation) -> {
                        ikeSession.onUnderlyingNetworkDied(invocation.getArgument(0));
                        return null;
                })
                .when(mockCallback)
                .onUnderlyingNetworkDied(any());

        doAnswer(
                (invocation) -> {
                        ikeSession.onError(invocation.getArgument(0));
                        return null;
                })
                .when(mockCallback)
                .onError(any());
    }

    private IkeSessionStateMachine makeAndStartIkeSession(
            IkeSessionParams ikeParams,
            InetAddress localAddress,
            InetAddress expectedRemoteAddress)
            throws Exception {
        IkeContext ikeContext =
                new IkeSessionStateMachine.Dependencies()
                        .newIkeContext(
                                mLooper.getLooper(), mSpyContext, ikeParams.getConfiguredNetwork());
        IkeConnectionController.Callback mockIkeConnectionCtrlCb =
                mock(IkeConnectionController.Callback.class);
        IkeConnectionController.Dependencies spyIkeConnectionCtrlDeps =
                spy(new IkeConnectionController.Dependencies());
        doReturn(mMockIkeLocalAddressGenerator)
                .when(spyIkeConnectionCtrlDeps)
                .newIkeLocalAddressGenerator();
        mMockIkeNattKeepalive = mock(IkeNattKeepalive.class);
        doAnswer(
                (invocation) -> {
                        mMockIkeNattKeepalive.start();
                        return mMockIkeNattKeepalive;
                })
                .when(spyIkeConnectionCtrlDeps)
                .newIkeNattKeepalive(any(), any());

        // Setup socket instances used by the IkeSessionStateMachine
        mMockIkeUdp4Socket = newMockIkeSocket(IkeUdp4Socket.class);
        mMockIkeUdp6Socket = newMockIkeSocket(IkeUdp6Socket.class);
        mMockIkeUdpEncapSocket = newMockIkeSocket(IkeUdpEncapSocket.class);
        mMockIkeUdp6WithEncapPortSocket = newMockIkeSocket(IkeUdp6WithEncapPortSocket.class);

        doReturn(mMockIkeUdp4Socket)
                .when(spyIkeConnectionCtrlDeps)
                .newIkeUdp4Socket(any(), any(), any());
        doReturn(mMockIkeUdp6Socket)
                .when(spyIkeConnectionCtrlDeps)
                .newIkeUdp6Socket(any(), any(), any());
        doReturn(mMockIkeUdpEncapSocket)
                .when(spyIkeConnectionCtrlDeps)
                .newIkeUdpEncapSocket(any(), any(), any(), any());
        doReturn(mMockIkeUdp6WithEncapPortSocket)
                .when(spyIkeConnectionCtrlDeps)
                .newIkeUdp6WithEncapPortSocket(any(), any(), any());

        // Can't use a mock object because delayMs must be nonzero. Otherwise, setNetwork will
        // spuriously restart keepalives, thinking that the current delay is zero.
        final IkeAlarmConfig alarmConfig = spy(new IkeAlarmConfig(mSpyContext,
                "mock", NATT_KEEPALIVE_DELAY * 1_000, null, null));
        mSpyIkeConnectionCtrl =
                spy(
                        new IkeConnectionController(
                                ikeContext,
                                new IkeConnectionController.Config(
                                        ikeParams,
                                        FAKE_SESSION_ID,
                                        CMD_ALARM_FIRED,
                                        CMD_SEND_KEEPALIVE,
                                        mockIkeConnectionCtrlCb),
                                spyIkeConnectionCtrlDeps));
        mSpyDeps =
                buildSpyDepsWithChildSession(
                        mMockChildSessionStateMachine, mMockChildSessionCallback);

        IkeSessionStateMachine ikeSession =
                new IkeSessionStateMachine(
                        mLooper.getLooper(),
                        mSpyContext,
                        mIpSecManager,
                        mMockConnectManager,
                        ikeParams,
                        mChildSessionParams,
                        mSpyUserCbExecutor,
                        mMockIkeSessionCallback,
                        mMockChildSessionCallback,
                        mSpyDeps);
        setupIkeConnectionCtrlCbWithIkeSession(mockIkeConnectionCtrlCb, ikeSession);

        ikeSession.setDbg(true);

        mLooper.dispatchAll();
        mMockCurrentIkeSocket = mSpyIkeConnectionCtrl.getIkeSocket();
        assertEquals(expectedRemoteAddress, mSpyIkeConnectionCtrl.getRemoteAddress());

        if (ikeParams.getConfiguredNetwork() == null) {
            verify(mMockConnectManager, atLeast(1)).getActiveNetwork();
        } else {
            verify(mMockConnectManager, never()).getActiveNetwork();
        }
        return ikeSession;
    }

    public static IkeSaProposal buildSaProposal() throws Exception {
        return buildSaProposalCommon().addDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP).build();
    }

    private static IkeSaProposal buildNegotiatedSaProposal() throws Exception {
        return buildSaProposalCommon().build();
    }

    private static IkeSaProposal.Builder buildSaProposalCommon() throws Exception {
        return new IkeSaProposal.Builder()
                .addEncryptionAlgorithm(
                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128)
                .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
                .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1)
                .addDhGroup(SaProposal.DH_GROUP_1024_BIT_MODP);
    }

    private IkeSessionParams.Builder buildIkeSessionParamsCommon() throws Exception {
        return new IkeSessionParams.Builder(mMockConnectManager)
                .setServerHostname(REMOTE_HOSTNAME)
                .addSaProposal(buildSaProposal())
                .setLocalIdentification(LOCAL_ID_IPV4)
                .setRemoteIdentification(REMOTE_ID_FQDN)
                .setNattKeepAliveDelaySeconds(NATT_KEEPALIVE_DELAY)
                .addPcscfServerRequest(AF_INET)
                .addPcscfServerRequest(AF_INET6)
                .setRetransmissionTimeoutsMillis(
                        new int[] {5000, 10000, 20000, 30000, 40000, 50000})
                .setLivenessRetransmissionTimeoutsMillis(
                        new int[] {1000, 1000, 1000, 1000, 5000, 5000, 10000});
    }

    private IkeSessionParams buildIkeSessionParams() throws Exception {
        return buildIkeSessionParamsCommon().setAuthPsk(mPsk).build();
    }

    private IkeSessionParams buildIkeSessionParamsPsk(byte[] psk) throws Exception {
        return buildIkeSessionParamsCommon().setAuthPsk(psk).build();
    }

    private IkeSessionParams buildIkeSessionParamsEap() throws Exception {
        return buildIkeSessionParamsCommon()
                .setAuthEap(mRootCertificate, mEapSessionConfig)
                .build();
    }

    private IkeSessionParams buildIkeSessionParamsEapAkaWithDeviceIdentity() throws Exception {
        Ike3gppParams ike3gppParams =
                new Ike3gppParams.Builder().setMobileDeviceIdentity(DEVICE_IDENTITY_IMEI).build();
        Ike3gppExtension ike3gppExtension =
                new Ike3gppExtension(ike3gppParams, mock(Ike3gppDataListener.class));
        mEapSessionConfigEapAka =
                new EapSessionConfig.Builder()
                        .setEapAkaConfig(0, TelephonyManager.APPTYPE_ISIM)
                        .build();

        return buildIkeSessionParamsCommon()
                .setAuthEap(mock(X509Certificate.class), mEapSessionConfigEapAka)
                .setIke3gppExtension(ike3gppExtension)
                .addIkeOption(IKE_OPTION_EAP_ONLY_AUTH)
                .build();
    }

    private IkeSessionParams buildIkeSessionParamsDigitalSignature() throws Exception {
        return buildIkeSessionParamsCommon()
                .setAuthDigitalSignature(mRootCertificate, mUserEndCert, mUserPrivateKey)
                .build();
    }

    private IkeSessionParams buildIkeSessionParamsIke3gppExtension(byte pduSessionId)
            throws Exception {
        Ike3gppExtension ike3gppExtension =
                new Ike3gppExtension(
                        new Ike3gppParams.Builder().setPduSessionId(pduSessionId).build(),
                        mMockIke3gppDataListener);
        return buildIkeSessionParamsCommon()
                .setAuthPsk(mPsk)
                .setIke3gppExtension(ike3gppExtension)
                .build();
    }

    private IkeSessionParams buildIkeSessionParamsWithIkeOptions(int... ikeOptions)
            throws Exception {
        IkeSessionParams.Builder builder = buildIkeSessionParamsCommon().setAuthPsk(mPsk);
        for (int option : ikeOptions) {
            builder.addIkeOption(option);
        }

        return builder.build();
    }

    private ChildSessionParams buildChildSessionParams() throws Exception {
        ChildSaProposal saProposal =
                new ChildSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128)
                        .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
                        .build();

        return new TunnelModeChildSessionParams.Builder()
                .addSaProposal(saProposal)
                .addInternalAddressRequest(AF_INET)
                .addInternalAddressRequest(AF_INET6)
                .build();
    }

    // Common IKE INIT response
    private ReceivedIkePacket makeIkeInitResponse() throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NOTIFY);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NOTIFY);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NOTIFY);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NOTIFY);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_VENDOR);

        payloadHexStringList.add(NAT_DETECTION_SOURCE_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(NAT_DETECTION_DESTINATION_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(FRAGMENTATION_SUPPORTED_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(SIGNATURE_HASH_SUPPORTED_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(VENDOR_ID_PAYLOAD_HEX_STRING);

        return makeIkeInitResponseWithRequiredPayloads(payloadTypeList, payloadHexStringList);
    }

    // Simplest IKE INIT response that does not include any optional payloads
    private ReceivedIkePacket makeIkeInitResponseWithRequiredPayloads(
            List<Integer> optionalPayloadTypes, List<String> optionalPayloadHexStrings)
            throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_SA);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_KE);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NONCE);
        payloadTypeList.addAll(optionalPayloadTypes);

        payloadHexStringList.add(IKE_SA_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(KE_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(NONCE_RESP_PAYLOAD_HEX_STRING);
        payloadHexStringList.addAll(optionalPayloadHexStrings);

        return makeDummyReceivedIkeInitRespPacket(
                payloadTypeList,
                payloadHexStringList);
    }

    private List<IkePayload> getIkeAuthPayloadListWithChildPayloads(
            List<IkePayload> authRelatedPayloads) throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_SA);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_TS_INITIATOR);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_TS_RESPONDER);

        payloadHexStringList.add(CHILD_SA_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(TS_INIT_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(TS_RESP_PAYLOAD_HEX_STRING);

        List<IkePayload> payloadList =
                hexStrListToIkePayloadList(payloadTypeList, payloadHexStringList, true /*isResp*/);
        payloadList.addAll(authRelatedPayloads);

        return payloadList;
    }

    private ReceivedIkePacket makeIkeAuthRespWithChildPayloads(List<IkePayload> authRelatedPayloads)
            throws Exception {
        List<IkePayload> payloadList = getIkeAuthPayloadListWithChildPayloads(authRelatedPayloads);

        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_IKE_AUTH,
                true /*isResp*/,
                payloadList);
    }

    private ReceivedIkePacket makeIkeAuthRespWithoutChildPayloads(
            List<IkePayload> authRelatedPayloads) throws Exception {
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_IKE_AUTH,
                true /*isResp*/,
                authRelatedPayloads);
    }

    private ReceivedIkePacket makeCreateChildCreateMessage(boolean isResp) throws Exception {
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                isResp,
                makeCreateChildPayloadList(isResp));
    }

    private ReceivedIkePacket makeRekeyChildCreateMessage(boolean isResp, int spi)
            throws Exception {
        IkeNotifyPayload rekeyPayload =
                new IkeNotifyPayload(
                        IkePayload.PROTOCOL_ID_ESP,
                        spi,
                        IkeNotifyPayload.NOTIFY_TYPE_REKEY_SA,
                        new byte[0]);

        List<IkePayload> payloadList = makeCreateChildPayloadList(isResp);
        payloadList.add(rekeyPayload);

        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                isResp,
                payloadList);
    }

    private List<IkePayload> makeCreateChildPayloadList(boolean isResp) throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_SA);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NONCE);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_TS_INITIATOR);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_TS_RESPONDER);

        payloadHexStringList.add(CHILD_SA_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(NONCE_RESP_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(TS_INIT_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(TS_RESP_PAYLOAD_HEX_STRING);

        return hexStrListToIkePayloadList(payloadTypeList, payloadHexStringList, isResp);
    }

    private ReceivedIkePacket makeDeleteChildPacket(IkeDeletePayload[] payloads, boolean isResp)
            throws Exception {
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                isResp,
                Arrays.asList(payloads));
    }

    private ReceivedIkePacket makeRekeyIkeResponse() throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_SA);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_KE);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NONCE);

        payloadHexStringList.add(IKE_REKEY_SA_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(KE_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(NONCE_RESP_PAYLOAD_HEX_STRING);

        return makeDummyEncryptedReceivedIkePacket(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                true /*isResp*/,
                payloadTypeList,
                payloadHexStringList);
    }

    private ReceivedIkePacket makeDeleteIkeResponse(IkeSaRecord ikeSaRecord) throws Exception {
        return makeDummyEncryptedReceivedIkePacket(
                ikeSaRecord,
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                true /*isResp*/,
                new ArrayList<>(),
                new ArrayList<>());
    }

    private ReceivedIkePacket makeDpdIkeRequest(IkeSaRecord saRecord) throws Exception {
        return makeDummyEncryptedReceivedIkePacket(
                saRecord,
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                false /*isResp*/,
                new ArrayList<>(),
                new ArrayList<>());
    }

    private ReceivedIkePacket makeDpdIkeRequest(int msgId, byte[] dummyIkePacketBytes)
            throws Exception {
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                EXCHANGE_TYPE_INFORMATIONAL,
                false /*isResp*/,
                msgId,
                new ArrayList<>(),
                dummyIkePacketBytes);
    }

    private ReceivedIkePacket makeDeviceIdentityIkeRequest() throws Exception {
        IkeNotifyPayload deviceIdentity =
                new IkeNotifyPayload(
                        NOTIFY_TYPE_DEVICE_IDENTITY, HexDump.hexStringToByteArray("01"));
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                EXCHANGE_TYPE_INFORMATIONAL,
                false /*isResp*/,
                Arrays.asList(deviceIdentity));
    }

    private ReceivedIkePacket makeRoutabilityCheckIkeRequest() throws Exception {
        IkeNotifyPayload cookie2Notify = new IkeNotifyPayload(NOTIFY_TYPE_COOKIE2, COOKIE2_DATA);
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                EXCHANGE_TYPE_INFORMATIONAL,
                false /*isResp*/,
                Arrays.asList(cookie2Notify));
    }

    private ReceivedIkePacket makeRekeyIkeRequest() throws Exception {
        IkeSaPayload saPayload =
                (IkeSaPayload)
                        IkeTestUtils.hexStringToIkePayload(
                                IkePayload.PAYLOAD_TYPE_SA,
                                false /*isResp*/,
                                IKE_REKEY_SA_PAYLOAD_HEX_STRING);
        return makeRekeyIkeRequest(saPayload);
    }

    private ReceivedIkePacket makeRekeyIkeRequestWithUnacceptableProposal() throws Exception {
        IkeSaPayload saPayload =
                (IkeSaPayload)
                        IkeTestUtils.hexStringToIkePayload(
                                IkePayload.PAYLOAD_TYPE_SA,
                                false /*isResp*/,
                                IKE_REKEY_UNACCEPTABLE_SA_PAYLOAD_HEX_STRING);
        return makeRekeyIkeRequest(saPayload);
    }

    private ReceivedIkePacket makeRekeyIkeRequestWithPayloads(List<IkePayload> payloads)
            throws Exception {
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord,
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                false /*isResp*/,
                payloads);
    }

    private ReceivedIkePacket makeRekeyIkeRequest(IkeSaPayload saPayload) throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_KE);
        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_NONCE);

        payloadHexStringList.add(KE_PAYLOAD_HEX_STRING);
        payloadHexStringList.add(NONCE_INIT_PAYLOAD_HEX_STRING);

        List<IkePayload> payloadList =
                hexStrListToIkePayloadList(payloadTypeList, payloadHexStringList, false /*isResp*/);
        payloadList.add(saPayload);

        return makeRekeyIkeRequestWithPayloads(payloadList);
    }

    private ReceivedIkePacket makeDeleteIkeRequest(IkeSaRecord saRecord) throws Exception {
        List<Integer> payloadTypeList = new ArrayList<>();
        List<String> payloadHexStringList = new ArrayList<>();

        payloadTypeList.add(IkePayload.PAYLOAD_TYPE_DELETE);

        payloadHexStringList.add(DELETE_IKE_PAYLOAD_HEX_STRING);

        return makeDummyEncryptedReceivedIkePacket(
                saRecord,
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                false /*isResp*/,
                payloadTypeList,
                payloadHexStringList);
    }

    private ReceivedIkePacket makeResponseWithErrorNotify(IkeNotifyPayload notify)
            throws Exception {
        List<IkePayload> payloads = new ArrayList<>();
        payloads.add(notify);
        return makeDummyEncryptedReceivedIkePacketWithPayloadList(
                mSpyCurrentIkeSaRecord, EXCHANGE_TYPE_INFORMATIONAL, true /*isResp*/, payloads);
    }

    private static boolean isIkePayloadExist(
            List<IkePayload> payloadList, @IkePayload.PayloadType int payloadType) {
        for (IkePayload payload : payloadList) {
            if (payload.payloadType == payloadType) return true;
        }
        return false;
    }

    private static boolean isNotifyExist(
            List<IkePayload> payloadList, @IkeNotifyPayload.NotifyType int notifyType) {
        for (IkeNotifyPayload notify :
                IkePayload.getPayloadListForTypeInProvidedList(
                        PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class, payloadList)) {
            if (notify.notifyType == notifyType) return true;
        }
        return false;
    }

    private static void assertByteArrayListEquals(
            List<byte[]> expectedList, List<byte[]> resultList) {
        assertEquals(expectedList.size(), resultList.size());
        for (int i = 0; i < expectedList.size(); i++) {
            assertArrayEquals(expectedList.get(i), resultList.get(i));
        }
    }

    private void verifyIncrementLocaReqMsgId() {
        assertEquals(
                ++mExpectedCurrentSaLocalReqMsgId,
                mSpyCurrentIkeSaRecord.getLocalRequestMessageId());
    }

    private void verifyIncrementRemoteReqMsgId() {
        assertEquals(
                ++mExpectedCurrentSaRemoteReqMsgId,
                mSpyCurrentIkeSaRecord.getRemoteRequestMessageId());
    }

    private void verifyRetransmissionStarted() {
        assertTrue(
                mIkeSessionStateMachine
                        .getHandler()
                        .hasMessages(IkeSessionStateMachine.CMD_RETRANSMIT));
    }

    private void verifyRetransmissionStopped() {
        assertFalse(
                mIkeSessionStateMachine
                        .getHandler()
                        .hasMessages(IkeSessionStateMachine.CMD_RETRANSMIT));
    }

    private IkeMessage verifyEncryptAndEncodeAndGetMessage(IkeSaRecord ikeSaRecord) {
        verify(mMockIkeMessageHelper)
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        eq(ikeSaRecord),
                        mIkeMessageCaptor.capture(),
                        anyBoolean(),
                        anyInt());
        return mIkeMessageCaptor.getValue();
    }

    private void verifyEncryptAndEncodeNeverCalled(IkeSaRecord ikeSaRecord) {
        verify(mMockIkeMessageHelper, never())
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        eq(ikeSaRecord),
                        any(IkeMessage.class),
                        anyBoolean(),
                        anyInt());
    }

    private void verifyEncryptAndEncodeNeverCalled() {
        verify(mMockIkeMessageHelper, never())
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        any(IkeSaRecord.class),
                        any(IkeMessage.class),
                        anyBoolean(),
                        anyInt());
    }

    private void resetMockIkeMessageHelper() {
        reset(mMockIkeMessageHelper);
        doReturn(new byte[0]).when(mMockIkeMessageHelper).encode(any());
        doReturn(new byte[1][0])
                .when(mMockIkeMessageHelper)
                .encryptAndEncode(any(), any(), any(), any(), anyBoolean(), anyInt());
    }

    private void resetSpyUserCbExecutor() {
        reset(mSpyUserCbExecutor);
    }

    @Test
    public void testQuit() {
        mIkeSessionStateMachine.quit();
        mLooper.dispatchAll();

        verify(mMockCurrentIkeSocket).releaseReference(eq(mSpyIkeConnectionCtrl));
        verify(mMockBusyWakelock).release();
    }

    @Test
    public void testAllocateIkeSpi() throws Exception {
        // Test randomness.
        IkeSecurityParameterIndex ikeSpiOne = IKE_SPI_GENERATOR.allocateSpi(LOCAL_ADDRESS);
        IkeSecurityParameterIndex ikeSpiTwo = IKE_SPI_GENERATOR.allocateSpi(LOCAL_ADDRESS);

        assertNotEquals(ikeSpiOne.getSpi(), ikeSpiTwo.getSpi());
        ikeSpiTwo.close();

        // Test duplicate SPIs.
        long spiValue = ikeSpiOne.getSpi();
        try {
            IKE_SPI_GENERATOR.allocateSpi(LOCAL_ADDRESS, spiValue);
            fail("Expected to fail because duplicate SPI was assigned to the same address.");
        } catch (IOException expected) {

        }

        ikeSpiOne.close();
        IkeSecurityParameterIndex ikeSpiThree =
                IKE_SPI_GENERATOR.allocateSpi(LOCAL_ADDRESS, spiValue);
        ikeSpiThree.close();
    }

    private void setupFirstIkeSa() throws Exception {
        // Inject IkeSaRecord and release IKE SPI resource since we will lose their references
        // later.
        when(mMockSaRecordHelper.makeFirstIkeSaRecord(any(), any(), any()))
                .thenAnswer(
                        (invocation) -> {
                            captureAndReleaseIkeSpiResource(invocation, 2);
                            mockScheduleRekey(mSpyCurrentIkeSaRecord.mSaLifetimeAlarmScheduler);
                            mSpyCurrentIkeSaRecord.mSaLifetimeAlarmScheduler
                                    .scheduleLifetimeExpiryAlarm(anyString());
                            return mSpyCurrentIkeSaRecord;
                        });
    }

    private void setupRekeyedIkeSa(IkeSaRecord rekeySaRecord) throws Exception {
        // Inject IkeSaRecord and release IKE SPI resource since we will lose their references
        // later.
        when(mMockSaRecordHelper.makeRekeyedIkeSaRecord(
                        eq(mSpyCurrentIkeSaRecord), any(), any(), any(), any()))
                .thenAnswer(
                        (invocation) -> {
                            captureAndReleaseIkeSpiResource(invocation, 4);
                            mockScheduleRekey(rekeySaRecord.mSaLifetimeAlarmScheduler);
                            rekeySaRecord.mSaLifetimeAlarmScheduler.scheduleLifetimeExpiryAlarm(
                                    anyString());
                            return rekeySaRecord;
                        });
    }

    private void throwExceptionWhenMakeRekeyIkeSa(Exception exception) throws Exception {
        // Inject IkeSaRecord and release IKE SPI resource since we will lose their references
        // later.
        when(mMockSaRecordHelper.makeRekeyedIkeSaRecord(
                        eq(mSpyCurrentIkeSaRecord), any(), any(), any(), any()))
                .thenAnswer(
                        (invocation) -> {
                            captureAndReleaseIkeSpiResource(invocation, 4);
                            throw exception;
                        });
    }

    private void captureAndReleaseIkeSpiResource(InvocationOnMock invocation, int ikeConfigIndex) {
        IkeSaRecordConfig config = (IkeSaRecordConfig) invocation.getArguments()[ikeConfigIndex];
        config.initSpi.close();
        config.respSpi.close();
    }

    private void setupDnsResolutionForNetwork(
            Network network, int dnsLookupsForSuccess, InetAddress remoteAddress)
            throws Exception {
        doAnswer(new Answer() {
            private int mAttempedDnsLookups = 0;

            public Object answer(InvocationOnMock invocation) throws IOException {
                mAttempedDnsLookups++;
                if (mAttempedDnsLookups < dnsLookupsForSuccess) {
                    throw new UnknownHostException("DNS failed");
                } else {
                    return new InetAddress[] {remoteAddress};
                }
            }
        }).when(network).getAllByName(REMOTE_HOSTNAME);
    }

    private void verifyIkeMetricsLogged(int stateCode, int exceptionCode) {
        verifyMetricsLogged(IkeMetrics.IKE_SESSION_TYPE_IKE, stateCode, exceptionCode);
    }

    private void verifyFireCallbackOnDnsFailure(IkeSessionCallback callback, IState expectedState) {
        if (SdkLevel.isAtLeastT()) {
            verify(callback).onClosedWithException(
                    argThat(e -> e instanceof IkeIOException
                            && e.getCause() instanceof UnknownHostException));
            verifyIkeMetricsLogged(
                    getStateCode(expectedState), IkeMetrics.IKE_ERROR_IO_DNS_FAILURE);
        } else {
            verify(callback).onClosedWithException(
                    argThat(e -> e instanceof IkeInternalException
                            && e.getCause() instanceof IOException));
            verifyIkeMetricsLogged(getStateCode(expectedState), IkeMetrics.IKE_ERROR_INTERNAL);
        }
    }

    private void setupAndVerifyDnsResolutionForIkeSession(
            int dnsLookupsForSuccess, int expectedDnsLookups, boolean expectSessionClosed)
            throws Exception {
        mIkeSessionStateMachine.quitNow();

        // Reset the network to ignore DNS resolution from mIkeSessionStateMachine creation in
        // setUp()
        resetDefaultNetwork();

        setupDnsResolutionForNetwork(mMockDefaultNetwork, dnsLookupsForSuccess, REMOTE_ADDRESS);

        IkeSessionParams ikeParams =
                buildIkeSessionParamsCommon()
                        .setAuthPsk(mPsk)
                        .setServerHostname(REMOTE_HOSTNAME)
                        .build();
        mIkeSessionStateMachine =
                makeAndStartIkeSession(
                        ikeParams,
                        LOCAL_ADDRESS,
                        expectSessionClosed ? null : REMOTE_ADDRESS);

        verify(mMockDefaultNetwork, times(expectedDnsLookups)).getAllByName(REMOTE_HOSTNAME);
        if (expectSessionClosed) {
            assertNull(mIkeSessionStateMachine.getCurrentState());
            verifyFireCallbackOnDnsFailure(
                    mMockIkeSessionCallback, mIkeSessionStateMachine.mInitial);
        }
    }

    @Test
    public void testResolveRemoteHostName() throws Exception {
        setupAndVerifyDnsResolutionForIkeSession(
                1 /* dnsLookupsForSuccess */,
                1 /* expectedDnsLookups */,
                false /* expectSessionClosed */);
    }

    @Test
    public void testResolveRemoteHostNameWithDnsRetries() throws Exception {
        setupAndVerifyDnsResolutionForIkeSession(
                2 /* dnsLookupsForSuccess */,
                2 /* expectedDnsLookups */,
                false /* expectSessionClosed */);
    }

    @Test
    public void testResolveRemoteHostNameWithDnsFailure() throws Exception {
        // Require more lookups for successful DNS than IKE allows to force failure
        setupAndVerifyDnsResolutionForIkeSession(
                4 /* dnsLookupsForSuccess */,
                3 /* expectedDnsLookups */,
                true /* expectSessionClosed */);
    }

    private void verifyTestMode(boolean isTestNetwork) throws Exception {
        doReturn(isTestNetwork)
                .when(mMockNetworkCapabilities)
                .hasTransport(RandomnessFactory.TRANSPORT_TEST);

        Network network = mockNewNetworkAndAddress(true /*isIpv4*/);
        IkeSessionParams ikeParams =
                buildIkeSessionParamsCommon()
                        .setNetwork(network)
                        .setAuthPsk("psk".getBytes())
                        .build();

        // Clear #getActiveNetwork() call in #setUp() to pass the verification in
        // #makeAndStartIkeSession()
        resetMockConnectManager();
        setupLocalAddressForNetwork(network, LOCAL_ADDRESS);
        setupRemoteAddressForNetwork(network, REMOTE_ADDRESS);
        IkeSessionStateMachine ikeSession = makeAndStartIkeSession(ikeParams);

        SecureRandom random = ikeSession.mIkeContext.getRandomnessFactory().getRandom();
        if (isTestNetwork) {
            assertNotNull(random);
            assertTrue(random instanceof DeterministicSecureRandom);
        } else {
            assertNull(random);
        }
    }

    @Test
    public void testEnableTestMode() throws Exception {
        verifyTestMode(true /* isTestNetwork */);
    }

    @Test
    public void testDisableTestMode() throws Exception {
        verifyTestMode(false /* isTestNetwork */);
    }

    private void verifyOutboundKePayload(int expectedDhGroup) {
        verify(mMockIkeMessageHelper, atLeastOnce()).encode(mIkeMessageCaptor.capture());
        IkeMessage reqMsg = mIkeMessageCaptor.getValue();
        IkeKePayload kePayload =
                reqMsg.getPayloadForType(IkePayload.PAYLOAD_TYPE_KE, IkeKePayload.class);
        assertEquals(expectedDhGroup, kePayload.dhGroup);
    }

    @Test
    public void testCreateIkeLocalIkeInitNegotiatesDhGroup() throws Exception {
        // Clear the calls triggered by starting IkeSessionStateMachine in #setup()
        reset(mSpyIkeConnectionCtrl);

        setupFirstIkeSa();
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();

        // Verify we started with the top proposed DH group
        verifyOutboundKePayload(SaProposal.DH_GROUP_1024_BIT_MODP);

        // Send back a INVALID_KE_PAYLOAD, and verify that the selected DH group changes
        resetMockIkeMessageHelper();
        ReceivedIkePacket resp =
                makeDummyReceivedIkeInitRespPacket(
                        Arrays.asList(IkePayload.PAYLOAD_TYPE_NOTIFY),
                        Arrays.asList(INVALID_KE_PAYLOAD_HEX_STRING));
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        verifyOutboundKePayload(SaProposal.DH_GROUP_2048_BIT_MODP);
        verify(mSpyIkeConnectionCtrl, atLeast(1)).tearDown();
    }

    private ReceivedIkePacket getIkeInitRespWithCookie() throws Exception {
        IkeNotifyPayload inCookieNotify = new IkeNotifyPayload(NOTIFY_TYPE_COOKIE, COOKIE_DATA);
        List<IkePayload> payloads = new ArrayList<>();
        payloads.add(inCookieNotify);
        return makeDummyReceivedIkeInitRespPacket(payloads);
    }

    @Test
    public void testCreateIkeLocalIkeInitReceivesCookie() throws Exception {
        setupFirstIkeSa();

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();

        // Encode 2 times: one for mIkeInitRequestBytes and one for sending packets
        verify(mMockIkeMessageHelper, times(2)).encode(mIkeMessageCaptor.capture());
        IkeMessage originalReqMsg = mIkeMessageCaptor.getValue();
        List<IkePayload> originalPayloadList = originalReqMsg.ikePayloadList;

        // Reset to forget sending original IKE INIT request
        resetMockIkeMessageHelper();

        // Send back a Notify-Cookie
        ReceivedIkePacket resp = getIkeInitRespWithCookie();
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        // Verify retry IKE INIT request
        verify(mMockIkeMessageHelper, times(2)).encode(mIkeMessageCaptor.capture());
        IkeMessage ikeInitReqMessage = mIkeMessageCaptor.getValue();
        List<IkePayload> payloadList = ikeInitReqMessage.ikePayloadList;

        IkeNotifyPayload outCookieNotify = (IkeNotifyPayload) payloadList.get(0);
        assertEquals(NOTIFY_TYPE_COOKIE, outCookieNotify.notifyType);
        assertArrayEquals(COOKIE_DATA, outCookieNotify.notifyData);

        // First 4 payloads MUST follow RFC 4306 so that IKE library can be compatible with old
        // implementations.
        int[] expectedPayloadType =
                new int[] {
                    PAYLOAD_TYPE_NOTIFY, PAYLOAD_TYPE_SA, PAYLOAD_TYPE_KE, PAYLOAD_TYPE_NONCE
                };
        int len = expectedPayloadType.length;
        for (int i = 0; i < len; i++) {
            assertEquals(expectedPayloadType[i], payloadList.get(i).payloadType);
        }

        assertEquals(originalPayloadList, payloadList.subList(1, payloadList.size()));

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeInit);
    }

    @Test
    public void testCreateIkeLocalIkeInitRcvRespAfterRcvCookie() throws Exception {
        setupFirstIkeSa();

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();

        // Receive IKE INIT response with Cookie
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, getIkeInitRespWithCookie());

        // Receive IKE INIT response
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, makeIkeInitResponse());
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuth);
        verifyIkeSaNegotiationResult();
    }

    @Test
    public void testCreateIkeLocalIkeInitSwitchesToEncapPortsIpv4() throws Exception {
        setupFirstIkeSa();
        assertFalse(mSpyIkeConnectionCtrl.useUdpEncapSocket());

        triggerAndVerifyIkeInitReq(true /* expectingNatDetection */, true /* expectingFakedNatd */);

        receiveAndGetIkeInitResp();

        assertTrue(mSpyIkeConnectionCtrl.useUdpEncapSocket());
        assertEquals(NAT_DETECTED, mSpyIkeConnectionCtrl.getNatStatus());

        verify(mMockIkeUdp4Socket).unregisterIke(anyLong());
    }

    private void restartIkeSessionWithEnforcePort4500AndVerifyIkeSocket() throws Exception {
        // Quit and start a new IKE Session with IKE_OPTION_FORCE_PORT_4500
        mIkeSessionStateMachine.quitNow();
        IkeSessionParams ikeParams =
                buildIkeSessionParamsWithIkeOptions(IKE_OPTION_FORCE_PORT_4500);
        mIkeSessionStateMachine = makeAndStartIkeSession(ikeParams);
        mLooper.dispatchAll();

        assertTrue(mSpyIkeConnectionCtrl.useUdpEncapSocket());
    }

    @Test
    public void testInitialStateWithEnforcePort4500() throws Exception {
        restartIkeSessionWithEnforcePort4500AndVerifyIkeSocket();
    }

    @Test
    public void testCreateIkeLocalIkeInitNatTraversalWithEnforcePort4500() throws Exception {
        restartIkeSessionWithEnforcePort4500AndVerifyIkeSocket();
        setupFirstIkeSa();
        assertTrue(mSpyIkeConnectionCtrl.useUdpEncapSocket());

        triggerAndVerifyIkeInitReq(true /* expectingNatDetection */, true /* expectingFakedNatd */);

        receiveAndGetIkeInitResp();

        assertTrue(mSpyIkeConnectionCtrl.useUdpEncapSocket());
        assertEquals(NAT_DETECTED, mSpyIkeConnectionCtrl.getNatStatus());
    }

    @Test
    public void testCreateIkeLocalIkeInitNatTraversalNotSupported() throws Exception {
        setupFirstIkeSa();
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();

        // Receive IKE INIT response
        ReceivedIkePacket dummyReceivedIkePacket =
                makeIkeInitResponseWithRequiredPayloads(
                        Collections.emptyList(), Collections.emptyList());
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyReceivedIkePacket);
        mLooper.dispatchAll();

        // Validate socket switched
        assertEquals(mMockIkeUdp4Socket, mSpyIkeConnectionCtrl.getIkeSocket());
        assertEquals(NAT_TRAVERSAL_UNSUPPORTED, mSpyIkeConnectionCtrl.getNatStatus());
        verify(mMockIkeUdp4Socket, never()).unregisterIke(anyLong());
    }

    private void verifyNatdSrcIpFromIkeInitReqMessage(IkeMessage ikeInitReqMessag) {
        verifyNatdSrcIpFromIkeInitReqMessage(ikeInitReqMessag, false /* expectingFakedNatd */);
    }

    private void verifyNatdSrcIpFromIkeInitReqMessage(
            IkeMessage ikeInitReqMessage, boolean expectingFakedNatd) {
        List<IkeNotifyPayload> notifyPayloads =
                ikeInitReqMessage.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        IkeNotifyPayload natdSrcIpPayload = null;
        for (IkeNotifyPayload notifyPayload : notifyPayloads) {
            if (notifyPayload.notifyType == NOTIFY_TYPE_NAT_DETECTION_SOURCE_IP) {
                natdSrcIpPayload = notifyPayload;
            }
        }
        assertNotNull(natdSrcIpPayload);

        byte[] localNatDataNotFaked =
                IkeNotifyPayload.generateNatDetectionData(
                        ikeInitReqMessage.ikeHeader.ikeInitiatorSpi,
                        ikeInitReqMessage.ikeHeader.ikeResponderSpi,
                        mSpyIkeConnectionCtrl.getLocalAddress(),
                        mSpyIkeConnectionCtrl.getLocalPort());

        assertEquals(
                !expectingFakedNatd,
                Arrays.equals(localNatDataNotFaked, natdSrcIpPayload.notifyData));
    }

    private void triggerAndVerifyIkeInitReq(boolean expectingNatDetection) throws Exception {
        triggerAndVerifyIkeInitReq(expectingNatDetection, false);
    }

    private void triggerAndVerifyIkeInitReq(
            boolean expectingNatDetection, boolean expectingFakedNatd) throws Exception {
        // Send IKE INIT request
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Validate outbound IKE INIT request
        verify(mMockIkeMessageHelper, times(2)).encode(mIkeMessageCaptor.capture());
        IkeMessage ikeInitReqMessage = mIkeMessageCaptor.getValue();

        IkeHeader ikeHeader = ikeInitReqMessage.ikeHeader;
        assertEquals(IkeHeader.EXCHANGE_TYPE_IKE_SA_INIT, ikeHeader.exchangeType);
        assertFalse(ikeHeader.isResponseMsg);
        assertTrue(ikeHeader.fromIkeInitiator);

        List<IkePayload> payloadList = ikeInitReqMessage.ikePayloadList;
        assertTrue(isIkePayloadExist(payloadList, IkePayload.PAYLOAD_TYPE_SA));
        assertTrue(isIkePayloadExist(payloadList, IkePayload.PAYLOAD_TYPE_KE));
        assertTrue(isIkePayloadExist(payloadList, IkePayload.PAYLOAD_TYPE_NONCE));
        assertTrue(isNotifyExist(payloadList, NOTIFY_TYPE_IKEV2_FRAGMENTATION_SUPPORTED));
        assertTrue(isNotifyExist(payloadList, NOTIFY_TYPE_SIGNATURE_HASH_ALGORITHMS));

        assertEquals(
                expectingNatDetection,
                isNotifyExist(payloadList, NOTIFY_TYPE_NAT_DETECTION_SOURCE_IP));
        assertEquals(
                expectingNatDetection,
                isNotifyExist(payloadList, NOTIFY_TYPE_NAT_DETECTION_DESTINATION_IP));

        if (expectingNatDetection) {
            verifyNatdSrcIpFromIkeInitReqMessage(ikeInitReqMessage, expectingFakedNatd);
        }
    }

    private ReceivedIkePacket receiveAndGetIkeInitResp() throws Exception {
        ReceivedIkePacket dummyReceivedIkePacket = makeIkeInitResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyReceivedIkePacket);
        mLooper.dispatchAll();
        verifyIncrementLocaReqMsgId();
        return dummyReceivedIkePacket;
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testCreateIkeLocalIkeInitWithoutIpv6NatD() throws Exception {
        mIkeSessionStateMachine.quitNow();
        resetMockConnectManager();
        resetMockIkeMessageHelper();

        // Restart mIkeSessionStateMachine so it uses IPv6 addresses
        final Network v6OnlyNetwork =
                mockNewNetworkAndAddress(false /* isIpv4 */, LOCAL_ADDRESS_V6, REMOTE_ADDRESS_V6);
        final IkeSessionParams params =
                buildIkeSessionParamsCommon()
                        .setAuthPsk(mPsk)
                        .setNetwork(v6OnlyNetwork)
                        .addIkeOption(IKE_OPTION_MOBIKE)
                        .build();
        mIkeSessionStateMachine =
                makeAndStartIkeSession(
                        params,
                        LOCAL_ADDRESS_V6,
                        REMOTE_ADDRESS_V6);
        setupFirstIkeSa();

        triggerAndVerifyIkeInitReq(false /* expectingNatDetection */);
        receiveAndGetIkeInitResp();

        assertTrue(mSpyIkeConnectionCtrl.getIkeSocket() instanceof IkeUdp6Socket);
        assertEquals(NAT_TRAVERSAL_SUPPORT_NOT_CHECKED, mSpyIkeConnectionCtrl.getNatStatus());
    }

    @Ignore
    public void disableTestCreateIkeLocalIkeInit() throws Exception {
        setupFirstIkeSa();

        triggerAndVerifyIkeInitReq(true /* expectingNatDetection */);
        final ReceivedIkePacket dummyReceivedIkePacket = receiveAndGetIkeInitResp();

        verify(mMockCurrentIkeSocket)
                .registerIke(eq(mSpyCurrentIkeSaRecord.getLocalSpi()), eq(mSpyIkeConnectionCtrl));

        verify(mMockIkeMessageHelper)
                .decode(0, dummyReceivedIkePacket.ikeHeader, dummyReceivedIkePacket.ikePacketBytes);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuth);
        verifyRetransmissionStarted();
        verifyIkeSaNegotiationResult();
    }

    private void verifyIkeSaNegotiationResult() throws Exception {
        // Validate negotiated SA proposal.
        IkeSaProposal negotiatedProposal = mIkeSessionStateMachine.mSaProposal;
        assertNotNull(negotiatedProposal);

        assertEquals(
                new EncryptionTransform[] {mIkeEncryptionTransform},
                negotiatedProposal.getEncryptionTransforms());
        assertEquals(
                new IntegrityTransform[] {mIkeIntegrityTransform},
                negotiatedProposal.getIntegrityTransforms());
        assertEquals(new PrfTransform[] {mIkePrfTransform}, negotiatedProposal.getPrfTransforms());

        // Validate current IkeSaRecord.
        verify(mMockSaRecordHelper)
                .makeFirstIkeSaRecord(
                        any(IkeMessage.class),
                        any(IkeMessage.class),
                        mIkeSaRecordConfigCaptor.capture());

        IkeSaRecordConfig ikeSaRecordConfig = mIkeSaRecordConfigCaptor.getValue();
        assertEquals(KEY_LEN_IKE_PRF, ikeSaRecordConfig.prf.getKeyLength());
        assertEquals(KEY_LEN_IKE_INTE, ikeSaRecordConfig.integrityKeyLength);
        assertEquals(KEY_LEN_IKE_ENCR, ikeSaRecordConfig.encryptionKeyLength);
        assertNotNull(ikeSaRecordConfig.saLifetimeAlarmScheduler);

        // Validate NAT detection
        assertEquals(NAT_DETECTED, mSpyIkeConnectionCtrl.getNatStatus());

        // Validate vendor IDs
        List<byte[]> vendorIds = new ArrayList<>();
        vendorIds.add(REMOTE_VENDOR_ID_ONE);
        assertByteArrayListEquals(vendorIds, mIkeSessionStateMachine.mRemoteVendorIds);

        // Validate fragmentation support negotiation
        assertEquals(
                Arrays.asList(EXTENSION_TYPE_FRAGMENTATION),
                mIkeSessionStateMachine.mEnabledExtensions);

        // Validate Signature Hash Algorithms received in IKE INIT response
        Set<Short> expectedHashAlgos = new HashSet<Short>();
        for (short algo : IkeAuthDigitalSignPayload.ALL_SIGNATURE_ALGO_TYPES) {
            expectedHashAlgos.add(algo);
        }
        assertEquals(
                expectedHashAlgos,
                ((CreateIkeLocalIkeAuth) mIkeSessionStateMachine.mCreateIkeLocalIkeAuth)
                        .mSetupData
                        .peerSignatureHashAlgorithms);
    }

    private void setIkeInitResults() throws Exception {
        mIkeSessionStateMachine.mIkeCipher = mock(IkeCipher.class);
        mIkeSessionStateMachine.mIkeIntegrity = mock(IkeMacIntegrity.class);
        mIkeSessionStateMachine.mIkePrf = mock(IkeMacPrf.class);
        mIkeSessionStateMachine.mSaProposal = buildNegotiatedSaProposal();
        mIkeSessionStateMachine.mCurrentIkeSaRecord = mSpyCurrentIkeSaRecord;
        mIkeSessionStateMachine.mRemoteVendorIds =
                Arrays.asList(REMOTE_VENDOR_ID_ONE, REMOTE_VENDOR_ID_TWO);
        mIkeSessionStateMachine.mEnabledExtensions.add(EXTENSION_TYPE_FRAGMENTATION);
        mIkeSessionStateMachine.addIkeSaRecord(mSpyCurrentIkeSaRecord);

        mSpyIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                false /* isNatDetected */, mSpyCurrentIkeSaRecord.getLocalSpi());
        mMockCurrentIkeSocket = mSpyIkeConnectionCtrl.getIkeSocket();
    }

    /** Initializes the mIkeSessionStateMachine in the IDLE state. */
    private void setupIdleStateMachine() throws Exception {
        verify(mMockBusyWakelock).acquire();

        setIkeInitResults();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();

        mDummyChildSmCallback =
                createChildAndGetChildSessionSmCallback(
                        mMockChildSessionStateMachine, CHILD_SPI_REMOTE, mMockChildSessionCallback);

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        verify(mMockBusyWakelock).release();

        // For convenience to verify wakelocks in all other places.
        reset(mMockBusyWakelock);
    }

    private abstract class IkeAuthTestPretestBase {
        protected IkeInitData mIkeInitData;

        IkeAuthTestPretestBase() {
            this(new HashSet<>() /* peerSignatureHashAlgorithms */);
        }

        IkeAuthTestPretestBase(Set<Short> peerSignatureHashAlgorithms) {
            InitialSetupData initialSetupData =
                    new InitialSetupData(
                            mChildSessionParams,
                            mMockChildSessionCallback,
                            SaProposal.DH_GROUP_1024_BIT_MODP);

            mIkeInitData =
                    new IkeInitData(
                            initialSetupData,
                            new byte[0],
                            new byte[0],
                            new IkeNoncePayload(createMockRandomFactory()),
                            new IkeNoncePayload(createMockRandomFactory()),
                            peerSignatureHashAlgorithms);
        }

        public void mockIkeInitAndTransitionToIkeAuth() throws Exception {
            setIkeInitResults();

            // Need to create a real IkeMacPrf instance for authentication because we cannot inject
            // a method stub for IkeMacPrf#signBytes. IkeMacPrf#signBytes is inherited from a
            // package protected class IkePrf. We don't have the visibility to mock it.
            mIkeSessionStateMachine.mIkePrf =
                    IkeMacPrf.create(new PrfTransform(SaProposal.PSEUDORANDOM_FUNCTION_HMAC_SHA1));

            performStateTransition();
        }

        abstract void performStateTransition() throws Exception;
    }

    private final class IkeFirstAuthTestPretest extends IkeAuthTestPretestBase {
        IkeFirstAuthTestPretest() {
            super();
        }

        IkeFirstAuthTestPretest(Set<Short> peerSignatureHashAlgorithms) {
            super(peerSignatureHashAlgorithms);
        }

        @Override
        void performStateTransition() throws Exception {
            mIkeSessionStateMachine.mCreateIkeLocalIkeAuth.setIkeSetupData(mIkeInitData);
            mIkeSessionStateMachine.sendMessage(
                    IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                    mIkeSessionStateMachine.mCreateIkeLocalIkeAuth);
            mLooper.dispatchAll();
        }
    }

    private abstract class IkeAuthEapTestPretestBase extends IkeAuthTestPretestBase {
        protected IkeAuthData mIkeAuthData;

        IkeAuthEapTestPretestBase(IkeIdPayload initIdPayload, IkeIdPayload respIdPayload)
                throws Exception {
            super();
            List<Integer> payloadTypeList = new ArrayList<>();
            List<String> payloadHexStringList = new ArrayList<>();

            payloadTypeList.add(IkePayload.PAYLOAD_TYPE_SA);
            payloadTypeList.add(IkePayload.PAYLOAD_TYPE_TS_INITIATOR);
            payloadTypeList.add(IkePayload.PAYLOAD_TYPE_TS_RESPONDER);

            payloadHexStringList.add(CHILD_SA_PAYLOAD_HEX_STRING);
            payloadHexStringList.add(TS_INIT_PAYLOAD_HEX_STRING);
            payloadHexStringList.add(TS_RESP_PAYLOAD_HEX_STRING);

            List<IkePayload> firstChildReqList =
                    hexStrListToIkePayloadList(
                            payloadTypeList, payloadHexStringList, false /*isResp*/);
            mIkeAuthData =
                    new IkeAuthData(mIkeInitData, initIdPayload, respIdPayload, firstChildReqList);
        }
    }

    private static IkeIdPayload buildMockIkeIdPayload() {
        IkeIdPayload idPayload = mock(IkeIdPayload.class);
        doReturn(new byte[0]).when(idPayload).getEncodedPayloadBody();
        return idPayload;
    }

    private final class IkeAuthInEapTestPretest extends IkeAuthEapTestPretestBase {
        IkeAuthInEapTestPretest() throws Exception {
            super(buildMockIkeIdPayload(), buildMockIkeIdPayload());
        }

        @Override
        void performStateTransition() throws Exception {
            mIkeSessionStateMachine.mCreateIkeLocalIkeAuthInEap.setIkeSetupData(mIkeAuthData);
            mIkeSessionStateMachine.sendMessage(
                    IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                    mIkeSessionStateMachine.mCreateIkeLocalIkeAuthInEap);
            mLooper.dispatchAll();
        }
    }

    private final class IkeAuthPostEapTestPretest extends IkeAuthEapTestPretestBase {
        IkeAuthPostEapTestPretest(IkeIdPayload initIdPayload, IkeIdPayload respIdPayload)
                throws Exception {
            super(initIdPayload, respIdPayload);
        }

        @Override
        void performStateTransition() throws Exception {
            mIkeSessionStateMachine.mCreateIkeLocalIkeAuthPostEap.setIkeSetupData(mIkeAuthData);
            mIkeSessionStateMachine.sendMessage(
                    IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                    mIkeSessionStateMachine.mCreateIkeLocalIkeAuthPostEap);
            mLooper.dispatchAll();
        }
    }

    /**
     * Utility to register a new callback -> state machine mapping.
     *
     * <p>Must be used if IkeSessionStateMachine.openChildSession() is not called, but commands
     * injected instead.
     *
     * @param callback The callback to be used for the mapping
     * @param sm The ChildSessionStateMachine instance to be used.
     */
    private void registerChildStateMachine(
            ChildSessionCallback callback, ChildSessionStateMachine sm) {
        injectChildSessionInSpyDeps(mSpyDeps, sm, callback);
        mIkeSessionStateMachine.registerChildSessionCallback(
                mChildSessionParams, callback, false /*isFirstChild*/);
    }

    private IChildSessionSmCallback verifyMakeChildAndReturnChildSmCb(
            ChildSessionCallback expectedChildSessionCb) {
        ArgumentCaptor<IkeContext> ikeContextCaptor = ArgumentCaptor.forClass(IkeContext.class);
        verify(mSpyDeps)
                .newChildSessionStateMachine(
                        ikeContextCaptor.capture(),
                        any(ChildSessionStateMachine.Config.class),
                        eq(expectedChildSessionCb),
                        mChildSessionSmCbCaptor.capture());

        IkeContext ikeContext = ikeContextCaptor.getValue();
        assertEquals(mLooper.getLooper(), ikeContext.getLooper());
        assertEquals(mSpyContext, ikeContext.getContext());

        return mChildSessionSmCbCaptor.getValue();
    }

    @Test
    public void testCreateAdditionalChild() throws Exception {
        setupIdleStateMachine();

        ChildSessionCallback childCallback = mock(ChildSessionCallback.class);
        ChildSessionStateMachine childStateMachine = mock(ChildSessionStateMachine.class);
        registerChildStateMachine(childCallback, childStateMachine);

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getChildLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_CHILD,
                        childCallback,
                        mChildSessionParams));
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verify(childStateMachine)
                .createChildSession(
                        eq(LOCAL_ADDRESS),
                        eq(REMOTE_ADDRESS),
                        any(), // udpEncapSocket
                        eq(mIkeSessionStateMachine.mIkePrf),
                        eq(mIkeSessionStateMachine.mSaProposal.getDhGroupTransforms()[0].id),
                        any()); // sk_d

        IChildSessionSmCallback cb = verifyMakeChildAndReturnChildSmCb(childCallback);

        // Mocking sending request
        cb.onOutboundPayloadsReady(
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                false /*isResp*/,
                new ArrayList<>(),
                childStateMachine);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        IkeMessage createChildRequest = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = createChildRequest.ikeHeader;
        assertEquals(IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA, ikeHeader.exchangeType);
        assertFalse(ikeHeader.isResponseMsg);
        assertTrue(ikeHeader.fromIkeInitiator);
        assertEquals(mSpyCurrentIkeSaRecord.getLocalRequestMessageId(), ikeHeader.messageId);
        assertTrue(createChildRequest.ikePayloadList.isEmpty());

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);

        // Mocking receiving response
        ReceivedIkePacket dummyCreateChildResp = makeCreateChildCreateMessage(true /*isResp*/);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyCreateChildResp);
        mLooper.dispatchAll();

        verifyIncrementLocaReqMsgId();
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyCreateChildResp);

        verify(childStateMachine)
                .receiveResponse(
                        eq(IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA), mPayloadListCaptor.capture());

        List<IkePayload> childRespList = mPayloadListCaptor.getValue();
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_SA));
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_TS_INITIATOR));
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_TS_RESPONDER));
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_NONCE));

        // Mock finishing procedure
        cb.onProcedureFinished(childStateMachine);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
        verifyRetransmissionStopped();
    }

    @Test
    public void testTriggerDeleteChildLocal() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getChildLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_CHILD,
                        mMockChildSessionCallback,
                        null /*childParams*/));
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verify(mMockChildSessionStateMachine).deleteChildSession();
        verify(mMockBusyWakelock).acquire();
    }

    @Test
    public void testHandleDeleteChildBeforeCreation() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getChildLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_CHILD,
                        mock(ChildSessionCallback.class),
                        null /*childParams*/));
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testTriggerRekeyChildLocal() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getChildLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_CHILD,
                        mMockChildSessionCallback,
                        null /*childParams*/));
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verify(mMockChildSessionStateMachine).rekeyChildSession();
        verify(mMockBusyWakelock).acquire();
    }

    private IChildSessionSmCallback createChildAndGetChildSessionSmCallback(
            ChildSessionStateMachine child, int remoteSpi) throws Exception {
        return createChildAndGetChildSessionSmCallback(
                child, remoteSpi, mock(ChildSessionCallback.class));
    }

    private IChildSessionSmCallback createChildAndGetChildSessionSmCallback(
            ChildSessionStateMachine child, int remoteSpi, ChildSessionCallback childCallback)
            throws Exception {
        registerChildStateMachine(childCallback, child);

        IChildSessionSmCallback cb = mIkeSessionStateMachine.new ChildSessionSmCallback();
        cb.onChildSaCreated(remoteSpi, child);
        mLooper.dispatchAll();

        return cb;
    }

    private void transitionToChildProcedureOngoing() {
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mChildProcedureOngoing);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
    }

    private void verifyChildReceiveDeleteRequest(
            ChildSessionStateMachine child, IkeDeletePayload[] expectedDelPayloads) {
        verify(child)
                .receiveRequest(
                        eq(IKE_EXCHANGE_SUBTYPE_DELETE_CHILD),
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        mPayloadListCaptor.capture());
        List<IkePayload> reqPayloads = mPayloadListCaptor.getValue();

        int numExpectedDelPayloads = expectedDelPayloads.length;
        assertEquals(numExpectedDelPayloads, reqPayloads.size());

        for (int i = 0; i < numExpectedDelPayloads; i++) {
            assertEquals(expectedDelPayloads[i], (IkeDeletePayload) reqPayloads.get(i));
        }
    }

    private void outboundDeleteChildPayloadsReady(
            IChildSessionSmCallback childSmCb,
            IkeDeletePayload delPayload,
            boolean isResp,
            ChildSessionStateMachine child) {
        List<IkePayload> outPayloadList = new ArrayList<>();
        outPayloadList.add(delPayload);
        childSmCb.onOutboundPayloadsReady(
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, isResp, outPayloadList, child);
        mLooper.dispatchAll();
    }

    private List<IkePayload> verifyOutInfoMsgHeaderAndGetPayloads(boolean isResp) {
        IkeMessage deleteChildMessage = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = deleteChildMessage.ikeHeader;
        assertEquals(mSpyCurrentIkeSaRecord.getInitiatorSpi(), ikeHeader.ikeInitiatorSpi);
        assertEquals(mSpyCurrentIkeSaRecord.getResponderSpi(), ikeHeader.ikeResponderSpi);
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, ikeHeader.exchangeType);
        assertEquals(mSpyCurrentIkeSaRecord.isLocalInit, ikeHeader.fromIkeInitiator);
        assertEquals(isResp, ikeHeader.isResponseMsg);

        return deleteChildMessage.ikePayloadList;
    }

    @Test
    public void testDeferChildRequestToChildProcedureOngoing() throws Exception {
        setupIdleStateMachine();

        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {new IkeDeletePayload(new int[] {CHILD_SPI_REMOTE})};
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verifyChildReceiveDeleteRequest(mMockChildSessionStateMachine, inboundDelPayloads);
    }

    @Test
    public void testRemoteDeleteOneChild() throws Exception {
        setupIdleStateMachine();
        transitionToChildProcedureOngoing();

        // Receive Delete Child Request
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {new IkeDeletePayload(new int[] {CHILD_SPI_REMOTE})};
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        // Verify received payloads
        verifyChildReceiveDeleteRequest(mMockChildSessionStateMachine, inboundDelPayloads);

        // Outbound payload list ready
        IkeDeletePayload outDelPayload = new IkeDeletePayload(new int[] {CHILD_SPI_LOCAL});
        outboundDeleteChildPayloadsReady(
                mDummyChildSmCallback,
                outDelPayload,
                true /*isResp*/,
                mMockChildSessionStateMachine);

        // Verify outbound response
        List<IkePayload> payloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, payloadList.size());
        assertEquals(outDelPayload, ((IkeDeletePayload) payloadList.get(0)));
        verify(mMockBusyWakelock).acquire();
    }

    @Test
    public void testRemoteDeleteMultipleChildSession() throws Exception {
        ChildSessionStateMachine childOne = mock(ChildSessionStateMachine.class);
        int childOneRemoteSpi = 11;
        int childOneLocalSpi = 12;

        ChildSessionStateMachine childTwo = mock(ChildSessionStateMachine.class);
        int childTwoRemoteSpi = 21;
        int childTwoLocalSpi = 22;

        setupIdleStateMachine();
        IChildSessionSmCallback childSmCbOne =
                createChildAndGetChildSessionSmCallback(childOne, childOneRemoteSpi);
        IChildSessionSmCallback childSmCbTwo =
                createChildAndGetChildSessionSmCallback(childTwo, childTwoRemoteSpi);

        transitionToChildProcedureOngoing();

        // Receive Delete Child Request
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {
                    new IkeDeletePayload(new int[] {childOneRemoteSpi, childTwoRemoteSpi})
                };
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        // Verify received payloads
        verifyChildReceiveDeleteRequest(childOne, inboundDelPayloads);
        verifyChildReceiveDeleteRequest(childTwo, inboundDelPayloads);

        // childOne outbound payload list ready
        IkeDeletePayload outDelPayloadOne = new IkeDeletePayload(new int[] {childOneLocalSpi});
        outboundDeleteChildPayloadsReady(childSmCbOne, outDelPayloadOne, true /*isResp*/, childOne);
        mLooper.dispatchAll();

        // Verify that no response is sent
        verifyEncryptAndEncodeNeverCalled(mSpyCurrentIkeSaRecord);

        // childTwo outbound payload list ready
        IkeDeletePayload outDelPayloadTwo = new IkeDeletePayload(new int[] {childTwoLocalSpi});
        outboundDeleteChildPayloadsReady(childSmCbTwo, outDelPayloadTwo, true /*isResp*/, childTwo);
        mLooper.dispatchAll();

        // Verify outbound response
        List<IkePayload> payloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(2, payloadList.size());
        assertEquals(outDelPayloadOne, ((IkeDeletePayload) payloadList.get(0)));
        assertEquals(outDelPayloadTwo, ((IkeDeletePayload) payloadList.get(1)));
    }

    @Test
    public void testRemoteDeleteMultipleChildSaInSameSession() throws Exception {
        int newChildRemoteSpi = 21;
        int newChildLocalSpi = 22;

        setupIdleStateMachine();
        mDummyChildSmCallback.onChildSaCreated(newChildRemoteSpi, mMockChildSessionStateMachine);

        transitionToChildProcedureOngoing();

        // Receive Delete Child Request
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {
                    new IkeDeletePayload(new int[] {CHILD_SPI_REMOTE}),
                    new IkeDeletePayload(new int[] {newChildRemoteSpi})
                };
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        // Verify received payloads
        verifyChildReceiveDeleteRequest(mMockChildSessionStateMachine, inboundDelPayloads);

        // child outbound payload list ready
        IkeDeletePayload outDelPayload =
                new IkeDeletePayload(new int[] {CHILD_SPI_LOCAL, newChildLocalSpi});
        outboundDeleteChildPayloadsReady(
                mDummyChildSmCallback,
                outDelPayload,
                true /*isResp*/,
                mMockChildSessionStateMachine);
        mLooper.dispatchAll();

        // Verify outbound response
        List<IkePayload> payloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, payloadList.size());
        assertEquals(outDelPayload, ((IkeDeletePayload) payloadList.get(0)));
    }

    @Test
    public void testIgnoreUnrecognizedChildSpi() throws Exception {
        int unrecognizedSpi = 2;

        setupIdleStateMachine();
        transitionToChildProcedureOngoing();

        // Receive Delete Child Request
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {
                    new IkeDeletePayload(new int[] {unrecognizedSpi, CHILD_SPI_REMOTE})
                };
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        // Verify received payloads
        verifyChildReceiveDeleteRequest(mMockChildSessionStateMachine, inboundDelPayloads);

        // child outbound payload list ready
        IkeDeletePayload outPayload = new IkeDeletePayload(new int[] {CHILD_SPI_LOCAL});
        outboundDeleteChildPayloadsReady(
                mDummyChildSmCallback, outPayload, true /*isResp*/, mMockChildSessionStateMachine);
        mLooper.dispatchAll();

        // Verify outbound response
        List<IkePayload> payloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, payloadList.size());
        assertEquals(outPayload, ((IkeDeletePayload) payloadList.get(0)));
    }

    @Test
    public void testRemoteDeleteChildHandlesReqWithNoRecognizedSpi() throws Exception {
        int unrecognizedSpi = 2;

        setupIdleStateMachine();

        // Receive Delete Child Request without any recognized SPI
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {new IkeDeletePayload(new int[] {unrecognizedSpi})};
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        // Verify outbound empty response was sent
        List<IkePayload> payloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertTrue(payloadList.isEmpty());

        // Verify IKE Session was back to Idle
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testRemoteCreateChild() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET, makeCreateChildCreateMessage(false /*isResp*/));

        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        List<IkePayload> ikePayloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, ikePayloadList.size());
        assertEquals(
                ERROR_TYPE_NO_ADDITIONAL_SAS,
                ((IkeNotifyPayload) ikePayloadList.get(0)).notifyType);
    }

    @Test
    public void testRemoteRekeyChild() throws Exception {
        setupIdleStateMachine();

        // Receive Rekey Create request
        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET,
                makeRekeyChildCreateMessage(false /*isResp*/, CHILD_SPI_REMOTE));
        mLooper.dispatchAll();

        verify(mMockChildSessionStateMachine)
                .receiveRequest(
                        eq(IKE_EXCHANGE_SUBTYPE_REKEY_CHILD),
                        eq(EXCHANGE_TYPE_CREATE_CHILD_SA),
                        any(List.class));
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);

        // Send Rekey Create response
        List<IkePayload> mockRekeyCreatePayloads = Arrays.asList(mock(IkePayload.class));
        mDummyChildSmCallback.onOutboundPayloadsReady(
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                true /*isResp*/,
                mockRekeyCreatePayloads,
                mMockChildSessionStateMachine);
        mLooper.dispatchAll();

        IkeMessage rekeyCreateResp =
                verifyAndGetOutboundEncryptedResp(IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA);
        assertEquals(mockRekeyCreatePayloads, rekeyCreateResp.ikePayloadList);

        // Forget sending Rekey Create response
        resetMockIkeMessageHelper();

        // Receive Delete Child Request
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {new IkeDeletePayload(new int[] {CHILD_SPI_REMOTE})};
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/));
        mLooper.dispatchAll();

        verify(mMockChildSessionStateMachine)
                .receiveRequest(
                        eq(IKE_EXCHANGE_SUBTYPE_DELETE_CHILD),
                        eq(EXCHANGE_TYPE_INFORMATIONAL),
                        any(List.class));

        // Send Rekey Delete response
        List<IkePayload> mockRekeyDeletePayloads = Arrays.asList(mock(IkePayload.class));
        mDummyChildSmCallback.onOutboundPayloadsReady(
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                true /*isResp*/,
                mockRekeyDeletePayloads,
                mMockChildSessionStateMachine);
        mLooper.dispatchAll();

        IkeMessage rekeyDeleteResp =
                verifyAndGetOutboundEncryptedResp(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL);
        assertEquals(mockRekeyDeletePayloads, rekeyDeleteResp.ikePayloadList);
    }

    @Test
    public void testHandleRekeyChildReqWithUnrecognizedSpi() throws Exception {
        int unrecognizedSpi = 2;

        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET,
                makeRekeyChildCreateMessage(false /*isResp*/, unrecognizedSpi));
        mLooper.dispatchAll();

        verify(mMockChildSessionStateMachine, never()).receiveRequest(anyInt(), anyInt(), any());
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        List<IkePayload> ikePayloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, ikePayloadList.size());
        IkeNotifyPayload notifyPayload = (IkeNotifyPayload) ikePayloadList.get(0);
        assertEquals(ERROR_TYPE_CHILD_SA_NOT_FOUND, notifyPayload.notifyType);
        assertEquals(unrecognizedSpi, notifyPayload.spi);
    }

    private void verifyNotifyUserCloseSession(IState state) {
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verify(mMockIkeSessionCallback).onClosed();
        verifyIkeMetricsLogged(getStateCode(state), IkeMetrics.IKE_ERROR_NONE);
    }

    private void verifyNotifyUserCloseSessionWithException(
            IState state, Exception exception, boolean isErrorFromChild) {
        IkeException ikeException =
                exception instanceof IkeException
                        ? (IkeException) exception
                        : new IkeInternalException(exception);

        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verify(mMockIkeSessionCallback)
                .onClosedWithException(argThat(e -> e.getCause() == ikeException.getCause()));

        if (isErrorFromChild) {
            verify(mIkeMetrics, never())
                    .logSessionTerminated(anyInt(), anyInt(), anyInt(), anyInt());
        } else {
            verifyIkeMetricsLogged(getStateCode(state), ikeException.getMetricsErrorCode());
        }
    }

    @Test
    public void testRcvRemoteDeleteIkeWhenChildProcedureOngoing() throws Exception {
        setupIdleStateMachine();
        transitionToChildProcedureOngoing();

        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET, makeDeleteIkeRequest(mSpyCurrentIkeSaRecord));

        mLooper.dispatchAll();

        verifyNotifyUserCloseSession(mIkeSessionStateMachine.mChildProcedureOngoing);

        // Verify state machine quit properly
        assertNull(mIkeSessionStateMachine.getCurrentState());

        List<IkePayload> ikePayloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertTrue(ikePayloadList.isEmpty());
    }

    @Test
    public void testRcvRemoteRekeyIkeWhenChildProcedureOngoing() throws Exception {
        setupIdleStateMachine();
        transitionToChildProcedureOngoing();

        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, makeRekeyIkeRequest());

        mLooper.dispatchAll();

        // Since we have forced state machine to transition to ChildProcedureOngoing state without
        // really starting any Child procedure, it should transition to Idle at this time.
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        List<IkePayload> ikePayloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, ikePayloadList.size());
        assertEquals(
                ERROR_TYPE_TEMPORARY_FAILURE,
                ((IkeNotifyPayload) ikePayloadList.get(0)).notifyType);
    }

    @Test
    public void testKillChildSessions() throws Exception {
        setupIdleStateMachine();

        ChildSessionStateMachine childOne = mock(ChildSessionStateMachine.class);
        ChildSessionStateMachine childTwo = mock(ChildSessionStateMachine.class);
        registerChildStateMachine(mock(ChildSessionCallback.class), childOne);
        registerChildStateMachine(mock(ChildSessionCallback.class), childTwo);

        mIkeSessionStateMachine.mCurrentIkeSaRecord = null;

        mIkeSessionStateMachine.quitNow();

        mLooper.dispatchAll();

        verify(childOne).killSession();
        verify(childTwo).killSession();
    }

    private IkeMessage verifyAuthReqAndGetMsg() {
        IkeMessage ikeAuthReqMessage = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = ikeAuthReqMessage.ikeHeader;
        assertEquals(IkeHeader.EXCHANGE_TYPE_IKE_AUTH, ikeHeader.exchangeType);
        assertFalse(ikeHeader.isResponseMsg);
        assertTrue(ikeHeader.fromIkeInitiator);

        return ikeAuthReqMessage;
    }

    private IkeMessage verifyAuthReqWithChildPayloadsAndGetMsg() {
        IkeMessage ikeAuthReqMessage = verifyAuthReqAndGetMsg();

        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_ID_INITIATOR, IkeIdPayload.class));
        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_ID_RESPONDER, IkeIdPayload.class));
        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_SA, IkeSaPayload.class));
        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_TS_INITIATOR, IkeTsPayload.class));
        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_TS_RESPONDER, IkeTsPayload.class));

        IkeConfigPayload configPayload =
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_CP, IkeConfigPayload.class);
        assertNotNull(configPayload);

        Map<Integer, Integer> expectedAttributes =
                Map.of(
                        CONFIG_ATTR_IP4_PCSCF,
                        1,
                        CONFIG_ATTR_IP6_PCSCF,
                        1,
                        CONFIG_ATTR_INTERNAL_IP4_ADDRESS,
                        1,
                        CONFIG_ATTR_INTERNAL_IP6_ADDRESS,
                        1,
                        CONFIG_ATTR_APPLICATION_VERSION,
                        1,
                        CONFIG_ATTR_INTERNAL_IP4_NETMASK,
                        1);
        Map<Integer, Integer> actualAttributes =
                buildAttributeMap(configPayload.recognizedAttributeList);
        assertEquals(expectedAttributes, actualAttributes);

        IkeNoncePayload noncePayload =
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_NONCE, IkeNoncePayload.class);
        assertNull(noncePayload);

        return ikeAuthReqMessage;
    }

    private Map<Integer, Integer> buildAttributeMap(
            List<IkeConfigPayload.ConfigAttribute> recognizedAttributeList) {
        Map<Integer, Integer> attrCountMap = new HashMap<>();
        for (IkeConfigPayload.ConfigAttribute attr : recognizedAttributeList) {
            attrCountMap.compute(attr.attributeType, (key, val) -> (val == null) ? 1 : val + 1);
        }

        return attrCountMap;
    }

    private void verifyDigitalSignatureAuthentication(
            IkeAuthDigitalSignPayload spyAuthPayload,
            X509Certificate certificate,
            IkeIdPayload respIdPayload,
            List<IkePayload> authRelatedPayloads,
            boolean hasChildPayloads,
            boolean hasConfigPayloadInResp)
            throws Exception {
        IkeMessage ikeAuthReqMessage =
                verifyAuthenticationCommonAndGetIkeMessage(
                        respIdPayload,
                        authRelatedPayloads,
                        hasChildPayloads,
                        hasConfigPayloadInResp,
                        false /* isMobikeEnabled */,
                        true /* isIpv4 */,
                        0 /* ike3gppCallbackInvocations */);

        // Calling mSpyCurrentIkeSaRecord.getSkPr() inline will introduce a mockito compile error:
        // InvalidUseOfMatchersException
        byte[] expectedSkPr = mSpyCurrentIkeSaRecord.getSkPr();
        verify(spyAuthPayload)
                .verifyInboundSignature(
                        eq(certificate),
                        any(),
                        eq(mSpyCurrentIkeSaRecord.nonceInitiator),
                        eq(respIdPayload.getEncodedPayloadBody()),
                        eq(mIkeSessionStateMachine.mIkePrf),
                        eq(expectedSkPr));

        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_AUTH, IkeAuthDigitalSignPayload.class));
    }

    private IkeMessage verifySharedKeyAuthentication(
            IkeAuthPskPayload spyAuthPayload,
            IkeIdPayload respIdPayload,
            List<IkePayload> authRelatedPayloads,
            boolean hasChildPayloads,
            boolean hasConfigPayloadInResp)
            throws Exception {
        return verifySharedKeyAuthentication(
                spyAuthPayload,
                respIdPayload,
                authRelatedPayloads,
                hasChildPayloads,
                hasConfigPayloadInResp,
                false /* isMobikeEnabled */,
                true /* isIpv4 */,
                0 /* ike3gppDataListenerInvocations */);
    }

    private IkeMessage verifySharedKeyAuthentication(
            IkeAuthPskPayload spyAuthPayload,
            IkeIdPayload respIdPayload,
            List<IkePayload> authRelatedPayloads,
            boolean hasChildPayloads,
            boolean hasConfigPayloadInResp,
            boolean isMobikeEnabled,
            boolean isIpv4,
            int ike3gppDataListenerInvocations)
            throws Exception {
        IkeMessage ikeAuthReqMessage =
                verifyAuthenticationCommonAndGetIkeMessage(
                        respIdPayload,
                        authRelatedPayloads,
                        hasChildPayloads,
                        hasConfigPayloadInResp,
                        isMobikeEnabled,
                        isIpv4,
                        ike3gppDataListenerInvocations);

        // Calling mSpyCurrentIkeSaRecord.getSkPr() inline will introduce a mockito compile error:
        // InvalidUseOfMatchersException
        byte[] expectedSkPr = mSpyCurrentIkeSaRecord.getSkPr();
        verify(spyAuthPayload)
                .verifyInboundSignature(
                        eq(mPsk),
                        any(),
                        eq(mSpyCurrentIkeSaRecord.nonceInitiator),
                        eq(respIdPayload.getEncodedPayloadBody()),
                        eq(mIkeSessionStateMachine.mIkePrf),
                        eq(expectedSkPr));

        assertNotNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_AUTH, IkeAuthPskPayload.class));

        return ikeAuthReqMessage;
    }

    private IkeMessage verifyAuthenticationCommonAndGetIkeMessage(
            IkeIdPayload respIdPayload,
            List<IkePayload> authRelatedPayloads,
            boolean hasChildPayloads,
            boolean hasConfigPayloadInResp,
            boolean isMobikeEnabled,
            boolean isIpv4,
            int ike3gppDataListenerInvocations)
            throws Exception {
        // Send IKE AUTH response to IKE state machine
        ReceivedIkePacket authResp = makeIkeAuthRespWithChildPayloads(authRelatedPayloads);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, authResp);
        mLooper.dispatchAll();

        // Validate outbound IKE AUTH request
        IkeMessage ikeAuthReqMessage;
        if (hasChildPayloads) {
            ikeAuthReqMessage = verifyAuthReqWithChildPayloadsAndGetMsg();
        } else {
            ikeAuthReqMessage = verifyAuthReqAndGetMsg();
        }

        // Validate that there is no EAP only notify payload
        List<IkeNotifyPayload> notifyPayloads =
                ikeAuthReqMessage.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        assertFalse(hasExpectedNotifyPayload(notifyPayloads, NOTIFY_TYPE_EAP_ONLY_AUTHENTICATION));

        // Validate the N1 Mode Capability payload
        verifyN1ModeCapabilityPayload(notifyPayloads);

        // Validate inbound IKE AUTH response
        verifyIncrementLocaReqMsgId();
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, authResp);

        // Validate that user has been notified. Expect one invocation for
        // IkeSessionCallback#onOpened and 'ike3gppDataListenerInvocations' invocations for
        // Ike3gppDataListener#onIke3gppDataReceived
        verify(mSpyUserCbExecutor, times(1 + ike3gppDataListenerInvocations))
                .execute(any(Runnable.class));

        // Verify IkeSessionConfiguration
        ArgumentCaptor<IkeSessionConfiguration> ikeSessionConfigurationArgumentCaptor =
                ArgumentCaptor.forClass(IkeSessionConfiguration.class);
        verify(mMockIkeSessionCallback).onOpened(ikeSessionConfigurationArgumentCaptor.capture());

        IkeSessionConfiguration sessionConfig = ikeSessionConfigurationArgumentCaptor.getValue();
        assertNotNull(sessionConfig);
        if (hasConfigPayloadInResp) {
            List<InetAddress> pcscfAddressList = sessionConfig.getPcscfServers();
            assertEquals(3, pcscfAddressList.size());
            assertTrue(pcscfAddressList.contains(InetAddress.getByName(PCSCF_IPV6_ADDRESS1)));
            assertTrue(pcscfAddressList.contains(InetAddress.getByName(PCSCF_IPV6_ADDRESS2)));
            assertTrue(pcscfAddressList.contains(InetAddress.getByName(PCSCF_IPV6_ADDRESS3)));
        } else {
            assertTrue(sessionConfig.getPcscfServers().size() == 0);
        }

        assertEquals(
                "" /*expected application version*/, sessionConfig.getRemoteApplicationVersion());
        assertByteArrayListEquals(
                Arrays.asList(REMOTE_VENDOR_ID_ONE, REMOTE_VENDOR_ID_TWO),
                sessionConfig.getRemoteVendorIds());
        assertTrue(
                sessionConfig.isIkeExtensionEnabled(
                        IkeSessionConfiguration.EXTENSION_TYPE_FRAGMENTATION));
        assertEquals(
                isMobikeEnabled,
                sessionConfig.isIkeExtensionEnabled(IkeSessionConfiguration.EXTENSION_TYPE_MOBIKE));

        IkeSessionConnectionInfo ikeConnInfo = sessionConfig.getIkeSessionConnectionInfo();

        InetAddress expectedLocalAddress = isIpv4 ? LOCAL_ADDRESS : LOCAL_ADDRESS_V6;
        InetAddress expectedRemoteAddress = isIpv4 ? REMOTE_ADDRESS : REMOTE_ADDRESS_V6;

        assertEquals(expectedLocalAddress, ikeConnInfo.getLocalAddress());
        assertEquals(expectedRemoteAddress, ikeConnInfo.getRemoteAddress());
        assertEquals(mMockDefaultNetwork, ikeConnInfo.getNetwork());

        // Verify payload list pair for first Child negotiation
        ArgumentCaptor<List<IkePayload>> mReqPayloadListCaptor =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<IkePayload>> mRespPayloadListCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mMockChildSessionStateMachine)
                .handleFirstChildExchange(
                        mReqPayloadListCaptor.capture(),
                        mRespPayloadListCaptor.capture(),
                        eq(expectedLocalAddress),
                        eq(expectedRemoteAddress),
                        any(), // udpEncapSocket
                        eq(mIkeSessionStateMachine.mIkePrf),
                        eq(mIkeSessionStateMachine.mSaProposal.getDhGroupTransforms()[0].id),
                        any()); // sk_d
        List<IkePayload> childReqList = mReqPayloadListCaptor.getValue();
        List<IkePayload> childRespList = mRespPayloadListCaptor.getValue();

        assertTrue(isIkePayloadExist(childReqList, IkePayload.PAYLOAD_TYPE_SA));
        assertTrue(isIkePayloadExist(childReqList, IkePayload.PAYLOAD_TYPE_TS_INITIATOR));
        assertTrue(isIkePayloadExist(childReqList, IkePayload.PAYLOAD_TYPE_TS_RESPONDER));
        assertTrue(isIkePayloadExist(childReqList, IkePayload.PAYLOAD_TYPE_NONCE));
        IkeSaPayload reqSaPayload =
                IkePayload.getPayloadForTypeInProvidedList(
                        IkePayload.PAYLOAD_TYPE_SA, IkeSaPayload.class, childReqList);
        assertFalse(reqSaPayload.isSaResponse);

        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_SA));
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_TS_INITIATOR));
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_TS_RESPONDER));
        assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_NONCE));
        if (hasConfigPayloadInResp) {
            assertTrue(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_CP));
        } else {
            assertFalse(isIkePayloadExist(childRespList, IkePayload.PAYLOAD_TYPE_CP));
        }
        IkeSaPayload respSaPayload =
                IkePayload.getPayloadForTypeInProvidedList(
                        IkePayload.PAYLOAD_TYPE_SA, IkeSaPayload.class, childRespList);
        assertTrue(respSaPayload.isSaResponse);

        // Mock finishing first Child SA negotiation.
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);

        IChildSessionSmCallback cb = verifyMakeChildAndReturnChildSmCb(mMockChildSessionCallback);

        cb.onProcedureFinished(mMockChildSessionStateMachine);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        return ikeAuthReqMessage;
    }

    private void verifyN1ModeCapabilityPayload(List<IkeNotifyPayload> notifyPayloads)
            throws Exception {
        IkeNotifyPayload n1ModeCapabilityPayload = null;
        for (IkeNotifyPayload notifyPayload : notifyPayloads) {
            if (notifyPayload.notifyType == NOTIFY_TYPE_N1_MODE_CAPABILITY) {
                n1ModeCapabilityPayload = notifyPayload;
            }
        }

        // Only expect a N1_MODE_CAPABILITY payload if an Ike3gppExtension and PDU Session ID
        // are specified.
        Ike3gppExtension ike3gppExtension =
                mIkeSessionStateMachine.mIkeSessionParams.getIke3gppExtension();
        if (ike3gppExtension == null || !ike3gppExtension.getIke3gppParams().hasPduSessionId()) {
            assertNull(n1ModeCapabilityPayload);
        } else {
            byte[] expectedNotifyData =
                    TestUtils.hexStringToByteArray(N1_MODE_CAPABILITY_PAYLOAD_DATA);
            assertArrayEquals(expectedNotifyData, n1ModeCapabilityPayload.notifyData);
        }
    }

    private IkeAuthPskPayload makeSpyRespPskPayload() throws Exception {
        IkeAuthPskPayload spyAuthPayload =
                spy(
                        (IkeAuthPskPayload)
                                IkeTestUtils.hexStringToIkePayload(
                                        IkePayload.PAYLOAD_TYPE_AUTH,
                                        true /*isResp*/,
                                        PSK_AUTH_RESP_PAYLOAD_HEX_STRING));

        doNothing()
                .when(spyAuthPayload)
                .verifyInboundSignature(any(), any(), any(), any(), any(), any());
        return spyAuthPayload;
    }

    private IkeAuthDigitalSignPayload makeSpyDigitalSignAuthPayload() throws Exception {
        IkeAuthDigitalSignPayload spyAuthPayload =
                spy(
                        (IkeAuthDigitalSignPayload)
                                IkeTestUtils.hexStringToIkePayload(
                                        IkePayload.PAYLOAD_TYPE_AUTH,
                                        true /*isResp*/,
                                        GENERIC_DIGITAL_SIGN_AUTH_RESP_HEX_STRING));
        doNothing()
                .when(spyAuthPayload)
                .verifyInboundSignature(any(), any(), any(), any(), any(), any());
        return spyAuthPayload;
    }

    private IkeIdPayload makeRespIdPayload() {
        return makeRespIdPayload(REMOTE_ID_FQDN);
    }

    private IkeNotifyPayload makeDeviceIdentityPayloadFromNetwork() {
        return new IkeNotifyPayload(
                NOTIFY_TYPE_DEVICE_IDENTITY, HexDump.hexStringToByteArray("01"));
    }

    private IkeIdPayload makeRespIdPayload(IkeIdentification ikeId) {
        return new IkeIdPayload(false /* isInitiator */, ikeId);
    }

    private void verifyEmptyInformationalSent(int count, boolean expectedResp) {
        verify(mMockIkeMessageHelper, times(count))
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        eq(mSpyCurrentIkeSaRecord),
                        argThat(
                                msg -> {
                                    return msg.ikePayloadList.isEmpty()
                                            && msg.ikeHeader.isResponseMsg == expectedResp
                                            && msg.ikeHeader.fromIkeInitiator
                                            && msg.ikeHeader.exchangeType
                                                    == IkeHeader.EXCHANGE_TYPE_INFORMATIONAL;
                                }),
                        anyBoolean(),
                        anyInt());
    }

    @Test
    public void testCreateIkeLocalIkeAuthDefersOtherMessages() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with Auth-PSK, ID-Responder and config payloads.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        ReceivedIkePacket req =
                makeDummyEncryptedReceivedIkePacket(
                        mSpyCurrentIkeSaRecord,
                        IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                        false,
                        Collections.emptyList(),
                        Collections.emptyList());
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, req);

        verifyEmptyInformationalSent(0, true /* expcetedResp*/);

        // Send IKE AUTH response to IKE state machine to trigger moving to next state
        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);
        authRelatedPayloads.add(makeConfigPayload());

        ReceivedIkePacket authResp = makeIkeAuthRespWithChildPayloads(authRelatedPayloads);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, authResp);
        mLooper.dispatchAll();

        verifyEmptyInformationalSent(1, true /* expcetedResp*/);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
    }

    private void verifyCreateIkeLocalIkeAuthDigitalSignature(Set<Short> peerSignatureHashAlgorithms)
            throws Exception {
        // Quit and restart IKE Session with Digital Signature Auth params
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsDigitalSignature());

        new IkeFirstAuthTestPretest(peerSignatureHashAlgorithms)
                .mockIkeInitAndTransitionToIkeAuth();

        // Build IKE AUTH response with Digital Signature Auth, ID-Responder and config payloads.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthDigitalSignPayload spyAuthPayload = makeSpyDigitalSignAuthPayload();
        authRelatedPayloads.add(spyAuthPayload);
        authRelatedPayloads.add(new IkeCertX509CertPayload(mServerEndCertificate));

        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);
        authRelatedPayloads.add(makeConfigPayload());

        verifyDigitalSignatureAuthentication(
                spyAuthPayload,
                mServerEndCertificate,
                respIdPayload,
                authRelatedPayloads,
                true /*hasChildPayloads*/,
                true /*hasConfigPayloadInResp*/);
        verifyRetransmissionStopped();
    }

    @Test
    public void testCreateIkeLocalIkeAuthRsaDigitalSignature() throws Exception {
        verifyCreateIkeLocalIkeAuthDigitalSignature(new HashSet<>());
    }

    @Test
    public void testCreateIkeLocalIkeAuthGenericDigitalSignature() throws Exception {
        Set<Short> hashAlgos = new HashSet<Short>();
        for (short algo : IkeAuthDigitalSignPayload.ALL_SIGNATURE_ALGO_TYPES) {
            hashAlgos.add(algo);
        }
        verifyCreateIkeLocalIkeAuthDigitalSignature(hashAlgos);
    }

    @Test
    public void testCreateIkeLocalIkeAuthPsk() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with Auth-PSK, ID-Responder and config payloads.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);
        authRelatedPayloads.add(makeConfigPayload());

        verifySharedKeyAuthentication(
                spyAuthPayload,
                respIdPayload,
                authRelatedPayloads,
                true /*hasChildPayloads*/,
                true /*hasConfigPayloadInResp*/);
        verifyRetransmissionStopped();
    }

    @Test
    public void testCreateIkeLocalIkeAuthDigitalSignatureIdMismatch() throws Exception {
        // Quit and restart IKE Session with Digital Signature Auth params
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsDigitalSignature());
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        resetMockIkeMessageHelper();

        // Build IKE AUTH response with Digital Signature Auth, ID-Responder and config payloads.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthDigitalSignPayload spyAuthPayload = makeSpyDigitalSignAuthPayload();
        authRelatedPayloads.add(spyAuthPayload);
        authRelatedPayloads.add(new IkeCertX509CertPayload(mServerEndCertificate));

        authRelatedPayloads.add(makeRespIdPayload(REMOTE_ID_IPV4));

        sendAuthFailRespAndVerifyCloseIke(
                mIkeSessionStateMachine.mCreateIkeLocalIkeAuth,
                makeIkeAuthRespWithChildPayloads(authRelatedPayloads));
    }

    @Test
    public void testCreateIkeLocalIkeAuthPskVerifyFail() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Build IKE AUTH response with invalid Auth-PSK Payload and ID-Responder Payload.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        doThrow(new AuthenticationFailedException("DummyAuthFailException"))
                .when(spyAuthPayload)
                .verifyInboundSignature(any(), any(), any(), any(), any(), any());
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);

        sendAuthFailRespAndVerifyCloseIke(
                mIkeSessionStateMachine.mCreateIkeLocalIkeAuth,
                makeIkeAuthRespWithChildPayloads(authRelatedPayloads));
    }

    private void sendAuthFailRespAndVerifyCloseIke(IState state, ReceivedIkePacket authFailResp)
            throws Exception {
        // Send response to IKE state machine
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, authFailResp);
        mLooper.dispatchAll();

        // Verify Delete request was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(false /*isResp*/);
        assertEquals(1, payloads.size());
        assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, payloads.get(0).payloadType);

        // Verify IKE Session was closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback)
                .onClosedWithException(any(AuthenticationFailedException.class));

        verifyIkeMetricsLogged(
                getStateCode(state), IkeMetrics.IKE_ERROR_PROTOCOL_AUTHENTICATION_FAILED);
    }

    @Test
    public void testAuthHandlesIkeErrorNotify() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Mock rejecting IKE AUTH with Authentication Failure notification
        ReceivedIkePacket mockAuthFailPacket =
                makeIkeAuthRespWithoutChildPayloads(
                        Arrays.asList(new IkeNotifyPayload(ERROR_TYPE_AUTHENTICATION_FAILED)));
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockAuthFailPacket);
        mLooper.dispatchAll();

        // Verify Delete request was never sent
        verifyEncryptAndEncodeNeverCalled();

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback)
                .onClosedWithException(any(AuthenticationFailedException.class));
    }

    @Test
    public void testAuthHandlesCreateChildErrorNotify() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Mock rejecting IKE AUTH with a Create Child error notification
        ReceivedIkePacket mockAuthFailPacket =
                makeIkeAuthRespWithoutChildPayloads(
                        Arrays.asList(new IkeNotifyPayload(ERROR_TYPE_INTERNAL_ADDRESS_FAILURE)));
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockAuthFailPacket);
        mLooper.dispatchAll();

        // Verify Delete request was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(false /*isResp*/);
        assertEquals(1, payloads.size());
        assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, payloads.get(0).payloadType);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());

        ArgumentCaptor<IkeProtocolException> captor =
                ArgumentCaptor.forClass(IkeProtocolException.class);
        verify(mMockIkeSessionCallback).onClosedWithException(captor.capture());
        IkeProtocolException exception = captor.getValue();
        assertEquals(ERROR_TYPE_INTERNAL_ADDRESS_FAILURE, exception.getErrorType());
    }

    @Test
    public void testAuthPskHandleRespWithParsingError() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Mock receiving packet with syntax error
        ReceivedIkePacket mockInvalidPacket =
                makeDummyReceivedIkePacketWithInvalidSyntax(
                        mSpyCurrentIkeSaRecord, true /*isResp*/, IkeHeader.EXCHANGE_TYPE_IKE_AUTH);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockInvalidPacket);
        mLooper.dispatchAll();

        // Verify Delete request was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(false /*isResp*/);
        assertEquals(1, payloads.size());
        assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, payloads.get(0).payloadType);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
    }

    @Test
    public void testAuthWithOptionAcceptAnyRemoteId() throws Exception {
        mIkeSessionStateMachine.quitNow();

        IkeSessionParams ikeSessionParams =
                buildIkeSessionParamsWithIkeOptions(
                        IkeSessionParams.IKE_OPTION_ACCEPT_ANY_REMOTE_ID);
        mIkeSessionStateMachine = makeAndStartIkeSession(ikeSessionParams);

        // Mock IKE INIT
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with Auth-PSK Payload and ID-Responder Payload that is different
        // from configured ID-Responder.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload(REMOTE_ID_IPV4);
        authRelatedPayloads.add(respIdPayload);

        // Send response to IKE state machine and verify authentication is done.
        verifySharedKeyAuthentication(
                spyAuthPayload,
                respIdPayload,
                authRelatedPayloads,
                true /*hasChildPayloads*/,
                false /*hasConfigPayloadInResp*/);
        verifyRetransmissionStopped();
    }

    @Test
    public void testAuthRejectOtherResponderId() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with Auth-PSK Payload and ID-Responder Payload that is different
        // from configured ID-Responder.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload(REMOTE_ID_IPV4);
        authRelatedPayloads.add(respIdPayload);

        // Send response to IKE state machine
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeIkeAuthRespWithChildPayloads(authRelatedPayloads));
        mLooper.dispatchAll();

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback)
                .onClosedWithException(any(AuthenticationFailedException.class));
    }

    @Test
    public void testCreateIkeLocalIkeAuthPreEap() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Mock IKE INIT
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with EAP. Auth, ID-Resp and Cert payloads.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();

        authRelatedPayloads.add(new IkeEapPayload(EAP_DUMMY_MSG));
        authRelatedPayloads.add(makeSpyDigitalSignAuthPayload());
        authRelatedPayloads.add(makeRespIdPayload());

        IkeCertX509CertPayload certPayload = new IkeCertX509CertPayload(mServerEndCertificate);
        authRelatedPayloads.add(certPayload);

        // Send IKE AUTH response to IKE state machine
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeIkeAuthRespWithoutChildPayloads(authRelatedPayloads));
        mLooper.dispatchAll();

        // Validate outbound IKE AUTH request
        IkeMessage ikeAuthReqMessage = verifyAuthReqWithChildPayloadsAndGetMsg();
        assertNull(
                ikeAuthReqMessage.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_AUTH, IkeAuthPayload.class));

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuthInEap);
        verifyRetransmissionStopped();

        IkeAuthData setupData =
                ((CreateIkeLocalIkeAuthInEap) mIkeSessionStateMachine.mCreateIkeLocalIkeAuthInEap)
                        .mSetupData;
        assertNotNull(setupData.initIdPayload);
        assertNotNull(setupData.respIdPayload);
    }

    private IEapCallback verifyEapAuthenticatorCreatedAndGetCallback(
            EapSessionConfig eapSessionConfig) {
        ArgumentCaptor<IkeContext> ikeContextCaptor = ArgumentCaptor.forClass(IkeContext.class);
        ArgumentCaptor<IEapCallback> eapCbCaptor = ArgumentCaptor.forClass(IEapCallback.class);

        verify(mSpyDeps)
                .newEapAuthenticator(
                        ikeContextCaptor.capture(), eapCbCaptor.capture(), eq(eapSessionConfig));

        IkeContext ikeContext = ikeContextCaptor.getValue();
        assertEquals(mIkeSessionStateMachine.getHandler().getLooper(), ikeContext.getLooper());
        assertEquals(mSpyContext, ikeContext.getContext());

        return eapCbCaptor.getValue();
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapStartsAuthenticatorAndProxiesMessage()
            throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Setup state and go to IN_EAP state
        new IkeAuthInEapTestPretest().mockIkeInitAndTransitionToIkeAuth();
        mLooper.dispatchAll();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EAP_START_EAP_AUTH, new IkeEapPayload(EAP_DUMMY_MSG));
        mLooper.dispatchAll();

        verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfig);

        verify(mMockEapAuthenticator).processEapMessage(eq(EAP_DUMMY_MSG));
    }

    @Test
    public void testInformationalResponseWithDeviceIdentity() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine =
                makeAndStartIkeSession(buildIkeSessionParamsEapAkaWithDeviceIdentity());
        setIkeInitResults();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
        verifyRcvDeviceIdentityCheckReqAndReply();
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapOutboundResponseIncludesDeviceIdentity()
            throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine =
                makeAndStartIkeSession(buildIkeSessionParamsEapAkaWithDeviceIdentity());

        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with EAP. Auth, ID-Resp and device identity
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        authRelatedPayloads.add(new IkeEapPayload(EAP_DUMMY_MSG));
        authRelatedPayloads.add(makeRespIdPayload());
        authRelatedPayloads.add(makeDeviceIdentityPayloadFromNetwork());

        // Send IKE AUTH response to IKE state machine
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeIkeAuthRespWithoutChildPayloads(authRelatedPayloads));
        mLooper.dispatchAll();

        IEapCallback callback =
                verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfigEapAka);
        callback.onResponse(
                EAP_DUMMY_MSG, EAP_RESPONSE_FLAG_MASK_WITH_EAP_AKA_SERVER_AUTHENTICATED);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        verify(mMockIkeMessageHelper, times(2))
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        eq(mSpyCurrentIkeSaRecord),
                        mIkeMessageCaptor.capture(),
                        anyBoolean(),
                        anyInt());
        IkeMessage ikeAuthReqMessage = mIkeMessageCaptor.getValue();

        // Validate the DEVICE_IDENTITY payload and the content sent to the network
        List<IkeNotifyPayload> notifyPayloads =
                ikeAuthReqMessage.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        IkeNotifyPayload deviceIdentityPayload = null;
        for (IkeNotifyPayload notifyPayload : notifyPayloads) {
            if (notifyPayload.notifyType == NOTIFY_TYPE_DEVICE_IDENTITY) {
                deviceIdentityPayload = notifyPayload;
            }
        }
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEI, deviceIdentityPayload.notifyData);

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuthInEap);
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapHandlesOutboundResponse() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Setup state and go to IN_EAP state
        new IkeAuthInEapTestPretest().mockIkeInitAndTransitionToIkeAuth();
        mLooper.dispatchAll();

        IEapCallback callback = verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfig);
        callback.onResponse(EAP_DUMMY_MSG, EAP_RESPONSE_FLAGS_NOT_SET);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Verify EAP response
        IkeMessage resp = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);
        IkeHeader ikeHeader = resp.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_IKE_AUTH, ikeHeader.exchangeType);
        assertFalse(ikeHeader.isResponseMsg);
        assertEquals(mSpyCurrentIkeSaRecord.isLocalInit, ikeHeader.fromIkeInitiator);

        assertEquals(1, resp.ikePayloadList.size());
        assertArrayEquals(EAP_DUMMY_MSG, ((IkeEapPayload) resp.ikePayloadList.get(0)).eapMessage);
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapHandlesMissingEapPacket() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Setup state and go to IN_EAP state
        new IkeAuthInEapTestPretest().mockIkeInitAndTransitionToIkeAuth();
        mLooper.dispatchAll();

        // Mock sending IKE_AUTH{EAP} request
        IEapCallback callback = verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfig);
        callback.onResponse(EAP_DUMMY_MSG, EAP_RESPONSE_FLAGS_NOT_SET);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Send IKE AUTH response with no EAP Payload to IKE state machine
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeIkeAuthRespWithoutChildPayloads(new ArrayList<>()));
        mLooper.dispatchAll();

        // Verify state machine quit properly
        verify(mMockIkeSessionCallback)
                .onClosedWithException(any(AuthenticationFailedException.class));
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapHandlesSuccess() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Setup state and go to IN_EAP state
        new IkeAuthInEapTestPretest().mockIkeInitAndTransitionToIkeAuth();
        mLooper.dispatchAll();

        IEapCallback callback = verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfig);

        EapAkaInfo eapInfo =
                new EapAkaInfo.Builder().setReauthId(REAUTH_ID_BYTES).build();

        callback.onSuccess(mPsk, new byte[0], eapInfo); // use mPsk as MSK, eMSK does not matter
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuthPostEap);
        assertEquals(mIkeSessionStateMachine.mCreateIkeLocalIkeAuthPostEap.mEapInfo, eapInfo);
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapHandlesError() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Setup state and go to IN_EAP state
        new IkeAuthInEapTestPretest().mockIkeInitAndTransitionToIkeAuth();
        mLooper.dispatchAll();

        IEapCallback callback = verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfig);

        Throwable error = new IllegalArgumentException();
        callback.onError(error);
        mLooper.dispatchAll();

        // Fires user error callbacks
        verify(mMockIkeSessionCallback)
                .onClosedWithException(argThat(err -> err.getCause() == error));

        // Verify state machine quit properly
        verify(mSpyCurrentIkeSaRecord).close();
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testCreateIkeLocalIkeAuthInEapHandlesFailure() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(buildIkeSessionParamsEap());

        // Setup state and go to IN_EAP state
        new IkeAuthInEapTestPretest().mockIkeInitAndTransitionToIkeAuth();
        mLooper.dispatchAll();

        IEapCallback callback = verifyEapAuthenticatorCreatedAndGetCallback(mEapSessionConfig);
        callback.onFail();
        mLooper.dispatchAll();

        // Fires user error callbacks
        verify(mMockIkeSessionCallback)
                .onClosedWithException(any(AuthenticationFailedException.class));

        // Verify state machine quit properly
        verify(mSpyCurrentIkeSaRecord).close();
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testCreateIkeLocalIkeAuthPostEap() throws Exception {
        verifyCreateIkeLocalIkeAuthPostEap(
                buildIkeSessionParamsEap(),
                new ArrayList<>() /* authRelatedPayloads */,
                false /* isMobikeEnabled */);
    }

    private void verifyCreateIkeLocalIkeAuthPostEap(
            IkeSessionParams params, List<IkePayload> authRelatedPayloads, boolean isMobikeEnabled)
            throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(params);

        // Setup state and go to IN_EAP state
        IkeIdPayload initIdPayload = mock(IkeIdPayload.class);
        doReturn(new byte[0]).when(initIdPayload).getEncodedPayloadBody();
        IkeIdPayload respIdPayload = makeRespIdPayload();
        new IkeAuthPostEapTestPretest(initIdPayload, respIdPayload)
                .mockIkeInitAndTransitionToIkeAuth();

        EapAkaInfo eapInfo =
                new EapAkaInfo.Builder().setReauthId(REAUTH_ID_BYTES).build();
        mIkeSessionStateMachine.mCreateIkeLocalIkeAuthPostEap.setEapInfo(eapInfo);
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_EAP_FINISH_EAP_AUTH, mPsk);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Build IKE AUTH response with Auth-PSK Payload and ID-Responder Payload.
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        verifySharedKeyAuthentication(
                spyAuthPayload,
                respIdPayload,
                authRelatedPayloads,
                false /*hasChildPayloads*/,
                false /*hasConfigPayloadInResp*/,
                isMobikeEnabled,
                true /* isIpv4 */,
                0 /* ike3gppDataListenerInvocations */);
        verifyRetransmissionStopped();

        ArgumentCaptor<IkeSessionConfiguration> ikeSessionConfigurationArgumentCaptor =
                ArgumentCaptor.forClass(IkeSessionConfiguration.class);
        verify(mMockIkeSessionCallback).onOpened(ikeSessionConfigurationArgumentCaptor.capture());

        IkeSessionConfiguration sessionConfig = ikeSessionConfigurationArgumentCaptor.getValue();
        assertNotNull(sessionConfig);
        assertArrayEquals(REAUTH_ID_BYTES, ((EapAkaInfo) sessionConfig.getEapInfo()).getReauthId());
    }

    @Test
    public void testCreateIkeLocalIkeAuthHandlesFirstFrag() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Received IKE fragment
        byte[] unencryptedData = "testCreateIkeLocalIkeAuthHandlesFrag".getBytes();
        int fragNum = 1;
        int totalFragments = 2;
        IkeSkfPayload skfPayload =
                IkeTestUtils.makeDummySkfPayload(unencryptedData, fragNum, totalFragments);

        ReceivedIkePacket packet =
                makeDummyReceivedIkeFragmentPacket(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_IKE_AUTH,
                        skfPayload,
                        PAYLOAD_TYPE_AUTH,
                        null /* collectedFrags*/);
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, packet);
        mLooper.dispatchAll();

        // Verify state doesn't change
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuth);

        // Verify the IkeSaRecord has stored the fragment.
        DecodeResultPartial resultPartial =
                mSpyCurrentIkeSaRecord.getCollectedFragments(true /*isResp*/);
        assertEquals(PAYLOAD_TYPE_AUTH, resultPartial.firstPayloadType);
        assertEquals(totalFragments, resultPartial.collectedFragsList.length);
        assertArrayEquals(unencryptedData, resultPartial.collectedFragsList[fragNum - 1]);
        assertFalse(resultPartial.isAllFragmentsReceived());

        assertNull(mSpyCurrentIkeSaRecord.getCollectedFragments(false /*isResp*/));
    }

    @Test
    public void testCreateIkeLocalIkeAuthHandlesLastFragOk() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Set previously collected IKE fragments
        DecodeResultPartial mockCollectedFrags = mock(DecodeResultPartial.class);
        mSpyCurrentIkeSaRecord.updateCollectedFragments(mockCollectedFrags, true /*isResp*/);

        // Build reassembled IKE AUTH response with Auth-PSK Payload and ID-Responder Payload.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);

        List<IkePayload> authPayloadList =
                getIkeAuthPayloadListWithChildPayloads(authRelatedPayloads);

        // Receive last auth response and do IKE_AUTH
        ReceivedIkePacket packet =
                makeDummyReceivedLastIkeFragmentPacketOk(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_IKE_AUTH,
                        mockCollectedFrags,
                        authPayloadList,
                        "FirstFrag".getBytes());
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, packet);
        mLooper.dispatchAll();

        // Verify IKE AUTH is done
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);

        // Verify collected response fragments are cleared.
        assertNull(mSpyCurrentIkeSaRecord.getCollectedFragments(true /*isResp*/));
        verify(mSpyCurrentIkeSaRecord).resetCollectedFragments(true /*isResp*/);
    }

    @Test
    public void testCreateIkeLocalIkeAuthHandlesLastFragError() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Set previously collected IKE fragments
        DecodeResultPartial mockCollectedFrags = mock(DecodeResultPartial.class);
        mSpyCurrentIkeSaRecord.updateCollectedFragments(mockCollectedFrags, true /*isResp*/);

        // Receive last auth response with syntax error
        ReceivedIkePacket packet =
                makeDummyReceivedLastIkeFragmentPacketError(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_IKE_AUTH,
                        mockCollectedFrags,
                        new InvalidSyntaxException("IkeStateMachineTest"));
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, packet);
        mLooper.dispatchAll();

        // Verify Delete request is sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(false /*isResp*/);
        assertEquals(1, payloads.size());
        assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, payloads.get(0).payloadType);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));

        // Collected response fragments are cleared
        assertNull(mSpyCurrentIkeSaRecord.getCollectedFragments(true /*isResp*/));
        verify(mSpyCurrentIkeSaRecord).resetCollectedFragments(true /*isResp*/);
    }

    @Test
    public void testRekeyIkeLocalCreateSendsRequest() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
        verifyRetransmissionStarted();

        // Verify outbound message
        IkeMessage rekeyMsg = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = rekeyMsg.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA, ikeHeader.exchangeType);
        assertEquals(mSpyCurrentIkeSaRecord.getLocalRequestMessageId(), ikeHeader.messageId);
        assertFalse(ikeHeader.isResponseMsg);
        assertTrue(ikeHeader.fromIkeInitiator);

        // Verify SA payload & proposals
        IkeSaPayload saPayload =
                rekeyMsg.getPayloadForType(IkePayload.PAYLOAD_TYPE_SA, IkeSaPayload.class);
        assertFalse(saPayload.isSaResponse);
        assertEquals(1, saPayload.proposalList.size());

        IkeSaPayload.IkeProposal proposal =
                (IkeSaPayload.IkeProposal) saPayload.proposalList.get(0);
        assertEquals(1, proposal.number); // Must be 1-indexed
        assertEquals(IkePayload.PROTOCOL_ID_IKE, proposal.protocolId);
        assertEquals(IkePayload.SPI_LEN_IKE, proposal.spiSize);
        assertEquals(mIkeSessionStateMachine.mSaProposal, proposal.saProposal);

        // Verify Nonce and KE payloads exist.
        assertNotNull(
                rekeyMsg.getPayloadForType(IkePayload.PAYLOAD_TYPE_NONCE, IkeNoncePayload.class));

        IkeKePayload kePayload =
                rekeyMsg.getPayloadForType(IkePayload.PAYLOAD_TYPE_KE, IkeKePayload.class);
        assertNotNull(kePayload);
        assertTrue(kePayload.isOutbound);
    }

    @Test
    public void testRekeyIkeLocalCreateHandlesResponse() throws Exception {
        setupIdleStateMachine();

        verifyRekeyIkeLocalCreateHandlesResponse();
    }

    private void verifyRekeyIkeLocalCreateHandlesResponse() throws Exception {
        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Prepare "rekeyed" SA
        setupRekeyedIkeSa(mSpyLocalInitIkeSaRecord);

        // Receive Rekey response
        ReceivedIkePacket dummyRekeyIkeRespReceivedPacket = makeRekeyIkeResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRespReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementLocaReqMsgId();
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyRekeyIkeRespReceivedPacket);

        // Verify in delete state, and new SA record was saved:
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalDelete);
        verifyRetransmissionStarted();
        assertEquals(mSpyLocalInitIkeSaRecord, mIkeSessionStateMachine.mLocalInitNewIkeSaRecord);
        verify(mSpyIkeConnectionCtrl.getIkeSocket())
                .registerIke(eq(mSpyLocalInitIkeSaRecord.getLocalSpi()), eq(mSpyIkeConnectionCtrl));
    }

    @Test
    public void testRekeyIkeLocalCreateHandleRespWithParsingError() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Mock receiving packet with syntax error
        ReceivedIkePacket mockInvalidPacket =
                makeDummyReceivedIkePacketWithInvalidSyntax(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockInvalidPacket);
        mLooper.dispatchAll();

        // Verify Delete request was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(false /*isResp*/);
        assertEquals(1, payloads.size());
        assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, payloads.get(0).payloadType);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
    }

    @Test
    public void testRekeyIkeLocalCreateHandleRespWithNonFatalErrorNotify() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();

        // Mock receiving packet with NO_PROPOSAL_CHOSEN
        ReceivedIkePacket resp =
                makeResponseWithErrorNotify(new IkeNotifyPayload(ERROR_TYPE_NO_PROPOSAL_CHOSEN));
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        // Verify IKE Session goes back to Idle and retry is scheduled
        verify(mSpyCurrentIkeSaRecord).rescheduleRekey(eq(RETRY_INTERVAL_MS));
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testRekeyIkeLocalCreateHandleRespWithFatalErrorNotify() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        resetMockIkeMessageHelper();

        // Mock receiving packet with NO_PROPOSAL_CHOSEN
        ReceivedIkePacket resp =
                makeResponseWithErrorNotify(new IkeNotifyPayload(ERROR_TYPE_INVALID_SYNTAX));
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        // Verify no message was sent because a fatal error notification was received
        verifyEncryptAndEncodeNeverCalled(mSpyCurrentIkeSaRecord);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
    }

    @Test
    public void testRekeyIkeLocalCreateSaCreationFail() throws Exception {
        // Throw error when building new IKE SA
        throwExceptionWhenMakeRekeyIkeSa(
                new GeneralSecurityException("testRekeyIkeLocalCreateSaCreationFail"));

        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        resetMockIkeMessageHelper();

        // Receive Rekey response
        ReceivedIkePacket dummyRekeyIkeRespReceivedPacket = makeRekeyIkeResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRespReceivedPacket);
        mLooper.dispatchAll();

        // Verify Delete request was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(false /*isResp*/);
        assertEquals(1, payloads.size());
        assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, payloads.get(0).payloadType);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(IkeInternalException.class));
    }

    @Test
    public void testRekeyIkeLocalCreateHandleReqWithNonFatalError() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Build protocol exception
        List<Integer> unsupportedPayloads = new ArrayList<>();
        unsupportedPayloads.add(PAYLOAD_TYPE_UNSUPPORTED);
        UnsupportedCriticalPayloadException exception =
                new UnsupportedCriticalPayloadException(unsupportedPayloads);

        // Mock receiving packet with unsupported critical payload
        ReceivedIkePacket mockInvalidPacket =
                makeDummyReceivedIkePacketWithDecodingError(
                        mSpyCurrentIkeSaRecord,
                        false /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                        exception);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockInvalidPacket);
        mLooper.dispatchAll();

        // Verify error notification was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, payloads.size());

        IkePayload payload = payloads.get(0);
        assertEquals(IkePayload.PAYLOAD_TYPE_NOTIFY, payload.payloadType);
        assertEquals(
                ERROR_TYPE_UNSUPPORTED_CRITICAL_PAYLOAD, ((IkeNotifyPayload) payload).notifyType);

        // Verify IKE Session stays in the same state
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
    }

    private void mockRescheduleRekey(IkeSaRecord spySaRecord) {
        IkeLocalRequest rekeyReq =
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE);
        doAnswer(
                (invocation) -> {
                        mIkeSessionStateMachine.sendMessageDelayed(
                                IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE,
                                rekeyReq,
                                RETRY_INTERVAL_MS);
                        return null;
                })
                .when(spySaRecord)
                .rescheduleRekey(eq(RETRY_INTERVAL_MS));
    }

    @Test
    public void testRekeyIkeLocalCreateHandleRespWithTempFailure() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();

        // Mock sending TEMPORARY_FAILURE response
        mockRcvTempFail();
        mLooper.dispatchAll();

        verify(mSpyCurrentIkeSaRecord).rescheduleRekey(eq(RETRY_INTERVAL_MS));
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    private void mockCreateAndTransitionToRekeyDeleteLocal() {
        // Seed fake rekey data and force transition to RekeyIkeLocalDelete
        mIkeSessionStateMachine.mLocalInitNewIkeSaRecord = mSpyLocalInitIkeSaRecord;
        mIkeSessionStateMachine.addIkeSaRecord(mSpyLocalInitIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeLocalDelete);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();
    }

    @Test
    public void testRekeyIkeLocalDeleteSendsRequest() throws Exception {
        setupIdleStateMachine();
        mockCreateAndTransitionToRekeyDeleteLocal();

        // Verify Rekey-Delete request
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalDelete);
        verifyRetransmissionStarted();

        // Verify outbound message
        IkeMessage delMsg = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = delMsg.ikeHeader;
        assertEquals(mSpyCurrentIkeSaRecord.getInitiatorSpi(), ikeHeader.ikeInitiatorSpi);
        assertEquals(mSpyCurrentIkeSaRecord.getResponderSpi(), ikeHeader.ikeResponderSpi);
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, ikeHeader.exchangeType);
        assertEquals(mSpyCurrentIkeSaRecord.isLocalInit, ikeHeader.fromIkeInitiator);
        assertFalse(ikeHeader.isResponseMsg);

        List<IkeDeletePayload> deletePayloadList =
                delMsg.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_DELETE, IkeDeletePayload.class);
        assertEquals(1, deletePayloadList.size());

        IkeDeletePayload deletePayload = deletePayloadList.get(0);
        assertEquals(IkePayload.PROTOCOL_ID_IKE, deletePayload.protocolId);
        assertEquals(0, deletePayload.numSpi);
        assertEquals(0, deletePayload.spiSize);
        assertArrayEquals(new int[0], deletePayload.spisToDelete);
    }

    private void verifyRekeyReplaceSa(IkeSaRecord newSaRecord) {
        verify(mSpyCurrentIkeSaRecord).close();
        verify(mMockCurrentIkeSocket).unregisterIke(eq(mSpyCurrentIkeSaRecord.getLocalSpi()));
        verify(mMockCurrentIkeSocket, never()).unregisterIke(eq(newSaRecord.getLocalSpi()));

        assertEquals(mIkeSessionStateMachine.mCurrentIkeSaRecord, newSaRecord);

        verify(mMockChildSessionStateMachine).setSkD(newSaRecord.getSkD());
    }

    @Test
    public void testRekeyIkeLocalDeleteHandlesResponse() throws Exception {
        setupIdleStateMachine();
        mockCreateAndTransitionToRekeyDeleteLocal();

        // Receive Delete response
        ReceivedIkePacket dummyDeleteIkeRespReceivedPacket =
                makeDeleteIkeResponse(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDeleteIkeRespReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementLocaReqMsgId();
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyDeleteIkeRespReceivedPacket);

        // Verify final state - Idle, with new SA, and old SA closed.
        verifyRekeyReplaceSa(mSpyLocalInitIkeSaRecord);
        verify(mMockIkeSessionCallback, never()).onClosed();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
        verifyRetransmissionStopped();
    }

    @Ignore
    public void disableTestRekeyIkeLocalDeleteHandlesRespWithParsingError() throws Exception {
        setupIdleStateMachine();
        mockCreateAndTransitionToRekeyDeleteLocal();
        resetMockIkeMessageHelper();

        // Mock receiving packet with syntax error
        ReceivedIkePacket mockInvalidPacket =
                makeDummyReceivedIkePacketWithInvalidSyntax(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_INFORMATIONAL);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockInvalidPacket);
        mLooper.dispatchAll();

        // Verify no more request out
        verifyEncryptAndEncodeNeverCalled(mSpyCurrentIkeSaRecord);

        // Verify final state - Idle, with new SA, and old SA closed.
        verifyRekeyReplaceSa(mSpyLocalInitIkeSaRecord);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
        verifyRetransmissionStopped();
    }

    @Test
    public void testRekeyIkeLocalDeleteWithRequestOnNewSa() throws Exception {
        setupIdleStateMachine();
        mockCreateAndTransitionToRekeyDeleteLocal();

        // Receive an empty (DPD) request on the new IKE SA
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDpdIkeRequest(mSpyLocalInitIkeSaRecord));
        mLooper.dispatchAll();

        // Verify final state - Idle, with new SA, and old SA closed.
        verifyRekeyReplaceSa(mSpyLocalInitIkeSaRecord);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
        verifyRetransmissionStopped();
    }

    @Test
    public void testRekeyIkeLocalDeleteWithRequestFragOnNewSa() throws Exception {
        setupIdleStateMachine();
        mockCreateAndTransitionToRekeyDeleteLocal();

        // Received IKE fragment
        byte[] unencryptedData = "testRekeyIkeLocalDeleteWithRequestFragOnNewSa".getBytes();
        int fragNum = 1;
        int totalFragments = 2;
        IkeSkfPayload skfPayload =
                IkeTestUtils.makeDummySkfPayload(unencryptedData, fragNum, totalFragments);

        ReceivedIkePacket packet =
                makeDummyReceivedIkeFragmentPacket(
                        mSpyLocalInitIkeSaRecord,
                        false /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                        skfPayload,
                        PAYLOAD_TYPE_SA,
                        null /* collectedFrags*/);
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, packet);
        mLooper.dispatchAll();

        // Verify rekey is done.
        verifyRekeyReplaceSa(mSpyLocalInitIkeSaRecord);
        verifyRetransmissionStopped();

        // Verify the IkeSaRecord has stored the new fragment.
        DecodeResultPartial resultPartial =
                mSpyLocalInitIkeSaRecord.getCollectedFragments(false /*isResp*/);
        assertEquals(PAYLOAD_TYPE_SA, resultPartial.firstPayloadType);
        assertEquals(totalFragments, resultPartial.collectedFragsList.length);
        assertArrayEquals(unencryptedData, resultPartial.collectedFragsList[fragNum - 1]);
        assertFalse(resultPartial.isAllFragmentsReceived());
    }

    @Test
    public void testRekeyIkeRemoteDeleteWithRequestOnNewSa() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyIkeRemoteDelete
        mIkeSessionStateMachine.mRemoteInitNewIkeSaRecord = mSpyRemoteInitIkeSaRecord;
        mIkeSessionStateMachine.addIkeSaRecord(mSpyRemoteInitIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        // Receive an empty (DPD) request on the new IKE SA
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDpdIkeRequest(mSpyRemoteInitIkeSaRecord));
        mLooper.dispatchAll();

        // Verify final state - Idle, with new SA, and old SA closed.
        verifyRekeyReplaceSa(mSpyRemoteInitIkeSaRecord);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testRekeyIkeRemoteCreate() throws Exception {
        setupIdleStateMachine();

        setupRekeyedIkeSa(mSpyRemoteInitIkeSaRecord);

        // Receive Rekey request
        ReceivedIkePacket dummyRekeyIkeRequestReceivedPacket = makeRekeyIkeRequest();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRequestReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementRemoteReqMsgId();
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyRekeyIkeRequestReceivedPacket);

        // Verify SA created with correct parameters
        ArgumentCaptor<SaRecord.IkeSaRecordConfig> recordConfigCaptor =
                ArgumentCaptor.forClass(SaRecord.IkeSaRecordConfig.class);
        verify(mMockSaRecordHelper)
                .makeRekeyedIkeSaRecord(any(), any(), any(), any(), recordConfigCaptor.capture());
        assertEquals(IKE_REKEY_SA_INITIATOR_SPI, recordConfigCaptor.getValue().initSpi.getSpi());

        // Verify outbound CREATE_CHILD_SA message
        IkeMessage rekeyCreateResp =
                verifyAndGetOutboundEncryptedResp(IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA);
        assertNotNull(
                rekeyCreateResp.getPayloadForType(IkePayload.PAYLOAD_TYPE_SA, IkeSaPayload.class));
        assertNotNull(
                rekeyCreateResp.getPayloadForType(IkePayload.PAYLOAD_TYPE_KE, IkeKePayload.class));
        assertNotNull(
                rekeyCreateResp.getPayloadForType(
                        IkePayload.PAYLOAD_TYPE_NONCE, IkeNoncePayload.class));

        // Verify SA, StateMachine state
        assertEquals(mSpyCurrentIkeSaRecord, mIkeSessionStateMachine.mIkeSaRecordAwaitingRemoteDel);
        assertEquals(mSpyRemoteInitIkeSaRecord, mIkeSessionStateMachine.mIkeSaRecordSurviving);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeRemoteDelete);
        verify(mMockCurrentIkeSocket)
                .registerIke(
                        eq(mSpyRemoteInitIkeSaRecord.getLocalSpi()), eq(mSpyIkeConnectionCtrl));
    }

    @Ignore
    public void disableTestRekeyIkeRemoteCreateHandlesInvalidReq() throws Exception {
        setupIdleStateMachine();

        // Receive Rekey request
        ReceivedIkePacket request = makeRekeyIkeRequestWithUnacceptableProposal();
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        verifyProcessRekeyReqFailure(ERROR_TYPE_NO_PROPOSAL_CHOSEN);
    }

    @Ignore
    public void disableTestRekeyIkeRemoteCreateSaCreationFailure() throws Exception {
        // Throw error when building new IKE SA
        throwExceptionWhenMakeRekeyIkeSa(
                new GeneralSecurityException("testRekeyIkeRemoteCreateSaCreationFailure"));
        setupIdleStateMachine();

        // Receive Rekey request
        ReceivedIkePacket request = makeRekeyIkeRequest();
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        verifyProcessRekeyReqFailure(ERROR_TYPE_NO_PROPOSAL_CHOSEN);
    }

    @Test
    public void testRekeyIkeRemoteCreateHandlesInvalidKePayload() throws Exception {
        setupIdleStateMachine();

        // Build Rekey request
        // SA Payload: ENCR_AES_CBC(128)|AUTH_HMAC_SHA1_96|DH_1024_BIT_MODP|PRF_HMAC_SHA1
        IkePayload saPayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_SA,
                        false /*isResp*/,
                        IKE_REKEY_SA_PAYLOAD_HEX_STRING);

        // Unrecognized DH Group: 0x0fff
        String unrecognizedKePayload =
                "280000880fff0000b4a2faf4bb54878ae21d638512ece55d9236fc50"
                        + "46ab6cef82220f421f3ce6361faf36564ecb6d28798a94aa"
                        + "d7b2b4b603ddeaaa5630adb9ece8ac37534036040610ebdd"
                        + "92f46bef84f0be7db860351843858f8acf87056e272377f7"
                        + "0c9f2d81e29c7b0ce4f291a3a72476bb0b278fd4b7b0a4c2"
                        + "6bbeb08214c7071376079587";
        IkePayload kePayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_KE, false /*isResp*/, unrecognizedKePayload);

        IkePayload noncePayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_NONCE,
                        false /*isResp*/,
                        NONCE_INIT_PAYLOAD_HEX_STRING);

        ReceivedIkePacket request =
                makeDummyEncryptedReceivedIkePacketWithPayloadList(
                        mSpyCurrentIkeSaRecord,
                        IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                        false /*isResp*/,
                        Arrays.asList(saPayload, kePayload, noncePayload));

        // Receive Rekey request
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        verifyProcessRekeyReqFailure(ERROR_TYPE_INVALID_KE_PAYLOAD);
    }

    @Test
    public void testRejectRemoteRekeyWithoutDhGroupInProposal() throws Exception {
        setupIdleStateMachine();

        // Build a Rekey request that does not propose DH groups.
        String rekeySaPayloadWithoutDhGroup =
                "22000038000000340101080400000000000000FF0300000c0100000c800e0080030"
                        + "000080300000203000008020000020000000802000002";
        IkePayload saPayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_SA, false /*isResp*/, rekeySaPayloadWithoutDhGroup);
        IkePayload kePayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_KE, false /*isResp*/, KE_PAYLOAD_HEX_STRING);
        IkePayload noncePayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_NONCE,
                        false /*isResp*/,
                        NONCE_INIT_PAYLOAD_HEX_STRING);
        ReceivedIkePacket request =
                makeRekeyIkeRequestWithPayloads(Arrays.asList(saPayload, kePayload, noncePayload));

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        verifyProcessRekeyReqFailure(ERROR_TYPE_NO_PROPOSAL_CHOSEN);
    }

    @Test
    public void testRejectRemoteRekeyWithoutKePayload() throws Exception {
        setupIdleStateMachine();

        // Build a Rekey request that proposes DH groups but does not include a KE payload
        IkePayload saPayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_SA,
                        false /*isResp*/,
                        IKE_REKEY_SA_PAYLOAD_HEX_STRING);
        IkePayload noncePayload =
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_NONCE,
                        false /*isResp*/,
                        NONCE_INIT_PAYLOAD_HEX_STRING);
        ReceivedIkePacket request =
                makeRekeyIkeRequestWithPayloads(Arrays.asList(saPayload, noncePayload));

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        verifyProcessRekeyReqFailure(ERROR_TYPE_INVALID_SYNTAX);
    }

    private void verifyProcessRekeyReqFailure(int expectedErrorCode) {
        // Verify IKE Session is back to Idle
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        // Verify error notification was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, payloads.size());
        IkeNotifyPayload notify = (IkeNotifyPayload) payloads.get(0);
        assertEquals(expectedErrorCode, notify.notifyType);
    }

    @Test
    public void testRekeyIkeRemoteDelete() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyIkeLocalDelete
        mIkeSessionStateMachine.mRemoteInitNewIkeSaRecord = mSpyRemoteInitIkeSaRecord;
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        // Rekey Delete request
        ReceivedIkePacket dummyDeleteIkeRequestReceivedPacket =
                makeDeleteIkeRequest(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDeleteIkeRequestReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementRemoteReqMsgId();
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyDeleteIkeRequestReceivedPacket);

        // Verify outbound DELETE_IKE_SA message
        IkeMessage rekeyDeleteResp = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);
        IkeHeader rekeyDeleteRespHeader = rekeyDeleteResp.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, rekeyDeleteRespHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, rekeyDeleteRespHeader.exchangeType);
        assertTrue(rekeyDeleteRespHeader.isResponseMsg);
        assertTrue(rekeyDeleteRespHeader.fromIkeInitiator);
        assertTrue(rekeyDeleteResp.ikePayloadList.isEmpty());

        // Verify final state - Idle, with new SA, and old SA closed.
        verifyRekeyReplaceSa(mSpyRemoteInitIkeSaRecord);

        verify(mMockIkeSessionCallback, never()).onClosed();

        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyDeleteIkeRequestReceivedPacket);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testRekeyIkeRemoteDeleteExitAndRenter() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyIkeLocalDelete
        mIkeSessionStateMachine.mRemoteInitNewIkeSaRecord = mSpyRemoteInitIkeSaRecord;
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        // Trigger a timeout, and immediately re-enter remote-delete
        mLooper.moveTimeForward(IkeSessionStateMachine.REKEY_DELETE_TIMEOUT_MS / 2 + 1);
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.TIMEOUT_REKEY_REMOTE_DELETE);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        // Shift time forward, and assert the previous timeout was NOT fired.
        mLooper.moveTimeForward(IkeSessionStateMachine.REKEY_DELETE_TIMEOUT_MS / 2 + 1);
        mLooper.dispatchAll();

        // Verify no request received, or response sent.
        verify(mMockIkeMessageHelper, never()).decode(anyInt(), anyObject(), anyObject());
        verifyEncryptAndEncodeNeverCalled(mSpyCurrentIkeSaRecord);

        // Verify final state has not changed - signal was not sent.
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeRemoteDelete);
    }

    @Test
    public void testRekeyIkeRemoteDeleteTimedOut() throws Exception {
        setupIdleStateMachine();

        // Seed fake rekey data and force transition to RekeyIkeLocalDelete
        mIkeSessionStateMachine.mRemoteInitNewIkeSaRecord = mSpyRemoteInitIkeSaRecord;
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        mLooper.moveTimeForward(IkeSessionStateMachine.REKEY_DELETE_TIMEOUT_MS);
        mLooper.dispatchAll();

        // Verify no request received, or response sent.
        verify(mMockIkeMessageHelper, never()).decode(anyInt(), anyObject(), anyObject());
        verifyEncryptAndEncodeNeverCalled(mSpyCurrentIkeSaRecord);

        // Verify final state - Idle, with new SA, and old SA closed.
        verifyRekeyReplaceSa(mSpyRemoteInitIkeSaRecord);

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Ignore
    public void disableTestSimulRekey() throws Exception {
        setupIdleStateMachine();

        // Prepare "rekeyed" SA
        setupRekeyedIkeSa(mSpyLocalInitIkeSaRecord);
        doReturn(1).when(mSpyLocalInitIkeSaRecord).compareTo(mSpyRemoteInitIkeSaRecord);

        // Send Rekey request on mSpyCurrentIkeSaRecord
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));

        // Receive Rekey request on mSpyCurrentIkeSaRecord
        ReceivedIkePacket dummyRekeyIkeRequestReceivedPacket = makeRekeyIkeRequest();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRequestReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementRemoteReqMsgId();

        // Receive Rekey response on mSpyCurrentIkeSaRecord
        ReceivedIkePacket dummyRekeyIkeRespReceivedPacket = makeRekeyIkeResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRespReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementLocaReqMsgId();
        verify(mMockCurrentIkeSocket)
                .registerIke(eq(mSpyLocalInitIkeSaRecord.getLocalSpi()), eq(mSpyIkeConnectionCtrl));

        // Receive Delete response on mSpyCurrentIkeSaRecord
        ReceivedIkePacket dummyDeleteIkeRespReceivedPacket =
                makeDeleteIkeResponse(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDeleteIkeRespReceivedPacket);
        mLooper.dispatchAll();
        verifyIncrementLocaReqMsgId();

        // Verify
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyRekeyIkeRequestReceivedPacket);
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyRekeyIkeRespReceivedPacket);
        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyDeleteIkeRespReceivedPacket);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        verifyRekeyReplaceSa(mSpyLocalInitIkeSaRecord);
        verify(mMockIkeSessionCallback, never()).onClosed();
    }

    @Test
    public void testOpenIkeSession() throws Exception {
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.Initial);

        mIkeSessionStateMachine.openSession();
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeInit);
    }

    @Test
    public void testIkeInitSchedulesRekey() throws Exception {
        setupFirstIkeSa();

        // Send IKE INIT request
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);

        // Receive IKE INIT response
        ReceivedIkePacket dummyReceivedIkePacket = makeIkeInitResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyReceivedIkePacket);

        // Mock IKE AUTH and transition to Idle
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();
        mIkeSessionStateMachine.mSaProposal = buildSaProposal();

        // Move time forward to trigger rekey
        mLooper.moveTimeForward(
                mIkeSessionStateMachine.mIkeSessionParams.getSoftLifetimeMsInternal());
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
    }

    @Test
    public void testRekeyCreateIkeSchedulesRekey() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();

        // Prepare "rekeyed" SA
        setupRekeyedIkeSa(mSpyLocalInitIkeSaRecord);

        // Receive Rekey response
        ReceivedIkePacket dummyRekeyIkeRespReceivedPacket = makeRekeyIkeResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRespReceivedPacket);
        mLooper.dispatchAll();

        // Mock rekey delete and transition to Idle
        mIkeSessionStateMachine.mCurrentIkeSaRecord = mSpyLocalInitIkeSaRecord;
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();

        // Move time forward to trigger rekey
        mLooper.moveTimeForward(
                mIkeSessionStateMachine.mIkeSessionParams.getSoftLifetimeMsInternal());
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
    }

    @Test
    public void testBuildEncryptedInformationalMessage() throws Exception {
        IkeNotifyPayload payload = new IkeNotifyPayload(ERROR_TYPE_INVALID_SYNTAX, new byte[0]);

        boolean isResp = false;
        IkeMessage generated =
                mIkeSessionStateMachine.buildEncryptedInformationalMessage(
                        mSpyCurrentIkeSaRecord, new IkeInformationalPayload[] {payload}, isResp, 0);

        assertEquals(mSpyCurrentIkeSaRecord.getInitiatorSpi(), generated.ikeHeader.ikeInitiatorSpi);
        assertEquals(mSpyCurrentIkeSaRecord.getResponderSpi(), generated.ikeHeader.ikeResponderSpi);
        assertEquals(
                mSpyCurrentIkeSaRecord.getLocalRequestMessageId(), generated.ikeHeader.messageId);
        assertEquals(isResp, generated.ikeHeader.isResponseMsg);
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, generated.ikeHeader.nextPayloadType);

        List<IkeNotifyPayload> generatedPayloads =
                generated.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        assertEquals(1, generatedPayloads.size());

        IkeNotifyPayload generatedPayload = generatedPayloads.get(0);
        assertArrayEquals(new byte[0], generatedPayload.notifyData);
        assertEquals(ERROR_TYPE_INVALID_SYNTAX, generatedPayload.notifyType);
    }

    private void verifyLastSentRespAllPackets(byte[][] expectedPackets, IkeSaRecord saRecord) {
        if (expectedPackets == null) {
            assertNull(saRecord.getLastSentRespAllPackets());
            return;
        }

        assertEquals(expectedPackets.length, saRecord.getLastSentRespAllPackets().size());
        for (int i = 0; i < expectedPackets.length; i++) {
            assertArrayEquals(expectedPackets[i], saRecord.getLastSentRespAllPackets().get(i));
        }
    }

    @Test
    public void testEncryptedRetransmitterImmediatelySendsRequest() throws Exception {
        setupIdleStateMachine();
        byte[][] dummyLastRespBytes =
                new byte[][] {"testRetransmitterSendsRequestLastResp".getBytes()};
        mSpyCurrentIkeSaRecord.updateLastSentRespAllPackets(
                Arrays.asList(dummyLastRespBytes),
                mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1);

        IkeMessage spyIkeReqMessage =
                spy(
                        new IkeMessage(
                                new IkeHeader(
                                        mSpyCurrentIkeSaRecord.getInitiatorSpi(),
                                        mSpyCurrentIkeSaRecord.getResponderSpi(),
                                        IkePayload.PAYLOAD_TYPE_SK,
                                        EXCHANGE_TYPE_INFORMATIONAL,
                                        false /*isResp*/,
                                        mSpyCurrentIkeSaRecord.isLocalInit,
                                        mSpyCurrentIkeSaRecord.getLocalRequestMessageId()),
                                new ArrayList<>()));

        // Use something unique as a sentinel value
        byte[][] dummyReqBytesList =
                new byte[][] {
                    "testRetransmitterSendsReqFrag1".getBytes(),
                    "testRetransmitterSendsReqFrag2".getBytes()
                };

        doReturn(dummyReqBytesList)
                .when(spyIkeReqMessage)
                .encryptAndEncode(any(), any(), eq(mSpyCurrentIkeSaRecord), anyBoolean(), anyInt());

        IkeSessionStateMachine.EncryptedRetransmitter retransmitter =
                mIkeSessionStateMachine.new EncryptedRetransmitter(spyIkeReqMessage);

        // Verify message is sent out, and that request does not change cached retransmit-response
        // mLastSentIkeResp.
        verify(mMockCurrentIkeSocket).sendIkePacket(eq(dummyReqBytesList[0]), eq(REMOTE_ADDRESS));
        verify(mMockCurrentIkeSocket).sendIkePacket(eq(dummyReqBytesList[1]), eq(REMOTE_ADDRESS));
        verifyLastSentRespAllPackets(dummyLastRespBytes, mSpyCurrentIkeSaRecord);
    }

    @Test
    public void testRetransmittedPacketsAreIdentical() throws Exception {
        setupIdleStateMachine();

        IkeMessage mockIkeReqMsg = mock(IkeMessage.class);
        byte[][] dummyReqBytesList =
                new byte[][] {"testRetransmittedPacketsAreIdentical".getBytes()};
        doReturn(dummyReqBytesList)
                .when(mockIkeReqMsg)
                .encryptAndEncode(any(), any(), eq(mSpyCurrentIkeSaRecord), anyBoolean(), anyInt());

        IkeSessionStateMachine.EncryptedRetransmitter retransmitter =
                mIkeSessionStateMachine.new EncryptedRetransmitter(mockIkeReqMsg);

        // Packet is immediately sent out
        verify(mMockCurrentIkeSocket).sendIkePacket(eq(dummyReqBytesList[0]), eq(REMOTE_ADDRESS));
        verify(mockIkeReqMsg)
                .encryptAndEncode(any(), any(), eq(mSpyCurrentIkeSaRecord), anyBoolean(), anyInt());

        // Retransmit packet
        retransmitter.retransmit();
        verify(mMockCurrentIkeSocket, times(2))
                .sendIkePacket(eq(dummyReqBytesList[0]), eq(REMOTE_ADDRESS));
        verify(mockIkeReqMsg)
                .encryptAndEncode(any(), any(), eq(mSpyCurrentIkeSaRecord), anyBoolean(), anyInt());
    }

    // TODO: b/141275871 Test retransmisstions are fired for correct times within certain time.

    @Test
    public void testCacheLastRequestAndResponse() throws Exception {
        setupIdleStateMachine();
        mSpyCurrentIkeSaRecord.updateLastReceivedReqFirstPacket(null /*reqPacket*/);
        mSpyCurrentIkeSaRecord.updateLastSentRespAllPackets(
                null /*respPacketList*/, mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1);

        byte[] dummyIkeReqFirstPacket = "testLastSentRequest".getBytes();
        byte[][] dummyIkeResp =
                new byte[][] {
                    "testLastSentRespFrag1".getBytes(), "testLastSentRespFrag2".getBytes()
                };

        doReturn(dummyIkeResp)
                .when(mMockIkeMessageHelper)
                .encryptAndEncode(
                        any(),
                        any(),
                        eq(mSpyCurrentIkeSaRecord),
                        any(IkeMessage.class),
                        anyBoolean(),
                        anyInt());

        // Receive a DPD request, expect to send dummyIkeResp
        ReceivedIkePacket dummyDpdRequest =
                makeDpdIkeRequest(
                        mSpyCurrentIkeSaRecord.getRemoteRequestMessageId(), dummyIkeReqFirstPacket);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDpdRequest);
        mLooper.dispatchAll();

        verify(mMockCurrentIkeSocket).sendIkePacket(eq(dummyIkeResp[0]), eq(REMOTE_ADDRESS));
        verify(mMockCurrentIkeSocket).sendIkePacket(eq(dummyIkeResp[1]), eq(REMOTE_ADDRESS));

        verifyLastSentRespAllPackets(dummyIkeResp, mSpyCurrentIkeSaRecord);
        assertTrue(mSpyCurrentIkeSaRecord.isRetransmittedRequest(dummyIkeReqFirstPacket));

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testReplyRetransmittedRequest() throws Exception {
        setupIdleStateMachine();

        // Mock last sent request and response
        byte[] dummyIkeReqFirstPacket = "testRcvRetransmittedRequestReq".getBytes();
        byte[][] dummyIkeResp = new byte[][] {"testRcvRetransmittedRequestResp".getBytes()};

        mSpyCurrentIkeSaRecord.updateLastReceivedReqFirstPacket(dummyIkeReqFirstPacket);
        mSpyCurrentIkeSaRecord.updateLastSentRespAllPackets(
                Arrays.asList(dummyIkeResp),
                mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1);

        // Build request with last validated message ID
        ReceivedIkePacket request =
                makeDpdIkeRequest(
                        mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1,
                        dummyIkeReqFirstPacket);

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);

        mLooper.dispatchAll();

        verifyLastSentRespAllPackets(dummyIkeResp, mSpyCurrentIkeSaRecord);
        verify(mMockCurrentIkeSocket).sendIkePacket(eq(dummyIkeResp[0]), eq(REMOTE_ADDRESS));

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testDiscardFakeRetransmittedRequest() throws Exception {
        setupIdleStateMachine();

        // Mock last sent request and response
        byte[] dummyIkeReqFirstPacket = "testDiscardFakeRetransmittedRequestReq".getBytes();
        byte[][] dummyIkeResp = new byte[][] {"testDiscardFakeRetransmittedRequestResp".getBytes()};
        mSpyCurrentIkeSaRecord.updateLastReceivedReqFirstPacket(dummyIkeReqFirstPacket);
        mSpyCurrentIkeSaRecord.updateLastSentRespAllPackets(
                Arrays.asList(dummyIkeResp),
                mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1);

        // Build request with last validated message ID but different bytes
        ReceivedIkePacket request =
                makeDpdIkeRequest(
                        mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1, new byte[0]);

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);

        mLooper.dispatchAll();

        verifyLastSentRespAllPackets(dummyIkeResp, mSpyCurrentIkeSaRecord);
        verify(mMockCurrentIkeSocket, never()).sendIkePacket(any(), any());

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testRcvRetransmittedRequestBeforeReplyOriginalRequest() throws Exception {
        setupIdleStateMachine();

        // Mock last sent response
        byte[][] dummyIkeResp = new byte[][] {"testLastSentResponse".getBytes()};
        mSpyCurrentIkeSaRecord.updateLastSentRespAllPackets(
                Arrays.asList(dummyIkeResp),
                mSpyCurrentIkeSaRecord.getRemoteRequestMessageId() - 1);

        // Send request with next message ID
        IkeDeletePayload[] inboundDelPayloads =
                new IkeDeletePayload[] {new IkeDeletePayload(new int[] {CHILD_SPI_REMOTE})};
        ReceivedIkePacket request = makeDeleteChildPacket(inboundDelPayloads, false /*isResp*/);
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        // Verify that no response has been sent out since we didn't configure Child Session to
        // respond
        verify(mMockCurrentIkeSocket, never()).sendIkePacket(any(), any());
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);

        // Retransmit the request
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        // Verify that no response has been sent out and state machine is still in
        // ChildProcedureOngoing
        verify(mMockCurrentIkeSocket, never()).sendIkePacket(any(), any());
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
    }

    @Test
    public void testDiscardRetransmittedResponse() throws Exception {
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();
        verifyRetransmissionStarted();

        // Build and send fake response with last validated message ID to IKE state machine
        ReceivedIkePacket resp =
                makeDummyEncryptedReceivedIkePacketWithPayloadList(
                        mSpyCurrentIkeSaRecord,
                        IkeHeader.EXCHANGE_TYPE_IKE_SA_INIT,
                        true /*isResp*/,
                        mSpyCurrentIkeSaRecord.getLocalRequestMessageId() - 1,
                        new ArrayList<>(),
                        new byte[0]);

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        // Verify current state does not change
        verifyRetransmissionStarted();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.CreateIkeLocalIkeAuth);
    }

    @Test
    public void testDeleteIkeLocalDeleteRequest() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Verify outbound message
        IkeMessage delMsg = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = delMsg.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, ikeHeader.exchangeType);
        assertFalse(ikeHeader.isResponseMsg);
        assertTrue(ikeHeader.fromIkeInitiator);

        List<IkeDeletePayload> deletePayloadList =
                delMsg.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_DELETE, IkeDeletePayload.class);
        assertEquals(1, deletePayloadList.size());

        IkeDeletePayload deletePayload = deletePayloadList.get(0);
        assertEquals(IkePayload.PROTOCOL_ID_IKE, deletePayload.protocolId);
        assertEquals(0, deletePayload.numSpi);
        assertEquals(0, deletePayload.spiSize);
        assertArrayEquals(new int[0], deletePayload.spisToDelete);
    }

    @Test
    public void testDeleteIkeLocalDeleteResponse() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        ReceivedIkePacket received = makeDeleteIkeResponse(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, received);
        mLooper.dispatchAll();
        verifyIncrementLocaReqMsgId();

        verifyNotifyUserCloseSession(mIkeSessionStateMachine.mDeleteIkeLocalDelete);

        // Verify state machine quit properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testDeleteIkeLocalDeleteResponseWithParsingError() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();
        resetMockIkeMessageHelper();

        // Mock receiving response with syntax error
        ReceivedIkePacket mockInvalidPacket =
                makeDummyReceivedIkePacketWithInvalidSyntax(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_INFORMATIONAL);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockInvalidPacket);
        mLooper.dispatchAll();

        // Verify no more request out
        verifyEncryptAndEncodeNeverCalled(mSpyCurrentIkeSaRecord);

        // Verify state machine quit properly
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testDeleteIkeLocalDeleteHandlesInvalidResp() throws Exception {
        setupIdleStateMachine();

        // Send delete request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_IKE));
        mLooper.dispatchAll();

        // Receive response with wrong exchange type
        ReceivedIkePacket resp =
                makeDummyReceivedIkePacketWithInvalidSyntax(
                        mSpyCurrentIkeSaRecord,
                        true /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        // Verify state machine quit properly
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testDeleteIkeLocalDeleteReceivedNonDeleteRequest() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Verify delete sent out.
        verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        resetMockIkeMessageHelper(); // Discard value.

        ReceivedIkePacket received = makeRekeyIkeRequest();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, received);

        mLooper.dispatchAll();
        verifyRetransmissionStarted();
        verifyIncrementRemoteReqMsgId();

        verifySendTempFailResponse();
    }

    private void verifySendTempFailResponse() {
        IkeMessage resp = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = resp.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, ikeHeader.exchangeType);
        assertTrue(ikeHeader.isResponseMsg);
        assertEquals(mSpyCurrentIkeSaRecord.isLocalInit, ikeHeader.fromIkeInitiator);

        List<IkeNotifyPayload> notificationPayloadList =
                resp.getPayloadListForType(IkePayload.PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        assertEquals(1, notificationPayloadList.size());

        IkeNotifyPayload notifyPayload = notificationPayloadList.get(0);
        assertEquals(IkeProtocolException.ERROR_TYPE_TEMPORARY_FAILURE, notifyPayload.notifyType);
    }

    @Test
    public void testDeleteIkeRemoteDelete() throws Exception {
        setupIdleStateMachine();

        verifyIkeDeleteRequestHandled(mIkeSessionStateMachine.mReceiving);
    }

    private void verifyIkeDeleteRequestHandled(IState expectedState) throws Exception {
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET,
                makeDeleteIkeRequest(mSpyCurrentIkeSaRecord));

        mLooper.dispatchAll();
        verifyIncrementRemoteReqMsgId();

        // Verify outbound message
        IkeMessage delMsg = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);

        IkeHeader ikeHeader = delMsg.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, ikeHeader.exchangeType);
        assertTrue(ikeHeader.isResponseMsg);
        assertEquals(mSpyCurrentIkeSaRecord.isLocalInit, ikeHeader.fromIkeInitiator);

        assertTrue(delMsg.ikePayloadList.isEmpty());

        verifyNotifyUserCloseSession(expectedState);

        // Verify state machine quit properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    private void verifySessionKilled(IState state, boolean hasDeleteRequestSent) {
        verifySessionKilledWithException(
                state, hasDeleteRequestSent, null, false /* isErrorFromChild */);
    }

    private void verifySessionKilledWithException(
            IState state,
            boolean hasDeleteRequestSent,
            Exception exception,
            boolean isErrorFromChild) {
        verify(mSpyCurrentIkeSaRecord).close();
        verify(mMockCurrentIkeSocket).unregisterIke(mSpyCurrentIkeSaRecord.getInitiatorSpi());

        if (exception != null) {
            verifyNotifyUserCloseSessionWithException(state, exception, isErrorFromChild);
        } else {
            verifyNotifyUserCloseSession(state);
        }

        if (hasDeleteRequestSent) {
            // Verify outbound request
            IkeMessage req = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);
            IkeHeader ikeHeader = req.ikeHeader;
            assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
            assertEquals(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, ikeHeader.exchangeType);
            assertFalse(ikeHeader.isResponseMsg);
            assertEquals(1, req.ikePayloadList.size());
            assertEquals(IkePayload.PAYLOAD_TYPE_DELETE, req.ikePayloadList.get(0).payloadType);
        } else {
            // Verify no outbound request
            verifyEncryptAndEncodeNeverCalled();
        }

        // Verify state machine quit properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockBusyWakelock).release();
    }

    @Test
    public void testKillSessionDeleteIkeRequestSent() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.killSession();
        mLooper.dispatchAll();

        verifySessionKilled(mIkeSessionStateMachine.mIdle, true /* hasDeleteRequestSent */);
    }

    @Test
    public void testKillSessionNoDeleteIkeRequestSent() throws Exception {
        setupIdleStateMachine();

        // Transition to state that does not send IKE delete requests
        InitialSetupData initialSetupData =
                new InitialSetupData(
                        mChildSessionParams,
                        mMockChildSessionCallback,
                        SaProposal.DH_GROUP_1024_BIT_MODP);
        mIkeSessionStateMachine.mCreateIkeLocalIkeInit.setIkeSetupData(initialSetupData);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mCreateIkeLocalIkeInit);
        mLooper.dispatchAll();

        mIkeSessionStateMachine.killSession();
        mLooper.dispatchAll();

        verifySessionKilled(
                mIkeSessionStateMachine.mCreateIkeLocalIkeInit, false /* hasDeleteRequestSent */);
    }

    @Test
    public void testOnFatalIkeSessionErrorFromChild() throws Exception {
        setupIdleStateMachine();

        transitionToChildProcedureOngoing();

        Exception exception = new IkeInternalException("IkeStateMachineTest");
        mDummyChildSmCallback.onFatalIkeSessionError(exception);
        mLooper.dispatchAll();

        verifySessionKilledWithException(
                mIkeSessionStateMachine.mChildProcedureOngoing,
                false /* hasDeleteRequestSent */,
                exception,
                true /* isErrorFromChild */);
    }

    @Test
    public void testScheduleDpd() throws Exception {
        setupIdleStateMachine();

        ArgumentCaptor<IkeAlarmConfig> captor = ArgumentCaptor.forClass(IkeAlarmConfig.class);
        verify(mSpyDeps).newExactAndAllowWhileIdleAlarm(captor.capture());
        assertEquals(
                TimeUnit.SECONDS.toMillis(
                        mIkeSessionStateMachine.mIkeSessionParams.getDpdDelaySeconds()),
                captor.getValue().delayMs);
    }

    @Test
    public void testDisableDpd() throws Exception {
        mIkeSessionStateMachine.quitNow();
        mLooper.dispatchAll();

        // Clear the wakelock call in quitNow() for setupIdleStateMachine() to pass
        reset(mMockBusyWakelock);

        // Restart IKE Session with DPD disabled
        IkeSessionParams ikeSessionParams =
                buildIkeSessionParamsCommon()
                        .setAuthPsk(mPsk)
                        .setDpdDelaySeconds(Integer.MAX_VALUE)
                        .build();
        mIkeSessionStateMachine = makeAndStartIkeSession(ikeSessionParams);
        setupIdleStateMachine();

        verify(mSpyDeps, never()).newExactAndAllowWhileIdleAlarm(any());
    }

    private IkeMessage verifyAndGetOutboundEncryptedResp(int exchangeType) {
        IkeMessage resp = verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);
        IkeHeader ikeHeader = resp.ikeHeader;
        assertEquals(IkePayload.PAYLOAD_TYPE_SK, ikeHeader.nextPayloadType);
        assertEquals(exchangeType, ikeHeader.exchangeType);
        assertTrue(ikeHeader.isResponseMsg);
        assertEquals(mSpyCurrentIkeSaRecord.isLocalInit, ikeHeader.fromIkeInitiator);
        return resp;
    }

    private IkeMessage verifyAndGetOutboundInformationalResp() {
        return verifyAndGetOutboundEncryptedResp(IkeHeader.EXCHANGE_TYPE_INFORMATIONAL);
    }

    @Test
    public void testReceiveDpd() throws Exception {
        setupIdleStateMachine();

        // Receive a DPD request, expect to stay in IDLE state
        ReceivedIkePacket dummyDpdRequest = makeDpdIkeRequest(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDpdRequest);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyDpdRequest);

        // Verify outbound response
        IkeMessage resp = verifyAndGetOutboundInformationalResp();
        assertTrue(resp.ikePayloadList.isEmpty());
    }

    @Test
    public void testReceiveDpdNonIdle() throws Exception {
        setupIdleStateMachine();

        // Move to a non-idle state. Use RekeyIkeRemoteDelete, as it doesn't send out any requests.
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        // In a rekey state, receiving (and handling) a DPD should not result in a change of states
        ReceivedIkePacket dummyDpdRequest = makeDpdIkeRequest(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDpdRequest);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeRemoteDelete);

        verifyDecodeEncryptedMessage(mSpyCurrentIkeSaRecord, dummyDpdRequest);

        // Verify outbound response
        IkeMessage resp = verifyAndGetOutboundInformationalResp();
        assertTrue(resp.ikePayloadList.isEmpty());
    }

    private void executeAndVerifySendLocalDPD() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mDpdIkeLocalInfo);
        mLooper.dispatchAll();

        verifyEmptyInformationalSent(1, false /* expectedResp*/);
        resetMockIkeMessageHelper();
    }

    @Test
    public void testDpdIkeLocalInfoRcvDpdReq() throws Exception {
        executeAndVerifySendLocalDPD();
        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET, makeDpdIkeRequest(mSpyCurrentIkeSaRecord));
        mLooper.dispatchAll();

        verifyEmptyInformationalSent(1, true /* expectedResp*/);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.DpdIkeLocalInfo);
    }

    @Test
    public void testDpdIkeLocalInfoRcvDeleteIkeReq() throws Exception {
        executeAndVerifySendLocalDPD();
        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET, makeDeleteIkeRequest(mSpyCurrentIkeSaRecord));
        mLooper.dispatchAll();

        verifyEmptyInformationalSent(1, true /* expectedResp*/);
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    @Test
    public void testDpdIkeLocalInfoRcvRekeyIkeReq() throws Exception {
        executeAndVerifySendLocalDPD();
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, makeRekeyIkeRequest());
        mLooper.dispatchAll();

        verifySendTempFailResponse();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.DpdIkeLocalInfo);
    }

    @Test
    public void testIdleTriggersNewRequests() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();

        // Verify that the command is executed, and the state machine transitions to the right state
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
        verifyRetransmissionStarted();
    }

    @Test
    public void testNonIdleStateDoesNotTriggerNewRequests() throws Exception {
        setupIdleStateMachine();

        // Force ourselves into a non-idle state
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mReceiving);
        mLooper.dispatchAll();
        verifyEncryptAndEncodeNeverCalled();

        // Queue a local request, and expect that it is not run (yet)
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();

        // Verify that the state machine is still in the Receiving state
        verifyEncryptAndEncodeNeverCalled();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.Receiving);

        // Go back to Idle, and expect to immediately transition to RekeyIkeLocalCreate from the
        // queued request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
        verifyEncryptAndEncodeAndGetMessage(mSpyCurrentIkeSaRecord);
    }

    @Test
    public void testOpenChildSessionValidatesArgs() throws Exception {
        setupIdleStateMachine();

        // Expect failure - no callbacks provided
        try {
            mIkeSessionStateMachine.openChildSession(mChildSessionParams, null);
            fail("Expected to fail due to missing ChildSessionCallback");
        } catch (IllegalArgumentException expected) {
        }

        // Expect failure - callbacks already registered
        try {
            mIkeSessionStateMachine.openChildSession(
                    mChildSessionParams, mMockChildSessionCallback);
            fail("Expected to fail due to invalid ChildSessionCallback");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testOpenChildSession() throws Exception {
        setupIdleStateMachine();

        ChildSessionCallback cb = mock(ChildSessionCallback.class);
        mIkeSessionStateMachine.openChildSession(mChildSessionParams, cb);

        // Test that inserting the same cb returns an error, even before the state
        // machine has a chance to process it.
        try {
            mIkeSessionStateMachine.openChildSession(mChildSessionParams, cb);
            fail("Expected to fail due to invalid ChildSessionCallback");
        } catch (IllegalArgumentException expected) {
        }

        verifyMakeChildAndReturnChildSmCb(cb);

        // Verify state in IkeSessionStateMachine
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);

        synchronized (mIkeSessionStateMachine.mChildCbToSessions) {
            assertTrue(mIkeSessionStateMachine.mChildCbToSessions.containsKey(cb));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testOpenChildSessionWithMobikeAndTransport() throws Exception {
        mIkeSessionStateMachine = restartStateMachineWithRfcMobikeConfigured();

        mIkeSessionStateMachine.openChildSession(
                mock(TransportModeChildSessionParams.class), mock(ChildSessionCallback.class));
    }

    @Test
    public void testCloseChildSessionValidatesArgs() throws Exception {
        setupIdleStateMachine();

        // Expect failure - callbacks not registered
        try {
            mIkeSessionStateMachine.closeChildSession(mock(ChildSessionCallback.class));
            fail("Expected to fail since callback is not registered");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testCloseChildSession() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.closeChildSession(mMockChildSessionCallback);
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
    }

    @Test
    public void testCloseImmediatelyAfterOpenChildSession() throws Exception {
        setupIdleStateMachine();

        ChildSessionCallback cb = mock(ChildSessionCallback.class);
        mIkeSessionStateMachine.openChildSession(mChildSessionParams, cb);

        // Verify that closing the session immediately still picks up the child callback
        // even before the looper has a chance to run.
        mIkeSessionStateMachine.closeChildSession(mMockChildSessionCallback);
    }

    @Test
    public void testOnChildSessionClosed() throws Exception {
        setupIdleStateMachine();

        mDummyChildSmCallback.onChildSessionClosed(mMockChildSessionCallback);

        synchronized (mIkeSessionStateMachine.mChildCbToSessions) {
            assertFalse(
                    mIkeSessionStateMachine.mChildCbToSessions.containsKey(
                            mMockChildSessionCallback));
        }
    }

    @Test
    public void testHandleUnexpectedExceptionInEnterState() throws Exception {
        Log spyIkeLog = TestUtils.makeSpyLogDoLogErrorForWtf(TAG);
        IkeManager.setIkeLog(spyIkeLog);

        IkeSessionParams mockSessionParams = mock(IkeSessionParams.class);
        when(mockSessionParams.getServerHostname()).thenReturn(REMOTE_HOSTNAME);

        RuntimeException cause = new RuntimeException();
        when(mockSessionParams.getSaProposalsInternal()).thenThrow(cause);

        DhGroupTransform dhGroupTransform = new DhGroupTransform(SaProposal.DH_GROUP_2048_BIT_MODP);
        IkeSaProposal mockSaProposal = mock(IkeSaProposal.class);
        when(mockSaProposal.getDhGroupTransforms())
                .thenReturn(new DhGroupTransform[] {dhGroupTransform});
        when(mockSessionParams.getSaProposals()).thenReturn(Arrays.asList(mockSaProposal));

        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(mockSessionParams);

        // Send IKE INIT request
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();

        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verify(spyIkeLog).wtf(anyString(), anyString(), any(RuntimeException.class));

        ArgumentCaptor<IkeInternalException> internalExceptionCaptor =
                ArgumentCaptor.forClass(IkeInternalException.class);
        verify(mMockIkeSessionCallback).onClosedWithException(internalExceptionCaptor.capture());
        IkeInternalException internalException = internalExceptionCaptor.getValue();

        // Verify that the Exception which caused the IkeSessionStateMachine to close is the same
        // one mocked for IkeSessionParams#getSaProposalsInternal
        assertSame(cause, internalException.getCause());
    }

    @Test
    public void testHandleUnexpectedExceptionInProcessStateMsg() throws Exception {
        Log spyIkeLog = TestUtils.makeSpyLogDoLogErrorForWtf(TAG);
        IkeManager.setIkeLog(spyIkeLog);

        setupIdleStateMachine();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, null /*receivedIkePacket*/);
        mLooper.dispatchAll();

        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mSpyUserCbExecutor).execute(any(Runnable.class));
        verify(mMockIkeSessionCallback).onClosedWithException(any(IkeInternalException.class));
        verify(spyIkeLog).wtf(anyString(), anyString(), any(RuntimeException.class));
    }

    @Test
    public void testCreateIkeLocalIkeInitRcvErrorNotify() throws Exception {
        // Send IKE INIT request
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Receive IKE INIT response with erro notification.
        List<IkePayload> payloads = new ArrayList<>();
        payloads.add(new IkeNotifyPayload(IkeProtocolException.ERROR_TYPE_NO_PROPOSAL_CHOSEN));
        ReceivedIkePacket resp =
                makeDummyUnencryptedReceivedIkePacket(
                        1L /*initiator SPI*/,
                        2L /*respodner SPI*/,
                        IkeHeader.EXCHANGE_TYPE_IKE_SA_INIT,
                        true /*isResp*/,
                        false /*fromIkeInit*/,
                        payloads);

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, resp);
        mLooper.dispatchAll();

        // Fires user error callbacks
        verify(mMockIkeSessionCallback)
                .onClosedWithException(
                        argThat(err -> err instanceof NoValidProposalChosenException));
        // Verify state machine quit properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
    }

    private void mockSendRekeyChildReq(boolean withMobike) throws Exception {
        if (withMobike) {
            setupIdleStateMachineWithMobike();
        } else {
            setupIdleStateMachine();
        }

        ChildLocalRequest childLocalRequest =
                mLocalRequestFactory.getChildLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_CHILD,
                        mMockChildSessionCallback,
                        null /*childParams*/);

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ, childLocalRequest);
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verify(mMockChildSessionStateMachine).rekeyChildSession();

        // Mocking sending request
        mDummyChildSmCallback.onOutboundPayloadsReady(
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA,
                false /*isResp*/,
                new ArrayList<>(),
                mMockChildSessionStateMachine);
        mLooper.dispatchAll();
    }

    private void mockRcvTempFail() throws Exception {
        ReceivedIkePacket resp =
                makeResponseWithErrorNotify(new IkeNotifyPayload(ERROR_TYPE_TEMPORARY_FAILURE));

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, resp);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();
    }

    @Ignore
    public void disableTestTempFailureHandlerTimeout() throws Exception {
        long currentTime = 0;
        int retryCnt = 0;

        mockSendRekeyChildReq(false);

        while (currentTime + RETRY_INTERVAL_MS < TEMP_FAILURE_RETRY_TIMEOUT_MS) {
            mockRcvTempFail();

            mLooper.moveTimeForward(RETRY_INTERVAL_MS);
            currentTime += RETRY_INTERVAL_MS;
            mLooper.dispatchAll();

            retryCnt++;
            verify(mMockChildSessionStateMachine, times(1 + retryCnt)).rekeyChildSession();
        }

        mLooper.moveTimeForward(RETRY_INTERVAL_MS);
        mLooper.dispatchAll();

        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(IkeInternalException.class));
    }

    @Test
    public void testTempFailureHandlerCancelTimer() throws Exception {
        mockRescheduleRekey(mSpyCurrentIkeSaRecord);
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Mock sending TEMPORARY_FAILURE response
        mockRcvTempFail();
        mLooper.dispatchAll();
        verify(mSpyCurrentIkeSaRecord).rescheduleRekey(eq(RETRY_INTERVAL_MS));
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        // Move time forward to trigger retry
        mLooper.moveTimeForward(IkeSessionStateMachine.RETRY_INTERVAL_MS);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);

        // Prepare "rekeyed" SA
        setupRekeyedIkeSa(mSpyLocalInitIkeSaRecord);

        // Receive valid Rekey-Create response
        ReceivedIkePacket dummyRekeyIkeRespReceivedPacket = makeRekeyIkeResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRespReceivedPacket);
        mLooper.dispatchAll();

        // Receive Delete response
        ReceivedIkePacket dummyDeleteIkeRespReceivedPacket =
                makeDeleteIkeResponse(mSpyCurrentIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyDeleteIkeRespReceivedPacket);
        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        // Move time forward
        mLooper.moveTimeForward(IkeSessionStateMachine.TEMP_FAILURE_RETRY_TIMEOUT_MS);
        mLooper.dispatchAll();

        // Validate IKE Session is not closed
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Ignore
    public void disableTestIdleReceiveRequestWithFatalError() throws Exception {
        setupIdleStateMachine();

        // Mock receiving packet with syntax error
        ReceivedIkePacket mockInvalidPacket =
                makeDummyReceivedIkePacketWithInvalidSyntax(
                        mSpyCurrentIkeSaRecord,
                        false /*isResp*/,
                        IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockInvalidPacket);
        mLooper.dispatchAll();

        // Verify Delete request was sent
        List<IkePayload> payloads = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, payloads.size());

        IkePayload payload = payloads.get(0);
        assertEquals(IkePayload.PAYLOAD_TYPE_NOTIFY, payload.payloadType);
        assertEquals(ERROR_TYPE_INVALID_SYNTAX, ((IkeNotifyPayload) payload).notifyType);

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
    }

    @Test
    public void testHandlesInvalidRequest() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mChildProcedureOngoing);

        // Receive an IKE AUTH request
        ReceivedIkePacket request =
                makeDummyEncryptedReceivedIkePacketWithPayloadList(
                        mSpyCurrentIkeSaRecord,
                        IkeHeader.EXCHANGE_TYPE_IKE_AUTH,
                        false /*isResp*/,
                        new ArrayList<IkePayload>());
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, request);
        mLooper.dispatchAll();

        // Verify error notification was sent
        List<IkePayload> ikePayloadList = verifyOutInfoMsgHeaderAndGetPayloads(true /*isResp*/);
        assertEquals(1, ikePayloadList.size());
        assertEquals(
                ERROR_TYPE_INVALID_SYNTAX, ((IkeNotifyPayload) ikePayloadList.get(0)).notifyType);

        // Verify IKE Session has quit
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(InvalidSyntaxException.class));
    }

    @Test
    public void testIdleHandlesDecryptPacketFailed() throws Exception {
        setupIdleStateMachine();

        ReceivedIkePacket packet =
                makeDummyReceivedIkePacketWithUnprotectedError(
                        mSpyCurrentIkeSaRecord,
                        false /*isResp*/,
                        EXCHANGE_TYPE_INFORMATIONAL,
                        mock(IkeException.class));
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, packet);
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testHandlesUnencryptedPacket() throws Exception {
        setupIdleStateMachine();
        IkeMessage.setIkeMessageHelper(new IkeMessageHelper());

        ReceivedIkePacket packet =
                makeDummyUnencryptedReceivedIkePacket(
                        mSpyCurrentIkeSaRecord.getLocalSpi(),
                        mSpyCurrentIkeSaRecord.getRemoteSpi(),
                        IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                        false /*isResp*/,
                        false /*fromIkeInit*/,
                        new ArrayList<>());

        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, packet);
        mLooper.dispatchAll();

        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);

        // Reset the IkeMessageHelper to be a mock. This is needed for #killSession (called in
        // #tearDown), which attempts to notify the remote about the IKE session dying in the Idle
        // state.
        IkeMessage.setIkeMessageHelper(mMockIkeMessageHelper);
        resetMockIkeMessageHelper();
    }

    @Test
    public void testEapOnlyOption() throws Exception {
        mIkeSessionStateMachine.quitNow();
        IkeSessionParams ikeSessionParams =
                buildIkeSessionParamsCommon()
                        .setAuthEap(mRootCertificate, mEapSessionConfig)
                        .addIkeOption(IKE_OPTION_EAP_ONLY_AUTH)
                        .build();

        verifyIkeAuthWithNotifyPayload(ikeSessionParams, NOTIFY_TYPE_EAP_ONLY_AUTHENTICATION);
    }

    @Test
    public void testInitialContact() throws Exception {
        mIkeSessionStateMachine.quitNow();
        IkeSessionParams ikeSessionParams =
                buildIkeSessionParamsWithIkeOptions(IkeSessionParams.IKE_OPTION_INITIAL_CONTACT);

        verifyIkeAuthWithNotifyPayload(ikeSessionParams, NOTIFY_TYPE_INITIAL_CONTACT);
    }

    private void verifyIkeAuthWithNotifyPayload(
            IkeSessionParams ikeSessionParams, int expectedNotifyType) throws Exception {
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine = makeAndStartIkeSession(ikeSessionParams);
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();

        IkeMessage ikeAuthReqMessage = verifyAuthReqAndGetMsg();
        List<IkeNotifyPayload> notifyPayloads =
                ikeAuthReqMessage.getPayloadListForType(
                        IkePayload.PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        assertTrue(hasExpectedNotifyPayload(notifyPayloads, expectedNotifyType));
    }

    private IkeConfigPayload makeConfigPayload() throws Exception {
        return (IkeConfigPayload)
                IkeTestUtils.hexStringToIkePayload(
                        IkePayload.PAYLOAD_TYPE_CP, true /*isResp*/, CP_PAYLOAD_HEX_STRING);
    }

    private boolean hasExpectedNotifyPayload(
            List<IkeNotifyPayload> notifyPayloads, int expectedNotifyType) {
        for (IkeNotifyPayload payload : notifyPayloads) {
            if (payload.notifyType == expectedNotifyType) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void testAcquireAndReleaseLocalReqWakeLock() throws Exception {
        setupIdleStateMachine();

        ChildSessionCallback cb = mock(ChildSessionCallback.class);
        mIkeSessionStateMachine.openChildSession(mChildSessionParams, cb);

        mLooper.dispatchAll();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verify(mMockLocalRequestWakelock).acquire();
        verify(mMockLocalRequestWakelock).release();
    }

    @Test
    public void testQuitClearAllLocalReqWakeLocks() throws Exception {
        final int localReqCnt = 3;
        setupIdleStateMachine();

        // Leave a Idle state so that the LocalRequest won't be executed
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mReceiving);
        mLooper.dispatchAll();

        // Only in test that all local requests will get the same WakeLock instance but in function
        // code each local request will have a separate WakeLock.
        for (int i = 0; i < localReqCnt; i++) {
            mIkeSessionStateMachine.openChildSession(
                    mChildSessionParams, mock(ChildSessionCallback.class));
        }
        mLooper.dispatchAll();
        verify(mMockLocalRequestWakelock, times(localReqCnt)).acquire();

        mIkeSessionStateMachine.killSession();
        mLooper.dispatchAll();
        verify(mMockLocalRequestWakelock, times(localReqCnt)).release();
    }

    @Test
    public void testIkeAuthWithN1Mode() throws Exception {
        verifyIkeAuthWith3gppEnabled(
                makeN1ModeInformationPayload(), 1 /* ike3gppDataListenerInvocations */);

        verifyN1ModeReceived();
    }

    private void verifyIkeAuthWith3gppEnabled(
            IkePayload ike3gppPayload, int ike3gppDataListenerInvocations) throws Exception {
        // Quit and restart IKE Session with N1 Mode Capability params
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine =
                makeAndStartIkeSession(buildIkeSessionParamsIke3gppExtension(PDU_SESSION_ID));
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();

        // Build IKE AUTH response with Auth-PSK, ID-Responder and config payloads.
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        authRelatedPayloads.add(ike3gppPayload);
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);
        authRelatedPayloads.add(makeConfigPayload());

        verifySharedKeyAuthentication(
                spyAuthPayload,
                respIdPayload,
                authRelatedPayloads,
                true /*hasChildPayloads*/,
                true /*hasConfigPayloadInResp*/,
                false /* isMobikeEnabled */,
                true /* isIpv4 */,
                ike3gppDataListenerInvocations);
        verifyRetransmissionStopped();
    }

    private IkeNotifyPayload makeN1ModeInformationPayload() {
        ByteBuffer n1ModeInformationBuffer = ByteBuffer.allocate(SNSSAI.length + 1);
        n1ModeInformationBuffer.put((byte) SNSSAI.length);
        n1ModeInformationBuffer.put(SNSSAI);
        return new IkeNotifyPayload(
                NOTIFY_TYPE_N1_MODE_INFORMATION, n1ModeInformationBuffer.array());
    }

    private void verifyN1ModeReceived() {
        ArgumentCaptor<List<Ike3gppData>> ike3gppDataCaptor = ArgumentCaptor.forClass(List.class);
        verify(mMockIke3gppDataListener).onIke3gppDataReceived(ike3gppDataCaptor.capture());

        Ike3gppN1ModeInformation n1ModeInformation = null;
        for (Ike3gppData payload : ike3gppDataCaptor.getValue()) {
            if (payload.getDataType() == Ike3gppData.DATA_TYPE_NOTIFY_N1_MODE_INFORMATION) {
                n1ModeInformation = (Ike3gppN1ModeInformation) payload;
            }
        }

        assertNotNull(n1ModeInformation);
        assertArrayEquals(SNSSAI, n1ModeInformation.getSnssai());
    }

    @Test
    public void testIkeAuthWithBackoffTimerNetworkError() throws Exception {
        verifyIkeAuthWithBackoffTimer(
                ERROR_TYPE_NETWORK_FAILURE, UnrecognizedIkeProtocolException.class);
    }

    @Test
    public void testIkeAuthWithBackoffTimerNoApnSubscription() throws Exception {
        verifyIkeAuthWithBackoffTimer(
                ERROR_TYPE_NO_APN_SUBSCRIPTION, UnrecognizedIkeProtocolException.class);
    }

    @Test
    public void testIkeAuthWithBackoffTimerAuthenticationFailed() throws Exception {
        verifyIkeAuthWithBackoffTimer(
                ERROR_TYPE_AUTHENTICATION_FAILED, AuthenticationFailedException.class);
    }

    private void verifyIkeAuthWithBackoffTimer(
            int expectedNotifyErrorCause, Class<? extends IkeProtocolException> exceptionType)
            throws Exception {
        // Quit and restart IKE Session with N1 Mode Capability params
        mIkeSessionStateMachine.quitNow();
        mIkeSessionStateMachine =
                makeAndStartIkeSession(buildIkeSessionParamsIke3gppExtension(PDU_SESSION_ID));
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();

        // Build IKE AUTH response with BackoffTimer and Error-Notify
        IkeNotifyPayload backoffTimerPayload =
                new IkeNotifyPayload(NOTIFY_TYPE_BACKOFF_TIMER, BACKOFF_TIMER_DATA);
        IkeNotifyPayload errorNotify = new IkeNotifyPayload(expectedNotifyErrorCause);

        ReceivedIkePacket mockBackoffTimerResponsePacket =
                makeIkeAuthRespWithoutChildPayloads(
                        Arrays.asList(backoffTimerPayload, errorNotify));
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, mockBackoffTimerResponsePacket);
        mLooper.dispatchAll();

        // Verify IKE Session is closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());

        // This exception is mapped to metrics error code:
        int expectedError = expectedNotifyErrorCause;
        if (expectedError == ERROR_TYPE_AUTHENTICATION_FAILED) {
            expectedError = IkeMetrics.IKE_ERROR_PROTOCOL_AUTHENTICATION_FAILED;
        }

        verify(mMockIkeSessionCallback).onClosedWithException(any(exceptionType));

        verifyIkeMetricsLogged(
                getStateCode(mIkeSessionStateMachine.mCreateIkeLocalIkeAuth), expectedError);

        verifyBackoffTimer(expectedNotifyErrorCause);
    }

    private void verifyBackoffTimer(int expectedNotifyErrorCause) {
        ArgumentCaptor<List<Ike3gppData>> ike3gppDataCaptor = ArgumentCaptor.forClass(List.class);
        verify(mMockIke3gppDataListener).onIke3gppDataReceived(ike3gppDataCaptor.capture());

        Ike3gppBackoffTimer backoffTimer = null;
        for (Ike3gppData payload : ike3gppDataCaptor.getValue()) {
            if (payload.getDataType() == Ike3gppData.DATA_TYPE_NOTIFY_BACKOFF_TIMER) {
                backoffTimer = (Ike3gppBackoffTimer) payload;
            }
        }

        assertNotNull(backoffTimer);
        assertEquals(BACKOFF_TIMER, backoffTimer.getBackoffTimer());
        assertEquals(expectedNotifyErrorCause, backoffTimer.getBackoffCause());
    }

    @Test
    public void testIkeAuthWithBackoffTimerWithoutError() throws Exception {
        verifyIkeAuthWith3gppEnabled(
                new IkeNotifyPayload(NOTIFY_TYPE_BACKOFF_TIMER, BACKOFF_TIMER_DATA),
                0 /* ike3gppDataListenerInvocations */);

        // BackoffTimer should be ignored
        verify(mMockIke3gppDataListener, never()).onIke3gppDataReceived(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIke3gppReuseCallback() throws Exception {
        mIkeSessionStateMachine =
                makeAndStartIkeSession(buildIkeSessionParamsIke3gppExtension(PDU_SESSION_ID));
        makeAndStartIkeSession(buildIkeSessionParamsIke3gppExtension(PDU_SESSION_ID));
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testOnlyRfcMobikeEnabled() throws Exception {
        verifyRfcMobikeEnabled(true /* doesPeerSupportMobike */);

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testOnlyRfcMobikeEnabledPeerUnsupported() throws Exception {
        verifyRfcMobikeEnabled(false /* doesPeerSupportMobike */);

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testOnlyRekeyMobilityEnabled() throws Exception {
        verifyOnlyRekeyMobilityEnabled();

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeRekeyMobilityEnabled() throws Exception {
        verifyMobikeRekeyMobilityEnabled(true /* doesPeerSupportMobike */);

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeRekeyMobilityEnabledPeerUnsupported() throws Exception {
        verifyMobikeRekeyMobilityEnabled(false /* doesPeerSupportMobike */);

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeEnabledWithEap() throws Exception {
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        authRelatedPayloads.add(new IkeNotifyPayload(NOTIFY_TYPE_MOBIKE_SUPPORTED));

        verifyCreateIkeLocalIkeAuthPostEap(
                buildIkeSessionParamsWithIkeOptions(IKE_OPTION_MOBIKE),
                authRelatedPayloads,
                true /* isMobikeEnabled */);
        assertTrue(mIkeSessionStateMachine.mEnabledExtensions.contains(EXTENSION_TYPE_MOBIKE));
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeEnabledNattSupportedIpv4() throws Exception {
        verifyMobikeEnabled(true /* doesPeerSupportNatt */, true /* isIpv4 */);

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeEnabledNattUnsupportedIpv4() throws Exception {
        verifyMobikeEnabled(false /* doesPeerSupportNatt */, true /* isIpv4 */);

        killSessionAndVerifyNetworkCallback();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeEnabledNattUnsupportedIpv6() throws Exception {
        verifyMobikeEnabled(false /* doesPeerSupportNatt */, false /* isIpv4 */);

        killSessionAndVerifyNetworkCallback();
    }

    /**
     * Restarts the IkeSessionStateMachine with MOBIKE enabled. If doesPeerSupportMobike, MOBIKE
     * will be active for the Session.
     *
     * @return the registered IkeDefaultNetworkCallack is returned if MOBIKE is active, else null
     */
    @Nullable
    private IkeDefaultNetworkCallback verifyRfcMobikeEnabled(boolean doesPeerSupportMobike)
            throws Exception {
        // Can cast to IkeDefaultNetworkCallback because no Network is specified
        return (IkeDefaultNetworkCallback)
                verifyMobikeEnabled(doesPeerSupportMobike, null /* configuredNetwork */);
    }

    @Nullable
    private IkeDefaultNetworkCallback verifyMobikeRekeyMobilityEnabled(
            boolean doesPeerSupportMobike) throws Exception {
        // Can cast to IkeDefaultNetworkCallback because no Network is specified
        return (IkeDefaultNetworkCallback)
                verifyMobikeEnabled(
                        doesPeerSupportMobike,
                        true /* doesPeerSupportNatt */,
                        true /* isIpv4 */,
                        false /* isEnforcePort4500*/,
                        null /* configuredNetwork */,
                        true /* mobikeEnabled */,
                        true /* rekeyMobilityEnabled */);
    }

    @Nullable
    private IkeDefaultNetworkCallback verifyOnlyRekeyMobilityEnabled() throws Exception {
        // Can cast to IkeDefaultNetworkCallback because no Network is specified
        return (IkeDefaultNetworkCallback)
                verifyMobikeEnabled(
                        false /* doesPeerSupportMobike */,
                        true /* doesPeerSupportNatt */,
                        true /* isIpv4 */,
                        false /* isEnforcePort4500*/,
                        null /* configuredNetwork */,
                        false /* mobikeEnabled */,
                        true /* rekeyMobilityEnabled */);
    }

    @Nullable
    private IkeNetworkCallbackBase verifyMobikeEnabled(
            boolean doesPeerSupportMobike, Network configuredNetwork) throws Exception {
        return verifyMobikeEnabled(
                doesPeerSupportMobike,
                true /* doesPeerSupportNatt */,
                true /* isIpv4 */,
                false /* isEnforcePort4500*/,
                configuredNetwork,
                true /* mobikeEnabled */,
                false /* rekeyMobilityEnabled */);
    }

    @Nullable
    private IkeDefaultNetworkCallback verifyMobikeEnabled(
            boolean doesPeerSupportNatt, boolean isIpv4) throws Exception {
        // Can cast to IkeDefaultNetworkCallback because no Network is specified
        return (IkeDefaultNetworkCallback)
                verifyMobikeEnabled(
                        true /* doesPeerSupportMobike */,
                        doesPeerSupportNatt,
                        isIpv4,
                        false /* isEnforcePort4500*/,
                        null /* configuredNetwork */,
                        true /* mobikeEnabled */,
                        false /* rekeyMobilityEnabled */);
    }

    /** Returns the expected IkeSocket type when MOBIKE is supported by both sides */
    private Class<? extends IkeSocket> getExpectedSocketType(
            boolean doesPeerSupportNatt, boolean isEnforcePort4500, boolean isIpv4) {
        if (doesPeerSupportNatt || isEnforcePort4500) {
            if (isIpv4) {
                return IkeUdpEncapSocket.class;
            } else {
                return IkeUdp6WithEncapPortSocket.class;
            }
        } else {
            if (isIpv4) {
                return IkeUdp4Socket.class;
            } else {
                return IkeUdp6Socket.class;
            }
        }
    }

    @Nullable
    private IkeNetworkCallbackBase verifyMobikeEnabled(
            boolean doesPeerSupportMobike,
            boolean doesPeerSupportNatt,
            boolean isIpv4,
            boolean isEnforcePort4500,
            Network configuredNetwork,
            boolean mobikeEnabled,
            boolean rekeyMobilityEnabled)
            throws Exception {
        mIkeSessionStateMachine =
                restartStateMachineWithMobikeConfigured(
                        configuredNetwork,
                        isEnforcePort4500,
                        isIpv4,
                        mobikeEnabled,
                        rekeyMobilityEnabled);
        new IkeFirstAuthTestPretest().mockIkeInitAndTransitionToIkeAuth();

        // Enable force_udp_encap under IPv4 network because the kernel cannot process both
        // UDP-encap and non-UDP-encap ESP packets for a single SA
        boolean doesEnableForceUdpEncap = isIpv4;

        if (isIpv4) {
            if (doesPeerSupportNatt || doesEnableForceUdpEncap) {
                // Either NAT detected or not detected won't affect the test since both cases
                // indicate the server support NAT-T
                mSpyIkeConnectionCtrl.handleNatDetectionResultInIkeInit(
                        true /* isNatDetected */, mSpyCurrentIkeSaRecord.getLocalSpi());
            } else {
                mSpyIkeConnectionCtrl.markSeverNattUnsupported();
            }
        } else {
            // IKE client does not support IPv6 NAT-T and will not check if the server supports
            // NAT-T when using IPv6 for IKE Session setup.
            mSpyIkeConnectionCtrl.resetSeverNattSupport();
        }

        // Build IKE AUTH response. Include MOBIKE_SUPPORTED if doesPeerSupportMobike is true
        List<IkePayload> authRelatedPayloads = new ArrayList<>();
        IkeAuthPskPayload spyAuthPayload = makeSpyRespPskPayload();
        authRelatedPayloads.add(spyAuthPayload);

        IkeIdPayload respIdPayload = makeRespIdPayload();
        authRelatedPayloads.add(respIdPayload);
        authRelatedPayloads.add(makeConfigPayload());

        if (doesPeerSupportMobike) {
            authRelatedPayloads.add(new IkeNotifyPayload(NOTIFY_TYPE_MOBIKE_SUPPORTED));
        }

        IkeMessage ikeAuthReqMessage =
                verifySharedKeyAuthentication(
                        spyAuthPayload,
                        respIdPayload,
                        authRelatedPayloads,
                        true /* hasChildPayloads */,
                        true /* hasConfigPayloadInResp */,
                        doesPeerSupportMobike,
                        isIpv4,
                        0 /* ike3gppDataListenerInvocations */);
        verifyRetransmissionStopped();

        boolean isMobikeSupportIndicated = false;
        List<IkeNotifyPayload> reqNotifyPayloads =
                ikeAuthReqMessage.getPayloadListForType(
                        PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        for (IkeNotifyPayload notifyPayload : reqNotifyPayloads) {
            if (notifyPayload.notifyType == NOTIFY_TYPE_MOBIKE_SUPPORTED) {
                isMobikeSupportIndicated = true;
                break;
            }
        }

        assertEquals(mobikeEnabled, isMobikeSupportIndicated);

        assertEquals(
                doesPeerSupportMobike,
                mIkeSessionStateMachine.mEnabledExtensions.contains(EXTENSION_TYPE_MOBIKE));

        if (rekeyMobilityEnabled || doesPeerSupportMobike) {
            assertTrue(mSpyIkeConnectionCtrl.isMobilityEnabled());
            assertTrue(
                    getExpectedSocketType(
                                    doesPeerSupportNatt || doesEnableForceUdpEncap,
                                    isEnforcePort4500,
                                    isIpv4)
                            .isInstance(mSpyIkeConnectionCtrl.getIkeSocket()));
        } else {
            assertFalse(mSpyIkeConnectionCtrl.isMobilityEnabled());
        }

        ArgumentCaptor<IkeNetworkCallbackBase> networkCallbackCaptor =
                ArgumentCaptor.forClass(IkeNetworkCallbackBase.class);
        // Expect different NetworkCallback registrations if there is a caller-configured
        // Network
        if (configuredNetwork == null) {
            verify(mMockConnectManager)
                    .registerDefaultNetworkCallback(networkCallbackCaptor.capture(), any());
        } else {
            verify(mMockConnectManager)
                    .registerNetworkCallback(any(), networkCallbackCaptor.capture(), any());
        }

        IkeNetworkCallbackBase networkCallback = networkCallbackCaptor.getValue();
        Class<? extends IkeNetworkCallbackBase> expectedCallbackType =
                configuredNetwork == null
                        ? IkeDefaultNetworkCallback.class
                        : IkeSpecificNetworkCallback.class;
        assertTrue(expectedCallbackType.isInstance(networkCallback));
        return networkCallback;
    }

    private void killSessionAndVerifyNetworkCallback() {
        mIkeSessionStateMachine.killSession();
        mLooper.dispatchAll();

        verify(mMockConnectManager).unregisterNetworkCallback(any(IkeDefaultNetworkCallback.class));
    }

    /** Restarts the IkeSessionStateMachine with MOBIKE configured in the IkeSessionParams. */
    private IkeSessionStateMachine restartStateMachineWithRfcMobikeConfigured() throws Exception {
        return restartStateMachineWithMobikeConfigured(
                null /* network */,
                false /* isEnforcePort4500*/,
                true /* isIpv4 */,
                true /* mobikeEnabled */,
                false /* rekeyMobilityEnabled */);
    }

    private IkeSessionStateMachine restartStateMachineWithMobikeConfigured(
            @Nullable Network configuredNetwork,
            boolean isEnforcePort4500,
            boolean isIpv4,
            boolean mobikeEnabled,
            boolean rekeyMobilityEnabled)
            throws Exception {
        mIkeSessionStateMachine.quitNow();
        mLooper.dispatchAll();

        // Clear ConnectivityManager#getActiveNetwork (#makeAndStartIkeSession() expects no use of
        // ConnectivityManager#getActiveNetwork when there is a configured Network) and the network
        // callback registration call in #setUp().
        resetMockConnectManager();

        // Set local and remote address
        Network network = configuredNetwork == null ? mMockDefaultNetwork : configuredNetwork;
        InetAddress localAddress = isIpv4 ? LOCAL_ADDRESS : LOCAL_ADDRESS_V6;
        InetAddress remoteAddress = isIpv4 ? REMOTE_ADDRESS : REMOTE_ADDRESS_V6;
        setupLocalAddressForNetwork(network, localAddress);
        setupRemoteAddressForNetwork(network, remoteAddress);

        IkeSessionParams.Builder ikeSessionParamsBuilder =
                buildIkeSessionParamsCommon().setAuthPsk(mPsk);

        if (mobikeEnabled) {
            ikeSessionParamsBuilder.addIkeOption(IKE_OPTION_MOBIKE);
        }

        if (rekeyMobilityEnabled) {
            ikeSessionParamsBuilder.addIkeOptionInternal(IKE_OPTION_REKEY_MOBILITY);
        } else {
            // IKE_OPTION_REKEY_MOBILITY will be automatically added with IKE_OPTION_MOBIKE. So
            // explicitly remove it here
            ikeSessionParamsBuilder.removeIkeOption(IKE_OPTION_REKEY_MOBILITY);
        }

        if (isEnforcePort4500) {
            ikeSessionParamsBuilder.addIkeOption(IKE_OPTION_FORCE_PORT_4500);
        }
        if (configuredNetwork != null) {
            ikeSessionParamsBuilder.setNetwork(configuredNetwork);
        }
        return makeAndStartIkeSession(ikeSessionParamsBuilder.build(), localAddress, remoteAddress);
    }

    private void verifyHandleNetworkChange() throws Exception {
        if (SdkLevel.isAtLeastT()) {
            verify(mMockIkeSessionCallback).onClosedWithException(any(IkeException.class));
            assertNull(mIkeSessionStateMachine.getCurrentState());
        } else {
            verify(mMockIkeSessionCallback).onError(any(IkeException.class));
            assertNotNull(mIkeSessionStateMachine.getCurrentState());
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobilityDisabledNetworkDies() throws Exception {
        IkeDefaultNetworkCallback callback =
                verifyRfcMobikeEnabled(false /* doesPeerSupportMobike */);
        callback.onLost(mMockDefaultNetwork);
        mLooper.dispatchAll();

        verifyHandleNetworkChange();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobilityDisabledNetworkUpdates() throws Exception {
        IkeDefaultNetworkCallback callback =
                verifyRfcMobikeEnabled(false /* doesPeerSupportMobike */);
        Network newNetwork = mock(Network.class);
        callback.onAvailable(newNetwork);
        callback.onCapabilitiesChanged(newNetwork, mock(NetworkCapabilities.class));
        callback.onLinkPropertiesChanged(newNetwork, mock(LinkProperties.class));

        mLooper.dispatchAll();

        verifyHandleNetworkChange();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeEnabledNetworkDies() throws Exception {
        IkeDefaultNetworkCallback callback =
                verifyRfcMobikeEnabled(true /* doesPeerSupportMobike */);
        callback.onLost(mMockDefaultNetwork);

        ArgumentCaptor<IkeException> exceptionCaptor = ArgumentCaptor.forClass(IkeException.class);
        verify(mMockIkeSessionCallback).onError(exceptionCaptor.capture());
        IkeNetworkLostException cause = (IkeNetworkLostException) exceptionCaptor.getValue();
        assertEquals(mMockDefaultNetwork, cause.getNetwork());
    }

    private void verifyMobikeActiveMobilityEvent(boolean isEnforcePort4500) throws Exception {
        IkeDefaultNetworkCallback callback =
                (IkeDefaultNetworkCallback)
                        verifyMobikeEnabled(
                                true /* doesPeerSupportMobike */,
                                false /* doesPeerSupportNatt */,
                                true /* isIpv4 */,
                                isEnforcePort4500,
                                null /* configuredNetwork */,
                                true /* mobikeEnabled */,
                                false /* rekeyMobilityEnabled */);

        Network newNetwork = mockNewNetworkAndAddress(true /* isIpv4 */);

        callback.onAvailable(newNetwork);
        callback.onCapabilitiesChanged(newNetwork, mock(NetworkCapabilities.class));
        callback.onLinkPropertiesChanged(
                newNetwork, mMockConnectManager.getLinkProperties(newNetwork));
        mLooper.dispatchAll();

        verifyNetworkAndLocalAddressUpdated(
                newNetwork, UPDATED_LOCAL_ADDRESS, REMOTE_ADDRESS, callback);
        verify(mMockIkeLocalAddressGenerator)
                .generateLocalAddress(
                        eq(newNetwork), eq(true /* isIpv4 */), eq(REMOTE_ADDRESS), anyInt());
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeActiveMobilityEvent() throws Exception {
        verifyMobikeActiveMobilityEvent(false /* isEnforcePort4500 */);
        assertTrue(mSpyIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeActiveMobilityEventWithEnforcePort4500() throws Exception {
        verifyMobikeActiveMobilityEvent(true /* isEnforcePort4500 */);
        assertTrue(mSpyIkeConnectionCtrl.getIkeSocket() instanceof IkeUdpEncapSocket);
    }

    private Network mockNewNetworkAndAddress(boolean isIpv4) throws Exception {
        InetAddress expectedRemoteAddress;
        InetAddress injectedLocalAddress;
        if (isIpv4) {
            expectedRemoteAddress = REMOTE_ADDRESS;
            injectedLocalAddress = UPDATED_LOCAL_ADDRESS;
        } else {
            expectedRemoteAddress = REMOTE_ADDRESS_V6;
            injectedLocalAddress = UPDATED_LOCAL_ADDRESS_V6;
        }

        return mockNewNetworkAndAddress(isIpv4, injectedLocalAddress, expectedRemoteAddress);
    }

    private Network mockNewNetworkAndAddress(
            boolean isIpv4, InetAddress localAddress, InetAddress remoteAddress) throws Exception {
        return mockNewNetworkAndAddress(isIpv4, localAddress, remoteAddress, 1 /* dnsLookups */);
    }

    private Network mockNewNetworkAndAddress(
            boolean isIpv4, InetAddress localAddress, InetAddress remoteAddress, int dnsLookups)
            throws Exception {
        Network newNetwork = mock(Network.class);

        mSpyIkeConnectionCtrl.addRemoteAddress(remoteAddress);

        LinkAddress linkAddress = mock(LinkAddress.class);
        when(linkAddress.getAddress()).thenReturn(localAddress);

        LinkProperties linkProperties = new LinkProperties();
        linkProperties.addLinkAddress(linkAddress);
        when(mMockConnectManager.getLinkProperties(eq(newNetwork))).thenReturn(linkProperties);

        if (!isIpv4) {
            when(linkAddress.isGlobalPreferred()).thenReturn(true);
        }

        setupDnsResolutionForNetwork(newNetwork, dnsLookups, remoteAddress);
        setupLocalAddressForNetwork(newNetwork, localAddress);

        return newNetwork;
    }

    private void verifyNetworkAndLocalAddressUpdated(
            Network underlyingNetwork,
            InetAddress localAddress,
            InetAddress remoteAddress,
            IkeNetworkCallbackBase networkCallback)
            throws Exception {
        assertEquals(underlyingNetwork, mSpyIkeConnectionCtrl.getNetwork());
        assertEquals(underlyingNetwork, mSpyIkeConnectionCtrl.getNetwork());
        assertEquals(localAddress, mSpyIkeConnectionCtrl.getLocalAddress());
        assertEquals(remoteAddress, mSpyIkeConnectionCtrl.getRemoteAddress());

        verifyIkeSaAddresses(
                mIkeSessionStateMachine.mCurrentIkeSaRecord, localAddress, remoteAddress);

        verify(underlyingNetwork, atLeastOnce()).getAllByName(REMOTE_HOSTNAME);

        assertEquals(underlyingNetwork, networkCallback.getNetwork());
        assertEquals(localAddress, networkCallback.getAddress());
    }

    private void verifyIkeSaAddresses(
            IkeSaRecord saRecord, InetAddress localAddress, InetAddress remoteAddress) {
        assertEquals(
                localAddress, saRecord.getInitiatorIkeSecurityParameterIndex().getSourceAddress());
        assertEquals(
                remoteAddress, saRecord.getResponderIkeSecurityParameterIndex().getSourceAddress());
    }

    // TODO : this should be NullPointerException
    @Test(expected = IllegalArgumentException.class)
    public void testSetNetworkNull() throws Exception {
        mIkeSessionStateMachine.setNetwork(null,
                IkeSessionParams.ESP_IP_VERSION_AUTO,
                IkeSessionParams.ESP_ENCAP_TYPE_AUTO,
                IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetNetworkMobikeNotActive() throws Exception {
        Network newNetwork = mock(Network.class);

        mIkeSessionStateMachine.setNetwork(newNetwork,
                IkeSessionParams.ESP_IP_VERSION_AUTO,
                IkeSessionParams.ESP_ENCAP_TYPE_AUTO,
                IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO);
    }

    @Test(expected = IllegalStateException.class)
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkMobikeActiveNetworkNotSpecified() throws Exception {
        Network newNetwork = mock(Network.class);

        verifyRfcMobikeEnabled(true /* doesPeerSupportMobike */);

        mIkeSessionStateMachine.setNetwork(newNetwork,
                IkeSessionParams.ESP_IP_VERSION_AUTO,
                IkeSessionParams.ESP_ENCAP_TYPE_AUTO,
                IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO);
    }

    private void verifySetNetwork(
            IkeNetworkCallbackBase callback, IkeSaRecord rekeySaRecord, State expectedState)
            throws Exception {
        verifySetNetwork(callback, rekeySaRecord, expectedState, true /* isIpv4 */);
    }

    private void verifySetNetwork(
            IkeNetworkCallbackBase callback,
            IkeSaRecord rekeySaRecord,
            State expectedState,
            boolean isIpv4)
            throws Exception {
        Network newNetwork = mockNewNetworkAndAddress(isIpv4);

        mIkeSessionStateMachine.setNetwork(newNetwork,
                IkeSessionParams.ESP_IP_VERSION_AUTO,
                IkeSessionParams.ESP_ENCAP_TYPE_AUTO,
                IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO);
        mLooper.dispatchAll();

        InetAddress expectedUpdatedLocalAddress =
                isIpv4 ? UPDATED_LOCAL_ADDRESS : UPDATED_LOCAL_ADDRESS_V6;
        InetAddress expectedRemoteAddress = isIpv4 ? REMOTE_ADDRESS : REMOTE_ADDRESS_V6;

        verifyNetworkAndLocalAddressUpdated(
                newNetwork, expectedUpdatedLocalAddress, expectedRemoteAddress, callback);
        verify(mMockIkeLocalAddressGenerator)
                .generateLocalAddress(
                        eq(newNetwork), eq(isIpv4), eq(expectedRemoteAddress), anyInt());

        verify(mSpyIkeConnectionCtrl.getIkeSocket())
                .registerIke(
                        mIkeSessionStateMachine.mCurrentIkeSaRecord.getLocalSpi(),
                        mSpyIkeConnectionCtrl);

        if (rekeySaRecord != null) {
            verifyIkeSaAddresses(rekeySaRecord, expectedUpdatedLocalAddress, expectedRemoteAddress);
            verify(mSpyIkeConnectionCtrl.getIkeSocket())
                    .registerIke(rekeySaRecord.getLocalSpi(), mSpyIkeConnectionCtrl);
        }

        assertEquals(expectedState, mIkeSessionStateMachine.getCurrentState());
    }

    private IkeNetworkCallbackBase setupIdleStateMachineWithMobike() throws Exception {
        return setupIdleStateMachineWithMobike(true /* doesPeerSupportNatt */, true /* isIpv4 */);
    }

    private IkeNetworkCallbackBase setupIdleStateMachineWithMobike(
            boolean doesPeerSupportNatt, boolean isIpv4) throws Exception {
        IkeNetworkCallbackBase callback =
                verifyMobikeEnabled(
                        true /* doesPeerSupportMobike */,
                        doesPeerSupportNatt,
                        isIpv4,
                        false /* isEnforcePort4500*/,
                        mMockDefaultNetwork,
                        true /* mobikeEnabled */,
                        false /* rekeyMobilityEnabled */);

        // reset IkeMessageHelper to make verifying outbound req easier
        resetMockIkeMessageHelper();

        mDummyChildSmCallback =
                createChildAndGetChildSessionSmCallback(
                        mMockChildSessionStateMachine, CHILD_SPI_REMOTE, mMockChildSessionCallback);

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);
        mLooper.dispatchAll();

        return callback;
    }

    private void verifySetNetworkInIdleState(
            boolean doesPeerSupportNatt,
            boolean isIpv4BeforeNetworkChange,
            boolean isIpv4AfterNetworkChange)
            throws Exception {
        IkeNetworkCallbackBase callback =
                setupIdleStateMachineWithMobike(doesPeerSupportNatt, isIpv4BeforeNetworkChange);

        verifySetNetwork(
                callback,
                null /* rekeySaRecord */,
                mIkeSessionStateMachine.mMobikeLocalInfo,
                isIpv4AfterNetworkChange);
        assertTrue(
                getExpectedSocketType(
                                doesPeerSupportNatt
                                        || isIpv4AfterNetworkChange /* force UDP-encap */,
                                false /* isEnforcePort4500*/,
                                isIpv4AfterNetworkChange)
                        .isInstance(mSpyIkeConnectionCtrl.getIkeSocket()));
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkInIdleStateNattSupportedIpv4ToIpv6() throws Exception {
        verifySetNetworkInIdleState(
                true /* doesPeerSupportNatt */,
                true /* isIpv4BeforeNetworkChange */,
                false /* isIpv4AfterNetworkChange */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkInIdleStateNattSupportedIpv6ToIpv4() throws Exception {
        verifySetNetworkInIdleState(
                true /* doesPeerSupportNatt */,
                true /* isIpv4BeforeNetworkChange */,
                false /* isIpv4AfterNetworkChange */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkInIdleStateNattSupportedIpv4ToIpv4() throws Exception {
        verifySetNetworkInIdleState(
                true /* doesPeerSupportNatt */,
                true /* isIpv4BeforeNetworkChange */,
                true /* isIpv4AfterNetworkChange */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkInIdleStateNattUnsupportedIpv4ToIpv4() throws Exception {
        verifySetNetworkInIdleState(
                false /* doesPeerSupportNatt */,
                true /* isIpv4BeforeNetworkChange */,
                true /* isIpv4AfterNetworkChange */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkInIdleStateNattUnsupportedIpv6ToIpv6() throws Exception {
        verifySetNetworkInIdleState(
                false /* doesPeerSupportNatt */,
                false /* isIpv4BeforeNetworkChange */,
                false /* isIpv4AfterNetworkChange */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkLocalRekeyState() throws Exception {
        // Start IKE Session + transition to Idle
        IkeNetworkCallbackBase callback =
                verifyMobikeEnabled(true /* doesPeerSupportMobike */, mMockDefaultNetwork);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mIdle);

        verifyRekeyIkeLocalCreateHandlesResponse();

        verifySetNetwork(
                callback,
                mIkeSessionStateMachine.mLocalInitNewIkeSaRecord,
                mIkeSessionStateMachine.mRekeyIkeLocalDelete);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSetNetworkRemoteRekeyState() throws Exception {
        // Start IKE Session + transition to remote rekey
        IkeNetworkCallbackBase callback =
                verifyMobikeEnabled(true /* doesPeerSupportMobike */, mMockDefaultNetwork);

        mIkeSessionStateMachine.mRemoteInitNewIkeSaRecord = mSpyRemoteInitIkeSaRecord;
        mIkeSessionStateMachine.addIkeSaRecord(mSpyRemoteInitIkeSaRecord);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_FORCE_TRANSITION,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
        mLooper.dispatchAll();

        verifySetNetwork(
                callback,
                mIkeSessionStateMachine.mRemoteInitNewIkeSaRecord,
                mIkeSessionStateMachine.mRekeyIkeRemoteDelete);
    }

    private void verifyRcvDeviceIdentityCheckReqAndReply() throws Exception {
        // Receive a Information req with DEVICE_IDENTITY payload
        ReceivedIkePacket dummyRequest = makeDeviceIdentityIkeRequest();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRequest);
        mLooper.dispatchAll();

        IkeMessage resp = verifyAndGetOutboundInformationalResp();

        List<IkeNotifyPayload> notifyPayloads =
                resp.getPayloadListForType(PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        assertEquals(1, notifyPayloads.size());
        IkeNotifyPayload deviceIdentity = notifyPayloads.get(0);
        assertEquals(NOTIFY_TYPE_DEVICE_IDENTITY, deviceIdentity.notifyType);
        assertArrayEquals(DEVICE_IDENTITY_PAYLOAD_IMEI, deviceIdentity.notifyData);
    }

    private void verifyRcvRoutabilityCheckReqAndReply() throws Exception {
        // Receive a routability check request
        ReceivedIkePacket dummyRequest = makeRoutabilityCheckIkeRequest();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRequest);
        mLooper.dispatchAll();

        IkeMessage resp = verifyAndGetOutboundInformationalResp();

        List<IkeNotifyPayload> notifyPayloads =
                resp.getPayloadListForType(PAYLOAD_TYPE_NOTIFY, IkeNotifyPayload.class);
        assertEquals(1, notifyPayloads.size());
        IkeNotifyPayload cookie2 = notifyPayloads.get(0);
        assertEquals(NOTIFY_TYPE_COOKIE2, cookie2.notifyType);
        assertArrayEquals(COOKIE2_DATA, cookie2.notifyData);
    }

    @Test
    public void testRcvRoutabilityCheckReqInIdle() throws Exception {
        setupIdleStateMachine();

        verifyRcvRoutabilityCheckReqAndReply();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState() instanceof IkeSessionStateMachine.Idle);
    }

    @Test
    public void testRcvRoutabilityCheckReqInDpd() throws Exception {
        executeAndVerifySendLocalDPD();
        verifyRcvRoutabilityCheckReqAndReply();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.DpdIkeLocalInfo);
    }

    @Test
    public void testRcvRoutabilityCheckReqInChildProcedureOngoing() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getChildLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_DELETE_CHILD,
                        mMockChildSessionCallback,
                        null /*childParams*/));
        mLooper.dispatchAll();

        // For conveniency to verify outbound routability check response, reset IkeMessageHelper to
        // forget sending Delete Child request
        resetMockIkeMessageHelper();

        verifyRcvRoutabilityCheckReqAndReply();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
    }

    @Test
    public void testRcvRoutabilityCheckReqInLocalRekeyIkeCreate() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();

        // For conveniency to verify outbound routability check response, reset IkeMessageHelper to
        // forget sending Rekey Create request
        resetMockIkeMessageHelper();

        verifyRcvRoutabilityCheckReqAndReply();
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.RekeyIkeLocalCreate);
    }

    private void verifyMobikeLocalInfoSendsRequest(
            boolean migrateToIpv4,
            boolean natSupported,
            boolean natDetected,
            boolean expectNatDetection)
            throws Exception {
        // IKE Session is set up with IPv4 address and with NAT detected
        setupIdleStateMachineWithMobike();

        if (natSupported) {
            mSpyIkeConnectionCtrl.setNatDetected(natDetected);
        } else {
            mSpyIkeConnectionCtrl.markSeverNattUnsupported();
        }

        if (!migrateToIpv4) {
            mSpyIkeConnectionCtrl.setLocalAddress(LOCAL_ADDRESS_V6);
            mSpyIkeConnectionCtrl.setRemoteAddress(REMOTE_ADDRESS_V6);
        }

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mMobikeLocalInfo);
        mLooper.dispatchAll();

        verifyUpdateSaAddressesReq(expectNatDetection);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoSendsRequestNatDetectedIpv4() throws Exception {
        verifyMobikeLocalInfoSendsRequest(
                true /* migrateToIpv4 */,
                true /* natSupported */,
                true /* natDetected */,
                true /* expectNatDetection */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoSendsRequestNatNotDetectedIpv4() throws Exception {
        verifyMobikeLocalInfoSendsRequest(
                true /* migrateToIpv4 */,
                true /* natSupported */,
                false /* natDetected */,
                true /* expectNatDetection */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoSendsRequestNattUnsupportedIpv4() throws Exception {
        verifyMobikeLocalInfoSendsRequest(
                true /* migrateToIpv4 */,
                false /* natSupported */,
                false /* natDetected */,
                false /* expectNatDetection */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoSendsRequestNatDetectedIpv6() throws Exception {
        verifyMobikeLocalInfoSendsRequest(
                false /* migrateToIpv4 */,
                true /* natSupported */,
                true /* natDetected */,
                true /* expectNatDetection */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoSendsRequestNatNotDetectedIpv6() throws Exception {
        verifyMobikeLocalInfoSendsRequest(
                false /* migrateToIpv4 */,
                true /* natSupported */,
                false /* natDetected */,
                false /* expectNatDetection */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoSendsRequestNattUnsupportedIpv6() throws Exception {
        verifyMobikeLocalInfoSendsRequest(
                false /* migrateToIpv4 */,
                false /* natSupported */,
                false /* natDetected */,
                false /* expectNatDetection */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoHandlesResponseWithNatDetection() throws Exception {
        setupIdleStateMachineWithMobike();

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mMobikeLocalInfo);
        mLooper.dispatchAll();

        verifyUpdateSaAddressesResp(
                true /* expectNatDetected */,
                mSpyIkeConnectionCtrl.getNetwork(),
                mSpyIkeConnectionCtrl.getLocalAddress(),
                mSpyIkeConnectionCtrl.getRemoteAddress());
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoHandlesResponseWithoutNatDetection() throws Exception {
        setupIdleStateMachineWithMobike();
        mSpyIkeConnectionCtrl.markSeverNattUnsupported();

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mMobikeLocalInfo);
        mLooper.dispatchAll();

        verifyUpdateSaAddressesResp(
                false /* expectNatDetected */,
                mSpyIkeConnectionCtrl.getNetwork(),
                mSpyIkeConnectionCtrl.getLocalAddress(),
                mSpyIkeConnectionCtrl.getRemoteAddress());
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoHandlesResponseWithNatDetectedIpv6() throws Exception {
        setupIdleStateMachineWithMobike();

        // Migrate to Ipv6
        mSpyIkeConnectionCtrl.setLocalAddress(LOCAL_ADDRESS_V6);
        mSpyIkeConnectionCtrl.setRemoteAddress(REMOTE_ADDRESS_V6);

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mMobikeLocalInfo);
        mLooper.dispatchAll();

        // Send response with NAT_DETECTION payloads
        List<Integer> respPayloadTypeList = new ArrayList<>();
        List<String> respPayloadHexStringList = new ArrayList<>();
        respPayloadTypeList.add(PAYLOAD_TYPE_NOTIFY);
        respPayloadTypeList.add(PAYLOAD_TYPE_NOTIFY);
        respPayloadHexStringList.add(NAT_DETECTION_SOURCE_PAYLOAD_HEX_STRING);
        respPayloadHexStringList.add(NAT_DETECTION_DESTINATION_PAYLOAD_HEX_STRING);

        ReceivedIkePacket respIkePacket =
                makeDummyEncryptedReceivedIkePacket(
                        mSpyCurrentIkeSaRecord,
                        EXCHANGE_TYPE_INFORMATIONAL,
                        true /* isResp */,
                        respPayloadTypeList,
                        respPayloadHexStringList);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, respIkePacket);
        mLooper.dispatchAll();

        // Verify IKE Session was closed properly
        assertNull(mIkeSessionStateMachine.getCurrentState());
        verify(mMockIkeSessionCallback).onClosedWithException(any(IkeInternalException.class));
    }

    private void verifyUpdateSaAddressesReq(boolean expectNatDetection) throws Exception {
        List<IkePayload> reqPayloadList = verifyOutInfoMsgHeaderAndGetPayloads(false /* isResp */);
        int expectedPayloads = expectNatDetection ? 3 : 1;
        assertEquals(expectedPayloads, reqPayloadList.size());
        assertTrue(isNotifyExist(reqPayloadList, NOTIFY_TYPE_UPDATE_SA_ADDRESSES));

        if (expectNatDetection) {
            assertTrue(isNotifyExist(reqPayloadList, NOTIFY_TYPE_NAT_DETECTION_SOURCE_IP));
            assertTrue(isNotifyExist(reqPayloadList, NOTIFY_TYPE_NAT_DETECTION_DESTINATION_IP));
        }
    }

    private void verifyUpdateSaAddressesResp(
            boolean expectNatDetected,
            Network expectedNetwork,
            InetAddress expectedLocalAddr,
            InetAddress expectedRemoteAddr)
            throws Exception {
        List<Integer> respPayloadTypeList = new ArrayList<>();
        List<String> respPayloadHexStringList = new ArrayList<>();

        // For simplicity, if the test tries to verify a case when there is not NAT detected, just
        // do not include NAT Detection payload in the response
        if (!expectNatDetected) {
            respPayloadTypeList.add(PAYLOAD_TYPE_NOTIFY);
            respPayloadHexStringList.add(NAT_DETECTION_SOURCE_PAYLOAD_HEX_STRING);

            respPayloadTypeList.add(PAYLOAD_TYPE_NOTIFY);
            respPayloadHexStringList.add(NAT_DETECTION_DESTINATION_PAYLOAD_HEX_STRING);
        }

        ReceivedIkePacket respIkePacket =
                makeDummyEncryptedReceivedIkePacket(
                        mSpyCurrentIkeSaRecord,
                        EXCHANGE_TYPE_INFORMATIONAL,
                        true /* isResp */,
                        respPayloadTypeList,
                        respPayloadHexStringList);
        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, respIkePacket);
        mLooper.dispatchAll();

        int expectedNatStatus = expectNatDetected ? NAT_DETECTED : NAT_TRAVERSAL_UNSUPPORTED;
        assertEquals(expectedNatStatus, mSpyIkeConnectionCtrl.getNatStatus());

        ArgumentCaptor<IkeSessionConnectionInfo> connectionInfoCaptor =
                ArgumentCaptor.forClass(IkeSessionConnectionInfo.class);
        verify(mMockIkeSessionCallback)
                .onIkeSessionConnectionInfoChanged(connectionInfoCaptor.capture());

        IkeSessionConnectionInfo newConnectionInfo = connectionInfoCaptor.getValue();
        assertEquals(expectedNetwork, newConnectionInfo.getNetwork());
        assertEquals(expectedLocalAddr, newConnectionInfo.getLocalAddress());
        assertEquals(expectedRemoteAddr, newConnectionInfo.getRemoteAddress());

        // TODO(b/172015298): verify IPsec SAs migrated instead of rekey when kernel supports it

        // Verify that Child Rekey (MOBIKE) initiated after successful UPDATE_SA_ADDRESSES resp
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.ChildProcedureOngoing);
        verify(mMockChildSessionStateMachine)
                .performMigration(eq(expectedLocalAddr), eq(expectedRemoteAddr), any());
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testNattKeepaliveStoppedDuringMobilityEvent() throws Exception {
        // IKE Session established with IPv4 address and a NAT detected
        IkeNetworkCallbackBase callback = setupIdleStateMachineWithMobike();

        verifySetNetwork(
                callback,
                null /* rekeySaRecord */,
                mIkeSessionStateMachine.mMobikeLocalInfo,
                false /* isIpv4 */);

        // Keepalive for the old UDP encap socket stopped
        verify(mMockIkeNattKeepalive).stop();

        // Keepalive is no longer needed since IKE Session switches to IPv6
        assertNull(mSpyIkeConnectionCtrl.getIkeNattKeepalive());
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoHandlesDeleteRequest() throws Exception {
        setupIdleStateMachineWithMobike();

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mMobikeLocalInfo);
        mLooper.dispatchAll();

        // Reset to ignore UPDATE_SA_ADDRESSES req sent when entering MobikeLocalInfo
        resetMockIkeMessageHelper();

        // Reset to ignore IkeSessionCallback#onOpened from setting up the state machine
        resetSpyUserCbExecutor();

        verifyIkeDeleteRequestHandled(mIkeSessionStateMachine.mMobikeLocalInfo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMobikeLocalInfoHandlesNonDeleteRequest() throws Exception {
        setupIdleStateMachineWithMobike();

        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mMobikeLocalInfo);
        mLooper.dispatchAll();

        // Reset to ignore UPDATE_SA_ADDRESSES req sent when entering MobikeLocalInfo
        resetMockIkeMessageHelper();

        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET, makeRoutabilityCheckIkeRequest());
        mLooper.dispatchAll();

        verifyIncrementRemoteReqMsgId();

        List<IkePayload> respPayloads = verifyOutInfoMsgHeaderAndGetPayloads(true /* isResp */);
        assertEquals(1, respPayloads.size());
        IkeNotifyPayload notifyPayload = (IkeNotifyPayload) respPayloads.get(0);
        assertEquals(ERROR_TYPE_TEMPORARY_FAILURE, notifyPayload.notifyType);

        assertEquals(
                mIkeSessionStateMachine.mMobikeLocalInfo,
                mIkeSessionStateMachine.getCurrentState());
    }

    private void setupAndVerifyDnsLookupsOnSetNetwork(
            int dnsLookupsForSuccess, int expectedDnsLookups, boolean expectSessionClosed)
            throws Exception {
        final IkeNetworkCallbackBase callback = setupIdleStateMachineWithMobike();

        final Network newNetwork =
                mockNewNetworkAndAddress(
                        false /* isIpv4 */,
                        UPDATED_LOCAL_ADDRESS_V6,
                        REMOTE_ADDRESS_V6,
                        dnsLookupsForSuccess);

        mIkeSessionStateMachine.setNetwork(newNetwork,
                IkeSessionParams.ESP_IP_VERSION_AUTO,
                IkeSessionParams.ESP_ENCAP_TYPE_AUTO,
                IkeSessionParams.NATT_KEEPALIVE_INTERVAL_AUTO);
        mLooper.dispatchAll();

        verify(newNetwork, times(expectedDnsLookups)).getAllByName(REMOTE_HOSTNAME);
        if (expectSessionClosed) {
            assertNull(mIkeSessionStateMachine.getCurrentState());
            verifyFireCallbackOnDnsFailure(mMockIkeSessionCallback, mIkeSessionStateMachine.mIdle);
        } else {
            assertTrue(mSpyIkeConnectionCtrl.getAllRemoteIpv4Addresses().isEmpty());
            assertEquals(
                    Arrays.asList(REMOTE_ADDRESS_V6),
                    mSpyIkeConnectionCtrl.getAllRemoteIpv6Addresses());
            verifyNetworkAndLocalAddressUpdated(
                    newNetwork, UPDATED_LOCAL_ADDRESS_V6, REMOTE_ADDRESS_V6, callback);
        }
    }

    private void verifyRetransmitSuspendedAndResumedOnNewNetwork(
            Class<?> expectedStateOfSuspended, Class<?> expectedStateOfResumed) {
        // Make sure the retransmit flag is set to suspended.
        assertTrue(mIkeSessionStateMachine.mIsRetransmitSuspended);

        // Make sure if there is no future retransmission.
        verifyRetransmissionStopped();

        // Make sure the state machine is still alive.
        assertTrue(expectedStateOfSuspended.isInstance(mIkeSessionStateMachine.getCurrentState()));

        // Elapse all retransmission timeouts.
        int[] timeouts =
                mIkeSessionStateMachine.mIkeSessionParams.getRetransmissionTimeoutsMillis();
        for (long delay : timeouts) {
            mLooper.dispatchAll();
            mLooper.moveTimeForward(delay);
        }
        mLooper.dispatchAll();

        // Connect to a new underlying network.
        // DPD packet should be sent by resuming retransmission.
        mIkeSessionStateMachine.onUnderlyingNetworkUpdated();
        mLooper.dispatchAll();

        // Make sure the retransmit flag is set to unsuspended.
        assertFalse(mIkeSessionStateMachine.mIsRetransmitSuspended);

        // Make sure if there is future retransmission.
        verifyRetransmissionStarted();

        // Make sure the state machine is still alive.
        assertTrue(expectedStateOfResumed.isInstance(mIkeSessionStateMachine.getCurrentState()));
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSuspendRetransmission_inDpd() throws Exception {
        setupIdleStateMachineWithMobike();

        // Start DPD task.
        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mDpdIkeLocalInfo);
        mLooper.dispatchAll();

        // Verify that retransmission has started.
        verifyRetransmissionStarted();
        verifyEmptyInformationalSent(1, false /* expectedResp*/);

        // Disconnect from the underlying network.
        mIkeSessionStateMachine.onUnderlyingNetworkDied(mMockDefaultNetwork);
        mLooper.dispatchAll();

        verifyRetransmitSuspendedAndResumedOnNewNetwork(
                IkeSessionStateMachine.DpdIkeLocalInfo.class,
                IkeSessionStateMachine.DpdIkeLocalInfo.class);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSuspendRetransmission_beforeEnteringDpd() throws Exception {
        setupIdleStateMachineWithMobike();

        // Disconnect from the underlying network.
        mIkeSessionStateMachine.onUnderlyingNetworkDied(mMockDefaultNetwork);

        // Start DPD task.
        mIkeSessionStateMachine.sendMessage(
                CMD_FORCE_TRANSITION, mIkeSessionStateMachine.mDpdIkeLocalInfo);
        mLooper.dispatchAll();

        verifyRetransmitSuspendedAndResumedOnNewNetwork(
                IkeSessionStateMachine.DpdIkeLocalInfo.class,
                IkeSessionStateMachine.DpdIkeLocalInfo.class);
        verifyEmptyInformationalSent(1, false /* expectedResp*/);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSuspendRetransmission_inIdle() throws Exception {
        setupIdleStateMachineWithMobike();

        // Disconnect from the underlying network.
        mIkeSessionStateMachine.onUnderlyingNetworkDied(mMockDefaultNetwork);
        mLooper.dispatchAll();

        verifyRetransmitSuspendedAndResumedOnNewNetwork(
                IkeSessionStateMachine.Idle.class, IkeSessionStateMachine.MobikeLocalInfo.class);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSuspendRetransmission_inRekeyChild() throws Exception {
        // Step 1. Init with the mobike and verify sending RekeyChildCreate request
        // Send the RekeyChildCreate
        mockSendRekeyChildReq(true);
        // Verify that message is EXCHANGE_TYPE_CREATE_CHILD_SA.
        verify(mMockIkeMessageHelper, times(1))
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        eq(mSpyCurrentIkeSaRecord),
                        mIkeMessageCaptor.capture(),
                        anyBoolean(),
                        anyInt());
        IkeMessage createChildMessage = mIkeMessageCaptor.getValue();
        assertEquals(
                IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA, createChildMessage.ikeHeader.exchangeType);

        // Verify that retransmission has started.
        verifyRetransmissionStarted();

        // Step 2. Underlying network died, verify retransmission is suspended
        // Disconnect from the underlying network.
        mIkeSessionStateMachine.onUnderlyingNetworkDied(mMockDefaultNetwork);
        mLooper.dispatchAll();
        // Make sure the retransmit flag is set to suspended.
        assertTrue(mIkeSessionStateMachine.mIsRetransmitSuspended);
        // Make sure if there is no future retransmission.
        verifyRetransmissionStopped();

        // Step 3. Receive a response with the last packet before the network dies, verify child
        // notifies to send the RekeyChildDelete request but should not be sent.
        // Mocking receiving response
        ReceivedIkePacket dummyCreateChildResp = makeCreateChildCreateMessage(true /*isResp*/);
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyCreateChildResp);
        mLooper.dispatchAll();
        // Make sure it is received.
        verify(mMockChildSessionStateMachine)
                .receiveResponse(eq(IkeHeader.EXCHANGE_TYPE_CREATE_CHILD_SA), any());
        // Send the DeleteRekeyChildRequest
        List<IkePayload> mockRekeyDeletePayloads = Arrays.asList(mock(IkePayload.class));
        mDummyChildSmCallback.onOutboundPayloadsReady(
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL,
                false /*isResp*/,
                mockRekeyDeletePayloads,
                mMockChildSessionStateMachine);
        mLooper.dispatchAll();
        // Verify that message is EXCHANGE_TYPE_INFORMATIONAL.
        verify(mMockIkeMessageHelper, times(2))
                .encryptAndEncode(
                        anyObject(),
                        anyObject(),
                        eq(mSpyCurrentIkeSaRecord),
                        mIkeMessageCaptor.capture(),
                        anyBoolean(),
                        anyInt());
        IkeMessage deleteChildMessage = mIkeMessageCaptor.getValue();
        assertEquals(
                IkeHeader.EXCHANGE_TYPE_INFORMATIONAL, deleteChildMessage.ikeHeader.exchangeType);

        // Step 4. Network updated, verify the RekeyChildDelete request is sent
        verifyRetransmitSuspendedAndResumedOnNewNetwork(
                IkeSessionStateMachine.ChildProcedureOngoing.class,
                IkeSessionStateMachine.ChildProcedureOngoing.class);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testDnsLookupOnSetNetwork() throws Exception {
        setupAndVerifyDnsLookupsOnSetNetwork(
                1 /* dnsLookupsForSuccess */,
                1 /* expectedDnsLookups */,
                false /* expectSessionClosed */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testDnsLookupOnSetNetworkWithDnsRetries() throws Exception {
        setupAndVerifyDnsLookupsOnSetNetwork(
                2 /* dnsLookupsForSuccess */,
                2 /* expectedDnsLookups */,
                false /* expectSessionClosed */);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testDnsLookupOnSetNetworkWithDnsFailure() throws Exception {
        // Require more lookups for successful DNS than IKE allows to force failure
        setupAndVerifyDnsLookupsOnSetNetwork(
                4 /* dnsLookupsForSuccess */,
                3 /* expectedDnsLookups */,
                true /* expectSessionClosed */);
    }

    @Test
    public void testNattKeepaliveStarts() throws Exception {
        IkeSessionParams sessionParams =
                buildIkeSessionParamsCommon()
                        .setAuthPsk(mPsk)
                        .build();

        // Restart IkeSessionStateMachine with NATT Keepalive delay configured
        setupFirstIkeSa();
        mIkeSessionStateMachine.quitNow();

        mIkeSessionStateMachine = makeAndStartIkeSession(sessionParams);
        mIkeSessionStateMachine.openSession();
        mLooper.dispatchAll();

        mIkeSessionStateMachine.sendMessage(CMD_RECEIVE_IKE_PACKET, makeIkeInitResponse());
        mLooper.dispatchAll();

        verify(mMockIkeNattKeepalive).start();
    }

    @Test
    public void testHandleRetransmissionTimeout() throws Exception {
        mIkeSessionStateMachine.sendMessage(IkeSessionStateMachine.CMD_LOCAL_REQUEST_CREATE_IKE);

        for (long delay :
                mIkeSessionStateMachine.mIkeSessionParams.getRetransmissionTimeoutsMillis()) {
            mLooper.dispatchAll();
            mLooper.moveTimeForward(delay);
        }

        mLooper.dispatchAll();
        assertNull(mIkeSessionStateMachine.getCurrentState());

        if (SdkLevel.isAtLeastT()) {
            verify(mMockIkeSessionCallback).onClosedWithException(
                    argThat(e -> e instanceof IkeIOException
                            && e.getCause() instanceof IkeTimeoutException));
        } else {
            verify(mMockIkeSessionCallback).onClosedWithException(
                    argThat(e -> e instanceof IkeInternalException
                            && e.getCause() instanceof IOException));
        }
    }

    private void verifyLivenessStatusCallback(
            @IkeSessionCallback.LivenessStatus int expectedStatus) {
        verify(mMockIkeSessionCallback).onLivenessStatusChanged(eq(expectedStatus));
    }

    private void executeAndVerifyRequestLivenessCheck() throws Exception {
        setupIdleStateMachine();

        mIkeSessionStateMachine.requestLivenessCheck();
        mLooper.dispatchAll();

        verifyEmptyInformationalSent(1, false /* expectedResp*/);
        verifyLivenessStatusCallback(IkeSessionCallback.LIVENESS_STATUS_ON_DEMAND_STARTED);
        resetMockIkeMessageHelper();
    }

    @Test
    public void testDpdOnDemandIkeLocalInfoRcvDpdReq() throws Exception {
        executeAndVerifyRequestLivenessCheck();
        mIkeSessionStateMachine.sendMessage(
                CMD_RECEIVE_IKE_PACKET, makeDpdIkeRequest(mSpyCurrentIkeSaRecord));
        mLooper.dispatchAll();

        verifyEmptyInformationalSent(1, true /* expectedResp*/);
        verifyLivenessStatusCallback(IkeSessionCallback.LIVENESS_STATUS_SUCCESS);
        assertTrue(
                mIkeSessionStateMachine.getCurrentState()
                        instanceof IkeSessionStateMachine.DpdOnDemandIkeLocalInfo);
    }

    @Test
    public void testDpdOnDemandIkeLocalInfoLivenessRetransmissionTimeout() throws Exception {
        executeAndVerifyRequestLivenessCheck();

        int[] timeouts =
                mIkeSessionStateMachine.mIkeSessionParams.getLivenessRetransmissionTimeoutsMillis();
        for (long delay : timeouts) {
            mLooper.dispatchAll();
            mLooper.moveTimeForward(delay);
        }
        mLooper.dispatchAll();

        verifyLivenessStatusCallback(IkeSessionCallback.LIVENESS_STATUS_FAILURE);
        if (SdkLevel.isAtLeastT()) {
            verify(mMockIkeSessionCallback)
                    .onClosedWithException(
                            argThat(
                                    e ->
                                            e instanceof IkeIOException
                                                    && e.getCause()
                                                            instanceof IkeTimeoutException));
        } else {
            verify(mMockIkeSessionCallback)
                    .onClosedWithException(
                            argThat(
                                    e ->
                                            e instanceof IkeInternalException
                                                    && e.getCause() instanceof IOException));
        }
    }

    @Test
    public void testLivenessCheckWithIncomingPacketsInBusyState() throws Exception {
        setupIdleStateMachine();

        // Send Rekey-Create request
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_EXECUTE_LOCAL_REQ,
                mLocalRequestFactory.getIkeLocalRequest(
                        IkeSessionStateMachine.CMD_LOCAL_REQUEST_REKEY_IKE));
        mLooper.dispatchAll();
        verifyRetransmissionStarted();

        // Request to check liveness during local rekey status
        mIkeSessionStateMachine.requestLivenessCheck();
        mLooper.dispatchAll();

        verifyLivenessStatusCallback(IkeSessionCallback.LIVENESS_STATUS_BACKGROUND_STARTED);

        // Prepare "rekeyed" SA
        setupRekeyedIkeSa(mSpyLocalInitIkeSaRecord);

        // Receive Rekey response
        ReceivedIkePacket dummyRekeyIkeRespReceivedPacket = makeRekeyIkeResponse();
        mIkeSessionStateMachine.sendMessage(
                IkeSessionStateMachine.CMD_RECEIVE_IKE_PACKET, dummyRekeyIkeRespReceivedPacket);
        mLooper.dispatchAll();

        verifyLivenessStatusCallback(IkeSessionCallback.LIVENESS_STATUS_SUCCESS);
    }
}
