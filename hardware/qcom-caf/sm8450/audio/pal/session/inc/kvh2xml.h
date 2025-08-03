/*
 * Copyright (c) 2019, The Linux Foundation. All rights reserved.
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
 */

typedef enum {
	PLATFORM_LA = 1, 	/**< @h2xmle_name {LA} */
	PLATFORM_LE = 2,	/**< @h2xmle_name {LE} */
}platforms;
/**
  	@h2xml_platforms{PLATFORM_LA,PLATFORM_LE}
*/

enum AllKeyIds{
	STREAMRX = 0xA1000000,       /**< @h2xmle_name{StreamRX} */
	DEVICERX = 0xA2000000,       /**< @h2xmle_name{DeviceRX} */
	DEVICETX = 0xA3000000,       /**< @h2xmle_name{DeviceTX} */
	VOLUME = 0xA4000000,         /**< @h2xmle_name{Volume} */
	SAMPLINGRATE = 0xA5000000,   /**< @h2xmle_name{SamplingRate} */
	BITWIDTH = 0xA6000000,       /**< @h2xmle_name{BitWidth} */
	PAUSE = 0xA7000000,          /**< @h2xmle_name{Pause} */
	MUTE = 0xA8000000,           /**< @h2xmle_name{Mute} */
	CHANNELS = 0xA9000000,       /**< @h2xmle_name{Channels} */
	ECNS = 0xAA000000,           /**< @h2xmle_name{ECNS} */
	INSTANCE = 0xAB000000,       /**< @h2xmle_name{Instance} */
	DEVICEPP_RX = 0xAC000000,    /**< @h2xmle_name{DevicePP_Rx} */
	DEVICEPP_TX = 0xAD000000,    /**< @h2xmle_name{DevicePP_Tx} */
	MEDIAFMTID = 0xae000000,
	STREAMTX = 0xb1000000,
	EQUALIZER_SWITCH = 0xb2000000,
	VIRTUALIZER_SWITCH = 0xb9000000,
	REVERB_SWITCH = 0xb8000000,
	PBE_SWITCH = 0xb6000000,
	BASS_BOOST_SWITCH = 0xb7000000,
	STREAM_SLOWTALK = 0xbb000000,
	STREAM_CONFIG = 0xbc000000,
	STREAM_MUXDEMUX = 0xbd000000,
	TAG_KEY_SLOT_MASK = 0xe0000000
};

/**
	@h2xmlk_key {STREAMRX}
	@h2xmlk_description {Type of Rx Stream}
*/
enum Key_StreamRX {
	PCM_LL_PLAYBACK = 0xA1000001, /**< @h2xmle_name {PCM_LL_Playback}*/
	PCM_RECORD = 0xA1000002,      /**< @h2xmle_name {PCM_Record}*/
	PCM_LOOPBACK = 0xA1000003,    /**< @h2xmle_name {PCM_Loopback}*/
	VOICE_UI = 0xA1000004,        /**< @h2xmle_name {Voice_UI}*/
	VOIP_RX_PLAYBACK = 0xA1000005,/**< @h2xmle_name {Voip_Rx}*/
	VOIP_TX_RECORD = 0xA1000006,   /**< @h2xmle_name {Voip_Tx}*/
	VOICE_UI_EC_REF_PATH = 0xA1000007,/**< @h2xmle_name {Voice_UI_EC_Ref_Path}*/
	VOIP_TX_EC_REF_PATH = 0xA1000008,   /**< @h2xmle_name {Voip_Tx_EC_Ref_Path}*/
	PCM_RECORD_EC_REF_PATH = 0xA1000009,/**< @h2xmle_name {PCM_Record_EC_Ref_Path}*/
	COMPRESSED_OFFLOAD_PLAYBACK = 0xa100000a,
	HAPTICS_PLAYBACK = 0xa1000015,
};


/**
	@h2xmlk_key {INSTANCE}
	@h2xmlk_description {Stream Instance Id}
*/
enum Key_Instance {
	INSTANCE_1 = 1, /**< @h2xmle_name {Instance_1}*/
	INSTANCE_2 = 2, /**< @h2xmle_name {Instance_2}*/
	INSTANCE_3 = 3, /**< @h2xmle_name {Instance_3}*/
};

/**
	@h2xmlk_key {DEVICERX}
	@h2xmlk_description {Rx Device}
*/
enum Key_DeviceRX {
	SPEAKER = 0xA2000001, /**< @h2xmle_name {Speaker}*/
	HANDSET = 0xa2000004,
	HAPTICS_DEVICE = 0xa2000009,
};
/**
	@h2xmlk_key {DEVICETX}
	@h2xmlk_description {Tx Device}
*/
enum Key_DeviceTX {
	HANDSETMIC = 0xA3000004, /**< @h2xmle_name {HandsetMic}*/
	HANDSETMIC_VA = 0xa3000006
};

/**
	@h2xmlk_key {DEVICEPP_RX}
	@h2xmlk_description {Rx Device Post/Pre Processing Chain}
*/
enum Key_DevicePP_RX {
	DEVICEPP_RX_DEFAULT = 0xAC000001, /**< @h2xmle_name {Audio_LL_Default_PP} @h2xmlk_description {Low Latency Default Playback}*/
	DEVICEPP_RX_AUDIO_MBDRC = 0xac000002,
};
/**
	@h2xmlk_key {DEVICEPP_TX}
	@h2xmlk_description {Tx Device Post/Pre Processing Chain}
*/
enum Key_DevicePP_TX {
	DEVICEPP_TX_FLUENCE_FFECNS        = 0xAD000001, /**< @h2xmle_name {Voice_Fluence_FFECNS} @h2xmlk_description {Used in Voice UI use-cases}*/
	DEVICEPP_TX_AUDIO_FLUENCE_SMECNS  = 0xAD000002, /**< @h2xmle_name {Audio_Fluence_SMECNS} @h2xmlk_description {Single Mic ECNS }*/
	DEVICEPP_TX_AUDIO_FLUENCE_ENDFIRE = 0xAD000003, /**< @h2xmle_name {Audio_Fluence_Endfire} @h2xmlk_description {EndFire_ECNS - Typically used for dual mic capture scenarios}*/
	DEVICEPP_TX_AUDIO_FLUENCE_PRO     = 0xAD000004, /**< @h2xmle_name {Audio_Fluence_Pro} @h2xmlk_description {Multi MIC scenarios ; at least 3 or more Micss}*/
};


enum Key_StreamConfig {
	STREAM_CFG_VUI_SVA = 0xbc000001,
};

/**
	@h2xmlk_key {VOLUME}
	@h2xmlk_description {Volume}
*/
enum Key_Volume {
	LEVEL_0 = 0, /**< @h2xmle_name {Level_0}*/
	LEVEL_1 = 1, /**< @h2xmle_name {Level_1}*/
	LEVEL_2 = 2, /**< @h2xmle_name {Level_2}*/
	LEVEL_3 = 3, /**< @h2xmle_name {Level_3}*/
	LEVEL_4 = 4, /**< @h2xmle_name {Level_4}*/
	LEVEL_5 = 5, /**< @h2xmle_name {Level_5}*/
	LEVEL_6 = 6, /**< @h2xmle_name {Level_6}*/
	LEVEL_7 = 7, /**< @h2xmle_name {Level_7}*/
	LEVEL_8 = 8, /**< @h2xmle_name {Level_8}*/
	LEVEL_9 = 9, /**< @h2xmle_name {Level_9}*/
	LEVEL_10 = 10, /**< @h2xmle_name {Level_10}*/
	LEVEL_11 = 11, /**< @h2xmle_name {Level_11}*/
	LEVEL_12 = 12, /**< @h2xmle_name {Level_12}*/
	LEVEL_13 = 13, /**< @h2xmle_name {Level_13}*/
	LEVEL_14 = 14, /**< @h2xmle_name {Level_14}*/
	LEVEL_15 = 15, /**< @h2xmle_name {Level_15}*/
};
/**
	@h2xmlk_key {SAMPLINGRATE}
	@h2xmlk_sampleRate
	@h2xmlk_description {Sampling Rate}
*/
enum Key_SamplingRate {
	SAMPLINGRATE_8K = 8000,   /**< @h2xmle_sampleRate{8000} @h2xmle_name {SR_8K}*/
	SAMPLINGRATE_16K = 16000, /**< @h2xmle_sampleRate{16000} @h2xmle_name {SR_16K}*/
	SAMPLINGRATE_32K = 32000, /**< @h2xmle_sampleRate{32000} @h2xmle_name {SR_32K}*/
	SAMPLINGRATE_44K = 44100, /**< @h2xmle_sampleRate{44100} @h2xmle_name {SR_44.1K}*/
	SAMPLINGRATE_48K = 48000, /**< @h2xmle_sampleRate{48000} @h2xmle_name {SR_48K}*/
	SAMPLINGRATE_96K = 96000, /**< @h2xmle_sampleRate{96000} @h2xmle_name {SR_96K}*/
	SAMPLINGRATE_192K = 192000, /**< @h2xmle_sampleRate{192000} @h2xmle_name {SR_192K}*/
	SAMPLINGRATE_384K = 384000, /**< @h2xmle_sampleRate{384000} @h2xmle_name {SR_384K}*/
};
/**
	@h2xmlk_key {BITWIDTH}
	@h2xmlk_description {Bit Width}
*/
enum Key_BitWidth {
	BITWIDTH_16 = 16, /**< @h2xmle_name {BW_16}*/
	BITWIDTH_24 = 24, /**< @h2xmle_name {BW_24}*/
	BITWIDTH_32 = 32, /**< @h2xmle_name {BW_32}*/
};
/**
	@h2xmlk_key {PAUSE}
	@h2xmlk_description {Pause}
*/
enum Key_Pause {
	OFF = 0, /**< @h2xmle_name {Off}*/
	ON = 1, /**< @h2xmle_name {On}*/
};
/**
	@h2xmlk_key {MUTE}
	@h2xmlk_description {Mute}
*/
enum Key_Mute {
	MUTE_OFF = 0, /**< @h2xmle_name {Off}*/
	MUTE_ON = 1, /**< @h2xmle_name {On}*/
};
/**
	@h2xmlk_key {CHANNELS}
	@h2xmlk_description {Channels}
*/
enum Key_Channels {
	CHANNELS_1 = 1, /**< @h2xmle_name {CHS_1}*/
	CHANNELS_2 = 2, /**< @h2xmle_name {CHS_2}*/
        CHANNELS_3 = 3, /**< @h2xmle_name {CHS_3}*/
	CHANNELS_4 = 4, /**< @h2xmle_name {CHS_4}*/
        CHANNELS_5 = 5, /**< @h2xmle_name {CHS_5}*/
        CHANNELS_5_1 = 6, /**< @h2xmle_name {CHS_6}*/
	CHANNELS_7 = 7, /**< @h2xmle_name {CHS_7}*/
	CHANNELS_8 = 8, /**< @h2xmle_name {CHS_8}*/
};

/**
	@h2xmlk_key {ECNS}
	@h2xmlk_description {ECNS}
*/
enum Key_ECNS {
	ECNS_OFF = 0, /**< @h2xmle_name {ECNS_Off}*/
	ECNS_ON = 1, /**< @h2xmle_name {ECNS_On}*/
	EC_ON = 2,
	NS_ON = 3,
};

/**
	@h2xmlk_gkeys
	@h2xmlk_description {Graph Keys}
*/
enum Graph_Keys {
	gk_StreamRX = STREAMRX,
	gk_DeviceRX = DEVICERX,
	gk_DeviceTX = DEVICETX,
	gk_DevicePP_RX = DEVICEPP_RX,
	gk_DevicePP_TX = DEVICEPP_TX,
	gk_Instance = INSTANCE,
	gk_StreamTX = STREAMTX,
};
/**
	@h2xmlk_ckeys
	@h2xmlk_description {Calibration Keys}
*/
enum Cal_Keys {
	ck_volume = VOLUME,
};

#define DEVICE_HW_ENDPOINT_RX        0xC0000004
/**
        @h2xmlk_modTag {"device_hw_ep_rx",DEVICE_HW_ENDPOINT_RX}
	@h2xmlk_description {Hw EP Rx}
*/
enum HW_ENDPOINT_RX_Keys {
	tk1_hweprx = DEVICERX,
	tk2_hweprx = SAMPLINGRATE,
	tk3_hweprx = BITWIDTH,
	tk4_hweprx = CHANNELS,
};
#define DEVICE_HW_ENDPOINT_TX        0xC0000005
/**
        @h2xmlk_modTag {"device_hw_ep_tx",DEVICE_HW_ENDPOINT_TX}
	@h2xmlk_description {Hw EP Tx}
*/
enum HW_ENDPOINT_TX_Keys {
	tk1_hweptx = DEVICETX,
	tk2_hweptx = SAMPLINGRATE,
	tk3_hweptx = BITWIDTH,
	tk4_hweptx = CHANNELS,
};
#define TAG_PAUSE       0xC0000006
/**
	@h2xmlk_modTag {"stream_pause", TAG_PAUSE}
	@h2xmlk_description {Stream Pause}
*/
enum TAG_PAUSE_Keys {
	tk1_Pause = PAUSE,
};
#define TAG_MUTE        0xC0000007
/**
	@h2xmlk_modTag {"stream_mute", TAG_MUTE}
	@h2xmlk_description {Stream Mute}
*/
enum TAG_MUTE_Keys {
	tk1_Mute = MUTE,
};

#define TAG_ECNS  0xC000000A
/**
	@h2xmlk_modTag {"device_ecns", TAG_ECNS}
	@h2xmlk_description {Ecns On/Off}
*/
enum TAG_ECNS_Keys {
	tk1_Ecns = ECNS,
};

#define TAG_STREAM_VOLUME  0xC000000D
/**
	@h2xmlk_modTag {"stream_volume", TAG_STREAM_VOLUME}
	@h2xmlk_description {Stream Volume}
*/
enum TAG_STREAM_VOLUME_Keys {
	tk1_Volume = VOLUME,
};

#define TAG_DEVICE_PP_MFC  0xC0000011
/**
	@h2xmlk_modTag {"device_pp_mfc", TAG_DEVICE_PP_MFC}
	@h2xmlk_description {Device PP MFC}
*/
enum TAG_DEVICE_PP_MFC_Keys {
	tk1_SamplingRate = SAMPLINGRATE,
	tk2_BitWidth     = BITWIDTH,
	tk3_Channels     = CHANNELS,
};

#define TAG_STREAM_MFC  0xc000000b

// Same enum names as TAG_DEVICE_PP_MFC_Keys
/*enum TAG_STREAM_MFC_Keys {
	tk1_SamplingRate = SAMPLINGRATE,
	tk2_BitWidth     = BITWIDTH,
	tk3_Channels     = CHANNELS,
};*/


#define TAG_STREAM_PLACEHOLDER_DECODER  0xc0000012

enum TAG_STREAM_PLACEHOLDER_DECODER_Keys {
	tk1_MediaFmtID = MEDIAFMTID,
};

#define TAG_STREAM_EQUALIZER  0xc0000014

enum TAG_STREAM_EQUALIZER_Keys {
	tk1_Equalizer = EQUALIZER_SWITCH,
};

#define TAG_STREAM_VIRTUALIZER  0xc0000015

enum TAG_STREAM_VIRTUALIZER_Keys {
	tk1_Virtualizer_Switch = VIRTUALIZER_SWITCH,
};

#define TAG_STREAM_REVERB  0xc0000016

enum TAG_STREAM_REVERB_Keys {
	tk1_Reverb_Switch = REVERB_SWITCH,
};

#define TAG_STREAM_PBE  0xc0000017

enum TAG_STREAM_PBE_Keys {
	tk1_PBE_Switch = PBE_SWITCH,
};

#define TAG_STREAM_BASS_BOOST  0xc0000018

enum TAG_STREAM_BASS_BOOST_Keys {
	tk1_BASS_BOOST_Switch = BASS_BOOST_SWITCH,
};

#define PER_STREAM_PER_DEVICE_MFC  0xc0000019

// Same enum names as TAG_DEVICE_PP_MFC_Keys
/*enum TAG_PSPD_MFC_Keys {
	tk1_SamplingRate = SAMPLINGRATE,
	tk2_BitWidth     = BITWIDTH,
	tk3_Channels     = CHANNELS,
};*/

#define TAG_STREAM_SLOWTALK  0xc0000025

enum TAG_STREAM_SLOWTALK_Keys {
	tk1_Stream_SlowTalk = STREAM_SLOWTALK,
};

#define TAG_MODULE_CHANNELS  0xc0000026

enum TAG_MODULE_CHANNELS_Keys {
	tk1_Channels = CHANNELS,
};

#define TAG_STREAM_MUXDEMUX  0xc0000027

enum TAG_STREAM_MUXDEMUX_Keys {
	tk1_Stream_MuxDemux= STREAM_MUXDEMUX,
};

#define TAG_DEVICE_MUX  0xc0000040

enum TAG_DEVICE_MUX_Keys {
	tk1_SlotMask= TAG_KEY_SLOT_MASK,
};

/**
	@h2xmlk_modTagList
*/
enum TAGS_DEFINITIONS {
        SHMEM_ENDPOINT              = 0xC0000001, /**< @h2xmle_name {"sh_ep"} */
        STREAM_INPUT_MEDIA_FORMAT   = 0xC0000002, /**< @h2xmle_name {"stream_input_media_format" } */
        STREAM_OUTPUT_MEDIA_FORMAT  = 0xC0000003, /**< @h2xmle_name {"stream_output_media_format" } */
        DEVICE_SVA                  = 0xC0000008, /**< @h2xmle_name {"device_sva"} */
        DEVICE_ADAM                 = 0xC0000009, /**< @h2xmle_name {"device_adam"} */
        STREAM_MFC                  = 0xC000000B, /**< @h2xmle_name {"stream_mfc"} */
        DEVICE_MFC                  = 0xC000000C, /**< @h2xmle_name {"device_mfc"} */
        STREAM_PCM_DECODER          = 0xC000000E, /**< @h2xmle_name {"stream_pcm_decoder"} */
        STREAM_PCM_ENCODER          = 0xC000000F, /**< @h2xmle_name {"stream_pcm_encoder"} */
        STREAM_PCM_CONVERTER        = 0xC0000010, /**< @h2xmle_name {"stream_pcm_converter"} */
};
typedef enum TAGS_DEFINITIONS TAGS_DEFINITIONS;

