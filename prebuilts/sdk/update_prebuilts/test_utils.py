#!/usr/bin/python3

import os
import tempfile
import utils


def test_append():
    assert utils.append("x", "y") == "x, y"
    assert utils.append(None, "y") == "y"


def test_cp():
    with tempfile.TemporaryDirectory() as tmpdir:
        src_dir = os.path.join(tmpdir, "src")
        src_dir_b = os.path.join(src_dir, "b")
        dst_dir = os.path.join(tmpdir, "dst")
        dst_dir_a = os.path.join(dst_dir, "a")

        os.mkdir(src_dir)
        os.mkdir(src_dir_b)
        os.mkdir(dst_dir)
        os.mkdir(dst_dir_a)

        dst_dir_b = os.path.join(dst_dir, "b")

        utils.cp(src_dir, dst_dir)

        # Destination contents are not preserved.
        assert not os.path.exists(dst_dir_a)

        # Source tree is copied to destination.
        assert os.path.exists(dst_dir_b)


def test_cp_mk_dst():
    with tempfile.TemporaryDirectory() as tmpdir:
        src_dir = os.path.join(tmpdir, "src")
        src_dir_b = os.path.join(src_dir, "b")
        dst_dir = os.path.join(tmpdir, "dst")

        os.mkdir(src_dir)
        os.mkdir(src_dir_b)

        dst_dir_b = os.path.join(dst_dir, "b")

        utils.cp(src_dir, dst_dir)

        # Missing destination is created.
        assert os.path.exists(dst_dir)

        # Source tree is copied to destination.
        assert os.path.exists(dst_dir_b)


def test_mv():
    with tempfile.TemporaryDirectory() as tmpdir:
        src_dir = os.path.join(tmpdir, "src")
        src_dir_b = os.path.join(src_dir, "b")
        dst_dir = os.path.join(tmpdir, "dst")
        dst_dir_a = os.path.join(dst_dir, "a")

        os.mkdir(src_dir)
        os.mkdir(src_dir_b)
        os.mkdir(dst_dir)
        os.mkdir(dst_dir_a)

        dst_dir_b = os.path.join(dst_dir, "b")

        utils.mv(src_dir, dst_dir)

        # Destination contents are not preserved.
        assert not os.path.exists(dst_dir_a)

        # Source tree is copied to destination.
        assert os.path.exists(dst_dir_b)

        # Source tree is removed.
        assert not os.path.exists(src_dir)


def test_mv_mk_dst():
    with tempfile.TemporaryDirectory() as tmpdir:
        src_dir = os.path.join(tmpdir, "src")
        src_dir_b = os.path.join(src_dir, "b")
        dst_dir = os.path.join(tmpdir, "dst")

        os.mkdir(src_dir)
        os.mkdir(src_dir_b)

        dst_dir_b = os.path.join(dst_dir, "b")

        utils.mv(src_dir, dst_dir)

        # Missing destination is created.
        assert os.path.exists(dst_dir)

        # Source tree is copied to destination.
        assert os.path.exists(dst_dir_b)

        # Source tree is removed.
        assert not os.path.exists(src_dir)


def test_rm():
    with tempfile.TemporaryDirectory() as tmpdir:
        src_dir = os.path.join(tmpdir, "src")
        src_dir_b = os.path.join(src_dir, "b")

        os.mkdir(src_dir)
        os.mkdir(src_dir_b)

        utils.rm(src_dir)

        # Source tree is removed.
        assert not os.path.exists(src_dir)


if __name__ == "__main__":
    test_append()
    test_cp()
    test_cp_mk_dst()
    test_mv()
    test_rm()
