#!/usr/bin/env python3

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Generate module graph json data for testing purposes."""


def make_dep(name, tag=None, variations=None):
  return {
      'Name': name,
      'Tag': tag,
      'Variations': variations,
  }


def make_variation(mutator, variation):
  return {
      'Mutator': mutator,
      'Variation': variation,
  }


def make_module(
    name,
    typ,
    deps=[],
    blueprint='',
    variations=None,
    created_by='',
    json_props=[],
):
  return {
      'Name': name,
      'Type': typ,
      'Blueprint': blueprint,
      'CreatedBy': created_by,
      'Deps': deps,
      'Variations': variations,
      'Module': {
          'Android': {
              'SetProperties': json_props,
          },
      },
  }


def make_property(name, value='', values=None):
  return {
      'Name': name,
      'Value': value,
      'Values': values,
  }
