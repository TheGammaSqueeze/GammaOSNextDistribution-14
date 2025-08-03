# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tools for interacting with CrOS CLs, and the CQ in particular."""

import dataclasses
import json
import logging
import re
import subprocess
from typing import Any, Dict, Iterable, List, Optional


BuildID = int


def _run_bb_decoding_output(command: List[str], multiline: bool = False) -> Any:
    """Runs `bb` with the `json` flag, and decodes the command's output.

    Args:
        command: Command to run
        multiline: If True, this function will parse each line of bb's output
            as a separate JSON object, and a return a list of all parsed
            objects.
    """
    # `bb` always parses argv[1] as a command, so put `-json` after the first
    # arg to `bb`.
    run_command = ["bb", command[0], "-json"] + command[1:]
    stdout = subprocess.run(
        run_command,
        check=True,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        encoding="utf-8",
    ).stdout

    def parse_or_log(text: str) -> Any:
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            logging.error(
                "Error parsing JSON from command %r; bubbling up. Tried to "
                "parse: %r",
                run_command,
                text,
            )
            raise

    if multiline:
        return [
            parse_or_log(line)
            for line in stdout.splitlines()
            if line and not line.isspace()
        ]
    return parse_or_log(stdout)


@dataclasses.dataclass(frozen=True, eq=True)
class ChangeListURL:
    """A consistent representation of a CL URL.

    The __str__s always converts to a crrev.com URL.
    """

    cl_id: int
    patch_set: Optional[int] = None

    @classmethod
    def parse(cls, url: str) -> "ChangeListURL":
        url_re = re.compile(
            # Match an optional https:// header.
            r"(?:https?://)?"
            # Match either chromium-review or crrev, leaving the CL number and
            # patch set as the next parts. These can be parsed in unison.
            r"(?:chromium-review\.googlesource\.com.*/\+/"
            r"|crrev\.com/c/)"
            # Match the CL number...
            r"(\d+)"
            # and (optionally) the patch-set, as well as consuming any of the
            # path after the patch-set.
            r"(?:/(\d+)?(?:/.*)?)?"
            # Validate any sort of GET params for completeness.
            r"(?:$|[?&].*)"
        )

        m = url_re.fullmatch(url)
        if not m:
            raise ValueError(
                f"URL {url!r} was not recognized. Supported URL formats are "
                "crrev.com/c/${cl_number}/${patch_set_number}, and "
                "chromium-review.googlesource.com/c/project/path/+/"
                "${cl_number}/${patch_set_number}. The patch-set number is "
                "optional, and there may be a preceding http:// or https://."
            )
        cl_id, maybe_patch_set = m.groups()
        if maybe_patch_set is not None:
            maybe_patch_set = int(maybe_patch_set)
        return cls(int(cl_id), maybe_patch_set)

    @classmethod
    def parse_with_patch_set(cls, url: str) -> "ChangeListURL":
        """parse(), but raises a ValueError if no patchset is specified."""
        result = cls.parse(url)
        if result.patch_set is None:
            raise ValueError("A patchset number must be specified.")
        return result

    def __str__(self):
        result = f"https://crrev.com/c/{self.cl_id}"
        if self.patch_set is not None:
            result += f"/{self.patch_set}"
        return result


def builder_url(build_id: BuildID) -> str:
    """Returns a builder URL given a build ID."""
    return f"https://ci.chromium.org/b/{build_id}"


def fetch_cq_orchestrator_ids(
    cl: ChangeListURL,
) -> List[BuildID]:
    """Returns the BuildID of completed cq-orchestrator runs on a CL.

    Newer runs are sorted later in the list.
    """
    results: List[Dict[str, Any]] = _run_bb_decoding_output(
        [
            "ls",
            "-cl",
            str(cl),
            "chromeos/cq/cq-orchestrator",
        ],
        multiline=True,
    )

    # We can theoretically filter on a status flag, but it seems to only accept
    # at most one value. Filter here instead; parsing one or two extra JSON
    # objects is cheap.
    finished_results = [
        x for x in results if x["status"] not in ("scheduled", "started")
    ]

    # Sort by createTime. Fall back to build ID if a tie needs to be broken.
    # While `createTime` is a string, it's formatted so it can be sorted
    # correctly without parsing.
    finished_results.sort(key=lambda x: (x["createTime"], x["id"]))
    return [int(x["id"]) for x in finished_results]


@dataclasses.dataclass(frozen=True)
class CQOrchestratorOutput:
    """A class representing the output of a cq-orchestrator builder."""

    # The status of the CQ builder.
    status: str
    # A dict of builders that this CQ builder spawned.
    child_builders: Dict[str, BuildID]

    @classmethod
    def fetch(cls, bot_id: BuildID) -> "CQOrchestratorOutput":
        decoded: Dict[str, Any] = _run_bb_decoding_output(
            ["get", "-steps", str(bot_id)]
        )
        results = {}

        # cq-orchestrator spawns builders in a series of steps. Each step has a
        # markdownified link to the builder in the summaryMarkdown for each
        # step. This loop parses those out.
        build_url_re = re.compile(
            re.escape("https://cr-buildbucket.appspot.com/build/") + r"(\d+)"
        )
        # Example step name containing a build URL:
        # "run builds|schedule new builds|${builder_name}". `builder_name`
        # contains no spaces, though follow-up steps with the same prefix might
        # include spaces.
        step_name_re = re.compile(
            re.escape("run builds|schedule new builds|") + "([^ ]+)"
        )
        for step in decoded["steps"]:
            step_name = step["name"]
            m = step_name_re.fullmatch(step_name)
            if not m:
                continue

            builder = m.group(1)
            summary = step["summaryMarkdown"]
            ids = build_url_re.findall(summary)
            if len(ids) != 1:
                raise ValueError(
                    f"Parsing summary of builder {builder} failed: wanted one "
                    f"match for {build_url_re}; got {ids}. Full summary: "
                    f"{summary!r}"
                )
            if builder in results:
                raise ValueError(f"Builder {builder} spawned multiple times?")
            results[builder] = int(ids[0])
        return cls(child_builders=results, status=decoded["status"])


@dataclasses.dataclass(frozen=True)
class CQBoardBuilderOutput:
    """A class representing the output of a *-cq builder (e.g., brya-cq)."""

    # The status of the CQ builder.
    status: str
    # Link to artifacts produced by this builder. Not available if the builder
    # isn't yet finished, and not available if the builder failed in a weird
    # way (e.g., INFRA_ERROR)
    artifacts_link: Optional[str]

    @classmethod
    def fetch_many(
        cls, bot_ids: Iterable[BuildID]
    ) -> List["CQBoardBuilderOutput"]:
        """Fetches CQBoardBuilderOutput for the given bots."""
        bb_output = _run_bb_decoding_output(
            ["get", "-p"] + [str(x) for x in bot_ids], multiline=True
        )
        results = []
        for result in bb_output:
            status = result["status"]
            output = result.get("output")
            if output is None:
                artifacts_link = None
            else:
                artifacts_link = output["properties"].get("artifact_link")
            results.append(cls(status=status, artifacts_link=artifacts_link))
        return results


def parse_release_from_builder_artifacts_link(artifacts_link: str) -> str:
    """Parses the release version from a builder artifacts link.

    >>> parse_release_from_builder_artifacts_link(
        "gs://chromeos-image-archive/amd64-generic-asan-cq/"
        "R122-15711.0.0-59730-8761718482083052481")
    "R122-15711.0.0"
    """
    results = re.findall(r"/(R\d+-\d+\.\d+\.\d+)-", artifacts_link)
    if len(results) != 1:
        raise ValueError(
            f"Expected one release version in {artifacts_link}; got: {results}"
        )
    return results[0]
