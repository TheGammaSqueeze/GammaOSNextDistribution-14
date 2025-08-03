#!/usr/bin/env python
#
# Copyright 2021 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the',  help='License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an',  help='AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import datetime
import logging
import time


class TimeFormatter(logging.Formatter):
  """A formatter used by the build system that:

  - Strips whitespace.
  - Formats time since start
  """

  def __init__(self, fmt=None):
    self.start_time = time.time()
    super(TimeFormatter, self).__init__(fmt)

  def formatTime(self, record, datefmt=None):
    fmt = datefmt or "%H:%M:%S"
    ct = self.converter(record.created)
    dt = datetime.timedelta(seconds=record.created - self.start_time)
    mm, ss = divmod(dt.total_seconds(), 60)
    _, mm = divmod(mm, 60)
    # 2 digit precision is sufficient.
    dt_fmt = "%02d:%02d.%-2d" % (mm, ss, dt.microseconds % 100)
    return "{}({})".format(time.strftime(fmt, ct), dt_fmt)

  def format(self, record):
    record.msg = str(record.msg).strip()
    return super(TimeFormatter, self).format(record)
