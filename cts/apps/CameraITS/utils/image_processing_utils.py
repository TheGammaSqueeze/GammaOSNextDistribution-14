# Copyright 2013 The Android Open Source Project
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
"""Image processing utility functions."""


import copy
import io
import logging
import math
import os
import sys

import capture_request_utils
import colour
import error_util
import noise_model_constants
import numpy
from PIL import Image
from PIL import ImageCms

# The matrix is from JFIF spec
DEFAULT_YUV_TO_RGB_CCM = numpy.matrix([[1.000, 0.000, 1.402],
                                       [1.000, -0.344, -0.714],
                                       [1.000, 1.772, 0.000]])

DEFAULT_YUV_OFFSETS = numpy.array([0, 128, 128])
MAX_LUT_SIZE = 65536
DEFAULT_GAMMA_LUT = numpy.array([
    math.floor((MAX_LUT_SIZE-1) * math.pow(i/(MAX_LUT_SIZE-1), 1/2.2) + 0.5)
    for i in range(MAX_LUT_SIZE)])
NUM_TRIES = 2
NUM_FRAMES = 4
TEST_IMG_DIR = os.path.join(os.environ['CAMERA_ITS_TOP'], 'test_images')

# Expected adapted primaries in ICC profile per color space
EXPECTED_RX_P3 = 0.682
EXPECTED_RY_P3 = 0.319
EXPECTED_GX_P3 = 0.285
EXPECTED_GY_P3 = 0.675
EXPECTED_BX_P3 = 0.156
EXPECTED_BY_P3 = 0.066

EXPECTED_RX_SRGB = 0.648
EXPECTED_RY_SRGB = 0.331
EXPECTED_GX_SRGB = 0.321
EXPECTED_GY_SRGB = 0.598
EXPECTED_BX_SRGB = 0.156
EXPECTED_BY_SRGB = 0.066

# Chosen empirically - tolerance for the point in triangle test for colorspace
# chromaticities
COLORSPACE_TRIANGLE_AREA_TOL = 0.00028


def capture_scene_image(cam, props, name_with_log_path):
  """Take a picture of the scene on test FAIL."""
  req = capture_request_utils.auto_capture_request()
  img = convert_capture_to_rgb_image(
      cam.do_capture(req, cam.CAP_YUV), props=props)
  write_image(img, f'{name_with_log_path}_scene.jpg', True)


def convert_image_to_uint8(image):
  image *= 255
  return image.astype(numpy.uint8)


def assert_props_is_not_none(props):
  if not props:
    raise AssertionError('props is None')


def convert_capture_to_rgb_image(cap,
                                 props=None,
                                 apply_ccm_raw_to_rgb=True):
  """Convert a captured image object to a RGB image.

  Args:
     cap: A capture object as returned by its_session_utils.do_capture.
     props: (Optional) camera properties object (of static values);
            required for processing raw images.
     apply_ccm_raw_to_rgb: (Optional) boolean to apply color correction matrix.

  Returns:
        RGB float-3 image array, with pixel values in [0.0, 1.0].
  """
  w = cap['width']
  h = cap['height']
  if cap['format'] == 'raw10' or cap['format'] == 'raw10QuadBayer':
    assert_props_is_not_none(props)
    is_quad_bayer = cap['format'] == 'raw10QuadBayer'
    cap = unpack_raw10_capture(cap, is_quad_bayer)

  if cap['format'] == 'raw12':
    assert_props_is_not_none(props)
    cap = unpack_raw12_capture(cap)

  if cap['format'] == 'yuv':
    y = cap['data'][0: w * h]
    u = cap['data'][w * h: w * h * 5//4]
    v = cap['data'][w * h * 5//4: w * h * 6//4]
    return convert_yuv420_planar_to_rgb_image(y, u, v, w, h)
  elif cap['format'] == 'jpeg' or cap['format'] == 'jpeg_r':
    return decompress_jpeg_to_rgb_image(cap['data'])
  elif (cap['format'] in ('raw', 'rawQuadBayer') or
        cap['format'] in noise_model_constants.VALID_RAW_STATS_FORMATS):
    assert_props_is_not_none(props)
    r, gr, gb, b = convert_capture_to_planes(cap, props)
    return convert_raw_to_rgb_image(
        r, gr, gb, b, props, cap['metadata'], apply_ccm_raw_to_rgb)
  elif cap['format'] == 'y8':
    y = cap['data'][0: w * h]
    return convert_y8_to_rgb_image(y, w, h)
  else:
    raise error_util.CameraItsError(f"Invalid format {cap['format']}")


def unpack_raw10_capture(cap, is_quad_bayer=False):
  """Unpack a raw-10 capture to a raw-16 capture.

  Args:
    cap: A raw-10 capture object.
    is_quad_bayer: Boolean flag for Bayer or Quad Bayer capture.

  Returns:
    New capture object with raw-16 data.
  """
  # Data is packed as 4x10b pixels in 5 bytes, with the first 4 bytes holding
  # the MSBs of the pixels, and the 5th byte holding 4x2b LSBs.
  w, h = cap['width'], cap['height']
  if w % 4 != 0:
    raise error_util.CameraItsError('Invalid raw-10 buffer width')
  cap = copy.deepcopy(cap)
  cap['data'] = unpack_raw10_image(cap['data'].reshape(h, w * 5 // 4))
  cap['format'] = 'rawQuadBayer' if is_quad_bayer else 'raw'
  return cap


def unpack_raw10_image(img):
  """Unpack a raw-10 image to a raw-16 image.

  Output image will have the 10 LSBs filled in each 16b word, and the 6 MSBs
  will be set to zero.

  Args:
    img: A raw-10 image, as a uint8 numpy array.

  Returns:
    Image as a uint16 numpy array, with all row padding stripped.
  """
  if img.shape[1] % 5 != 0:
    raise error_util.CameraItsError('Invalid raw-10 buffer width')
  w = img.shape[1] * 4 // 5
  h = img.shape[0]
  # Cut out the 4x8b MSBs and shift to bits [9:2] in 16b words.
  msbs = numpy.delete(img, numpy.s_[4::5], 1)
  msbs = msbs.astype(numpy.uint16)
  msbs = numpy.left_shift(msbs, 2)
  msbs = msbs.reshape(h, w)
  # Cut out the 4x2b LSBs and put each in bits [1:0] of their own 8b words.
  lsbs = img[::, 4::5].reshape(h, w // 4)
  lsbs = numpy.right_shift(
      numpy.packbits(numpy.unpackbits(lsbs).reshape((h, w // 4, 4, 2)), 3), 6)
  # Pair the LSB bits group to 0th pixel instead of 3rd pixel
  lsbs = lsbs.reshape(h, w // 4, 4)[:, :, ::-1]
  lsbs = lsbs.reshape(h, w)
  # Fuse the MSBs and LSBs back together
  img16 = numpy.bitwise_or(msbs, lsbs).reshape(h, w)
  return img16


def unpack_raw12_capture(cap):
  """Unpack a raw-12 capture to a raw-16 capture.

  Args:
    cap: A raw-12 capture object.

  Returns:
     New capture object with raw-16 data.
  """
  # Data is packed as 4x10b pixels in 5 bytes, with the first 4 bytes holding
  # the MSBs of the pixels, and the 5th byte holding 4x2b LSBs.
  w, h = cap['width'], cap['height']
  if w % 2 != 0:
    raise error_util.CameraItsError('Invalid raw-12 buffer width')
  cap = copy.deepcopy(cap)
  cap['data'] = unpack_raw12_image(cap['data'].reshape(h, w * 3 // 2))
  cap['format'] = 'raw'
  return cap


def unpack_raw12_image(img):
  """Unpack a raw-12 image to a raw-16 image.

  Output image will have the 12 LSBs filled in each 16b word, and the 4 MSBs
  will be set to zero.

  Args:
   img: A raw-12 image, as a uint8 numpy array.

  Returns:
    Image as a uint16 numpy array, with all row padding stripped.
  """
  if img.shape[1] % 3 != 0:
    raise error_util.CameraItsError('Invalid raw-12 buffer width')
  w = img.shape[1] * 2 // 3
  h = img.shape[0]
  # Cut out the 2x8b MSBs and shift to bits [11:4] in 16b words.
  msbs = numpy.delete(img, numpy.s_[2::3], 1)
  msbs = msbs.astype(numpy.uint16)
  msbs = numpy.left_shift(msbs, 4)
  msbs = msbs.reshape(h, w)
  # Cut out the 2x4b LSBs and put each in bits [3:0] of their own 8b words.
  lsbs = img[::, 2::3].reshape(h, w // 2)
  lsbs = numpy.right_shift(
      numpy.packbits(numpy.unpackbits(lsbs).reshape((h, w // 2, 2, 4)), 3), 4)
  # Pair the LSB bits group to pixel 0 instead of pixel 1
  lsbs = lsbs.reshape(h, w // 2, 2)[:, :, ::-1]
  lsbs = lsbs.reshape(h, w)
  # Fuse the MSBs and LSBs back together
  img16 = numpy.bitwise_or(msbs, lsbs).reshape(h, w)
  return img16


def convert_yuv420_planar_to_rgb_image(y_plane, u_plane, v_plane,
                                       w, h,
                                       ccm_yuv_to_rgb=DEFAULT_YUV_TO_RGB_CCM,
                                       yuv_off=DEFAULT_YUV_OFFSETS):
  """Convert a YUV420 8-bit planar image to an RGB image.

  Args:
    y_plane: The packed 8-bit Y plane.
    u_plane: The packed 8-bit U plane.
    v_plane: The packed 8-bit V plane.
    w: The width of the image.
    h: The height of the image.
    ccm_yuv_to_rgb: (Optional) the 3x3 CCM to convert from YUV to RGB.
    yuv_off: (Optional) offsets to subtract from each of Y,U,V values.

  Returns:
    RGB float-3 image array, with pixel values in [0.0, 1.0].
  """
  y = numpy.subtract(y_plane, yuv_off[0])
  u = numpy.subtract(u_plane, yuv_off[1]).view(numpy.int8)
  v = numpy.subtract(v_plane, yuv_off[2]).view(numpy.int8)
  u = u.reshape(h // 2, w // 2).repeat(2, axis=1).repeat(2, axis=0)
  v = v.reshape(h // 2, w // 2).repeat(2, axis=1).repeat(2, axis=0)
  yuv = numpy.dstack([y, u.reshape(w * h), v.reshape(w * h)])
  flt = numpy.empty([h, w, 3], dtype=numpy.float32)
  flt.reshape(w * h * 3)[:] = yuv.reshape(h * w * 3)[:]
  flt = numpy.dot(flt.reshape(w * h, 3), ccm_yuv_to_rgb.T).clip(0, 255)
  rgb = numpy.empty([h, w, 3], dtype=numpy.uint8)
  rgb.reshape(w * h * 3)[:] = flt.reshape(w * h * 3)[:]
  return rgb.astype(numpy.float32) / 255.0


def decompress_jpeg_to_rgb_image(jpeg_buffer):
  """Decompress a JPEG-compressed image, returning as an RGB image.

  Args:
    jpeg_buffer: The JPEG stream.

  Returns:
     A numpy array for the RGB image, with pixels in [0,1].
  """
  img = Image.open(io.BytesIO(jpeg_buffer))
  w = img.size[0]
  h = img.size[1]
  return numpy.array(img).reshape((h, w, 3)) / 255.0


def decompress_jpeg_to_yuv_image(jpeg_buffer):
  """Decompress a JPEG-compressed image, returning as a YUV image.

  Args:
    jpeg_buffer: The JPEG stream.

  Returns:
     A numpy array for the YUV image, with pixels in [0,1].
  """
  img = Image.open(io.BytesIO(jpeg_buffer))
  img = img.convert('YCbCr')
  w = img.size[0]
  h = img.size[1]
  return numpy.array(img).reshape((h, w, 3)) / 255.0


def extract_luma_from_patch(cap, patch_x, patch_y, patch_w, patch_h):
  """Extract luma from capture."""
  y, _, _ = convert_capture_to_planes(cap)
  patch = get_image_patch(y, patch_x, patch_y, patch_w, patch_h)
  luma = compute_image_means(patch)[0]
  return luma


def convert_image_to_numpy_array(image_path):
  """Converts image at image_path to numpy array and returns the array.

  Args:
    image_path: file path

  Returns:
    numpy array
  """
  if not os.path.exists(image_path):
    raise AssertionError(f'{image_path} does not exist.')
  image = Image.open(image_path)
  return numpy.array(image)


def _convert_quad_bayer_img_to_bayer_channels(quad_bayer_img, props=None):
  """Convert a quad Bayer image to the Bayer image channels.

  Args:
      quad_bayer_img: The quad Bayer image.
      props: The camera properties.

  Returns:
      A list of reordered standard Bayer channels of the Bayer image.
  """
  height, width, num_channels = quad_bayer_img.shape

  if num_channels != noise_model_constants.NUM_QUAD_BAYER_CHANNELS:
    raise AssertionError(
        'The number of channels in the quad Bayer image must be '
        f'{noise_model_constants.NUM_QUAD_BAYER_CHANNELS}.'
    )
  quad_bayer_cfa_order = get_canonical_cfa_order(props, is_quad_bayer=True)

  # Bayer channels are in the order of R, Gr, Gb and B.
  bayer_channels = []
  for ch in range(4):
    channel_img = numpy.zeros(shape=(height, width), dtype='<f')
    # Average every four quad Bayer channels into a standard Bayer channel.
    for i in quad_bayer_cfa_order[4 * ch: 4 * (ch + 1)]:
      channel_img[:, :] += quad_bayer_img[:, :, i]
    bayer_channels.append(channel_img / 4)
  return bayer_channels


def subsample(image, num_channels=4):
  """Subsamples the image to separate its color channels.

  Args:
    image:        2-D numpy array of raw image.
    num_channels: The number of channels in the image.

  Returns:
    3-D numpy image with each channel separated.
  """
  if num_channels not in noise_model_constants.VALID_NUM_CHANNELS:
    raise error_util.CameraItsError(
        f'Invalid number of channels {num_channels}, which should be in '
        f'{noise_model_constants.VALID_NUM_CHANNELS}.'
    )

  size_h, size_v = image.shape[1], image.shape[0]

  # Subsample step size, which is the horizontal or vertical pixel interval
  # between two adjacent pixels of the same channel.
  stride = int(numpy.sqrt(num_channels))
  subsample_img = lambda img, i, h, v, s: img[i // s: v: s, i % s: h: s]
  channel_img = numpy.empty((
      image.shape[0] // stride,
      image.shape[1] // stride,
      num_channels,
  ))

  for i in range(num_channels):
    sub_img = subsample_img(image, i, size_h, size_v, stride)
    channel_img[:, :, i] = sub_img

  return channel_img


def convert_capture_to_planes(cap, props=None):
  """Convert a captured image object to separate image planes.

  Decompose an image into multiple images, corresponding to different planes.

  For YUV420 captures ("yuv"):
        Returns Y,U,V planes, where the Y plane is full-res and the U,V planes
        are each 1/2 x 1/2 of the full res.

    For standard Bayer or quad Bayer captures ("raw", "raw10", "raw12",
    "rawQuadBayer", "rawStats", "rawQuadBayerStats", "raw10QuadBayer",
    "raw10Stats", "raw10QuadBayerStats"):
        Returns planes in the order R, Gr, Gb, B, regardless of the Bayer
        pattern layout.
        For full-res raw images ("raw", "rawQuadBayer", "raw10",
        "raw10QuadBayer", "raw12"), each plane is 1/2 x 1/2 of the full res.
        For standard Bayer stats images, the mean image is returned.
        For quad Bayer stats images, the average mean image is returned.

    For JPEG captures ("jpeg"):
        Returns R,G,B full-res planes.

  Args:
    cap: A capture object as returned by its_session_utils.do_capture.
    props: (Optional) camera properties object (of static values);
            required for processing raw images.

  Returns:
    A tuple of float numpy arrays (one per plane), consisting of pixel values
    in the range [0.0, 1.0].
  """
  w = cap['width']
  h = cap['height']
  if cap['format'] in ('raw10', 'raw10QuadBayer'):
    assert_props_is_not_none(props)
    is_quad_bayer = cap['format'] == 'raw10QuadBayer'
    cap = unpack_raw10_capture(cap, is_quad_bayer)

  if cap['format'] == 'raw12':
    assert_props_is_not_none(props)
    cap = unpack_raw12_capture(cap)
  if cap['format'] == 'yuv':
    y = cap['data'][0:w * h]
    u = cap['data'][w * h:w * h * 5 // 4]
    v = cap['data'][w * h * 5 // 4:w * h * 6 // 4]
    return ((y.astype(numpy.float32) / 255.0).reshape(h, w, 1),
            (u.astype(numpy.float32) / 255.0).reshape(h // 2, w // 2, 1),
            (v.astype(numpy.float32) / 255.0).reshape(h // 2, w // 2, 1))
  elif cap['format'] == 'jpeg':
    rgb = decompress_jpeg_to_rgb_image(cap['data']).reshape(w * h * 3)
    return (rgb[::3].reshape(h, w, 1), rgb[1::3].reshape(h, w, 1),
            rgb[2::3].reshape(h, w, 1))
  elif cap['format'] in ('raw', 'rawQuadBayer'):
    assert_props_is_not_none(props)
    is_quad_bayer = 'QuadBayer' in cap['format']
    white_level = float(props['android.sensor.info.whiteLevel'])
    img = numpy.ndarray(
        shape=(h * w,), dtype='<u2', buffer=cap['data'][0:w * h * 2])
    img = img.astype(numpy.float32).reshape(h, w) / white_level
    if is_quad_bayer:
      pixel_array_size = props.get(
          'android.sensor.info.pixelArraySizeMaximumResolution'
      )
      active_array_size = props.get(
          'android.sensor.info.preCorrectionActiveArraySizeMaximumResolution'
      )
    else:
      pixel_array_size = props.get('android.sensor.info.pixelArraySize')
      active_array_size = props.get(
          'android.sensor.info.preCorrectionActiveArraySize'
      )
    # Crop the raw image to the active array region.
    if pixel_array_size and active_array_size:
      # Note that the Rect class is defined such that the left,top values
      # are "inside" while the right,bottom values are "outside"; that is,
      # it's inclusive of the top,left sides only. So, the width is
      # computed as right-left, rather than right-left+1, etc.
      wfull = pixel_array_size['width']
      hfull = pixel_array_size['height']
      xcrop = active_array_size['left']
      ycrop = active_array_size['top']
      wcrop = active_array_size['right'] - xcrop
      hcrop = active_array_size['bottom'] - ycrop
      if not wfull >= wcrop >= 0:
        raise AssertionError(f'wcrop: {wcrop} not in wfull: {wfull}')
      if not hfull >= hcrop >= 0:
        raise AssertionError(f'hcrop: {hcrop} not in hfull: {hfull}')
      if not wfull - wcrop >= xcrop >= 0:
        raise AssertionError(f'xcrop: {xcrop} not in wfull-crop: {wfull-wcrop}')
      if not hfull - hcrop >= ycrop >= 0:
        raise AssertionError(f'ycrop: {ycrop} not in hfull-crop: {hfull-hcrop}')
      if w == wfull and h == hfull:
        # Crop needed; extract the center region.
        img = img[ycrop:ycrop + hcrop, xcrop:xcrop + wcrop]
        w = wcrop
        h = hcrop
      elif w == wcrop and h == hcrop:
        logging.debug('Image is already cropped. No cropping needed.')
      else:
        raise error_util.CameraItsError('Invalid image size metadata')

    idxs = get_canonical_cfa_order(props, is_quad_bayer)
    if is_quad_bayer:
      # Subsample image array based on the color map.
      quad_bayer_img = subsample(
          img, noise_model_constants.NUM_QUAD_BAYER_CHANNELS
      )
      bayer_channels = _convert_quad_bayer_img_to_bayer_channels(
          quad_bayer_img, props
      )
      return bayer_channels
    else:
      # Separate the image planes.
      imgs = [
          img[::2].reshape(w * h // 2)[::2].reshape(h // 2, w // 2, 1),
          img[::2].reshape(w * h // 2)[1::2].reshape(h // 2, w // 2, 1),
          img[1::2].reshape(w * h // 2)[::2].reshape(h // 2, w // 2, 1),
          img[1::2].reshape(w * h // 2)[1::2].reshape(h // 2, w // 2, 1),
      ]
      return [imgs[i] for i in idxs]
  elif cap['format'] in (
      'rawStats',
      'raw10Stats',
      'rawQuadBayerStats',
      'raw10QuadBayerStats',
  ):
    assert_props_is_not_none(props)
    is_quad_bayer = 'QuadBayer' in cap['format']
    white_level = float(props['android.sensor.info.whiteLevel'])
    if is_quad_bayer:
      num_channels = noise_model_constants.NUM_QUAD_BAYER_CHANNELS
    else:
      num_channels = noise_model_constants.NUM_BAYER_CHANNELS
    mean_image, _ = unpack_rawstats_capture(cap, num_channels)
    if is_quad_bayer:
      bayer_channels = _convert_quad_bayer_img_to_bayer_channels(
          mean_image, props
      )
      bayer_channels = [
          bayer_channels[i] / white_level for i in range(len(bayer_channels))
      ]
      return bayer_channels
    else:
      # Standard Bayer canonical color channel indices.
      idxs = get_canonical_cfa_order(props, is_quad_bayer=False)
      # Normalizes the range to [0, 1] without subtracting the black level.
      return [mean_image[:, :, i] / white_level for i in idxs]
  else:
    raise error_util.CameraItsError(f"Invalid format {cap['format']}")


def downscale_image(img, f):
  """Shrink an image by a given integer factor.

  This function computes output pixel values by averaging over rectangular
  regions of the input image; it doesn't skip or sample pixels, and all input
  image pixels are evenly weighted.

  If the downscaling factor doesn't cleanly divide the width and/or height,
  then the remaining pixels on the right or bottom edge are discarded prior
  to the downscaling.

  Args:
    img: The input image as an ndarray.
    f: The downscaling factor, which should be an integer.

  Returns:
    The new (downscaled) image, as an ndarray.
  """
  h, w, chans = img.shape
  f = int(f)
  assert f >= 1
  h = (h//f)*f
  w = (w//f)*f
  img = img[0:h:, 0:w:, ::]
  chs = []
  for i in range(chans):
    ch = img.reshape(h*w*chans)[i::chans].reshape(h, w)
    ch = ch.reshape(h, w//f, f).mean(2).reshape(h, w//f)
    ch = ch.T.reshape(w//f, h//f, f).mean(2).T.reshape(h//f, w//f)
    chs.append(ch.reshape(h*w//(f*f)))
  img = numpy.vstack(chs).T.reshape(h//f, w//f, chans)
  return img


def convert_raw_to_rgb_image(r_plane, gr_plane, gb_plane, b_plane, props,
                             cap_res, apply_ccm_raw_to_rgb=True):
  """Convert a Bayer raw-16 image to an RGB image.

  Includes some extremely rudimentary demosaicking and color processing
  operations; the output of this function shouldn't be used for any image
  quality analysis.

  Args:
   r_plane:
   gr_plane:
   gb_plane:
   b_plane: Numpy arrays for each color plane
            in the Bayer image, with pixels in the [0.0, 1.0] range.
   props: Camera properties object.
   cap_res: Capture result (metadata) object.
   apply_ccm_raw_to_rgb: (Optional) boolean to apply color correction matrix.

  Returns:
   RGB float-3 image array, with pixel values in [0.0, 1.0]
  """
  # Values required for the RAW to RGB conversion.
  assert_props_is_not_none(props)
  white_level = float(props['android.sensor.info.whiteLevel'])
  gains = cap_res['android.colorCorrection.gains']
  ccm = cap_res['android.colorCorrection.transform']

  # Reorder black levels and gains to R,Gr,Gb,B, to match the order
  # of the planes.
  black_levels = get_black_levels(props, cap_res, is_quad_bayer=False)
  gains = get_gains_in_canonical_order(props, gains)

  # Convert CCM from rational to float, as numpy arrays.
  ccm = numpy.array(capture_request_utils.rational_to_float(ccm)).reshape(3, 3)

  # Need to scale the image back to the full [0,1] range after subtracting
  # the black level from each pixel.
  scale = white_level / (white_level - max(black_levels))

  # Three-channel black levels, normalized to [0,1] by white_level.
  black_levels = numpy.array(
      [b / white_level for b in [black_levels[i] for i in [0, 1, 3]]])

  # Three-channel gains.
  gains = numpy.array([gains[i] for i in [0, 1, 3]])

  h, w = r_plane.shape[:2]
  img = numpy.dstack([r_plane, (gr_plane + gb_plane) / 2.0, b_plane])
  img = (((img.reshape(h, w, 3) - black_levels) * scale) * gains).clip(0.0, 1.0)
  if apply_ccm_raw_to_rgb:
    img = numpy.dot(
        img.reshape(w * h, 3), ccm.T).reshape((h, w, 3)).clip(0.0, 1.0)
  return img


def convert_y8_to_rgb_image(y_plane, w, h):
  """Convert a Y 8-bit image to an RGB image.

  Args:
    y_plane: The packed 8-bit Y plane.
    w: The width of the image.
    h: The height of the image.

  Returns:
    RGB float-3 image array, with pixel values in [0.0, 1.0].
  """
  y3 = numpy.dstack([y_plane, y_plane, y_plane])
  rgb = numpy.empty([h, w, 3], dtype=numpy.uint8)
  rgb.reshape(w * h * 3)[:] = y3.reshape(w * h * 3)[:]
  return rgb.astype(numpy.float32) / 255.0


def write_image(img, fname, apply_gamma=False, is_yuv=False):
  """Save a float-3 numpy array image to a file.

  Supported formats: PNG, JPEG, and others; see PIL docs for more.

  Image can be 3-channel, which is interpreted as RGB or YUV, or can be
  1-channel, which is greyscale.

  Can optionally specify that the image should be gamma-encoded prior to
  writing it out; this should be done if the image contains linear pixel
  values, to make the image look "normal".

  Args:
   img: Numpy image array data.
   fname: Path of file to save to; the extension specifies the format.
   apply_gamma: (Optional) apply gamma to the image prior to writing it.
   is_yuv: Whether the image is in YUV format.
  """
  if apply_gamma:
    img = apply_lut_to_image(img, DEFAULT_GAMMA_LUT)
  (h, w, chans) = img.shape
  if chans == 3:
    if not is_yuv:
      Image.fromarray((img * 255.0).astype(numpy.uint8), 'RGB').save(fname)
    else:
      Image.fromarray((img * 255.0).astype(numpy.uint8), 'YCbCr').save(fname)
  elif chans == 1:
    img3 = (img * 255.0).astype(numpy.uint8).repeat(3).reshape(h, w, 3)
    Image.fromarray(img3, 'RGB').save(fname)
  else:
    raise error_util.CameraItsError('Unsupported image type')


def read_image(fname):
  """Read image function to match write_image() above."""
  return Image.open(fname)


def apply_lut_to_image(img, lut):
  """Applies a LUT to every pixel in a float image array.

  Internally converts to a 16b integer image, since the LUT can work with up
  to 16b->16b mappings (i.e. values in the range [0,65535]). The lut can also
  have fewer than 65536 entries, however it must be sized as a power of 2
  (and for smaller luts, the scale must match the bitdepth).

  For a 16b lut of 65536 entries, the operation performed is:

  lut[r * 65535] / 65535 -> r'
  lut[g * 65535] / 65535 -> g'
  lut[b * 65535] / 65535 -> b'

  For a 10b lut of 1024 entries, the operation becomes:

  lut[r * 1023] / 1023 -> r'
  lut[g * 1023] / 1023 -> g'
  lut[b * 1023] / 1023 -> b'

  Args:
    img: Numpy float image array, with pixel values in [0,1].
    lut: Numpy table encoding a LUT, mapping 16b integer values.

  Returns:
    Float image array after applying LUT to each pixel.
  """
  n = len(lut)
  if n <= 0 or n > MAX_LUT_SIZE or (n & (n - 1)) != 0:
    raise error_util.CameraItsError(f'Invalid arg LUT size: {n}')
  m = float(n - 1)
  return (lut[(img * m).astype(numpy.uint16)] / m).astype(numpy.float32)


def get_gains_in_canonical_order(props, gains):
  """Reorders the gains tuple to the canonical R,Gr,Gb,B order.

  Args:
    props: Camera properties object.
    gains: List of 4 values, in R,G_even,G_odd,B order.

  Returns:
    List of gains values, in R,Gr,Gb,B order.
  """
  cfa_pat = props['android.sensor.info.colorFilterArrangement']
  if cfa_pat in [0, 1]:
    # RGGB or GRBG, so G_even is Gr
    return gains
  elif cfa_pat in [2, 3]:
    # GBRG or BGGR, so G_even is Gb
    return [gains[0], gains[2], gains[1], gains[3]]
  else:
    raise error_util.CameraItsError('Not supported')


def get_black_levels(props, cap=None, is_quad_bayer=False):
  """Gets black levels to use for a given capture.

  Uses a dynamic value from the capture result if available, else falls back
  to the static global value in the camera characteristics.

  Args:
    props: The camera properties object.
    cap: A capture object.
    is_quad_bayer: Boolean flag for Bayer or Quad Bayer capture.

  Returns:
    A list of black level values reordered in canonical order.
  """
  if (cap is not None and
      'android.sensor.dynamicBlackLevel' in cap and
      cap['android.sensor.dynamicBlackLevel'] is not None):
    black_levels = cap['android.sensor.dynamicBlackLevel']
  else:
    black_levels = props['android.sensor.blackLevelPattern']

  idxs = get_canonical_cfa_order(props, is_quad_bayer)
  if is_quad_bayer:
    ordered_black_levels = [black_levels[i // 4] for i in idxs]
  else:
    ordered_black_levels = [black_levels[i] for i in idxs]
  return ordered_black_levels


def get_canonical_cfa_order(props, is_quad_bayer=False):
  """Returns a list of channel indices according to color filter arrangement.

  Color filter arrangement index is a integer ranging from 0 to 3, which maps
  the color filter arrangement in the following way.
    0: R, Gr, Gb, B,
    1: Gr, R, B, Gb,
    2: Gb, B, R, Gr,
    3: B, Gb, Gr, R.

  This function return a list of channel indices that can be used to reorder
  the stats data as the canonical order:
    (1) For standard Bayer: R, Gr, Gb, B.
    (2) For quad Bayer: R0, R1, R2, R3,
                        Gr0, Gr1, Gr2, Gr3,
                        Gb0, Gb1, Gb2, Gb3,
                        B0, B1, B2, B3.

  Args:
    props: Camera properties object.
    is_quad_bayer: Boolean flag for Bayer or Quad Bayer capture.

  Returns:
    A list of channel indices with values ranging from:
      (1) [0, 3] for standard Bayer,
      (2) [0, 15] for quad Bayer.
  """
  cfa_pat = props['android.sensor.info.colorFilterArrangement']
  if not 0 <= cfa_pat < 4:
    raise error_util.CameraItsError('Not supported')

  channel_indices = []
  if is_quad_bayer:
    color_map = noise_model_constants.QUAD_BAYER_COLOR_FILTER_MAP[cfa_pat]
    for ch in noise_model_constants.BAYER_COLORS:
      channel_indices.extend(color_map[ch])
  else:
    color_map = noise_model_constants.BAYER_COLOR_FILTER_MAP[cfa_pat]
    channel_indices = [
        color_map[ch] for ch in noise_model_constants.BAYER_COLORS
    ]
  return channel_indices


def unpack_rawstats_capture(cap, num_channels=4):
  """Unpacks a stats image capture to the mean and variance images.

  Args:
    cap: A capture object as returned by its_session_utils.do_capture.
    num_channels: The number of color channels in the stats image capture, which
      can be one of noise_model_constants.VALID_NUM_CHANNELS.

  Returns:
    Tuple (mean_image var_image) of float-4 images, with non-normalized
    pixel values computed from the RAW10/RAW16 images on the device
  """
  if cap['format'] not in noise_model_constants.VALID_RAW_STATS_FORMATS:
    raise AssertionError(f"Unsupported stats format: {cap['format']}")

  if num_channels not in noise_model_constants.VALID_NUM_CHANNELS:
    raise AssertionError(
        f'Unsupported number of channels {num_channels}, which should be in'
        f' {noise_model_constants.VALID_NUM_CHANNELS}.'
    )

  w = cap['width']
  h = cap['height']
  img = numpy.ndarray(
      shape=(2 * h * w * num_channels,), dtype='<f', buffer=cap['data']
  )
  analysis_image = img.reshape((2, h, w, num_channels))
  mean_image = analysis_image[0, :, :, :].reshape(h, w, num_channels)
  var_image = analysis_image[1, :, :, :].reshape(h, w, num_channels)
  return mean_image, var_image


def get_image_patch(img, xnorm, ynorm, wnorm, hnorm):
  """Get a patch (tile) of an image.

  Args:
   img: Numpy float image array, with pixel values in [0,1].
   xnorm:
   ynorm:
   wnorm:
   hnorm: Normalized (in [0,1]) coords for the tile.

  Returns:
     Numpy float image array of the patch.
  """
  hfull = img.shape[0]
  wfull = img.shape[1]
  xtile = int(math.ceil(xnorm * wfull))
  ytile = int(math.ceil(ynorm * hfull))
  wtile = int(math.floor(wnorm * wfull))
  htile = int(math.floor(hnorm * hfull))
  if len(img.shape) == 2:
    return img[ytile:ytile + htile, xtile:xtile + wtile].copy()
  else:
    return img[ytile:ytile + htile, xtile:xtile + wtile, :].copy()


def compute_image_means(img):
  """Calculate the mean of each color channel in the image.

  Args:
    img: Numpy float image array, with pixel values in [0,1].

  Returns:
     A list of mean values, one per color channel in the image.
  """
  means = []
  chans = img.shape[2]
  for i in range(chans):
    means.append(numpy.mean(img[:, :, i], dtype=numpy.float64))
  return means


def compute_image_variances(img):
  """Calculate the variance of each color channel in the image.

  Args:
    img: Numpy float image array, with pixel values in [0,1].

  Returns:
    A list of variance values, one per color channel in the image.
  """
  variances = []
  chans = img.shape[2]
  for i in range(chans):
    variances.append(numpy.var(img[:, :, i], dtype=numpy.float64))
  return variances


def compute_image_sharpness(img):
  """Calculate the sharpness of input image.

  Args:
    img: numpy float RGB/luma image array, with pixel values in [0,1].

  Returns:
    Sharpness estimation value based on the average of gradient magnitude.
    Larger value means the image is sharper.
  """
  chans = img.shape[2]
  if chans != 1 and chans != 3:
    raise AssertionError(f'Not RGB or MONO image! depth: {chans}')
  if chans == 1:
    luma = img[:, :, 0]
  else:
    luma = convert_rgb_to_grayscale(img)
  gy, gx = numpy.gradient(luma)
  return numpy.average(numpy.sqrt(gy*gy + gx*gx))


def compute_image_max_gradients(img):
  """Calculate the maximum gradient of each color channel in the image.

  Args:
    img: Numpy float image array, with pixel values in [0,1].

  Returns:
    A list of gradient max values, one per color channel in the image.
  """
  grads = []
  chans = img.shape[2]
  for i in range(chans):
    grads.append(numpy.amax(numpy.gradient(img[:, :, i])))
  return grads


def compute_image_snrs(img):
  """Calculate the SNR (dB) of each color channel in the image.

  Args:
    img: Numpy float image array, with pixel values in [0,1].

  Returns:
    A list of SNR values in dB, one per color channel in the image.
  """
  means = compute_image_means(img)
  variances = compute_image_variances(img)
  std_devs = [math.sqrt(v) for v in variances]
  snrs = [20 * math.log10(m/s) for m, s in zip(means, std_devs)]
  return snrs


def convert_rgb_to_grayscale(img):
  """Convert and 3-D array RGB image to grayscale image.

  Args:
    img: numpy float RGB/luma image array, with pixel values in [0,1].

  Returns:
    2-D grayscale image
  """
  chans = img.shape[2]
  if chans != 3:
    raise AssertionError(f'Not an RGB image! Depth: {chans}')
  return 0.299*img[:, :, 0] + 0.587*img[:, :, 1] + 0.114*img[:, :, 2]


def normalize_img(img):
  """Normalize the image values to between 0 and 1.

  Args:
    img: 2-D numpy array of image values
  Returns:
    Normalized image
  """
  return (img - numpy.amin(img))/(numpy.amax(img) - numpy.amin(img))


def rotate_img_per_argv(img):
  """Rotate an image 180 degrees if "rotate" is in argv.

  Args:
    img: 2-D numpy array of image values
  Returns:
    Rotated image
  """
  img_out = img
  if 'rotate180' in sys.argv:
    img_out = numpy.fliplr(numpy.flipud(img_out))
  return img_out


def stationary_lens_cap(cam, req, fmt):
  """Take up to NUM_TRYS caps and save the 1st one with lens stationary.

  Args:
   cam: open device session
   req: capture request
   fmt: format for capture

  Returns:
    capture
  """
  tries = 0
  done = False
  reqs = [req] * NUM_FRAMES
  while not done:
    logging.debug('Waiting for lens to move to correct location.')
    cap = cam.do_capture(reqs, fmt)
    done = (cap[NUM_FRAMES - 1]['metadata']['android.lens.state'] == 0)
    logging.debug('status: %s', done)
    tries += 1
    if tries == NUM_TRIES:
      raise error_util.CameraItsError('Cannot settle lens after %d tries!' %
                                      tries)
  return cap[NUM_FRAMES - 1]


def compute_image_rms_difference_1d(rgb_x, rgb_y):
  """Calculate the RMS difference between 2 RBG images as 1D arrays.

  Args:
    rgb_x: image array
    rgb_y: image array

  Returns:
    rms_diff
  """
  len_rgb_x = len(rgb_x)
  len_rgb_y = len(rgb_y)
  if len_rgb_y != len_rgb_x:
    raise AssertionError('RGB images have different number of planes! '
                         f'x: {len_rgb_x}, y: {len_rgb_y}')
  return math.sqrt(sum([pow(rgb_x[i] - rgb_y[i], 2.0)
                        for i in range(len_rgb_x)]) / len_rgb_x)


def compute_image_rms_difference_3d(rgb_x, rgb_y):
  """Calculate the RMS difference between 2 RBG images as 3D arrays.

  Args:
    rgb_x: image array in the form of w * h * channels
    rgb_y: image array in the form of w * h * channels

  Returns:
    rms_diff
  """
  shape_rgb_x = numpy.shape(rgb_x)
  shape_rgb_y = numpy.shape(rgb_y)
  if shape_rgb_y != shape_rgb_x:
    raise AssertionError('RGB images have different number of planes! '
                         f'x: {shape_rgb_x}, y: {shape_rgb_y}')
  if len(shape_rgb_x) != 3:
    raise AssertionError(f'RGB images dimension {len(shape_rgb_x)} is not 3!')

  mean_square_sum = 0.0
  for i in range(shape_rgb_x[0]):
    for j in range(shape_rgb_x[1]):
      for k in range(shape_rgb_x[2]):
        mean_square_sum += pow(rgb_x[i][j][k] - rgb_y[i][j][k], 2.0)
  return (math.sqrt(mean_square_sum /
                    (shape_rgb_x[0] * shape_rgb_x[1] * shape_rgb_x[2])))


def compute_image_sad(img_x, img_y):
  """Calculate the sum of absolute differences between 2 images.

  Args:
    img_x: image array in the form of w * h * channels
    img_y: image array in the form of w * h * channels

  Returns:
    sad
  """
  img_x = img_x[:, :, 1:].ravel()
  img_y = img_y[:, :, 1:].ravel()
  return numpy.sum(numpy.abs(numpy.subtract(img_x, img_y, dtype=float)))


def get_img(buffer):
  """Return a PIL.Image of the capture buffer.

  Args:
    buffer: data field from the capture result.

  Returns:
    A PIL.Image
  """
  return Image.open(io.BytesIO(buffer))


def jpeg_has_icc_profile(jpeg_img):
  """Checks if a jpeg PIL.Image has an icc profile attached.

  Args:
    jpeg_img: The PIL.Image.

  Returns:
    True if an icc profile is present, False otherwise.
  """
  return jpeg_img.info.get('icc_profile') is not None


def get_primary_chromaticity(primary):
  """Given an ImageCms primary, returns just the xy chromaticity coordinates.

  Args:
    primary: The primary from the ImageCms profile.

  Returns:
    (float, float): The xy chromaticity coordinates of the primary.
  """
  ((_, _, _), (x, y, _)) = primary
  return x, y


def is_jpeg_icc_profile_correct(jpeg_img, color_space, icc_profile_path=None):
  """Compare a jpeg's icc profile to a color space's expected parameters.

  Args:
    jpeg_img: The PIL.Image.
    color_space: 'DISPLAY_P3' or 'SRGB'
    icc_profile_path: Optional path to an icc file to be created with the
        raw contents.

  Returns:
    True if the icc profile matches expectations, False otherwise.
  """
  icc = jpeg_img.info.get('icc_profile')
  f = io.BytesIO(icc)
  icc_profile = ImageCms.getOpenProfile(f)

  if icc_profile_path is not None:
    raw_icc_bytes = f.getvalue()
    f = open(icc_profile_path, 'wb')
    f.write(raw_icc_bytes)
    f.close()

  cms_profile = icc_profile.profile
  (rx, ry) = get_primary_chromaticity(cms_profile.red_primary)
  (gx, gy) = get_primary_chromaticity(cms_profile.green_primary)
  (bx, by) = get_primary_chromaticity(cms_profile.blue_primary)

  if color_space == 'DISPLAY_P3':
    # Expected primaries based on Apple's Display P3 primaries
    expected_rx = EXPECTED_RX_P3
    expected_ry = EXPECTED_RY_P3
    expected_gx = EXPECTED_GX_P3
    expected_gy = EXPECTED_GY_P3
    expected_bx = EXPECTED_BX_P3
    expected_by = EXPECTED_BY_P3
  elif color_space == 'SRGB':
    # Expected primaries based on Pixel sRGB profile
    expected_rx = EXPECTED_RX_SRGB
    expected_ry = EXPECTED_RY_SRGB
    expected_gx = EXPECTED_GX_SRGB
    expected_gy = EXPECTED_GY_SRGB
    expected_bx = EXPECTED_BX_SRGB
    expected_by = EXPECTED_BY_SRGB
  else:
    # Unsupported color space for comparison
    return False

  cmp_values = [
      [rx, expected_rx],
      [ry, expected_ry],
      [gx, expected_gx],
      [gy, expected_gy],
      [bx, expected_bx],
      [by, expected_by]
  ]

  for (actual, expected) in cmp_values:
    if not math.isclose(actual, expected, abs_tol=0.001):
      # Values significantly differ
      return False

  return True


def area_of_triangle(x1, y1, x2, y2, x3, y3):
  """Calculates the area of a triangle formed by three points.

  Args:
    x1 (float): The x-coordinate of the first point.
    y1 (float): The y-coordinate of the first point.
    x2 (float): The x-coordinate of the second point.
    y2 (float): The y-coordinate of the second point.
    x3 (float): The x-coordinate of the third point.
    y3 (float): The y-coordinate of the third point.

  Returns:
    float: The area of the triangle.
  """
  area = abs((x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)) / 2.0)
  return area


def point_in_triangle(x1, y1, x2, y2, x3, y3, xp, yp, abs_tol):
  """Checks if the point (xp, yp) is inside the triangle.

  Args:
    x1 (float): The x-coordinate of the first point.
    y1 (float): The y-coordinate of the first point.
    x2 (float): The x-coordinate of the second point.
    y2 (float): The y-coordinate of the second point.
    x3 (float): The x-coordinate of the third point.
    y3 (float): The y-coordinate of the third point.
    xp (float): The x-coordinate of the point to check.
    yp (float): The y-coordinate of the point to check.
    abs_tol (float): Absolute tolerance amount.

  Returns:
    bool: True if the point is inside the triangle, False otherwise.
  """
  a = area_of_triangle(x1, y1, x2, y2, x3, y3)
  a1 = area_of_triangle(xp, yp, x2, y2, x3, y3)
  a2 = area_of_triangle(x1, y1, xp, yp, x3, y3)
  a3 = area_of_triangle(x1, y1, x2, y2, xp, yp)
  return math.isclose(a, (a1 + a2 + a3), abs_tol=abs_tol)


def distance(p, q):
  """Returns the Euclidean distance from point p to point q.

  Args:
    p: an Iterable of numbers
    q: an Iterable of numbers
  """
  return math.sqrt(sum((px - qx) ** 2.0 for px, qx in zip(p, q)))


def p3_img_has_wide_gamut(wide_img):
  """Check if a DISPLAY_P3 image contains wide gamut pixels.

  Given a DISPLAY_P3 image that should have a wider gamut than SRGB, checks all
  pixel values to see if any reside outside the SRGB gamut. This is done by
  converting to CIE xy chromaticities using a Bradford chromatic adaptation for
  consistency with ICC profiles.

  Args:
    wide_img: The PIL.Image in the DISPLAY_P3 color space.

  Returns:
    True if the gamut of wide_img is greater than that of SRGB.
    False otherwise.
  """
  w = wide_img.size[0]
  h = wide_img.size[1]
  wide_arr = numpy.array(wide_img)

  img_arr = colour.RGB_to_XYZ(
      wide_arr / 255.0,
      colour.models.rgb.datasets.display_p3.RGB_COLOURSPACE_DISPLAY_P3.whitepoint,
      colour.models.rgb.datasets.display_p3.RGB_COLOURSPACE_DISPLAY_P3.whitepoint,
      colour.models.rgb.datasets.display_p3.RGB_COLOURSPACE_DISPLAY_P3.matrix_RGB_to_XYZ,
      'Bradford', lambda x: colour.eotf(x, 'sRGB'))

  xy_arr = colour.XYZ_to_xy(img_arr)

  srgb_colorspace = colour.models.RGB_COLOURSPACE_sRGB
  srgb_primaries = srgb_colorspace.primaries

  for y in range(h):
    for x in range(w):
      # Check if the pixel chromaticity is inside or outside the SRGB gamut.
      # This check is not guaranteed not to emit false positives / negatives,
      # however the probability of either on an arbitrary DISPLAY_P3 camera
      # capture is exceedingly unlikely.
      if not point_in_triangle(*srgb_primaries.reshape(6),
                               xy_arr[y][x][0], xy_arr[y][x][1],
                               COLORSPACE_TRIANGLE_AREA_TOL):
        return True

  return False
