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
	STREAMRX = 0xA1000000,       /**< @h2xmle_name{Stream} */
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
	STREAMPP_RX = 0xaf000000,
	STREAMPP_TX = 0xb0000000,
	STREAMTX = 0xb1000000,
	EQUALIZER_SWITCH = 0xb2000000,
	VSID = 0xb3000000,
	BTPROFILE = 0xb4000000,
	BTFORMAT = 0xb5000000,
	PBE_SWITCH = 0xb6000000,
	BASS_BOOST_SWITCH = 0xb7000000,
	REVERB_SWITCH = 0xb8000000,
	VIRTUALIZER_SWITCH = 0xb9000000,
	SW_SIDETONE = 0xba000000,
	STREAM_SLOWTALK = 0xbb000000,
	STREAM_CONFIG = 0xbc000000,
	STREAM_MUXDEMUX = 0xbd000000,
	SPKRPROTDEVMAP = 0xbe000000,
	SPKRPROTVIMAP = 0xbf000000,
	RAS_SWITCH = 0xd0000000,
	VOIP_SAMPLE_RATE = 0xd100000,
	PROXYTXTYPE = 0xd1000000,
	GAIN = 0xd2000000,
	STREAM = 0xd3000000,
	STREAMCHANNELS = 0xd4000000,
	ICL = 0xd5000000,
	DEVICETX_EXT = 0xd7000000,
	DATALOGGING = 0xd8000000,
	DTMF_SWITCH = 0xde000000,
	DTMF_GEN_TONE = 0xdf000000,
	TAG_KEY_SLOT_MASK = 0xe0000000,
	DUTYCYCLE = 0xe1000000
};

/**
	@h2xmlk_key {STREAMRX}
	@h2xmlk_description {Type of Rx Stream}
*/
enum Key_StreamRX {
	PCM_DEEP_BUFFER = 0xa1000001,
	PCM_RX_LOOPBACK = 0xa1000003,
	VOIP_RX_PLAYBACK = 0xa1000005,
	COMPRESSED_OFFLOAD_PLAYBACK = 0xa100000a,
	HFP_RX_PLAYBACK = 0xa100000c,
	PCM_LL_PLAYBACK = 0xa100000e,
	PCM_OFFLOAD_PLAYBACK = 0xa100000f,
	VOICE_CALL_RX = 0xa1000010,
	PCM_ULL_PLAYBACK = 0xa1000011,
	PCM_PROXY_PLAYBACK = 0xa1000012,
	INCALL_MUSIC = 0xa1000013,
	GENERIC_PLAYBACK = 0xa1000014,
	HAPTICS_PLAYBACK = 0xa1000015,
	RAW_PLAYBACK = 0xa1000019,
};

/**
	@h2xmlk_key {STREAMTX}
	@h2xmlk_description {Type of Tx Stream}
*/
enum Key_StreamTX {
	PCM_RECORD = 0xb1000001,
	VOICE_UI = 0xb1000003,
	VOIP_TX_RECORD = 0xb1000004,
	HFP_RX_CAPTURE = 0xb1000005,
	VOICE_CALL_TX = 0xb1000007,
	RAW_RECORD = 0xb1000009,
	PCM_ULL_RECORD = 0xb100000a,
	PCM_PROXY_RECORD = 0xb100000b,
	INCALL_RECORD = 0xb100000c,
	ACD = 0xb100000d,
	SENSOR_PCM_DATA = 0xb100000e,
	VOICE_RECOGNITION_RECORD = 0xb1000011,
	COMPRESS_CAPTURE = 0xb1000012,
};

/**
	@h2xmlk_key {INSTANCE}
	@h2xmlk_description {Stream Instance Id}
*/
enum Key_Instance {
	INSTANCE_1 = 1, /**< @h2xmle_name {Instance_1}*/
	INSTANCE_2 = 2, /**< @h2xmle_name {Instance_2}*/
	INSTANCE_3 = 3, /**< @h2xmle_name {Instance_3}*/
	INSTANCE_4 = 4,
	INSTANCE_5 = 5,
	INSTANCE_6 = 6,
	INSTANCE_7 = 7,
	INSTANCE_8 = 8,
};

/**
	@h2xmlk_key {DEVICERX}
	@h2xmlk_description {Rx Device}
*/
enum Key_DeviceRX {
	SPEAKER = 0xa2000001,
	HEADPHONES = 0xa2000002,
	BT_RX = 0xa2000003,
	HANDSET = 0xa2000004,
	USB_RX = 0xa2000005,
	HDMI_RX = 0xa2000006,
	PROXY_RX = 0xa2000007,
	PROXY_RX_VOICE = 0xa2000008,
	HAPTICS_DEVICE = 0xa2000009,
	ULTRASOUND_RX = 0xa200000a,
	ULTRASOUND_RX_DEDICATED = 0xa200000b,
};
/**
	@h2xmlk_key {DEVICETX}
	@h2xmlk_description {Tx Device}
*/
enum Key_DeviceTX {
	SPEAKER_MIC = 0xa3000001,
	BT_TX = 0xa3000002,
	HEADPHONE_MIC = 0xa3000003,
	HANDSETMIC = 0xa3000004,
	USB_TX = 0xa3000005,
	HANDSETMIC_VA = 0xa3000006,
	HEADSETMIC_VA = 0xa3000007,
	PROXY_TX = 0xa3000008,
	VI_TX = 0xa3000009,
	FM_TX = 0xa300000a,
	ULTRASOUND_TX = 0xa300000b,
	ECHO_REF_TX = 0xa300000d,
};

/**
	@h2xmlk_key {DEVICEPP_RX}
	@h2xmlk_description {Rx Device Post/Pre Processing Chain}
*/
enum Key_DevicePP_RX {
	DEVICEPP_RX_DEFAULT = 0xac000001,
	DEVICEPP_RX_AUDIO_MBDRC = 0xac000002,
	DEVICEPP_RX_VOIP_MBDRC = 0xac000003,
	DEVICEPP_RX_HFPSINK = 0xac000004,
	DEVICEPP_RX_VOICE_DEFAULT = 0xac000005,
	DEVICEPP_RX_ULTRASOUND_GENERATOR = 0xac000006,
	DEVICEPP_RX_VOICE_RVE = 0xac000007,
	DEVICEPP_RX_VOICE_NN_NS = 0xac000009,
	DEVICEPP_RX_AUDIO_MSPP = 0xac00000b,
};

/**
	@h2xmlk_key {DEVICEPP_TX}
	@h2xmlk_description {Tx Device Post/Pre Processing Chain}
*/
enum Key_DevicePP_TX {
	DEVICEPP_TX_FLUENCE_FFECNS = 0xad000001,
	DEVICEPP_TX_AUDIO_FLUENCE_SMECNS = 0xad000002,
	DEVICEPP_TX_AUDIO_FLUENCE_ENDFIRE = 0xad000003,
	DEVICEPP_TX_AUDIO_FLUENCE_PRO = 0xad000004,
	DEVICEPP_TX_VOIP_FLUENCE_PRO = 0xad000005,
	DEVICEPP_TX_HFP_SINK_FLUENCE_SMECNS = 0xad000006,
	DEVICEPP_TX_VOIP_FLUENCE_SMECNS = 0xad000007,
	DEVICEPP_TX_VOICE_FLUENCE_SMECNS = 0xad000008,
	DEVICEPP_TX_VOICE_FLUENCE_ENDFIRE = 0xad000009,
	DEVICEPP_TX_VOICE_FLUENCE_PRO = 0xad00000a,
	DEVICEPP_TX_RAW_LPI = 0xad00000c,
	DEVICEPP_TX_VOIP_FLUENCE_ENDFIRE = 0xad00000d,
	DEVICEPP_TX_RAW_NLPI = 0xad00000e,
	DEVICEPP_TX_VOICE_FLUENCE_NN_SM = 0xad00000f,
	DEVICEPP_TX_VOIP_FLUENCE_NN_SM = 0xad000010,
	DEVICEPP_TX_FLUENCE_FFEC = 0xad000012,
	DEVICEPP_TX_VOICE_FLUENCE_ENDFIRE_RVE = 0xad000013,
	DEVICEPP_TX_VOICE_RECOGNITION = 0xad000017,
	DEVICEPP_TX_AAD = 0xad000019,
	DEVICEPP_TX_FLUENCE_FFNS_AAD = 0xad00001a,
	DEVICEPP_TX_RAW_LPI_AAD = 0xad00001b,
	DEVICEPP_TX_AUDIO_RECORD_ENQORE = 0xad000023,
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
	@h2xmlk_key {GAIN}
	@h2xmlk_description {Gain}
*/
enum Key_Gain {
	GAIN_0 = 0, /**< @h2xmle_name {Gain_0}*/
	GAIN_1 = 1, /**< @h2xmle_name {Gain_1}*/
	GAIN_2 = 2, /**< @h2xmle_name {Gain_2}*/
	GAIN_3 = 3, /**< @h2xmle_name {Gain_3}*/
	GAIN_4 = 4, /**< @h2xmle_name {Gain_4}*/
	GAIN_5 = 5, /**< @h2xmle_name {Gain_5}*/
	GAIN_6 = 6, /**< @h2xmle_name {Gain_6}*/
	GAIN_7 = 7, /**< @h2xmle_name {Gain_7}*/
	GAIN_8 = 8, /**< @h2xmle_name {Gain_8}*/
	GAIN_9 = 9, /**< @h2xmle_name {Gain_9}*/
	GAIN_10 = 10, /**< @h2xmle_name {Gain_10}*/
	GAIN_11 = 11, /**< @h2xmle_name {Gain_11}*/
	GAIN_12 = 12, /**< @h2xmle_name {Gain_12}*/
	GAIN_13 = 13, /**< @h2xmle_name {Gain_13}*/
	GAIN_14 = 14, /**< @h2xmle_name {Gain_14}*/
	GAIN_15 = 15, /**< @h2xmle_name {Gain_15}*/
};

/**
	@h2xmlk_key {SAMPLINGRATE}
	@h2xmlk_sampleRate
	@h2xmlk_description {Sampling Rate}
*/
enum Key_SamplingRate {
	SAMPLINGRATE_8K = 8000,   /**< @h2xmle_sampleRate{8000} @h2xmle_name {SR_8K}*/
	SAMPLINGRATE_16K = 16000, /**< @h2xmle_sampleRate{16000} @h2xmle_name {SR_16K}*/
	SAMPLINGRATE_22K = 22050, /**< @h2xmle_sampleRate{22050} @h2xmle_name {SR_22K}*/
	SAMPLINGRATE_24K = 24000, /**< @h2xmle_sampleRate{24000} @h2xmle_name {SR_24K}*/
	SAMPLINGRATE_32K = 32000, /**< @h2xmle_sampleRate{32000} @h2xmle_name {SR_32K}*/
	SAMPLINGRATE_44K = 44100, /**< @h2xmle_sampleRate{44100} @h2xmle_name {SR_44.1K}*/
	SAMPLINGRATE_48K = 48000, /**< @h2xmle_sampleRate{48000} @h2xmle_name {SR_48K}*/
	SAMPLINGRATE_96K = 96000, /**< @h2xmle_sampleRate{96000} @h2xmle_name {SR_96K}*/
	SAMPLINGRATE_192K = 192000, /**< @h2xmle_sampleRate{192000} @h2xmle_name {SR_192K}*/
	SAMPLINGRATE_352K = 352800, /**< @h2xmle_sampleRate{352800} @h2xmle_name {SR_352K}*/
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
	@h2xmlk_key {DTMF}
	@h2xmlk_description {DMTF_SWITCH}
*/
enum Key_Dtmf {
	DISABLE = 0,
	ENABLE = 1,
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
	gk_StreamPP_RX = STREAMPP_RX,
	gk_StreamPP_TX = STREAMPP_TX,
	gk_StreamTX = STREAMTX,
	gk_VSID = VSID,
	gk_BtProfile = BTPROFILE,
	gk_BtFormat = BTFORMAT,
	gk_SW_Sidetone = SW_SIDETONE,
	gk_Stream_Config = STREAM_CONFIG,
	gk_ProxyTxType = PROXYTXTYPE,
	gk_Stream = STREAM,
	gk_DeviceTX_EXT = DEVICETX_EXT,
};
/**
	@h2xmlk_ckeys
	@h2xmlk_description {Calibration Keys}
*/
enum Cal_Keys {
	ck_volume = VOLUME,
	ck_channels = CHANNELS,
	ck_sp_dev_map = SPKRPROTDEVMAP,
	ck_ras_switch = RAS_SWITCH,
	ck_sp_vi_map = SPKRPROTVIMAP,
	ck_gain = GAIN,
	ck_stream_channels = STREAMCHANNELS,
	ck_voip_sample_rate = VOIP_SAMPLE_RATE,
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

#define TAG_MODULE_MSPP  0xc0000043

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
        STREAM_SPR                  = 0xc0000013,
        BT_ENCODER                  = 0xc0000020,
        COP_PACKETIZER_V0           = 0xc0000021,
        RATE_ADAPTER_MODULE         = 0xc0000022,
        BT_PCM_CONVERTER            = 0xc0000023,
        BT_DECODER                  = 0xc0000024,
        MODULE_VI                   = 0xc0000028,
        MODULE_SP                   = 0xc0000029,
        MODULE_GAPLESS              = 0xc000002a,
        WR_SHMEM_ENDPOINT           = 0xc000002c,
        RD_SHMEM_ENDPOINT           = 0xc000002e,
        COP_PACKETIZER_V2           = 0xc000002f,
        COP_DEPACKETIZER_V2         = 0xc0000030,
        CONTEXT_DETECTION_ENGINE    = 0xc0000031,
        ULTRASOUND_DETECTION_MODULE = 0xc0000032,
        DEVICE_POP_SUPPRESSOR       = 0xc000003a,
};
typedef enum TAGS_DEFINITIONS TAGS_DEFINITIONS;
