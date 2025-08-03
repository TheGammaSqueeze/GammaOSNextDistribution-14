/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.hal.test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import static java.lang.Integer.toHexString;

import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Mocked implementation of {@link IVehicle}.
 */
public class HidlMockedVehicleHal extends IVehicle.Stub {
    private static final String TAG = HidlMockedVehicleHal.class.getSimpleName();

    private final Object mLock = new Object();

    /**
     * Interface for handler of each property.
     */
    public interface VehicleHalPropertyHandler {
        default void onPropertySet(VehiclePropValue value) {}

        default VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return null;
        }

        default void onPropertySubscribe(int property, float sampleRate) {}

        default void onPropertyUnsubscribe(int property) {}

        VehicleHalPropertyHandler NOP = new VehicleHalPropertyHandler() {};
    }

    @GuardedBy("mLock")
    private final Map<Integer, VehicleHalPropertyHandler> mPropertyHandlerMap = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<Integer, VehiclePropConfig> mConfigs = new HashMap<>();

    @GuardedBy("mLock")
    private final Map<Integer, List<IVehicleCallback>> mSubscribers = new HashMap<>();

    public void addProperties(VehiclePropConfig... configs) {
        synchronized (mLock) {
            for (VehiclePropConfig config : configs) {
                addProperty(config, new DefaultPropertyHandler(config, null));
            }
        }
    }

    public void addProperty(VehiclePropConfig config, VehicleHalPropertyHandler handler) {
        synchronized (mLock) {
            mPropertyHandlerMap.put(config.prop, handler);
            mConfigs.put(config.prop, config);
        }
    }

    public void addStaticProperty(VehiclePropConfig config, VehiclePropValue value) {
        synchronized (mLock) {
            addProperty(config, new StaticPropertyHandler(value));
        }
    }

    public boolean waitForSubscriber(int propId, long timeoutMillis) {
        long startTime = SystemClock.elapsedRealtime();
        try {
            synchronized (mLock) {
                while (mSubscribers.get(propId) == null) {
                    long waitMillis = startTime - SystemClock.elapsedRealtime() + timeoutMillis;
                    if (waitMillis < 0) break;
                    wait(waitMillis);
                }
                return mSubscribers.get(propId) != null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void injectEvent(VehiclePropValue value, boolean setProperty) {
        synchronized (mLock) {
            List<IVehicleCallback> callbacks = mSubscribers.get(value.prop);
            assertNotNull("Injecting event failed for property: " + value.prop
                    + ". No listeners found", callbacks);

            if (setProperty) {
                // Update property if requested
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(value.prop);
                if (handler != null) {
                    handler.onPropertySet(value);
                }
            }

            for (int i = 0; i < callbacks.size(); i++) {
                IVehicleCallback callback = callbacks.get(i);
                try {
                    ArrayList<VehiclePropValue> values = new ArrayList<>(1);
                    values.add(value);
                    callback.onPropertyEvent(values);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed invoking callback", e);
                    fail("Remote exception while injecting events.");
                }
            }
        }
    }

    public void injectEvent(VehiclePropValue value) {
        injectEvent(value, false);
    }

    public void injectError(int errorCode, int propertyId, int areaId) {
        synchronized (mLock) {
            List<IVehicleCallback> callbacks = mSubscribers.get(propertyId);
            assertNotNull("Injecting error failed for property: " + propertyId
                    + ". No listeners found", callbacks);
            for (int i = 0; i < callbacks.size(); i++) {
                IVehicleCallback callback = callbacks.get(i);
                try {
                    callback.onPropertySetError(errorCode, propertyId, areaId);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed invoking callback", e);
                    fail("Remote exception while injecting errors.");
                }
            }
        }
    }

    @Override
    public ArrayList<VehiclePropConfig> getAllPropConfigs() {
        synchronized (mLock) {
            return new ArrayList<>(mConfigs.values());
        }
    }

    @Override
    public void getPropConfigs(ArrayList<Integer> props, getPropConfigsCallback cb) {
        synchronized (mLock) {
            ArrayList<VehiclePropConfig> res = new ArrayList<>();
            for (Integer prop : props) {
                VehiclePropConfig config = mConfigs.get(prop);
                if (config == null) {
                    cb.onValues(StatusCode.INVALID_ARG, new ArrayList<>());
                    return;
                }
                res.add(config);
            }
            cb.onValues(StatusCode.OK, res);
        }
    }

    @Override
    public void get(VehiclePropValue requestedPropValue, getCallback cb) {
        synchronized (mLock) {
            VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(requestedPropValue.prop);
            if (handler == null) {
                cb.onValues(StatusCode.INVALID_ARG, null);
            } else {
                try {
                    VehiclePropValue prop = handler.onPropertyGet(requestedPropValue);
                    cb.onValues(StatusCode.OK, prop);
                } catch (ServiceSpecificException e) {
                    // Don't directly pass ServiceSpecificException through binder to client, pass
                    // status code similar to how the c++ server does.
                    cb.onValues(e.errorCode, null);
                }
            }
        }
    }

    @Override
    public int set(VehiclePropValue propValue) {
        synchronized (mLock) {
            VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(propValue.prop);
            if (handler == null) {
                return StatusCode.INVALID_ARG;
            } else {
                try {
                    handler.onPropertySet(propValue);
                    return StatusCode.OK;
                } catch (ServiceSpecificException e) {
                    // Don't directly pass ServiceSpecificException through binder to client, pass
                    // status code similar to how the c++ server does.
                    return e.errorCode;
                }
            }
        }
    }

    @Override
    public int subscribe(IVehicleCallback callback, ArrayList<SubscribeOptions> options) {
        synchronized (mLock) {
            for (SubscribeOptions opt : options) {
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(opt.propId);
                if (handler == null) {
                    return StatusCode.INVALID_ARG;
                }

                handler.onPropertySubscribe(opt.propId, opt.sampleRate);
                List<IVehicleCallback> subscribers = mSubscribers.get(opt.propId);
                if (subscribers == null) {
                    subscribers = new ArrayList<>();
                    mSubscribers.put(opt.propId, subscribers);
                    notifyAll();
                } else {
                    for (int i = 0; i < subscribers.size(); i++) {
                        IVehicleCallback s = subscribers.get(i);
                        if (callback.asBinder() == s.asBinder()) {
                            // Remove callback that was registered previously for this property
                            subscribers.remove(callback);
                            break;
                        }
                    }
                }
                subscribers.add(callback);
            }
        }
        return StatusCode.OK;
    }

    @Override
    public int unsubscribe(IVehicleCallback callback, int propId) {
        synchronized (mLock) {
            VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(propId);
            if (handler == null) {
                return StatusCode.INVALID_ARG;
            }

            handler.onPropertyUnsubscribe(propId);
            List<IVehicleCallback> subscribers = mSubscribers.get(propId);
            if (subscribers != null) {
                subscribers.remove(callback);
                if (subscribers.size() == 0) {
                    mSubscribers.remove(propId);
                }
            }
        }
        return StatusCode.OK;
    }

    @Override
    public String debugDump() {
        return null;
    }

    public static class FailingPropertyHandler implements VehicleHalPropertyHandler {
        @Override
        public void onPropertySet(VehiclePropValue value) {
            fail("Unexpected onPropertySet call");
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            fail("Unexpected onPropertyGet call");
            return null;
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            fail("Unexpected onPropertySubscribe call");
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            fail("Unexpected onPropertyUnsubscribe call");
        }
    }

    @NotThreadSafe
    public static final class StaticPropertyHandler extends FailingPropertyHandler {

        private final VehiclePropValue mValue;

        public StaticPropertyHandler(VehiclePropValue value) {
            mValue = value;
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return mValue;
        }
    }

    @NotThreadSafe
    public static final class ErrorCodeHandler extends FailingPropertyHandler {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private int mStatus;

        public void setStatus(int status) {
            synchronized (mLock) {
                mStatus = status;
            }
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            synchronized (mLock) {
                throw new ServiceSpecificException(mStatus);
            }
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            synchronized (mLock) {
                throw new ServiceSpecificException(mStatus);
            }
        }
    }

    @NotThreadSafe
    public static final class DefaultPropertyHandler implements VehicleHalPropertyHandler {

        private final VehiclePropConfig mConfig;
        private VehiclePropValue mValue;
        private boolean mSubscribed = false;

        public DefaultPropertyHandler(VehiclePropConfig config, VehiclePropValue initialValue) {
            mConfig = config;
            mValue = initialValue;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            assertEquals(mConfig.prop, value.prop);
            assertEquals(VehiclePropertyAccess.WRITE,
                    mConfig.access & VehiclePropertyAccess.WRITE);
            mValue = value;
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertEquals(mConfig.prop, value.prop);
            assertEquals(VehiclePropertyAccess.READ,
                    mConfig.access & VehiclePropertyAccess.READ);
            return mValue;
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            assertEquals(mConfig.prop, property);
            mSubscribed = true;
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            assertEquals(mConfig.prop, property);
            if (!mSubscribed) {
                throw new IllegalArgumentException("Property was not subscribed 0x"
                        + toHexString(property));
            }
            mSubscribed = false;
        }
    }
}
