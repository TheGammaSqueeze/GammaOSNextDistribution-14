#!/usr/bin/env python3
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Checks for new reverts in LLVM on a nightly basis.

If any reverts are found that were previously unknown, this cherry-picks them or
fires off an email. All LLVM SHAs to monitor are autodetected.
"""

import argparse
import dataclasses
import io
import json
import logging
import os
import pprint
import subprocess
import sys
import time
from typing import Any, Callable, Dict, List, NamedTuple, Set, Tuple

from cros_utils import email_sender
from cros_utils import tiny_render
import get_llvm_hash
import get_upstream_patch
import git_llvm_rev
import revert_checker


ONE_DAY_SECS = 24 * 60 * 60
# How often to send an email about a HEAD not moving.
HEAD_STALENESS_ALERT_INTERVAL_SECS = 21 * ONE_DAY_SECS
# How long to wait after a HEAD changes for the first 'update' email to be sent.
HEAD_STALENESS_ALERT_INITIAL_SECS = 60 * ONE_DAY_SECS


# Not frozen, as `next_notification_timestamp` may be mutated.
@dataclasses.dataclass(frozen=False, eq=True)
class HeadInfo:
    """Information about about a HEAD that's tracked by this script."""

    # The most recent SHA observed for this HEAD.
    last_sha: str
    # The time at which the current value for this HEAD was first seen.
    first_seen_timestamp: int
    # The next timestamp to notify users if this HEAD doesn't move.
    next_notification_timestamp: int

    @classmethod
    def from_json(cls, json_object: Any) -> "HeadInfo":
        return cls(**json_object)

    def to_json(self) -> Any:
        return dataclasses.asdict(self)


@dataclasses.dataclass(frozen=True, eq=True)
class State:
    """Persistent state for this script."""

    # Mapping of LLVM SHA -> List of reverts that have been seen for it
    seen_reverts: Dict[str, List[str]] = dataclasses.field(default_factory=dict)
    # Mapping of friendly HEAD name (e.g., main-legacy) to last-known info
    # about it.
    heads: Dict[str, HeadInfo] = dataclasses.field(default_factory=dict)

    @classmethod
    def from_json(cls, json_object: Any) -> "State":
        # Autoupgrade old JSON files.
        if "heads" not in json_object:
            json_object = {
                "seen_reverts": json_object,
                "heads": {},
            }

        return cls(
            seen_reverts=json_object["seen_reverts"],
            heads={
                k: HeadInfo.from_json(v)
                for k, v in json_object["heads"].items()
            },
        )

    def to_json(self) -> Any:
        return {
            "seen_reverts": self.seen_reverts,
            "heads": {k: v.to_json() for k, v in self.heads.items()},
        }


def _find_interesting_android_shas(
    android_llvm_toolchain_dir: str,
) -> List[Tuple[str, str]]:
    llvm_project = os.path.join(
        android_llvm_toolchain_dir, "toolchain/llvm-project"
    )

    def get_llvm_merge_base(branch: str) -> str:
        head_sha = subprocess.check_output(
            ["git", "rev-parse", branch],
            cwd=llvm_project,
            encoding="utf-8",
        ).strip()
        merge_base = subprocess.check_output(
            ["git", "merge-base", branch, "aosp/upstream-main"],
            cwd=llvm_project,
            encoding="utf-8",
        ).strip()
        logging.info(
            "Merge-base for %s (HEAD == %s) and upstream-main is %s",
            branch,
            head_sha,
            merge_base,
        )
        return merge_base

    main_legacy = get_llvm_merge_base("aosp/master-legacy")  # nocheck
    testing_upstream = get_llvm_merge_base("aosp/testing-upstream")
    result: List[Tuple[str, str]] = [("main-legacy", main_legacy)]

    # If these are the same SHA, there's no point in tracking both.
    if main_legacy != testing_upstream:
        result.append(("testing-upstream", testing_upstream))
    else:
        logging.info(
            "main-legacy and testing-upstream are identical; ignoring "
            "the latter."
        )
    return result


def _parse_llvm_ebuild_for_shas(
    ebuild_file: io.TextIOWrapper,
) -> List[Tuple[str, str]]:
    def parse_ebuild_assignment(line: str) -> str:
        no_comments = line.split("#")[0]
        no_assign = no_comments.split("=", 1)[1].strip()
        assert no_assign.startswith('"') and no_assign.endswith('"'), no_assign
        return no_assign[1:-1]

    llvm_hash, llvm_next_hash = None, None
    for line in ebuild_file:
        if line.startswith("LLVM_HASH="):
            llvm_hash = parse_ebuild_assignment(line)
            if llvm_next_hash:
                break
        if line.startswith("LLVM_NEXT_HASH"):
            llvm_next_hash = parse_ebuild_assignment(line)
            if llvm_hash:
                break
    if not llvm_next_hash or not llvm_hash:
        raise ValueError(
            "Failed to detect SHAs for llvm/llvm_next. Got: "
            "llvm=%s; llvm_next=%s" % (llvm_hash, llvm_next_hash)
        )

    results: List[Tuple[str, str]] = [("llvm", llvm_hash)]
    if llvm_next_hash != llvm_hash:
        results.append(("llvm-next", llvm_next_hash))
    return results


def _find_interesting_chromeos_shas(
    chromeos_base: str,
) -> List[Tuple[str, str]]:
    llvm_dir = os.path.join(
        chromeos_base, "src/third_party/chromiumos-overlay/sys-devel/llvm"
    )
    candidate_ebuilds = [
        os.path.join(llvm_dir, x)
        for x in os.listdir(llvm_dir)
        if "_pre" in x and not os.path.islink(os.path.join(llvm_dir, x))
    ]

    if len(candidate_ebuilds) != 1:
        raise ValueError(
            "Expected exactly one llvm ebuild candidate; got %s"
            % pprint.pformat(candidate_ebuilds)
        )

    with open(candidate_ebuilds[0], encoding="utf-8") as f:
        return _parse_llvm_ebuild_for_shas(f)


_Email = NamedTuple(
    "_Email",
    [
        ("subject", str),
        ("body", tiny_render.Piece),
    ],
)


def _generate_revert_email(
    repository_name: str,
    friendly_name: str,
    sha: str,
    prettify_sha: Callable[[str], tiny_render.Piece],
    get_sha_description: Callable[[str], tiny_render.Piece],
    new_reverts: List[revert_checker.Revert],
) -> _Email:
    email_pieces = [
        "It looks like there may be %s across %s ("
        % (
            "a new revert" if len(new_reverts) == 1 else "new reverts",
            friendly_name,
        ),
        prettify_sha(sha),
        ").",
        tiny_render.line_break,
        tiny_render.line_break,
        "That is:" if len(new_reverts) == 1 else "These are:",
    ]

    revert_listing = []
    for revert in sorted(new_reverts, key=lambda r: r.sha):
        revert_listing.append(
            [
                prettify_sha(revert.sha),
                " (appears to revert ",
                prettify_sha(revert.reverted_sha),
                "): ",
                get_sha_description(revert.sha),
            ]
        )

    email_pieces.append(tiny_render.UnorderedList(items=revert_listing))
    email_pieces += [
        tiny_render.line_break,
        "PTAL and consider reverting them locally.",
    ]
    return _Email(
        subject="[revert-checker/%s] new %s discovered across %s"
        % (
            repository_name,
            "revert" if len(new_reverts) == 1 else "reverts",
            friendly_name,
        ),
        body=email_pieces,
    )


_EmailRecipients = NamedTuple(
    "_EmailRecipients",
    [
        ("well_known", List[str]),
        ("direct", List[str]),
    ],
)


def _send_revert_email(recipients: _EmailRecipients, email: _Email) -> None:
    email_sender.EmailSender().SendX20Email(
        subject=email.subject,
        identifier="revert-checker",
        well_known_recipients=recipients.well_known,
        direct_recipients=["gbiv@google.com"] + recipients.direct,
        text_body=tiny_render.render_text_pieces(email.body),
        html_body=tiny_render.render_html_pieces(email.body),
    )


def _write_state(state_file: str, new_state: State) -> None:
    tmp_file = state_file + ".new"
    try:
        with open(tmp_file, "w", encoding="utf-8") as f:
            json.dump(
                new_state.to_json(),
                f,
                sort_keys=True,
                indent=2,
                separators=(",", ": "),
            )
        os.rename(tmp_file, state_file)
    except:
        try:
            os.remove(tmp_file)
        except FileNotFoundError:
            pass
        raise


def _read_state(state_file: str) -> State:
    try:
        with open(state_file, encoding="utf-8") as f:
            return State.from_json(json.load(f))
    except FileNotFoundError:
        logging.info(
            "No state file found at %r; starting with an empty slate",
            state_file,
        )
        return State()


@dataclasses.dataclass(frozen=True)
class NewRevertInfo:
    """A list of new reverts for a given SHA."""

    friendly_name: str
    sha: str
    new_reverts: List[revert_checker.Revert]


def locate_new_reverts_across_shas(
    llvm_dir: str,
    interesting_shas: List[Tuple[str, str]],
    state: State,
) -> Tuple[State, List[NewRevertInfo]]:
    """Locates and returns yet-unseen reverts across `interesting_shas`."""
    new_state = State()
    revert_infos = []
    for friendly_name, sha in interesting_shas:
        logging.info("Finding reverts across %s (%s)", friendly_name, sha)
        all_reverts = revert_checker.find_reverts(
            llvm_dir, sha, root="origin/" + git_llvm_rev.MAIN_BRANCH
        )
        logging.info(
            "Detected the following revert(s) across %s:\n%s",
            friendly_name,
            pprint.pformat(all_reverts),
        )

        new_state.seen_reverts[sha] = [r.sha for r in all_reverts]

        if sha not in state.seen_reverts:
            logging.info("SHA %s is new to me", sha)
            existing_reverts = set()
        else:
            existing_reverts = set(state.seen_reverts[sha])

        new_reverts = [r for r in all_reverts if r.sha not in existing_reverts]
        if not new_reverts:
            logging.info("...All of which have been reported.")
            continue

        new_head_info = None
        if old_head_info := state.heads.get(friendly_name):
            if old_head_info.last_sha == sha:
                new_head_info = old_head_info

        if new_head_info is None:
            now = int(time.time())
            notify_at = HEAD_STALENESS_ALERT_INITIAL_SECS + now
            new_head_info = HeadInfo(
                last_sha=sha,
                first_seen_timestamp=now,
                next_notification_timestamp=notify_at,
            )
        new_state.heads[friendly_name] = new_head_info

        revert_infos.append(
            NewRevertInfo(
                friendly_name=friendly_name,
                sha=sha,
                new_reverts=new_reverts,
            )
        )
    return new_state, revert_infos


def do_cherrypick(
    chroot_path: str,
    llvm_dir: str,
    repository: str,
    interesting_shas: List[Tuple[str, str]],
    state: State,
    reviewers: List[str],
    cc: List[str],
) -> State:
    def prettify_sha(sha: str) -> tiny_render.Piece:
        rev = get_llvm_hash.GetVersionFrom(llvm_dir, sha)
        return prettify_sha_for_email(sha, rev)

    new_state = State()
    seen: Set[str] = set()

    new_state, new_reverts = locate_new_reverts_across_shas(
        llvm_dir, interesting_shas, state
    )

    for revert_info in new_reverts:
        if revert_info.friendly_name in seen:
            continue
        seen.add(revert_info.friendly_name)
        for sha, reverted_sha in revert_info.new_reverts:
            try:
                # We upload reverts for all platforms by default, since there's
                # no real reason for them to be CrOS-specific.
                get_upstream_patch.get_from_upstream(
                    chroot_path=chroot_path,
                    create_cl=True,
                    start_sha=reverted_sha,
                    patches=[sha],
                    reviewers=reviewers,
                    cc=cc,
                    platforms=(),
                )
            except get_upstream_patch.CherrypickError as e:
                logging.info("%s, skipping...", str(e))

    maybe_email_about_stale_heads(
        new_state,
        repository,
        recipients=_EmailRecipients(
            well_known=[],
            direct=reviewers + cc,
        ),
        prettify_sha=prettify_sha,
        is_dry_run=False,
    )
    return new_state


def prettify_sha_for_email(
    sha: str,
    rev: int,
) -> tiny_render.Piece:
    """Returns a piece of an email representing the given sha and its rev."""
    # 12 is arbitrary, but should be unambiguous enough.
    short_sha = sha[:12]
    return tiny_render.Switch(
        text=f"r{rev} ({short_sha})",
        html=tiny_render.Link(
            href=f"https://github.com/llvm/llvm-project/commit/{sha}",
            inner=f"r{rev}",
        ),
    )


def maybe_email_about_stale_heads(
    new_state: State,
    repository_name: str,
    recipients: _EmailRecipients,
    prettify_sha: Callable[[str], tiny_render.Piece],
    is_dry_run: bool,
) -> bool:
    """Potentially send an email about stale HEADs in `new_state`.

    These emails are sent to notify users of the current HEADs detected by this
    script. They:
    - aren't meant to hurry LLVM rolls along,
    - are worded to avoid the implication that an LLVM roll is taking an
      excessive amount of time, and
    - are initially sent at the 2 month point of seeing the same HEAD.

    We've had multiple instances in the past of upstream changes (e.g., moving
    to other git branches or repos) leading to this revert checker silently
    checking a very old HEAD for months. The intent is to send emails when the
    correctness of the HEADs we're working with _might_ be wrong.
    """
    logging.info("Checking HEAD freshness...")
    now = int(time.time())
    stale = sorted(
        (name, info)
        for name, info in new_state.heads.items()
        if info.next_notification_timestamp <= now
    )
    if not stale:
        logging.info("All HEADs are fresh-enough; no need to send an email.")
        return False

    stale_listings = []

    for name, info in stale:
        days = (now - info.first_seen_timestamp) // ONE_DAY_SECS
        pretty_rev = prettify_sha(info.last_sha)
        stale_listings.append(
            f"{name} at {pretty_rev}, which was last updated ~{days} days ago."
        )

    shas_are = "SHAs are" if len(stale_listings) > 1 else "SHA is"
    email_body = [
        "Hi! This is a friendly notification that the current upstream LLVM "
        f"{shas_are} being tracked by the LLVM revert checker:",
        tiny_render.UnorderedList(stale_listings),
        tiny_render.line_break,
        "If that's still correct, great! If it looks wrong, the revert "
        "checker's SHA autodetection may need an update. Please file a bug "
        "at go/crostc-bug if an update is needed. Thanks!",
    ]

    email = _Email(
        subject=f"[revert-checker/{repository_name}] Tracked branch update",
        body=email_body,
    )
    if is_dry_run:
        logging.info("Dry-run specified; would otherwise send email %s", email)
    else:
        _send_revert_email(recipients, email)

    next_notification = now + HEAD_STALENESS_ALERT_INTERVAL_SECS
    for _, info in stale:
        info.next_notification_timestamp = next_notification
    return True


def do_email(
    is_dry_run: bool,
    llvm_dir: str,
    repository: str,
    interesting_shas: List[Tuple[str, str]],
    state: State,
    recipients: _EmailRecipients,
) -> State:
    def prettify_sha(sha: str) -> tiny_render.Piece:
        rev = get_llvm_hash.GetVersionFrom(llvm_dir, sha)
        return prettify_sha_for_email(sha, rev)

    def get_sha_description(sha: str) -> tiny_render.Piece:
        return subprocess.check_output(
            ["git", "log", "-n1", "--format=%s", sha],
            cwd=llvm_dir,
            encoding="utf-8",
        ).strip()

    new_state, new_reverts = locate_new_reverts_across_shas(
        llvm_dir, interesting_shas, state
    )

    for revert_info in new_reverts:
        email = _generate_revert_email(
            repository,
            revert_info.friendly_name,
            revert_info.sha,
            prettify_sha,
            get_sha_description,
            revert_info.new_reverts,
        )
        if is_dry_run:
            logging.info(
                "Would send email:\nSubject: %s\nBody:\n%s\n",
                email.subject,
                tiny_render.render_text_pieces(email.body),
            )
        else:
            logging.info("Sending email with subject %r...", email.subject)
            _send_revert_email(recipients, email)
            logging.info("Email sent.")

    maybe_email_about_stale_heads(
        new_state, repository, recipients, prettify_sha, is_dry_run
    )
    return new_state


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "action",
        choices=["cherry-pick", "email", "dry-run"],
        help="Automatically cherry-pick upstream reverts, send an email, or "
        "write to stdout.",
    )
    parser.add_argument(
        "--state_file", required=True, help="File to store persistent state in."
    )
    parser.add_argument(
        "--llvm_dir", required=True, help="Up-to-date LLVM directory to use."
    )
    parser.add_argument("--debug", action="store_true")
    parser.add_argument(
        "--reviewers",
        type=str,
        nargs="*",
        help="""
        Requests reviews from REVIEWERS. All REVIEWERS must have existing
        accounts.
        """,
    )
    parser.add_argument(
        "--cc",
        type=str,
        nargs="*",
        help="""
        CCs the CL or email to the recipients. If in cherry-pick mode, all
        recipients must have Gerrit accounts.
        """,
    )

    subparsers = parser.add_subparsers(dest="repository")
    subparsers.required = True

    chromeos_subparser = subparsers.add_parser("chromeos")
    chromeos_subparser.add_argument(
        "--chromeos_dir",
        required=True,
        help="Up-to-date CrOS directory to use.",
    )

    android_subparser = subparsers.add_parser("android")
    android_subparser.add_argument(
        "--android_llvm_toolchain_dir",
        required=True,
        help="Up-to-date android-llvm-toolchain directory to use.",
    )

    return parser.parse_args(argv)


def find_chroot(
    opts: argparse.Namespace, cc: List[str]
) -> Tuple[str, List[Tuple[str, str]], _EmailRecipients]:
    if opts.repository == "chromeos":
        chroot_path = opts.chromeos_dir
        return (
            chroot_path,
            _find_interesting_chromeos_shas(chroot_path),
            _EmailRecipients(well_known=["mage"], direct=cc),
        )
    elif opts.repository == "android":
        if opts.action == "cherry-pick":
            raise RuntimeError(
                "android doesn't currently support automatic cherry-picking."
            )

        chroot_path = opts.android_llvm_toolchain_dir
        return (
            chroot_path,
            _find_interesting_android_shas(chroot_path),
            _EmailRecipients(
                well_known=[],
                direct=["android-llvm-dev@google.com"] + cc,
            ),
        )
    else:
        raise ValueError(f"Unknown repository {opts.repository}")


def main(argv: List[str]) -> int:
    opts = parse_args(argv)

    logging.basicConfig(
        format="%(asctime)s: %(levelname)s: "
        "%(filename)s:%(lineno)d: %(message)s",
        level=logging.DEBUG if opts.debug else logging.INFO,
    )

    action = opts.action
    llvm_dir = opts.llvm_dir
    repository = opts.repository
    state_file = opts.state_file
    reviewers = opts.reviewers if opts.reviewers else []
    cc = opts.cc if opts.cc else []

    chroot_path, interesting_shas, recipients = find_chroot(opts, cc)
    logging.info("Interesting SHAs were %r", interesting_shas)

    state = _read_state(state_file)
    logging.info("Loaded state\n%s", pprint.pformat(state))

    # We want to be as free of obvious side-effects as possible in case
    # something above breaks. Hence, action as late as possible.
    if action == "cherry-pick":
        new_state = do_cherrypick(
            chroot_path=chroot_path,
            llvm_dir=llvm_dir,
            repository=repository,
            interesting_shas=interesting_shas,
            state=state,
            reviewers=reviewers,
            cc=cc,
        )
    else:
        new_state = do_email(
            is_dry_run=action == "dry-run",
            llvm_dir=llvm_dir,
            interesting_shas=interesting_shas,
            repository=repository,
            state=state,
            recipients=recipients,
        )

    _write_state(state_file, new_state)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
