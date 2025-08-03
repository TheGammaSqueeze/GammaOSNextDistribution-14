"""Ssh module for checking status, starting and closing apps."""
import logging
import re
import paramiko  # type: ignore

_LOG = logging.getLogger(__name__)


class SshLibrary:
  """Library for creating a ssh connection, closing and opening apps."""

  _PSEXEC_PROC_STARTED_REGEX_FORMAT = 'started on * with process ID {proc_id}'

  _SSH_START_APP_CMD_FORMAT = 'psexec -s -d -i 1 "{exe_path}"'
  _SSH_CHECK_APP_RUNNING_CMD_FORMAT = (
      'tasklist /fi "ImageName eq {regex_app_name}"'
  )
  _SSH_KILL_PROCESS_BY_NAME = 'taskkill /IM {process_name} /F'

  def __init__(self, hostname: str, username: str):
    self.log = _LOG
    self.ssh = self.create_ssh_socket(hostname, username)

  def create_ssh_socket(
      self, hostname: str, username: str
  ) -> paramiko.SSHClient:
    """Creates ssh session to host.

    Args:
      hostname: IP address of the host machine.
      username: Username of the host ims account.

    Returns:
      An SSHClient object connected the hostname.
    """

    self.log.info('Creating ssh session to hostname:%s ', hostname)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.load_system_host_keys()
    ssh.connect(hostname=hostname, username=username)
    self.log.info('SSH client to hostname:%s is connected', hostname)
    return ssh

  def run_command_paramiko(self, command: str) -> tuple[str, str, str]:
    """Runs a command using Paramiko and return stdout code.

    Args:
      command: Command to run on the connected host.

    Returns:
      A tuple containing the command result output, error information, and exit
      status.
    """

    self.log.info('Running command: command:%s', command)
    stdin, stdout, stderr = self.ssh.exec_command(command, timeout=10)
    stdin.close()
    err = ''.join(stderr.readlines())
    out = ''.join(stdout.readlines())

    # psexec return process ID as part of the exit code
    exit_status = stderr.channel.recv_exit_status()
    if err:
      self.log.error(str(err))
    else:
      self.log.info(str(out))
    return out, err, str(exit_status)

  def close_ssh_connection(self):
    """Closes ssh connection."""

    self.log.info('Closing ssh connection')
    self.ssh.close()

  def close_app(self, app: str) -> str:
    """Closes any app whose name passed as an argument.

    Args:
      app: Application name.

    Returns:
      Resulting output of closing the application.
    """

    command = self._SSH_KILL_PROCESS_BY_NAME.format(process_name=app)
    result, _, _ = self.run_command_paramiko(command)
    return result

  def start_app(self, app: str, location: str) -> str:
    """Starts any app whose name passed as an argument.

    Args:
      app: Application name.
      location: Directory location of the application.

    Returns:
      Resulting output of starting the application.

    Raises:
      RuntimeError:
        Application failed to start.
    """

    command = self._SSH_START_APP_CMD_FORMAT.format(exe_path=location + app)
    results, err, exit_status = self.run_command_paramiko(command)

    id_in_err = re.search(
        self._PSEXEC_PROC_STARTED_REGEX_FORMAT.format(proc_id=exit_status),
        err[-1],
    )
    if id_in_err:
      raise RuntimeError('Fail to start app: ' + results + err)

    return results

  def check_app_running(self, app: str) -> bool:
    """Checks if the given app is running.

    Args:
      app: Application name.

    Returns:
      A boolean representing if the application is running or not.
    """
    is_running_cmd1 = self._SSH_CHECK_APP_RUNNING_CMD_FORMAT.format(
        regex_app_name=app
    )

    result, _, _ = self.run_command_paramiko(is_running_cmd1)
    return 'PID' in result
