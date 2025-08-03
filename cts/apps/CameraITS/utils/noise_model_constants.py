# Copyright 2024 The Android Open Source Project.

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
"""Noise model constants."""


import matplotlib.colors
import matplotlib.pyplot as plt

# Standard Bayer color channel names in canonical order.
BAYER_COLORS = ('R', 'Gr', 'Gb', 'B')

# Standard Bayer color filter arrangements dictionary.
# The keys are color filter arrangement indices and values are dictionaries
# mapping standard Bayer color channels to indices.
BAYER_COLOR_FILTER_MAP = {
    0: {'R': 0, 'Gr': 1, 'Gb': 2, 'B': 3},
    1: {'Gr': 0, 'R': 1, 'B': 2, 'Gb': 3},
    2: {'Gb': 0, 'B': 1, 'R': 2, 'Gr': 3},
    3: {'B': 0, 'Gb': 1, 'Gr': 2, 'R': 3},
}

# Colors for plotting standard Bayer noise model parameters of each channel.
BAYER_PLOT_COLORS = ('red', 'green', 'black', 'blue')

# Quad Bayer color channel names in canonical order.
QUAD_BAYER_COLORS = (
    'R0', 'R1', 'R2', 'R3',
    'Gr0', 'Gr1', 'Gr2', 'Gr3',
    'Gb0', 'Gb1', 'Gb2', 'Gb3',
    'B0', 'B1', 'B2', 'B3',
)

# Quad Bayer color filter arrangements dictionary.
# The keys are color filter arrangement indices and values are dictionaries
# mapping quad Bayer color channels to indices.
QUAD_BAYER_COLOR_FILTER_MAP = {
    0: {
        'R': [0, 1, 4, 5],
        'Gr': [2, 3, 6, 7],
        'Gb': [8, 9, 12, 13],
        'B': [10, 11, 14, 15],
    },
    1: {
        'Gr': [0, 1, 4, 5],
        'R': [2, 3, 6, 7],
        'B': [8, 9, 12, 13],
        'Gb': [10, 11, 14, 15],
    },
    2: {
        'Gb': [0, 1, 4, 5],
        'B': [2, 3, 6, 7],
        'R': [8, 9, 12, 13],
        'Gr': [10, 11, 14, 15],
    },
    3: {
        'B': [0, 1, 4, 5],
        'Gb': [2, 3, 6, 7],
        'Gr': [8, 9, 12, 13],
        'R': [10, 11, 14, 15],
    },
}

# Colors for plotting noise model parameters of each quad Bayer channel.
QUAD_BAYER_PLOT_COLORS = (
    'pink', 'magenta', 'red', 'darkred',
    'lightgreen', 'greenyellow', 'lime', 'green',
    'orange', 'yellow', 'grey', 'black',
    'lightblue', 'cyan', 'blue', 'darkblue',
)

NUM_BAYER_CHANNELS = len(BAYER_COLORS)
NUM_QUAD_BAYER_CHANNELS = len(QUAD_BAYER_COLORS)
VALID_NUM_CHANNELS = (NUM_BAYER_CHANNELS, NUM_QUAD_BAYER_CHANNELS)
VALID_RAW_STATS_FORMATS = (
    'rawStats', 'rawQuadBayerStats',
    'raw10Stats', 'raw10QuadBayerStats',
)

# Rainbow color map used to plot stats samples of different exposure times.
RAINBOW_CMAP = plt.cm.rainbow
# Assume the maximum exposure time is 2^12 ms for calibration.
COLOR_NORM = matplotlib.colors.Normalize(vmin=0, vmax=12)
COLOR_BAR = plt.cm.ScalarMappable(cmap=RAINBOW_CMAP, norm=COLOR_NORM)
OUTLIER_MEDIAN_ABS_DEVS_DEFAULT = 3
