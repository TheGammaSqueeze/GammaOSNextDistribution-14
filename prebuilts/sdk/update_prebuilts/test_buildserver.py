#!/usr/bin/python3

import buildserver


def test_parse_build_id():
    build_id_presubmit = buildserver.parse_build_id("12345")
    assert build_id_presubmit.url_id == "12345"
    assert build_id_presubmit.fs_id == "12345"


def test_parse_build_id_presubmit():
    build_id_presubmit = buildserver.parse_build_id("P12345")
    assert build_id_presubmit.url_id == "P12345"
    assert build_id_presubmit.fs_id == "0"


def test_parse_build_id_invalid():
    build_id_presubmit = buildserver.parse_build_id("PABCDE")
    assert build_id_presubmit is None


if __name__ == "__main__":
    test_parse_build_id()
    test_parse_build_id_presubmit()
    test_parse_build_id_invalid()
