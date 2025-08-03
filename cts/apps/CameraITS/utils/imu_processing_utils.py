# Copyright 2024 The Android Open Source Project
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
"""Utility functions for IMU data processing."""


def calc_rv_drift(data):
  """Calculate data drift accounting for +/-180 degrees for stationary DUT.

  Args:
    data: list of +180/-180 rotation vector data

  Returns:
    list of data-data[0] drift
  """
  data_360 = [i % 360 for i in data]
  drift = []
  for d in data_360:
    if d - data_360[0] <= -180:
      drift.append(d - data_360[0] + 360)
    elif d - data_360[0] > 180:
      drift.append(d - data_360[0] - 360)
    else:
      drift.append(d - data_360[0])
  return drift
