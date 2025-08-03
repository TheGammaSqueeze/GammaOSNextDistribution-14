#  Copyright (C) 2020 The Android Open Source Project
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
"""Utility functions for atest."""
from __future__ import print_function

import getpass
import logging
import os
import subprocess
import time
import uuid

try:
  import httplib2
except ModuleNotFoundError as e:
  logging.debug('Import error due to %s', e)

from pathlib import Path
from socket import socket

try:
  from oauth2client import client as oauth2_client
  from oauth2client.contrib import multiprocess_file_storage
  from oauth2client import tools as oauth2_tools
except ModuleNotFoundError as e:
  logging.debug('Import error due to %s', e)

from atest.atest_enum import DetectType
from atest.logstorage import logstorage_utils
from atest.metrics import metrics
from atest import atest_utils
from atest import constants


class RunFlowFlags:
  """Flags for oauth2client.tools.run_flow."""

  def __init__(self, browser_auth):
    self.auth_host_port = [8080, 8090]
    self.auth_host_name = 'localhost'
    self.logging_level = 'ERROR'
    self.noauth_local_webserver = not browser_auth


class GCPHelper:
  """GCP bucket helper class."""

  def __init__(
      self,
      client_id=None,
      client_secret=None,
      user_agent=None,
      scope=constants.SCOPE_BUILD_API_SCOPE,
  ):
    """Init stuff for GCPHelper class.

    Args:
        client_id: String, client id from the cloud project.
        client_secret: String, client secret for the client_id.
        user_agent: The user agent for the credential.
        scope: String, scopes separated by space.
    """
    self.client_id = client_id
    self.client_secret = client_secret
    self.user_agent = user_agent
    self.scope = scope

  def get_refreshed_credential_from_file(self, creds_file_path):
    """Get refreshed credential from file.

    Args:
        creds_file_path: Credential file path.

    Returns:
        An oauth2client.OAuth2Credentials instance.
    """
    credential = self.get_credential_from_file(creds_file_path)
    if credential:
      try:
        credential.refresh(httplib2.Http())
      except oauth2_client.AccessTokenRefreshError as e:
        logging.debug('Token refresh error: %s', e)
      if not credential.invalid:
        return credential
    logging.debug('Cannot get credential.')
    return None

  def get_credential_from_file(self, creds_file_path):
    """Get credential from file.

    Args:
        creds_file_path: Credential file path.

    Returns:
        An oauth2client.OAuth2Credentials instance.
    """
    storage = multiprocess_file_storage.get_credential_storage(
        filename=os.path.abspath(creds_file_path),
        client_id=self.client_id,
        user_agent=self.user_agent,
        scope=self.scope,
    )
    return storage.get()

  def get_credential_with_auth_flow(self, creds_file_path):
    """Get Credential object from file.

    Get credential object from file. Run oauth flow if haven't authorized
    before.

    Args:
        creds_file_path: Credential file path.

    Returns:
        An oauth2client.OAuth2Credentials instance.
    """
    credentials = None
    # SSO auth
    try:
      token = self._get_sso_access_token()
      credentials = oauth2_client.AccessTokenCredentials(token, 'atest')
      if credentials:
        return credentials
    # pylint: disable=broad-except
    except Exception as e:
      logging.debug('Exception:%s', e)
    # GCP auth flow
    credentials = self.get_refreshed_credential_from_file(creds_file_path)
    if not credentials:
      storage = multiprocess_file_storage.get_credential_storage(
          filename=os.path.abspath(creds_file_path),
          client_id=self.client_id,
          user_agent=self.user_agent,
          scope=self.scope,
      )
      return self._run_auth_flow(storage)
    return credentials

  def _run_auth_flow(self, storage):
    """Get user oauth2 credentials.

    Using the loopback IP address flow for desktop clients.

    Args:
        storage: GCP storage object.

    Returns:
        An oauth2client.OAuth2Credentials instance.
    """
    flags = RunFlowFlags(browser_auth=True)

    # Get a free port on demand.
    port = None
    while not port or port < 10000:
      with socket() as local_socket:
        local_socket.bind(('', 0))
        _, port = local_socket.getsockname()
    _localhost_port = port
    _direct_uri = f'http://localhost:{_localhost_port}'
    flow = oauth2_client.OAuth2WebServerFlow(
        client_id=self.client_id,
        client_secret=self.client_secret,
        scope=self.scope,
        user_agent=self.user_agent,
        redirect_uri=f'{_direct_uri}',
    )
    credentials = oauth2_tools.run_flow(flow=flow, storage=storage, flags=flags)
    return credentials

  @staticmethod
  def _get_sso_access_token():
    """Use stubby command line to exchange corp sso to a scoped oauth

    token.

    Returns:
        A token string.
    """
    if not constants.TOKEN_EXCHANGE_COMMAND:
      return None

    request = constants.TOKEN_EXCHANGE_REQUEST.format(
        user=getpass.getuser(), scope=constants.SCOPE
    )
    # The output format is: oauth2_token: "<TOKEN>"
    return subprocess.run(
        constants.TOKEN_EXCHANGE_COMMAND,
        input=request,
        check=True,
        text=True,
        shell=True,
        stdout=subprocess.PIPE,
    ).stdout.split('"')[1]


def do_upload_flow(extra_args):
  """Run upload flow.

  Asking user's decision and do the related steps.

  Args:
      extra_args: Dict of extra args to add to test run.

  Return:
      tuple(invocation, workunit)
  """
  config_folder = os.path.join(atest_utils.get_misc_dir(), '.atest')
  fetch_cred_start = time.time()
  creds = fetch_credential(config_folder, extra_args)
  metrics.LocalDetectEvent(
      detect_type=DetectType.FETCH_CRED_MS,
      result=int((time.time() - fetch_cred_start) * 1000),
  )
  if creds:
    prepare_upload_start = time.time()
    inv, workunit, local_build_id, build_target = _prepare_data(creds)
    metrics.LocalDetectEvent(
        detect_type=DetectType.UPLOAD_PREPARE_MS,
        result=int((time.time() - prepare_upload_start) * 1000),
    )
    extra_args[constants.INVOCATION_ID] = inv['invocationId']
    extra_args[constants.WORKUNIT_ID] = workunit['id']
    extra_args[constants.LOCAL_BUILD_ID] = local_build_id
    extra_args[constants.BUILD_TARGET] = build_target
    if not os.path.exists(os.path.dirname(constants.TOKEN_FILE_PATH)):
      os.makedirs(os.path.dirname(constants.TOKEN_FILE_PATH))
    with open(constants.TOKEN_FILE_PATH, 'w') as token_file:
      if creds.token_response:
        token_file.write(creds.token_response['access_token'])
      else:
        token_file.write(creds.access_token)
    return creds, inv
  return None, None


def fetch_credential(config_folder, extra_args):
  """Fetch the credential whenever --request-upload-result is specified.

  Args:
      config_folder: The directory path to put config file. The default path is
        ~/.atest.
      extra_args: Dict of extra args to add to test run.

  Return:
      The credential object.
  """
  if not os.path.exists(config_folder):
    os.makedirs(config_folder)
  not_upload_file = os.path.join(config_folder, constants.DO_NOT_UPLOAD)
  # Do nothing if there are no related config or DO_NOT_UPLOAD exists.
  if not constants.CREDENTIAL_FILE_NAME or not constants.TOKEN_FILE_PATH:
    return None

  creds_f = os.path.join(config_folder, constants.CREDENTIAL_FILE_NAME)
  if extra_args.get(constants.REQUEST_UPLOAD_RESULT):
    if os.path.exists(not_upload_file):
      os.remove(not_upload_file)
  else:
    # TODO(b/275113186): Change back to default upload after AnTS upload
    #  extremely slow problem be solved.
    if os.path.exists(creds_f):
      os.remove(creds_f)
    Path(not_upload_file).touch()

  # If DO_NOT_UPLOAD not exist, ATest will try to get the credential
  # from the file.
  if not os.path.exists(not_upload_file):
    return GCPHelper(
        client_id=constants.CLIENT_ID,
        client_secret=constants.CLIENT_SECRET,
        user_agent='atest',
    ).get_credential_with_auth_flow(creds_f)

  # TODO(b/275113186): Change back the warning message after the bug solved.
  atest_utils.colorful_print(
      'WARNING: AnTS upload disabled by default due to upload slowly'
      '(b/275113186). If you still want to upload test result to AnTS, '
      'please add the option --request-upload-result manually.',
      constants.YELLOW,
  )
  return None


def _prepare_data(creds):
  """Prepare data for build api using.

  Args:
      creds: The credential object.

  Return:
      invocation and workunit object.
      build id and build target of local build.
  """
  try:
    logging.disable(logging.INFO)
    external_id = str(uuid.uuid4())
    client = logstorage_utils.BuildClient(creds)
    branch = _get_branch(client)
    target = _get_target(branch, client)
    build_record = client.insert_local_build(external_id, target, branch)
    client.insert_build_attempts(build_record)
    invocation = client.insert_invocation(build_record)
    workunit = client.insert_work_unit(invocation)
    return invocation, workunit, build_record['buildId'], target
  finally:
    logging.disable(logging.NOTSET)


def _get_branch(build_client):
  """Get source code tree branch.

  Args:
      build_client: The build client object.

  Return:
      "git_main" in internal git, "aosp-main" otherwise.
  """
  default_branch = 'git_main' if constants.CREDENTIAL_FILE_NAME else 'aosp-main'
  local_branch = 'git_%s' % atest_utils.get_manifest_branch()
  branch = build_client.get_branch(local_branch)
  return local_branch if branch else default_branch


def _get_target(branch, build_client):
  """Get local build selected target.

  Args:
      branch: The branch want to check.
      build_client: The build client object.

  Return:
      The matched build target, "aosp_x86_64-trunk_staging-userdebug"
      otherwise.
  """
  default_target = 'aosp_x86_64-trunk_staging-userdebug'
  local_target = atest_utils.get_build_target()
  targets = [t['target'] for t in build_client.list_target(branch)['targets']]
  return local_target if local_target in targets else default_target
