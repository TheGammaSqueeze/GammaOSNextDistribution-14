
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'audio/Aconnect',
            suites = ['audio'],
            main_package = 'autotest_lib.client.site_tests.audio_Aconnect.audio_Aconnect',
        ),
        test_common.define_test(
            'audio/ActiveStreamStress',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.audio_ActiveStreamStress.audio_ActiveStreamStress',
        ),
        test_common.define_test(
            'audio/Aplay',
            suites = ['kernel_per-build_regression'],
            main_package = 'autotest_lib.client.site_tests.audio_Aplay.audio_Aplay',
        ),
        test_common.define_test(
            'audio/AudioBasicAssistant',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicBluetoothPlayback',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicBluetoothPlaybackRecord',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicBluetoothRecord',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicExternalMicrophone',
            suites = ['audio_essential'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicHDMI',
            suites = ['audio_basic'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicHeadphone',
            suites = ['audio_essential'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicHotwording',
            suites = ['audio_basic'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicInternalMicrophone',
            suites = ['audio_essential'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicInternalSpeaker',
            suites = ['audio_essential'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicUSBPlayback',
            suites = ['audio_basic'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicUSBPlaybackRecord',
            suites = ['audio_basic'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBasicUSBRecord',
            suites = ['audio_basic'],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioBluetoothConnectionStability',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioCorruption',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.audio_AudioCorruption.audio_AudioCorruption',
        ),
        test_common.define_test(
            'audio/AudioInputGain',
            suites = ['audio_essential'],
            main_package = 'autotest_lib.client.site_tests.audio_AudioInputGain.audio_AudioInputGain',
        ),
        test_common.define_test(
            'audio/AudioNodeSwitch',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioRoutingUSB',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioSanityCheck',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/AudioWebRTCLoopback',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'audio/CRASFormatConversion',
            suites = ['audio'],
            main_package = 'autotest_lib.client.site_tests.audio_CRASFormatConversion.audio_CRASFormatConversion',
        ),
        test_common.define_test(
            'audio/CrasDevSwitchStress',
            suites = ['audio'],
            main_package = 'autotest_lib.client.site_tests.audio_CrasDevSwitchStress.audio_CrasDevSwitchStress',
        ),
        test_common.define_test(
            'audio/CrasGetNodes',
            suites = ['audio_essential'],
            main_package = 'autotest_lib.client.site_tests.audio_CrasGetNodes.audio_CrasGetNodes',
        ),
        test_common.define_test(
            'audio/CrasPinnedStream',
            suites = ['audio', 'partners'],
            main_package = 'autotest_lib.client.site_tests.audio_CrasPinnedStream.audio_CrasPinnedStream',
        ),
        test_common.define_test(
            'audio/CrasSanity',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.audio_CrasCheck.audio_CrasCheck',
        ),
        test_common.define_test(
            'audio/CrasStress',
            suites = ['audio'],
            main_package = 'autotest_lib.client.site_tests.audio_CrasStress.audio_CrasStress',
        ),
        test_common.define_test(
            'audio/PlaybackPower',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.audio_PlaybackPower.audio_PlaybackPower',
        ),
        test_common.define_test(
            'audio/SeekAudioFeedback',
            suites = ['audio'],
            main_package = 'autotest_lib.client.site_tests.audio_SeekAudioFeedback.audio_SeekAudioFeedback',
        ),
        test_common.define_test(
            'audio/WebRtcAudioLoopback',
            suites = ['hotrod'],
            main_package = 'autotest_lib.client.site_tests.audio_WebRtcAudioLoopback.audio_WebRtcAudioLoopback',
        ),
        test_common.define_test(
            'audio/YoutubePlayback',
            suites = ['audio'],
            main_package = 'autotest_lib.client.site_tests.audio_YoutubePlayback.audio_YoutubePlayback',
        )
    ]
