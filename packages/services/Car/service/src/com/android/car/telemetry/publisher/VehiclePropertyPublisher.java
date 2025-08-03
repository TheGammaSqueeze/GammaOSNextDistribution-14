/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.telemetry.publisher;

import static com.android.car.telemetry.CarTelemetryService.DEBUG;

import static java.lang.Integer.toHexString;

import android.annotation.NonNull;
import android.car.VehiclePropertyIds;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.Publisher.PublisherCase;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.CarPropertyService;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Publisher for Vehicle Property changes, aka {@code CarPropertyService}.
 *
 * <p> When a subscriber is added, it registers a car property change listener for the
 * property id of the subscriber and starts pushing the change events to the subscriber.
 */
public class VehiclePropertyPublisher extends AbstractPublisher {

    private final CarPropertyService mCarPropertyService;
    private final Handler mTelemetryHandler;
    // The class only reads, no need to synchronize this object.
    // Maps property_id to CarPropertyConfig.
    private final SparseArray<CarPropertyConfig> mCarPropertyList;
    private final ICarPropertyEventListener mCarPropertyEventListener =
            new ICarPropertyEventListener.Stub() {
                @Override
                public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                    if (DEBUG) {
                        Slogf.d(CarLog.TAG_TELEMETRY,
                                "Received " + events.size() + " vehicle property events");
                    }
                    for (CarPropertyEvent event : events) {
                        onVehicleEvent(event);
                    }
                }
            };

    // Maps property id to the PropertyData containing batch of its bundles and subscribers.
    // Each property is batched separately.
    private final SparseArray<PropertyData> mPropertyDataLookup = new SparseArray<>();
    private long mBatchIntervalMillis = 100L;  // Batch every 100 milliseconds = 10Hz

    public VehiclePropertyPublisher(
            @NonNull CarPropertyService carPropertyService,
            @NonNull PublisherListener listener,
            @NonNull Handler handler) {
        super(listener);
        mCarPropertyService = carPropertyService;
        mTelemetryHandler = handler;
        // Load car property list once, as the list doesn't change runtime.
        List<CarPropertyConfig> propertyList = mCarPropertyService.getPropertyList()
                .getConfigs();
        mCarPropertyList = new SparseArray<>(propertyList.size());
        for (CarPropertyConfig property : propertyList) {
            mCarPropertyList.append(property.getPropertyId(), property);
        }
    }

    @Override
    public void addDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        Preconditions.checkArgument(
                publisherParam.getPublisherCase()
                        == TelemetryProto.Publisher.PublisherCase.VEHICLE_PROPERTY,
                "Subscribers only with VehicleProperty publisher are supported by this class.");
        int propertyId = publisherParam.getVehicleProperty().getVehiclePropertyId();
        CarPropertyConfig<?> config = mCarPropertyList.get(propertyId);
        Preconditions.checkArgument(
                config != null,
                "Vehicle property " + VehiclePropertyIds.toString(propertyId) + " not found.");
        if (Flags.areaIdConfigAccess()) {
            for (AreaIdConfig<?> areaIdConfig : config.getAreaIdConfigs()) {
                int accessLevel = areaIdConfig.getAccess();
                Preconditions.checkArgument(
                        accessLevel == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                                || accessLevel
                                == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        "No access. Cannot read " + VehiclePropertyIds.toString(propertyId)
                                + " at areaId: " + areaIdConfig.getAreaId());
            }
        } else {
            Preconditions.checkArgument(
                    config.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                            || config.getAccess()
                            == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    "No access. Cannot read " + VehiclePropertyIds.toString(propertyId) + ".");
        }
        PropertyData propertyData = mPropertyDataLookup.get(propertyId);
        if (propertyData == null) {
            propertyData = new PropertyData(config);
            mPropertyDataLookup.put(propertyId, propertyData);
        }
        if (propertyData.subscribers.isEmpty()) {
            mCarPropertyService.registerListener(
                    propertyId,
                    publisherParam.getVehicleProperty().getReadRate(),
                    mCarPropertyEventListener);
        }
        propertyData.subscribers.add(subscriber);
    }

    @Override
    public void removeDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        if (publisherParam.getPublisherCase() != PublisherCase.VEHICLE_PROPERTY) {
            Slogf.w(CarLog.TAG_TELEMETRY,
                    "Expected VEHICLE_PROPERTY publisher, but received "
                            + publisherParam.getPublisherCase().name());
            return;
        }
        int propertyId = publisherParam.getVehicleProperty().getVehiclePropertyId();
        PropertyData propertyData = mPropertyDataLookup.get(propertyId);
        if (propertyData == null) {
            return;
        }
        propertyData.subscribers.remove(subscriber);
        if (propertyData.subscribers.isEmpty()) {
            mPropertyDataLookup.remove(propertyId);
            // Doesn't throw exception as listener is not null. mCarPropertyService and
            // local mCarPropertyToSubscribers will not get out of sync.
            mCarPropertyService.unregisterListener(propertyId, mCarPropertyEventListener);
        }
    }

    @Override
    public void removeAllDataSubscribers() {
        for (int i = 0; i < mPropertyDataLookup.size(); i++) {
            // Doesn't throw exception as listener is not null. mCarPropertyService and
            // local mCarPropertyToSubscribers will not get out of sync.
            mCarPropertyService.unregisterListener(
                    mPropertyDataLookup.keyAt(i), mCarPropertyEventListener);
        }
        mPropertyDataLookup.clear();
    }

    @Override
    public boolean hasDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        if (publisherParam.getPublisherCase() != PublisherCase.VEHICLE_PROPERTY) {
            return false;
        }
        int propertyId = publisherParam.getVehicleProperty().getVehiclePropertyId();
        return mPropertyDataLookup.contains(propertyId)
                && mPropertyDataLookup.get(propertyId).subscribers.contains(subscriber);
    }

    @VisibleForTesting
    public void setBatchIntervalMillis(long intervalMillis) {
        mBatchIntervalMillis = intervalMillis;
    }

    @Override
    protected void handleSessionStateChange(SessionAnnotation annotation) {}

    /**
     * Called when publisher receives new event. It's executed on a CarPropertyService's
     * worker thread.
     */
    private void onVehicleEvent(@NonNull CarPropertyEvent event) {
        // move the work from CarPropertyService's worker thread to the telemetry thread
        mTelemetryHandler.post(() -> {
            CarPropertyValue propValue = event.getCarPropertyValue();
            int propertyId = propValue.getPropertyId();
            PropertyData propertyData = mPropertyDataLookup.get(propertyId);
            PersistableBundle bundle = parseCarPropertyValue(
                    propValue, propertyData.config.getConfigArray());
            propertyData.pendingData.add(bundle);
            if (propertyData.pendingData.size() == 1) {
                mTelemetryHandler.postDelayed(
                        () -> {
                            pushPendingDataToSubscribers(propertyData);
                        },
                        mBatchIntervalMillis);
            }
        });
    }

    /**
     * Pushes bundle batch to subscribers and resets batch.
     */
    private void pushPendingDataToSubscribers(PropertyData propertyData) {
        for (DataSubscriber subscriber : propertyData.subscribers) {
            subscriber.push(propertyData.pendingData);
        }
        propertyData.pendingData = new ArrayList<>();
    }

    /**
     * Parses the car property value into a PersistableBundle.
     */
    private PersistableBundle parseCarPropertyValue(
            CarPropertyValue propValue, List<Integer> configArray) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putLong(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_TIMESTAMP, propValue.getTimestamp());
        bundle.putInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_PROP_ID, propValue.getPropertyId());
        bundle.putInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_AREA_ID, propValue.getAreaId());
        bundle.putInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STATUS, propValue.getStatus());
        int type = propValue.getPropertyId() & VehiclePropertyType.MASK;
        Object value = propValue.getValue();
        if (VehiclePropertyType.BOOLEAN == type) {
            bundle.putBoolean(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BOOLEAN, (Boolean) value);
        } else if (VehiclePropertyType.FLOAT == type) {
            bundle.putDouble(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT,
                    ((Float) value).doubleValue());
        } else if (VehiclePropertyType.INT32 == type) {
            bundle.putInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT, (Integer) value);
        } else if (VehiclePropertyType.INT64 == type) {
            bundle.putLong(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG, (Long) value);
        } else if (VehiclePropertyType.FLOAT_VEC == type) {
            Float[] floats = (Float[]) value;
            double[] doubles = new double[floats.length];
            for (int i = 0; i < floats.length; i++) {
                doubles[i] = floats[i].doubleValue();
            }
            bundle.putDoubleArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT_ARRAY, doubles);
        } else if (VehiclePropertyType.INT32_VEC == type) {
            Integer[] integers = (Integer[]) value;
            int[] ints = new int[integers.length];
            for (int i = 0; i < integers.length; i++) {
                ints[i] = integers[i];
            }
            bundle.putIntArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT_ARRAY, ints);
        } else if (VehiclePropertyType.INT64_VEC == type) {
            Long[] oldLongs = (Long[]) value;
            long[] longs = new long[oldLongs.length];
            for (int i = 0; i < oldLongs.length; i++) {
                longs[i] = oldLongs[i];
            }
            bundle.putLongArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG_ARRAY, longs);
        } else if (VehiclePropertyType.STRING == type) {
            bundle.putString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING, (String) value);
        } else if (VehiclePropertyType.BYTES == type) {
            bundle.putString(
                    Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BYTE_ARRAY,
                    new String((byte[]) value, StandardCharsets.UTF_8));
        } else if (VehiclePropertyType.MIXED == type) {
            Object[] mixed = (Object[]) value;
            int k = 0;
            if (configArray.get(0) == 1) {  // Has single String
                bundle.putString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING, (String) mixed[k++]);
            }
            if (configArray.get(1) == 1) {  // Has single Boolean
                bundle.putBoolean(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BOOLEAN,
                        (Boolean) mixed[k++]);
            }
            if (configArray.get(2) == 1) {  // Has single Integer
                bundle.putInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT, (Integer) mixed[k++]);
            }
            if (configArray.get(3) != 0) {  // Integer[] length is non-zero
                int[] ints = new int[configArray.get(3)];
                for (int i = 0; i < configArray.get(3); i++) {
                    ints[i] = (Integer) mixed[k++];
                }
                bundle.putIntArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT_ARRAY, ints);
            }
            if (configArray.get(4) == 1) {  // Has single Long
                bundle.putLong(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG, (Long) mixed[k++]);
            }
            if (configArray.get(5) != 0) {  // Long[] length is non-zero
                long[] longs = new long[configArray.get(5)];
                for (int i = 0; i < configArray.get(5); i++) {
                    longs[i] = (Long) mixed[k++];
                }
                bundle.putLongArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG_ARRAY, longs);
            }
            if (configArray.get(6) == 1) {  // Has single Float
                bundle.putDouble(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT,
                        ((Float) mixed[k++]).doubleValue());
            }
            if (configArray.get(7) != 0) {  // Float[] length is non-zero
                double[] doubles = new double[configArray.get(7)];
                for (int i = 0; i < configArray.get(7); i++) {
                    doubles[i] = ((Float) mixed[k++]).doubleValue();
                }
                bundle.putDoubleArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT_ARRAY, doubles);
            }
            if (configArray.get(8) != 0) {  // Byte[] length is non-zero
                byte[] bytes = new byte[configArray.get(8)];
                for (int i = 0; i < configArray.get(8); i++) {
                    bytes[i] = (Byte) mixed[k++];
                }
                bundle.putString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BYTE_ARRAY,
                        new String(bytes, StandardCharsets.UTF_8));
            }
        } else {
            throw new IllegalArgumentException(
                    "Unexpected property type: " + toHexString(type));
        }
        return bundle;
    }

    /**
     * Container class holding all the relevant information for a property.
     */
    private static final class PropertyData {
        // The config containing info on how to parse the property value.
        public final CarPropertyConfig config;
        // Subscribers subscribed to the property this PropertyData is mapped to.
        public final ArraySet<DataSubscriber> subscribers = new ArraySet<>();
        // The list of bundles that are batched together and pushed to subscribers
        public List<PersistableBundle> pendingData = new ArrayList<>();

        PropertyData(CarPropertyConfig propConfig) {
            config = propConfig;
        }
    }
}
