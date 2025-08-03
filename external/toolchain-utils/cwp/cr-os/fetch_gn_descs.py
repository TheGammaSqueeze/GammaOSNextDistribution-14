#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Produces a JSON object of `gn desc`'s output for each given arch.

A full Chromium checkout is required in order to run this script.

The result is of the form:
{
  "arch1": {
    "//gn:target": {
      'configs": ["bar"],
      "sources": ["foo"]
    }
  }
}
"""


import argparse
import json
import logging
import os
import subprocess
import sys
import tempfile


def _find_chromium_root(search_from):
    """Finds the chromium root directory from `search_from`."""
    current = search_from
    while current != "/":
        if os.path.isfile(os.path.join(current, ".gclient")):
            return current
        current = os.path.dirname(current)
    raise ValueError(
        "%s doesn't appear to be a Chromium subdirectory" % search_from
    )


def _create_gn_args_for(arch):
    """Creates a `gn args` listing for the given architecture."""
    # FIXME(gbiv): is_chromeos_device = True would be nice to support, as well.
    # Requires playing nicely with SimpleChrome though, and this should be "close
    # enough" for now.
    return "\n".join(
        (
            'target_os = "chromeos"',
            'target_cpu = "%s"' % arch,
            "is_official_build = true",
            "is_chrome_branded = true",
        )
    )


def _parse_gn_desc_output(output):
    """Parses the output of `gn desc --format=json`.

    Args:
      output: a seekable file containing the JSON output of `gn desc`.

    Returns:
      A tuple of (warnings, gn_desc_json).
    """
    warnings = []
    desc_json = None
    while True:
        start_pos = output.tell()
        next_line = next(output, None)
        if next_line is None:
            raise ValueError("No JSON found in the given gn file")

        if next_line.lstrip().startswith("{"):
            output.seek(start_pos)
            desc_json = json.load(output)
            break

        warnings.append(next_line)

    return "".join(warnings).strip(), desc_json


def _run_gn_desc(in_dir, gn_args):
    logging.info("Running `gn gen`...")
    subprocess.check_call(["gn", "gen", ".", "--args=" + gn_args], cwd=in_dir)

    logging.info("Running `gn desc`...")
    with tempfile.TemporaryFile(mode="r+", encoding="utf-8") as f:
        gn_command = ["gn", "desc", "--format=json", ".", "//*:*"]
        exit_code = subprocess.call(gn_command, stdout=f, cwd=in_dir)
        f.seek(0)
        if exit_code:
            logging.error("gn failed; stdout:\n%s", f.read())
            raise subprocess.CalledProcessError(exit_code, gn_command)
        warnings, result = _parse_gn_desc_output(f)

    if warnings:
        logging.warning(
            "Encountered warning(s) running `gn desc`:\n%s", warnings
        )
    return result


def _fix_result(rename_out, out_dir, chromium_root, gn_desc):
    """Performs postprocessing on `gn desc` JSON."""
    result = {}

    rel_out = "//" + os.path.relpath(
        out_dir, os.path.join(chromium_root, "src")
    )
    rename_out = rename_out if rename_out.endswith("/") else rename_out + "/"

    def fix_source_file(f):
        if not f.startswith(rel_out):
            return f
        return rename_out + f[len(rel_out) + 1 :]

    for target, info in gn_desc.items():
        sources = info.get("sources")
        configs = info.get("configs")
        if not sources or not configs:
            continue

        result[target] = {
            "configs": configs,
            "sources": [fix_source_file(f) for f in sources],
        }

    return result


def main(args):
    known_arches = [
        "arm",
        "arm64",
        "x64",
        "x86",
    ]

    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "arch",
        nargs="+",
        help="Architecture(s) to fetch `gn desc`s for. "
        "Supported ones are %s" % known_arches,
    )
    parser.add_argument(
        "--output", required=True, help="File to write results to."
    )
    parser.add_argument(
        "--chromium_out_dir",
        required=True,
        help="Chromium out/ directory for us to use. This directory will "
        "be clobbered by this script.",
    )
    parser.add_argument(
        "--rename_out",
        default="//out",
        help="Directory to rename files in --chromium_out_dir to. "
        "Default: %(default)s",
    )
    opts = parser.parse_args(args)

    logging.basicConfig(
        format="%(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: %(message)s",
        level=logging.INFO,
    )

    arches = opts.arch
    rename_out = opts.rename_out
    for arch in arches:
        if arch not in known_arches:
            parser.error(
                "unknown architecture: %s; try one of %s" % (arch, known_arches)
            )

    results_file = os.path.realpath(opts.output)
    out_dir = os.path.realpath(opts.chromium_out_dir)
    chromium_root = _find_chromium_root(out_dir)

    os.makedirs(out_dir, exist_ok=True)
    results = {}
    for arch in arches:
        logging.info("Getting `gn` desc for %s...", arch)

        results[arch] = _fix_result(
            rename_out,
            out_dir,
            chromium_root,
            _run_gn_desc(
                in_dir=out_dir,
                gn_args=_create_gn_args_for(arch),
            ),
        )

    os.makedirs(os.path.dirname(results_file), exist_ok=True)

    results_intermed = results_file + ".tmp"
    with open(results_intermed, "w", encoding="utf-8") as f:
        json.dump(results, f)
    os.rename(results_intermed, results_file)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
