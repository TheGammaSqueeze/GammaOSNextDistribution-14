# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import sys

# We run the tests by disabling compilation with app image.
# Run the test twice: one run for generating the image, a second run for using
# the image.
def run(ctx, args):
  ctx.default_run(args, app_image=False)
  # Pass another argument to let the test know it should now expect an image.
  ctx.default_run(args, app_image=False, test_args=["--second-run"])
