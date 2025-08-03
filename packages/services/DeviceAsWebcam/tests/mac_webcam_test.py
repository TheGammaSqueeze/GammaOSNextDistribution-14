# Copyright 2023 The Android Open Source Project
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
"""Verifies advertised FPS from device as webcam."""

import logging
import time

import AVFoundation
import Cocoa
from libdispatch import dispatch_queue_create
import objc


objc.loadBundle('AVFoundation',
                bundle_path=objc.pathForFramework('AVFoundation.framework'),
                module_globals=globals())

_TOTAL_NUM_FRAMES = 0
_DEVICE_NAME = 'android' # TODO b/277159494
_FPS_TEST_DURATION = 10  # seconds


class SampleBufferDelegate(Cocoa.NSObject):
  """Notified upon every frame. Used to help calculate FPS.
  """

  def captureOutput_didOutputSampleBuffer_fromConnection_(self,
                                                          output,
                                                          sample_buffer,
                                                          connection):
    global _TOTAL_NUM_FRAMES

    if sample_buffer:
      _TOTAL_NUM_FRAMES += 1


def initialize_device():
  """Initializes android webcam device.

  Returns:
      Returns the device if an android device is found, None otherwise
  """
  devices = AVFoundation.AVCaptureDevice.devices()

  res_device = None
  for device in devices:
    if (device.hasMediaType_(AVFoundation.AVMediaTypeVideo) and
        (_DEVICE_NAME in device.localizedName().lower())):
      res_device = device
      logging.info('Using webcam %s', res_device.localizedName())
      break

  return res_device


def initialize_formats_and_resolutions(device):
  """Initializes list of device advertised formats, resolutions and FPS.

  Args:
      device: the device from which the info will be retrieved

  Returns:
      Returns a list of the formats, resolutions, and frame rates
      [ (format, [resolution, [frame rates] ] )]
  """
  supported_formats = device.formats()
  formats_and_resolutions = []

  for format_index, frmt in enumerate(supported_formats):
    formats_and_resolutions.append((frmt, []))
    frame_rate_ranges = frmt.videoSupportedFrameRateRanges()

    for frame_rate_range in frame_rate_ranges:
      min_frame_rate = frame_rate_range.minFrameRate()
      max_frame_rate = frame_rate_range.maxFrameRate()
      default_frame_rate = (
          device.activeVideoMinFrameDuration().timescale /
          device.activeVideoMinFrameDuration().value)

      formats_and_resolutions[format_index][1].append(min_frame_rate + 1)
      formats_and_resolutions[format_index][1].append(max_frame_rate)
      formats_and_resolutions[format_index][1].append(default_frame_rate)

  return formats_and_resolutions


def setup_for_test_fps(device, supported_formats_and_resolutions):
  """Configures device with format, resolution, and FPS to be tested.

  Args:
      device: device under test
      supported_formats_and_resolutions: list containing supported device
          configurations

  Returns:
      Returns a list of tuples, where the first element is the frame
      rate that was being tested and the second element is the actual fps
  """
  res = []

  delegate = SampleBufferDelegate.alloc().init()
  session = AVFoundation.AVCaptureSession.alloc().init()

  input_for_session = (
      AVFoundation.AVCaptureDeviceInput.deviceInputWithDevice_error_(device,
                                                                     None))
  session.addInput_(input_for_session[0])

  video_output = AVFoundation.AVCaptureVideoDataOutput.alloc().init()
  queue = dispatch_queue_create(b'1', None)
  video_output.setSampleBufferDelegate_queue_(delegate, queue)
  session.addOutput_(video_output)

  session.startRunning()

  for elem in supported_formats_and_resolutions:
    frmt = elem[0]
    frame_rates = elem[1]

    for frame_rate in frame_rates:
      global _TOTAL_NUM_FRAMES
      _TOTAL_NUM_FRAMES = 0  # reset total num frames for every test
      device.lockForConfiguration_(None)
      device.setActiveFormat_(frmt)
      device.setActiveVideoMinFrameDuration_(
          AVFoundation.CMTimeMake(1, frame_rate))
      device.setActiveVideoMaxFrameDuration_(
          AVFoundation.CMTimeMake(1, frame_rate))
      device.unlockForConfiguration()

      res.append((frame_rate, test_fps()))

  session.removeInput_(input_for_session)
  session.removeOutput_(video_output)
  session.stopRunning()
  input_for_session[0].release()
  video_output.release()
  return res


def test_fps():
  """Tests and returns test estimated fps.

  Returns:
      Test estimated fps
  """
  start_time = time.time()
  time.sleep(_FPS_TEST_DURATION)
  end_time = time.time()
  fps = _TOTAL_NUM_FRAMES / (end_time - start_time)
  return fps


def main():
  device = initialize_device()

  if not device:
    logging.error('Supported device not found!')
    return []

  supported_formats_and_resolutions = initialize_formats_and_resolutions(device)

  if not supported_formats_and_resolutions:
    logging.error('Error retrieving formats and resolutions')
    return []

  res = setup_for_test_fps(device, supported_formats_and_resolutions)

  return res


if __name__ == '__main__':
  main()
