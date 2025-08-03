#!/usr/bin/python3

import argparse
import glob
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import zipfile

from collections import defaultdict
from pathlib import Path

# See go/fetch_artifact for details on this script.
FETCH_ARTIFACT = '/google/data/ro/projects/android/fetch_artifact'
COMPAT_REPO = Path('prebuilts/sdk')
COMPAT_README = Path('extensions/README.md')
# This build target is used when fetching from a train build (TXXXXXXXX)
BUILD_TARGET_TRAIN = 'train_build'
# This build target is used when fetching from a non-train build (XXXXXXXX)
BUILD_TARGET_CONTINUOUS = 'mainline_modules_sdks-userdebug'
# The glob of sdk artifacts to fetch from remote build
ARTIFACT_PATTERN = 'mainline-sdks/for-next-build/current/{module_name}/sdk/*.zip'
# The glob of sdk artifacts to fetch from local build
ARTIFACT_LOCAL_PATTERN = 'out/dist/mainline-sdks/for-next-build/current/{module_name}/sdk/*.zip'
COMMIT_TEMPLATE = """Finalize artifacts for extension SDK %d

Import from build id %s.

Generated with:
$ %s

Bug: %d
Test: presubmit"""

def fail(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)
    sys.exit(1)

def fetch_artifacts(target, build_id, module_name):
    tmpdir = Path(tempfile.TemporaryDirectory().name)
    tmpdir.mkdir()
    if args.local_mode:
        artifact_path = ARTIFACT_LOCAL_PATTERN.format(module_name='*')
        print('Copying %s to %s ...' % (artifact_path, tmpdir))
        for file in glob.glob(artifact_path):
            shutil.copy(file, tmpdir)
    else:
        artifact_path = ARTIFACT_PATTERN.format(module_name=module_name)
        print('Fetching %s from %s ...' % (artifact_path, target))
        fetch_cmd = [FETCH_ARTIFACT]
        fetch_cmd.extend(['--bid', str(build_id)])
        fetch_cmd.extend(['--target', target])
        fetch_cmd.append(artifact_path)
        fetch_cmd.append(str(tmpdir))
        print("Running: " + ' '.join(fetch_cmd))
        try:
            subprocess.check_output(fetch_cmd, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError:
            fail('FAIL: Unable to retrieve %s artifact for build ID %s' % (artifact_path, build_id))
    return tmpdir

def repo_for_sdk(filename):
    module = filename.split('-')[0]
    target_dir = ''
    if module == 'btservices': return Path('prebuilts/module_sdk/Bluetooth')
    if module == 'media': return Path('prebuilts/module_sdk/Media')
    if module == 'rkpd': return Path('prebuilts/module_sdk/RemoteKeyProvisioning')
    if module == 'tethering': return Path('prebuilts/module_sdk/Connectivity')
    for dir in os.listdir('prebuilts/module_sdk/'):
        if module.lower() in dir.lower():
            if target_dir:
                fail('Multiple target dirs matched "%s": %s' % (module, (target_dir, dir)))
            target_dir = dir
    if not target_dir:
        fail('Could not find a target dir for %s' % filename)

    return Path('prebuilts/module_sdk/%s' % target_dir)

def dir_for_sdk(filename, version):
    base = str(version)
    if 'test-exports' in filename:
        return os.path.join(base, 'test-exports')
    if 'host-exports' in filename:
        return os.path.join(base, 'host-exports')
    return base

def is_ignored(file):
    # Conscrypt has some legacy API tracking files that we don't consider for extensions.
    bad_stem_prefixes = ['conscrypt.module.intra.core.api', 'conscrypt.module.platform.api']
    return any([file.stem.startswith(p) for p in bad_stem_prefixes])


def maybe_tweak_compat_stem(file):
    # For legacy reasons, art and conscrypt txt file names in the SDKs (*.module.public.api)
    # do not match their expected filename in prebuilts/sdk (art, conscrypt). So rename them
    # to match.
    new_stem = file.stem
    new_stem = new_stem.replace('art.module.public.api', 'art')
    new_stem = new_stem.replace('conscrypt.module.public.api', 'conscrypt')

    # The stub jar artifacts from official builds are named '*-stubs.jar', but
    # the convention for the copies in prebuilts/sdk is just '*.jar'. Fix that.
    new_stem = new_stem.replace('-stubs', '')

    return file.with_stem(new_stem)

if not os.path.isdir('build/soong'):
    fail("This script must be run from the top of an Android source tree.")

parser = argparse.ArgumentParser(description=('Finalize an extension SDK with prebuilts'))
parser.add_argument('-f', '--finalize_sdk', type=int, required=True, help='The numbered SDK to finalize.')
parser.add_argument('-b', '--bug', type=int, required=True, help='The bug number to add to the commit message.')
parser.add_argument('-r', '--readme', required=True, help='Version history entry to add to %s' % (COMPAT_REPO / COMPAT_README))
parser.add_argument('-a', '--amend_last_commit', action="store_true", help='Amend current HEAD commits instead of making new commits.')
parser.add_argument('-m', '--modules', action='append', help='Modules to include. Can be provided multiple times, or not at all for all modules.')
parser.add_argument('-l', '--local_mode', action="store_true", help='Local mode: use locally built artifacts and don\'t upload the result to Gerrit.')
parser.add_argument('bid', help='Build server build ID')
args = parser.parse_args()

build_target = BUILD_TARGET_TRAIN if args.bid[0] == 'T' else BUILD_TARGET_CONTINUOUS
branch_name = 'finalize-%d' % args.finalize_sdk
cmdline = shlex.join([x for x in sys.argv if x not in ['-a', '--amend_last_commit', '-l', '--local_mode']])
commit_message = COMMIT_TEMPLATE % (args.finalize_sdk, args.bid, cmdline, args.bug)
module_names = args.modules or ['*']

compat_dir = COMPAT_REPO.joinpath('extensions/%d' % args.finalize_sdk)
if compat_dir.is_dir():
    print('Removing existing dir %s' % compat_dir)
    shutil.rmtree(compat_dir)

created_dirs = defaultdict(set)
for m in module_names:
    tmpdir = fetch_artifacts(build_target, args.bid, m)
    for f in tmpdir.iterdir():
        repo = repo_for_sdk(f.name)
        dir = dir_for_sdk(f.name, args.finalize_sdk)
        target_dir = repo.joinpath(dir)
        if target_dir.is_dir():
            print('Removing existing dir %s' % target_dir)
            shutil.rmtree(target_dir)
        with zipfile.ZipFile(tmpdir.joinpath(f)) as zipFile:
            zipFile.extractall(target_dir)

        # Disable the Android.bp, but keep it for reference / potential future use.
        shutil.move(target_dir.joinpath('Android.bp'), target_dir.joinpath('Android.bp.auto'))

        print('Created %s' % target_dir)
        created_dirs[repo].add(dir)

        # Copy api txt files to compat tracking dir
        src_files = [Path(p) for p in glob.glob(os.path.join(target_dir, 'sdk_library/*/*.txt')) + glob.glob(os.path.join(target_dir, 'sdk_library/*/*.jar'))]
        for src_file in src_files:
            if is_ignored(src_file):
                continue
            api_type = src_file.parts[-2]
            dest_dir = compat_dir.joinpath(api_type, 'api') if src_file.suffix == '.txt' else compat_dir.joinpath(api_type)
            dest_file = maybe_tweak_compat_stem(dest_dir.joinpath(src_file.name))
            os.makedirs(dest_dir, exist_ok = True)
            shutil.copy(src_file, dest_file)
            created_dirs[COMPAT_REPO].add(dest_dir.relative_to(COMPAT_REPO))

if args.local_mode:
    print('Updated prebuilts using locally built artifacts. Don\'t submit or use for anything besides local testing.')
    sys.exit(0)

subprocess.check_output(['repo', 'start', branch_name] + list(created_dirs.keys()))
print('Running git commit')
for repo in created_dirs:
    git = ['git', '-C', str(repo)]
    subprocess.check_output(git + ['add'] + list(created_dirs[repo]))

    if repo == COMPAT_REPO:
        with open(COMPAT_REPO / COMPAT_README, "a") as readme:
            readme.write(f"- {args.finalize_sdk}: {args.readme}\n")
        subprocess.check_output(git + ['add', COMPAT_README])

    if args.amend_last_commit:
        change_id = '\n' + re.search(r'Change-Id: [^\\n]+', str(subprocess.check_output(git + ['log', '-1']))).group(0)
        subprocess.check_output(git + ['commit', '--amend', '-m', commit_message + change_id])
    else:
        subprocess.check_output(git + ['commit', '-m', commit_message])
