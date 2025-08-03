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

import errno
import fcntl
import glob
import logging
import mmap
import os
import time
import v4l2

_DEVICE_NAME = 'android' # TODO b/277159494
_TEST_DURATION_SECONDS = 10
_WAIT_MS = 10000  # 10 seconds
_REQUEST_BUFFER_COUNT = 10
_VIDEO_DEVICES_PATH = '/dev/video*'


def v4l2_fourcc_to_str(fourcc):
  return ''.join([chr((fourcc >> 8 * i) & 0xFF) for i in range(4)])


def initialize_device_path():
  """Returns the device path of the device to be tested.

  Returns:
    Device path, /dev/video*, of device to be tested
  """
  device_path = ''
  selected_device = False

  video_devices = glob.glob(_VIDEO_DEVICES_PATH)

  for current_device_path in video_devices:
    try:
      video_device = os.open(current_device_path, os.O_RDWR | os.O_NONBLOCK)
      caps = v4l2.v4l2_capability()
      ioctl_retry_error(video_device, v4l2.VIDIOC_QUERYCAP,
                        caps, OSError, errno.EBUSY)

      if (_DEVICE_NAME in caps.card.lower().decode('utf-8') and
          not selected_device and
          caps.capabilities & v4l2.V4L2_CAP_VIDEO_CAPTURE):
        # Devices can mount multiple nodes at /dev/video*
        # Check for one that is used for capturing by finding
        # if formats can be retrieved from it
        while True:
          try:
            fmtdesc = v4l2.v4l2_fmtdesc()
            fmtdesc.type = v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE
            ioctl_retry_error(video_device, v4l2.VIDIOC_ENUM_FMT,
                              fmtdesc, OSError, errno.EBUSY)
          except OSError:
            break
          else:
            selected_device = True
            device_path = current_device_path
            break

      os.close(video_device)
    except OSError:
      pass

  return device_path


def initialize_formats_and_resolutions(video_device):
  """Gets a list of the supported formats, resolutions and frame rates for the device.

  Args:
    video_device: Device to be checked

  Returns:
    List of formats, resolutions, and frame rates:
      [ (Format (fmtdesc), [ (Resolution (frmsize),
          [ FrameRates (v4l2_frmivalenum) ]) ]) ]
  """
  # [(Format (fmtdesc),[(Resolution(frmsize),[FrameRates(v4l2_frmivalenum)])])]
  formats_and_resolutions = []

  # Retrieve supported formats
  format_index = 0
  while True:
    try:
      fmtdesc = v4l2.v4l2_fmtdesc()
      fmtdesc.type = v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE
      fmtdesc.index = format_index
      ioctl_retry_error(video_device, v4l2.VIDIOC_ENUM_FMT,
                        fmtdesc, OSError, errno.EBUSY)
    except OSError:
      break
    else:
      formats_and_resolutions.append((fmtdesc, []))
      format_index += 1

  # Use the found formats to retrieve the supported
  # resolutions per format
  for index, elem in enumerate(formats_and_resolutions):
    fmtdesc = elem[0]
    frmsize_index = 0

    while True:
      try:
        frmsize = v4l2.v4l2_frmsizeenum()
        frmsize.pixel_format = fmtdesc.pixelformat
        frmsize.index = frmsize_index
        ioctl_retry_error(video_device, v4l2.VIDIOC_ENUM_FRAMESIZES,
                          frmsize, OSError, errno.EBUSY)
      except OSError:
        break
      else:
        if frmsize.type == v4l2.V4L2_FRMSIZE_TYPE_DISCRETE:
          formats_and_resolutions[index][1].append((frmsize, []))
        frmsize_index += 1

  # Get advertised frame rates supported per format and resolution
  for format_index, elem in enumerate(formats_and_resolutions):
    fmtdesc = elem[0]
    frmsize_list = elem[1]

    for frmsize_index, frmsize_elem in enumerate(frmsize_list):
      curr_frmsize = frmsize_elem[0]
      frmival_index = 0
      while True:
        try:
          frmivalenum = v4l2.v4l2_frmivalenum()
          frmivalenum.index = frmival_index
          frmivalenum.pixel_format = fmtdesc.pixelformat
          frmivalenum.width = curr_frmsize.discrete.width
          frmivalenum.height = curr_frmsize.discrete.height
          ioctl_retry_error(video_device, v4l2.VIDIOC_ENUM_FRAMEINTERVALS,
                            frmivalenum, OSError, errno.EBUSY)
        except OSError:
          break
        else:
          formats_and_resolutions[format_index][1][
              frmsize_index][1].append(frmivalenum)
          frmival_index += 1

  return formats_and_resolutions


def print_formats_and_resolutions(formats_and_resolutions):
  """Helper function to print out device capabilities for debugging.

  Args:
    formats_and_resolutions: List to be printed
  """
  for elem in formats_and_resolutions:
    fmtdesc = elem[0]
    print(f"""Format - {fmtdesc.description},
        {fmtdesc.pixelformat} ({v4l2_fourcc_to_str(fmtdesc.pixelformat)})""")
    frmsize_list = elem[1]
    for frmsize_elem in frmsize_list:
      frmsize = frmsize_elem[0]
      print(f'-Resolution: {frmsize.discrete.width}x{frmsize.discrete.height}')
      frmivalenum_list = frmsize_elem[1]
      for frmivalenum in frmivalenum_list:
        print(f"""\t{fmtdesc.description} ({fmtdesc.pixelformat}),
            {frmivalenum.discrete.denominator / frmivalenum.discrete.numerator}
            fps""")


def ioctl_retry_error(video_device, request, arg, error, errno_code):
  """Adds wait check for specified ioctl call.

  Args:
    video_device: the device the ioctl call will interface with
    request: request for the ioctl call
    arg: arguments for ioctl
    error: the error to be catched and waited on
    errno_code: errno code of error to be waited on
  """
  wait_time = _WAIT_MS
  while True:
    try:
      fcntl.ioctl(video_device, request, arg)
      break
    except error as e:
      # if the error is a blocking I/O error, wait a short time and try again
      if e.errno == errno_code and wait_time >= 0:
        time.sleep(0.01)  # wait for 10 milliseconds
        wait_time -= 10
        continue
      else:
        raise  # otherwise, re-raise the exception


def setup_for_test_fps(video_device, formats_and_resolutions):
  """Sets up and calls fps test for device.

  Args:
    video_device: device to be tested
    formats_and_resolutions: device capabilities to be tested

  Returns:
    List of fps test results with expected fps and actual tested fps
      [ (Expected, Actual )]
  """
  res = []
  for elem in formats_and_resolutions:
    fmtdesc = elem[0]

    fmt = v4l2.v4l2_format()
    fmt.type = v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE
    fmt.fmt.pix.pixelformat = fmtdesc.pixelformat

    frmsize_list = elem[1]
    for frmsize_elem in frmsize_list:
      frmsize = frmsize_elem[0]
      fmt.fmt.pix.width = frmsize.discrete.width
      fmt.fmt.pix.height = frmsize.discrete.height

      ioctl_retry_error(video_device, v4l2.VIDIOC_S_FMT, fmt,
                        OSError, errno.EBUSY)

      ioctl_retry_error(video_device, v4l2.VIDIOC_G_FMT, fmt,
                        OSError, errno.EBUSY)

      frmivalenum_list = frmsize_elem[1]
      for frmivalenum_elem in frmivalenum_list:
        streamparm = v4l2.v4l2_streamparm()
        streamparm.type = v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE
        streamparm.parm.capture.timeperframe.numerator = (
            frmivalenum_elem.discrete.numerator)
        streamparm.parm.capture.timeperframe.denominator = (
            frmivalenum_elem.discrete.denominator)
        ioctl_retry_error(video_device, v4l2.VIDIOC_S_PARM, streamparm,
                          OSError, errno.EBUSY)

        res.append((frmivalenum_elem.discrete.denominator,
                    test_fps(video_device,
                             frmivalenum_elem.discrete.denominator)))
  return res


def test_fps(video_device, fps):
  """Runs fps test.

  Args:
    video_device: device to be tested
    fps: fps being tested

  Returns:
    Actual fps achieved from device
  """
  # Request buffers
  req = v4l2.v4l2_requestbuffers()
  req.count = _REQUEST_BUFFER_COUNT
  req.type = v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE
  req.memory = v4l2.V4L2_MEMORY_MMAP

  ioctl_retry_error(video_device, v4l2.VIDIOC_REQBUFS, req,
                    OSError, errno.EBUSY)

  buffers = []
  for i in range(req.count):
    buf = v4l2.v4l2_buffer()
    buf.type = v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE
    buf.memory = v4l2.V4L2_MEMORY_MMAP
    buf.index = i

    ioctl_retry_error(video_device, v4l2.VIDIOC_QUERYBUF, buf,
                      OSError, errno.EBUSY)

    buf.buffer = mmap.mmap(video_device,
                           buf.length, mmap.PROT_READ,
                           mmap.MAP_SHARED, offset=buf.m.offset)
    buffers.append(buf)
    ioctl_retry_error(video_device, v4l2.VIDIOC_QBUF, buf,
                      OSError, errno.EBUSY)

  # Stream on
  buf_type = v4l2.v4l2_buf_type(v4l2.V4L2_BUF_TYPE_VIDEO_CAPTURE)
  ioctl_retry_error(video_device, v4l2.VIDIOC_STREAMON, buf_type,
                    OSError, errno.EBUSY)

  # Test FPS
  num_frames = fps * _TEST_DURATION_SECONDS
  start_time = time.time()

  for x in range(num_frames):
    buf = buffers[x % _REQUEST_BUFFER_COUNT]
    ioctl_retry_error(video_device, v4l2.VIDIOC_DQBUF, buf,
                      BlockingIOError, errno.EWOULDBLOCK)
    ioctl_retry_error(video_device, v4l2.VIDIOC_QBUF, buf,
                      OSError, errno.EBUSY)

  end_time = time.time()
  elapsed_time = end_time - start_time
  fps_res = num_frames / elapsed_time

  # Stream off and clean up
  ioctl_retry_error(video_device, v4l2.VIDIOC_STREAMOFF, buf_type,
                    OSError, errno.EBUSY)
  req.count = 0
  ioctl_retry_error(video_device, v4l2.VIDIOC_REQBUFS, req,
                    OSError, errno.EBUSY)

  for buf in buffers:
    buf.buffer.close()

  return fps_res


def main():
  # Open the webcam device
  device_path = initialize_device_path()
  if not device_path:
    logging.error('Supported device not found!')
    return []

  try:
    video_device = os.open(device_path, os.O_RDWR | os.O_NONBLOCK)
  except Exception as e:
    print(f'Error: failed to open device {device_path}: error {e}')
    return []

  formats_and_resolutions = initialize_formats_and_resolutions(video_device)
  if not formats_and_resolutions:
    logging.error('Error retrieving formats and resolutions')
    return []

  res = setup_for_test_fps(video_device, formats_and_resolutions)

  os.close(video_device)

  return res

if __name__ == '__main__':
  main()
