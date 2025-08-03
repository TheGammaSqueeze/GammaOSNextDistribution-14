# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Profile proxy base module."""

from mmi2grpc._helpers import format_function


class ProfileProxy:
    """Profile proxy base class."""

    def interact(
            self, test: str, mmi_name: str, mmi_description: str,
            pts_addr: bytes):
        """Translate a MMI call to its corresponding implementation.

        Args:
            test: PTS test id.
            mmi_name: MMI name.
            mmi_description: MMI description.
            pts_addr: Bluetooth MAC address of the PTS in bytes.

        Raises:
            AttributeError: the MMI is not implemented.
        """
        try:
            return getattr(self, mmi_name)(
                test=test, description=mmi_description, pts_addr=pts_addr)
        except AttributeError:
            code = format_function(mmi_name, mmi_description)
            assert False, f'Unhandled mmi {id}\n{code}'
