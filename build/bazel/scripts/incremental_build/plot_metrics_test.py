# Copyright (C) 2023 The Android Open Source Project
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
import unittest
from pathlib import Path

from plot_metrics import prepare_script


class PlotMetricsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.csv_data = [
            "cuj,targets,SOONG_ONLY,MIXED_PROD,MIXED_STAGING",
            "c1 WARMUP,libc adbd,5:01,7:23,9:01",
            "c1,libc adbd,5:01,7:23,9:01",
            "c1 rebuild,libc adbd,5:01,7:23,9:01",
        ]
        self.script = prepare_script("\n".join(self.csv_data), Path("blah"))

    def test_prepare_script_filters_data(self):
        filtered = "\n".join([self.csv_data[0], self.csv_data[2]])
        self.assertTrue(f"$data << EOD\n{filtered}\nEOD" in self.script)

    def test_prepare_script_covers_each_build_type_column(self):
        self.assertTrue(r"plot for[i=3:5] $data using" in self.script)


if __name__ == "__main__":
    unittest.main()
