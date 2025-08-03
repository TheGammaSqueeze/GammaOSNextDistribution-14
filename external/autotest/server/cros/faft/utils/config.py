# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import logging
import os

import common
from autotest_lib.client.common_lib import error


# Path to the local checkout of the fw-testing-configs repo
_CONFIG_DIR = os.path.abspath(os.path.join(
        os.path.dirname(os.path.realpath(__file__)), os.pardir,
        'fw-testing-configs'))
_CONSOLIDATED_JSON_BASENAME = 'CONSOLIDATED.json'


def _consolidated_json_fp():
    """Get the absolute path to CONSOLIDATED.json."""
    return os.path.join(_CONFIG_DIR, _CONSOLIDATED_JSON_BASENAME)


class Config(object):
    """Configuration for FAFT tests.

    This object is meant to be the interface to all configuration required
    by FAFT tests, including device specific overrides.

    It gets the values from the JSON files in _CONFIG_DIR.
    Default values are declared in the DEFAULTS.json.
    Platform-specific overrides come from <platform>.json.
    If the platform has model-specific overrides, then those take precedence
    over the platform's config.
    If the platform inherits overrides from a parent platform, then the child
    platform's overrides take precedence over the parent's.

    @ivar platform: string containing the board name being tested.
    @ivar model: string containing the model name being tested
    """

    def __init__(self, platform, model=None):
        """Initialize an object with FAFT settings.
        Load JSON in order of importance (model, platform, parent/s, DEFAULTS).

        @param platform: The name of the platform being tested.
        """
        self._precedence_list = []
        self._precedence_names = []
        with open(_consolidated_json_fp()) as f:
            consolidated_json = json.load(f)
        # Load the most specific JSON config possible by splitting `platform`
        # at '_'/'-' and reversing ([::-1]). For example, veyron_minnie should
        # load minnie.json. octopus_fleex should look for fleex.json. It
        # doesn't exist, so instead it loads octopus.json.
        platform = platform.lower().replace('-', '_')
        for p in platform.rsplit('_')[::-1]:
            logging.debug('Looking for %s config', p)
            if p in consolidated_json:
                logging.info('Found %s config', p)
                self.platform = p
                break
        else:
            self.platform = platform
        if self.platform in consolidated_json:
            platform_config = consolidated_json[self.platform]
            seen_platforms = [self.platform]
            self._add_cfg_to_precedence(self.platform, platform_config)
            model_configs = platform_config.get('models', {})
            model_config = model_configs.get(str(model), None)
            if model_config is not None:
                self._add_cfg_to_precedence(
                        'MODEL:%s' % model, model_config, prepend=True)
                logging.debug('Using model override for %s', model)
            parent_platform = self._precedence_list[-1].get('parent', None)
            while parent_platform is not None:
                if parent_platform in seen_platforms:
                    loop = ' -> '.join(seen_platforms + [parent_platform])
                    raise error.TestError('fw-testing-configs for platform %s '
                                          'contains an inheritance loop: %s' % (
                                          self.platform, loop))
                parent_config = consolidated_json[parent_platform]
                seen_platforms.append(parent_platform)
                self._add_cfg_to_precedence(parent_platform, parent_config)
                parent_platform = self._precedence_list[-1].get('parent', None)
        else:
            logging.debug('Platform %s not found in %s. Using DEFAULTS.',
                          self.platform, consolidated_json)
        default_config = consolidated_json['DEFAULTS']
        self._add_cfg_to_precedence('DEFAULTS', default_config)

        # Set attributes
        all_attributes = self._precedence_list[-1].keys()
        self.attributes = {}
        self.attributes['platform'] = self.platform
        for attribute in all_attributes:
            if attribute.endswith('.DOC') or attribute == 'models':
                continue
            for config_dict in self._precedence_list:
                if attribute in config_dict:
                    self.attributes[attribute] = config_dict[attribute]
                    break

    def _add_cfg_to_precedence(self, cfg_name, cfg, prepend=False):
        """Add a configuration to self._precedence_list.

        @ivar cfg_name: The name of the config.
        @ivar cfg: The config dict.
        @ivar prepend: If true, add to the beginning of self._precedence_list.
                       Otherwise, add it to the end.
        """
        position = 0 if prepend else len(self._precedence_list)
        self._precedence_list.insert(position, cfg)
        self._precedence_names.insert(position, cfg_name)

    def __getattr__(self, attr):
        if attr in self.attributes:
            return self.attributes[attr]
        raise AttributeError('FAFT config has no attribute named %s' % attr)

    def __str__(self):
        str_list = []
        str_list.append('----------[ FW Testing Config Variables ]----------')
        str_list.append('--- Precedence list: %s ---' % self._precedence_names)
        for attr in sorted(self.attributes):
            str_list.append('  %s: %s' % (attr, self.attributes[attr]))
        str_list.append('---------------------------------------------------')
        return '\n'.join(str_list)
