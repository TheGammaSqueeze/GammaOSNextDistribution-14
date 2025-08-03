"""Provides helper functions for fetching artifacts."""

import io
import os
import re
import sys
import sysconfig
import time

# This is a workaround to put '/usr/lib/python3.X' ahead of googleapiclient
# Using embedded_launcher won't work since py3-cmd doesn't contain _ssl module.
if sys.version_info.major == 3:
  sys.path.insert(0, os.path.dirname(sysconfig.get_paths()['purelib']))

# pylint: disable=import-error,g-bad-import-order,g-import-not-at-top
import apiclient
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

import httplib2
from oauth2client.service_account import ServiceAccountCredentials

_SCOPE_URL = 'https://www.googleapis.com/auth/androidbuild.internal'
_DEF_JSON_KEYFILE = '.config/gcloud/application_default_credentials.json'


# 20 MB default chunk size -- used in Buildbot
_DEFAULT_CHUNK_SIZE = 20 * 1024 * 1024

# HTTP errors -- used in Builbot
_DEFAULT_MASKED_ERRORS = [404]
_DEFAULT_RETRIED_ERRORS = [503]
_DEFAULT_RETRIES = 10


def _create_http_from_p12(robot_credentials_file, robot_username):
  """Creates a credentialed HTTP object for requests.

  Args:
    robot_credentials_file: The path to the robot credentials file.
    robot_username: A string containing the username of the robot account.

  Returns:
    An authorized httplib2.Http object.
  """
  try:
    credentials = ServiceAccountCredentials.from_p12_keyfile(
        service_account_email=robot_username,
        filename=robot_credentials_file,
        scopes=_SCOPE_URL)
  except AttributeError:
    raise ValueError('Machine lacks openssl or pycrypto support')
  http = httplib2.Http()
  return credentials.authorize(http)


def _simple_execute(http_request,
                    masked_errors=None,
                    retried_errors=None,
                    retry_delay_seconds=5,
                    max_tries=_DEFAULT_RETRIES):
  """Execute http request and return None on specified errors.

  Args:
    http_request: the apiclient provided http request
    masked_errors: list of errors to return None on
    retried_errors: list of erros to retry the request on
    retry_delay_seconds: how many seconds to sleep before retrying
    max_tries: maximum number of attmpts to make request

  Returns:
    The result on success or None on masked errors.
  """
  if not masked_errors:
    masked_errors = _DEFAULT_MASKED_ERRORS
  if not retried_errors:
    retried_errors = _DEFAULT_RETRIED_ERRORS

  last_error = None
  for _ in range(max_tries):
    try:
      return http_request.execute()
    except HttpError as e:
      last_error = e
      if e.resp.status in masked_errors:
        return None
      elif e.resp.status in retried_errors:
        time.sleep(retry_delay_seconds)
      else:
        # Server Error is server error
        raise e

  # We've gone through the max_retries, raise the last error
  raise last_error  # pylint: disable=raising-bad-type


def create_client(http):
  """Creates an Android build api client from an authorized http object.

  Args:
     http: An authorized httplib2.Http object.

  Returns:
    An authorized android build api client.
  """
  return build(serviceName='androidbuildinternal', version='v3', http=http,
               static_discovery=False)


def create_client_from_json_keyfile(json_keyfile_name=None):
  """Creates an Android build api client from a json keyfile.

  Args:
    json_keyfile_name: The location of the keyfile, if None is provided use
                       default location.

  Returns:
    An authorized android build api client.
  """
  if not json_keyfile_name:
    json_keyfile_name = os.path.join(os.getenv('HOME'), _DEF_JSON_KEYFILE)

  credentials = ServiceAccountCredentials.from_json_keyfile_name(
      filename=json_keyfile_name, scopes=_SCOPE_URL)
  http = httplib2.Http()
  credentials.authorize(http)
  return create_client(http)


def create_client_from_p12(robot_credentials_file, robot_username):
  """Creates an Android build api client from a config file.

  Args:
    robot_credentials_file: The path to the robot credentials file.
    robot_username: A string containing the username of the robot account.

  Returns:
    An authorized android build api client.
  """
  http = _create_http_from_p12(robot_credentials_file, robot_username)
  return create_client(http)


def fetch_artifact(client, build_id, target, resource_id, dest):
  """Fetches an artifact.

  Args:
    client: An authorized android build api client.
    build_id: AB build id
    target: the target name to download from
    resource_id: the resource id of the artifact
    dest: path to store the artifact
  """
  out_dir = os.path.dirname(dest)
  if not os.path.exists(out_dir):
    os.makedirs(out_dir)

  dl_req = client.buildartifact().get_media(
      buildId=build_id,
      target=target,
      attemptId='latest',
      resourceId=resource_id)

  print('Fetching %s to %s...' % (resource_id, dest))
  with io.FileIO(dest, mode='wb') as fh:
    downloader = apiclient.http.MediaIoBaseDownload(
        fh, dl_req, chunksize=_DEFAULT_CHUNK_SIZE)
    done = False
    while not done:
      status, done = downloader.next_chunk(num_retries=_DEFAULT_RETRIES)
      print('Fetching...' + str(status.progress() * 100))

  print('Done Fetching %s to %s' % (resource_id, dest))


def get_build_list(client, **kwargs):
  """Get a list of builds from the android build api that matches parameters.

  Args:
    client: An authorized android build api client.
    **kwargs: keyworded arguments to pass to build api.

  Returns:
    Response from build api.
  """
  build_request = client.build().list(**kwargs)

  return _simple_execute(build_request)


def list_artifacts(client, regex, **kwargs):
  """List artifacts from the android build api that matches parameters.

  Args:
    client: An authorized android build api client.
    regex: Regular expression pattern to match artifact name.
    **kwargs: keyworded arguments to pass to buildartifact.list api.

  Returns:
    List of matching artifact names.
  """
  matching_artifacts = []
  kwargs.setdefault('attemptId', 'latest')
  req = client.buildartifact().list(nameRegexp=regex, **kwargs)
  while req:
    result = _simple_execute(req)
    if result and 'artifacts' in result:
      for a in result['artifacts']:
        matching_artifacts.append(a['name'])
    req = client.buildartifact().list_next(req, result)
  return matching_artifacts


def fetch_artifacts(client, out_dir, target, pattern, build_id):
  """Fetches target files artifacts matching patterns.

  Args:
    client: An authorized instance of an android build api client for making
      requests.
    out_dir: The directory to store the fetched artifacts to.
    target: The target name to download from.
    pattern: A regex pattern to match to artifacts filename.
    build_id: The Android Build id.
  """
  if not os.path.exists(out_dir):
    os.makedirs(out_dir)

  # Build a list of needed artifacts
  artifacts = list_artifacts(
      client=client,
      regex=pattern,
      buildId=build_id,
      target=target)

  for artifact in artifacts:
    fetch_artifact(
        client=client,
        build_id=build_id,
        target=target,
        resource_id=artifact,
        dest=os.path.join(out_dir, artifact))


def get_latest_build_id(client, branch, target):
  """Get the latest build id.

  Args:
    client: An authorized instance of an android build api client for making
      requests.
    branch: The branch to download from
    target: The target name to download from.
  Returns:
    The build id.
  """
  build_response = get_build_list(
      client=client,
      branch=branch,
      target=target,
      maxResults=1,
      successful=True,
      buildType='submitted')

  if not build_response:
    raise ValueError('Unable to determine latest build ID!')

  return build_response['builds'][0]['buildId']


def fetch_latest_artifacts(client, out_dir, target, pattern, branch):
  """Fetches target files artifacts matching patterns from the latest build.

  Args:
    client: An authorized instance of an android build api client for making
      requests.
    out_dir: The directory to store the fetched artifacts to.
    target: The target name to download from.
    pattern: A regex pattern to match to artifacts filename
    branch: The branch to download from
  """
  build_id = get_latest_build_id(
      client=client, branch=branch, target=target)

  fetch_artifacts(client, out_dir, target, pattern, build_id)
