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

import logging
from typing import Sequence

import paramiko

COMMAND_RETRY_TIMES = 3


class RunCommandError(Exception):
  """Raises an error when run command fail."""


class NotConnectedError(Exception):
  """Raises an error when run command without SSH connect."""


class RemoteClient:
  """The SSH client class interacts with the test machine.

  Attributes:
    host: A string representing the IP address of amarisoft.
    port: A string representing the default port of SSH.
    username: A string representing the username of amarisoft.
    password: A string representing the password of amarisoft.
    ssh: A SSH client.
    sftp: A SFTP client.
  """

  def __init__(self,
               host: str,
               username: str,
               password: str,
               port: str = '22') -> None:
    self.host = host
    self.port = port
    self.username = username
    self.password = password
    self.ssh = paramiko.SSHClient()
    self.sftp = None

  def ssh_is_connected(self) -> bool:
    """Checks SSH connect or not.

    Returns:
      True if SSH is connected, False otherwise.
    """
    return self.ssh and self.ssh.get_transport().is_active()

  def ssh_close(self) -> bool:
    """Closes the SSH connection.

    Returns:
      True if ssh session closed, False otherwise.
    """
    for _ in range(COMMAND_RETRY_TIMES):
      if self.ssh_is_connected():
        self.ssh.close()
      else:
        return True
    return False

  def connect(self) -> bool:
    """Creats SSH connection.

    Returns:
      True if success, False otherwise.
    """
    for _ in range(COMMAND_RETRY_TIMES):
      try:
        self.ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.ssh.connect(self.host, self.port, self.username, self.password)
        self.ssh.get_transport().set_keepalive(1)
        self.sftp = paramiko.SFTPClient.from_transport(self.ssh.get_transport())
        return True
      except Exception:  # pylint: disable=broad-except
        self.ssh_close()
    return False

  def run_cmd(self, cmd: str) -> Sequence[str]:
    """Runs shell command.

    Args:
      cmd: Command to be executed.

    Returns:
      Standard output of the shell command.

    Raises:
       RunCommandError: Raise error when command failed.
       NotConnectedError: Raised when run command without SSH connect.
    """
    if not self.ssh_is_connected():
      raise NotConnectedError('ssh remote has not been established')

    logging.debug('ssh remote -> %s', cmd)
    _, stdout, stderr = self.ssh.exec_command(cmd)
    err = stderr.readlines()
    if err:
      logging.error('command failed.')
      raise RunCommandError(err)
    return stdout.readlines()

  def is_file_exist(self, file: str) -> bool:
    """Checks target file exist.

    Args:
        file: Target file with absolute path.

    Returns:
        True if file exist, false otherwise.
    """
    return any('exist' in line for line in self.run_cmd(
        f'if [ -f "{file}" ]; then echo -e "exist"; fi'))

  def sftp_upload(self, src: str, dst: str) -> bool:
    """Uploads a local file to remote side.

    Args:
      src: The target file with absolute path.
      dst: The absolute path to put the file with file name.
      For example:
        upload('/usr/local/google/home/zoeyliu/Desktop/sample_config.yml',
        '/root/sample_config.yml')

    Returns:
      True if file upload success, False otherwise.

    Raises:
       NotConnectedError: Raised when run command without SSH connect.
    """
    if not self.ssh_is_connected():
      raise NotConnectedError('ssh remote has not been established')
    if not self.sftp:
      raise NotConnectedError('sftp remote has not been established')

    logging.info('[local] %s -> [remote] %s', src, dst)
    self.sftp.put(src, dst)
    return self.is_file_exist(dst)

  def sftp_download(self, src: str, dst: str) -> bool:
    """Downloads a file to local.

    Args:
      src: The target file with absolute path.
      dst: The absolute path to put the file.

    Returns:
      True if file download success, False otherwise.

    Raises:
       NotConnectedError: Raised when run command without SSH connect.
    """
    if not self.ssh_is_connected():
      raise NotConnectedError('ssh remote has not been established')
    if not self.sftp:
      raise NotConnectedError('sftp remote has not been established')

    logging.info('[remote] %s -> [local] %s', src, dst)
    self.sftp.get(src, dst)
    return self.is_file_exist(dst)

  def sftp_list_dir(self, path: str) -> Sequence[str]:
    """Lists the names of the entries in the given path.

    Args:
      path: The path of the list.

    Returns:
      The names of the entries in the given path.

    Raises:
       NotConnectedError: Raised when run command without SSH connect.
    """
    if not self.ssh_is_connected():
      raise NotConnectedError('ssh remote has not been established')
    if not self.sftp:
      raise NotConnectedError('sftp remote has not been established')
    return sorted(self.sftp.listdir(path))

