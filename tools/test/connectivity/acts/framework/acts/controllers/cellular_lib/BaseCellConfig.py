#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.


class BaseCellConfig:
    """ Base cell configuration class.

    Attributes:
      output_power: a float indicating the required signal level at the
          instrument's output.
      input_power: a float indicating the required signal level at the
          instrument's input.
    """
    # Configuration dictionary keys
    PARAM_UL_PW = 'pul'
    PARAM_DL_PW = 'pdl'

    def __init__(self, log):
        """ Initialize the base station config by setting all its
            parameters to None.
        Args:
            log: logger object.
        """
        self.log = log
        self.output_power = None
        self.input_power = None
        self.band = None

    def incorporate(self, new_config):
        """ Incorporates a different configuration by replacing the current
            values with the new ones for all the parameters different to None.
        Args:
            new_config: 5G cell configuration object.
        """
        for attr, value in vars(new_config).items():
            if value is not None:
                setattr(self, attr, value)
