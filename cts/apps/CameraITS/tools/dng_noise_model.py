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
"""CameraITS script to generate noise models."""

import logging
import math
import os.path
import pathlib
import pickle
import tempfile
import textwrap

import capture_read_noise_utils
import its_base_test
import its_session_utils
from matplotlib import pylab
import matplotlib.pyplot as plt
import matplotlib.ticker
from mobly import test_runner
import noise_model_constants
import noise_model_utils
import numpy as np

_IS_QUAD_BAYER = False  # A manual flag to choose standard or quad Bayer noise
                        # model generation.
if _IS_QUAD_BAYER:
  _COLOR_CHANNEL_NAMES = noise_model_constants.QUAD_BAYER_COLORS
  _PLOT_COLORS = noise_model_constants.QUAD_BAYER_PLOT_COLORS
  _TILE_SIZE = 64  # Tile size to compute mean/variance. Large tiles may have
                   # their variance corrupted by low freq image changes.
  _STATS_FORMAT = 'raw10QuadBayerStats'  # rawQuadBayerStats|raw10QuadBayerStats
  _READ_NOISE_RAW_FORMAT = 'raw10QuadBayer'  # rawQuadBayer|raw10QuadBayer
else:
  _COLOR_CHANNEL_NAMES = noise_model_constants.BAYER_COLORS
  _PLOT_COLORS = noise_model_constants.BAYER_PLOT_COLORS
  _TILE_SIZE = 32  # Tile size to compute mean/variance. Large tiles may have
                   # their variance corrupted by low freq image changes.
  _STATS_FORMAT = 'rawStats'  # rawStats|raw10Stats
  _READ_NOISE_RAW_FORMAT = 'raw'  # raw|raw10

_STATS_CONFIG = {
    'format': _STATS_FORMAT,
    'gridWidth': _TILE_SIZE,
    'gridHeight': _TILE_SIZE,
}
_BRACKET_MAX = 8  # Exposure bracketing range in stops
_BRACKET_FACTOR = math.pow(2, _BRACKET_MAX)
_ISO_MAX_VALUE = None  # ISO range max value, uses sensor max if None
_ISO_MIN_VALUE = None  # ISO range min value, uses sensor min if None
_MAX_SCALE_FUDGE = 1.1
_MAX_SIGNAL_VALUE = 0.25  # Maximum value to allow mean of the tiles to go.
_NAME = os.path.basename(__file__).split('.')[0]
_NAME_READ_NOISE = os.path.join(tempfile.gettempdir(), 'CameraITS/ReadNoise')
_NAME_READ_NOISE_FILE = 'read_noise_results.pkl'
_STATS_FILE_NAME = 'stats.pkl'
_OUTLIER_MEDIAN_ABS_DEVS = 10  # Defines the number of Median Absolute
                               # Deviations that constitutes acceptable data
_READ_NOISE_STEPS_PER_STOP = 12  # Sensitivities per stop to sample for read
                                 # noise
_REMOVE_VAR_OUTLIERS = False  # When True, filters the variance to remove
                              # outliers
_STEPS_PER_STOP = 3  # How many sensitivities per stop to sample.
_ISO_MULTIPLIER = math.pow(2, 1.0 / _STEPS_PER_STOP)
_TILE_CROP_N = 0  # Number of tiles to crop from edge of image. Usually 0.
_TWO_STAGE_MODEL = False  # Require read noise data prior to running noise model
_ZOOM_RATIO = 1  # Zoom target to be used while running the model
_FIG_DPI = 100  # DPI for plotting noise model figures.
_BAYER_COLORS_FOR_NOISE_PROFILE = tuple(
    map(str.lower, noise_model_constants.BAYER_COLORS)
)
_QUAD_BAYER_COLORS_FOR_NOISE_PROFILE = tuple(
    map(str.lower, noise_model_constants.QUAD_BAYER_COLORS)
)


class DngNoiseModel(its_base_test.ItsBaseTest):
  """Create DNG noise model.

  Captures RAW images with increasing analog gains to create the model.
  """

  def _create_noise_model_code(self, noise_model, sens_min, sens_max,
                               sens_max_analog, file_path):
    """Creates the C file for the noise model.

    Args:
      noise_model: Noise model parameters.
      sens_min: The minimum sensitivity value.
      sens_max: The maximum sensitivity value.
      sens_max_analog: The maximum analog sensitivity value.
      file_path: The path to the noise model file.
    """
    # Generate individual noise model components.
    scale_a, scale_b, offset_a, offset_b = zip(*noise_model)
    digital_gain_cdef = (
        f'(sens / {sens_max_analog:.1f}) < 1.0 ? '
        f'1.0 : (sens / {sens_max_analog:.1f})'
    )

    with open(file_path, 'w') as text_file:
      scale_a_str = ','.join([str(i) for i in scale_a])
      scale_b_str = ','.join([str(i) for i in scale_b])
      offset_a_str = ','.join([str(i) for i in offset_a])
      offset_b_str = ','.join([str(i) for i in offset_b])
      # pylint: disable=line-too-long
      code = textwrap.dedent(f"""\
              /* Generated test code to dump a table of data for external validation
              * of the noise model parameters.
              */
              #include <stdio.h>
              #include <assert.h>
              double compute_noise_model_entry_S(int plane, int sens);
              double compute_noise_model_entry_O(int plane, int sens);
              int main(void) {{
                  for (int plane = 0; plane < {len(scale_a)}; plane++) {{
                      for (int sens = {sens_min}; sens <= {sens_max}; sens += 100) {{
                          double o = compute_noise_model_entry_O(plane, sens);
                          double s = compute_noise_model_entry_S(plane, sens);
                          printf("%d,%d,%lf,%lf\\n", plane, sens, o, s);
                      }}
                  }}
                  return 0;
              }}

              /* Generated functions to map a given sensitivity to the O and S noise
              * model parameters in the DNG noise model. The planes are in
              * R, Gr, Gb, B order.
              */
              double compute_noise_model_entry_S(int plane, int sens) {{
                  static double noise_model_A[] = {{ {scale_a_str:s} }};
                  static double noise_model_B[] = {{ {scale_b_str:s} }};
                  double A = noise_model_A[plane];
                  double B = noise_model_B[plane];
                  double s = A * sens + B;
                  return s < 0.0 ? 0.0 : s;
              }}

              double compute_noise_model_entry_O(int plane, int sens) {{
                  static double noise_model_C[] = {{ {offset_a_str:s} }};
                  static double noise_model_D[] = {{ {offset_b_str:s} }};
                  double digital_gain = {digital_gain_cdef:s};
                  double C = noise_model_C[plane];
                  double D = noise_model_D[plane];
                  double o = C * sens * sens + D * digital_gain * digital_gain;
                  return o < 0.0 ? 0.0 : o;
              }}
              """)

      text_file.write(code)

  def _create_noise_profile_code(self, noise_model, color_channels, file_path):
    """Creates the noise profile C++ file.

    Args:
      noise_model: Noise model parameters.
      color_channels: Color channels in canonical order.
      file_path: The path to the noise profile C++ file.
    """
    # Generate individual noise model components.
    scale_a, scale_b, offset_a, offset_b = zip(*noise_model)
    num_channels = noise_model.shape[0]
    params = []
    for ch, color in enumerate(color_channels):
      prefix = f'.noise_coefficients_{color} = {{'
      spaces = ' ' * len(prefix)
      suffix = '},' if ch != num_channels - 1 else '}'
      params.append(textwrap.dedent(f"""
        {prefix}.gradient_slope = {scale_a[ch]},
        {spaces}.offset_slope = {scale_b[ch]},
        {spaces}.gradient_intercept = {offset_a[ch]},
        {spaces}.offset_intercept = {offset_b[ch]}{suffix}"""))

    with open(file_path, 'w') as text_file:
      # pylint: disable=line-too-long
      code_comment = textwrap.dedent("""\
              /* noise_profile.cc
                Note: gradient_slope --> gradient of API s_measured parameter
                      offset_slope --> o_model of API s_measured parameter
                      gradient_intercept--> gradient of API o_measured parameter
                      offset_intercept --> o_model of API o_measured parameter
                Note: SENSOR_NOISE_PROFILE in Android Developers doc uses
                      N(x) = sqrt(Sx + O), where 'S' is 's_measured' & 'O' is 'o_measured'
              */
      """)
      params_str = textwrap.indent(''.join(params), ' ' * 4)
      code_params = '.profile = {' + params_str + '},'
      code = code_comment + code_params
      text_file.write(code)

  def _create_noise_model_and_profile_code(self, noise_model, sens_min,
                                           sens_max, sens_max_analog, log_path):
    """Creates the code file with noise model parameters.

    Args:
      noise_model: Noise model parameters.
      sens_min: The minimum sensitivity value.
      sens_max: The maximum sensitivity value.
      sens_max_analog: The maximum analog sensitivity value.
      log_path: The path to the log file.
    """
    noise_model_utils.check_noise_model_shape(noise_model)
    # Create noise model code with noise model parameters.
    self._create_noise_model_code(
        noise_model,
        sens_min,
        sens_max,
        sens_max_analog,
        os.path.join(log_path, 'noise_model.c'),
    )

    num_channels = noise_model.shape[0]
    is_quad_bayer = (
        num_channels == noise_model_constants.NUM_QUAD_BAYER_CHANNELS
    )
    if is_quad_bayer:
      # Average noise model parameters of every four channels.
      avg_noise_model = noise_model.reshape(-1, 4, noise_model.shape[1]).mean(
          axis=1
      )
      # Create noise model code with average noise model parameters.
      self._create_noise_model_code(
          avg_noise_model,
          sens_min,
          sens_max,
          sens_max_analog,
          os.path.join(log_path, 'noise_model_avg.c'),
      )
      # Create noise profile code with average noise model parameters.
      self._create_noise_profile_code(
          avg_noise_model,
          _BAYER_COLORS_FOR_NOISE_PROFILE,
          os.path.join(log_path, 'noise_profile_avg.cc'),
      )
      # Create noise profile code with noise model parameters.
      self._create_noise_profile_code(
          noise_model,
          _QUAD_BAYER_COLORS_FOR_NOISE_PROFILE,
          os.path.join(log_path, 'noise_profile.cc'),
      )

    else:
      # Create noise profile code with noise model parameters.
      self._create_noise_profile_code(
          noise_model,
          _BAYER_COLORS_FOR_NOISE_PROFILE,
          os.path.join(log_path, 'noise_profile.cc'),
      )

  def _plot_stats_and_noise_model_fittings(
      self, iso_to_stats_dict, measured_models, noise_model, sens_max_analog,
      folder_path_prefix):
    """Plots the stats (means, vars_) and noise models fittings.

    Args:
      iso_to_stats_dict: A dictionary mapping ISO to a list of tuples of
        exposure time in milliseconds, mean values, and variance values.
      measured_models: A list of measured noise models for each ISO value.
      noise_model: A numpy array of global noise model parameters for all ISO
        values.
      sens_max_analog: The maximum analog sensitivity value.
      folder_path_prefix: The prefix of path to save figures.

    Raises:
      ValueError: If the noise model shape is invalid.
    """
    noise_model_utils.check_noise_model_shape(noise_model)
    # Separate individual noise model components.
    scale_a, scale_b, offset_a, offset_b = zip(*noise_model)

    iso_pidx_to_measured_model_dict = {}
    num_channels = noise_model.shape[0]
    for pidx in range(num_channels):
      for iso, s_measured, o_measured in measured_models[pidx]:
        iso_pidx_to_measured_model_dict[(iso, pidx)] = (s_measured, o_measured)

    isos = np.asarray(sorted(iso_to_stats_dict.keys()))
    digital_gains = noise_model_utils.compute_digital_gains(
        isos, sens_max_analog
    )

    x_range = [0, _MAX_SIGNAL_VALUE]
    for iso, digital_gain in zip(isos, digital_gains):
      logging.info('Plotting stats and noise model for ISO %d.', iso)
      fig, subplots = noise_model_utils.create_stats_figure(
          iso, _COLOR_CHANNEL_NAMES
      )

      xmax = 0
      stats_per_plane = [[] for _ in range(num_channels)]
      for exposure_ms, means, vars_ in iso_to_stats_dict[iso]:
        exposure_norm = noise_model_constants.COLOR_NORM(np.log2(exposure_ms))
        exposure_color = noise_model_constants.RAINBOW_CMAP(exposure_norm)
        for pidx in range(num_channels):
          means_p = means[pidx]
          vars_p = vars_[pidx]

          if means_p.size > 0 and vars_p.size > 0:
            subplots[pidx].plot(
                means_p,
                vars_p,
                color=exposure_color,
                marker='.',
                markeredgecolor=exposure_color,
                markersize=1,
                linestyle='None',
                alpha=0.5,
            )

            stats_per_plane[pidx].extend(list(zip(means_p, vars_p)))
            xmax = max(xmax, max(means_p))

      iso_sq = iso ** 2
      digital_gain_sq = digital_gain ** 2
      for pidx in range(num_channels):
        # Add the final noise model to subplots.
        s_model = scale_a[pidx] * iso * digital_gain + scale_b[pidx]
        o_model = (offset_a[pidx] * iso_sq + offset_b[pidx]) * digital_gain_sq

        plot_color = _PLOT_COLORS[pidx]
        subplots[pidx].plot(
            x_range,
            [o_model, s_model * _MAX_SIGNAL_VALUE + o_model],
            color=plot_color,
            linestyle='-',
            label='Model',
            alpha=0.5,
        )

        # Add the noise model measured by captures with current iso to subplots.
        if (iso, pidx) not in iso_pidx_to_measured_model_dict:
          continue

        s_measured, o_measured = iso_pidx_to_measured_model_dict[(iso, pidx)]

        subplots[pidx].plot(
            x_range,
            [o_measured, s_measured * _MAX_SIGNAL_VALUE + o_measured],
            color=plot_color,
            linestyle='--',
            label='Linear fit',
        )

        ymax = (o_measured + s_measured * xmax) * _MAX_SCALE_FUDGE
        subplots[pidx].set_xlim(xmin=0, xmax=xmax)
        subplots[pidx].set_ylim(ymin=0, ymax=ymax)
        subplots[pidx].legend()

      fig.savefig(
          f'{folder_path_prefix}_samples_iso{iso:04d}.png', dpi=_FIG_DPI
      )

  def _plot_noise_model_single_plane(
      self, pidx, plot, sens, measured_params, modeled_params):
    """Plots the noise model for one color plane specified by pidx.

    Args:
      pidx: The index of the color plane in Bayer pattern.
      plot: The ax to plot on.
      sens: The sensitivity of the sensor.
      measured_params:  The measured parameters.
      modeled_params: The modeled parameters.
    """
    color_channel = _COLOR_CHANNEL_NAMES[pidx]
    measured_label = f'{color_channel}-Measured'
    model_label = f'{color_channel}-Model'

    plot_color = _PLOT_COLORS[pidx]
    # Plot the measured parameters.
    plot.loglog(
        sens,
        measured_params,
        color=plot_color,
        marker='+',
        markeredgecolor=plot_color,
        linestyle='None',
        base=10,
        label=measured_label,
    )
    # Plot the modeled parameters.
    plot.loglog(
        sens,
        modeled_params,
        color=plot_color,
        marker='o',
        markeredgecolor=plot_color,
        linestyle='None',
        base=10,
        label=model_label,
        alpha=0.3,
    )

  def _plot_noise_model(self, isos, measured_models, noise_model,
                        sens_max_analog, name_with_log_path):
    """Plot the noise model for a given set of ISO values.

    The read noise model is defined by the following equation:
      f(x) = s_model * x + o_model
    where we have:
    s_model = scale_a * analog_gain * digital_gain + scale_b is the
    multiplicative factor,
    o_model = (offset_a * analog_gain^2 + offset_b) * digital_gain^2
    is the offset term.

    Args:
      isos: A list of ISO values.
      measured_models: A list of measured models, each of which is a tuple of
        (sens, s_measured, o_measured).
      noise_model: Noise model parameters of each plane, each of which is a
        tuple of (scale_a, scale_b, offset_a, offset_b).
      sens_max_analog: The maximum analog gain.
      name_with_log_path: The name of the file to save the logs to.
    """
    noise_model_utils.check_noise_model_shape(noise_model)

    # Plot noise model parameters.
    fig, axes = plt.subplots(4, 2, figsize=(22, 17))
    s_plots, o_plots = axes[:, 0], axes[:, 1]
    num_channels = noise_model.shape[0]
    is_quad_bayer = (
        num_channels == noise_model_constants.NUM_QUAD_BAYER_CHANNELS
    )
    for pidx, measured_model in enumerate(measured_models):
      # Grab the sensitivities and line parameters of each sensitivity.
      sens, s_measured, o_measured = zip(*measured_model)
      sens = np.asarray(sens)
      sens_sq = np.square(sens)
      scale_a, scale_b, offset_a, offset_b = noise_model[pidx]
      # Plot noise model components with the values predicted by the model.
      digital_gains = noise_model_utils.compute_digital_gains(
          sens, sens_max_analog
      )

      # s_model = scale_a * analog_gain * digital_gain + scale_b,
      # o_model = (offset_a * analog_gain^2 + offset_b) * digital_gain^2.
      s_model = scale_a * sens * digital_gains + scale_b
      o_model = (offset_a * sens_sq + offset_b) * np.square(digital_gains)
      if is_quad_bayer:
        s_plot, o_plot = s_plots[pidx // 4], o_plots[pidx // 4]
      else:
        s_plot, o_plot = s_plots[pidx], o_plots[pidx]

      self._plot_noise_model_single_plane(
          pidx, s_plot, sens, s_measured, s_model)
      self._plot_noise_model_single_plane(
          pidx, o_plot, sens, o_measured, o_model)

    # Set figure attributes after plotting noise model parameters.
    for s_plot, o_plot in zip(s_plots, o_plots):
      s_plot.set_xlabel('ISO')
      s_plot.set_ylabel('S')

      o_plot.set_xlabel('ISO')
      o_plot.set_ylabel('O')

      for sub_plot in (s_plot, o_plot):
        sub_plot.set_xticks(isos)
        # No minor ticks.
        sub_plot.xaxis.set_minor_locator(matplotlib.ticker.NullLocator())
        sub_plot.xaxis.set_major_formatter(matplotlib.ticker.ScalarFormatter())
        sub_plot.legend()

    fig.suptitle('Noise model: N(x) = sqrt(Sx + O)', x=0.54, y=0.99)
    pylab.tight_layout()
    fig.savefig(f'{name_with_log_path}.png', dpi=_FIG_DPI)

  def test_dng_noise_model_generation(self):
    """Calibrates standard Bayer or quad Bayer noise model.

    def requires 'test' in name to actually run.
    This function:
    * Calibrates read noise (optional).
    * Captures stats images of different ISO values and exposure times.
    * Measures linear fittings for each ISO value.
    * Computes and validates overall noise model parameters.
    * Plots noise model parameters figures.
    * Plots stats samples, linear fittings and model fittings.
    * Saves the read noise plot and csv data (optional).
    * Generates noise model and noise profile code.
    """
    read_noise_file_path = capture_read_noise_utils.calibrate_read_noise(
        self.dut.serial,
        self.camera_id,
        self.hidden_physical_id,
        _NAME_READ_NOISE,
        _NAME_READ_NOISE_FILE,
        _READ_NOISE_STEPS_PER_STOP,
        raw_format=_READ_NOISE_RAW_FORMAT,
        is_two_stage_model=_TWO_STAGE_MODEL,
    )

    # Begin DNG Noise Model Calibration
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)
      logging.info('Starting %s for camera %s', _NAME, cam.get_camera_name())

      # Get basic properties we need.
      sens_min, sens_max = props['android.sensor.info.sensitivityRange']
      sens_max_analog = props['android.sensor.maxAnalogSensitivity']
      # Maximum sensitivity for measuring noise model.
      sens_max_meas = sens_max_analog

      # Change the ISO min and/or max values if specified
      if _ISO_MIN_VALUE is not None:
        sens_min = _ISO_MIN_VALUE
      if _ISO_MAX_VALUE is not None:
        sens_max_meas = _ISO_MAX_VALUE

      logging.info('Sensitivity range: [%d, %d]', sens_min, sens_max)
      logging.info('Max analog sensitivity: %d', sens_max_analog)
      logging.info(
          'Sensitivity range for measurement: [%d, %d]',
          sens_min, sens_max_meas,
      )

      offset_a, offset_b = None, None
      read_noise_data = None
      if _TWO_STAGE_MODEL:
        # Check if read noise results exist for this device and camera
        if not os.path.exists(read_noise_file_path):
          raise AssertionError(
              'Read noise results file does not exist for this device. Run'
              ' capture_read_noise_file_path script to gather read noise data'
              ' for current sensor'
          )

        with open(read_noise_file_path, 'rb') as f:
          read_noise_data = pickle.load(f)

        offset_a, offset_b = (
            capture_read_noise_utils.get_read_noise_coefficients(
                read_noise_data,
                sens_min,
                sens_max_meas,
            )
        )

      iso_to_stats_dict = noise_model_utils.capture_stats_images(
          cam,
          props,
          _STATS_CONFIG,
          sens_min,
          sens_max_meas,
          _ZOOM_RATIO,
          _TILE_CROP_N,
          _MAX_SIGNAL_VALUE,
          _ISO_MULTIPLIER,
          _BRACKET_MAX,
          _BRACKET_FACTOR,
          self.log_path,
          stats_file_name=_STATS_FILE_NAME,
          is_remove_var_outliers=_REMOVE_VAR_OUTLIERS,
          outlier_median_abs_deviations=_OUTLIER_MEDIAN_ABS_DEVS,
          is_debug_mode=self.debug_mode,
      )

    measured_models, samples = noise_model_utils.measure_linear_noise_models(
        iso_to_stats_dict,
        _COLOR_CHANNEL_NAMES,
    )

    noise_model = noise_model_utils.compute_noise_model(
        samples,
        sens_max_analog,
        offset_a,
        offset_b,
        _TWO_STAGE_MODEL,
    )

    noise_model_utils.validate_noise_model(
        noise_model,
        _COLOR_CHANNEL_NAMES,
        sens_min,
    )

    self._plot_noise_model(
        sorted(iso_to_stats_dict.keys()),
        measured_models,
        noise_model,
        sens_max_analog,
        name_with_log_path,
    )

    self._plot_stats_and_noise_model_fittings(
        iso_to_stats_dict,
        measured_models,
        noise_model,
        sens_max_analog,
        name_with_log_path,
    )

    # If 2-Stage model is enabled, save the read noise graph and csv data
    if _TWO_STAGE_MODEL:
      # Save the linear plot of the read noise data
      filename = f'{pathlib.Path(_NAME_READ_NOISE_FILE).stem}.png'
      file_path = os.path.join(log_path, filename)
      capture_read_noise_utils.plot_read_noise_data(
          read_noise_data,
          sens_min,
          sens_max_meas,
          file_path,
          _COLOR_CHANNEL_NAMES,
          _PLOT_COLORS,
      )

      # Save the data as a csv file
      filename = f'{pathlib.Path(_NAME_READ_NOISE_FILE).stem}.csv'
      file_path = os.path.join(log_path, filename)
      capture_read_noise_utils.save_read_noise_data_as_csv(
          read_noise_data,
          sens_min,
          sens_max_meas,
          file_path,
          _COLOR_CHANNEL_NAMES,
      )

    # Generate the noise model file.
    self._create_noise_model_and_profile_code(
        noise_model,
        sens_min,
        sens_max,
        sens_max_analog,
        log_path,
    )


if __name__ == '__main__':
  test_runner.main()
