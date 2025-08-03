#!/usr/bin/env python3
#
#  Copyright (C) 2022 The Android Open Source Project
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

"""The script to upload generated artifacts from build server to CAS."""
import argparse
import dataclasses
import glob
import json
import logging
import os
import shutil
import subprocess
import tempfile
import time
import re


@dataclasses.dataclass
class ArtifactConfig:
    """Configuration of an artifact to be uploaded to CAS.

    Attributes:
        source_path: path to the artifact that relative to the root of source code.
        unzip: true if the artifact should be unzipped and uploaded as a directory.
        chunk: true if the artifact should be uploaded with chunking.
        exclude_filters: a list of regular expressions for files that are excluded from uploading.
    """
    source_path: str
    unzip: bool
    chunk: bool = False
    exclude_filters: list[str] = dataclasses.field(default_factory=list)


@dataclasses.dataclass
class CasInfo:
    """Basic information of CAS server and client.

    Attributes:
        cas_instance: the instance name of CAS service.
        cas_service: the address of CAS service.
        client_path: path to the CAS uploader client.
        version: version of the CAS uploader client, in turple format.
    """
    cas_instance: str
    cas_service: str
    client_path: str
    client_version: tuple


@dataclasses.dataclass
class UploadResult:
    """Result of uploading a single artifact with CAS client.

    Attributes:
        digest: root digest of the artifact.
        content_details: detail information of all uploaded files inside the uploaded artifact.
    """
    digest: str
    content_details: list[dict[str,any]]


CAS_UPLOADER_PREBUILT_PATH = 'tools/tradefederation/prebuilts/'
CAS_UPLOADER_PATH = 'tools/content_addressed_storage/prebuilts/'
CAS_UPLOADER_BIN = 'casuploader'

UPLOADER_TIMEOUT_SECS = 600 # 10 minutes

DIGESTS_PATH = 'cas_digests.json'
LOG_PATH = 'logs/cas_uploader.log'
CONTENT_DETAILS_PATH = 'logs/cas_content_details.json'

# Configurations of artifacts will be uploaded to CAS.
# TODO(b/298890453) Add artifacts after this script is attached to build process.
ARTIFACTS = [
    # test_suite targets
    ArtifactConfig('android-catbox.zip', True),
    ArtifactConfig('android-csuite.zip', True),
    ArtifactConfig('android-cts.zip', True),
    ArtifactConfig('android-gcatbox.zip', True),
    ArtifactConfig('android-gts.zip', True),
    ArtifactConfig('android-mcts.zip', True),
    ArtifactConfig('android-mts.zip', True),
    ArtifactConfig('android-pts.zip', True),
    ArtifactConfig('android-vts.zip', True),
    ArtifactConfig('art-host-tests.zip', True),
    ArtifactConfig('bazel-test-suite.zip', True),
    ArtifactConfig('host-unit-tests.zip', True),
    ArtifactConfig('general-tests.zip', True),
    ArtifactConfig('general-tests_configs.zip', True),
    ArtifactConfig('general-tests_host-shared-libs.zip', True),
    ArtifactConfig('tradefed.zip', True),
    ArtifactConfig('google-tradefed.zip', True),
    ArtifactConfig('robolectric-tests.zip', True),
    ArtifactConfig('ravenwood-tests.zip', True),
    ArtifactConfig('test_mappings.zip', True),

    # Mainline artifacts
    ArtifactConfig('*.apex', False),
    ArtifactConfig('*.apk', False),

    # Device target artifacts
    ArtifactConfig('androidTest.zip', True),
    ArtifactConfig('device-tests.zip', True),
    ArtifactConfig('device-tests_configs.zip', True),
    ArtifactConfig('device-tests_host-shared-libs.zip', True),
    ArtifactConfig('*-tests-*zip', True),
    ArtifactConfig('*-continuous_instrumentation_tests-*zip', True),
    ArtifactConfig('*-continuous_instrumentation_metric_tests-*zip', True),
    ArtifactConfig('*-continuous_native_tests-*zip', True),
    ArtifactConfig('cvd-host_package.tar.gz', False),
    ArtifactConfig('bootloader.img', False),
    ArtifactConfig('radio.img', False),
    ArtifactConfig('*-target_files-*.zip', True),
    ArtifactConfig('*-img-*zip', False)
]

# Artifacts will be uploaded if the config name is set in arguments `--experiment_artifacts`.
# These configs are usually used to upload artifacts in partial branches/targets for experiment
# purpose.
# A sample entry:
#   "device_image_target_files": ArtifactConfig('*-target_files-*.zip', True)
EXPERIMENT_ARTIFACT_CONFIGS = {
    "device_image_proguard_dict": ArtifactConfig('*-proguard-dict-*.zip', False, True),
}

def _init_cas_info() -> CasInfo:
    client_path = _get_client()
    return CasInfo(
        _get_env_var('RBE_instance', check=True),
        _get_env_var('RBE_service', check=True),
        client_path,
        _get_client_version(client_path)
    )


def _get_client() -> str:
    bin_path = os.path.join(CAS_UPLOADER_PATH, CAS_UPLOADER_BIN)
    if os.path.isfile(bin_path):
        logging.info('Using client at %s', bin_path)
        return bin_path
    client = glob.glob(CAS_UPLOADER_PREBUILT_PATH + '**/' + CAS_UPLOADER_BIN, recursive=True)
    if not client:
        raise ValueError('Could not find casuploader binary')
    logging.info('Using client at %s', client[0])
    return client[0]


def _get_client_version(client_path: str) -> int:
    """Get the version of CAS client in turple format."""
    version_output = ''
    try:
        version_output = subprocess.check_output([client_path, '-version']).decode('utf-8').strip()
        matched = re.findall(r'version: (\d+\.\d+)', version_output)
        if not matched:
            logging.warning('Failed to parse CAS client version. Output: %s', version_output)
            return (0, 0)
        version = tuple(map(int, matched[0].split('.')))
        logging.info('CAS client version is %s', version)
        return version
    # pylint: disable=broad-exception-caught
    except Exception as e:
    # pylint: enable=broad-exception-caught
        logging.warning('Failed to get CAS client version. Output: %s. Error %s', version_output, e)
        return (0, 0)


def _get_env_var(key: str, default=None, check=False):
    value = os.environ.get(key, default)
    if check and not value:
        raise ValueError(f'Error: the environment variable {key} is not set')
    return value


def _parse_additional_artifacts(args) -> list[ArtifactConfig]:
    additional_artifacts = []
    for config in args.experiment_artifacts:
        if config not in EXPERIMENT_ARTIFACT_CONFIGS:
            logging.warning('Ignore invalid experiment_artifacts: %s', config)
        else:
            additional_artifacts.append(EXPERIMENT_ARTIFACT_CONFIGS[config])
            logging.info(
                'Added experiment artifact from arguments %s',
                EXPERIMENT_ARTIFACT_CONFIGS[config].source_path,
            )
    return additional_artifacts


def _upload(
        cas_info: CasInfo,
        artifact: ArtifactConfig,
        working_dir: str,
        log_file: str,
) -> str:
    """Upload the artifact to CAS by casuploader binary.

    Args:
      cas_info: the basic CAS server information.
      artifact: the artifact to be uploaded to CAS.
      working_dir: the directory for intermediate files.
      log_file: the file where to add the upload logs.

    Returns: the digest of the uploaded artifact, formatted as "<hash>/<size>".
      returns None if artifact upload fails.
    """
    # `-dump-file-details` only supports on cas uploader V1.0 or later.
    dump_file_details = cas_info.client_version >= (1, 0)
    if not dump_file_details:
        logging.warning('-dump-file-details is not enabled')

    with tempfile.NamedTemporaryFile(mode='w+') as digest_file, tempfile.NamedTemporaryFile(
      mode='w+') as content_details_file:
        logging.info(
            'Uploading %s to CAS instance %s', artifact.source_path, cas_info.cas_instance
        )

        cmd = [
            cas_info.client_path,
            '-cas-instance',
            cas_info.cas_instance,
            '-cas-addr',
            cas_info.cas_service,
            '-dump-digest',
            digest_file.name,
            '-use-adc',
        ]

        cmd = cmd + _path_for_artifact(artifact, working_dir)

        if artifact.chunk:
            cmd = cmd + ['-chunk']

        for exclude_filter in artifact.exclude_filters:
            cmd = cmd + ['-exclude-filters', exclude_filter]

        if dump_file_details:
            cmd = cmd + ['-dump-file-details', content_details_file.name]

        try:
            logging.info('Running command: %s', cmd)
            with open(log_file, 'a', encoding='utf8') as outfile:
                subprocess.run(
                    cmd,
                    check=True,
                    text=True,
                    stdout=outfile,
                    stderr=subprocess.STDOUT,
                    encoding='utf-8',
                    timeout=UPLOADER_TIMEOUT_SECS
                )
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
            logging.warning(
                'Failed to upload %s to CAS instance %s. Skip.\nError message: %s\nLog: %s',
                artifact.source_path, cas_info.cas_instance, e, e.stdout,
            )
            return None
        except subprocess.SubprocessError as e:
            logging.warning('Failed to upload %s to CAS instance %s. Skip.\n. Error %s',
                artifact.source_path, cas_info.cas_instance, e)
            return None

        # Read digest of the root directory or file from dumped digest file.
        digest = digest_file.read()
        if digest:
            logging.info('Uploaded %s to CAS. Digest: %s', artifact.source_path, digest)
        else:
            logging.warning(
                'No digest is dumped for file %s, the uploading may fail.', artifact.source_path)
            return None

        content_details = None
        if dump_file_details:
            try:
                content_details = json.loads(content_details_file.read())
            except json.JSONDecodeError as e:
                logging.warning('Failed to parse uploaded content details: %s', e)

        return UploadResult(digest, content_details)


def _path_for_artifact(artifact: ArtifactConfig, working_dir: str) -> [str]:
    if artifact.unzip:
        return ['-zip-path', artifact.source_path]
    if artifact.chunk:
        return ['-file-path', artifact.source_path]
    # TODO(b/250643926) This is a workaround to handle non-directory files.
    tmp_dir = tempfile.mkdtemp(dir=working_dir)
    target_path = os.path.join(tmp_dir, os.path.basename(artifact.source_path))
    shutil.copy(artifact.source_path, target_path)
    return ['-dir-path', tmp_dir]


def _output_results(
        cas_info: CasInfo,
        output_dir: str,
        digests: dict[str, str],
        content_details: list[dict[str, any]],
):
    digests_output = {
        'cas_instance': cas_info.cas_instance,
        'cas_service': cas_info.cas_service,
        'client_version': '.'.join(map(str, cas_info.client_version)),
        'files': digests,
    }
    output_path = os.path.join(output_dir, DIGESTS_PATH)
    with open(output_path, 'w', encoding='utf8') as writer:
        writer.write(json.dumps(digests_output, sort_keys=True, indent=2))
    logging.info('Output digests to %s', output_path)

    output_path = os.path.join(output_dir, CONTENT_DETAILS_PATH)
    with open(output_path, 'w', encoding='utf8') as writer:
        writer.write(json.dumps(content_details, sort_keys=True, indent=2))
    logging.info('Output uploaded content details to %s', output_path)


def _upload_all_artifacts(cas_info: CasInfo, all_artifacts: ArtifactConfig,
    dist_dir: str, working_dir: str, log_file:str):
    file_digests = {}
    content_details = []
    for artifact in all_artifacts:
        source_path = artifact.source_path
        for f in glob.glob(dist_dir + '/**/' + source_path, recursive=True):
            start = time.time()
            name = os.path.basename(f)
            artifact.source_path = f
            result = _upload(cas_info, artifact, working_dir, log_file)

            if result and result.digest:
                file_digests[name] = result.digest
            else:
                logging.warning(
                    'Skip to save the digest of file %s, the uploading may fail', name
                )
            if result and result.content_details:
                content_details.append({"artifact": name, "details": result.content_details})
            else:
                logging.warning('Skip to save the content details of file %s', name)

            logging.info(
                'Elapsed time of uploading %s: %d seconds\n\n',
                artifact.source_path,
                time.time() - start,
            )
    _output_results(
        cas_info,
        dist_dir,
        file_digests,
        content_details,
    )



def main():
    """Uploads the specified artifacts to CAS."""
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--experiment_artifacts',
        required=False,
        action='append',
        default=[],
        help='Name of configuration which artifact to upload',
    )
    args = parser.parse_args()

    dist_dir = _get_env_var('DIST_DIR', check=True)
    log_file = os.path.join(dist_dir, LOG_PATH)
    print('content_uploader.py will export logs to:', log_file)
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s %(levelname)s %(message)s',
        filename=log_file,
    )
    logging.info('Environment variables of running server: %s', os.environ)

    additional_artifacts = _parse_additional_artifacts(args)
    cas_info = _init_cas_info()

    with tempfile.TemporaryDirectory() as working_dir:
        logging.info('The working dir is %s', working_dir)
        start = time.time()
        _upload_all_artifacts(cas_info, ARTIFACTS + additional_artifacts,
            dist_dir, working_dir, log_file)
        logging.info('Total time of uploading build artifacts to CAS: %d seconds',
                     time.time() - start)


if __name__ == '__main__':
    main()
