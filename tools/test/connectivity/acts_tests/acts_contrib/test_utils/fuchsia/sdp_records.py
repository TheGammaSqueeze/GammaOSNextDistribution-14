#!/usr/bin/env python3
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

from acts_contrib.test_utils.bt.bt_constants import bt_attribute_values
from acts_contrib.test_utils.bt.bt_constants import sig_uuid_constants

BASE_UUID = sig_uuid_constants['BASE_UUID']

# A list of pre-defined SDP definitions
sdp_pts_record_list = [
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['AudioSink'])],
        'protocol_descriptors': [
            {
                'protocol': int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data': int(sig_uuid_constants['AVDTP'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVDTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['AdvancedAudioDistribution'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors': [{
            'protocol':
            int(sig_uuid_constants['L2CAP'], 16),
            'params': [
                {
                    'data': int(sig_uuid_constants['AVDTP'], 16),
                },
                {
                    'data': int(sig_uuid_constants['AVCTP'], 16),
                },
                {
                    'data': int(sig_uuid_constants['GenericAudio'], 16),
                },
            ]
        }],
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_SERVICE_AVAILABILITY'],
            'element': {
                'data': 0xff  # Indicate all available
            }
        }]
    },
    {
        'service_class_uuids': [
            BASE_UUID.format(sig_uuid_constants['A/V_RemoteControlTarget']),
            BASE_UUID.format(sig_uuid_constants['A/V_RemoteControl']),
            BASE_UUID.format(sig_uuid_constants['A/V_RemoteControlController'])
        ],
        'protocol_descriptors': [
            {
                'protocol': int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data': int(sig_uuid_constants['AVCTP'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['PANU'])],
        'protocol_descriptors': [
            {
                'protocol': int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data': int(sig_uuid_constants['NAP'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['SerialPort'])],
        'protocol_descriptors': [
            {
                'protocol':
                int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data':
                    int(sig_uuid_constants['SerialPort'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['DialupNetworking'])],
        'protocol_descriptors': [
            {
                'protocol':
                int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data':
                    int(sig_uuid_constants['DialupNetworking'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['OBEXObjectPush'])],
        'protocol_descriptors': [
            {
                'protocol':
                int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data':
                    int(sig_uuid_constants['OBEXObjectPush'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['OBEXFileTransfer'])],
        'protocol_descriptors': [
            {
                'protocol':
                int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data':
                    int(sig_uuid_constants['OBEXFileTransfer'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['Headset'])],
        'protocol_descriptors': [
            {
                'protocol': int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data': int(sig_uuid_constants['Headset'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['HandsfreeAudioGateway'])],
        'protocol_descriptors': [
            {
                'protocol':
                int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data':
                    int(sig_uuid_constants['HandsfreeAudioGateway'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['Handsfree'])],
        'protocol_descriptors': [
            {
                'protocol': int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data': int(sig_uuid_constants['Handsfree'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    },
    {
        'service_class_uuids': [BASE_UUID.format(sig_uuid_constants['SIM_Access'])],
        'protocol_descriptors': [
            {
                'protocol': int(sig_uuid_constants['L2CAP'], 16),
                'params': [{
                    'data': int(sig_uuid_constants['SIM_Access'], 16),
                }]
            },
            {
                'protocol': int(sig_uuid_constants['AVCTP'], 16),
                'params': [{
                    'data': 0x103  # to indicate 1.3
                }]
            },
        ],
        'profile_descriptors': [{
            'profile_id':
            int(sig_uuid_constants['A/V_RemoteControl'], 16),
            'major_version':
            1,
            'minor_version':
            2,
        }],
        'additional_protocol_descriptors':
        None,
        'information': [{
            'language': "en",
            'name': "A2DP",
            'description': "Advanced Audio Distribution Profile",
            'provider': "Fuchsia"
        }],
        'additional_attributes': [{
            'id':
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
            'element': {
                'data': 0x0011
            }
        }]
    }
]