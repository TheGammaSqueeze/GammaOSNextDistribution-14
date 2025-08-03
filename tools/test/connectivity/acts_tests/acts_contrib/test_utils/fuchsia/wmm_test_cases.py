#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

# Internal Traffic Differentiation
test_internal_traffic_diff_VO_VI = {
    'phase_1': {
        'stream_VO':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.87,
                      rel_tolerance=0.03)
             ]),
        'stream_VI':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<', phase='phase_1', stream='stream_VO')
             ])
    }
}

test_internal_traffic_diff_VO_BE = {
    'phase_1': {
        'stream_VO':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BE':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<', phase='phase_1', stream='stream_VO')
             ])
    }
}

test_internal_traffic_diff_VO_BK = {
    'phase_1': {
        'stream_VO':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BK':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<', phase='phase_1', stream='stream_VO')
             ])
    }
}

test_internal_traffic_diff_VI_BE = {
    'phase_1': {
        'stream_VI':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BE':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<', phase='phase_1', stream='stream_VI')
             ])
    }
}

test_internal_traffic_diff_VI_BK = {
    'phase_1': {
        'stream_VI':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BK':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<', phase='phase_1', stream='stream_VI')
             ])
    }
}

test_internal_traffic_diff_BE_BK = {
    'phase_1': {
        'stream_BE':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.03)
             ]),
        'stream_BK':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<', phase='phase_1', stream='stream_BE')
             ])
    }
}
# External Traffic Differentiation

# Single station, STAUT transmits high priority
test_external_traffic_diff_staut_VO_ap_VI = {
    'phase_1': {
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.87,
                      rel_tolerance=0.03)
             ]),
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VO_staut_to_ap')
             ])
    }
}

test_external_traffic_diff_staut_VO_ap_BE = {
    'phase_1': {
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VO_staut_to_ap')
             ])
    }
}

test_external_traffic_diff_staut_VO_ap_BK = {
    'phase_1': {
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VO_staut_to_ap')
             ])
    }
}

test_external_traffic_diff_staut_VI_ap_BE = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VI_staut_to_ap')
             ])
    }
}

test_external_traffic_diff_staut_VI_ap_BK = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VI_staut_to_ap')
             ])
    }
}

test_external_traffic_diff_staut_BE_ap_BK = {
    'phase_1': {
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.03)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_BE_staut_to_ap')
             ])
    }
}

# Single station, STAUT transmits low priority
test_external_traffic_diff_staut_VI_ap_VO = {
    'phase_1': {
        'stream_VO_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.87,
                      rel_tolerance=0.03)
             ]),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VO_ap_to_staut')
             ])
    }
}

test_external_traffic_diff_staut_BE_ap_VO = {
    'phase_1': {
        'stream_VO_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VO_ap_to_staut')
             ])
    }
}

test_external_traffic_diff_staut_BK_ap_VO = {
    'phase_1': {
        'stream_VO_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VO_ap_to_staut')
             ])
    }
}

test_external_traffic_diff_staut_BE_ap_VI = {
    'phase_1': {
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VI_ap_to_staut')
             ])
    }
}

test_external_traffic_diff_staut_BK_ap_VI = {
    'phase_1': {
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.03)
             ]),
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_VI_ap_to_staut')
             ])
    }
}

test_external_traffic_diff_staut_BK_ap_BE = {
    'phase_1': {
        'stream_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.03)
             ]),
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=1.0,
             validation=[
                 dict(operator='<',
                      phase='phase_1',
                      stream='stream_BE_ap_to_staut')
             ])
    }
}

# Dual Internal/External Traffic Differetiation

test_dual_traffic_diff_staut_VO_VI_ap_VI = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.5,
             validation=[
                 dict(operator='==',
                      phase='phase_1',
                      stream='stream_VI_ap_to_staut',
                      max_bw_rel_tolerance=0.15)
             ]),
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.01)
             ]),
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=0.5)
    }
}

test_dual_traffic_diff_staut_VO_BE_ap_BE = {
    'phase_1': {
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.5,
             validation=[
                 dict(operator='==',
                      phase='phase_1',
                      stream='stream_BE_ap_to_staut',
                      max_bw_rel_tolerance=0.15)
             ]),
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=0.5)
    }
}

test_dual_traffic_diff_staut_VO_BK_ap_BK = {
    'phase_1': {
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.5,
             validation=[
                 dict(operator='==',
                      phase='phase_1',
                      stream='stream_BK_ap_to_staut',
                      max_bw_rel_tolerance=0.15)
             ]),
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.01)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=0.5)
    }
}

test_dual_traffic_diff_staut_VI_BE_ap_BE = {
    'phase_1': {
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.5,
             validation=[
                 dict(operator='==',
                      phase='phase_1',
                      stream='stream_BE_ap_to_staut',
                      max_bw_rel_tolerance=0.15)
             ]),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=0.5)
    }
}

test_dual_traffic_diff_staut_VI_BK_ap_BK = {
    'phase_1': {
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.5,
             validation=[
                 dict(operator='==',
                      phase='phase_1',
                      stream='stream_BK_ap_to_staut',
                      max_bw_rel_tolerance=0.15)
             ]),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.01)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=0.5)
    }
}

test_dual_traffic_diff_staut_BE_BK_ap_BK = {
    'phase_1': {
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.5,
             validation=[
                 dict(operator='==',
                      phase='phase_1',
                      stream='stream_BK_ap_to_staut',
                      max_bw_rel_tolerance=0.15)
             ]),
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.85,
             validation=[
                 dict(operator='>=',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.01)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=0.5)
    }
}

# ACM Bit Conformance Tests (Single station, as WFA test below uses two)
test_acm_bit_on_VI = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_VI',
            max_bandwidth_percentage=0.6,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_staut_to_ap_1',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_staut_to_ap_2',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
            ]),
        'stream_BE_staut_to_ap_1':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.6),
        'stream_BE_staut_to_ap_2':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.6)
    }
}

# AC Parameter Modificiation Tests (Single station, as WFA test below uses two)
test_ac_param_degrade_VI = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_VI',
            max_bandwidth_percentage=0.6,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_staut_to_ap_1',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_staut_to_ap_2',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
            ]),
        'stream_BE_staut_to_ap_1':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.6),
        'stream_BE_staut_to_ap_2':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.6)
    }
}

test_ac_param_degrade_VO = {
    'phase_1': {
        'stream_VO_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_VO',
            max_bandwidth_percentage=0.6,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_staut_to_ap_1',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_staut_to_ap_2',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
            ]),
        'stream_BE_staut_to_ap_1':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.6),
        'stream_BE_staut_to_ap_2':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.6)
    }
}

test_ac_param_improve_BE = {
    'phase_1': {
        'stream_BE_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_BE',
            max_bandwidth_percentage=0.6,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='>',
                     phase='phase_1',
                     stream='stream_VI_staut_to_ap_1',
                     bandwidth_percentage=0.869,
                     rel_tolerance=0.05),
                dict(operator='>',
                     phase='phase_1',
                     stream='stream_VI_staut_to_ap_2',
                     bandwidth_percentage=0.869,
                     rel_tolerance=0.05),
            ]),
        'stream_VI_staut_to_ap_1':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.6),
        'stream_VI_staut_to_ap_2':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.6)
    }
}

test_ac_param_improve_BK = {
    'phase_1': {
        'stream_BK_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_BK',
            max_bandwidth_percentage=0.6,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='>',
                     phase='phase_1',
                     stream='stream_VI_staut_to_ap_1',
                     bandwidth_percentage=0.869,
                     rel_tolerance=0.05),
                dict(operator='>',
                     phase='phase_1',
                     stream='stream_VI_staut_to_ap_2',
                     bandwidth_percentage=0.869,
                     rel_tolerance=0.05),
            ]),
        'stream_VI_staut_to_ap_1':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.6),
        'stream_VI_staut_to_ap_2':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.6)
    }
}
# WFA Test Plan Cases

# Traffic Differentiation in Single BSS (Single Station)
test_wfa_traffic_diff_single_station_staut_BE_ap_VI_BE = {
    'phase_1': {
        'steam_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'steam_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_VI_ap_to_staut',
                      bandwidth_percentage=0.85,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_traffic_diff_single_station_staut_VI_BE = {
    'phase_1': {
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'stream_BE_staut_to_ap_1':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_VI_staut_to_ap',
                      bandwidth_percentage=0.89,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_staut_to_ap_2':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65),
    }
}

test_wfa_traffic_diff_single_station_staut_VI_BE_ap_BE = {
    'phase_1': {
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_VI_staut_to_ap',
                      bandwidth_percentage=0.87,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_traffic_diff_single_station_staut_BE_BK_ap_BK = {
    'phase_1': {
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.45),
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'stream_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.45),
        'stream_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_BE_staut_to_ap',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.01)
             ]),
        'stream_BK_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_BK',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_traffic_diff_single_station_staut_VO_VI_ap_VI = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45),
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'stream_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45),
        'stream_VO_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_VO_staut_to_ap',
                      bandwidth_percentage=0.81,
                      rel_tolerance=0.01)
             ]),
        'stream_VI_ap_to_staut':
        dict(transmitter_str='access_point',
             receiver_str='staut',
             access_category='AC_VI',
             max_bandwidth_percentage=0.65)
    }
}

# Traffic Differentiation in Single BSS (Two Stations)
test_wfa_traffic_diff_two_stations_staut_BE_secondary_VI_BE = {
    'phase_1': {
        'steam_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'steam_BE_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_VI_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_VI_secondary_to_ap',
                      bandwidth_percentage=0.90,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_traffic_diff_two_stations_staut_VI_secondary_BE = {
    'phase_1': {
        'steam_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45),
        'stream_BE_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'steam_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='steam_VI_staut_to_ap',
                      bandwidth_percentage=0.88,
                      rel_tolerance=0.01)
             ]),
        'stream_BE_secondary_to_ap_1':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45),
        'stream_BE_secondary_to_ap_2':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_traffic_diff_two_stations_staut_BK_secondary_BE_BK = {
    'phase_1': {
        'steam_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.45),
        'stream_BE_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'steam_BK_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.45),
        'stream_BE_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_BE_secondary_to_ap',
                      bandwidth_percentage=0.90,
                      rel_tolerance=0.01)
             ]),
        'stream_BK_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BK',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_traffic_diff_two_stations_staut_VI_secondary_VO_VI = {
    'phase_1': {
        'steam_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45),
        'stream_VO_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.45)
    },
    'phase_2': {
        'steam_VI_staut_to_ap':
        dict(transmitter_str='staut',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.45),
        'stream_VO_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_VO',
             max_bandwidth_percentage=0.45,
             validation=[
                 dict(operator='>=',
                      phase='phase_1',
                      stream='stream_VO_secondary_to_ap',
                      bandwidth_percentage=0.90,
                      rel_tolerance=0.01)
             ]),
        'stream_VI_secondary_to_ap':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_VI',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_acm_bit_on_VI = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_VI',
            max_bandwidth_percentage=0.65,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_secondary_to_ap_1',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_secondary_to_ap_2',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
            ]),
        'stream_BE_secondary_to_ap_1':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65),
        'stream_BE_secondary_to_ap_2':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65)
    }
}

test_wfa_ac_param_degrade_VI = {
    'phase_1': {
        'stream_VI_staut_to_ap':
        dict(
            transmitter_str='staut',
            receiver_str='access_point',
            access_category='AC_VI',
            max_bandwidth_percentage=0.65,
            validation=[
                # TODO(): This should technically be an "or"
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_secondary_to_ap_1',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
                dict(operator='<',
                     phase='phase_1',
                     stream='stream_BE_secondary_to_ap_2',
                     bandwidth_percentage=1.15,
                     rel_tolerance=0.05),
            ]),
        'stream_BE_secondary_to_ap_1':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65),
        'stream_BE_secondary_to_ap_2':
        dict(transmitter_str='secondary_sta',
             receiver_str='access_point',
             access_category='AC_BE',
             max_bandwidth_percentage=0.65)
    }
}
