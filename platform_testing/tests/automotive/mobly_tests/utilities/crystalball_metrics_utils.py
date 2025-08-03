#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import os
import logging
from typing import Any, Dict

# Keep consistent with
# https://g3doc.corp.google.com/wireless/android/test_tools/crystalball/cbpp/README.md
CB_FILENAME = 'test_results.txt'


def export_to_crystalball(data: Dict[str, Any], output_dir: str, test_name: str) -> None:
    """Writes data to a CrystalBall output file.

    Repeated calls with the same output dir will append data to the same file.

    :param data: input data
    :param output_dir: directory of the output file
    :param test_name: name used by CrystalBall to identify the set of metrics
    :return: None
    """
    cb_path = os.path.join(output_dir, CB_FILENAME)
    logging.debug(f'Exporting test metrics of {test_name} to CrystalBall at {cb_path}')
    with open(cb_path, 'a') as f:
        f.write(test_name + '\n\n')
        f.writelines(['%s:%s\n' % (key, value) for key, value in data.items()])
        f.write('\n\n')
