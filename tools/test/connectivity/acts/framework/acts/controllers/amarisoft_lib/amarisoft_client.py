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

import asyncio
import json
import logging
from typing import Any, Mapping, Optional, Tuple

from acts.controllers.amarisoft_lib import ssh_utils
import immutabledict
import websockets

_CONFIG_DIR_MAPPING = immutabledict.immutabledict({
    'enb': '/config/enb.cfg',
    'mme': '/config/mme.cfg',
    'ims': '/config/ims.cfg',
    'mbms': '/config/mbmsgw.cfg',
    'ots': '/config/ots.cfg'
})


class MessageFailureError(Exception):
  """Raises an error when the message execution fail."""


class AmariSoftClient(ssh_utils.RemoteClient):
  """The SSH client class interacts with Amarisoft.

    A simulator used to simulate the base station can output different signals
    according to the network configuration settings.
    For example: T Mobile NSA LTE band 66 + NR band 71.
  """

  async def _send_message_to_callbox(self, uri: str,
                                     msg: str) -> Tuple[str, str]:
    """Implements async function for send message to the callbox.

    Args:
      uri: The uri of specific websocket interface.
      msg: The message to be send to callbox.

    Returns:
      The response from callbox.
    """
    async with websockets.connect(
        uri, extra_headers={'origin': 'Test'}) as websocket:
      await websocket.send(msg)
      head = await websocket.recv()
      body = await websocket.recv()
    return head, body

  def send_message(self, port: str, msg: str) -> Tuple[str, str]:
    """Sends a message to the callbox.

    Args:
      port: The port of specific websocket interface.
      msg: The message to be send to callbox.

    Returns:
      The response from callbox.
    """
    return asyncio.get_event_loop().run_until_complete(
        self._send_message_to_callbox(f'ws://{self.host}:{port}/', msg))

  def verify_response(self, func: str, head: str,
                      body: str) -> Tuple[Mapping[str, Any], Mapping[str, Any]]:
    """Makes sure there are no error messages in Amarisoft's response.

    If a message produces an error, response will have an error string field
    representing the error.
    For example:
      {
        "message": "ready",
        "message_id": <message id>,
        "error": <error message>,
        "type": "ENB",
        "name: <name>,
      }

    Args:
      func: The message send to Amarisoft.
      head: Responsed message head.
      body: Responsed message body.

    Returns:
      Standard output of the shell command.

    Raises:
       MessageFailureError: Raised when an error occurs in the response message.
    """
    loaded_head = json.loads(head)
    loaded_body = json.loads(body)

    if loaded_head.get('message') != 'ready':
      raise MessageFailureError(
          f'Fail to get response from callbox, message: {loaded_head["error"]}')
    if 'error' in loaded_body:
      raise MessageFailureError(
          f'Fail to excute {func} with error message: {loaded_body["error"]}')
    if loaded_body.get('message') != func:
      raise MessageFailureError(
          f'The message sent was {loaded_body["message"]} instead of {func}.')
    return loaded_head, loaded_body

  def lte_service_stop(self) -> None:
    """Stops to output signal."""
    self.run_cmd('systemctl stop lte')

  def lte_service_start(self):
    """Starts to output signal."""
    self.run_cmd('systemctl start lte')

  def lte_service_restart(self):
    """Restarts to output signal."""
    self.run_cmd('systemctl restart lte')

  def lte_service_enable(self):
    """lte service remains enable until next reboot."""
    self.run_cmd('systemctl enable lte')

  def lte_service_disable(self):
    """lte service remains disable until next reboot."""
    self.run_cmd('systemctl disable lte')

  def lte_service_is_active(self) -> bool:
    """Checks lte service is active or not.

    Returns:
      True if service active, False otherwise.
    """
    return not any('inactive' in line
                   for line in self.run_cmd('systemctl is-active lte'))

  def set_config_dir(self, cfg_type: str, path: str) -> None:
    """Sets the path of target configuration file.

    Args:
      cfg_type: The type of target configuration. (e.g. mme, enb ...etc.)
      path: The path of target configuration. (e.g.
        /root/lteenb-linux-2020-12-14)
    """
    path_old = self.get_config_dir(cfg_type)
    if path != path_old:
      logging.info('set new path %s (was %s)', path, path_old)
      self.run_cmd(f'ln -sfn {path} /root/{cfg_type}')
    else:
      logging.info('path %s does not change.', path_old)

  def get_config_dir(self, cfg_type: str) -> Optional[str]:
    """Gets the path of target configuration.

    Args:
      cfg_type: Target configuration type. (e.g. mme, enb...etc.)

    Returns:
      The path of configuration.
    """
    result = self.run_cmd(f'readlink /root/{cfg_type}')
    if result:
      path = result[0].strip()
    else:
      logging.warning('%s path not found.', cfg_type)
      return None
    return path

  def set_config_file(self, cfg_type: str, cfg_file: str) -> None:
    """Sets the configuration to be executed.

    Args:
      cfg_type: The type of target configuration. (e.g. mme, enb...etc.)
      cfg_file: The configuration to be executed. (e.g.
        /root/lteenb-linux-2020-12-14/config/gnb.cfg )

    Raises:
      FileNotFoundError: Raised when a file or directory is requested but
      doesn’t exist.
    """
    cfg_link = self.get_config_dir(cfg_type) + _CONFIG_DIR_MAPPING[cfg_type]
    if not self.is_file_exist(cfg_file):
      raise FileNotFoundError("The command file doesn't exist")
    self.run_cmd(f'ln -sfn {cfg_file} {cfg_link}')

  def get_config_file(self, cfg_type: str) -> Optional[str]:
    """Gets the current configuration of specific configuration type.

    Args:
      cfg_type: The type of target configuration. (e.g. mme, enb...etc.)

    Returns:
      The current configuration with absolute path.
    """
    cfg_path = self.get_config_dir(cfg_type) + _CONFIG_DIR_MAPPING[cfg_type]
    if cfg_path:
      result = self.run_cmd(f'readlink {cfg_path}')
      if result:
        return result[0].strip()

  def get_all_config_dir(self) -> Mapping[str, str]:
    """Gets all configuration directions.

    Returns:
      All configuration directions.
    """
    config_dir = {}
    for cfg_type in ('ots', 'enb', 'mme', 'mbms'):
      config_dir[cfg_type] = self.get_config_dir(cfg_type)
      logging.debug('get path of %s: %s', cfg_type, config_dir[cfg_type])
    return config_dir
