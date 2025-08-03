/* Copyright (c) 2016, 2020-2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.ims;

/**
 * The class contains definitions for Qti specific carrier configs.
 */
/**
 * @hide
 */
public class QtiCarrierConfigs {

    /**
     * Private constructor. This class should not be instantiated.
     */
    private QtiCarrierConfigs() {
    }

    /* Flag specifying whether video calls are supported if device is in low battery or not */
    public static final String ALLOW_VIDEO_CALL_IN_LOW_BATTERY = "allow_video_call_in_low_battery";

    /**
     * Flag indicating whether preview video needs to be hidden during
     * video conference call.
     */
    public static final String HIDE_PREVIEW_IN_VT_CONFERENCE =
            "config_hide_preview_in_vt_confcall";

    /* Flag determining whether UI extensions for video calls should be used or not */
    public static final String USE_VIDEO_UI_EXTENSIONS = "video_call_use_ext";

    /* Flag determining whether customized video ui support is required or not */
    public static final String USE_CUSTOM_VIDEO_UI = "use_custom_video_ui";

    /* Flag specifying whether IMS to CS retry should be available for carrier
       false - hard disabled.
       true - then depends on user preference */
    public static final String CONFIG_CS_RETRY = "config_carrier_cs_retry_available";

    /* Controls modify call capabilities
       FALSE - default capabilities will be retained
       TRUE - remove modify call capabilities which will hide modify call button*/
    public static final String REMOVE_MODIFY_CALL_CAPABILITY = "remove_modify_call_capability";

    /* Config to show/hide Video quality toast */
    public static final String SHOW_VIDEO_QUALITY_TOAST = "show_video_quality_toast";

    /* Config that controls video quality UI options
       true - enabled, false - disabled */
    public static final String SHOW_VIDEO_QUALITY_UI = "show_video_quality_ui";

    /* Config to show/hide call session event toast like player start/stop */
    public static final String SHOW_CALL_SESSION_EVENT_TOAST = "show_call_session_event_toast";
    /* Config to show/hide data usage toast */
    public static final String SHOW_DATA_USAGE_TOAST = "show_data_usage_toast";
    /* Config that controls whether to transmit static image instead of
       preview video stream
       true - enabled, false - disabled */
    public static final String TRANSMIT_STATIC_IMAGE = "transmit_static_image";
    /* Config that controls whether to show static image related UI elements or not
       eg. static image option under IMS Settings, static image in preview window,
       hide me button in active call screen etc.
       true - enabled, false - disabled */
    public static final String SHOW_STATIC_IMAGE_UI = "show_static_image_ui";

    /* Config to control the voWiFi call quality indication. */
    public static final String VOWIFI_CALL_QUALITY = "vowifi_call_quality";

    /* Config to determine if Carrier supports RTT Visibility Setting
     * true - if supported else false
     */
    public static final String KEY_SHOW_RTT_VISIBILITY_SETTING =
            "show_rtt_visibility_setting_bool";

    /* Config to determine if Carrier supports Cancel Modify Call
     * true - if cancel modify call is supported else false
     */
    public static final String KEY_CARRIER_CANCEL_MODIFY_CALL_SUPPORTED =
            "support_cancel_modify_call";

    /* Config to determine if carrier supports accepting MT video call as one way
     * true if user can accept MT video call as one way else false
     */
    public static final String ALLOW_ONE_WAY_ACCEPT_FOR_VIDEO_CALL =
            "allow_one_way_accept_video_call";

    /* Config to determine if Carrier supports call composer
     * true - if call composer is supported else false
     */
    public static final String KEY_CARRIER_CALL_COMPOSER_SUPPORTED =
            "carrier_call_composer_supported_bool";

    /* Config to determine if Carrier supports b2c enriched calling
     * true - if b2c enriched calling is supported else false
     */
    public static final String KEY_CARRIER_B2C_ENRICHED_CALLING_SUPPORTED =
            "carrier_b2c_enriched_calling_supported_bool";

    /* Config to determine if Carrier supports video CRS
     * true - if video CRS is supported else false
     */
    public static final String KEY_CARRIER_VIDEO_CRS_SUPPORTED =
            "carrier_video_crs_supported_bool";

    /* Config to determine if Carrier supports video CRBT
     * true - if video CRBT is supported else false
     */
    public static final String KEY_CARRIER_VIDEO_CRBT_SUPPORTED =
            "config_enable_video_crbt";

    /* Config to determine if Carrier supports showing call progress notiication during alerting.
     * true - if call progress notification is supported else false
     */
    public static final String KEY_CARRIER_CALL_PROGRESS_NOTIFICATION_SUPPORTED =
            "carrier_call_progress_notification";

    /* List of numbers that should be treated as customer service numbers.
     * Number list are whitelisted in carrier config file, more numbers could
     * be added, below is format.
     * <string-array name="carrier_support_video_service_numbers" num="2">
     *     <item value="0532100860086"/>
     *     <item value="0201008616"/>
     * </string-array>
     */
    public static final String KEY_CARRIER_VIDEO_CUSTOMER_SERVICE_NUMBERS =
        "carrier_support_video_service_numbers";

    /* Config to determine if Carrier supports set media config (screen size and max avc and
     * hevc resolution) to modem.
     * true - if set media config is supported else false.
     */
    public static final String KEY_CARRIER_SEND_MEDIA_CONFIG_SUPPORTED =
        "carrier_support_send_media_configs_to_radio_layer_bool";

    /* Config to determine if Carrier should be considered as a private network
     * true - if it is considered as a private network  else false.
     */
    public static final String KEY_IS_PRIVATE_NETWORK =
        "is_private_network";

    /* Config to determine if Carrier supports ims data channel
     * true - if data channel is supported else false
     */
     public static final String KEY_CARRIER_DATA_CHANNEL_SUPPORTED =
         "carrier_data_channel_supported_bool";

    /* Config to determine if Carrier supports video online service
     * true - if video online service is supported else false
     */
    public static final String KEY_CARRIER_VIDEO_ONLINE_SERVICE_SUPPORTED =
        "carrier_video_online_service_supported_bool";
}
