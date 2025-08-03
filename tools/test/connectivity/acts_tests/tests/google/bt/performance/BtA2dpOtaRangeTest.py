#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Thu Jul 16 22:58:03 2020

@author: qijiang
"""

import os
import pyvisa
import time
import acts_contrib.test_utils.coex.audio_test_utils as atu
import acts_contrib.test_utils.bt.bt_test_utils as btutils
import pandas as pd
from acts import asserts
from acts_contrib.test_utils.abstract_devices.bluetooth_handsfree_abstract_device import BluetoothHandsfreeAbstractDeviceFactory as bt_factory
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest
from acts_contrib.test_utils.power.PowerBTBaseTest import ramp_attenuation

PHONE_MUSIC_FILE_DIRECTORY = '/sdcard/Music'


class RPIAxis(object):
    def __init__(self, VisaConnectString):
        """Constructor.
        Create a Visa connection

        """
        rm = pyvisa.ResourceManager()
        self.instrument = rm.open_resource(VisaConnectString)
        self.instrument.read_termination = "\n"  # make sure we look for newline at the end of strings we read

    def __getattr__(self, attr):
        return getattr(self.instrument, attr)  # Delegate all other attrs


class RPIAxis_card(RPIAxis):
    """ RPIAxis_card()
        Create an axis

    """
    def __init__(self, axis_object):
        # create an object to communicate to an RPI2 (remote positioner instrument) axis
        self.axis = axis_object  # store pyvisa instrument connection

    def __getattr__(self, attr):
        return getattr(self.axis, attr)  # Delegate all other attrs

    def moveTo(self, where):
        """ moveTo
        move to a given position and make sure you arrived at the target.
        """
        # max travale time in seconds. adjust this if you have a really slow positioner!
        MAXTRAVELTIME = 150
        t0 = time.time()
        self.axis.write("SK %d\n" % where)
        done = False
        while (not done):
            if (time.time() - t0) > MAXTRAVELTIME:
                print("looks like we are stuck!\n")
                return False
            response = self.axis.query("*opc?\n")
            if (response == '1'):
                return True
            else:
                response = self.axis.query("CP?\n")

    # stop the positioner
    def Stop(self):
        t0 = time.time()
        done = False
        self.axis.write("ST\n")
        while (not done):
            if (time.time() - t0) > 2:
                print("Runaway positioner!\n")
                return False
            response = self.axis.query("*opc?\n")
            if (response == '1'):
                return True

    # set continuous rotation mode
    def SetContinuousRotationMode(self):
        self.axis.write("CR\n")

    # set non continuous rotation mode
    def SetNonContinuousRotationMode(self):
        self.axis.write("NCR\n")


class BtA2dpOtaRangeTest(A2dpBaseTest):
    def setup_class(self):

        #'audio_params' is a dict, contains the audio device type, audio streaming
        #settings such as volume, duration, audio recording parameters such as
        #channel, sampling rate/width, and thdn parameters for audio processing
        req_params = [
            'audio_params', 'positioner', 'dut_config', 'attenuation_vector'
        ]
        opt_params = ['music_files']
        self.unpack_userparams(req_params)
        if len(self.android_devices) > 1:
            self.dut = self.android_devices[1]
            self.unpack_userparams(opt_params)
            music_src = self.music_files[0]
            music_dest = PHONE_MUSIC_FILE_DIRECTORY
            success = self.dut.push_system_file(music_src, music_dest)
            if success:
                self.music_file = os.path.join(PHONE_MUSIC_FILE_DIRECTORY,
                                               os.path.basename(music_src))
            # Initialize media_control class
            self.media = btutils.MediaControlOverSl4a(self.dut,
                                                      self.music_file)
        # Set attenuator to minimum attenuation
        self.attenuator = self.attenuators[0]
        self.attenuator.set_atten(self.attenuation_vector['min'])
        # Create the BTOE(Bluetooth-Other-End) device object
        bt_devices = self.user_params.get('bt_devices', [])
        attr, idx = bt_devices.split(':')
        self.bt_device_controller = getattr(self, attr)[int(idx)]
        self.bt_device = bt_factory().generate(self.bt_device_controller)
        btutils.enable_bqr(self.bt_device_controller)

        #Setup positioner
        self.PhiAxisAddress = "TCPIP0::{}::{}::SOCKET".format(
            self.positioner["server_ip"], self.positioner["phi_axis_port"])
        self.ThetaAxisAddress = "TCPIP0::{}::{}::SOCKET".format(
            self.positioner["server_ip"], self.positioner["theta_axis_port"])
        self.phi_axis = RPIAxis(self.PhiAxisAddress)
        self.phi_card = RPIAxis_card(self.phi_axis)
        self.log.info("*IDN? response: {}".format(
            self.phi_card.query("*idn?\n")))
        self.theta_axis = RPIAxis(self.ThetaAxisAddress)
        self.theta_card = RPIAxis_card(self.theta_axis)
        self.log.info("*IDN? response: {}".format(
            self.theta_card.query("*idn?\n")))
        self.phi_card.Stop()
        self.theta_card.Stop()

    def teardown_class(self):

        if hasattr(self, 'media'):
            self.media.stop()
        if hasattr(self, 'attenuator'):
            self.attenuator.set_atten(self.attenuation_vector['min'])
        if hasattr(self, 'dut'):
            self.dut.droid.bluetoothFactoryReset()
            btutils.disable_bluetooth(self.dut.droid)
        self.bt_device.reset()
        self.bt_device.power_off()
        self.phi_card.moveTo(0)
        self.theta_card.moveTo(0)

    def setup_test(self):

        # Reset headset
        self.bt_device.reset()
        # Initialize audio capture devices
        self.audio_device = atu.get_audio_capture_device(
            self.bt_device_controller, self.audio_params)
        # Connect BT link
        connected = self.establish_bt_connection()
        asserts.assert_true(connected, 'BT connection failed')
        # Output file
        file_name = 'OTA_Range_Over_Angle_{}_{}.csv'.format(
            self.dut_config['model'], self.dut_config['screen_placement'])
        self.file_output = os.path.join(self.log_path, file_name)

    def teardown_test(self):

        if hasattr(self, 'media'):
            self.media.stop()
        if hasattr(self, 'attenuator'):
            self.attenuator.set_atten(self.attenuation_vector['min'])
        if hasattr(self, 'dut'):
            self.dut.droid.bluetoothFactoryReset()
            btutils.disable_bluetooth(self.dut.droid)
        self.bt_device.reset()
        self.bt_device.power_off()
        self.phi_card.moveTo(0)
        self.theta_card.moveTo(0)

    def a2dp_play(self):

        if hasattr(self, 'dut'):
            vol = self.dut.droid.getMaxMediaVolume(
            ) * self.audio_params['volume']
            self.dut.droid.setMediaVolume(vol)
            self.media.play()
        else:
            vol = self.bt_device_controller.droid.getMaxMediaVolume(
            ) * self.audio_params['volume']
            self.bt_device_controller.droid.setMediaVolume(vol)
            self.bt_device.previous_track()
            self.bt_device.play()

    def a2dp_stop(self):

        if hasattr(self, 'dut'):
            self.media.stop()
        else:
            self.bt_device.pause()

    def establish_bt_connection(self):

        if hasattr(self, 'dut'):
            self.dut.droid.bluetoothFactoryReset()
            self.bt_device.reset()
            self.bt_device.power_on()
            btutils.enable_bluetooth(self.dut.droid, self.dut.ed)
            connected = btutils.connect_phone_to_headset(
                self.dut, self.bt_device, 60)
            vol = self.dut.droid.getMaxMediaVolume(
            ) * self.audio_params['volume']
            self.dut.droid.setMediaVolume(0)
            time.sleep(1)
            self.dut.droid.setMediaVolume(int(vol))
            self.media.play()
            return connected

        elif len(self.bt_device_controller.droid.
                 bluetoothA2dpSinkGetConnectedDevices()) == 0:
            self.log.warning('Need manual intervention to connect BT link')
            os.system(
                'spd-say "Please manually connect BT and start playback"')
            input('Once fixed, please press ENTER to resume the test')
            return 1

    def run_thdn_analysis(self, audio_captured):
        """Calculate Total Harmonic Distortion plus Noise for latest recording.

        Args:
            audio_captured: the captured audio file
        Returns:
            thdn: thdn value in a list
        """
        # Calculate Total Harmonic Distortion + Noise
        audio_result = atu.AudioCaptureResult(audio_captured,
                                              self.audio_params)
        thdn = audio_result.THDN(**self.audio_params['thdn_params'])
        return thdn

    def record_audio_and_analyze_thdn(self):

        self.a2dp_play()
        time.sleep(1)
        self.audio_device.start()
        time.sleep(self.audio_params['duration'])
        audio_captured = self.audio_device.stop()
        audio_result = atu.AudioCaptureResult(audio_captured,
                                              self.audio_params)
        thdn = audio_result.THDN(**self.audio_params['thdn_params'])
        self.log.info('THDN is {}'.format(thdn[0]))

        self.a2dp_stop()

        return thdn[0]

    def recover_bt_link(self):
        """Recover BT link during test.

        Recover BT link from the a2dp sink device

        Returns:
            connected: signal whether bt link is restored
        """
        #Try to connect from the sink device
        if len(self.bt_device_controller.droid.bluetoothGetConnectedDevices()
               ) == 0:
            self.log.warning('Try to recover BT link')
            self.attenuator.set_atten(self.attenuation_vector['min'])

            if hasattr(self, 'dut'):
                connected = self.establish_bt_connection()
                return connected
            else:
                device_bonded = self.bt_device_controller.droid.bluetoothGetBondedDevices(
                )[0]['address']
                trial_count = 0
                trial_limit = 3
                self.log.info('Try to reconnect from the sink device')
                while trial_count < trial_limit:
                    #Connect master device from the sink device
                    time_start = time.time()
                    while time.time() < time_start + 5:
                        try:
                            self.bt_device_controller.droid.bluetoothConnectBonded(
                                device_bonded)
                            break
                        except:
                            pass
                    time.sleep(2)
                    if len(self.bt_device_controller.droid.
                           bluetoothA2dpSinkGetConnectedDevices()) > 0:
                        vol = self.bt_device_controller.droid.getMaxMediaVolume(
                        ) * self.audio_params['volume']
                        self.bt_device_controller.droid.setMediaVolume(0)
                        time.sleep(1)
                        self.bt_device_controller.droid.setMediaVolume(
                            int(vol))
                        return 1
                    trial_count += 1
                #Automated reconnect from sink device doesn't work, start fresh connection
                if trial_count >= trial_limit:
                    self.log.info(
                        'Need manual intervention on the master device side')
                    connected = self.establish_bt_connection()
                    return connected
        else:
            return 1

    def find_bt_max_range_bisection_search(self):

        #First linear search to narrow the bisection search
        atten_min = self.attenuation_vector['min']
        atten_max = self.attenuation_vector['max']
        atten_step = self.attenuation_vector['step_bisection']
        #Start from initial attenuation
        atten_left = atten_min
        atten_right = atten_min
        while atten_left == atten_right and atten_left < atten_max:
            atten_now = self.attenuator.get_atten()
            connected = self.recover_bt_link()
            if connected == 0:
                self.log.warning("Skip this angle as BT connection failed")
                max_range = atten_max
                return max_range
            else:
                self.log.info('Connection restored')
            ramp_attenuation(self.attenuator, atten_now)
            self.log.info("Attenuation set to {}".format(atten_now))
            time.sleep(2)

            thdn = self.record_audio_and_analyze_thdn()
            if thdn > self.audio_params['thdn_threshold'] or thdn == 0:
                #Hit the right limit for bisection search
                if atten_right == atten_min:
                    self.log.warning('Link breaks at the minimum attenuation')
                    max_range = atten_min
                    return max_range
                else:
                    atten_right = atten_now
                    self.log.info(
                        'Right limit found at {} dB'.format(atten_right))
            else:
                atten_left = atten_now
                atten_right = atten_left
                atten_next = min(atten_now + atten_step, atten_max)
                ramp_attenuation(self.attenuator, atten_next)
        if atten_left == atten_right:
            self.log.warning('Could not reach max range')
            max_range = atten_max
            return max_range

        #Start the bisection search
        self.log.info('Start bisection search between {} dB and {} dB'.format(
            atten_left, atten_right))
        while atten_right - atten_left > 1:
            connected = self.recover_bt_link()
            if connected == 0:
                self.log.warning("Skip this angle as BT connection failed")
                max_range = atten_max
                return max_range
            else:
                self.log.info('Connection restored')

            atten_mid = round((atten_left + atten_right) / 2)
            ramp_attenuation(self.attenuator, atten_mid)
            atten_now = self.attenuator.get_atten()
            self.log.info("Attenuation set to {}".format(atten_now))
            time.sleep(5)
            thdn = self.record_audio_and_analyze_thdn()
            if thdn > self.audio_params['thdn_threshold'] or thdn == 0:
                atten_right = atten_mid
                max_range = atten_right - 1
            else:
                atten_left = atten_mid
                max_range = atten_left
        self.log.info('Max range reached at {} dB'.format(max_range))
        return max_range

    def find_bt_max_range_linear_fine_search(self):

        thdn = 0.03
        atten_now = self.attenuator.get_atten()

        while thdn < self.audio_params[
                'thdn_threshold'] and thdn != 0 and atten_now < self.attenuation_vector[
                    'max']:
            atten_now = self.attenuator.get_atten()
            self.log.info("Attenuation set to {}".format(atten_now))
            thdn = self.record_audio_and_analyze_thdn()
            self.log.info("THDN is {}".format(thdn))
            self.attenuator.set_atten(atten_now +
                                      self.attenuation_vector['step_fine'])
        max_range = self.attenuator.get_atten(
        ) - self.attenuation_vector['step_fine'] * 2
        if thdn == 0:
            self.log.warning(
                "Music play stopped, link might get lost, max range reached at {} dB"
                .format(max_range))
        else:
            self.log.info("Max range reached at {}".format(max_range))
        if atten_now == self.attenuation_vector['max']:
            self.log.warning("Fail to reach max range")
        return max_range

    def test_bisection_search_max(self):

        #Find the BT max range under each angle using bisection search
        max_range_all = []

        for phi in self.positioner['phi_range']:

            succeed = self.phi_card.moveTo(phi)
            if succeed:
                self.log.info("Phi positioner moved to {} degree".format(phi))
            else:
                self.log.warning(
                    "Fail to move phi positioner to {} degree".format(phi))
            self.log.info("Phi positioner moved to {} degree".format(phi))
            max_ranges = [phi]

            for theta in self.positioner['theta_range']:

                succeed = self.theta_card.moveTo(theta)
                if succeed:
                    self.log.info(
                        "Theta positioner moved to {} degree".format(theta))
                else:
                    self.log.warning(
                        "Failed to move theta positioner to {} degree".format(
                            theta))
                self.log.info(
                    "Theta positioner moved to {} degree".format(theta))

                ramp_attenuation(self.attenuator,
                                 self.attenuation_vector['min'])
                time.sleep(2)
                max_range = self.find_bt_max_range_bisection_search()
                max_ranges.append(max_range)
            max_range_all.append(max_ranges)
        columns = ['Phi/Theta']
        columns.extend(self.positioner['theta_range'])
        df = pd.DataFrame(max_range_all, columns=columns)
        df.to_csv(self.file_output, index=False)

    def test_coarse_search(self):

        #Coarse search to find the highest minimum attenuation can be set to
        #be a starting point for all angles
        thdn = 0.03
        max_atten_reached = 0
        ramp_attenuation(self.attenuator,
                         self.attenuation_vector['start_coarse'])
        self.log.info('Start attenuation at {} dB'.format(
            self.attenuator.get_atten()))
        while True:
            atten_now = self.attenuator.get_atten()
            if atten_now == self.attenuation_vector['max']:
                if max_atten_reached > 1:
                    self.log.warning(
                        'Can not reach to the highest minimum, attenuator is already set to be max, need to add more attenuation'
                    )
                    break
            for phi in self.positioner['phi_range']:
                if thdn == 0 or thdn >= self.audio_params["thdn_threshold"]:
                    break
                succeed = self.phi_card.moveTo(phi)
                if succeed:
                    self.log.info(
                        "Phi positioner moved to {} degree".format(phi))
                else:
                    self.log.warning(
                        "Fail to move phi positioner to {} degree".format(phi))
                self.log.info("Phi positioner moved to {} degree".format(phi))

                for theta in self.positioner['theta_range']:

                    succeed = self.theta_card.moveTo(theta)
                    if succeed:
                        self.log.info(
                            "Theta positioner moved to {} degree".format(
                                theta))
                    else:
                        self.log.warning(
                            "Failed to move theta positioner to {} degree".
                            format(theta))
                    self.log.info(
                        "Theta positioner moved to {} degree".format(theta))

                    thdn = self.record_audio_and_analyze_thdn()
                    self.log.info(
                        'THDN at thea {} degree, phi {} degree is {}'.format(
                            theta, phi, thdn))
                    if thdn == 0 or thdn >= self.audio_params["thdn_threshold"]:
                        break
            if thdn == 0 or thdn >= self.audio_params["thdn_threshold"]:
                highest_max = self.attenuator.get_atten(
                ) - self.attenuation_vector['step_coarse']
                self.log.info(
                    'Highest minimum attenuation is {} dB, fine search can start from there'
                    .format(highest_max))
                break
            atten_new = min(atten_now + self.attenuation_vector['step_coarse'],
                            self.attenuation_vector['max'])
            if atten_new == self.attenuation_vector['max']:
                max_atten_reached += 1
            self.attenuator.set_atten(atten_new)
            self.log.info('\nSetting attenuator to {} dB'.format(
                self.attenuator.get_atten()))

    def test_finestep_search_max(self):

        #Find the BT max range under each angle with a finer step search
        max_range_all = []
        for phi in self.positioner['phi_range']:

            succeed = self.phi_card.moveTo(phi)
            if succeed:
                self.log.info("Phi positioner moved to {} degree".format(phi))
            else:
                self.log.warning(
                    "Fail to move phi positioner to {} degree".format(phi))
            self.log.info("Phi positioner moved to {} degree".format(phi))
            max_ranges = [phi]

            for theta in self.positioner['theta_range']:

                succeed = self.theta_card.moveTo(theta)
                if succeed:
                    self.log.info(
                        "Theta positioner moved to {} degree".format(theta))
                else:
                    self.log.warning(
                        "Failed to move theta positioner to {} degree".format(
                            theta))
                self.log.info(
                    "Theta positioner moved to {} degree".format(theta))
                connected = self.recover_bt_link()
                if connected == 0:
                    self.log.warning("Skip this angle as BT connection failed")
                    max_range = self.attenuation_vector['max']
                    return max_range
                else:
                    self.log.info('Connection restored')
                ramp_attenuation(self.attenuator,
                                 self.attenuation_vector['start_fine'])
                max_range = self.find_bt_max_range_linear_fine_search()
                max_ranges.append(max_range)
            max_range_all.append(max_ranges)
        columns = ['Phi/Theta']
        columns.extend(self.positioner['theta_range'])
        df_range = pd.DataFrame(max_range_all, columns=columns)
        df_range.to_csv(self.file_output, index=False)
