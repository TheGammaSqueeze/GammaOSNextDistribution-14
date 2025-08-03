# Copyright 2018 The Android Open Source Project
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
"""CameraITS test that device will request/capture correct exp/gain values."""

import logging
import os.path

from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils


_NAME = os.path.basename(__file__).split('.')[0]
# Spec to be within 3% but not over for exposure in capture vs exposure request.
_RTOL_EXP_GAIN = 0.97
_TEST_EXP_RANGE = [6E6, 1E9]  # ns [6ms, 1s]


class RequestCaptureMatchTest(its_base_test.ItsBaseTest):
  """Test device captures have correct exp/gain values from request."""

  def test_request_capture_match(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.per_frame_control(props))
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)

      valid_formats = ['yuv', 'jpg']
      if camera_properties_utils.raw16(props):
        valid_formats.insert(0, 'raw')
      # grab exp/gain ranges from camera
      sensor_exp_range = props['android.sensor.info.exposureTimeRange']
      sens_range = props['android.sensor.info.sensitivityRange']
      logging.debug('sensor exposure time range: %s', sensor_exp_range)
      logging.debug('sensor sensitivity range: %s', sens_range)

      # determine if exposure test range is within sensor reported range
      if sensor_exp_range[0] == 0:
        raise AssertionError('Min expsoure == 0')
      exp_range = []
      if sensor_exp_range[0] < _TEST_EXP_RANGE[0]:
        exp_range.append(_TEST_EXP_RANGE[0])
      else:
        exp_range.append(sensor_exp_range[0])
      if sensor_exp_range[1] > _TEST_EXP_RANGE[1]:
        exp_range.append(_TEST_EXP_RANGE[1])
      else:
        exp_range.append(sensor_exp_range[1])

      data = {}
      # build requests
      for fmt in valid_formats:
        logging.debug('format: %s', fmt)
        size = capture_request_utils.get_available_output_sizes(fmt, props)[-1]
        out_surface = {'width': size[0], 'height': size[1], 'format': fmt}
        # pylint: disable=protected-access
        if cam._hidden_physical_id:
          out_surface['physicalCamera'] = cam._hidden_physical_id
        reqs = []
        index_list = []
        for exp in exp_range:
          for sens in sens_range:
            reqs.append(capture_request_utils.manual_capture_request(sens, exp))
            index_list.append((fmt, exp, sens))
            logging.debug('exp_req: %d, sens_req: %d', exp, sens)

        # take shots
        caps = cam.do_capture(reqs, out_surface)

        # extract exp/sensitivity data
        for i, cap in enumerate(caps):
          exposure_cap = cap['metadata']['android.sensor.exposureTime']
          sensitivity_cap = cap['metadata']['android.sensor.sensitivity']
          data[index_list[i]] = (fmt, exposure_cap, sensitivity_cap)

      # check read/write match across all shots
      e_failed = []  # exposure time FAILs
      s_failed = []  # sensitivity FAILs
      r_failed = []  # sensitivity range FAILs
      for fmt_req in valid_formats:
        for e_req in exp_range:
          for s_req in sens_range:
            fmt_cap, e_cap, s_cap = data[(fmt_req, e_req, s_req)]
            if (e_req < e_cap or e_cap / float(e_req) <= _RTOL_EXP_GAIN):
              e_failed.append({
                  'format': fmt_cap,
                  'e_req': e_req,
                  'e_cap': e_cap,
                  's_req': s_req,
                  's_cap': s_cap
              })
            if (s_req < s_cap or s_cap / float(s_req) <= _RTOL_EXP_GAIN):
              s_failed.append({
                  'format': fmt_cap,
                  'e_req': e_req,
                  'e_cap': e_cap,
                  's_req': s_req,
                  's_cap': s_cap
              })
            if (first_api_level >= its_session_utils.ANDROID14_API_LEVEL and
                s_cap < sens_range[0]):
              r_failed.append({
                  'format': fmt_cap,
                  'e_req': e_req,
                  'e_cap': e_cap,
                  's_req': s_req,
                  's_cap': s_cap
              })

        # print results
        if e_failed:
          logging.debug('FAILs for exposure time')
          for fail in e_failed:
            logging.debug('format: %s, e_req: %d, e_cap: %d, RTOL: %.2f, ',
                          fail['format'], fail['e_req'], fail['e_cap'],
                          _RTOL_EXP_GAIN)
            logging.debug('s_req: %d, s_cap: %d, RTOL: %.2f',
                          fail['s_req'], fail['s_cap'], _RTOL_EXP_GAIN)
        if s_failed:
          logging.debug('FAILs for sensitivity(ISO)')
          for fail in s_failed:
            logging.debug('format: %s, s_req: %d, s_cap: %d, RTOL: %.2f, ',
                          fail['format'], fail['s_req'], fail['s_cap'],
                          _RTOL_EXP_GAIN)
            logging.debug('e_req: %d, e_cap: %d, RTOL: %.2f',
                          fail['e_req'], fail['e_cap'], _RTOL_EXP_GAIN)
        if r_failed:
          logging.debug('FAILs for sensitivity(ISO) range')
          for fail in r_failed:
            logging.debug('format: %s, s_req: %d, s_cap: %d, RTOL: %.2f, ',
                          fail['format'], fail['s_req'], fail['s_cap'],
                          _RTOL_EXP_GAIN)
            logging.debug('e_req: %d, e_cap: %d, RTOL: %.2f',
                          fail['e_req'], fail['e_cap'], _RTOL_EXP_GAIN)

        # PASS/FAIL
        if e_failed:
          raise AssertionError(f'Exposure fails: {e_failed}')
        if s_failed:
          raise AssertionError(f'Sensitivity fails: {s_failed}')
        if r_failed:
          raise AssertionError(f'Sensitivity range FAILs: {r_failed}')


if __name__ == '__main__':
  test_runner.main()
