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

package android.car.testapi;

import static android.car.hardware.property.CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE;

import static java.lang.Integer.toHexString;

import android.annotation.Nullable;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyType;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.os.RemoteException;
import android.util.ArraySet;

import com.android.car.internal.PropertyPermissionMapping;
import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.AsyncPropertyServiceRequestList;
import com.android.car.internal.property.CarPropertyConfigList;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetPropertyConfigListResult;
import com.android.car.internal.property.GetSetValueResult;
import com.android.car.internal.property.GetSetValueResultList;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.car.internal.util.PairSparseArray;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This is fake implementation of the service which is used in
 * {@link android.car.hardware.property.CarPropertyManager}.
 *
 * @hide
 */
class FakeCarPropertyService extends ICarProperty.Stub implements CarPropertyController {
    private final Map<Integer, CarPropertyConfig> mConfigs = new HashMap<>();
    private final Map<PropKey, CarPropertyValue> mValues = new HashMap<>();

    private final PropertyPermissionMapping mPermissions = new PropertyPermissionMapping();

    // Contains a list of values that were set from the manager.
    private final ArrayList<CarPropertyValue<?>> mValuesSet = new ArrayList<>();

    private final Object mLock = new Object();

    // Mapping between [propId, areaId] and a set of listeners.
    @GuardedBy("mLock")
    private final PairSparseArray<Set<ListenerInfo>> mListenersByPropIdAreaId =
            new PairSparseArray<>();

    @Override
    public void registerListener(List<CarSubscription> subscriptions,
            ICarPropertyEventListener listener) {
        synchronized (mLock) {
            for (int i = 0; i < subscriptions.size(); i++) {
                int propId = subscriptions.get(i).propertyId;
                for (int areaId : subscriptions.get(i).areaIds) {
                    Set<ListenerInfo> propListeners = mListenersByPropIdAreaId.get(propId, areaId);
                    if (propListeners == null) {
                        propListeners = new ArraySet<>();
                        mListenersByPropIdAreaId.put(propId, areaId, propListeners);
                    }

                    propListeners.add(new ListenerInfo(listener));
                }
            }
        }
    }

    @Override
    public void unregisterListener(int propId, ICarPropertyEventListener listener)
            throws RemoteException {
        synchronized (mLock) {
            for (int areaId : mListenersByPropIdAreaId.getSecondKeysForFirstKey(propId)) {
                Set<ListenerInfo> propListeners = mListenersByPropIdAreaId.get(propId, areaId);
                if (propListeners.remove(new ListenerInfo(listener)) && propListeners.isEmpty()) {
                    mListenersByPropIdAreaId.remove(propId, areaId);
                }
            }
        }
    }

    @Override
    public CarPropertyConfigList getPropertyList() throws RemoteException {
        return new CarPropertyConfigList(new ArrayList<>(mConfigs.values()));
    }

    @Override
    public GetPropertyConfigListResult getPropertyConfigList(int[] propIds) {
        List<CarPropertyConfig> configs = new ArrayList<>(propIds.length);
        for (int prop : propIds) {
            CarPropertyConfig cfg = mConfigs.get(prop);
            if (cfg != null) {
                configs.add(cfg);
            }
        }
        GetPropertyConfigListResult result = new GetPropertyConfigListResult();
        result.unsupportedPropIds = new int[0];
        result.missingPermissionPropIds = new int[0];
        result.carPropertyConfigList = new CarPropertyConfigList(configs);
        return result;
    }

    @Override
    public void getPropertiesAsync(AsyncPropertyServiceRequestList asyncPropertyServiceRequests,
            IAsyncPropertyResultCallback asyncPropertyResultCallback, long timeoutInMs)
            throws RemoteException {
        List<AsyncPropertyServiceRequest> asyncPropertyServiceRequestList =
                asyncPropertyServiceRequests.getList();
        List<GetSetValueResult> getValueResults = new ArrayList<>();
        for (int i = 0; i < asyncPropertyServiceRequestList.size(); i++) {
            AsyncPropertyServiceRequest asyncPropertyServiceRequest =
                    asyncPropertyServiceRequestList.get(i);
            getValueResults.add(GetSetValueResult.newGetValueResult(
                    asyncPropertyServiceRequest.getRequestId(),
                    getProperty(asyncPropertyServiceRequest.getPropertyId(),
                            asyncPropertyServiceRequest.getAreaId())));
        }
        asyncPropertyResultCallback.onGetValueResults(new GetSetValueResultList(getValueResults));
    }

    @Override
    public void setPropertiesAsync(AsyncPropertyServiceRequestList asyncPropertyServiceRequests,
            IAsyncPropertyResultCallback asyncPropertyResultCallback, long timeoutInMs)
            throws RemoteException {
        List<AsyncPropertyServiceRequest> asyncPropertyServiceRequestList =
                asyncPropertyServiceRequests.getList();
        List<GetSetValueResult> setValueResults = new ArrayList<>();
        for (int i = 0; i < asyncPropertyServiceRequestList.size(); i++) {
            AsyncPropertyServiceRequest asyncPropertyServiceRequest =
                    asyncPropertyServiceRequestList.get(i);
            setProperty(asyncPropertyServiceRequest.getCarPropertyValue(), /* listener= */ null);
            setValueResults.add(GetSetValueResult.newSetValueResult(
                    asyncPropertyServiceRequest.getRequestId(), /* updateTimestampNanos= */ 0));
        }
        asyncPropertyResultCallback.onSetValueResults(new GetSetValueResultList(setValueResults));
    }

    @Override
    public CarPropertyValue getProperty(int prop, int zone) throws RemoteException {
        return mValues.get(PropKey.of(prop, zone));
    }

    @Override
    public void setProperty(CarPropertyValue prop, ICarPropertyEventListener listener)
            throws RemoteException {
        mValues.put(PropKey.of(prop), prop);
        mValuesSet.add(prop);
        sendEvent(prop);
    }

    @Override
    public void cancelRequests(int[] serviceRequestIds) {
        // Do nothing.
    }

    @Override
    public String getReadPermission(int propId) throws RemoteException {
        return mConfigs.containsKey(propId) ? mPermissions.getReadPermission(propId) : null;
    }

    @Override
    public String getWritePermission(int propId) throws RemoteException {
        return mConfigs.containsKey(propId) ? mPermissions.getWritePermission(propId) : null;
    }

    @Override
    public int[] getSupportedNoReadPermPropIds(int[] propertyids) {
        return new int[0];
    }

    @Override
    public boolean isSupportedAndHasWritePermissionOnly(int propertyId) {
        return false;
    }

    @Override
    public CarPropertyController addProperty(Integer propId, Object value) {
        int areaType = getVehicleAreaType(propId);
        Class<?> type = getPropertyType(propId);
        CarPropertyConfig.Builder<?> builder = CarPropertyConfig
                .newBuilder(type, propId, areaType);
        mConfigs.put(propId, builder.build());
        if (value != null) {
            updateValues(false, new CarPropertyValue<>(propId, 0, value));
        }

        return this;
    }

    @Override
    public CarPropertyController addProperty(CarPropertyConfig<?> config,
            @Nullable CarPropertyValue<?> value) {
        mConfigs.put(config.getPropertyId(), config);
        if (value != null) {
            updateValues(false, value);
        }
        return this;
    }

    @Override
    public void updateValues(boolean triggerListeners, CarPropertyValue<?>... propValues) {
        for (CarPropertyValue v : propValues) {
            mValues.put(PropKey.of(v), v);
            if (triggerListeners) {
                sendEvent(v);
            }
        }
    }

    private void sendEvent(CarPropertyValue v) {
        synchronized (mLock) {
            Set<ListenerInfo> listeners = mListenersByPropIdAreaId.get(v.getPropertyId(),
                    v.getAreaId());
            if (listeners != null) {
                for (ListenerInfo listenerInfo : listeners) {
                    List<CarPropertyEvent> events = new ArrayList<>();
                    events.add(new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, v));
                    try {
                        listenerInfo.mListener.onEvent(events);
                    } catch (RemoteException e) {
                        // This is impossible as the code runs within the same process in test.
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public List<CarPropertyValue<?>> getSetValues() {
        // Explicitly return the instance of this object rather than copying it such that test code
        // will have a chance to clear this list if needed.
        return mValuesSet;
    }

    /** Consists of property id and area */
    private static class PropKey {
        final int mPropId;
        final int mAreaId;

        private PropKey(int propId, int areaId) {
            this.mPropId = propId;
            this.mAreaId = areaId;
        }

        static PropKey of(int propId, int areaId) {
            return new PropKey(propId, areaId);
        }

        static PropKey of(CarPropertyValue carPropertyValue) {
            return of(carPropertyValue.getPropertyId(), carPropertyValue.getAreaId());
        }

        @Override

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PropKey)) {
                return false;
            }
            PropKey propKey = (PropKey) o;
            return mPropId == propKey.mPropId && mAreaId == propKey.mAreaId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPropId, mAreaId);
        }
    }

    private static class ListenerInfo {
        private final ICarPropertyEventListener mListener;

        ListenerInfo(ICarPropertyEventListener listener) {
            this.mListener = listener;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ListenerInfo)) {
                return false;
            }
            ListenerInfo that = (ListenerInfo) o;
            return Objects.equals(mListener, that.mListener);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mListener);
        }
    }

    private static Class<?> getPropertyType(int propId) {
        int type = propId & VehiclePropertyType.MASK;
        switch (type) {
            case VehiclePropertyType.BOOLEAN:
                return Boolean.class;
            case VehiclePropertyType.FLOAT:
                return Float.class;
            case VehiclePropertyType.INT32:
                return Integer.class;
            case VehiclePropertyType.INT64:
                return Long.class;
            case VehiclePropertyType.FLOAT_VEC:
                return Float[].class;
            case VehiclePropertyType.INT32_VEC:
                return Integer[].class;
            case VehiclePropertyType.INT64_VEC:
                return Long[].class;
            case VehiclePropertyType.STRING:
                return String.class;
            case VehiclePropertyType.BYTES:
                return byte[].class;
            case VehiclePropertyType.MIXED:
                return Object.class;
            default:
                throw new IllegalArgumentException("Unexpected type: " + toHexString(type));
        }
    }

    private static int getVehicleAreaType(int propId) {
        int halArea = propId & VehicleArea.MASK;
        switch (halArea) {
            case VehicleArea.GLOBAL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
            case VehicleArea.SEAT:
                return VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
            case VehicleArea.DOOR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_DOOR;
            case VehicleArea.WINDOW:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW;
            case VehicleArea.MIRROR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR;
            case VehicleArea.WHEEL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL;
            default:
                if (Flags.androidVicVehicleProperties()) {
                    if (halArea == VehicleArea.VENDOR) {
                        return VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR;
                    }
                }
                throw new RuntimeException("Unsupported area type " + halArea);
        }
    }

    /** Copy from VHAL generated file VehicleArea.java */
    private static final class VehicleArea {
        static final int GLOBAL = 0x01000000;
        static final int WINDOW = 0x03000000;
        static final int MIRROR = 0x04000000;
        static final int SEAT = 0x05000000;
        static final int DOOR = 0x06000000;
        static final int WHEEL = 0x07000000;
        static final int VENDOR = 0x08000000;
        static final int MASK = 0x0f000000;
    }
}
