#!/usr/bin/python3

import os
import subprocess
import zipfile

from utils import print_e, mv

# See go/fetch_artifact for details on this script.
FETCH_ARTIFACT = '/google/data/ro/projects/android/fetch_artifact'
FETCH_ARTIFACT_BEYOND_CORP = '/usr/bin/fetch_artifact'


class BuildId(object):
    def __init__(self, url_id, fs_id):
        # id when used in build server urls
        self.url_id = url_id
        # id when used in build commands
        self.fs_id = fs_id


def fetch_artifact(target, build_id, artifact_path, beyond_corp):
    download_to = os.path.join('.', os.path.dirname(artifact_path))
    print(f'Fetching {artifact_path} from {target}...')
    if not os.path.exists(download_to):
        os.makedirs(download_to)
    if beyond_corp:
        fetch_cmd = [FETCH_ARTIFACT_BEYOND_CORP, '--use_oauth2',
                     '--bid', str(build_id), '--target', target, artifact_path, download_to]
    else:
        fetch_cmd = [FETCH_ARTIFACT,
                     '--bid', str(build_id), '--target', target, artifact_path, download_to]
    print('Running: ' + ' '.join(fetch_cmd))
    try:
        subprocess.check_output(fetch_cmd, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError:
        print_e(f'FAIL: Unable to retrieve {artifact_path} artifact for build ID {build_id}')
        print_e('Please make sure you are authenticated for build server access!')
        return None
    return artifact_path


def fetch_artifacts(target, build_id, artifact_dict, beyond_corp):
    for artifact, target_path in artifact_dict.items():
        artifact_path = fetch_artifact(target, build_id.url_id, artifact, beyond_corp)
        if not artifact_path:
            return False
        mv(artifact_path, target_path)
    return True


def extract_artifact(artifact_path):
    # Unzip the repo archive into a separate directory.
    repo_dir = os.path.splitext(artifact_path)[0]
    with zipfile.ZipFile(artifact_path) as zipFile:
        zipFile.extractall(repo_dir)
    return repo_dir


def fetch_and_extract(target, build_id, file, beyond_corp, artifact_path=None):
    if not artifact_path:
        artifact_path = fetch_artifact(target, build_id, file, beyond_corp)
    if not artifact_path:
        return None
    return extract_artifact(artifact_path)


def parse_build_id(source):
    # must be in the format 12345 or P12345
    number_text = source
    presubmit = False
    if number_text.startswith('P'):
        presubmit = True
        number_text = number_text[1:]
    if not number_text.isnumeric():
        return None
    url_id = source
    fs_id = url_id
    if presubmit:
        fs_id = '0'
    return BuildId(url_id, fs_id)
