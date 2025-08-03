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

package com.android.server.uwb;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.test.TestLooper;
import android.provider.DeviceConfig;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.uwb.DeviceConfigFacade.PoseSourceType;
import com.android.uwb.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class DeviceConfigFacadeTest {
    @Mock private Resources mResources;
    @Mock private Context mContext;

    final ArgumentCaptor<DeviceConfig.OnPropertiesChangedListener>
            mOnPropertiesChangedListenerCaptor =
            ArgumentCaptor.forClass(DeviceConfig.OnPropertiesChangedListener.class);

    private DeviceConfigFacade mDeviceConfigFacade;
    private TestLooper mLooper = new TestLooper();
    private MockitoSession mSession;

    /**
     * Setup the mocks and an instance of DeviceConfig before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig.class, withSettings().lenient())
                .startMocking();
        // Have DeviceConfig return the default value passed in.
        when(DeviceConfig.getBoolean(anyString(), anyString(), anyBoolean()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public boolean answer(String namespace, String field, boolean def) {
                        return def;
                    }
                });
        when(DeviceConfig.getInt(anyString(), anyString(), anyInt()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public int answer(String namespace, String field, int def) {
                        return def;
                    }
                });
        when(DeviceConfig.getLong(anyString(), anyString(), anyLong()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public long answer(String namespace, String field, long def) {
                        return def;
                    }
                });
        when(DeviceConfig.getString(anyString(), anyString(), anyString()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public String answer(String namespace, String field, String def) {
                        return def;
                    }
                });

        when(mResources.getBoolean(R.bool.enable_filters)).thenReturn(true);
        when(mResources.getBoolean(R.bool.enable_primer_est_elevation)).thenReturn(true);
        when(mResources.getBoolean(R.bool.enable_primer_aoa)).thenReturn(true);
        when(mResources.getInteger(R.integer.filter_distance_inliers_percent))
                .thenReturn(1);
        when(mResources.getInteger(R.integer.filter_distance_window))
                .thenReturn(2);
        when(mResources.getInteger(R.integer.filter_angle_inliers_percent))
                .thenReturn(3);
        when(mResources.getInteger(R.integer.filter_angle_window))
                .thenReturn(4);
        when(mResources.getInteger(R.integer.primer_fov_degrees))
                .thenReturn(5);
        when(mResources.getString(R.string.pose_source_type))
                .thenReturn("ROTATION_VECTOR");
        when(mResources.getInteger(R.integer.prediction_timeout_seconds))
                .thenReturn(6);
        when(mResources.getBoolean(R.bool.enable_azimuth_mirroring)).thenReturn(true);
        when(mResources.getBoolean(R.bool.predict_rear_azimuths)).thenReturn(true);
        when(mResources.getInteger(R.integer.mirror_detection_window))
                .thenReturn(7);
        when(mResources.getInteger(R.integer.front_mirror_dps))
                .thenReturn(8);
        when(mResources.getInteger(R.integer.back_mirror_dps))
                .thenReturn(9);
        when(mResources.getInteger(R.integer.mirror_score_std_degrees))
                .thenReturn(10);
        when(mResources.getInteger(R.integer.back_noise_influence_percent))
                .thenReturn(11);

        // Setup the default values for the Advertising profile and Rx data packet parameters.
        when(mResources.getInteger(R.integer.advertise_aoa_criteria_angle))
                .thenReturn(5);
        when(mResources.getInteger(R.integer.advertise_time_threshold_millis))
                .thenReturn(2000);
        when(mResources.getInteger(R.integer.advertise_array_size_to_check))
                .thenReturn(12);
        when(mResources.getInteger(R.integer.advertise_array_start_index_to_cal_variance))
                .thenReturn(3);
        when(mResources.getInteger(R.integer.advertise_array_end_index_to_cal_variance))
                .thenReturn(7);
        when(mResources.getInteger(R.integer.advertise_trusted_variance_value))
                .thenReturn(12);
        when(mResources.getInteger(R.integer.rx_data_max_packets_to_store))
                .thenReturn(10);
        when(mResources.getBoolean(R.bool.background_ranging_enabled))
                .thenReturn(false);
        when(mResources.getBoolean(R.bool.ranging_error_streak_timer_enabled))
                .thenReturn(true);
        when(mResources.getBoolean(R.bool.ccc_ranging_stopped_params_send_enabled))
                .thenReturn(false);
        when(mResources.getBoolean(R.bool.ccc_absolute_uwb_initiation_time_enabled))
                .thenReturn(false);
        when(mResources.getBoolean(R.bool.location_use_for_country_code_enabled))
                .thenReturn(true);
        when(mResources.getBoolean(R.bool.uwb_disabled_until_first_toggle))
                .thenReturn(false);
        when(mResources.getBoolean(R.bool.persistent_cache_use_for_country_code_enabled))
                .thenReturn(false);
        when(mResources.getBoolean(R.bool.hw_idle_turn_off_enabled))
                .thenReturn(false);

        when(mContext.getResources()).thenReturn(mResources);

        mDeviceConfigFacade = new DeviceConfigFacade(new Handler(mLooper.getLooper()),
                mContext);
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(), any(),
                mOnPropertiesChangedListenerCaptor.capture()));
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        mSession.finishMocking();
    }

    /**
     * Verifies that default values are set correctly
     */
    @Test
    public void testDefaultValue() throws Exception {
        assertEquals(DeviceConfigFacade.DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS,
                mDeviceConfigFacade.getRangingResultLogIntervalMs());
        assertEquals(false, mDeviceConfigFacade.isDeviceErrorBugreportEnabled());
        assertEquals(false, mDeviceConfigFacade.isSessionInitErrorBugreportEnabled());
        assertEquals(DeviceConfigFacade.DEFAULT_BUG_REPORT_MIN_INTERVAL_MS,
                mDeviceConfigFacade.getBugReportMinIntervalMs());

        assertEquals(true, mDeviceConfigFacade.isEnableFilters());
        assertEquals(true, mDeviceConfigFacade.isEnablePrimerEstElevation());
        assertEquals(true, mDeviceConfigFacade.isEnablePrimerAoA());
        assertEquals(true, mDeviceConfigFacade.isEnableBackAzimuth());
        assertEquals(true, mDeviceConfigFacade.isEnableBackAzimuthMasking());

        assertEquals(1, mDeviceConfigFacade.getFilterDistanceInliersPercent());
        assertEquals(2, mDeviceConfigFacade.getFilterDistanceWindow());
        assertEquals(3, mDeviceConfigFacade.getFilterAngleInliersPercent());
        assertEquals(4, mDeviceConfigFacade.getFilterAngleWindow());
        assertEquals(5, mDeviceConfigFacade.getPrimerFovDegree());
        assertEquals(PoseSourceType.ROTATION_VECTOR, mDeviceConfigFacade.getPoseSourceType());
        assertEquals(6, mDeviceConfigFacade.getPredictionTimeoutSeconds());
        assertEquals(7, mDeviceConfigFacade.getBackAzimuthWindow());
        assertEquals(
                Math.toRadians(8),
                mDeviceConfigFacade.getFrontAzimuthRadiansPerSecond(),
                0.001);
        assertEquals(
                Math.toRadians(9),
                mDeviceConfigFacade.getBackAzimuthRadiansPerSecond(),
                0.001);
        assertEquals(
                Math.toRadians(10),
                mDeviceConfigFacade.getMirrorScoreStdRadians(),
                0.001);
        assertEquals(
                11 / 100F,
                mDeviceConfigFacade.getBackNoiseInfluenceCoeff(),
                0.001);

        // true because FOV is 5: within limits.
        assertEquals(true, mDeviceConfigFacade.isEnablePrimerFov());

        // Check the default values for the Advertising profile and Rx packet parameters.
        assertEquals(5, mDeviceConfigFacade.getAdvertiseAoaCriteriaAngle());
        assertEquals(2000, mDeviceConfigFacade.getAdvertiseTimeThresholdMillis());
        assertEquals(12, mDeviceConfigFacade.getAdvertiseArraySizeToCheck());
        assertEquals(3, mDeviceConfigFacade.getAdvertiseArrayStartIndexToCalVariance());
        assertEquals(7, mDeviceConfigFacade.getAdvertiseArrayEndIndexToCalVariance());
        assertEquals(12, mDeviceConfigFacade.getAdvertiseTrustedVarianceValue());
        assertEquals(10, mDeviceConfigFacade.getRxDataMaxPacketsToStore());
        assertEquals(false, mDeviceConfigFacade.isBackgroundRangingEnabled());
        assertEquals(true, mDeviceConfigFacade.isRangingErrorStreakTimerEnabled());
        assertEquals(false, mDeviceConfigFacade.isCccRangingStoppedParamsSendEnabled());
        assertEquals(false, mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled());
        assertEquals(true, mDeviceConfigFacade.isLocationUseForCountryCodeEnabled());
        assertEquals(false, mDeviceConfigFacade.isUwbDisabledUntilFirstToggle());
        assertEquals(false, mDeviceConfigFacade.isPersistentCacheUseForCountryCodeEnabled());
        assertEquals(false, mDeviceConfigFacade.isHwIdleTurnOffEnabled());
    }

    /**
     * Verifies that all fields are updated properly.
     */
    @Test
    public void testFieldUpdates() throws Exception {
        // These are interwoven (change then check, repeated) to make sure that copypasta
        //  errors didn't cause two values to get flipped.

        // Simulate updating the fields, make sure the corresponding call is updated.
        when(DeviceConfig.getInt(anyString(), eq("ranging_result_log_interval_ms"),
                anyInt())).thenReturn(4000);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(4000, mDeviceConfigFacade.getRangingResultLogIntervalMs());

        when(DeviceConfig.getBoolean(anyString(), eq("device_error_bugreport_enabled"),
                anyBoolean())).thenReturn(true);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(true, mDeviceConfigFacade.isDeviceErrorBugreportEnabled());

        when(DeviceConfig.getBoolean(anyString(), eq("session_init_error_bugreport_enabled"),
                anyBoolean())).thenReturn(true);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(true, mDeviceConfigFacade.isSessionInitErrorBugreportEnabled());

        when(DeviceConfig.getInt(anyString(), eq("bug_report_min_interval_ms"),
                anyInt())).thenReturn(10 * 3600_000);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(10 * 3600_000, mDeviceConfigFacade.getBugReportMinIntervalMs());

        when(DeviceConfig.getBoolean(anyString(), eq("enable_filters"),
                anyBoolean())).thenReturn(false);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(false, mDeviceConfigFacade.isEnableFilters());

        when(DeviceConfig.getBoolean(anyString(), eq("enable_primer_est_elevation"),
                anyBoolean())).thenReturn(false);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(false, mDeviceConfigFacade.isEnablePrimerEstElevation());

        when(DeviceConfig.getBoolean(anyString(), eq("enable_primer_aoa"),
                anyBoolean())).thenReturn(false);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(false, mDeviceConfigFacade.isEnablePrimerAoA());

        when(DeviceConfig.getBoolean(anyString(), eq("enable_azimuth_mirroring"),
                anyBoolean())).thenReturn(false);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(false, mDeviceConfigFacade.isEnableBackAzimuth());

        when(DeviceConfig.getBoolean(anyString(), eq("predict_rear_azimuths"),
                anyBoolean())).thenReturn(false);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(false, mDeviceConfigFacade.isEnableBackAzimuthMasking());

        when(DeviceConfig.getInt(anyString(), eq("filter_distance_inliers_percent"),
                anyInt())).thenReturn(6);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(6, mDeviceConfigFacade.getFilterDistanceInliersPercent());

        when(DeviceConfig.getInt(anyString(), eq("filter_distance_window"),
                anyInt())).thenReturn(7);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(7, mDeviceConfigFacade.getFilterDistanceWindow());

        when(DeviceConfig.getInt(anyString(), eq("filter_angle_inliers_percent"),
                anyInt())).thenReturn(8);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(8, mDeviceConfigFacade.getFilterAngleInliersPercent());

        when(DeviceConfig.getInt(anyString(), eq("filter_angle_window"),
                anyInt())).thenReturn(9);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(9, mDeviceConfigFacade.getFilterAngleWindow());

        when(DeviceConfig.getInt(anyString(), eq("primer_fov_degrees"),
                anyInt())).thenReturn(0);
        when(DeviceConfig.getString(anyString(), eq("pose_source_type"),
                anyString())).thenReturn("NONE");
        when(DeviceConfig.getInt(anyString(), eq("prediction_timeout_seconds"),
                anyInt())).thenReturn(5);

        when(DeviceConfig.getInt(anyString(), eq("advertise_aoa_criteria_angle"), anyInt()))
                .thenReturn(20);
        when(DeviceConfig.getInt(anyString(), eq("advertise_time_threshold_millis"), anyInt()))
                .thenReturn(3000);
        when(DeviceConfig.getInt(anyString(), eq("advertise_array_size_to_check"), anyInt()))
                .thenReturn(15);
        when(DeviceConfig.getInt(anyString(), eq("advertise_array_start_index_to_cal_variance"),
                anyInt())).thenReturn(3);
        when(DeviceConfig.getInt(anyString(), eq("advertise_array_end_index_to_cal_variance"),
                anyInt())).thenReturn(7);
        when(DeviceConfig.getInt(anyString(), eq("advertise_trusted_variance_value"), anyInt()))
                .thenReturn(12);
        when(DeviceConfig.getInt(anyString(), eq("rx_data_max_packets_to_store"),
                anyInt())).thenReturn(20);
        when(DeviceConfig.getBoolean(anyString(), eq("background_ranging_enabled"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getBoolean(anyString(), eq("ranging_error_streak_timer_enabled"),
                anyBoolean())).thenReturn(false);
        when(DeviceConfig.getBoolean(anyString(), eq("ccc_ranging_stopped_params_send_enabled"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getBoolean(anyString(), eq("ccc_absolute_uwb_initiation_time_enabled"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getBoolean(anyString(), eq("location_use_for_country_code_enabled"),
                anyBoolean())).thenReturn(false);
        when(DeviceConfig.getBoolean(anyString(), eq("uwb_disabled_until_first_toggle"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getBoolean(anyString(),
                eq("persistent_cache_use_for_country_code_enabled"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getBoolean(anyString(), eq("hw_idle_turn_off_enabled"),
                anyBoolean())).thenReturn(true);

        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(0, mDeviceConfigFacade.getPrimerFovDegree());
        // false because FOV is 0.
        assertEquals(false, mDeviceConfigFacade.isEnablePrimerFov());

        assertEquals(20, mDeviceConfigFacade.getAdvertiseAoaCriteriaAngle());
        assertEquals(3000, mDeviceConfigFacade.getAdvertiseTimeThresholdMillis());
        assertEquals(15, mDeviceConfigFacade.getAdvertiseArraySizeToCheck());
        assertEquals(3, mDeviceConfigFacade.getAdvertiseArrayStartIndexToCalVariance());
        assertEquals(7 , mDeviceConfigFacade.getAdvertiseArrayEndIndexToCalVariance());
        assertEquals(12, mDeviceConfigFacade.getAdvertiseTrustedVarianceValue());
        assertEquals(20, mDeviceConfigFacade.getRxDataMaxPacketsToStore());
        assertEquals(true, mDeviceConfigFacade.isBackgroundRangingEnabled());
        assertEquals(false, mDeviceConfigFacade.isRangingErrorStreakTimerEnabled());
        assertEquals(true, mDeviceConfigFacade.isCccRangingStoppedParamsSendEnabled());
        assertEquals(true, mDeviceConfigFacade.isCccAbsoluteUwbInitiationTimeEnabled());
        assertEquals(false, mDeviceConfigFacade.isLocationUseForCountryCodeEnabled());
        assertEquals(true, mDeviceConfigFacade.isUwbDisabledUntilFirstToggle());
        assertEquals(true, mDeviceConfigFacade.isPersistentCacheUseForCountryCodeEnabled());
        assertEquals(true, mDeviceConfigFacade.isHwIdleTurnOffEnabled());
        when(DeviceConfig.getString(anyString(), eq("pose_source_type"),
                anyString())).thenReturn("NONE");
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(PoseSourceType.NONE, mDeviceConfigFacade.getPoseSourceType());

        when(DeviceConfig.getInt(anyString(), eq("prediction_timeout_seconds"),
                anyInt())).thenReturn(5);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(5, mDeviceConfigFacade.getPredictionTimeoutSeconds());

        when(DeviceConfig.getInt(anyString(), eq("mirror_detection_window"),
                anyInt())).thenReturn(11);
        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(11, mDeviceConfigFacade.getBackAzimuthWindow());

        when(DeviceConfig.getInt(anyString(), eq("front_mirror_dps"),
                anyInt())).thenReturn(12);
        when(DeviceConfig.getInt(anyString(), eq("back_mirror_dps"),
                anyInt())).thenReturn(13);
        when(DeviceConfig.getInt(anyString(), eq("mirror_score_std_degrees"),
                anyInt())).thenReturn(14);
        when(DeviceConfig.getInt(anyString(), eq("back_noise_influence_percent"),
                anyInt())).thenReturn(15);

        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);
        assertEquals(
                Math.toRadians(12),
                mDeviceConfigFacade.getFrontAzimuthRadiansPerSecond(),
                0.001);
        assertEquals(
                Math.toRadians(13),
                mDeviceConfigFacade.getBackAzimuthRadiansPerSecond(),
                0.001);
        assertEquals(
                Math.toRadians(14),
                mDeviceConfigFacade.getMirrorScoreStdRadians(),
                0.001);
        assertEquals(
                15 / 100F,
                mDeviceConfigFacade.getBackNoiseInfluenceCoeff(),
                0.001);
    }
}
