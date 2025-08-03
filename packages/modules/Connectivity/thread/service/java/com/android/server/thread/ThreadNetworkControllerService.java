/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.server.thread;

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.net.MulticastRoutingConfig.CONFIG_FORWARD_NONE;
import static android.net.MulticastRoutingConfig.FORWARD_SELECTED;
import static android.net.MulticastRoutingConfig.FORWARD_WITH_MIN_SCOPE;
import static android.net.thread.ActiveOperationalDataset.CHANNEL_PAGE_24_GHZ;
import static android.net.thread.ActiveOperationalDataset.LENGTH_EXTENDED_PAN_ID;
import static android.net.thread.ActiveOperationalDataset.LENGTH_MESH_LOCAL_PREFIX_BITS;
import static android.net.thread.ActiveOperationalDataset.LENGTH_NETWORK_KEY;
import static android.net.thread.ActiveOperationalDataset.LENGTH_PSKC;
import static android.net.thread.ActiveOperationalDataset.MESH_LOCAL_PREFIX_FIRST_BYTE;
import static android.net.thread.ActiveOperationalDataset.SecurityPolicy.DEFAULT_ROTATION_TIME_HOURS;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_DETACHED;
import static android.net.thread.ThreadNetworkController.STATE_DISABLED;
import static android.net.thread.ThreadNetworkController.STATE_DISABLING;
import static android.net.thread.ThreadNetworkController.STATE_ENABLED;
import static android.net.thread.ThreadNetworkController.THREAD_VERSION_1_3;
import static android.net.thread.ThreadNetworkException.ERROR_ABORTED;
import static android.net.thread.ThreadNetworkException.ERROR_BUSY;
import static android.net.thread.ThreadNetworkException.ERROR_FAILED_PRECONDITION;
import static android.net.thread.ThreadNetworkException.ERROR_INTERNAL_ERROR;
import static android.net.thread.ThreadNetworkException.ERROR_REJECTED_BY_PEER;
import static android.net.thread.ThreadNetworkException.ERROR_RESOURCE_EXHAUSTED;
import static android.net.thread.ThreadNetworkException.ERROR_RESPONSE_BAD_FORMAT;
import static android.net.thread.ThreadNetworkException.ERROR_THREAD_DISABLED;
import static android.net.thread.ThreadNetworkException.ERROR_TIMEOUT;
import static android.net.thread.ThreadNetworkException.ERROR_UNSUPPORTED_CHANNEL;
import static android.net.thread.ThreadNetworkManager.DISALLOW_THREAD_NETWORK;
import static android.net.thread.ThreadNetworkManager.PERMISSION_THREAD_NETWORK_PRIVILEGED;

import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_ABORT;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_BUSY;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_FAILED_PRECONDITION;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_INVALID_STATE;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_NO_BUFS;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_PARSE;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_REASSEMBLY_TIMEOUT;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_REJECTED;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_RESPONSE_TIMEOUT;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_THREAD_DISABLED;
import static com.android.server.thread.openthread.IOtDaemon.ErrorCode.OT_ERROR_UNSUPPORTED_CHANNEL;
import static com.android.server.thread.openthread.IOtDaemon.OT_STATE_DISABLED;
import static com.android.server.thread.openthread.IOtDaemon.OT_STATE_DISABLING;
import static com.android.server.thread.openthread.IOtDaemon.OT_STATE_ENABLED;
import static com.android.server.thread.openthread.IOtDaemon.TUN_IF_NAME;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.LocalNetworkConfig;
import android.net.LocalNetworkInfo;
import android.net.MulticastRoutingConfig;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.net.TestNetworkSpecifier;
import android.net.thread.ActiveOperationalDataset;
import android.net.thread.ActiveOperationalDataset.SecurityPolicy;
import android.net.thread.IActiveOperationalDatasetReceiver;
import android.net.thread.IOperationReceiver;
import android.net.thread.IOperationalDatasetCallback;
import android.net.thread.IStateCallback;
import android.net.thread.IThreadNetworkController;
import android.net.thread.OperationalDatasetTimestamp;
import android.net.thread.PendingOperationalDataset;
import android.net.thread.ThreadNetworkController;
import android.net.thread.ThreadNetworkController.DeviceRole;
import android.net.thread.ThreadNetworkException.ErrorCode;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceManagerWrapper;
import com.android.server.thread.openthread.BackboneRouterState;
import com.android.server.thread.openthread.BorderRouterConfigurationParcel;
import com.android.server.thread.openthread.IChannelMasksReceiver;
import com.android.server.thread.openthread.IOtDaemon;
import com.android.server.thread.openthread.IOtDaemonCallback;
import com.android.server.thread.openthread.IOtStatusReceiver;
import com.android.server.thread.openthread.Ipv6AddressInfo;
import com.android.server.thread.openthread.OtDaemonState;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Implementation of the {@link ThreadNetworkController} API.
 *
 * <p>Threading model: This class is not Thread-safe and should only be accessed from the
 * ThreadNetworkService class. Additional attention should be paid to handle the threading code
 * correctly: 1. All member fields other than `mHandler` and `mContext` MUST be accessed from the
 * thread of `mHandler` 2. In the @Override methods, the actual work MUST be dispatched to the
 * HandlerThread except for arguments or permissions checking
 */
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
final class ThreadNetworkControllerService extends IThreadNetworkController.Stub {
    private static final String TAG = "ThreadNetworkService";

    // Below member fields can be accessed from both the binder and handler threads

    private final Context mContext;
    private final Handler mHandler;

    // Below member fields can only be accessed from the handler thread (`mHandler`). In
    // particular, the constructor does not run on the handler thread, so it must not touch any of
    // the non-final fields, nor must it mutate any of the non-final fields inside these objects.

    private final NetworkProvider mNetworkProvider;
    private final Supplier<IOtDaemon> mOtDaemonSupplier;
    private final ConnectivityManager mConnectivityManager;
    private final TunInterfaceController mTunIfController;
    private final InfraInterfaceController mInfraIfController;
    private final NsdPublisher mNsdPublisher;
    private final OtDaemonCallbackProxy mOtDaemonCallbackProxy = new OtDaemonCallbackProxy();

    @Nullable private IOtDaemon mOtDaemon;
    @Nullable private NetworkAgent mNetworkAgent;
    @Nullable private NetworkAgent mTestNetworkAgent;

    private MulticastRoutingConfig mUpstreamMulticastRoutingConfig = CONFIG_FORWARD_NONE;
    private MulticastRoutingConfig mDownstreamMulticastRoutingConfig = CONFIG_FORWARD_NONE;
    private Network mUpstreamNetwork;
    private NetworkRequest mUpstreamNetworkRequest;
    private UpstreamNetworkCallback mUpstreamNetworkCallback;
    private TestNetworkSpecifier mUpstreamTestNetworkSpecifier;
    private final HashMap<Network, String> mNetworkToInterface;
    private final ThreadPersistentSettings mPersistentSettings;
    private final UserManager mUserManager;
    private boolean mUserRestricted;

    private BorderRouterConfigurationParcel mBorderRouterConfig;

    @VisibleForTesting
    ThreadNetworkControllerService(
            Context context,
            Handler handler,
            NetworkProvider networkProvider,
            Supplier<IOtDaemon> otDaemonSupplier,
            ConnectivityManager connectivityManager,
            TunInterfaceController tunIfController,
            InfraInterfaceController infraIfController,
            ThreadPersistentSettings persistentSettings,
            NsdPublisher nsdPublisher,
            UserManager userManager) {
        mContext = context;
        mHandler = handler;
        mNetworkProvider = networkProvider;
        mOtDaemonSupplier = otDaemonSupplier;
        mConnectivityManager = connectivityManager;
        mTunIfController = tunIfController;
        mInfraIfController = infraIfController;
        mUpstreamNetworkRequest = newUpstreamNetworkRequest();
        mNetworkToInterface = new HashMap<Network, String>();
        mBorderRouterConfig = new BorderRouterConfigurationParcel();
        mPersistentSettings = persistentSettings;
        mNsdPublisher = nsdPublisher;
        mUserManager = userManager;
    }

    public static ThreadNetworkControllerService newInstance(
            Context context, ThreadPersistentSettings persistentSettings) {
        HandlerThread handlerThread = new HandlerThread("ThreadHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        NetworkProvider networkProvider =
                new NetworkProvider(context, handlerThread.getLooper(), "ThreadNetworkProvider");

        return new ThreadNetworkControllerService(
                context,
                handler,
                networkProvider,
                () -> IOtDaemon.Stub.asInterface(ServiceManagerWrapper.waitForService("ot_daemon")),
                context.getSystemService(ConnectivityManager.class),
                new TunInterfaceController(TUN_IF_NAME),
                new InfraInterfaceController(),
                persistentSettings,
                NsdPublisher.newInstance(context, handler),
                context.getSystemService(UserManager.class));
    }

    private static Inet6Address bytesToInet6Address(byte[] addressBytes) {
        try {
            return (Inet6Address) Inet6Address.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            // This is unlikely to happen unless the Thread daemon is critically broken
            return null;
        }
    }

    private static InetAddress addressInfoToInetAddress(Ipv6AddressInfo addressInfo) {
        return bytesToInet6Address(addressInfo.address);
    }

    private static LinkAddress newLinkAddress(Ipv6AddressInfo addressInfo) {
        long deprecationTimeMillis =
                addressInfo.isPreferred
                        ? LinkAddress.LIFETIME_PERMANENT
                        : SystemClock.elapsedRealtime();

        InetAddress address = addressInfoToInetAddress(addressInfo);

        // flags and scope will be adjusted automatically depending on the address and
        // its lifetimes.
        return new LinkAddress(
                address,
                addressInfo.prefixLength,
                0 /* flags */,
                0 /* scope */,
                deprecationTimeMillis,
                LinkAddress.LIFETIME_PERMANENT /* expirationTime */);
    }

    private NetworkRequest newUpstreamNetworkRequest() {
        NetworkRequest.Builder builder = new NetworkRequest.Builder().clearCapabilities();

        if (mUpstreamTestNetworkSpecifier != null) {
            return builder.addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                    .setNetworkSpecifier(mUpstreamTestNetworkSpecifier)
                    .build();
        }
        return builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
    }

    private LocalNetworkConfig newLocalNetworkConfig() {
        return new LocalNetworkConfig.Builder()
                .setUpstreamMulticastRoutingConfig(mUpstreamMulticastRoutingConfig)
                .setDownstreamMulticastRoutingConfig(mDownstreamMulticastRoutingConfig)
                .setUpstreamSelector(mUpstreamNetworkRequest)
                .build();
    }

    private void initializeOtDaemon() {
        try {
            getOtDaemon();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to initialize ot-daemon");
        }
    }

    private IOtDaemon getOtDaemon() throws RemoteException {
        checkOnHandlerThread();

        if (mOtDaemon != null) {
            return mOtDaemon;
        }

        IOtDaemon otDaemon = mOtDaemonSupplier.get();
        if (otDaemon == null) {
            throw new RemoteException("Internal error: failed to start OT daemon");
        }
        otDaemon.initialize(mTunIfController.getTunFd(), isEnabled(), mNsdPublisher);
        otDaemon.registerStateCallback(mOtDaemonCallbackProxy, -1);
        otDaemon.asBinder().linkToDeath(() -> mHandler.post(this::onOtDaemonDied), 0);
        mOtDaemon = otDaemon;
        return mOtDaemon;
    }

    private void onOtDaemonDied() {
        checkOnHandlerThread();
        Log.w(TAG, "OT daemon is dead, clean up and restart it...");

        OperationReceiverWrapper.onOtDaemonDied();
        mOtDaemonCallbackProxy.onOtDaemonDied();
        mTunIfController.onOtDaemonDied();
        mNsdPublisher.onOtDaemonDied();
        mOtDaemon = null;
        initializeOtDaemon();
    }

    public void initialize() {
        mHandler.post(
                () -> {
                    Log.d(TAG, "Initializing Thread system service...");
                    try {
                        mTunIfController.createTunInterface();
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Failed to create Thread tunnel interface", e);
                    }
                    mConnectivityManager.registerNetworkProvider(mNetworkProvider);
                    requestUpstreamNetwork();
                    requestThreadNetwork();
                    mUserRestricted = isThreadUserRestricted();
                    registerUserRestrictionsReceiver();
                    initializeOtDaemon();
                });
    }

    public void setEnabled(boolean isEnabled, @NonNull IOperationReceiver receiver) {
        enforceAllPermissionsGranted(PERMISSION_THREAD_NETWORK_PRIVILEGED);

        mHandler.post(
                () ->
                        setEnabledInternal(
                                isEnabled,
                                true /* persist */,
                                new OperationReceiverWrapper(receiver)));
    }

    private void setEnabledInternal(
            boolean isEnabled, boolean persist, @NonNull OperationReceiverWrapper receiver) {
        if (isEnabled && isThreadUserRestricted()) {
            receiver.onError(
                    ERROR_FAILED_PRECONDITION,
                    "Cannot enable Thread: forbidden by user restriction");
            return;
        }

        if (persist) {
            // The persistent setting keeps the desired enabled state, thus it's set regardless
            // the otDaemon set enabled state operation succeeded or not, so that it can recover
            // to the desired value after reboot.
            mPersistentSettings.put(ThreadPersistentSettings.THREAD_ENABLED.key, isEnabled);
        }

        try {
            getOtDaemon().setThreadEnabled(isEnabled, newOtStatusReceiver(receiver));
        } catch (RemoteException e) {
            Log.e(TAG, "otDaemon.setThreadEnabled failed", e);
            receiver.onError(ERROR_INTERNAL_ERROR, "Thread stack error");
        }
    }

    private void registerUserRestrictionsReceiver() {
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        onUserRestrictionsChanged(isThreadUserRestricted());
                    }
                },
                new IntentFilter(UserManager.ACTION_USER_RESTRICTIONS_CHANGED),
                null /* broadcastPermission */,
                mHandler);
    }

    private void onUserRestrictionsChanged(boolean newUserRestrictedState) {
        checkOnHandlerThread();
        if (mUserRestricted == newUserRestrictedState) {
            return;
        }
        Log.i(
                TAG,
                "Thread user restriction changed: "
                        + mUserRestricted
                        + " -> "
                        + newUserRestrictedState);
        mUserRestricted = newUserRestrictedState;

        final boolean isEnabled = isEnabled();
        final IOperationReceiver receiver =
                new IOperationReceiver.Stub() {
                    @Override
                    public void onSuccess() {
                        Log.d(
                                TAG,
                                (isEnabled ? "Enabled" : "Disabled")
                                        + " Thread due to user restriction change");
                    }

                    @Override
                    public void onError(int otError, String messages) {
                        Log.e(
                                TAG,
                                "Failed to "
                                        + (isEnabled ? "enable" : "disable")
                                        + " Thread for user restriction change");
                    }
                };
        // Do not save the user restriction state to persistent settings so that the user
        // configuration won't be overwritten
        setEnabledInternal(isEnabled, false /* persist */, new OperationReceiverWrapper(receiver));
    }

    /** Returns {@code true} if Thread is set enabled. */
    private boolean isEnabled() {
        return !mUserRestricted && mPersistentSettings.get(ThreadPersistentSettings.THREAD_ENABLED);
    }

    /** Returns {@code true} if Thread has been restricted for the user. */
    private boolean isThreadUserRestricted() {
        return mUserManager.hasUserRestriction(DISALLOW_THREAD_NETWORK);
    }

    private void requestUpstreamNetwork() {
        if (mUpstreamNetworkCallback != null) {
            throw new AssertionError("The upstream network request is already there.");
        }
        mUpstreamNetworkCallback = new UpstreamNetworkCallback();
        mConnectivityManager.registerNetworkCallback(
                mUpstreamNetworkRequest, mUpstreamNetworkCallback, mHandler);
    }

    private void cancelRequestUpstreamNetwork() {
        if (mUpstreamNetworkCallback == null) {
            throw new AssertionError("The upstream network request null.");
        }
        mNetworkToInterface.clear();
        mConnectivityManager.unregisterNetworkCallback(mUpstreamNetworkCallback);
        mUpstreamNetworkCallback = null;
    }

    private final class UpstreamNetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            checkOnHandlerThread();
            Log.i(TAG, "Upstream network available: " + network);
        }

        @Override
        public void onLost(@NonNull Network network) {
            checkOnHandlerThread();
            Log.i(TAG, "Upstream network lost: " + network);

            // TODO: disable border routing when upsteam network disconnected
        }

        @Override
        public void onLinkPropertiesChanged(
                @NonNull Network network, @NonNull LinkProperties linkProperties) {
            checkOnHandlerThread();

            String existingIfName = mNetworkToInterface.get(network);
            String newIfName = linkProperties.getInterfaceName();
            if (Objects.equals(existingIfName, newIfName)) {
                return;
            }
            Log.i(TAG, "Upstream network changed: " + existingIfName + " -> " + newIfName);
            mNetworkToInterface.put(network, newIfName);

            // TODO: disable border routing if netIfName is null
            if (network.equals(mUpstreamNetwork)) {
                enableBorderRouting(mNetworkToInterface.get(mUpstreamNetwork));
            }
        }
    }

    private final class ThreadNetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            checkOnHandlerThread();
            Log.i(TAG, "Thread network available: " + network);
        }

        @Override
        public void onLocalNetworkInfoChanged(
                @NonNull Network network, @NonNull LocalNetworkInfo localNetworkInfo) {
            checkOnHandlerThread();
            Log.i(
                    TAG,
                    "LocalNetworkInfo of Thread network changed: {threadNetwork: "
                            + network
                            + ", localNetworkInfo: "
                            + localNetworkInfo
                            + "}");
            if (localNetworkInfo.getUpstreamNetwork() == null) {
                mUpstreamNetwork = null;
                return;
            }
            if (!localNetworkInfo.getUpstreamNetwork().equals(mUpstreamNetwork)) {
                mUpstreamNetwork = localNetworkInfo.getUpstreamNetwork();
                if (mNetworkToInterface.containsKey(mUpstreamNetwork)) {
                    enableBorderRouting(mNetworkToInterface.get(mUpstreamNetwork));
                }
            }
        }
    }

    private void requestThreadNetwork() {
        mConnectivityManager.registerNetworkCallback(
                new NetworkRequest.Builder()
                        // clearCapabilities() is needed to remove forbidden capabilities and UID
                        // requirement.
                        .clearCapabilities()
                        .addTransportType(NetworkCapabilities.TRANSPORT_THREAD)
                        .build(),
                new ThreadNetworkCallback(),
                mHandler);
    }

    /** Injects a {@link NetworkAgent} for testing. */
    @VisibleForTesting
    void setTestNetworkAgent(@Nullable NetworkAgent testNetworkAgent) {
        mTestNetworkAgent = testNetworkAgent;
    }

    private NetworkAgent newNetworkAgent() {
        if (mTestNetworkAgent != null) {
            return mTestNetworkAgent;
        }

        final NetworkCapabilities netCaps =
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_THREAD)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                        .build();
        final NetworkScore score =
                new NetworkScore.Builder()
                        .setKeepConnectedReason(NetworkScore.KEEP_CONNECTED_LOCAL_NETWORK)
                        .build();
        return new NetworkAgent(
                mContext,
                mHandler.getLooper(),
                TAG,
                netCaps,
                mTunIfController.getLinkProperties(),
                newLocalNetworkConfig(),
                score,
                new NetworkAgentConfig.Builder().build(),
                mNetworkProvider) {};
    }

    private void registerThreadNetwork() {
        if (mNetworkAgent != null) {
            return;
        }

        mNetworkAgent = newNetworkAgent();
        mNetworkAgent.register();
        mNetworkAgent.markConnected();
        Log.i(TAG, "Registered Thread network");
    }

    private void unregisterThreadNetwork() {
        if (mNetworkAgent == null) {
            // unregisterThreadNetwork can be called every time this device becomes detached or
            // disabled and the mNetworkAgent may not be created in this cases
            return;
        }

        Log.d(TAG, "Unregistering Thread network agent");

        mNetworkAgent.unregister();
        mNetworkAgent = null;
    }

    @Override
    public int getThreadVersion() {
        return THREAD_VERSION_1_3;
    }

    @Override
    public void createRandomizedDataset(
            String networkName, IActiveOperationalDatasetReceiver receiver) {
        ActiveOperationalDatasetReceiverWrapper receiverWrapper =
                new ActiveOperationalDatasetReceiverWrapper(receiver);
        mHandler.post(() -> createRandomizedDatasetInternal(networkName, receiverWrapper));
    }

    private void createRandomizedDatasetInternal(
            String networkName, @NonNull ActiveOperationalDatasetReceiverWrapper receiver) {
        checkOnHandlerThread();

        try {
            getOtDaemon().getChannelMasks(newChannelMasksReceiver(networkName, receiver));
        } catch (RemoteException e) {
            Log.e(TAG, "otDaemon.getChannelMasks failed", e);
            receiver.onError(ERROR_INTERNAL_ERROR, "Thread stack error");
        }
    }

    private IChannelMasksReceiver newChannelMasksReceiver(
            String networkName, ActiveOperationalDatasetReceiverWrapper receiver) {
        return new IChannelMasksReceiver.Stub() {
            @Override
            public void onSuccess(int supportedChannelMask, int preferredChannelMask) {
                ActiveOperationalDataset dataset =
                        createRandomizedDataset(
                                networkName,
                                supportedChannelMask,
                                preferredChannelMask,
                                Instant.now(),
                                new Random(),
                                new SecureRandom());

                receiver.onSuccess(dataset);
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                receiver.onError(otErrorToAndroidError(errorCode), errorMessage);
            }
        };
    }

    private static ActiveOperationalDataset createRandomizedDataset(
            String networkName,
            int supportedChannelMask,
            int preferredChannelMask,
            Instant now,
            Random random,
            SecureRandom secureRandom) {
        int panId = random.nextInt(/* bound= */ 0xffff);
        final byte[] meshLocalPrefix = newRandomBytes(random, LENGTH_MESH_LOCAL_PREFIX_BITS / 8);
        meshLocalPrefix[0] = MESH_LOCAL_PREFIX_FIRST_BYTE;

        final SparseArray<byte[]> channelMask = new SparseArray<>(1);
        channelMask.put(CHANNEL_PAGE_24_GHZ, channelMaskToByteArray(supportedChannelMask));
        final int channel = selectChannel(supportedChannelMask, preferredChannelMask, random);

        final byte[] securityFlags = new byte[] {(byte) 0xff, (byte) 0xf8};

        return new ActiveOperationalDataset.Builder()
                .setActiveTimestamp(
                        new OperationalDatasetTimestamp(
                                now.getEpochSecond() & 0xffffffffffffL, 0, false))
                .setExtendedPanId(newRandomBytes(random, LENGTH_EXTENDED_PAN_ID))
                .setPanId(panId)
                .setNetworkName(networkName)
                .setChannel(CHANNEL_PAGE_24_GHZ, channel)
                .setChannelMask(channelMask)
                .setPskc(newRandomBytes(secureRandom, LENGTH_PSKC))
                .setNetworkKey(newRandomBytes(secureRandom, LENGTH_NETWORK_KEY))
                .setMeshLocalPrefix(meshLocalPrefix)
                .setSecurityPolicy(new SecurityPolicy(DEFAULT_ROTATION_TIME_HOURS, securityFlags))
                .build();
    }

    private static int selectChannel(
            int supportedChannelMask, int preferredChannelMask, Random random) {
        // If the preferred channel mask is not empty, select a random channel from it, otherwise
        // choose one from the supported channel mask.
        preferredChannelMask = preferredChannelMask & supportedChannelMask;
        if (preferredChannelMask == 0) {
            preferredChannelMask = supportedChannelMask;
        }

        return selectRandomChannel(preferredChannelMask, random);
    }

    private static byte[] newRandomBytes(Random random, int length) {
        byte[] result = new byte[length];
        random.nextBytes(result);
        return result;
    }

    private static byte[] channelMaskToByteArray(int channelMask) {
        // Per Thread spec, a Channel Mask is:
        // A variable-length bit mask that identifies the channels within the channel page
        // (1 = selected, 0 = unselected). The channels are represented in most significant bit
        // order. For example, the most significant bit of the left-most byte indicates channel 0.
        // If channel 0 and channel 10 are selected, the mask would be: 80 20 00 00. For IEEE
        // 802.15.4-2006 2.4 GHz PHY, the ChannelMask is 27 bits and MaskLength is 4.
        //
        // The pass-in channelMask represents a channel K by (channelMask & (1 << K)), so here
        // needs to do bit-wise reverse to convert it to the Thread spec format in bytes.
        channelMask = Integer.reverse(channelMask);
        return new byte[] {
            (byte) (channelMask >>> 24),
            (byte) (channelMask >>> 16),
            (byte) (channelMask >>> 8),
            (byte) channelMask
        };
    }

    private static int selectRandomChannel(int supportedChannelMask, Random random) {
        int num = random.nextInt(Integer.bitCount(supportedChannelMask));
        for (int i = 0; i < 32; i++) {
            if ((supportedChannelMask & 1) == 1 && (num--) == 0) {
                return i;
            }
            supportedChannelMask >>>= 1;
        }
        return -1;
    }

    private void enforceAllPermissionsGranted(String... permissions) {
        for (String permission : permissions) {
            mContext.enforceCallingOrSelfPermission(
                    permission, "Permission " + permission + " is missing");
        }
    }

    @Override
    public void registerStateCallback(IStateCallback stateCallback) throws RemoteException {
        enforceAllPermissionsGranted(permission.ACCESS_NETWORK_STATE);
        mHandler.post(() -> mOtDaemonCallbackProxy.registerStateCallback(stateCallback));
    }

    @Override
    public void unregisterStateCallback(IStateCallback stateCallback) throws RemoteException {
        enforceAllPermissionsGranted(permission.ACCESS_NETWORK_STATE);
        mHandler.post(() -> mOtDaemonCallbackProxy.unregisterStateCallback(stateCallback));
    }

    @Override
    public void registerOperationalDatasetCallback(IOperationalDatasetCallback callback)
            throws RemoteException {
        enforceAllPermissionsGranted(
                permission.ACCESS_NETWORK_STATE, PERMISSION_THREAD_NETWORK_PRIVILEGED);
        mHandler.post(() -> mOtDaemonCallbackProxy.registerDatasetCallback(callback));
    }

    @Override
    public void unregisterOperationalDatasetCallback(IOperationalDatasetCallback callback)
            throws RemoteException {
        enforceAllPermissionsGranted(
                permission.ACCESS_NETWORK_STATE, PERMISSION_THREAD_NETWORK_PRIVILEGED);
        mHandler.post(() -> mOtDaemonCallbackProxy.unregisterDatasetCallback(callback));
    }

    private void checkOnHandlerThread() {
        if (Looper.myLooper() != mHandler.getLooper()) {
            Log.wtf(TAG, "Must be on the handler thread!");
        }
    }

    private IOtStatusReceiver newOtStatusReceiver(OperationReceiverWrapper receiver) {
        return new IOtStatusReceiver.Stub() {
            @Override
            public void onSuccess() {
                receiver.onSuccess();
            }

            @Override
            public void onError(int otError, String message) {
                receiver.onError(otErrorToAndroidError(otError), message);
            }
        };
    }

    @ErrorCode
    private static int otErrorToAndroidError(int otError) {
        // See external/openthread/include/openthread/error.h for OT error definition
        switch (otError) {
            case OT_ERROR_ABORT:
                return ERROR_ABORTED;
            case OT_ERROR_BUSY:
                return ERROR_BUSY;
            case OT_ERROR_NO_BUFS:
                return ERROR_RESOURCE_EXHAUSTED;
            case OT_ERROR_PARSE:
                return ERROR_RESPONSE_BAD_FORMAT;
            case OT_ERROR_REASSEMBLY_TIMEOUT:
            case OT_ERROR_RESPONSE_TIMEOUT:
                return ERROR_TIMEOUT;
            case OT_ERROR_REJECTED:
                return ERROR_REJECTED_BY_PEER;
            case OT_ERROR_UNSUPPORTED_CHANNEL:
                return ERROR_UNSUPPORTED_CHANNEL;
            case OT_ERROR_THREAD_DISABLED:
                return ERROR_THREAD_DISABLED;
            case OT_ERROR_FAILED_PRECONDITION:
                return ERROR_FAILED_PRECONDITION;
            case OT_ERROR_INVALID_STATE:
            default:
                return ERROR_INTERNAL_ERROR;
        }
    }

    @Override
    public void join(
            @NonNull ActiveOperationalDataset activeDataset, @NonNull IOperationReceiver receiver) {
        enforceAllPermissionsGranted(PERMISSION_THREAD_NETWORK_PRIVILEGED);

        OperationReceiverWrapper receiverWrapper = new OperationReceiverWrapper(receiver);
        mHandler.post(() -> joinInternal(activeDataset, receiverWrapper));
    }

    private void joinInternal(
            @NonNull ActiveOperationalDataset activeDataset,
            @NonNull OperationReceiverWrapper receiver) {
        checkOnHandlerThread();

        try {
            // The otDaemon.join() will leave first if this device is currently attached
            getOtDaemon().join(activeDataset.toThreadTlvs(), newOtStatusReceiver(receiver));
        } catch (RemoteException e) {
            Log.e(TAG, "otDaemon.join failed", e);
            receiver.onError(ERROR_INTERNAL_ERROR, "Thread stack error");
        }
    }

    @Override
    public void scheduleMigration(
            @NonNull PendingOperationalDataset pendingDataset,
            @NonNull IOperationReceiver receiver) {
        enforceAllPermissionsGranted(PERMISSION_THREAD_NETWORK_PRIVILEGED);

        OperationReceiverWrapper receiverWrapper = new OperationReceiverWrapper(receiver);
        mHandler.post(() -> scheduleMigrationInternal(pendingDataset, receiverWrapper));
    }

    public void scheduleMigrationInternal(
            @NonNull PendingOperationalDataset pendingDataset,
            @NonNull OperationReceiverWrapper receiver) {
        checkOnHandlerThread();

        try {
            getOtDaemon()
                    .scheduleMigration(
                            pendingDataset.toThreadTlvs(), newOtStatusReceiver(receiver));
        } catch (RemoteException e) {
            Log.e(TAG, "otDaemon.scheduleMigration failed", e);
            receiver.onError(ERROR_INTERNAL_ERROR, "Thread stack error");
        }
    }

    @Override
    public void leave(@NonNull IOperationReceiver receiver) throws RemoteException {
        enforceAllPermissionsGranted(PERMISSION_THREAD_NETWORK_PRIVILEGED);

        mHandler.post(() -> leaveInternal(new OperationReceiverWrapper(receiver)));
    }

    private void leaveInternal(@NonNull OperationReceiverWrapper receiver) {
        checkOnHandlerThread();

        try {
            getOtDaemon().leave(newOtStatusReceiver(receiver));
        } catch (RemoteException e) {
            // Oneway AIDL API should never throw?
            receiver.onError(ERROR_INTERNAL_ERROR, "Thread stack error");
        }
    }

    /**
     * Sets the country code.
     *
     * @param countryCode 2 characters string country code (as defined in ISO 3166) to set.
     * @param receiver the receiver to receive result of this operation
     */
    @RequiresPermission(PERMISSION_THREAD_NETWORK_PRIVILEGED)
    public void setCountryCode(@NonNull String countryCode, @NonNull IOperationReceiver receiver) {
        enforceAllPermissionsGranted(PERMISSION_THREAD_NETWORK_PRIVILEGED);

        OperationReceiverWrapper receiverWrapper = new OperationReceiverWrapper(receiver);
        mHandler.post(() -> setCountryCodeInternal(countryCode, receiverWrapper));
    }

    private void setCountryCodeInternal(
            String countryCode, @NonNull OperationReceiverWrapper receiver) {
        checkOnHandlerThread();

        try {
            getOtDaemon().setCountryCode(countryCode, newOtStatusReceiver(receiver));
        } catch (RemoteException e) {
            Log.e(TAG, "otDaemon.setCountryCode failed", e);
            receiver.onError(ERROR_INTERNAL_ERROR, "Thread stack error");
        }
    }

    @Override
    public void setTestNetworkAsUpstream(
            @Nullable String testNetworkInterfaceName, @NonNull IOperationReceiver receiver) {
        enforceAllPermissionsGranted(PERMISSION_THREAD_NETWORK_PRIVILEGED, NETWORK_SETTINGS);

        Log.i(TAG, "setTestNetworkAsUpstream: " + testNetworkInterfaceName);
        mHandler.post(() -> setTestNetworkAsUpstreamInternal(testNetworkInterfaceName, receiver));
    }

    private void setTestNetworkAsUpstreamInternal(
            @Nullable String testNetworkInterfaceName, @NonNull IOperationReceiver receiver) {
        checkOnHandlerThread();

        TestNetworkSpecifier testNetworkSpecifier = null;
        if (testNetworkInterfaceName != null) {
            testNetworkSpecifier = new TestNetworkSpecifier(testNetworkInterfaceName);
        }

        if (!Objects.equals(mUpstreamTestNetworkSpecifier, testNetworkSpecifier)) {
            cancelRequestUpstreamNetwork();
            mUpstreamTestNetworkSpecifier = testNetworkSpecifier;
            mUpstreamNetworkRequest = newUpstreamNetworkRequest();
            requestUpstreamNetwork();
            sendLocalNetworkConfig();
        }
        try {
            receiver.onSuccess();
        } catch (RemoteException ignored) {
            // do nothing if the client is dead
        }
    }

    private void enableBorderRouting(String infraIfName) {
        if (mBorderRouterConfig.isBorderRoutingEnabled
                && infraIfName.equals(mBorderRouterConfig.infraInterfaceName)) {
            return;
        }
        Log.i(TAG, "Enable border routing on AIL: " + infraIfName);
        try {
            mBorderRouterConfig.infraInterfaceName = infraIfName;
            mBorderRouterConfig.infraInterfaceIcmp6Socket =
                    mInfraIfController.createIcmp6Socket(infraIfName);
            mBorderRouterConfig.isBorderRoutingEnabled = true;

            mOtDaemon.configureBorderRouter(
                    mBorderRouterConfig,
                    new IOtStatusReceiver.Stub() {
                        @Override
                        public void onSuccess() {
                            Log.i(TAG, "configure border router successfully");
                        }

                        @Override
                        public void onError(int i, String s) {
                            Log.w(
                                    TAG,
                                    String.format(
                                            "failed to configure border router: %d %s", i, s));
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "enableBorderRouting failed: " + e);
        }
    }

    private void handleThreadInterfaceStateChanged(boolean isUp) {
        try {
            mTunIfController.setInterfaceUp(isUp);
            Log.i(TAG, "Thread TUN interface becomes " + (isUp ? "up" : "down"));
        } catch (IOException e) {
            Log.e(TAG, "Failed to handle Thread interface state changes", e);
        }
    }

    private void handleDeviceRoleChanged(@DeviceRole int deviceRole) {
        if (ThreadNetworkController.isAttached(deviceRole)) {
            Log.i(TAG, "Attached to the Thread network");

            // This is an idempotent method which can be called for multiple times when the device
            // is already attached (e.g. going from Child to Router)
            registerThreadNetwork();
        } else {
            Log.i(TAG, "Detached from the Thread network");

            // This is an idempotent method which can be called for multiple times when the device
            // is already detached or stopped
            unregisterThreadNetwork();
        }
    }

    private void handleAddressChanged(Ipv6AddressInfo addressInfo, boolean isAdded) {
        checkOnHandlerThread();
        InetAddress address = addressInfoToInetAddress(addressInfo);
        if (address.isMulticastAddress()) {
            Log.i(TAG, "Ignoring multicast address " + address.getHostAddress());
            return;
        }

        LinkAddress linkAddress = newLinkAddress(addressInfo);
        if (isAdded) {
            mTunIfController.addAddress(linkAddress);
        } else {
            mTunIfController.removeAddress(linkAddress);
        }

        // The OT daemon can send link property updates before the networkAgent is
        // registered
        if (mNetworkAgent != null) {
            mNetworkAgent.sendLinkProperties(mTunIfController.getLinkProperties());
        }
    }

    private void sendLocalNetworkConfig() {
        if (mNetworkAgent == null) {
            return;
        }
        final LocalNetworkConfig localNetworkConfig = newLocalNetworkConfig();
        mNetworkAgent.sendLocalNetworkConfig(localNetworkConfig);
        Log.d(TAG, "Sent localNetworkConfig: " + localNetworkConfig);
    }

    private void handleMulticastForwardingChanged(BackboneRouterState state) {
        MulticastRoutingConfig upstreamMulticastRoutingConfig;
        MulticastRoutingConfig downstreamMulticastRoutingConfig;

        if (state.multicastForwardingEnabled) {
            // When multicast forwarding is enabled, setup upstream forwarding to any address
            // with minimal scope 4
            // setup downstream forwarding with addresses subscribed from Thread network
            upstreamMulticastRoutingConfig =
                    new MulticastRoutingConfig.Builder(FORWARD_WITH_MIN_SCOPE, 4).build();
            downstreamMulticastRoutingConfig =
                    buildDownstreamMulticastRoutingConfigSelected(state.listeningAddresses);
        } else {
            // When multicast forwarding is disabled, set both upstream and downstream
            // forwarding config to FORWARD_NONE.
            upstreamMulticastRoutingConfig = CONFIG_FORWARD_NONE;
            downstreamMulticastRoutingConfig = CONFIG_FORWARD_NONE;
        }

        if (upstreamMulticastRoutingConfig.equals(mUpstreamMulticastRoutingConfig)
                && downstreamMulticastRoutingConfig.equals(mDownstreamMulticastRoutingConfig)) {
            return;
        }

        mUpstreamMulticastRoutingConfig = upstreamMulticastRoutingConfig;
        mDownstreamMulticastRoutingConfig = downstreamMulticastRoutingConfig;
        sendLocalNetworkConfig();
    }

    private MulticastRoutingConfig buildDownstreamMulticastRoutingConfigSelected(
            List<String> listeningAddresses) {
        MulticastRoutingConfig.Builder builder =
                new MulticastRoutingConfig.Builder(FORWARD_SELECTED);
        for (String addressStr : listeningAddresses) {
            Inet6Address address = (Inet6Address) InetAddresses.parseNumericAddress(addressStr);
            builder.addListeningAddress(address);
        }
        return builder.build();
    }

    private static final class CallbackMetadata {
        private static long gId = 0;

        // The unique ID
        final long id;

        final IBinder.DeathRecipient deathRecipient;

        CallbackMetadata(IBinder.DeathRecipient deathRecipient) {
            this.id = allocId();
            this.deathRecipient = deathRecipient;
        }

        private static long allocId() {
            if (gId == Long.MAX_VALUE) {
                gId = 0;
            }
            return gId++;
        }
    }

    /**
     * Handles and forwards Thread daemon callbacks. This class must be accessed from the thread of
     * {@code mHandler}.
     */
    private final class OtDaemonCallbackProxy extends IOtDaemonCallback.Stub {
        private final Map<IStateCallback, CallbackMetadata> mStateCallbacks = new HashMap<>();
        private final Map<IOperationalDatasetCallback, CallbackMetadata> mOpDatasetCallbacks =
                new HashMap<>();

        private OtDaemonState mState;
        private ActiveOperationalDataset mActiveDataset;
        private PendingOperationalDataset mPendingDataset;

        public void registerStateCallback(IStateCallback callback) {
            checkOnHandlerThread();
            if (mStateCallbacks.containsKey(callback)) {
                throw new IllegalStateException("Registering the same IStateCallback twice");
            }

            IBinder.DeathRecipient deathRecipient =
                    () -> mHandler.post(() -> unregisterStateCallback(callback));
            CallbackMetadata callbackMetadata = new CallbackMetadata(deathRecipient);
            mStateCallbacks.put(callback, callbackMetadata);
            try {
                callback.asBinder().linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                mStateCallbacks.remove(callback);
                // This is thrown when the client is dead, do nothing
            }

            try {
                getOtDaemon().registerStateCallback(this, callbackMetadata.id);
            } catch (RemoteException e) {
                // oneway operation should never fail
            }
        }

        private void notifyThreadEnabledUpdated(IStateCallback callback, int enabledState) {
            try {
                callback.onThreadEnableStateChanged(enabledState);
                Log.i(TAG, "onThreadEnableStateChanged " + enabledState);
            } catch (RemoteException ignored) {
                // do nothing if the client is dead
            }
        }

        public void unregisterStateCallback(IStateCallback callback) {
            checkOnHandlerThread();
            if (!mStateCallbacks.containsKey(callback)) {
                return;
            }
            callback.asBinder().unlinkToDeath(mStateCallbacks.remove(callback).deathRecipient, 0);
        }

        public void registerDatasetCallback(IOperationalDatasetCallback callback) {
            checkOnHandlerThread();
            if (mOpDatasetCallbacks.containsKey(callback)) {
                throw new IllegalStateException(
                        "Registering the same IOperationalDatasetCallback twice");
            }

            IBinder.DeathRecipient deathRecipient =
                    () -> mHandler.post(() -> unregisterDatasetCallback(callback));
            CallbackMetadata callbackMetadata = new CallbackMetadata(deathRecipient);
            mOpDatasetCallbacks.put(callback, callbackMetadata);
            try {
                callback.asBinder().linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                mOpDatasetCallbacks.remove(callback);
            }

            try {
                getOtDaemon().registerStateCallback(this, callbackMetadata.id);
            } catch (RemoteException e) {
                // oneway operation should never fail
            }
        }

        public void unregisterDatasetCallback(IOperationalDatasetCallback callback) {
            checkOnHandlerThread();
            if (!mOpDatasetCallbacks.containsKey(callback)) {
                return;
            }
            callback.asBinder()
                    .unlinkToDeath(mOpDatasetCallbacks.remove(callback).deathRecipient, 0);
        }

        public void onOtDaemonDied() {
            checkOnHandlerThread();
            if (mState == null) {
                return;
            }

            // If this device is already STOPPED or DETACHED, do nothing
            if (!ThreadNetworkController.isAttached(mState.deviceRole)) {
                return;
            }

            // The Thread device role is considered DETACHED when the OT daemon process is dead
            handleDeviceRoleChanged(DEVICE_ROLE_DETACHED);
            for (IStateCallback callback : mStateCallbacks.keySet()) {
                try {
                    callback.onDeviceRoleChanged(DEVICE_ROLE_DETACHED);
                } catch (RemoteException ignored) {
                    // do nothing if the client is dead
                }
            }
        }

        @Override
        public void onThreadEnabledChanged(int state) {
            mHandler.post(() -> onThreadEnabledChangedInternal(state));
        }

        private void onThreadEnabledChangedInternal(int state) {
            checkOnHandlerThread();
            for (IStateCallback callback : mStateCallbacks.keySet()) {
                notifyThreadEnabledUpdated(callback, otStateToAndroidState(state));
            }
        }

        private static int otStateToAndroidState(int state) {
            switch (state) {
                case OT_STATE_ENABLED:
                    return STATE_ENABLED;
                case OT_STATE_DISABLED:
                    return STATE_DISABLED;
                case OT_STATE_DISABLING:
                    return STATE_DISABLING;
                default:
                    throw new IllegalArgumentException("Unknown ot state " + state);
            }
        }

        @Override
        public void onStateChanged(OtDaemonState newState, long listenerId) {
            mHandler.post(() -> onStateChangedInternal(newState, listenerId));
        }

        private void onStateChangedInternal(OtDaemonState newState, long listenerId) {
            checkOnHandlerThread();
            onInterfaceStateChanged(newState.isInterfaceUp);
            onDeviceRoleChanged(newState.deviceRole, listenerId);
            onPartitionIdChanged(newState.partitionId, listenerId);
            mState = newState;

            ActiveOperationalDataset newActiveDataset;
            try {
                if (newState.activeDatasetTlvs.length != 0) {
                    newActiveDataset =
                            ActiveOperationalDataset.fromThreadTlvs(newState.activeDatasetTlvs);
                } else {
                    newActiveDataset = null;
                }
                onActiveOperationalDatasetChanged(newActiveDataset, listenerId);
                mActiveDataset = newActiveDataset;
            } catch (IllegalArgumentException e) {
                // Is unlikely that OT will generate invalid Operational Dataset
                Log.wtf(TAG, "Invalid Active Operational Dataset from OpenThread", e);
            }

            PendingOperationalDataset newPendingDataset;
            try {
                if (newState.pendingDatasetTlvs.length != 0) {
                    newPendingDataset =
                            PendingOperationalDataset.fromThreadTlvs(newState.pendingDatasetTlvs);
                } else {
                    newPendingDataset = null;
                }
                onPendingOperationalDatasetChanged(newPendingDataset, listenerId);
                mPendingDataset = newPendingDataset;
            } catch (IllegalArgumentException e) {
                // Is unlikely that OT will generate invalid Operational Dataset
                Log.wtf(TAG, "Invalid Pending Operational Dataset from OpenThread", e);
            }
        }

        private void onInterfaceStateChanged(boolean isUp) {
            checkOnHandlerThread();
            if (mState == null || mState.isInterfaceUp != isUp) {
                handleThreadInterfaceStateChanged(isUp);
            }
        }

        private void onDeviceRoleChanged(@DeviceRole int deviceRole, long listenerId) {
            checkOnHandlerThread();
            boolean hasChange = (mState == null || mState.deviceRole != deviceRole);
            if (hasChange) {
                handleDeviceRoleChanged(deviceRole);
            }

            for (var callbackEntry : mStateCallbacks.entrySet()) {
                if (!hasChange && callbackEntry.getValue().id != listenerId) {
                    continue;
                }
                try {
                    callbackEntry.getKey().onDeviceRoleChanged(deviceRole);
                } catch (RemoteException ignored) {
                    // do nothing if the client is dead
                }
            }
        }

        private void onPartitionIdChanged(long partitionId, long listenerId) {
            checkOnHandlerThread();
            boolean hasChange = (mState == null || mState.partitionId != partitionId);

            for (var callbackEntry : mStateCallbacks.entrySet()) {
                if (!hasChange && callbackEntry.getValue().id != listenerId) {
                    continue;
                }
                try {
                    callbackEntry.getKey().onPartitionIdChanged(partitionId);
                } catch (RemoteException ignored) {
                    // do nothing if the client is dead
                }
            }
        }

        private void onActiveOperationalDatasetChanged(
                ActiveOperationalDataset activeDataset, long listenerId) {
            checkOnHandlerThread();
            boolean hasChange = !Objects.equals(mActiveDataset, activeDataset);

            for (var callbackEntry : mOpDatasetCallbacks.entrySet()) {
                if (!hasChange && callbackEntry.getValue().id != listenerId) {
                    continue;
                }
                try {
                    callbackEntry.getKey().onActiveOperationalDatasetChanged(activeDataset);
                } catch (RemoteException ignored) {
                    // do nothing if the client is dead
                }
            }
        }

        private void onPendingOperationalDatasetChanged(
                PendingOperationalDataset pendingDataset, long listenerId) {
            checkOnHandlerThread();
            boolean hasChange = !Objects.equals(mPendingDataset, pendingDataset);
            for (var callbackEntry : mOpDatasetCallbacks.entrySet()) {
                if (!hasChange && callbackEntry.getValue().id != listenerId) {
                    continue;
                }
                try {
                    callbackEntry.getKey().onPendingOperationalDatasetChanged(pendingDataset);
                } catch (RemoteException ignored) {
                    // do nothing if the client is dead
                }
            }
        }

        @Override
        public void onAddressChanged(Ipv6AddressInfo addressInfo, boolean isAdded) {
            mHandler.post(() -> handleAddressChanged(addressInfo, isAdded));
        }

        @Override
        public void onBackboneRouterStateChanged(BackboneRouterState state) {
            mHandler.post(() -> handleMulticastForwardingChanged(state));
        }
    }
}
