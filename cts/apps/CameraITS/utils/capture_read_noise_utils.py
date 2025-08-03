# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Utility functions to enable capture read noise analysis."""

import csv
import logging
import math
import os
import pickle
import camera_properties_utils
import capture_request_utils
import error_util
import image_processing_utils
import its_session_utils
from matplotlib import pylab
import matplotlib.pyplot as plt
from matplotlib.ticker import NullLocator
from matplotlib.ticker import ScalarFormatter
import noise_model_constants
import noise_model_utils
import numpy as np

_LINEAR_FIT_NUM_SAMPLES = 100  # Number of samples to plot for the linear fit
_PLOT_AXIS_TICKS = 5  # Number of ticks to display on the plot axis
_FIG_DPI = 100  # Read noise plots dpi.
# Valid raw format for capturing read noise images.
_VALID_RAW_FORMATS = ('raw', 'raw10', 'rawQuadBayer', 'raw10QuadBayer')


def save_read_noise_data_as_csv(read_noise_data, iso_low, iso_high, file,
                                color_channels_names):
  """Creates and saves a CSV file containing read noise data.

  Args:
    read_noise_data: A list of lists of dictionaries, where each dictionary
      contains read noise data for a single color channel.
    iso_low: The minimum ISO sensitivity to include in the CSV file.
    iso_high: The maximum ISO sensitivity to include in the CSV file.
    file: The path to the CSV file to create.
    color_channels_names: A list of color channels to include in the CSV file.
  """
  with open(file, 'w+') as f:
    writer = csv.writer(f)

    results = list(
        filter(
            lambda x: x[0]['iso'] >= iso_low and x[0]['iso'] <= iso_high,
            read_noise_data,
        )
    )

    # Create headers for csv file
    headers = ['iso', 'iso^2']
    headers.extend([f'mean_{color}' for color in color_channels_names])
    headers.extend([f'var_{color}' for color in color_channels_names])
    headers.extend([f'norm_var_{color}' for color in color_channels_names])

    writer.writerow(headers)

    # Create data rows
    for data_row in results:
      row = [data_row[0]['iso']]
      row.append(data_row[0]['iso']**2)
      row.extend([stats['mean'] for stats in data_row])
      row.extend([stats['var'] for stats in data_row])
      row.extend([stats['norm_var'] for stats in data_row])

      writer.writerow(row)

    writer.writerow([])  # divider line

    # Create row containing the offset coefficients calculated by np.polyfit
    coeff_headers = ['', 'offset_coefficient_a', 'offset_coefficient_b']
    writer.writerow(coeff_headers)

    offset_a, offset_b = get_read_noise_coefficients(results, iso_low, iso_high)
    for i in range(len(color_channels_names)):
      writer.writerow([color_channels_names[i], offset_a[i], offset_b[i]])


def plot_read_noise_data(read_noise_data, iso_low, iso_high, file_path,
                         color_channel_names, plot_colors):
  """Plots the read noise data for the given ISO range.

  Args:
      read_noise_data: Quad Bayer read noise data object.
      iso_low: The minimum iso value to include.
      iso_high: The maximum iso value to include.
      file_path: File path for the plot image.
      color_channel_names: The name list of each color channel.
      plot_colors: The color list for plotting.
  """
  num_channels = len(color_channel_names)
  is_quad_bayer = num_channels == noise_model_constants.NUM_QUAD_BAYER_CHANNELS
  # Create the figure for plotting the read noise to ISO^2 curve.
  fig, ((red, green_r), (green_b, blue)) = plt.subplots(2, 2, figsize=(22, 22))
  subplots = [red, green_r, green_b, blue]
  fig.gca()
  fig.suptitle('Read Noise to ISO^2', x=0.54, y=0.99)

  # Get the ISO values for the current range.
  filtered_data = list(
      filter(
          lambda x: x[0]['iso'] >= iso_low and x[0]['iso'] <= iso_high,
          read_noise_data,
      )
  )

  # Get X-axis values (ISO^2) for current_range.
  iso_sq = [data[0]['iso']**2 for data in filtered_data]

  # Get X-axis values for the calculated linear fit for the read noise
  iso_sq_values = np.linspace(iso_low**2, iso_high**2, _LINEAR_FIT_NUM_SAMPLES)

  # Get the line fit coeff for plotting the linear fit of read noise to iso^2
  coeff_a, coeff_b = get_read_noise_coefficients(
      filtered_data, iso_low, iso_high
  )

  # Plot the read noise to iso^2 data
  for pidx, color_channel in enumerate(color_channel_names):
    norm_vars = [data[pidx]['norm_var'] for data in filtered_data]

    # Plot the measured read noise to ISO^2 values
    if is_quad_bayer:
      subplot = subplots[pidx // 4]
    else:
      subplot = subplots[pidx]

    subplot.plot(
        iso_sq,
        norm_vars,
        color=plot_colors[pidx],
        marker='o',
        markeredgecolor=plot_colors[pidx],
        linestyle='None',
        label=color_channel,
        alpha=0.3,
    )

    # Plot the line fit calculated from the read noise values
    subplot.plot(
        iso_sq_values,
        coeff_a[pidx] * iso_sq_values + coeff_b[pidx],
        color=plot_colors[pidx],
        )

  # Create a numpy array containing all normalized variance values for the
  # current range, this will be used for labelling the X-axis
  y_values = np.array(
      [[color['norm_var'] for color in x] for x in filtered_data]
  )

  x_ticks = np.linspace(iso_low**2, iso_high**2, _PLOT_AXIS_TICKS)
  y_ticks = np.linspace(np.min(y_values), np.max(y_values), _PLOT_AXIS_TICKS)

  for i, subplot in enumerate(subplots):
    subplot.set_title(noise_model_constants.BAYER_COLORS[i])
    subplot.set_xlabel('ISO^2')
    subplot.set_ylabel('Read Noise')

    subplot.set_xticks(x_ticks)
    subplot.xaxis.set_minor_locator(NullLocator())
    subplot.xaxis.set_major_formatter(ScalarFormatter())

    subplot.set_yticks(y_ticks)
    subplot.yaxis.set_minor_locator(NullLocator())
    subplot.yaxis.set_major_formatter(ScalarFormatter())

    subplot.legend()
    pylab.tight_layout()

  fig.savefig(file_path, dpi=_FIG_DPI)


def _generate_read_noise_stats(img, iso, white_level, cfa_order):
  """Generates read noise data for a given image.

    The read noise data of each channel is added in the order of cfa_order.
    As a result, the read noise data channels are reordered as the following.
    (1) For standard Bayer: R, Gr, Gb, B.
    (2) For quad Bayer: R0, R1, R2, R3,
                        Gr0, Gr1, Gr2, Gr3,
                        Gb0, Gb1, Gb2, Gb3,
                        B0, B1, B2, B3.

  Args:
    img: The input image.
    iso: The ISO sensitivity used to capture the image.
    white_level: The white level of the image.
    cfa_order: The color filter arrangement (CFA) order of the image.

  Returns:
    A list of dictionaries, where each dictionary contains information for a
    single color channel in the image.
  """
  result = []

  num_channels = len(cfa_order)
  channel_img = image_processing_utils.subsample(img, num_channels)

  # Create a list of dictionaries of read noise stats for each color channel
  # in the image.
  # The stats is reordered according to the color filter arrangement order.
  for ch in cfa_order:
    mean = np.mean(channel_img[:, :, ch])
    var = np.var(channel_img[:, :, ch])
    norm_var = var / ((white_level - mean)**2)
    result.append({
        'iso': iso,
        'mean': mean,
        'var': var,
        'norm_var': norm_var
    })

  return result


def get_read_noise_coefficients(read_noise_data, iso_low=0, iso_high=1000000):
  """Calculates read noise coefficients that best fit the read noise data.

  Args:
    read_noise_data: Read noise data object.
    iso_low: The lower bound of the ISO range to consider.
    iso_high: The upper bound of the ISO range to consider.

  Returns:
    A tuple of two numpy arrays, where the first array contains read noise
    coefficient a and the second array contains read noise coefficient b.
  """
  # Filter the values by the given ISO range.
  read_noise_data_filtered = list(
      filter(
          lambda x: x[0]['iso'] >= iso_low and x[0]['iso'] <= iso_high,
          read_noise_data,
      )
  )

  read_noise_coefficients_a = []
  read_noise_coefficients_b = []

  # Get ISO^2 values used for X-axis in polyfit
  iso_sq = [data[0]['iso'] ** 2 for data in read_noise_data_filtered]

  # Find the linear equation coefficients for each color channel
  num_channels = len(read_noise_data_filtered[0])
  for i in range(num_channels):
    norm_var = [data[i]['norm_var'] for data in read_noise_data_filtered]
    coeffs = np.polyfit(iso_sq, norm_var, 1)

    read_noise_coefficients_a.append(coeffs[0])
    read_noise_coefficients_b.append(coeffs[1])

  read_noise_coefficients_a = np.asarray(read_noise_coefficients_a)
  read_noise_coefficients_b = np.asarray(read_noise_coefficients_b)
  return read_noise_coefficients_a, read_noise_coefficients_b


def _capture_read_noise_for_iso_range(cam, raw_format, low_iso, high_iso,
                                      steps_per_stop, dest_file):
  """Captures read noise data at the lowest advertised exposure value.

  This function captures a series of images at different ISO sensitivities,
  starting at `low_iso` and ending at `high_iso`. The number of steps between
  each ISO sensitivity is equal to `steps`. Then read noise stats data is
  computed. Finally, stats data of color channels are reordered into the
  canonical order before saving it to `dest_file`.

  Args:
    cam:             Camera for the current ItsSession.
    raw_format:      The format of read noise image.
    low_iso:         The lowest iso value in range.
    high_iso:        The highest iso value in range.
    steps_per_stop:  Steps to take per stop.
    dest_file:       The path where read noise stats should be saved.

  Returns:
    Read noise stats list for each sensitivity.
  """
  if raw_format not in _VALID_RAW_FORMATS:
    supported_formats_str = ', '.join(_VALID_RAW_FORMATS)
    raise error_util.CameraItsError(
        f'Invalid raw format {raw_format}. '
        f'Current supported raw formats: {supported_formats_str}.'
    )

  props = cam.get_camera_properties()
  props = cam.override_with_hidden_physical_camera_props(props)

  format_check_result = False
  if raw_format in ('raw', 'rawQuadBayer'):
    format_check_result = camera_properties_utils.raw16(props)
  elif raw_format in ('raw10', 'raw10QuadBayer'):
    format_check_result = camera_properties_utils.raw10(props)

  camera_properties_utils.skip_unless(
      format_check_result and
      camera_properties_utils.manual_sensor(props) and
      camera_properties_utils.read_3a(props) and
      camera_properties_utils.per_frame_control(props))
  min_exposure_ns, _ = props['android.sensor.info.exposureTimeRange']
  min_fd = props['android.lens.info.minimumFocusDistance']
  white_level = props['android.sensor.info.whiteLevel']
  is_quad_bayer = 'QuadBayer' in raw_format
  cfa_order = image_processing_utils.get_canonical_cfa_order(
      props, is_quad_bayer
  )
  pre_iso_cap = None
  iso = low_iso
  iso_multiplier = math.pow(2, 1.0 / steps_per_stop)
  stats_list = []
  # This operation can last a very long time, if it happens to fail halfway
  # through, this section of code will allow us to pick up where we left off
  if os.path.exists(dest_file):
    # If there already exists a read noise stats file, retrieve them.
    with open(dest_file, 'rb') as f:
      stats_list = pickle.load(f)
    # Set the starting iso to the last iso of read noise stats.
    pre_iso_cap = stats_list[-1][0]['iso']
    iso = noise_model_utils.get_next_iso(pre_iso_cap, high_iso, iso_multiplier)

  if round(iso) <= high_iso:
    # Wait until camera is repositioned for read noise data collection.
    input(
        f'\nPress <ENTER> after concealing camera {cam.get_camera_name()} '
        'in complete darkness.\n'
    )

  fmt = {'format': raw_format}
  logging.info('Capturing read noise images with format %s.', raw_format)
  while round(iso) <= high_iso:
    req = capture_request_utils.manual_capture_request(
        round(iso), min_exposure_ns
    )
    req['android.lens.focusDistance'] = min_fd
    cap = cam.do_capture(req, fmt)
    iso_cap = cap['metadata']['android.sensor.sensitivity']

    # Different iso values may result in captures with the same iso_cap value,
    # so skip this capture if it's redundant.
    if iso_cap == pre_iso_cap:
      logging.info(
          'Skip current capture because of the same iso %d with the previous'
          ' capture.',
          iso_cap,
      )
      iso = noise_model_utils.get_next_iso(iso, high_iso, iso_multiplier)
      continue

    pre_iso_cap = iso_cap
    w = cap['width']
    h = cap['height']

    if raw_format in ('raw10', 'raw10QuadBayer'):
      img = image_processing_utils.unpack_raw10_image(
          cap['data'].reshape(h, w * 5 // 4)
      )
    elif raw_format in ('raw', 'rawQuadBayer'):
      img = np.ndarray(
          shape=(h * w,), dtype='<u2', buffer=cap['data'][0: w * h * 2]
      )
      img = img.astype(dtype=np.uint16).reshape(h, w)

    # Add reordered read noise stats to read noise stats list.
    stats = _generate_read_noise_stats(img, iso_cap, white_level, cfa_order)
    stats_list.append(stats)

    logging.info('iso: %.2f, mean: %.2f, var: %.2f, min: %d, max: %d', iso_cap,
                 np.mean(img), np.var(img), np.min(img), np.max(img))

    with open(dest_file, 'wb+') as f:
      pickle.dump(stats_list, f)

    iso = noise_model_utils.get_next_iso(iso, high_iso, iso_multiplier)

  logging.info('Read noise stats pickled into file %s.', dest_file)

  return stats_list


def calibrate_read_noise(
    device_id: str,
    camera_id: str,
    hidden_physical_id: str,
    read_noise_folder_prefix: str,
    read_noise_file_name: str,
    steps_per_stop: int,
    raw_format: str = 'raw',
    is_two_stage_model: bool = False,
) -> str:
  """Calibrates the read noise of the camera.

  Read noise is a type of noise that occurs in digital cameras when the image
  sensor converts light to an electronic signal. Calibrating read noise is the
  first step in the 2-stage noise model calibration.

  Args:
    device_id: The device ID of the camera.
    camera_id: The camera ID of the camera.
    hidden_physical_id: The hidden physical ID of the camera.
    read_noise_folder_prefix: The prefix of the read noise folder.
    read_noise_file_name: The name of the read noise file.
    steps_per_stop: The number of steps per stop.
    raw_format: The format of raw capture, which can be one of raw, raw10,
      rawQuadBayer and raw10QuadBayer.
    is_two_stage_model: A boolean flag indicating if the noise model is
      calibrated in the two-stage mode.

  Returns:
    The path to the read noise file.
  """
  if not is_two_stage_model:
    return ''
  # If two-stage model is enabled, check/collect read noise data.
  with its_session_utils.ItsSession(
      device_id=device_id,
      camera_id=camera_id,
      hidden_physical_id=hidden_physical_id,
  ) as cam:
    props = cam.get_camera_properties()
    props = cam.override_with_hidden_physical_camera_props(props)

    # Get sensor analog ISO range.
    sens_min, _ = props['android.sensor.info.sensitivityRange']
    sens_max_analog = props['android.sensor.maxAnalogSensitivity']
    # Maximum sensitivity for measuring noise model.
    sens_max_meas = sens_max_analog

    # Prepare read noise folder.
    camera_name = cam.get_camera_name()
    read_noise_folder = os.path.join(
        read_noise_folder_prefix, device_id.replace(':', '_'), camera_name
    )
    read_noise_file_path = os.path.join(read_noise_folder, read_noise_file_name)
    if not os.path.exists(read_noise_folder):
      os.makedirs(read_noise_folder)
    logging.info('Read noise data folder: %s', read_noise_folder)

    # Collect or retrieve read noise data.
    if not os.path.isfile(read_noise_file_path):
      logging.info('Collecting read noise data for %s', camera_name)
      # Read noise data file does not exist, collect read noise data.
      _capture_read_noise_for_iso_range(
          cam,
          raw_format,
          sens_min,
          sens_max_meas,
          steps_per_stop,
          read_noise_file_path,
      )
    else:
      # If data exists, check if it covers the full range.
      with open(read_noise_file_path, 'rb') as f:
        read_noise_data = pickle.load(f)
        # The +5 offset takes write to read error into account.
        if read_noise_data[-1][0]['iso'] + 5 < sens_max_meas:
          logging.error(
              (
                  '\nNot enough ISO data points exist. '
                  '\nMax ISO measured: %.2f'
                  '\nMax ISO possible: %.2f'
              ),
              read_noise_data[-1][0]['iso'],
              sens_max_meas,
          )
          # Not all data points were captured, continue capture.
          _capture_read_noise_for_iso_range(
              cam,
              raw_format,
              sens_min,
              sens_max_meas,
              steps_per_stop,
              read_noise_file_path,
          )

    return read_noise_file_path
