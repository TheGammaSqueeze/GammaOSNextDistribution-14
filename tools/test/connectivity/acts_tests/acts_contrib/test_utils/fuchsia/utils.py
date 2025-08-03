#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
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

import os
from acts.controllers.fuchsia_lib.ssh import FuchsiaSSHError


def http_file_download_by_curl(fd,
                               url,
                               out_path='/tmp/',
                               curl_loc='/bin/curl',
                               remove_file_after_check=True,
                               timeout=3600,
                               limit_rate=None,
                               additional_args=None,
                               retry=3):
    """Download http file by ssh curl.

    Args:
        fd: Fuchsia Device Object.
        url: The url that file to be downloaded from.
        out_path: Optional. Where to download file to.
            out_path is /tmp by default.
        curl_loc: Location of curl binary on fd.
        remove_file_after_check: Whether to remove the downloaded file after
            check.
        timeout: timeout for file download to complete.
        limit_rate: download rate in bps. None, if do not apply rate limit.
        additional_args: Any additional args for curl.
        retry: the retry request times provided in curl command.
    """
    file_directory, file_name = _generate_file_directory_and_file_name(
        url, out_path)
    file_path = os.path.join(file_directory, file_name)
    curl_cmd = curl_loc
    if limit_rate:
        curl_cmd += f' --limit-rate {limit_rate}'
    if retry:
        curl_cmd += f' --retry {retry}'
    if additional_args:
        curl_cmd += f' {additional_args}'
    curl_cmd += f' --url {url} > {file_path}'

    fd.log.info(f'Download {url} to {file_path} by ssh command {curl_cmd}')
    try:
        fd.ssh.run(curl_cmd, timeout_sec=timeout)
        if _check_file_existence(fd, file_path):
            fd.log.info(f'{url} is downloaded to {file_path} successfully')
            return True

        fd.log.warning(f'Fail to download {url}')
        return False
    except FuchsiaSSHError as e:
        fd.log.warning(f'Command "{curl_cmd}" failed with error {e}')
        return False
    except Exception as e:
        fd.log.error(f'Download {url} failed with unexpected exception {e}')
        return False
    finally:
        if remove_file_after_check:
            fd.log.info(f'Remove the downloaded file {file_path}')
            try:
                fd.ssh.run(f'rm {file_path}')
            except FuchsiaSSHError:
                pass


def _generate_file_directory_and_file_name(url, out_path):
    """Splits the file from the url and specifies the appropriate location of
       where to store the downloaded file.

    Args:
        url: A url to the file that is going to be downloaded.
        out_path: The location of where to store the file that is downloaded.

    Returns:
        file_directory: The directory of where to store the downloaded file.
        file_name: The name of the file that is being downloaded.
    """
    file_name = url.split('/')[-1]
    if not out_path:
        file_directory = '/tmp/'
    elif not out_path.endswith('/'):
        file_directory, file_name = os.path.split(out_path)
    else:
        file_directory = out_path
    return file_directory, file_name


def _check_file_existence(fd, file_path):
    """Check file existence by file_path. If expected_file_size
       is provided, then also check if the file meet the file size requirement.

    Args:
        fd: A fuchsia device
        file_path: Where to store the file on the fuchsia device.
    """
    try:
        result = fd.ssh.run(f'ls -al "{file_path}"')
        fd.log.debug(f'File {file_path} exists.')
        return True
    except FuchsiaSSHError as e:
        if 'No such file or directory' in e.result.stderr:
            fd.log.debug(f'File {file_path} does not exist.')
            return False
        raise e
