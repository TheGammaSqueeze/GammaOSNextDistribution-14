/*
 * Copyright 2023 The Android Open Source Project
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

#pragma once

/* Define common 16-bit protocol UUIDs
 */
#define UUID_PROTOCOL_RFCOMM 0x0003
#define UUID_PROTOCOL_OBEX 0x0008
#define UUID_PROTOCOL_BNEP 0x000F
#define UUID_PROTOCOL_HIDP 0x0011
#define UUID_PROTOCOL_AVCTP 0x0017
#define UUID_PROTOCOL_AVDTP 0x0019
#define UUID_PROTOCOL_L2CAP 0x0100
#define UUID_PROTOCOL_ATT 0x0007

/* Define common 16-bit service class UUIDs
 */
#define UUID_SERVCLASS_SERVICE_DISCOVERY_SERVER 0X1000
#define UUID_SERVCLASS_BROWSE_GROUP_DESCRIPTOR 0X1001
#define UUID_SERVCLASS_PUBLIC_BROWSE_GROUP 0X1002
#define UUID_SERVCLASS_SERIAL_PORT 0X1101
#define UUID_SERVCLASS_LAN_ACCESS_USING_PPP 0X1102
#define UUID_SERVCLASS_DIALUP_NETWORKING 0X1103
#define UUID_SERVCLASS_IRMC_SYNC 0X1104
#define UUID_SERVCLASS_OBEX_OBJECT_PUSH 0X1105
#define UUID_SERVCLASS_OBEX_FILE_TRANSFER 0X1106
#define UUID_SERVCLASS_IRMC_SYNC_COMMAND 0X1107
#define UUID_SERVCLASS_HEADSET 0X1108
#define UUID_SERVCLASS_CORDLESS_TELEPHONY 0X1109
#define UUID_SERVCLASS_AUDIO_SOURCE 0X110A
#define UUID_SERVCLASS_AUDIO_SINK 0X110B
/* Audio/Video Control profile */
#define UUID_SERVCLASS_AV_REM_CTRL_TARGET 0X110C
/* Advanced Audio Distribution profile */
#define UUID_SERVCLASS_ADV_AUDIO_DISTRIBUTION 0X110D
/* Audio/Video Control profile */
#define UUID_SERVCLASS_AV_REMOTE_CONTROL 0X110E
/* Audio/Video Control profile */
#define UUID_SERVCLASS_AV_REM_CTRL_CONTROL 0X110F
#define UUID_SERVCLASS_INTERCOM 0X1110
#define UUID_SERVCLASS_FAX 0X1111
#define UUID_SERVCLASS_HEADSET_AUDIO_GATEWAY 0X1112
#define UUID_SERVCLASS_WAP 0X1113
#define UUID_SERVCLASS_WAP_CLIENT 0X1114
#define UUID_SERVCLASS_PANU 0X1115                    /* PAN profile */
#define UUID_SERVCLASS_NAP 0X1116                     /* PAN profile */
#define UUID_SERVCLASS_GN 0X1117                      /* PAN profile */
#define UUID_SERVCLASS_DIRECT_PRINTING 0X1118         /* BPP profile */
#define UUID_SERVCLASS_REFERENCE_PRINTING 0X1119      /* BPP profile */
#define UUID_SERVCLASS_IMAGING 0X111A                 /* Imaging profile */
#define UUID_SERVCLASS_IMAGING_RESPONDER 0X111B       /* Imaging profile */
#define UUID_SERVCLASS_IMAGING_AUTO_ARCHIVE 0X111C    /* Imaging profile */
#define UUID_SERVCLASS_IMAGING_REF_OBJECTS 0X111D     /* Imaging profile */
#define UUID_SERVCLASS_HF_HANDSFREE 0X111E            /* Handsfree profile */
#define UUID_SERVCLASS_AG_HANDSFREE 0X111F            /* Handsfree profile */
#define UUID_SERVCLASS_DIR_PRT_REF_OBJ_SERVICE 0X1120 /* BPP profile */
#define UUID_SERVCLASS_REFLECTED_UI 0X1121            /* BPP profile */
#define UUID_SERVCLASS_BASIC_PRINTING 0X1122          /* BPP profile */
#define UUID_SERVCLASS_PRINTING_STATUS 0X1123         /* BPP profile */
#define UUID_SERVCLASS_HUMAN_INTERFACE 0X1124         /* HID profile */
#define UUID_SERVCLASS_CABLE_REPLACEMENT 0X1125       /* HCRP profile */
#define UUID_SERVCLASS_HCRP_PRINT 0X1126              /* HCRP profile */
#define UUID_SERVCLASS_HCRP_SCAN 0X1127               /* HCRP profile */
/* CAPI Message Transport Protocol*/
#define UUID_SERVCLASS_COMMON_ISDN_ACCESS 0X1128
/* Video Conferencing profile */
#define UUID_SERVCLASS_VIDEO_CONFERENCING_GW 0X1129
/* Unrestricted Digital Information profile */
#define UUID_SERVCLASS_UDI_MT 0X112A
/* Unrestricted Digital Information profile */
#define UUID_SERVCLASS_UDI_TA 0X112B
#define UUID_SERVCLASS_VCP 0X112C      /* Video Conferencing profile */
#define UUID_SERVCLASS_SAP 0X112D      /* SIM Access profile */
#define UUID_SERVCLASS_PBAP_PCE 0X112E /* Phonebook Access - PCE */
#define UUID_SERVCLASS_PBAP_PSE 0X112F /* Phonebook Access - PSE */
#define UUID_SERVCLASS_PHONE_ACCESS 0x1130
#define UUID_SERVCLASS_HEADSET_HS 0x1131 /* Headset - HS, from HSP v1.2 */
#define UUID_SERVCLASS_MPS_PROFILE \
  0x113A /* Multi-Profile Specification - Profile */
#define UUID_SERVCLASS_MPS_SC \
  0x113B /* Multi-Profile Specification - Service Class */
#define UUID_SERVCLASS_PNP_INFORMATION 0X1200 /* Device Identification */
#define UUID_SERVCLASS_GENERIC_NETWORKING 0X1201
#define UUID_SERVCLASS_GENERIC_FILETRANSFER 0X1202
#define UUID_SERVCLASS_GENERIC_AUDIO 0X1203
#define UUID_SERVCLASS_GENERIC_TELEPHONY 0X1204
#define UUID_SERVCLASS_UPNP_SERVICE 0X1205       /* UPNP_Service [ESDP] */
#define UUID_SERVCLASS_UPNP_IP_SERVICE 0X1206    /* UPNP_IP_Service [ESDP] */
#define UUID_SERVCLASS_ESDP_UPNP_IP_PAN 0X1300   /* UPNP_IP_PAN [ESDP] */
#define UUID_SERVCLASS_ESDP_UPNP_IP_LAP 0X1301   /* UPNP_IP_LAP [ESDP] */
#define UUID_SERVCLASS_ESDP_UPNP_IP_L2CAP 0X1302 /* UPNP_L2CAP [ESDP] */

/* Video Distribution Profile (VDP) */
#define UUID_SERVCLASS_VIDEO_SOURCE 0X1303
#define UUID_SERVCLASS_VIDEO_SINK 0X1304
#define UUID_SERVCLASS_VIDEO_DISTRIBUTION 0X1305

#define UUID_SERVCLASS_HDP_PROFILE 0X1400    /* Health Device profile (HDP) */
#define UUID_SERVCLASS_HDP_SOURCE 0X1401     /* Health Device profile (HDP) */
#define UUID_SERVCLASS_HDP_SINK 0X1402       /* Health Device profile (HDP) */
#define UUID_SERVCLASS_MAP_PROFILE 0X1134    /* MAP profile */
#define UUID_SERVCLASS_MESSAGE_ACCESS 0X1132 /* Message Access Service */
#define UUID_SERVCLASS_MESSAGE_NOTIFICATION \
  0X1133 /* Message Notification Service */

#define UUID_SERVCLASS_GAP_SERVER 0x1800
#define UUID_SERVCLASS_GATT_SERVER 0x1801
#define UUID_SERVCLASS_DEVICE_INFO 0x180A /* device info service */
#define UUID_SERVCLASS_LE_HID 0x1812      /*  HID over LE */
#define UUID_SERVCLASS_SCAN_PARAM 0x1813  /* Scan Parameter service */

#define UUID_SERVCLASS_VOLUME_CONTROL_SERVER 0x1844
#define UUID_SERVCLASS_GMCS_SERVER 0x1849 /* Generic Media Control Service */
#define UUID_SERVCLASS_GTBS_SERVER   \
  0x184c /* Generic Telephony Bearer \
            Service*/
#define UUID_SERVCLASS_TMAS_SERVER \
  0x1855 /* Telephone and Media Audio Service */
