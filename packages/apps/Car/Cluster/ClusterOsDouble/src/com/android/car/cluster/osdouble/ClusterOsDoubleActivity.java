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

package com.android.car.cluster.osdouble;

import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
import static android.car.cluster.ClusterHomeManager.UI_TYPE_CLUSTER_HOME;
import static android.car.cluster.ClusterHomeManager.UI_TYPE_CLUSTER_NONE;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

import static com.android.car.cluster.osdouble.ClusterOsDoubleApplication.TAG;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.content.res.CompatibilityInfo;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.IntArray;
import android.util.Log;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.cluster.sensors.Sensors;
import com.android.car.cluster.view.BitmapFetcher;
import com.android.car.cluster.view.ClusterViewModel;
import com.android.car.cluster.view.ImageResolver;
import com.android.car.cluster.view.NavStateController;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * The Activity which plays the role of ClusterOs for the testing.
 */
public class ClusterOsDoubleActivity extends ComponentActivity {
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // VehiclePropertyGroup
    private static final int SYSTEM = 0x10000000;
    private static final int VENDOR = 0x20000000;
    private static final int MASK = 0xf0000000;

    private static final int VENDOR_CLUSTER_REPORT_STATE = toVendorId(
            VehiclePropertyIds.CLUSTER_REPORT_STATE);
    private static final int VENDOR_CLUSTER_SWITCH_UI = toVendorId(
            VehiclePropertyIds.CLUSTER_SWITCH_UI);
    private static final int VENDOR_CLUSTER_NAVIGATION_STATE = toVendorId(
            VehiclePropertyIds.CLUSTER_NAVIGATION_STATE);
    private static final int VENDOR_CLUSTER_REQUEST_DISPLAY = toVendorId(
            VehiclePropertyIds.CLUSTER_REQUEST_DISPLAY);
    private static final int VENDOR_CLUSTER_DISPLAY_STATE = toVendorId(
            VehiclePropertyIds.CLUSTER_DISPLAY_STATE);

    // For the detail, please refer to vehicle/2.0/types.hal.
    private static final int REPORT_STATE_MAIN_UI_INDEX = 9;
    private static final int REPORT_STATE_UI_AVAILABILITY_INDEX = 11;

    private DisplayManager mDisplayManager;
    private CarPropertyManager mPropertyManager;

    private SurfaceView mSurfaceView;
    private Rect mBounds;
    private Insets mInsets;
    private static VirtualDisplay sVirtualDisplay;

    private ClusterViewModel mClusterViewModel;
    private final ArrayMap<Sensors.Gear, View> mGearsToIcon = new ArrayMap<>();
    private final ArrayList<View> mUiToButton = new ArrayList<>();
    int mCurrentUi = UI_TYPE_CLUSTER_HOME;
    private final IntArray mUiAvailability = new IntArray();

    private NavStateController mNavStateController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisplayManager = getSystemService(DisplayManager.class);

        Car.createCar(getApplicationContext(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) return;
                    mPropertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
                    initClusterOsDouble();
                });

        View view = getLayoutInflater().inflate(R.layout.cluster_os_double_activity, null);
        mSurfaceView = view.findViewById(R.id.cluster_display);
        mSurfaceView.getHolder().addCallback(mSurfaceViewCallback);
        setContentView(view);

        registerGear(findViewById(R.id.gear_parked), Sensors.Gear.PARK);
        registerGear(findViewById(R.id.gear_reverse), Sensors.Gear.REVERSE);
        registerGear(findViewById(R.id.gear_neutral), Sensors.Gear.NEUTRAL);
        registerGear(findViewById(R.id.gear_drive), Sensors.Gear.DRIVE);

        mClusterViewModel = new ViewModelProvider(this).get(ClusterViewModel.class);
        mClusterViewModel.getSensor(Sensors.SENSOR_GEAR).observe(this, this::updateSelectedGear);

        registerSensor(findViewById(R.id.info_fuel), mClusterViewModel.getFuelLevel());
        registerSensor(findViewById(R.id.info_speed), mClusterViewModel.getSpeed());
        registerSensor(findViewById(R.id.info_range), mClusterViewModel.getRange());
        registerSensor(findViewById(R.id.info_rpm), mClusterViewModel.getRPM());

        // The order should be matched with ClusterHomeApplication.
        registerUi(findViewById(R.id.btn_car_info));
        registerUi(findViewById(R.id.btn_nav));
        registerUi(findViewById(R.id.btn_music));
        registerUi(findViewById(R.id.btn_phone));

        BitmapFetcher bitmapFetcher = new BitmapFetcher(this);
        ImageResolver imageResolver = new ImageResolver(bitmapFetcher);
        mNavStateController = new NavStateController(
                findViewById(R.id.navigation_state), imageResolver);
    }

    private final SurfaceHolder.Callback mSurfaceViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surfaceCreated, holder: " + holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surfaceChanged, holder: " + holder + ", size:" + width + "x" + height
                    + ", format:" + format);

            // Create mock unobscured area to report to navigation activity.
            int obscuredWidth = (int) getResources()
                    .getDimension(R.dimen.speedometer_overlap_width);
            int obscuredHeight = (int) getResources()
                    .getDimension(R.dimen.navigation_gradient_height);
            mBounds = new Rect(/* left= */ 0, /* top= */ 0,
                    /* right= */ width, /* bottom= */ height);
            // Adds some empty space in the boundary of the display to verify if mBounds works.
            mBounds.inset(/* dx= */ 12, /* dy= */ 12);
            mInsets = Insets.of(obscuredWidth, obscuredHeight, obscuredWidth, obscuredHeight);
            if (sVirtualDisplay == null) {
                sVirtualDisplay = createVirtualDisplay(holder.getSurface(), width, height);
            } else {
                DisplayInfo displayInfo = new DisplayInfo();
                DisplayMetrics boundsMetrics = new DisplayMetrics();
                boolean isDisplayValid = sVirtualDisplay.getDisplay().getDisplayInfo(displayInfo);
                displayInfo.getLogicalMetrics(boundsMetrics,
                        CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO, /* configuration= */ null);
                if (isDisplayValid && boundsMetrics.widthPixels == width
                        && boundsMetrics.heightPixels == height) {
                    sVirtualDisplay.setSurface(holder.getSurface());
                } else {
                    // Display was resized, delete existing and create new display.
                    // TODO(b/254931119): Resize the display instead of replacing it.
                    sVirtualDisplay.release();
                    sVirtualDisplay = createVirtualDisplay(holder.getSurface(), width, height);
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed, holder: " + holder + ", detaching surface from"
                    + " display, surface: " + holder.getSurface());
            // detaching surface is similar to turning off the display
            sVirtualDisplay.setSurface(null);
        }
    };

    private VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) {
        Log.i(TAG, "createVirtualDisplay, surface: " + surface + ", width: " + width
                + "x" + height);
        return mDisplayManager.createVirtualDisplay(/* projection= */ null, "ClusterOsDouble-VD",
                width, height, 160, surface,
                // Don't use VIRTUAL_DISPLAY_FLAG_TRUSTED, because we don't want the cluster display
                // to be the focus display which can hinder Rotary service (b/206862329).
                VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                /* callback= */ null, /* handler= */ null, "ClusterDisplay");
    }

    private void initClusterOsDouble() {
        mPropertyManager.registerCallback(mPropertyEventCallback,
                VENDOR_CLUSTER_REPORT_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE);
        mPropertyManager.registerCallback(mPropertyEventCallback,
                VENDOR_CLUSTER_NAVIGATION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE);
        mPropertyManager.registerCallback(mPropertyEventCallback,
                VENDOR_CLUSTER_REQUEST_DISPLAY, CarPropertyManager.SENSOR_RATE_ONCHANGE);
    }

    private final CarPropertyEventCallback mPropertyEventCallback = new CarPropertyEventCallback() {
        @Override
        public void onChangeEvent(CarPropertyValue carProp) {
            int propertyId = carProp.getPropertyId();
            if (propertyId == VENDOR_CLUSTER_REPORT_STATE) {
                onClusterReportState((Object[]) carProp.getValue());
            } else if (propertyId == VENDOR_CLUSTER_NAVIGATION_STATE) {
                onClusterNavigationState((byte[]) carProp.getValue());
            } else if (propertyId == VENDOR_CLUSTER_REQUEST_DISPLAY) {
                onClusterRequestDisplay((Integer) carProp.getValue());
            }
        }

        @Override
        public void onErrorEvent(int propId, int zone) {

        }
    };

    private void onClusterReportState(Object[] values) {
        if (DBG) Log.d(TAG, "onClusterReportState: " + Arrays.toString(values));
        // CLUSTER_REPORT_STATE should have at least 11 elements, check vehicle/2.0/types.hal.
        if (values.length < 11) {
            throw new IllegalArgumentException("Insufficient size of CLUSTER_REPORT_STATE");
        }
        int mainUi = (Integer) values[REPORT_STATE_MAIN_UI_INDEX];
        int totalUiSize = values.length - REPORT_STATE_UI_AVAILABILITY_INDEX;
        mUiAvailability.resize(totalUiSize);
        for (int i = 0; i < totalUiSize; ++i) {
            mUiAvailability.set(i, (Byte) values[i + REPORT_STATE_UI_AVAILABILITY_INDEX]);
        }
        selectUiButton(mainUi);
    }

    private void selectUiButton(int mainUi) {
        for (int i = mUiToButton.size() - 1; i >= 0; --i) {
            View button = mUiToButton.get(i);
            button.setSelected(i == mainUi);
        }
        mCurrentUi = mainUi;
    }

    private void onClusterNavigationState(byte[] protoBytes) {
        if (DBG) Log.d(TAG, "onClusterNavigationState: " + Arrays.toString(protoBytes));
        try {
            NavigationStateProto navState = NavigationStateProto.parseFrom(protoBytes);
            mNavStateController.update(navState);
            if (DBG) Log.d(TAG, "onClusterNavigationState: " + navState);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Error parsing navigation state proto", e);
        }
    }

    private void onClusterRequestDisplay(Integer mainUi) {
        if (DBG) Log.d(TAG, "onClusterRequestDisplay: " + mainUi);
        sendDisplayState();
    }

    private static int toVendorId(int propId) {
        return (propId & ~MASK) | VENDOR;
    }

    private <V> void registerSensor(TextView textView, LiveData<V> source) {
        String emptyValue = getString(R.string.info_value_empty);
        source.observe(this, value -> {
            // Need to check that the text is actually different, or else
            // it will generate a bunch of CONTENT_CHANGE_TYPE_TEXT accessability
            // actions. This will cause cts tests to fail when they waitForIdle(),
            // and the system never idles because it's constantly updating these
            // TextViews
            if (value != null && !value.toString().contentEquals(textView.getText())) {
                textView.setText(value.toString());
            }
            if (value == null && !emptyValue.contentEquals(textView.getText())) {
                textView.setText(emptyValue);
            }
        });
    }

    private void registerGear(View view, Sensors.Gear gear) {
        mGearsToIcon.put(gear, view);
    }

    private void updateSelectedGear(Sensors.Gear gear) {
        for (Map.Entry<Sensors.Gear, View> entry : mGearsToIcon.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == gear);
        }
    }

    private void registerUi(View view) {
        int currentUi = mUiToButton.size();
        mUiToButton.add(view);
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "onTouch: " + currentUi);
                switchUi(currentUi);
            }
            return true;
        });
    }

    private void sendDisplayState() {
        if (mBounds == null || mInsets == null) return;
        mPropertyManager.setProperty(Integer[].class, VENDOR_CLUSTER_DISPLAY_STATE,
                VEHICLE_AREA_TYPE_GLOBAL, new Integer[] {
                        1  /* Display On */,
                        mBounds.left, mBounds.top, mBounds.right, mBounds.bottom,
                        mInsets.left, mInsets.top, mInsets.right, mInsets.bottom,
                        UI_TYPE_CLUSTER_HOME, UI_TYPE_CLUSTER_NONE});
    }

    private void switchUi(int mainUi) {
        mPropertyManager.setProperty(Integer.class, VENDOR_CLUSTER_SWITCH_UI,
                VEHICLE_AREA_TYPE_GLOBAL, Integer.valueOf(mainUi));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            int nextUi = mCurrentUi;
            do {
                nextUi = nextUi + 1;
                if (nextUi >= mUiToButton.size()) nextUi = 0;
            } while (mUiAvailability.get(nextUi) == 0);
            switchUi(nextUi);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
