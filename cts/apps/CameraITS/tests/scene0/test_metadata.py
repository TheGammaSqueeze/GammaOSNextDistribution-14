# Copyright 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""CameraITS test to verify metadata entries."""

import logging
import math

from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils

_HYPERFOCAL_MIN = 0.02


class MetadataTest(its_base_test.ItsBaseTest):
  """Test the validity of some metadata entries.

  Looks at the capture results and at the camera characteristics objects.
  """

  def test_metadata(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      # Arbitrary capture request exposure values; image content is not
      # important for this test, only the metadata.
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.backward_compatible(props))
      auto_req = capture_request_utils.auto_capture_request()
      cap = cam.do_capture(auto_req)
      md = cap['metadata']
      self.failed = False
      logging.debug('Hardware level')
      logging.debug('Legacy: %s', camera_properties_utils.legacy(props))

      logging.debug('Limited: %s', camera_properties_utils.limited(props))
      logging.debug('Full or better: %s',
                    camera_properties_utils.full_or_better(props))
      logging.debug('Level 3: %s', camera_properties_utils.level3(props))
      logging.debug('Capabilities')
      logging.debug('Manual sensor: %s',
                    camera_properties_utils.manual_sensor(props))
      logging.debug('Manual post-proc: %s',
                    camera_properties_utils.manual_post_proc(props))
      logging.debug('Raw: %s', camera_properties_utils.raw(props))
      logging.debug('Sensor fusion: %s',
                    camera_properties_utils.sensor_fusion(props))

      check(self, 'android.info.supportedHardwareLevel' in props,
            'android.info.supportedHardwareLevel in props')
      check(self, props['android.info.supportedHardwareLevel'] is not None,
            'props[android.info.supportedHardwareLevel] is not None')
      check(self, props['android.info.supportedHardwareLevel'] in [0, 1, 2, 3],
            'props[android.info.supportedHardwareLevel] in [0, 1, 2, 3]')
      manual_sensor = camera_properties_utils.manual_sensor(props)
      # Test: rollingShutterSkew, and frameDuration tags must all be present,
      # and rollingShutterSkew must be greater than zero and smaller than all
      # of the possible frame durations.
      if manual_sensor:
        check(self, 'android.sensor.frameDuration' in md,
              'md.has_key("android.sensor.frameDuration")')
        check(self, md['android.sensor.frameDuration'] is not None,
              'md["android.sensor.frameDuration"] is not None')
        check(self, md['android.sensor.rollingShutterSkew'] > 0,
              'md["android.sensor.rollingShutterSkew"] > 0')
        check(self, md['android.sensor.frameDuration'] > 0,
              'md["android.sensor.frameDuration"] > 0')
        check(
            self, md['android.sensor.rollingShutterSkew'] <=
            md['android.sensor.frameDuration'],
            ('md["android.sensor.rollingShutterSkew"] <= '
             'md["android.sensor.frameDuration"]'))
        logging.debug('frameDuration: %d ns',
                      md['android.sensor.frameDuration'])

      check(self, 'android.sensor.rollingShutterSkew' in md,
            'md.has_key("android.sensor.rollingShutterSkew")')
      check(self, md['android.sensor.rollingShutterSkew'] is not None,
            'md["android.sensor.rollingShutterSkew"] is not None')
      logging.debug('rollingShutterSkew: %d ns',
                    md['android.sensor.rollingShutterSkew'])

      # Test: timestampSource must be a valid value.
      check(self, 'android.sensor.info.timestampSource' in props,
            'props.has_key("android.sensor.info.timestampSource")')
      check(self, props['android.sensor.info.timestampSource'] is not None,
            'props["android.sensor.info.timestampSource"] is not None')
      check(self, props['android.sensor.info.timestampSource'] in [0, 1],
            'props["android.sensor.info.timestampSource"] in [0,1]')

      # Test: croppingType must be a valid value, and for full devices, it
      # must be FREEFORM=1.
      check(self, 'android.scaler.croppingType' in props,
            'props.has_key("android.scaler.croppingType")')
      check(self, props['android.scaler.croppingType'] is not None,
            'props["android.scaler.croppingType"] is not None')
      check(self, props['android.scaler.croppingType'] in [0, 1],
            'props["android.scaler.croppingType"] in [0,1]')

      # Test: android.sensor.blackLevelPattern exists for RAW and is not None
      if camera_properties_utils.raw(props):
        check(self, 'android.sensor.blackLevelPattern' in props,
              'props.has_key("android.sensor.blackLevelPattern")')
        check(self, props['android.sensor.blackLevelPattern'] is not None,
              'props["android.sensor.blackLevelPattern"] is not None')

      if self.failed:
        raise AssertionError('props failure. Check test_log.DEBUG.')

      if not camera_properties_utils.legacy(props):
        # Test: pixel_pitch, FOV, and hyperfocal distance are reasonable
        fmts = props['android.scaler.streamConfigurationMap'][
            'availableStreamConfigurations']
        fmts = sorted(
            fmts, key=lambda k: k['width'] * k['height'], reverse=True)
        sensor_size = props['android.sensor.info.physicalSize']
        pixel_pitch_h = (sensor_size['height'] / fmts[0]['height'] * 1E3)
        pixel_pitch_w = (sensor_size['width'] / fmts[0]['width'] * 1E3)
        logging.debug('Assert pixel_pitch WxH: %.2f um, %.2f um', pixel_pitch_w,
                      pixel_pitch_h)
        if (not 0.5 <= pixel_pitch_w <= 10 or
            not 0.5 <= pixel_pitch_h <= 10 or
            not 0.333 <= pixel_pitch_w/pixel_pitch_h <= 3.0):
          raise AssertionError(
              f'Pixel pitch error! w: {pixel_pitch_w}, h: {pixel_pitch_h}')

        diag = math.sqrt(sensor_size['height']**2 + sensor_size['width']**2)
        fl = md['android.lens.focalLength']
        logging.debug('Focal length: %.3f', fl)
        fov = 2 * math.degrees(math.atan(diag / (2 * fl)))
        logging.debug('Assert field of view: %.1f degrees', fov)
        if not 10 <= fov <= 130:
          raise AssertionError(f'FoV error: {fov:.1f}')

        if camera_properties_utils.lens_approx_calibrated(props):
          diopter_hyperfocal = props['android.lens.info.hyperfocalDistance']
          if diopter_hyperfocal != 0.0:
            hyperfocal = 1.0 / diopter_hyperfocal
            if _HYPERFOCAL_MIN > hyperfocal:
              raise AssertionError('hyperfocal distance error: '
                                   f'{hyperfocal:.2f}, MIN: {_HYPERFOCAL_MIN}')

        logging.debug('Minimum focus distance: %3.f',
                      props['android.lens.info.minimumFocusDistance'])


def check(self, expr, msg):
  if expr:
    logging.debug('Passed>>%s', msg)
  else:
    logging.debug('Failed>>%s', msg)
    self.failed = True


if __name__ == '__main__':
  test_runner.main()
