# Copyright 2014 The Android Open Source Project.

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
"""Noise model utility functions."""

import collections
import logging
import math
import os.path
import pickle
from typing import Any, Dict, List, Tuple
import warnings
import capture_request_utils
import image_processing_utils
from matplotlib import pylab
import matplotlib.pyplot as plt
import noise_model_constants
import numpy as np
import scipy.stats


_OUTLIER_MEDIAN_ABS_DEVS_DEFAULT = (
    noise_model_constants.OUTLIER_MEDIAN_ABS_DEVS_DEFAULT
)

def _check_auto_exposure_targets(
    auto_exposure_ns: float,
    sens_min: int,
    sens_max: int,
    bracket_factor: int,
    min_exposure_ns: int,
    max_exposure_ns: int,
) -> None:
  """Checks if AE too bright for highest gain & too dark for lowest gain.

  Args:
    auto_exposure_ns: The auto exposure value in nanoseconds.
    sens_min: The minimum sensitivity value.
    sens_max: The maximum sensitivity value.
    bracket_factor: Exposure bracket factor.
    min_exposure_ns: The minimum exposure time in nanoseconds.
    max_exposure_ns: The maximum exposure time in nanoseconds.
  """

  if auto_exposure_ns < min_exposure_ns * sens_max:
    raise AssertionError(
        'Scene is too bright to properly expose at highest '
        f'sensitivity: {sens_max}'
    )
  if auto_exposure_ns * bracket_factor > max_exposure_ns * sens_min:
    raise AssertionError(
        'Scene is too dark to properly expose at lowest '
        f'sensitivity: {sens_min}'
    )


def check_noise_model_shape(noise_model: np.ndarray) -> None:
  """Checks if the shape of noise model is valid.

  Args:
    noise_model: A numpy array of shape (num_channels, num_parameters).
  """
  num_channels, num_parameters = noise_model.shape
  if num_channels not in noise_model_constants.VALID_NUM_CHANNELS:
    raise AssertionError(
        f'The number of channels {num_channels} is not in'
        f' {noise_model_constants.VALID_NUM_CHANNELS}.'
    )
  if num_parameters != 4:
    raise AssertionError(
        f'The number of parameters of each channel {num_parameters} != 4.'
    )


def validate_noise_model(
    noise_model: np.ndarray,
    color_channels: List[str],
    sens_min: int,
) -> None:
  """Performs validation checks on the noise model.

  This function checks if read noise and intercept gradient are positive for
  each color channel.

  Args:
      noise_model: Noise model parameters each channel, including scale_a,
        scale_b, offset_a, offset_b.
      color_channels: Array of color channels.
      sens_min: Minimum sensitivity value.
  """
  check_noise_model_shape(noise_model)
  num_channels = noise_model.shape[0]
  if len(color_channels) != num_channels:
    raise AssertionError(
        f'Number of color channels {num_channels} != number of noise model '
        f'channels {len(color_channels)}.'
    )

  scale_a, _, offset_a, offset_b = zip(*noise_model)
  for i, color_channel in enumerate(color_channels):
    if scale_a[i] < 0:
      raise AssertionError(
          f'{color_channel} model API scale gradient < 0: {scale_a[i]:.4e}'
      )

    if offset_a[i] <= 0:
      raise AssertionError(
          f'{color_channel} model API intercept gradient < 0: {offset_a[i]:.4e}'
      )

    read_noise = offset_a[i] * sens_min * sens_min + offset_b[i]
    if read_noise <= 0:
      raise AssertionError(
          f'{color_channel} model min ISO noise < 0! '
          f'API intercept gradient: {offset_a[i]:.4e}, '
          f'API intercept offset: {offset_b[i]:.4e}, '
          f'read_noise: {read_noise:.4e}'
      )


def compute_digital_gains(
    gains: np.ndarray,
    sens_max_analog: np.ndarray,
) -> np.ndarray:
  """Computes the digital gains for the given gains and maximum analog gain.

  Define digital gain as the gain divide the max analog gain sensitivity.
  This function ensures that the digital gains are always equal to 1. If any
  of the digital gains is not equal to 1, an AssertionError is raised.

  Args:
    gains: An array of gains.
    sens_max_analog: The maximum analog gain sensitivity.

  Returns:
    An numpy array of digital gains.
  """
  digital_gains = np.maximum(gains / sens_max_analog, 1)
  if not np.all(digital_gains == 1):
    raise AssertionError(
        f'Digital gains are not all 1! gains: {gains}, '
        f'Max analog gain sensitivity: {sens_max_analog}.'
    )
  return digital_gains


def crop_and_save_capture(
    cap,
    props,
    capture_path: str,
    num_tiles_crop: int,
) -> None:
  """Crops and saves a capture image.

  Args:
    cap: The capture to be cropped and saved.
    props: The properties to be used to convert the capture to an RGB image.
    capture_path: The path to which the capture image should be saved.
    num_tiles_crop: The number of tiles to crop.
  """
  img = image_processing_utils.convert_capture_to_rgb_image(cap, props=props)
  height, width, _ = img.shape
  num_tiles_crop_max = min(height, width) // 2
  if num_tiles_crop >= num_tiles_crop_max:
    raise AssertionError(
        f'Number of tiles to corp {num_tiles_crop} >= {num_tiles_crop_max}.'
    )
  img = img[
      num_tiles_crop: height - num_tiles_crop,
      num_tiles_crop: width - num_tiles_crop,
      :,
  ]

  image_processing_utils.write_image(img, capture_path, True)


def crop_and_reorder_stats_images(
    mean_img: np.ndarray,
    var_img: np.ndarray,
    num_tiles_crop: int,
    channel_indices: List[int],
) -> Tuple[np.ndarray, np.ndarray]:
  """Crops the stats images and sorts stats images channels in canonical order.

  Args:
      mean_img: The mean image.
      var_img: The variance image.
      num_tiles_crop: The number of tiles to crop from each side of the image.
      channel_indices: The channel indices to sort stats image channels in
        canonical order.

  Returns:
      The cropped and reordered mean image and variance image.
  """
  if mean_img.shape != var_img.shape:
    raise AssertionError(
        'Unmatched shapes of mean and variance image: '
        f'shape of mean image is {mean_img.shape}, '
        f'shape of variance image is {var_img.shape}.'
    )
  height, width, _ = mean_img.shape
  if 2 * num_tiles_crop > min(height, width):
    raise AssertionError(
        f'The number of tiles to crop ({num_tiles_crop}) is so large that'
        ' images cannot be cropped.'
    )

  means = []
  vars_ = []
  for i in channel_indices:
    means_i = mean_img[
        num_tiles_crop: height - num_tiles_crop,
        num_tiles_crop: width - num_tiles_crop,
        i,
    ]
    vars_i = var_img[
        num_tiles_crop: height - num_tiles_crop,
        num_tiles_crop: width - num_tiles_crop,
        i,
    ]
    means.append(means_i)
    vars_.append(vars_i)
  means, vars_ = np.asarray(means), np.asarray(vars_)
  return means, vars_


def filter_stats(
    means: np.ndarray,
    vars_: np.ndarray,
    black_levels: List[float],
    white_level: float,
    max_signal_value: float = 0.25,
    is_remove_var_outliers: bool = False,
    deviations: int = _OUTLIER_MEDIAN_ABS_DEVS_DEFAULT,
) -> Tuple[np.ndarray, np.ndarray]:
  """Filters means outliers and variance outliers.

  Args:
      means: A numpy ndarray of pixel mean values.
      vars_: A numpy ndarray of pixel variance values.
      black_levels: A list of black levels for each pixel.
      white_level: A scalar white level.
      max_signal_value: The maximum signal (mean) value.
      is_remove_var_outliers: A boolean value indicating whether to remove
        variance outliers.
      deviations: A scalar value specifying the number of standard deviations to
        use when removing variance outliers.

  Returns:
      A tuple of (means_filtered, vars_filtered) where means_filtered and
      vars_filtered are numpy ndarrays of filtered pixel mean and variance
      values, respectively.
  """
  if means.shape != vars_.shape:
    raise AssertionError(
        f'Unmatched shapes of means and vars: means.shape={means.shape},'
        f' vars.shape={vars_.shape}.'
    )
  num_planes = len(means)
  means_filtered = []
  vars_filtered = []

  for pidx in range(num_planes):
    black_level = black_levels[pidx]
    means_i = means[pidx]
    vars_i = vars_[pidx]

    # Basic constraints:
    # (1) means are within the range [0, 1],
    # (2) vars are non-negative values.
    constraints = [
        means_i >= black_level,
        means_i <= white_level,
        vars_i >= 0,
    ]
    if is_remove_var_outliers:
      # Filter out variances that differ too much from the median of variances.
      std_dev = scipy.stats.median_abs_deviation(vars_i, axis=None, scale=1)
      med = np.median(vars_i)
      constraints.extend([
          vars_i > med - deviations * std_dev,
          vars_i < med + deviations * std_dev,
      ])

    keep_indices = np.where(np.logical_and.reduce(constraints))
    if not np.any(keep_indices):
      logging.info('After filter channel %d, stats array is empty.', pidx)

    # Normalizes the range to [0, 1].
    means_i = (means_i[keep_indices] - black_level) / (
        white_level - black_level
    )
    vars_i = vars_i[keep_indices] / ((white_level - black_level) ** 2)
    # Filter out the tiles if they have samples that might be clipped.
    mean_var_pairs = list(
        filter(
            lambda x: x[0] + 2 * math.sqrt(x[1]) < max_signal_value,
            zip(means_i, vars_i),
        )
    )
    if mean_var_pairs:
      means_i, vars_i = zip(*mean_var_pairs)
    else:
      means_i, vars_i = [], []
    means_i = np.asarray(means_i)
    vars_i = np.asarray(vars_i)
    means_filtered.append(means_i)
    vars_filtered.append(vars_i)

  # After filtering, means_filtered and vars_filtered may have different shapes
  # in each color planes.
  means_filtered = np.asarray(means_filtered, dtype=object)
  vars_filtered = np.asarray(vars_filtered, dtype=object)
  return means_filtered, vars_filtered


def get_next_iso(
    iso: float,
    max_iso: int,
    iso_multiplier: float,
) -> float:
  """Moves to the next sensitivity.

  Args:
    iso: The current ISO sensitivity.
    max_iso: The maximum ISO sensitivity.
    iso_multiplier: The ISO multiplier to use.

  Returns:
    The next ISO sensitivity.
  """
  if iso_multiplier <= 1:
    raise AssertionError(
        f'ISO multiplier is {iso_multiplier}, which should be greater than 1.'
    )

  if round(iso) < max_iso < round(iso * iso_multiplier):
    return max_iso
  else:
    return iso * iso_multiplier


def capture_stats_images(
    cam,
    props,
    stats_config: Dict[str, Any],
    sens_min: int,
    sens_max_meas: int,
    zoom_ratio: float,
    num_tiles_crop: int,
    max_signal_value: float,
    iso_multiplier: float,
    max_bracket: int,
    bracket_factor: int,
    capture_path_prefix: str,
    stats_file_name: str = '',
    is_remove_var_outliers: bool = False,
    outlier_median_abs_deviations: int = _OUTLIER_MEDIAN_ABS_DEVS_DEFAULT,
    is_debug_mode: bool = False,
) -> Dict[int, List[Tuple[float, np.ndarray, np.ndarray]]]:
  """Capture stats images and saves the stats in a dictionary.

  This function captures stats images at different ISO values and exposure
  times, and stores the stats data in a file with the specified name.
  The stats data includes the mean and variance of each plane, as well as
  exposure times.

  Args:
    cam: The camera session (its_session_utils.ItsSession) for capturing stats
      images.
    props: Camera property object.
    stats_config: The stats format config, a dictionary that specifies the raw
      stats image format and tile size.
    sens_min: The minimum sensitivity.
    sens_max_meas: The maximum sensitivity to measure.
    zoom_ratio: The zoom ratio to use.
    num_tiles_crop: The number of tiles to crop the images into.
    max_signal_value: The maximum signal value to allow.
    iso_multiplier: The ISO multiplier to use.
    max_bracket: The maximum number of bracketed exposures to capture.
    bracket_factor: The bracket factor with default value 2^max_bracket.
    capture_path_prefix: The path prefix to use for captured images.
    stats_file_name: The name of the file to save the stats images to.
    is_remove_var_outliers: Whether to remove variance outliers.
    outlier_median_abs_deviations: The number of median absolute deviations to
      use for detecting outliers.
    is_debug_mode: Whether to enable debug mode.

  Returns:
    A dictionary mapping ISO values to mean and variance image of each plane.
  """
  if is_debug_mode:
    logging.info('Capturing stats images with stats config: %s.', stats_config)
    capture_folder = os.path.join(capture_path_prefix, 'captures')
    if not os.path.exists(capture_folder):
      os.makedirs(capture_folder)
    logging.info('Capture folder: %s', capture_folder)

  white_level = props['android.sensor.info.whiteLevel']
  min_exposure_ns, max_exposure_ns = props[
      'android.sensor.info.exposureTimeRange'
  ]
  # Focus at zero to intentionally blur the scene as much as possible.
  f_dist = 0.0
  # Whether the stats images are quad Bayer or standard Bayer.
  is_quad_bayer = 'QuadBayer' in stats_config['format']
  if is_quad_bayer:
    num_channels = noise_model_constants.NUM_QUAD_BAYER_CHANNELS
  else:
    num_channels = noise_model_constants.NUM_BAYER_CHANNELS
  # A dict maps iso to stats images of different exposure times.
  iso_to_stats_dict = collections.defaultdict(list)
  # Start the sensitivity at the minimum.
  iso = sens_min
  # Previous iso cap.
  pre_iso_cap = None
  if stats_file_name:
    stats_file_path = os.path.join(capture_path_prefix, stats_file_name)
    if os.path.isfile(stats_file_path):
      try:
        with open(stats_file_path, 'rb') as f:
          saved_iso_to_stats_dict = pickle.load(f)
          # Filter saved stats data.
          if saved_iso_to_stats_dict:
            for iso, stats in saved_iso_to_stats_dict.items():
              if sens_min <= iso <= sens_max_meas:
                iso_to_stats_dict[iso] = stats

        # Set the starting iso to the last iso in saved stats file.
        if iso_to_stats_dict.keys():
          pre_iso_cap = sorted(iso_to_stats_dict.keys())[-1]
          iso = get_next_iso(pre_iso_cap, sens_max_meas, iso_multiplier)
      except OSError as e:
        logging.exception(
            'Failed to load stats file stored at %s. Error message: %s',
            stats_file_path,
            e,
        )

  if round(iso) <= sens_max_meas:
    # Wait until camera is repositioned for noise model calibration.
    input(
        f'\nPress <ENTER> after covering camera lense {cam.get_camera_name()} '
        'with frosted glass diffuser, and facing lense at evenly illuminated'
        ' surface.\n'
    )
    # Do AE to get a rough idea of where we are.
    iso_ae, exp_ae, _, _, _ = cam.do_3a(
        get_results=True, do_awb=False, do_af=False
    )

    # Underexpose to get more data for low signal levels.
    auto_exposure_ns = iso_ae * exp_ae / bracket_factor
    _check_auto_exposure_targets(
        auto_exposure_ns,
        sens_min,
        sens_max_meas,
        bracket_factor,
        min_exposure_ns,
        max_exposure_ns,
    )

  while round(iso) <= sens_max_meas:
    req = capture_request_utils.manual_capture_request(
        round(iso), min_exposure_ns, f_dist
    )
    cap = cam.do_capture(req, stats_config)
    # Instead of raising an error when the sensitivity readback != requested
    # use the readback value for calculations instead.
    iso_cap = cap['metadata']['android.sensor.sensitivity']

    # Different iso values may result in captures with the same iso_cap
    # value, so skip this capture if it's redundant.
    if iso_cap == pre_iso_cap:
      logging.info(
          'Skip current capture because of the same iso %d with the previous'
          ' capture.',
          iso_cap,
      )
      iso = get_next_iso(iso, sens_max_meas, iso_multiplier)
      continue
    pre_iso_cap = iso_cap

    logging.info('Request ISO: %d, Capture ISO: %d.', iso, iso_cap)

    for bracket in range(max_bracket):
      # Get the exposure for this sensitivity and exposure time.
      exposure_ns = round(math.pow(2, bracket) * auto_exposure_ns / iso)
      exposure_ms = round(exposure_ns * 1.0e-6, 3)
      logging.info('ISO: %d, exposure time: %.3f ms.', iso_cap, exposure_ms)
      req = capture_request_utils.manual_capture_request(
          iso_cap,
          exposure_ns,
          f_dist,
      )
      req['android.control.zoomRatio'] = zoom_ratio
      cap = cam.do_capture(req, stats_config)

      if is_debug_mode:
        capture_path = os.path.join(
            capture_folder, f'iso{iso_cap}_exposure{exposure_ns}ns.jpg'
        )
        crop_and_save_capture(cap, props, capture_path, num_tiles_crop)

      mean_img, var_img = image_processing_utils.unpack_rawstats_capture(
          cap, num_channels=num_channels
      )
      cfa_order = image_processing_utils.get_canonical_cfa_order(
          props, is_quad_bayer
      )

      means, vars_ = crop_and_reorder_stats_images(
          mean_img,
          var_img,
          num_tiles_crop,
          cfa_order,
      )
      if is_debug_mode:
        logging.info('Raw stats image size: %s', mean_img.shape)
        logging.info('R plane means image size: %s', means[0].shape)
        logging.info(
            'means min: %.3f, median: %.3f, max: %.3f',
            np.min(means), np.median(means), np.max(means),
        )
        logging.info(
            'vars_ min: %.4f, median: %.4f, max: %.4f',
            np.min(vars_), np.median(vars_), np.max(vars_),
        )

      black_levels = image_processing_utils.get_black_levels(
          props,
          cap['metadata'],
          is_quad_bayer,
      )

      means, vars_ = filter_stats(
          means,
          vars_,
          black_levels,
          white_level,
          max_signal_value,
          is_remove_var_outliers,
          outlier_median_abs_deviations,
      )

      iso_to_stats_dict[iso_cap].append((exposure_ms, means, vars_))

    if stats_file_name:
      with open(stats_file_path, 'wb+') as f:
        pickle.dump(iso_to_stats_dict, f)
    iso = get_next_iso(iso, sens_max_meas, iso_multiplier)

  return iso_to_stats_dict


def measure_linear_noise_models(
    iso_to_stats_dict: Dict[int, List[Tuple[float, np.ndarray, np.ndarray]]],
    color_planes: List[str],
):
  """Measures linear noise models.

  This function measures linear noise models from means and variances for each
  color plane and ISO setting.

  Args:
      iso_to_stats_dict: A dictionary mapping ISO settings to a list of stats
        data.
      color_planes: A list of color planes.

  Returns:
      A tuple containing:
          measured_models: A list of linear models, one for each color plane.
          samples: A list of samples, one for each color plane. Each sample is a
              tuple of (iso, mean, var).
  """
  num_planes = len(color_planes)
  # Model parameters for each color plane.
  measured_models = [[] for _ in range(num_planes)]
  # Samples (ISO, mean and var) of each quad Bayer color channels.
  samples = [[] for _ in range(num_planes)]

  for iso in sorted(iso_to_stats_dict.keys()):
    logging.info('Calculating measured models for ISO %d.', iso)
    stats_per_plane = [[] for _ in range(num_planes)]
    for _, means, vars_ in iso_to_stats_dict[iso]:
      for pidx in range(num_planes):
        means_p = means[pidx]
        vars_p = vars_[pidx]
        if means_p.size > 0 and vars_p.size > 0:
          stats_per_plane[pidx].extend(list(zip(means_p, vars_p)))

    for pidx, mean_var_pairs in enumerate(stats_per_plane):
      if not mean_var_pairs:
        raise ValueError(
            f'For ISO {iso}, samples are empty in color plane'
            f' {color_planes[pidx]}.'
        )
      slope, intercept, rvalue, _, _ = scipy.stats.linregress(mean_var_pairs)

      measured_models[pidx].append((iso, slope, intercept))
      logging.info(
          (
              'Measured model for ISO %d and color plane %s: '
              'y = %e * x + %e (R=%.6f).'
          ),
          iso, color_planes[pidx], slope, intercept, rvalue,
      )

      # Add the samples for this sensitivity to the global samples list.
      samples[pidx].extend([(iso, mean, var) for (mean, var) in mean_var_pairs])

  return measured_models, samples


def compute_noise_model(
    samples: List[List[Tuple[float, np.ndarray, np.ndarray]]],
    sens_max_analog: int,
    offset_a: np.ndarray,
    offset_b: np.ndarray,
    is_two_stage_model: bool = False,
) -> np.ndarray:
  """Computes noise model parameters from samples.

  The noise model is defined by the following equation:
    f(x) = scale * x + offset

  where we have:
    scale = scale_a * analog_gain * digital_gain + scale_b,
    offset = (offset_a * analog_gain^2 + offset_b) * digital_gain^2.
    scale is the multiplicative factor and offset is the offset term.

  Assume digital_gain is 1.0 and scale_a, scale_b, offset_a, offset_b are
  sa, sb, oa, ob respectively, so we have noise model function:
  f(x) = (sa * analog_gain + sb) * x + (oa * analog_gain^2 + ob).

  The noise model is fit to the mesuared data using the scipy.optimize
  function, which uses an iterative Levenberg-Marquardt algorithm to
  find the model parameters that minimize the mean squared error.

  Args:
    samples: A list of samples, each of which is a list of tuples of `(gains,
      means, vars_)`.
    sens_max_analog: The maximum analog gain.
    offset_a: The gradient coefficients from the read noise calibration.
    offset_b: The intercept coefficients from the read noise calibration.
    is_two_stage_model: A boolean flag indicating if the noise model is
      calibrated in the two-stage mode.

  Returns:
    A numpy array containing noise model parameters (scale_a, scale_b,
    offset_a, offset_b) of each channel.
  """
  noise_model = []
  for pidx, samples_p in enumerate(samples):
    gains, means, vars_ = zip(*samples_p)
    gains = np.asarray(gains).flatten()
    means = np.asarray(means).flatten()
    vars_ = np.asarray(vars_).flatten()

    compute_digital_gains(gains, sens_max_analog)

    # Use a global linear optimization to fit the noise model.
    # Noise model function:
    # f(x) = scale * x + offset
    # Where:
    # scale = scale_a * analog_gain * digital_gain + scale_b.
    # offset = (offset_a * analog_gain^2 + offset_b) * digital_gain^2.
    # Function f will be used to train the scale and offset coefficients
    # scale_a, scale_b, offset_a, offset_b.
    if is_two_stage_model:
      # For the two-stage model, we want to use the line fit coefficients
      # found from capturing read noise data (offset_a and offset_b) to
      # train the scale coefficients.
      oa, ob = offset_a[pidx], offset_b[pidx]

      # Cannot pass oa and ob as the parameters of f since we only want
      # curve_fit return 2 parameters.
      def f(x, sa, sb):
        scale = sa * x[0] + sb
        # pylint: disable=cell-var-from-loop
        offset = oa * x[0] ** 2 + ob
        return (scale * x[1] + offset) / x[0]

    else:
      def f(x, sa, sb, oa, ob):
        scale = sa * x[0] + sb
        offset = oa * x[0] ** 2 + ob
        return (scale * x[1] + offset) / x[0]

    # Divide the whole system by gains*means.
    coeffs, _ = scipy.optimize.curve_fit(f, (gains, means), vars_ / (gains))

    # If using two-stage model, two of the coefficients calculated above are
    # constant, so we need to append them to the coeffs ndarray.
    if is_two_stage_model:
      coeffs = np.append(coeffs, offset_a[pidx])
      coeffs = np.append(coeffs, offset_b[pidx])

    # coeffs[0:4] = (scale_a, scale_b, offset_a, offset_b).
    noise_model.append(coeffs[0:4])

  noise_model = np.asarray(noise_model)
  check_noise_model_shape(noise_model)
  return noise_model


def create_stats_figure(
    iso: int,
    color_channel_names: List[str],
):
  """Creates a figure with subplots showing the mean and variance samples.

  Args:
    iso: The ISO setting for the images.
    color_channel_names: A list of strings containing the names of the color
      channels.

  Returns:
    A tuple of the figure and a list of the subplots.
  """
  if len(color_channel_names) not in noise_model_constants.VALID_NUM_CHANNELS:
    raise AssertionError(
        'The number of channels should be in'
        f' {noise_model_constants.VALID_NUM_CHANNELS}, but found'
        f' {len(color_channel_names)}. '
    )

  is_quad_bayer = (
      len(color_channel_names) == noise_model_constants.NUM_QUAD_BAYER_CHANNELS
  )
  if is_quad_bayer:
    # Adds a plot of the mean and variance samples for each color plane.
    fig, axes = plt.subplots(4, 4, figsize=(22, 22))
    fig.gca()
    fig.suptitle('ISO %d' % iso, x=0.52, y=0.99)

    cax = fig.add_axes([0.65, 0.995, 0.33, 0.003])
    cax.set_title('log(exposure_ms):', x=-0.13, y=-2.0)
    fig.colorbar(
        noise_model_constants.COLOR_BAR, cax=cax, orientation='horizontal'
    )

    # Add a big axis, hide frame.
    fig.add_subplot(111, frameon=False)

    # Add a common x-axis and y-axis.
    plt.tick_params(
        labelcolor='none',
        which='both',
        top=False,
        bottom=False,
        left=False,
        right=False,
    )
    plt.xlabel('Mean signal level', ha='center')
    plt.ylabel('Variance', va='center', rotation='vertical')

    subplots = []
    for pidx in range(noise_model_constants.NUM_QUAD_BAYER_CHANNELS):
      subplot = axes[pidx // 4, pidx % 4]
      subplot.set_title(color_channel_names[pidx])
      # Set 'y' axis to scientific notation for all numbers by setting
      # scilimits to (0, 0).
      subplot.ticklabel_format(axis='y', style='sci', scilimits=(0, 0))
      subplots.append(subplot)

  else:
    # Adds a plot of the mean and variance samples for each color plane.
    fig, [[plt_r, plt_gr], [plt_gb, plt_b]] = plt.subplots(
        2, 2, figsize=(11, 11)
    )
    fig.gca()
    # Add color bar to show exposure times.
    cax = fig.add_axes([0.73, 0.99, 0.25, 0.01])
    cax.set_title('log(exposure_ms):', x=-0.3, y=-1.0)
    fig.colorbar(
        noise_model_constants.COLOR_BAR, cax=cax, orientation='horizontal'
    )

    subplots = [plt_r, plt_gr, plt_gb, plt_b]
    fig.suptitle('ISO %d' % iso, x=0.54, y=0.99)
    for pidx, subplot in enumerate(subplots):
      subplot.set_title(color_channel_names[pidx])
      subplot.set_xlabel('Mean signal level')
      subplot.set_ylabel('Variance')
      subplot.ticklabel_format(axis='y', style='sci', scilimits=(0, 0))

  with warnings.catch_warnings():
    warnings.simplefilter('ignore', UserWarning)
    pylab.tight_layout()

  return fig, subplots
