#!/usr/bin/env python3
#
#   Copyright 2022 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import enum
import os
import immutabledict

from acts.controllers.amarisoft_lib import amarisoft_client

TEMPLATE_PATH = os.path.dirname(os.path.abspath(__file__)) + '/config_templates'
TEMPLATE_PATH_ENB = f'{TEMPLATE_PATH}/enb/'
TEMPLATE_PATH_MME = f'{TEMPLATE_PATH}/mme/'

_CLIENT_CONFIG_DIR_MAPPING = immutabledict.immutabledict({
    'enb': '/config/mhtest_enb.cfg',
    'mme': '/config/mhtest_mme.cfg',
})


class EnbCfg():
  """MME configuration templates."""
  ENB_GENERIC = 'enb-single-generic.cfg'
  GNB_NSA_GENERIC = 'gnb-nsa-lte-ho-generic.cfg'
  GNB_SA_GENERIC = 'gnb-sa-lte-ho-generic.cfg'


class MmeCfg():
  """MME configuration templates."""
  MME_GENERIC = 'mme-generic.cfg'


class SpecTech(enum.Enum):
  """Spectrum usage techniques."""
  FDD = 0
  TDD = 1


class ConfigUtils():
  """Utilities for set Amarisoft configs.

  Attributes:
    remote: An amarisoft client.
  """

  def __init__(self, remote: amarisoft_client.AmariSoftClient):
    self.remote = remote

  def upload_enb_template(self, cfg: str) -> bool:
    """Loads ENB configuration.

    Args:
      cfg: The ENB configuration to be loaded.

    Returns:
      True if the ENB configuration was loaded successfully, False otherwise.
    """
    cfg_template = TEMPLATE_PATH_ENB + cfg
    if not os.path.isfile(cfg_template):
      return False
    cfg_path = self.remote.get_config_dir(
        'enb') + _CLIENT_CONFIG_DIR_MAPPING['enb']
    self.remote.run_cmd('rm -f ' + cfg_path)
    self.remote.sftp_upload(cfg_template, cfg_path)
    self.remote.set_config_file('enb', cfg_path)
    if not self.remote.is_file_exist(cfg_path):
      return False
    return True

  def upload_mme_template(self, cfg: str) -> bool:
    """Loads MME configuration.

    Args:
      cfg: The MME configuration to be loaded.

    Returns:
      True if the ENB configuration was loaded successfully, False otherwise.
    """
    cfg_template = TEMPLATE_PATH_MME + cfg
    if not os.path.isfile(cfg_template):
      return False
    cfg_path = self.remote.get_config_dir(
        'mme') + _CLIENT_CONFIG_DIR_MAPPING['mme']
    self.remote.run_cmd('rm -f ' + cfg_path)
    self.remote.sftp_upload(cfg_template, cfg_path)
    self.remote.set_config_file('mme', cfg_path)
    if not self.remote.is_file_exist(cfg_path):
      return False
    return True

  def enb_set_plmn(self, plmn: str) -> bool:
    """Sets the PLMN in ENB configuration.

    Args:
      plmn: The PLMN to be set. ex: 311480

    Returns:
      True if set PLMN successfully, False otherwise.
    """
    cfg_path = self.remote.get_config_dir(
        'enb') + _CLIENT_CONFIG_DIR_MAPPING['enb']
    if not self.remote.is_file_exist(cfg_path):
      return False
    string_from = '#define PLMN \"00101\"'
    string_to = f'#define PLMN \"{plmn}\"'
    self.remote.run_cmd(f'sed -i \'s/\\r//g\' {cfg_path}')
    self.remote.run_cmd(
        f'sed -i \':a;N;$!ba;s/{string_from}/{string_to}/g\' {cfg_path}')
    return True

  def mme_set_plmn(self, plmn: str) -> bool:
    """Sets the PLMN in MME configuration.

    Args:
      plmn: The PLMN to be set. ex:'311480'

    Returns:
      True if set PLMN successfully, False otherwise.
    """
    cfg_path = self.remote.get_config_dir(
        'mme') + _CLIENT_CONFIG_DIR_MAPPING['mme']
    if not self.remote.is_file_exist(cfg_path):
      return False
    string_from = '#define PLMN \"00101\"'
    string_to = f'#define PLMN \"{plmn}\"'
    self.remote.run_cmd(f'sed -i \'s/\\r//g\' {cfg_path}')
    self.remote.run_cmd(
        f'sed -i \':a;N;$!ba;s/{string_from}/{string_to}/g\' {cfg_path}')
    return True

  def enb_set_fdd_arfcn(self, arfcn: int) -> bool:
    """Sets the FDD ARFCN in ENB configuration.

    Args:
      arfcn: The arfcn to be set. ex: 1400

    Returns:
      True if set FDD ARFCN successfully, False otherwise.
    """
    cfg_path = self.remote.get_config_dir(
        'enb') + _CLIENT_CONFIG_DIR_MAPPING['enb']
    if not self.remote.is_file_exist(cfg_path):
      return False
    string_from = '#define FDD_CELL_earfcn 1400'
    string_to = f'#define FDD_CELL_earfcn {arfcn}'
    self.remote.run_cmd(f'sed -i \'s/\\r//g\' {cfg_path}')
    self.remote.run_cmd(
        f'sed -i \':a;N;$!ba;s/{string_from}/{string_to}/g\' {cfg_path}')
    return True

  def enb_set_tdd_arfcn(self, arfcn: int) -> bool:
    """Sets the TDD ARFCN in ENB configuration.

    Args:
      arfcn: The arfcn to be set. ex: 1400

    Returns:
      True if set FDD ARFCN successfully, False otherwise.
    """
    cfg_path = self.remote.get_config_dir(
        'enb') + _CLIENT_CONFIG_DIR_MAPPING['enb']
    if not self.remote.is_file_exist(cfg_path):
      return False
    string_from = '#define TDD_CELL_earfcn 40620'
    string_to = f'#define TDD_CELL_earfcn {arfcn}'
    self.remote.run_cmd(f'sed -i \'s/\\r//g\' {cfg_path}')
    self.remote.run_cmd(
        f'sed -i \':a;N;$!ba;s/{string_from}/{string_to}/g\' {cfg_path}')
    return True

  def enb_set_spectrum_tech(self, tech: int) -> bool:
    """Sets the spectrum usage techniques in ENB configuration.

    Args:
      tech: the spectrum usage techniques. ex: SpecTech.FDD.name

    Returns:
      True if set spectrum usage techniques successfully, False otherwise.
    """
    cfg_path = self.remote.get_config_dir(
        'enb') + _CLIENT_CONFIG_DIR_MAPPING['enb']
    if not self.remote.is_file_exist(cfg_path):
      return False
    string_from = '#define TDD 0'
    string_to = f'#define TDD {tech}'
    self.remote.run_cmd(f'sed -i \'s/\\r//g\' {cfg_path}')
    self.remote.run_cmd(
        f'sed -i \':a;N;$!ba;s/{string_from}/{string_to}/g\' {cfg_path}')
    return True
