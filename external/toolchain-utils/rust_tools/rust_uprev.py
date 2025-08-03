#!/usr/bin/env python3
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tool to automatically generate a new Rust uprev CL.

This tool is intended to automatically generate a CL to uprev Rust to
a newer version in Chrome OS, including creating a new Rust version or
removing an old version. When using the tool, the progress can be
saved to a JSON file, so the user can resume the process after a
failing step is fixed. Example usage to create a new version:

1. (outside chroot) $ ./rust_tools/rust_uprev.py            \\
                     --state_file /tmp/rust-to-1.60.0.json  \\
                     roll --uprev 1.60.0
2. Step "compile rust" failed due to the patches can't apply to new version.
3. Manually fix the patches.
4. Execute the command in step 1 again, but add "--continue" before "roll".
5. Iterate 1-4 for each failed step until the tool passes.

Besides "roll", the tool also support subcommands that perform
various parts of an uprev.

See `--help` for all available options.
"""

import argparse
import functools
import json
import logging
import os
import pathlib
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import threading
import time
from typing import (
    Any,
    Callable,
    Dict,
    List,
    NamedTuple,
    Optional,
    Protocol,
    Sequence,
    Tuple,
    TypeVar,
    Union,
)
import urllib.request

from llvm_tools import chroot
from llvm_tools import git


T = TypeVar("T")
Command = Sequence[Union[str, os.PathLike]]
PathOrStr = Union[str, os.PathLike]


class RunStepFn(Protocol):
    """Protocol that corresponds to run_step's type.

    This can be used as the type of a function parameter that accepts
    run_step as its value.
    """

    def __call__(
        self,
        step_name: str,
        step_fn: Callable[[], T],
        result_from_json: Optional[Callable[[Any], T]] = None,
        result_to_json: Optional[Callable[[T], Any]] = None,
    ) -> T:
        ...


def get_command_output(command: Command, *args, **kwargs) -> str:
    return subprocess.check_output(
        command, encoding="utf-8", *args, **kwargs
    ).strip()


def _get_source_root() -> Path:
    """Returns the path to the chromiumos directory."""
    return Path(get_command_output(["repo", "--show-toplevel"]))


SOURCE_ROOT = _get_source_root()
EQUERY = "equery"
GPG = "gpg"
GSUTIL = "gsutil.py"
MIRROR_PATH = "gs://chromeos-localmirror/distfiles"
EBUILD_PREFIX = SOURCE_ROOT / "src/third_party/chromiumos-overlay"
CROS_RUSTC_ECLASS = EBUILD_PREFIX / "eclass/cros-rustc.eclass"
# Keyserver to use with GPG. Not all keyservers have Rust's signing key;
# this must be set to a keyserver that does.
GPG_KEYSERVER = "keyserver.ubuntu.com"
PGO_RUST = Path(
    "/mnt/host/source"
    "/src/third_party/toolchain-utils/pgo_tools_rust/pgo_rust.py"
)
RUST_PATH = Path(EBUILD_PREFIX, "dev-lang", "rust")
# This is the signing key used by upstream Rust as of 2023-08-09.
# If the project switches to a different key, this will have to be updated.
# We require the key to be updated manually so that we have an opportunity
# to verify that the key change is legitimate.
RUST_SIGNING_KEY = "85AB96E6FA1BE5FE"
RUST_SRC_BASE_URI = "https://static.rust-lang.org/dist/"
# Packages that need to be processed like dev-lang/rust.
RUST_PACKAGES = (
    ("dev-lang", "rust-host"),
    ("dev-lang", "rust"),
)


class SignatureVerificationError(Exception):
    """Error that indicates verification of a downloaded file failed.

    Attributes:
        message: explanation of why the verification failed.
        path: the path to the file whose integrity was being verified.
    """

    def __init__(self, message: str, path: Path):
        super(SignatureVerificationError, self).__init__()
        self.message = message
        self.path = path


def get_command_output_unchecked(command: Command, *args, **kwargs) -> str:
    # pylint: disable=subprocess-run-check
    return subprocess.run(
        command,
        *args,
        **dict(
            {
                "check": False,
                "stdout": subprocess.PIPE,
                "encoding": "utf-8",
            },
            **kwargs,
        ),
    ).stdout.strip()


class RustVersion(NamedTuple):
    """NamedTuple represents a Rust version"""

    major: int
    minor: int
    patch: int

    def __str__(self):
        return f"{self.major}.{self.minor}.{self.patch}"

    @staticmethod
    def parse_from_ebuild(ebuild_name: PathOrStr) -> "RustVersion":
        input_re = re.compile(
            r"^rust-"
            r"(?P<major>\d+)\."
            r"(?P<minor>\d+)\."
            r"(?P<patch>\d+)"
            r"(:?-r\d+)?"
            r"\.ebuild$"
        )
        m = input_re.match(Path(ebuild_name).name)
        assert m, f"failed to parse {ebuild_name!r}"
        return RustVersion(
            int(m.group("major")), int(m.group("minor")), int(m.group("patch"))
        )

    @staticmethod
    def parse(x: str) -> "RustVersion":
        input_re = re.compile(
            r"^(?:rust-)?"
            r"(?P<major>\d+)\."
            r"(?P<minor>\d+)\."
            r"(?P<patch>\d+)"
            r"(?:.ebuild)?$"
        )
        m = input_re.match(x)
        assert m, f"failed to parse {x!r}"
        return RustVersion(
            int(m.group("major")), int(m.group("minor")), int(m.group("patch"))
        )


class PreparedUprev(NamedTuple):
    """Container for the information returned by prepare_uprev."""

    template_version: RustVersion


def compute_ebuild_path(category: str, name: str, version: RustVersion) -> Path:
    return EBUILD_PREFIX / category / name / f"{name}-{version}.ebuild"


def compute_rustc_src_name(version: RustVersion) -> str:
    return f"rustc-{version}-src.tar.gz"


def compute_rust_bootstrap_prebuilt_name(version: RustVersion) -> str:
    return f"rust-bootstrap-{version}.tbz2"


def find_ebuild_for_package(name: str) -> str:
    """Returns the path to the ebuild for the named package."""
    return run_in_chroot(
        [EQUERY, "w", name],
        stdout=subprocess.PIPE,
    ).stdout.strip()


def find_ebuild_path(
    directory: Path, name: str, version: Optional[RustVersion] = None
) -> Path:
    """Finds an ebuild in a directory.

    Returns the path to the ebuild file.  The match is constrained by
    name and optionally by version, but can match any patch level.
    E.g. "rust" version 1.3.4 can match rust-1.3.4.ebuild but also
    rust-1.3.4-r6.ebuild.

    The expectation is that there is only one matching ebuild, and
    an assert is raised if this is not the case. However, symlinks to
    ebuilds in the same directory are ignored, so having a
    rust-x.y.z-rn.ebuild symlink to rust-x.y.z.ebuild is allowed.
    """
    if version:
        pattern = f"{name}-{version}*.ebuild"
    else:
        pattern = f"{name}-*.ebuild"
    matches = set(directory.glob(pattern))
    result = []
    # Only count matches that are not links to other matches.
    for m in matches:
        try:
            target = os.readlink(directory / m)
        except OSError:
            # Getting here means the match is not a symlink to one of
            # the matching ebuilds, so add it to the result list.
            result.append(m)
            continue
        if directory / target not in matches:
            result.append(m)
    assert len(result) == 1, result
    return result[0]


def get_rust_bootstrap_version():
    """Get the version of the current rust-bootstrap package."""
    bootstrap_ebuild = find_ebuild_path(rust_bootstrap_path(), "rust-bootstrap")
    m = re.match(r"^rust-bootstrap-(\d+).(\d+).(\d+)", bootstrap_ebuild.name)
    assert m, bootstrap_ebuild.name
    return RustVersion(int(m.group(1)), int(m.group(2)), int(m.group(3)))


def parse_commandline_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--state_file",
        required=True,
        help="A state file to hold previous completed steps. If the file "
        "exists, it needs to be used together with --continue or --restart. "
        "If not exist (do not use --continue in this case), we will create a "
        "file for you.",
    )
    parser.add_argument(
        "--restart",
        action="store_true",
        help="Restart from the first step. Ignore the completed steps in "
        "the state file",
    )
    parser.add_argument(
        "--continue",
        dest="cont",
        action="store_true",
        help="Continue the steps from the state file",
    )

    create_parser_template = argparse.ArgumentParser(add_help=False)
    create_parser_template.add_argument(
        "--template",
        type=RustVersion.parse,
        default=None,
        help="A template to use for creating a Rust uprev from, in the form "
        "a.b.c The ebuild has to exist in the chroot. If not specified, the "
        "tool will use the current Rust version in the chroot as template.",
    )
    create_parser_template.add_argument(
        "--skip_compile",
        action="store_true",
        help="Skip compiling rust to test the tool. Only for testing",
    )

    subparsers = parser.add_subparsers(dest="subparser_name")
    subparser_names = []
    subparser_names.append("create")
    create_parser = subparsers.add_parser(
        "create",
        parents=[create_parser_template],
        help="Create changes uprevs Rust to a new version",
    )
    create_parser.add_argument(
        "--rust_version",
        type=RustVersion.parse,
        required=True,
        help="Rust version to uprev to, in the form a.b.c",
    )

    subparser_names.append("remove")
    remove_parser = subparsers.add_parser(
        "remove",
        help="Clean up old Rust version from chroot",
    )
    remove_parser.add_argument(
        "--rust_version",
        type=RustVersion.parse,
        default=None,
        help="Rust version to remove, in the form a.b.c If not "
        "specified, the tool will remove the oldest version in the chroot",
    )

    subparser_names.append("roll")
    roll_parser = subparsers.add_parser(
        "roll",
        parents=[create_parser_template],
        help="A command can create and upload a Rust uprev CL, including "
        "preparing the repo, creating new Rust uprev, deleting old uprev, "
        "and upload a CL to crrev.",
    )
    roll_parser.add_argument(
        "--uprev",
        type=RustVersion.parse,
        required=True,
        help="Rust version to uprev to, in the form a.b.c",
    )
    roll_parser.add_argument(
        "--remove",
        type=RustVersion.parse,
        default=None,
        help="Rust version to remove, in the form a.b.c If not "
        "specified, the tool will remove the oldest version in the chroot",
    )
    roll_parser.add_argument(
        "--skip_cross_compiler",
        action="store_true",
        help="Skip updating cross-compiler in the chroot",
    )
    roll_parser.add_argument(
        "--no_upload",
        action="store_true",
        help="If specified, the tool will not upload the CL for review",
    )

    args = parser.parse_args()
    if args.subparser_name not in subparser_names:
        parser.error("one of %s must be specified" % subparser_names)

    if args.cont and args.restart:
        parser.error("Please select either --continue or --restart")

    if os.path.exists(args.state_file):
        if not args.cont and not args.restart:
            parser.error(
                "State file exists, so you should either --continue "
                "or --restart"
            )
    if args.cont and not os.path.exists(args.state_file):
        parser.error("Indicate --continue but the state file does not exist")

    if args.restart and os.path.exists(args.state_file):
        os.remove(args.state_file)

    return args


def prepare_uprev(
    rust_version: RustVersion, template: RustVersion
) -> Optional[PreparedUprev]:
    ebuild_path = find_ebuild_for_rust_version(template)

    if rust_version <= template:
        logging.info(
            "Requested version %s is not newer than the template version %s.",
            rust_version,
            template,
        )
        return None

    logging.info(
        "Template Rust version is %s (ebuild: %s)",
        template,
        ebuild_path,
    )

    return PreparedUprev(template)


def create_ebuild(
    category: str,
    name: str,
    template_version: RustVersion,
    new_version: RustVersion,
) -> None:
    template_ebuild = compute_ebuild_path(category, name, template_version)
    new_ebuild = compute_ebuild_path(category, name, new_version)
    shutil.copyfile(template_ebuild, new_ebuild)
    subprocess.check_call(
        ["git", "add", new_ebuild.name], cwd=new_ebuild.parent
    )


def set_include_profdata_src(ebuild_path: os.PathLike, include: bool) -> None:
    """Changes an ebuild file to include or omit profile data from SRC_URI.

    If include is True, the ebuild file will be rewritten to include
    profile data in SRC_URI.

    If include is False, the ebuild file will be rewritten to omit profile
    data from SRC_URI.
    """
    if include:
        old = ""
        new = "yes"
    else:
        old = "yes"
        new = ""
    contents = Path(ebuild_path).read_text(encoding="utf-8")
    contents, subs = re.subn(
        f"^INCLUDE_PROFDATA_IN_SRC_URI={old}$",
        f"INCLUDE_PROFDATA_IN_SRC_URI={new}",
        contents,
        flags=re.MULTILINE,
    )
    # We expect exactly one substitution.
    assert subs == 1, "Failed to update INCLUDE_PROFDATA_IN_SRC_URI"
    Path(ebuild_path).write_text(contents, encoding="utf-8")


def update_bootstrap_version(
    path: PathOrStr, new_bootstrap_version: RustVersion
) -> None:
    path = Path(path)
    contents = path.read_text(encoding="utf-8")
    contents, subs = re.subn(
        r"^BOOTSTRAP_VERSION=.*$",
        'BOOTSTRAP_VERSION="%s"' % (new_bootstrap_version,),
        contents,
        flags=re.MULTILINE,
    )
    if not subs:
        raise RuntimeError(f"BOOTSTRAP_VERSION not found in {path}")
    path.write_text(contents, encoding="utf-8")
    logging.info("Rust BOOTSTRAP_VERSION updated to %s", new_bootstrap_version)


def ebuild_actions(
    package: str, actions: List[str], sudo: bool = False
) -> None:
    ebuild_path_inchroot = find_ebuild_for_package(package)
    cmd = ["ebuild", ebuild_path_inchroot] + actions
    if sudo:
        cmd = ["sudo"] + cmd
    run_in_chroot(cmd)


def fetch_distfile_from_mirror(name: str) -> None:
    """Gets the named file from the local mirror.

    This ensures that the file exists on the mirror, and
    that we can read it. We overwrite any existing distfile
    to ensure the checksums that `ebuild manifest` records
    match the file as it exists on the mirror.

    This function also attempts to verify the ACL for
    the file (which is expected to have READER permission
    for allUsers). We can only see the ACL if the user
    gsutil runs with is the owner of the file. If not,
    we get an access denied error. We also count this
    as a success, because it means we were able to fetch
    the file even though we don't own it.
    """
    mirror_file = MIRROR_PATH + "/" + name
    local_file = get_distdir() / name
    cmd: Command = [GSUTIL, "cp", mirror_file, local_file]
    logging.info("Running %r", cmd)
    rc = subprocess.call(cmd)
    if rc != 0:
        logging.error(
            """Could not fetch %s

If the file does not yet exist at %s
please download the file, verify its integrity
with something like:

curl -O https://static.rust-lang.org/dist/%s
gpg --verify %s.asc

You may need to import the signing key first, e.g.:

gpg --recv-keys 85AB96E6FA1BE5FE

Once you have verify the integrity of the file, upload
it to the local mirror using gsutil cp.
""",
            mirror_file,
            MIRROR_PATH,
            name,
            name,
        )
        raise Exception(f"Could not fetch {mirror_file}")
    # Check that the ACL allows allUsers READER access.
    # If we get an AccessDeniedAcception here, that also
    # counts as a success, because we were able to fetch
    # the file as a non-owner.
    cmd = [GSUTIL, "acl", "get", mirror_file]
    logging.info("Running %r", cmd)
    output = get_command_output_unchecked(cmd, stderr=subprocess.STDOUT)
    acl_verified = False
    if "AccessDeniedException:" in output:
        acl_verified = True
    else:
        acl = json.loads(output)
        for x in acl:
            if x["entity"] == "allUsers" and x["role"] == "READER":
                acl_verified = True
                break
    if not acl_verified:
        logging.error("Output from acl get:\n%s", output)
        raise Exception("Could not verify that allUsers has READER permission")


def fetch_bootstrap_distfiles(version: RustVersion) -> None:
    """Fetches rust-bootstrap distfiles from the local mirror

    Fetches the distfiles for a rust-bootstrap ebuild to ensure they
    are available on the mirror and the local copies are the same as
    the ones on the mirror.
    """
    fetch_distfile_from_mirror(compute_rust_bootstrap_prebuilt_name(version))
    fetch_distfile_from_mirror(compute_rustc_src_name(version))


def fetch_rust_distfiles(version: RustVersion) -> None:
    """Fetches rust distfiles from the local mirror

    Fetches the distfiles for a rust ebuild to ensure they
    are available on the mirror and the local copies are
    the same as the ones on the mirror.
    """
    fetch_distfile_from_mirror(compute_rustc_src_name(version))


def fetch_rust_src_from_upstream(uri: str, local_path: Path) -> None:
    """Fetches Rust sources from upstream.

    This downloads the source distribution and the .asc file
    containing the signatures. It then verifies that the sources
    have the expected signature and have been signed by
    the expected key.
    """
    subprocess.run(
        [GPG, "--keyserver", GPG_KEYSERVER, "--recv-keys", RUST_SIGNING_KEY],
        check=True,
    )
    subprocess.run(
        [GPG, "--keyserver", GPG_KEYSERVER, "--refresh-keys", RUST_SIGNING_KEY],
        check=True,
    )
    asc_uri = uri + ".asc"
    local_asc_path = Path(local_path.parent, local_path.name + ".asc")
    logging.info("Fetching %s", uri)
    urllib.request.urlretrieve(uri, local_path)
    logging.info("%s fetched", uri)

    # Raise SignatureVerificationError if we cannot get the signature.
    try:
        logging.info("Fetching %s", asc_uri)
        urllib.request.urlretrieve(asc_uri, local_asc_path)
        logging.info("%s fetched", asc_uri)
    except Exception as e:
        raise SignatureVerificationError(
            f"error fetching signature file {asc_uri}",
            local_path,
        ) from e

    # Raise SignatureVerificationError if verifying the signature
    # failed.
    try:
        output = get_command_output(
            [GPG, "--verify", "--status-fd", "1", local_asc_path]
        )
    except subprocess.CalledProcessError as e:
        raise SignatureVerificationError(
            f"error verifying signature. GPG output:\n{e.stdout}",
            local_path,
        ) from e

    # Raise SignatureVerificationError if the file was not signed
    # with the expected key.
    if f"GOODSIG {RUST_SIGNING_KEY}" not in output:
        message = f"GOODSIG {RUST_SIGNING_KEY} not found in output"
        if f"REVKEYSIG {RUST_SIGNING_KEY}" in output:
            message = "signing key has been revoked"
        elif f"EXPKEYSIG {RUST_SIGNING_KEY}" in output:
            message = "signing key has expired"
        elif f"EXPSIG {RUST_SIGNING_KEY}" in output:
            message = "signature has expired"
        raise SignatureVerificationError(
            f"{message}. GPG output:\n{output}",
            local_path,
        )


def get_distdir() -> Path:
    """Returns portage's distdir outside the chroot."""
    return SOURCE_ROOT / ".cache/distfiles"


def mirror_has_file(name: str) -> bool:
    """Checks if the mirror has the named file."""
    mirror_file = MIRROR_PATH + "/" + name
    cmd: Command = [GSUTIL, "ls", mirror_file]
    proc = subprocess.run(
        cmd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
    )
    if "URLs matched no objects" in proc.stdout:
        return False
    elif proc.returncode == 0:
        return True

    raise Exception(
        "Unexpected result from gsutil ls:"
        f" rc {proc.returncode} output:\n{proc.stdout}"
    )


def mirror_rust_source(version: RustVersion) -> None:
    """Ensures source code for a Rust version is on the local mirror.

    If the source code is not found on the mirror, it is fetched
    from upstream, its integrity is verified, and it is uploaded
    to the mirror.
    """
    filename = compute_rustc_src_name(version)
    if mirror_has_file(filename):
        logging.info("%s is present on the mirror", filename)
        return
    uri = f"{RUST_SRC_BASE_URI}{filename}"
    local_path = get_distdir() / filename
    mirror_path = f"{MIRROR_PATH}/{filename}"
    fetch_rust_src_from_upstream(uri, local_path)
    subprocess.run(
        [GSUTIL, "cp", "-a", "public-read", local_path, mirror_path],
        check=True,
    )


def update_rust_packages(
    pkgatom: str, rust_version: RustVersion, add: bool
) -> None:
    package_file = EBUILD_PREFIX.joinpath(
        "profiles/targets/chromeos/package.provided"
    )
    with open(package_file, encoding="utf-8") as f:
        contents = f.read()
    if add:
        rust_packages_re = re.compile(
            "^" + re.escape(pkgatom) + r"-\d+\.\d+\.\d+$", re.MULTILINE
        )
        rust_packages = rust_packages_re.findall(contents)
        # Assume all the rust packages are in alphabetical order, so insert
        # the new version to the place after the last rust_packages
        new_str = f"{pkgatom}-{rust_version}"
        new_contents = contents.replace(
            rust_packages[-1], f"{rust_packages[-1]}\n{new_str}"
        )
        logging.info("%s has been inserted into package.provided", new_str)
    else:
        old_str = f"{pkgatom}-{rust_version}\n"
        assert old_str in contents, f"{old_str!r} not found in package.provided"
        new_contents = contents.replace(old_str, "")
        logging.info("%s has been removed from package.provided", old_str)

    with open(package_file, "w", encoding="utf-8") as f:
        f.write(new_contents)


def update_virtual_rust(
    template_version: RustVersion, new_version: RustVersion
) -> None:
    template_ebuild = find_ebuild_path(
        EBUILD_PREFIX.joinpath("virtual/rust"), "rust", template_version
    )
    virtual_rust_dir = template_ebuild.parent
    new_name = f"rust-{new_version}.ebuild"
    new_ebuild = virtual_rust_dir.joinpath(new_name)
    shutil.copyfile(template_ebuild, new_ebuild)
    subprocess.check_call(["git", "add", new_name], cwd=virtual_rust_dir)


def unmerge_package_if_installed(pkgatom: str) -> None:
    """Unmerges a package if it is installed."""
    shpkg = shlex.quote(pkgatom)
    run_in_chroot(
        [
            "sudo",
            "bash",
            "-c",
            f"! emerge --pretend --quiet --unmerge {shpkg}"
            f" || emerge --rage-clean {shpkg}",
        ],
    )


def perform_step(
    state_file: pathlib.Path,
    tmp_state_file: pathlib.Path,
    completed_steps: Dict[str, Any],
    step_name: str,
    step_fn: Callable[[], T],
    result_from_json: Optional[Callable[[Any], T]] = None,
    result_to_json: Optional[Callable[[T], Any]] = None,
) -> T:
    if step_name in completed_steps:
        logging.info("Skipping previously completed step %s", step_name)
        if result_from_json:
            return result_from_json(completed_steps[step_name])
        return completed_steps[step_name]

    logging.info("Running step %s", step_name)
    val = step_fn()
    logging.info("Step %s complete", step_name)
    if result_to_json:
        completed_steps[step_name] = result_to_json(val)
    else:
        completed_steps[step_name] = val

    with tmp_state_file.open("w", encoding="utf-8") as f:
        json.dump(completed_steps, f, indent=4)
    tmp_state_file.rename(state_file)
    return val


def prepare_uprev_from_json(obj: Any) -> Optional[PreparedUprev]:
    if not obj:
        return None
    version = obj[0]
    return PreparedUprev(
        RustVersion(*version),
    )


def prepare_uprev_to_json(
    prepared_uprev: Optional[PreparedUprev],
) -> Optional[Tuple[RustVersion]]:
    if prepared_uprev is None:
        return None
    return (prepared_uprev.template_version,)


def create_rust_uprev(
    rust_version: RustVersion,
    template_version: RustVersion,
    skip_compile: bool,
    run_step: RunStepFn,
) -> None:
    prepared = run_step(
        "prepare uprev",
        lambda: prepare_uprev(rust_version, template_version),
        result_from_json=prepare_uprev_from_json,
        result_to_json=prepare_uprev_to_json,
    )
    if prepared is None:
        return
    template_version = prepared.template_version

    run_step(
        "mirror bootstrap sources",
        lambda: mirror_rust_source(
            template_version,
        ),
    )
    run_step(
        "mirror rust sources",
        lambda: mirror_rust_source(
            rust_version,
        ),
    )

    # The fetch steps will fail (on purpose) if the files they check for
    # are not available on the mirror. To make them pass, fetch the
    # required files yourself, verify their checksums, then upload them
    # to the mirror.
    run_step(
        "fetch bootstrap distfiles",
        lambda: fetch_bootstrap_distfiles(template_version),
    )
    run_step("fetch rust distfiles", lambda: fetch_rust_distfiles(rust_version))
    run_step(
        "update bootstrap version",
        lambda: update_bootstrap_version(CROS_RUSTC_ECLASS, template_version),
    )
    run_step(
        "turn off profile data sources in cros-rustc.eclass",
        lambda: set_include_profdata_src(CROS_RUSTC_ECLASS, include=False),
    )

    for category, name in RUST_PACKAGES:
        run_step(
            f"create new {category}/{name} ebuild",
            functools.partial(
                create_ebuild,
                category,
                name,
                template_version,
                rust_version,
            ),
        )

    run_step(
        "update dev-lang/rust-host manifest to add new version",
        lambda: ebuild_actions("dev-lang/rust-host", ["manifest"]),
    )

    run_step(
        "generate profile data for rustc",
        lambda: run_in_chroot([PGO_RUST, "generate"]),
        # Avoid returning subprocess.CompletedProcess, which cannot be
        # serialized to JSON.
        result_to_json=lambda _x: None,
    )
    run_step(
        "upload profile data for rustc",
        lambda: run_in_chroot([PGO_RUST, "upload-profdata"]),
        # Avoid returning subprocess.CompletedProcess, which cannot be
        # serialized to JSON.
        result_to_json=lambda _x: None,
    )
    run_step(
        "turn on profile data sources in cros-rustc.eclass",
        lambda: set_include_profdata_src(CROS_RUSTC_ECLASS, include=True),
    )
    run_step(
        "update dev-lang/rust-host manifest to add profile data",
        lambda: ebuild_actions("dev-lang/rust-host", ["manifest"]),
    )
    if not skip_compile:
        run_step("build packages", lambda: rebuild_packages(rust_version))
    run_step(
        "insert host version into rust packages",
        lambda: update_rust_packages(
            "dev-lang/rust-host", rust_version, add=True
        ),
    )
    run_step(
        "insert target version into rust packages",
        lambda: update_rust_packages("dev-lang/rust", rust_version, add=True),
    )
    run_step(
        "upgrade virtual/rust",
        lambda: update_virtual_rust(template_version, rust_version),
    )


def find_rust_versions() -> List[Tuple[RustVersion, Path]]:
    """Returns (RustVersion, ebuild_path) for base versions of dev-lang/rust.

    This excludes symlinks to ebuilds, so if rust-1.34.0.ebuild and
    rust-1.34.0-r1.ebuild both exist and -r1 is a symlink to the other,
    only rust-1.34.0.ebuild will be in the return value.
    """
    return [
        (RustVersion.parse_from_ebuild(ebuild), ebuild)
        for ebuild in RUST_PATH.iterdir()
        if ebuild.suffix == ".ebuild" and not ebuild.is_symlink()
    ]


def find_oldest_rust_version() -> RustVersion:
    """Returns the RustVersion of the oldest dev-lang/rust ebuild."""
    rust_versions = find_rust_versions()
    if len(rust_versions) <= 1:
        raise RuntimeError("Expect to find more than one Rust versions")
    return min(rust_versions)[0]


def find_ebuild_for_rust_version(version: RustVersion) -> Path:
    """Returns the path of the ebuild for the given version of dev-lang/rust."""
    return find_ebuild_path(RUST_PATH, "rust", version)


def rebuild_packages(version: RustVersion):
    """Rebuild packages modified by this script."""
    # Remove all packages we modify to avoid depending on preinstalled
    # versions. This ensures that the packages can really be built.
    packages = [f"{category}/{name}" for category, name in RUST_PACKAGES]
    for pkg in packages:
        unmerge_package_if_installed(pkg)
    # Mention only dev-lang/rust explicitly, so that others are pulled
    # in as dependencies (letting us detect dependency errors).
    # Packages we modify are listed in --usepkg-exclude to ensure they
    # are built from source.
    try:
        run_in_chroot(
            [
                "sudo",
                "emerge",
                "--quiet-build",
                "--usepkg-exclude",
                " ".join(packages),
                f"=dev-lang/rust-{version}",
            ],
        )
    except:
        logging.warning(
            "Failed to build dev-lang/rust or one of its dependencies."
            " If necessary, you can restore rust and rust-host from"
            " binary packages:\n  sudo emerge --getbinpkgonly dev-lang/rust"
        )
        raise


def remove_ebuild_version(path: PathOrStr, name: str, version: RustVersion):
    """Remove the specified version of an ebuild.

    Removes {path}/{name}-{version}.ebuild and {path}/{name}-{version}-*.ebuild
    using git rm.

    Args:
        path: The directory in which the ebuild files are.
        name: The name of the package (e.g. 'rust').
        version: The version of the ebuild to remove.
    """
    path = Path(path)
    pattern = f"{name}-{version}-*.ebuild"
    matches = list(path.glob(pattern))
    ebuild = path / f"{name}-{version}.ebuild"
    if ebuild.exists():
        matches.append(ebuild)
    if not matches:
        logging.warning(
            "No ebuilds matching %s version %s in %r", name, version, str(path)
        )
    for m in matches:
        remove_files(m.name, path)


def remove_files(filename: PathOrStr, path: PathOrStr) -> None:
    subprocess.check_call(["git", "rm", filename], cwd=path)


def remove_rust_uprev(
    rust_version: Optional[RustVersion],
    run_step: RunStepFn,
) -> None:
    def find_desired_rust_version() -> RustVersion:
        if rust_version:
            return rust_version
        return find_oldest_rust_version()

    def find_desired_rust_version_from_json(obj: Any) -> RustVersion:
        return RustVersion(*obj)

    delete_version = run_step(
        "find rust version to delete",
        find_desired_rust_version,
        result_from_json=find_desired_rust_version_from_json,
    )

    for category, name in RUST_PACKAGES:
        run_step(
            f"remove old {name} ebuild",
            functools.partial(
                remove_ebuild_version,
                EBUILD_PREFIX / category / name,
                name,
                delete_version,
            ),
        )

    run_step(
        "update dev-lang/rust-host manifest to delete old version",
        lambda: ebuild_actions("dev-lang/rust-host", ["manifest"]),
    )
    run_step(
        "remove target version from rust packages",
        lambda: update_rust_packages(
            "dev-lang/rust", delete_version, add=False
        ),
    )
    run_step(
        "remove host version from rust packages",
        lambda: update_rust_packages(
            "dev-lang/rust-host", delete_version, add=False
        ),
    )
    run_step("remove virtual/rust", lambda: remove_virtual_rust(delete_version))


def remove_virtual_rust(delete_version: RustVersion) -> None:
    remove_ebuild_version(
        EBUILD_PREFIX.joinpath("virtual/rust"), "rust", delete_version
    )


def rust_bootstrap_path() -> Path:
    return EBUILD_PREFIX.joinpath("dev-lang/rust-bootstrap")


def create_new_repo(rust_version: RustVersion) -> None:
    output = get_command_output(
        ["git", "status", "--porcelain"], cwd=EBUILD_PREFIX
    )
    if output:
        raise RuntimeError(
            f"{EBUILD_PREFIX} has uncommitted changes, please either discard "
            "them or commit them."
        )
    git.CreateBranch(EBUILD_PREFIX, f"rust-to-{rust_version}")


def build_cross_compiler(template_version: RustVersion) -> None:
    # Get target triples in ebuild
    rust_ebuild = find_ebuild_path(RUST_PATH, "rust", template_version)
    contents = rust_ebuild.read_text(encoding="utf-8")

    target_triples_re = re.compile(r"RUSTC_TARGET_TRIPLES=\(([^)]+)\)")
    m = target_triples_re.search(contents)
    assert m, "RUST_TARGET_TRIPLES not found in rust ebuild"
    target_triples = m.group(1).strip().split("\n")

    compiler_targets_to_install = [
        target.strip() for target in target_triples if "cros-" in target
    ]
    for target in target_triples:
        if "cros-" not in target:
            continue
        target = target.strip()

    # We also always need arm-none-eabi, though it's not mentioned in
    # RUSTC_TARGET_TRIPLES.
    compiler_targets_to_install.append("arm-none-eabi")

    logging.info("Emerging cross compilers %s", compiler_targets_to_install)
    run_in_chroot(
        ["sudo", "emerge", "-j", "-G"]
        + [f"cross-{target}/gcc" for target in compiler_targets_to_install],
    )


def create_new_commit(rust_version: RustVersion) -> None:
    subprocess.check_call(["git", "add", "-A"], cwd=EBUILD_PREFIX)
    messages = [
        f"[DO NOT SUBMIT] dev-lang/rust: upgrade to Rust {rust_version}",
        "",
        "This CL is created by rust_uprev tool automatically." "",
        "BUG=None",
        "TEST=Use CQ to test the new Rust version",
    ]
    branch = f"rust-to-{rust_version}"
    git.CommitChanges(EBUILD_PREFIX, messages)
    git.UploadChanges(EBUILD_PREFIX, branch)


def run_in_chroot(cmd: Command, *args, **kwargs) -> subprocess.CompletedProcess:
    """Runs a command in the ChromiumOS chroot.

    This takes the same arguments as subprocess.run(). By default,
    it uses check=True, encoding="utf-8". If needed, these can be
    overridden by keyword arguments passed to run_in_chroot().
    """
    full_kwargs = dict(
        {
            "check": True,
            "encoding": "utf-8",
        },
        **kwargs,
    )
    full_cmd = ["cros_sdk", "--"] + list(cmd)
    logging.info("Running %s", shlex.join(str(x) for x in full_cmd))
    # pylint: disable=subprocess-run-check
    # (check is actually set above; it defaults to True)
    return subprocess.run(full_cmd, *args, **full_kwargs)


def sudo_keepalive() -> None:
    """Ensures we have sudo credentials, and keeps them up-to-date.

    Some operations (notably run_in_chroot) run sudo, which may require
    user interaction. To avoid blocking progress while we sit waiting
    for that interaction, sudo_keepalive checks that we have cached
    sudo credentials, gets them if necessary, then keeps them up-to-date
    so that the rest of the script can run without needing to get
    sudo credentials again.
    """
    logging.info(
        "Caching sudo credentials for running commands inside the chroot"
    )
    # Exits successfully if cached credentials exist. Otherwise, tries
    # created cached credentials, prompting for authentication if necessary.
    subprocess.run(["sudo", "true"], check=True)

    def sudo_keepalive_loop() -> None:
        # Between credential refreshes, we sleep so that we don't
        # unnecessarily burn CPU cycles. The sleep time must be shorter
        # than sudo's configured cached credential expiration time, which
        # is 15 minutes by default.
        sleep_seconds = 10 * 60
        # So as to not keep credentials cached forever, we limit the number
        # of times we will refresh them.
        max_seconds = 16 * 3600
        max_refreshes = max_seconds // sleep_seconds
        for _x in range(max_refreshes):
            # Refreshes cached credentials if they exist, but never prompts
            # for anything. If cached credentials do not exist, this
            # command exits with an error. We ignore that error to keep the
            # loop going, so that cached credentials will be kept fresh
            # again once we have them (e.g. after the next cros_sdk command
            # successfully authenticates the user).
            #
            # The standard file descriptors are all redirected to/from
            # /dev/null to prevent this command from consuming any input
            # or mixing its output with that of the other commands rust_uprev
            # runs (which could be confusing, for example making it look like
            # errors occurred during a build when they are actually in a
            # separate task).
            #
            # Note: The command specifically uses "true" and not "-v", because
            # it turns out that "-v" actually will prompt for a password when
            # sudo is configured with NOPASSWD=all, even though in that case
            # no password is required to run actual commands.
            subprocess.run(
                ["sudo", "-n", "true"],
                check=False,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            time.sleep(sleep_seconds)

    # daemon=True causes the thread to be killed when the script exits.
    threading.Thread(target=sudo_keepalive_loop, daemon=True).start()


def main() -> None:
    chroot.VerifyOutsideChroot()
    logging.basicConfig(level=logging.INFO)
    args = parse_commandline_args()
    state_file = pathlib.Path(args.state_file)
    tmp_state_file = state_file.with_suffix(".tmp")

    try:
        with state_file.open(encoding="utf-8") as f:
            completed_steps = json.load(f)
    except FileNotFoundError:
        completed_steps = {}

    def run_step(
        step_name: str,
        step_fn: Callable[[], T],
        result_from_json: Optional[Callable[[Any], T]] = None,
        result_to_json: Optional[Callable[[T], Any]] = None,
    ) -> T:
        return perform_step(
            state_file,
            tmp_state_file,
            completed_steps,
            step_name,
            step_fn,
            result_from_json,
            result_to_json,
        )

    if args.subparser_name == "create":
        sudo_keepalive()
        create_rust_uprev(
            args.rust_version, args.template, args.skip_compile, run_step
        )
    elif args.subparser_name == "remove":
        remove_rust_uprev(args.rust_version, run_step)
    else:
        # If you have added more subparser_name, please also add the handlers
        # above
        assert args.subparser_name == "roll"

        sudo_keepalive()
        # Determine the template version, if not given.
        template_version = args.template
        if template_version is None:
            rust_ebuild = find_ebuild_for_package("rust")
            template_version = RustVersion.parse_from_ebuild(rust_ebuild)

        run_step("create new repo", lambda: create_new_repo(args.uprev))
        if not args.skip_cross_compiler:
            run_step(
                "build cross compiler",
                lambda: build_cross_compiler(template_version),
            )
        create_rust_uprev(
            args.uprev, template_version, args.skip_compile, run_step
        )
        remove_rust_uprev(args.remove, run_step)
        prepared = prepare_uprev_from_json(completed_steps["prepare uprev"])
        assert prepared is not None, "no prepared uprev decoded from JSON"
        if not args.no_upload:
            run_step(
                "create rust uprev CL", lambda: create_new_commit(args.uprev)
            )


if __name__ == "__main__":
    main()
