/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.multchip;

import android.content.Context;
import android.util.Log;

import com.android.uwb.ChipGroupInfo;
import com.android.uwb.ChipInfo;
import com.android.uwb.Coordinates;
import com.android.uwb.UwbChipConfig;
import com.android.uwb.XmlParser;
import com.android.uwb.resources.R;

import com.google.common.base.Strings;
import com.google.uwb.support.multichip.ChipInfoParams;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.datatype.DatatypeConfigurationException;

/**
 * Manages UWB chip information (such as id and position) for a multi-chip device.
 */
public class UwbMultichipData {
    private static final String TAG = "UwbMultichipData";
    private final Context mContext;
    private String mDefaultChipId = "default";
    private List<ChipInfoParams> mChipInfoParamsList =
            List.of(ChipInfoParams.createBuilder().setChipId(mDefaultChipId).build());
    private List<String> mChipIds = List.of(mDefaultChipId);
    private OnInitializedListener mListener;

    public UwbMultichipData(Context context) {
        mContext = context;
    }

    /**
     * Reads in a configuration file to initialize chip info, if the device is a multi-chip system
     * a configuration file is defined and available.
     *
     * <p>If the device is single-chip, or if no configuration file is available, default values are
     * used.
     */
    public void initialize() {
        if (mContext.getResources().getBoolean(R.bool.config_isMultichip)) {
            String filePath =
                    mContext.getResources().getString(R.string.config_multichipConfigPath);
            if (Strings.isNullOrEmpty(filePath)) {
                Log.w(TAG, "Multichip is set to true, but configuration file is not defined.");
            } else {
                readConfigurationFile(filePath);
            }
        }
        if (mListener != null) {
            mListener.onInitialized();
        }
    }

    /**
     * Returns a list of UWB chip infos in a {@link ChipInfoParams} object.
     *
     * Callers can invoke methods on a specific UWB chip by passing its {@code chipId} to the
     * method, which can be determined by calling:
     * <pre>
     * {@code
     * List<ChipInfoParams> chipInfos = getChipInfos();
     * for (ChipInfoParams chipInfo : chipInfos) {
     *     String chipId = chipInfo.getChipId();
     * }
     * }
     * </pre>
     *
     * @return list of {@link ChipInfoParams} containing info about UWB chips for a multi-HAL
     * system, or a list of info for a single chip for a single HAL system.
     */
    public List<ChipInfoParams> getChipInfos() {
        return mChipInfoParamsList;
    }

    /**
     * Convenience method that returns a list of UWB chip ids.
     *
     * @return List of UWB chip ids
     */
    public List<String> getChipIds() {
        return mChipIds;
    }

    /**
     * Returns the default UWB chip identifier.
     *
     * If callers do not pass a specific {@code chipId} to UWB methods, then the method will be
     * invoked on the default chip, which is determined at system initialization from a
     * configuration file.
     *
     * @return default UWB chip identifier for a multi-HAL system, or the identifier of the only UWB
     * chip in a single HAL system.
     */
    public String getDefaultChipId() {
        return mDefaultChipId;
    }

    /**
     * Sets an {@link OnInitializedListener}.
     */
    public void setOnInitializedListener(OnInitializedListener listener) {
        mListener = listener;
    }

    private void readConfigurationFile(String filePath) {
        InputStream stream = null;
        try {
            stream = new BufferedInputStream(new FileInputStream(filePath));
            UwbChipConfig uwbChipConfig = XmlParser.read(stream);
            mDefaultChipId = uwbChipConfig.getDefaultChipId();
            Log.d(TAG, "Default chip id is " + mDefaultChipId);
            // Reset mChipInfoParamsList so that it can be populated with values from configuration
            // file.
            mChipInfoParamsList = new ArrayList<>();
            List<ChipGroupInfo> chipGroups = uwbChipConfig.getChipGroup();
            for (ChipGroupInfo chipGroup : chipGroups) {
                List<ChipInfo> chips = chipGroup.getChip();
                for (ChipInfo chip : chips) {
                    String chipId = chip.getId();
                    Coordinates position = chip.getPosition();
                    double x, y, z;
                    if (position == null) {
                        x = 0.0;
                        y = 0.0;
                        z = 0.0;
                    } else {
                        x = position.getX() == null ? 0.0 : position.getX().doubleValue();
                        y = position.getY() == null ? 0.0 : position.getY().doubleValue();
                        z = position.getZ() == null ? 0.0 : position.getZ().doubleValue();
                    }
                    Log.d(TAG,
                            "Chip with id " + chipId + " has position " + x + ", " + y + ", " + z);
                    mChipInfoParamsList
                            .add(ChipInfoParams.createBuilder()
                                    .setChipId(chipId)
                                    .setPositionX(x)
                                    .setPositionY(y)
                                    .setPositionZ(z).build());
                }
            }
            mChipIds = mChipInfoParamsList.stream().map(ChipInfoParams::getChipId).collect(
                    Collectors.toUnmodifiableList());
        } catch (XmlPullParserException | IOException | DatatypeConfigurationException e) {
            Log.e(TAG, "Cannot read file " + filePath, e);
        } finally {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException e) {
                Log.e(TAG, "Cannot close file " + filePath, e);
            }
        }
    }

    /**
     * Listener for initialization of {@link UwbMultichipData}.
     */
    public interface OnInitializedListener {
        /**
         * Invoked by {@link UwbMultichipData#initialize()}.
         */
        void onInitialized();
    }
}
