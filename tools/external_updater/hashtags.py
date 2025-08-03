# Copyright (C) 2020 The Android Open Source Project
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
"""Find main reviewers for git push commands."""

from pathlib import Path

def find_hashtag(proj_path: Path) -> str:
    """Returns an empty string or a hashtag for git push."""
    if str(proj_path).find('/external/rust/') != -1:
        return 'external_updater_rust'
    return 'external_updater'
