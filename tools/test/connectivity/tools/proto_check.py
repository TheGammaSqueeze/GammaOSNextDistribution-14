#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import filecmp
import logging
import os
import re
import shutil
import subprocess
import tempfile

from acts.libs.proc import job

COMMIT_ID_ENV_KEY = 'PREUPLOAD_COMMIT'
GIT_FILE_NAMES_CMD = 'git diff-tree --no-commit-id --name-status -r %s'


def compile_proto(proto_path, output_dir):
    """Invoke Protocol Compiler to generate python from given source .proto."""
    # Find compiler path
    protoc = None
    if 'PROTOC' in os.environ and os.path.exists(os.environ['PROTOC']):
        protoc = os.environ['PROTOC']
    if not protoc:
        protoc = shutil.which('protoc')
    if not protoc:
        logging.error(
            "Cannot find protobuf compiler (>=3.0.0), please install"
            "protobuf-compiler package. Prefer copying from <top>/prebuilts/tools"
        )
        logging.error("    prebuilts/tools/linux-x86_64/protoc/bin/protoc")
        logging.error("If prebuilts are not available, use apt-get:")
        logging.error("    sudo apt-get install protobuf-compiler")
        return None
    # Validate input proto path
    if not os.path.exists(proto_path):
        logging.error('Can\'t find required file: %s\n' % proto_path)
        return None
    # Validate output py-proto path
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    elif not os.path.isdir(output_dir):
        logging.error("Output path is not a valid directory: %s" %
                      (output_dir))
        return None
    input_dir = os.path.dirname(proto_path)
    output_filename = os.path.basename(proto_path).replace('.proto', '_pb2.py')
    output_path = os.path.join(output_dir, output_filename)
    # Compiling proto
    logging.debug('Generating %s' % output_path)
    protoc_command = [
        protoc, '-I=%s' % (input_dir), '--python_out=%s' % (output_dir),
        proto_path
    ]
    logging.debug('Running command %s' % protoc_command)
    if subprocess.call(protoc_command, stderr=subprocess.STDOUT) != 0:
        logging.error("Fail to compile proto")
        return None
    output_module_name = os.path.splitext(output_filename)[0]
    return output_module_name


def proto_generates_gen_file(proto_file, proto_gen_file):
    """Check that the proto_gen_file matches the compilation result of the
    proto_file.

    Params:
        proto_file: The proto file.
        proto_gen_file: The generated file.

    Returns: True if the compiled proto matches the given proto_gen_file.
    """
    with tempfile.TemporaryDirectory() as tmp_dir:
        module_name = compile_proto(proto_file, tmp_dir)
        if not module_name:
            return False
        tmp_proto_gen_file = os.path.join(tmp_dir, '%s.py' % module_name)
        return filecmp.cmp(tmp_proto_gen_file, proto_gen_file)


def verify_protos_update_generated_files(proto_files, proto_gen_files):
    """For each proto file in proto_files, find the corresponding generated
    file in either proto_gen_files, or in the directory tree of the proto.
    Verify that the generated file matches the compilation result of the proto.

    Params:
        proto_files: List of proto files.
        proto_gen_files: List of generated files.
    """
    success = True
    gen_files = set(proto_gen_files)
    for proto_file in proto_files:
        gen_filename = os.path.basename(proto_file).replace('.proto', '_pb2.py')
        gen_file = ''
        # search the gen_files set first
        for path in gen_files:
            if (os.path.basename(path) == gen_filename
                    and path.startswith(os.path.dirname(proto_file))):
                gen_file = path
                gen_files.remove(path)
                break
        else:
            # search the proto file's directory
            for root, _, filenames in os.walk(os.path.dirname(proto_file)):
                for filename in filenames:
                    if filename == gen_filename:
                        gen_file = os.path.join(root, filename)
                        break
                if gen_file:
                    break

        # check that the generated file matches the compiled proto
        if gen_file and not proto_generates_gen_file(proto_file, gen_file):
            logging.error('Proto file %s does not compile to %s'
                          % (proto_file, gen_file))
            protoc = shutil.which('protoc')
            if protoc:
                protoc_command = ' '.join([
                    protoc, '-I=%s' % os.path.dirname(proto_file),
                    '--python_out=%s' % os.path.dirname(gen_file), proto_file])
                logging.error('Run this command to re-generate file from proto'
                              '\n%s' % protoc_command)
            success = False

    return success


def main():
    if COMMIT_ID_ENV_KEY not in os.environ:
        logging.error('Missing commit id in environment.')
        exit(1)

    # get list of *.proto and *_pb2.py files from commit, then compare
    proto_files = []
    proto_gen_files = []
    git_cmd = GIT_FILE_NAMES_CMD % os.environ[COMMIT_ID_ENV_KEY]
    lines = job.run(git_cmd).stdout.splitlines()
    for line in lines:
        match = re.match(r'(\S+)\s+(.*)', line)
        status, f = match.group(1), match.group(2)
        if status != 'D':
            if f.endswith('.proto'):
                proto_files.append(os.path.abspath(f))
            if f.endswith('_pb2.py'):
                proto_gen_files.append(os.path.abspath(f))
    exit(not verify_protos_update_generated_files(proto_files, proto_gen_files))


if __name__ == '__main__':
    main()
