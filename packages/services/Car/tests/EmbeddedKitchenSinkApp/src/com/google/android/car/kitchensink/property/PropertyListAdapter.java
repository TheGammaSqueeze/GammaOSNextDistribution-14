/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.car.kitchensink.property;

import static java.lang.Integer.toHexString;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.car.hardware.property.Subscription;
import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.car.kitchensink.R;

import java.util.Arrays;
import java.util.List;

class PropertyListAdapter extends BaseAdapter implements ListAdapter {
    private static final float DEFAULT_RATE_HZ = 1f;
    private static final String TAG = "PropertyListAdapter";
    private static final String OFF = "Off";
    private static final String ON = "On";
    private static final String MAX_SAMPLE_RATE = "Maximum sample rate";
    private static final String AVG_SAMPLE_RATE = "Average sample rate";
    private static final String VUR = "VUR";
    private static final String[] DROP_MENU_FOR_CONTINUOUS =
            new String[]{OFF, MAX_SAMPLE_RATE, AVG_SAMPLE_RATE};
    private static final String[] DROP_MENU_FOR_CONTINUOUS_VUR =
            new String[]{OFF, VUR, MAX_SAMPLE_RATE, AVG_SAMPLE_RATE};
    private static final String[] DROP_MENU_FOR_ON_CHANGE = new String[]{OFF, ON};
    private final Context mContext;
    private final PropertyListEventListener mListener;
    private final CarPropertyManager mMgr;
    private final List<PropertyInfo> mPropInfo;
    private String[] mItems;

    PropertyListAdapter(List<PropertyInfo> propInfo, CarPropertyManager mgr, TextView eventLog,
            ScrollView svEventLog, Context context) {
        mContext = context;
        mListener = new PropertyListEventListener(eventLog, svEventLog);
        mMgr = mgr;
        mPropInfo = propInfo;
    }

    @Override
    public int getCount() {
        return mPropInfo.size();
    }

    @Override
    public Object getItem(int pos) {
        return mPropInfo.get(pos);
    }

    @Override
    public long getItemId(int pos) {
        return mPropInfo.get(pos).mPropId;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.property_list_item, null);
        }

        //Handle TextView and display string from your list
        TextView listItemText = (TextView) view.findViewById(R.id.tvPropertyName);
        listItemText.setText(mPropInfo.get(position).toString());

        Spinner dropdown = view.findViewById(R.id.tbRegisterPropertySpinner);

        CarPropertyConfig c = mPropInfo.get(position).mConfig;
        if (c.getChangeMode() == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            if (isVurSupportedForAtLeastOneAreaId(c)) {
                mItems = DROP_MENU_FOR_CONTINUOUS_VUR;
            } else {
                mItems = DROP_MENU_FOR_CONTINUOUS;
            }
        } else {
            mItems = DROP_MENU_FOR_ON_CHANGE;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
                R.layout.custom_spinner_dropdown_item, mItems);

        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(0);
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                String item = (String) adapterView.getItemAtPosition(pos);
                CarPropertyConfig c = mPropInfo.get(position).mConfig;
                int propId = c.getPropertyId();
                try {
                    if (OFF.equals(item)) {
                        mMgr.unsubscribePropertyEvents(propId, mListener);
                    } else if (VUR.equals(item)) {
                        // Default update rate is 1hz.
                        mListener.addPropertySelectedSampleRate(propId, DEFAULT_RATE_HZ);
                        mListener.updatePropertyStartTime(propId);
                        mListener.resetEventCountForProperty(propId);

                        // By default, VUR is on.
                        mMgr.subscribePropertyEvents(propId, mListener);
                    } else {
                        float updateRateHz = 0;
                        if (MAX_SAMPLE_RATE.equals(item)) {
                            updateRateHz = c.getMaxSampleRate();
                        } else if (AVG_SAMPLE_RATE.equals(item)) {
                            updateRateHz = (c.getMaxSampleRate() + c.getMinSampleRate()) / 2;
                        } else if (ON.equals(item)) {
                            updateRateHz = DEFAULT_RATE_HZ;
                        }
                        mListener.addPropertySelectedSampleRate(propId, updateRateHz);
                        mListener.updatePropertyStartTime(propId);
                        mListener.resetEventCountForProperty(propId);

                        mMgr.subscribePropertyEvents(List.of(
                                new Subscription.Builder(propId).setUpdateRateHz(updateRateHz)
                                .setVariableUpdateRateEnabled(false)
                                .build()),
                                /* callbackExecutor= */ null, mListener);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unhandled exception: ", e);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // do nothing.
            }
        });
        return view;
    }

    private boolean isVurSupportedForAtLeastOneAreaId(CarPropertyConfig carPropertyConfig) {
        List<AreaIdConfig<?>> areaIdConfigs = carPropertyConfig.getAreaIdConfigs();
        for (int i = 0; i < areaIdConfigs.size(); i++) {
            AreaIdConfig<?> areaIdConfig = areaIdConfigs.get(i);
            if (areaIdConfig.isVariableUpdateRateSupported()) {
                return true;
            }
        }
        return false;
    }


    private static class PropertyListEventListener implements CarPropertyEventCallback {
        private final ScrollView mScrollView;
        private final TextView mTvLogEvent;
        private final SparseArray<Float> mPropSampleRate = new SparseArray<>();
        private final SparseLongArray mStartTime = new SparseLongArray();
        private final SparseIntArray mNumEvents = new SparseIntArray();

        PropertyListEventListener(TextView logEvent, ScrollView scrollView) {
            mScrollView = scrollView;
            mTvLogEvent = logEvent;
        }

        void addPropertySelectedSampleRate(Integer propId, Float sampleRate) {
            mPropSampleRate.put(propId, sampleRate);
        }

        void updatePropertyStartTime(Integer propId) {
            mStartTime.put(propId, System.currentTimeMillis());
        }

        void resetEventCountForProperty(Integer propId) {
            mNumEvents.put(propId, 0);
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            int propId = value.getPropertyId();
            int areaId = value.getAreaId();

            mNumEvents.put(propId, mNumEvents.get(propId) + 1);

            String valueString = value.getValue().getClass().isArray()
                    ? Arrays.toString((Object[]) value.getValue())
                    : value.getValue().toString();

            mTvLogEvent.append(String.format("Event %1$s: time=%2$s propId=0x%3$s areaId=0x%4$s "
                            + "name=%5$s status=%6$s value=%7$s", mNumEvents.get(propId),
                    value.getTimestamp(), toHexString(propId), toHexString(areaId),
                    VehiclePropertyIds.toString(propId), value.getStatus(), valueString));
            if (mPropSampleRate.contains(propId)) {
                mTvLogEvent.append(
                        String.format(" selected sample rate=%1$s actual sample rate=%2$s\n",
                                mPropSampleRate.get(propId),
                                mNumEvents.get(propId) / (System.currentTimeMillis()
                                        - mStartTime.get(propId))));
            } else {
                mTvLogEvent.append("\n");
            }
            scrollToBottom();
        }

        @Override
        public void onErrorEvent(int propId, int areaId) {
            mTvLogEvent.append("Received error event propId=0x" + toHexString(propId)
                    + ", areaId=0x" + toHexString(areaId));
            scrollToBottom();
        }

        private void scrollToBottom() {
            mScrollView.post(new Runnable() {
                public void run() {
                    mScrollView.fullScroll(View.FOCUS_DOWN);
                    //mScrollView.smoothScrollTo(0, mTextStatus.getBottom());
                }
            });
        }

    }
}
