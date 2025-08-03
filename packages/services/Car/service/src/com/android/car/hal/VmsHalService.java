/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.hal;

import static com.android.car.internal.common.CommonConstants.EMPTY_BYTE_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_FLOAT_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_LONG_ARRAY;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.VehicleAreaType;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsClient;
import android.car.vms.VmsClientManager.VmsClientCallback;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsSubscriptionHelper;
import android.car.vms.VmsSubscriptionState;
import android.content.Context;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VmsBaseMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.VmsMessageType;
import android.hardware.automotive.vehicle.VmsMessageWithLayerAndPublisherIdIntegerValuesIndex;
import android.hardware.automotive.vehicle.VmsMessageWithLayerIntegerValuesIndex;
import android.hardware.automotive.vehicle.VmsOfferingMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.VmsPublisherInformationIntegerValuesIndex;
import android.hardware.automotive.vehicle.VmsStartSessionMessageIntegerValuesIndex;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.os.HandlerExecutor;
import com.android.car.internal.util.DebugUtils;
import com.android.car.vms.VmsBrokerService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * VMS client implementation that proxies VmsPublisher/VmsSubscriber API calls to the Vehicle HAL
 * using HAL-specific message encodings.
 *
 * @see android.hardware.automotive.vehicle.IVehicle
 */
public class VmsHalService extends HalServiceBase {
    private static final String TAG = CarLog.tagFor(VmsHalService.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final int HAL_PROPERTY_ID = VehicleProperty.VEHICLE_MAP_SERVICE;
    private static final int[] SUPPORTED_PROPERTIES = new int[]{
            HAL_PROPERTY_ID
    };
    private static final int NUM_INTEGERS_IN_VMS_LAYER = 3;
    private static final int UNKNOWN_CLIENT_ID = -1;
    private static final byte[] DEFAULT_PUBLISHER_INFO = EMPTY_BYTE_ARRAY;

    private final VehicleHal mVehicleHal;
    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final int mCoreId;
    private final BiFunction<Handler, VmsClientCallback, VmsClient> mInitVmsClient;
    private final int mClientMetricsProperty;
    private final boolean mPropagatePropertyException;
    private final VmsSubscriptionHelper mSubscriptionHelper =
            new VmsSubscriptionHelper(this::setSubscriptions);

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private boolean mIsSupported;
    @GuardedBy("mLock")
    private VmsClient mClient;

    private final HalPropValueBuilder mPropValueBuilder;

    private final VmsClientCallback mClientCallback = new VmsClientCallback() {
        @Override
        public void onClientConnected(VmsClient client) {
            Slogf.wtf(TAG, "onClientConnnected triggered for local client");
        }

        @Override
        public void onSubscriptionStateChanged(VmsSubscriptionState subscriptionState) {
            if (DBG) Slogf.d(TAG, "Handling a subscription state change");
            setPropertyValue(createSubscriptionStateMessage(mPropValueBuilder,
                    VmsMessageType.SUBSCRIPTIONS_CHANGE, subscriptionState));
        }

        @Override
        public void onLayerAvailabilityChanged(VmsAvailableLayers availableLayers) {
            if (DBG) Slogf.d(TAG, "Handling a layer availability change");
            setPropertyValue(createAvailableLayersMessage(mPropValueBuilder,
                    VmsMessageType.AVAILABILITY_CHANGE, availableLayers));
        }

        @Override
        public void onPacketReceived(int providerId, VmsLayer layer, byte[] packet) {
            if (DBG) Slogf.d(TAG, "Handling a data message for Layer: " + layer);
            setPropertyValue(createDataMessage(mPropValueBuilder, layer, providerId, packet));
        }
    };

    /**
     * Constructor used by {@link VehicleHal}
     */
    VmsHalService(Context context, VehicleHal vehicleHal) {
        this(context, vehicleHal, SystemClock::uptimeMillis, VmsHalService::initVmsClient,
                BuildHelper.isDebuggableBuild());
    }

    @VisibleForTesting
    VmsHalService(Context context, VehicleHal vehicleHal, Supplier<Long> getCoreId,
            BiFunction<Handler, VmsClientCallback, VmsClient> initVmsClient,
            boolean propagatePropertyException) {
        mVehicleHal = vehicleHal;
        mCoreId = (int) (getCoreId.get() % Integer.MAX_VALUE);
        mInitVmsClient = initVmsClient;
        mClientMetricsProperty = getClientMetricsProperty(context);
        mPropagatePropertyException = propagatePropertyException;
        mPropValueBuilder = vehicleHal.getHalPropValueBuilder();
    }

    private static int getClientMetricsProperty(Context context) {
        int propId = context.getResources().getInteger(
                com.android.car.R.integer.vmsHalClientMetricsProperty);
        if (propId == 0) {
            Slogf.i(TAG, "Metrics collection disabled");
            return 0;
        }
        if ((propId & VehiclePropertyGroup.MASK) != VehiclePropertyGroup.VENDOR) {
            Slogf.w(TAG, "Metrics collection disabled, non-vendor property: 0x%x", propId);
            return 0;
        }

        Slogf.i(TAG, "Metrics collection property: 0x%x", propId);
        return propId;
    }

    /**
     * Retrieves the callback message handler for use by unit tests.
     */
    @VisibleForTesting
    Handler getHandler() {
        return mHandler;
    }

    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> properties) {
        if (properties.isEmpty()) {
            return;
        }
        synchronized (mLock) {
            mIsSupported = true;
        }
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (!mIsSupported) {
                Slogf.i(TAG, "VmsHalService VHAL property not supported");
                return; // Do not continue initialization
            }
            connectVmsClient();
        }

        Slogf.i(TAG, "Initializing VmsHalService VHAL property");
        mVehicleHal.subscribePropertySafe(this, HAL_PROPERTY_ID);

        mHandler.post(() ->
                setPropertyValue(createStartSessionMessage(
                        mPropValueBuilder, mCoreId, UNKNOWN_CLIENT_ID)));
    }

    @Override
    public void release() {
        synchronized (mLock) {
            disconnectVmsClient();
            if (!mIsSupported) {
                return;
            }
        }
        if (DBG) {
            Slogf.d(TAG, "Releasing VmsHalService VHAL property");
        }
        mVehicleHal.unsubscribePropertySafe(this, HAL_PROPERTY_ID);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        synchronized (mLock) {
            writer.println("*VMS HAL*");
            writer.printf("VmsProperty: %s\n", mIsSupported ? "supported" : "unsupported");
            if (mClient == null) {
                writer.println("VmsClient: disconnected");
                return;
            }
            writer.println("VmsClient: connected");
            writer.printf("Subscriptions: %s\n", mSubscriptionHelper.getSubscriptions());
            writer.printf("AvailableLayers: %s\n", mClient.getAvailableLayers());
            writer.printf("SubscriptionState: %s\n", mClient.getSubscriptionState());
        }
    }

    /**
     * Dumps HAL client metrics obtained by reading the VMS HAL property.
     *
     * @param fd Dumpsys file descriptor to write client metrics to.
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpMetrics(FileDescriptor fd) {
        if (mClientMetricsProperty == 0) {
            Slogf.w(TAG, "Metrics collection is disabled");
            return;
        }

        HalPropValue vehicleProp = null;
        try {
            vehicleProp = mVehicleHal.get(mClientMetricsProperty);
        } catch (RuntimeException e) {
            // Failures to retrieve metrics should be non-fatal
            Slogf.e(TAG, "While reading metrics from client", e);
        }
        if (vehicleProp == null) {
            if (DBG) Slogf.d(TAG, "Metrics unavailable");
            return;
        }

        try (FileOutputStream fout = new FileOutputStream(fd)) {
            fout.write(vehicleProp.getByteArray());
            fout.flush();
        } catch (IOException e) {
            Slogf.e(TAG, "Error writing metrics to output stream", e);
        }
    }

    /**
     * Consumes/produces HAL messages.
     *
     * The format of these messages is defined in:
     * hardware/interfaces/automotive/vehicle/2.0/types.hal
     */
    @Override
    public void onHalEvents(List<HalPropValue> values) {
        if (DBG) Slogf.d(TAG, "Handling a VMS property change");
        for (HalPropValue v : values) {
            ArrayList<Integer> vec = new ArrayList<Integer>(v.getInt32ValuesSize());
            for (int i = 0; i < v.getInt32ValuesSize(); i++) {
                vec.add(v.getInt32Value(i));
            }
            int messageType;
            try {
                messageType = v.getInt32Value(VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE);
            } catch (IndexOutOfBoundsException e) {
                Slogf.e(TAG, "Invalid event, no message type", e);
                continue;
            }

            if (DBG) {
                Slogf.d(TAG, "Received "
                        + DebugUtils.constantToString(VmsMessageType.class, messageType)
                        + " message");
            }
            try {
                switch (messageType) {
                    case VmsMessageType.DATA:
                        handleDataEvent(vec, v.getByteArray());
                        break;
                    case VmsMessageType.SUBSCRIBE:
                        handleSubscribeEvent(vec);
                        break;
                    case VmsMessageType.UNSUBSCRIBE:
                        handleUnsubscribeEvent(vec);
                        break;
                    case VmsMessageType.SUBSCRIBE_TO_PUBLISHER:
                        handleSubscribeToPublisherEvent(vec);
                        break;
                    case VmsMessageType.UNSUBSCRIBE_TO_PUBLISHER:
                        handleUnsubscribeFromPublisherEvent(vec);
                        break;
                    case VmsMessageType.PUBLISHER_ID_REQUEST:
                        handlePublisherIdRequest(v.getByteArray());
                        break;
                    case VmsMessageType.PUBLISHER_INFORMATION_REQUEST:
                        handlePublisherInfoRequest(vec);
                        break;
                    case VmsMessageType.OFFERING:
                        handleOfferingEvent(vec);
                        break;
                    case VmsMessageType.AVAILABILITY_REQUEST:
                        handleAvailabilityRequestEvent();
                        break;
                    case VmsMessageType.SUBSCRIPTIONS_REQUEST:
                        handleSubscriptionsRequestEvent();
                        break;
                    case VmsMessageType.START_SESSION:
                        handleStartSessionEvent(vec);
                        break;
                    default:
                        Slogf.e(TAG, "Unexpected message type: " + messageType);
                }
            } catch (IndexOutOfBoundsException e) {
                Slogf.e(TAG, "While handling "
                        + DebugUtils.constantToString(VmsMessageType.class, messageType), e);
            }
        }
    }

    private void connectVmsClient() {
        synchronized (mLock) {
            mClient = mInitVmsClient.apply(mHandler, mClientCallback);
        }
    }

    private void disconnectVmsClient() {
        synchronized (mLock) {
            if (mClient != null) {
                try {
                    mClient.unregister();
                } catch (RemoteException e) {
                    Slogf.wtf(TAG, "Local broker should not throw RemoteException", e);
                }
                mClient = null;
            }
        }
    }

    private static VmsClient initVmsClient(Handler handler, VmsClientCallback callback) {
        VmsBrokerService brokerService = CarLocalServices.getService(VmsBrokerService.class);
        if (brokerService == null) {
            Slogf.e(TAG, "Broker service is not enabled");
            return null;
        }
        VmsClient client = new VmsClient(brokerService, new HandlerExecutor(handler), callback,
                /* legacyClient= */ true, /* autoCloseMemory */ false,
                /* exceptionHandler= */ ignored -> { });
        try {
            client.register();
        } catch (RemoteException e) {
            Slogf.wtf(TAG, "Local broker should not throw RemoteException", e);
        }
        return client;
    }

    private VmsClient getVmsClient() {
        synchronized (mLock) {
            if (mClient == null) {
                throw new IllegalStateException("VmsClient is not connected");
            }
            return mClient;
        }
    }

    /**
     * SESSION_START message format:
     * <ul>
     * <li>Message type
     * <li>Core ID
     * <li>Client ID
     * </ul>
     */
    private void handleStartSessionEvent(List<Integer> message) {
        int coreId = message.get(VmsStartSessionMessageIntegerValuesIndex.SERVICE_ID);
        int clientId = message.get(VmsStartSessionMessageIntegerValuesIndex.CLIENT_ID);
        Slogf.i(TAG, "Starting new session with coreId: " + coreId + " client: " + clientId);

        if (coreId != mCoreId) {
            // Reset VmsClient
            disconnectVmsClient();
            connectVmsClient();
            // Send acknowledgement message
            setPropertyValue(createStartSessionMessage(mPropValueBuilder, mCoreId, clientId));
        }
        mClientCallback.onLayerAvailabilityChanged(getVmsClient().getAvailableLayers());
    }

    /**
     * DATA message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * <li>Payload
     * </ul>
     */
    private void handleDataEvent(List<Integer> message, byte[] payload) {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        if (DBG) {
            Slogf.d(TAG,
                    "Handling a data event for Layer: " + vmsLayer + " Publisher: " + publisherId);
        }
        if (payload.length == 0) {
            Slogf.e(TAG, "Get 0 length payload while handling data event");
            return;
        }
        getVmsClient().publishPacket(publisherId, vmsLayer, payload);
    }

    /**
     * SUBSCRIBE message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     */
    private void handleSubscribeEvent(List<Integer> message) {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        if (DBG) Slogf.d(TAG, "Handling a subscribe event for Layer: " + vmsLayer);
        mSubscriptionHelper.subscribe(vmsLayer);
    }

    /**
     * SUBSCRIBE_TO_PUBLISHER message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * </ul>
     */
    private void handleSubscribeToPublisherEvent(List<Integer> message) {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        if (DBG) {
            Slogf.d(TAG, "Handling a subscribe event for Layer: " + vmsLayer
                    + " Publisher: " + publisherId);
        }
        mSubscriptionHelper.subscribe(vmsLayer, publisherId);
    }

    /**
     * UNSUBSCRIBE message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     */
    private void handleUnsubscribeEvent(List<Integer> message) {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        if (DBG) Slogf.d(TAG, "Handling an unsubscribe event for Layer: " + vmsLayer);
        mSubscriptionHelper.unsubscribe(vmsLayer);
    }

    /**
     * UNSUBSCRIBE_TO_PUBLISHER message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * </ul>
     */
    private void handleUnsubscribeFromPublisherEvent(List<Integer> message) {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        if (DBG) {
            Slogf.d(TAG, "Handling an unsubscribe event for Layer: " + vmsLayer
                    + " Publisher: " + publisherId);
        }
        mSubscriptionHelper.unsubscribe(vmsLayer, publisherId);
    }

    private void setSubscriptions(Set<VmsAssociatedLayer> subscriptions) {
        getVmsClient().setSubscriptions(subscriptions);
    }

    /**
     * PUBLISHER_ID_REQUEST message format:
     * <ul>
     * <li>Message type
     * <li>Publisher info (bytes)
     * </ul>
     *
     * PUBLISHER_ID_RESPONSE message format:
     * <ul>
     * <li>Message type
     * <li>Publisher ID
     * </ul>
     */
    private void handlePublisherIdRequest(byte[] payload) {
        if (DBG) {
            Slogf.d(TAG, "Handling a publisher id request event");
        }
        if (payload.length == 0) {
            Slogf.e(TAG, "Get 0 length payload while handling data event");
            return;
        }

        int publisherId = getVmsClient().registerProvider(payload);
        HalPropValue vehicleProp = createVmsMessage(mPropValueBuilder,
                VmsMessageType.PUBLISHER_ID_RESPONSE, new ArrayList<Integer>(
                        Arrays.asList(publisherId)));

        setPropertyValue(vehicleProp);
    }


    /**
     * PUBLISHER_INFORMATION_REQUEST message format:
     * <ul>
     * <li>Message type
     * <li>Publisher ID
     * </ul>
     *
     * PUBLISHER_INFORMATION_RESPONSE message format:
     * <ul>
     * <li>Message type
     * <li>Publisher info (bytes)
     * </ul>
     */
    private void handlePublisherInfoRequest(List<Integer> message) {
        if (DBG) Slogf.d(TAG, "Handling a publisher info request event");
        int publisherId = message.get(VmsPublisherInformationIntegerValuesIndex.PUBLISHER_ID);

        // Publisher Info
        byte[] publisherInfo = getVmsClient().getProviderDescription(publisherId);
        byte[] payload = publisherInfo != null ? publisherInfo : DEFAULT_PUBLISHER_INFO;

        HalPropValue vehicleProp =
                createVmsMessage(mPropValueBuilder, VmsMessageType.PUBLISHER_INFORMATION_RESPONSE,
                        /* values= */ new ArrayList<Integer>(), payload);
        setPropertyValue(vehicleProp);
    }

    /**
     * OFFERING message format:
     * <ul>
     * <li>Message type
     * <li>Publisher ID
     * <li>Number of offerings.
     * <li>Offerings (x number of offerings)
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Number of layer dependencies.
     * <li>Layer dependencies (x number of layer dependencies)
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     * </ul>
     * </ul>
     */
    private void handleOfferingEvent(List<Integer> message) {
        // Publisher ID for OFFERING is stored at a different index than in other message types
        int publisherId = message.get(VmsOfferingMessageIntegerValuesIndex.PUBLISHER_ID);
        int numLayerDependencies =
                message.get(
                        VmsOfferingMessageIntegerValuesIndex.NUMBER_OF_OFFERS);
        if (DBG) {
            Slogf.d(TAG, "Handling an offering event of " + numLayerDependencies
                    + " layers for Publisher: " + publisherId);
        }

        Set<VmsLayerDependency> offeredLayers = new ArraySet<>(numLayerDependencies);
        int idx = VmsOfferingMessageIntegerValuesIndex.OFFERING_START;
        for (int i = 0; i < numLayerDependencies; i++) {
            VmsLayer offeredLayer = parseVmsLayerAtIndex(message, idx);
            idx += NUM_INTEGERS_IN_VMS_LAYER;

            int numDependenciesForLayer = message.get(idx++);
            if (numDependenciesForLayer == 0) {
                offeredLayers.add(new VmsLayerDependency(offeredLayer));
            } else {
                Set<VmsLayer> dependencies = new HashSet<>();

                for (int j = 0; j < numDependenciesForLayer; j++) {
                    VmsLayer dependantLayer = parseVmsLayerAtIndex(message, idx);
                    idx += NUM_INTEGERS_IN_VMS_LAYER;
                    dependencies.add(dependantLayer);
                }
                offeredLayers.add(new VmsLayerDependency(offeredLayer, dependencies));
            }
        }
        getVmsClient().setProviderOfferings(publisherId, offeredLayers);
    }

    /**
     * AVAILABILITY_REQUEST message format:
     * <ul>
     * <li>Message type
     * </ul>
     */
    private void handleAvailabilityRequestEvent() {
        setPropertyValue(createAvailableLayersMessage(mPropValueBuilder,
                VmsMessageType.AVAILABILITY_RESPONSE, getVmsClient().getAvailableLayers()));
    }

    /**
     * SUBSCRIPTION_REQUEST message format:
     * <ul>
     * <li>Message type
     * </ul>
     */
    private void handleSubscriptionsRequestEvent() {
        setPropertyValue(createSubscriptionStateMessage(mPropValueBuilder,
                VmsMessageType.SUBSCRIPTIONS_RESPONSE, getVmsClient().getSubscriptionState()));
    }

    private void setPropertyValue(HalPropValue vehicleProp) {
        int messageType = vehicleProp.getInt32Value(
                VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE);

        synchronized (mLock) {
            if (!mIsSupported) {
                Slogf.w(TAG, "HAL unsupported while attempting to send "
                        + DebugUtils.constantToString(VmsMessageType.class, messageType));
                return;
            }
        }

        try {
            mVehicleHal.set(vehicleProp);
        } catch (RuntimeException e) {
            Slogf.e(TAG, "While sending "
                    + DebugUtils.constantToString(VmsMessageType.class, messageType), e);
            if (mPropagatePropertyException) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Creates a SESSION_START type {@link HalPropValue}.
     *
     * SESSION_START message format:
     * <ul>
     * <li>Message type
     * <li>Core ID
     * <li>Client ID
     * </ul>
     */
    private static HalPropValue createStartSessionMessage(HalPropValueBuilder builder, int coreId,
            int clientId) {
        // Message type + layer
        HalPropValue vehicleProp = createVmsMessage(builder, VmsMessageType.START_SESSION,
                new ArrayList<Integer>(Arrays.asList(coreId, clientId)));
        return vehicleProp;
    }

    /**
     * Creates a DATA type {@link HalPropValue}.
     *
     * DATA message format:
     * <ul>
     * <li>Message type
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Publisher ID
     * <li>Payload
     * </ul>
     *
     * @param layer Layer for which message was published.
     * @param publisherId Publisher of message
     * @param payload Data message
     */
    private static HalPropValue createDataMessage(HalPropValueBuilder builder, VmsLayer layer,
            int publisherId, byte[] payload) {
        // Message type + layer
        List<Integer> message = new ArrayList<Integer>();
        appendLayer(message, layer);
        // Publisher ID
        message.add(publisherId);

        return createVmsMessage(builder, VmsMessageType.DATA, message, payload);
    }

    /**
     * Creates a SUBSCRIPTION_CHANGE or SUBSCRIPTION_RESPONSE type {@link HalPropValue}.
     *
     * Both message types have the same format:
     * <ul>
     * <li>Message type
     * <li>Sequence number
     * <li>Number of layers
     * <li>Number of associated layers
     * <li>Layers (x number of layers) (see {@link #appendLayer})
     * <li>Associated layers (x number of associated layers) (see {@link #appendAssociatedLayer})
     * </ul>
     *
     * @param messageType       Either SUBSCRIPTIONS_CHANGE or SUBSCRIPTIONS_RESPONSE.
     * @param subscriptionState The subscription state to encode in the message.
     */
    private static HalPropValue createSubscriptionStateMessage(HalPropValueBuilder builder,
            int messageType, VmsSubscriptionState subscriptionState) {
        List<Integer> message = new ArrayList<Integer>();
        // Sequence number
        message.add(subscriptionState.getSequenceNumber());

        Set<VmsLayer> layers = subscriptionState.getLayers();
        Set<VmsAssociatedLayer> associatedLayers = subscriptionState.getAssociatedLayers();

        // Number of layers
        message.add(layers.size());
        // Number of associated layers
        message.add(associatedLayers.size());

        // Layers
        for (VmsLayer layer : layers) {
            appendLayer(message, layer);
        }

        // Associated layers
        for (VmsAssociatedLayer layer : associatedLayers) {
            appendAssociatedLayer(message, layer);
        }

        return createVmsMessage(builder, messageType, message);
    }

    /**
     * Creates an AVAILABILITY_CHANGE or AVAILABILITY_RESPONSE type {@link HalPropValue}.
     *
     * Both message types have the same format:
     * <ul>
     * <li>Message type
     * <li>Sequence number.
     * <li>Number of associated layers.
     * <li>Associated layers (x number of associated layers) (see {@link #appendAssociatedLayer})
     * </ul>
     *
     * @param messageType     Either AVAILABILITY_CHANGE or AVAILABILITY_RESPONSE.
     * @param availableLayers The available layers to encode in the message.
     */
    private static HalPropValue createAvailableLayersMessage(HalPropValueBuilder builder,
            int messageType, VmsAvailableLayers availableLayers) {
        List<Integer> message = new ArrayList<Integer>();
        // Sequence number
        message.add(availableLayers.getSequence());

        // Number of associated layers
        message.add(availableLayers.getAssociatedLayers().size());

        // Associated layers
        for (VmsAssociatedLayer layer : availableLayers.getAssociatedLayers()) {
            appendAssociatedLayer(message, layer);
        }
        return createVmsMessage(builder, messageType, message);
    }

    /**
     * Creates a {@link HalPropValue} of the requested message type, and a list of values.
     *
     * @param messageType Type of message, from {@link VmsMessageType}
     * @param values A list of values.
     */
    private static HalPropValue createVmsMessage(HalPropValueBuilder builder,  int messageType,
            List<Integer> values) {
        int[] intValues = new int[values.size() + 1];
        intValues[0] = messageType;
        for (int i = 0; i < values.size(); i++) {
            intValues[i + 1] = values.get(i);
        }
        return builder.build(HAL_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                /*timestamp=*/0, VehiclePropertyStatus.AVAILABLE,
                /*values=*/intValues);
    }

    /**
     * Creates a {@link HalPropValue} of the requested message type, and a list of values.
     *
     * @param messageType Type of message, from {@link VmsMessageType}
     * @param values A list of values.
     * @param payload The byte values.
     */
    private static HalPropValue createVmsMessage(HalPropValueBuilder builder, int messageType,
            List<Integer> values, byte[] payload) {
        int[] intValues = new int[values.size() + 1];
        intValues[0] = messageType;
        for (int i = 0; i < values.size(); i++) {
            intValues[i + 1] = values.get(i);
        }
        return builder.build(HAL_PROPERTY_ID, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                /*timestamp=*/ 0, VehiclePropertyStatus.AVAILABLE,
                /*int32Values=*/ intValues, /*floatValues=*/ EMPTY_FLOAT_ARRAY,
                /*int64Values=*/ EMPTY_LONG_ARRAY, /*stringValue=*/ "", /*byteValues=*/payload);
    }

    /**
     * Appends a {@link VmsLayer} to an encoded VMS message.
     *
     * Layer format:
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * </ul>
     *
     * @param message Message to append to.
     * @param layer   Layer to append.
     */
    private static void appendLayer(List<Integer> message, VmsLayer layer) {
        message.add(layer.getType());
        message.add(layer.getSubtype());
        message.add(layer.getVersion());
    }

    /**
     * Appends a {@link VmsAssociatedLayer} to an encoded VMS message.
     *
     * AssociatedLayer format:
     * <ul>
     * <li>Layer ID
     * <li>Layer subtype
     * <li>Layer version
     * <li>Number of publishers
     * <li>Publisher ID (x number of publishers)
     * </ul>
     *
     * @param message Message to append to.
     * @param layer   Layer to append.
     */
    private static void appendAssociatedLayer(List<Integer> message, VmsAssociatedLayer layer) {
        message.add(layer.getVmsLayer().getType());
        message.add(layer.getVmsLayer().getSubtype());
        message.add(layer.getVmsLayer().getVersion());
        message.add(layer.getProviderIds().size());
        message.addAll(layer.getProviderIds());
    }

    private static VmsLayer parseVmsLayerFromMessage(List<Integer> message) {
        return parseVmsLayerAtIndex(message,
                VmsMessageWithLayerIntegerValuesIndex.LAYER_TYPE);
    }

    private static VmsLayer parseVmsLayerAtIndex(List<Integer> message, int index) {
        List<Integer> layerValues = message.subList(index, index + NUM_INTEGERS_IN_VMS_LAYER);
        return new VmsLayer(layerValues.get(0), layerValues.get(1), layerValues.get(2));
    }

    private static int parsePublisherIdFromMessage(List<Integer> message) {
        return message.get(VmsMessageWithLayerAndPublisherIdIntegerValuesIndex.PUBLISHER_ID);
    }
}
