#!/usr/bin/python3

import glob
import os

from shutil import rmtree, move, copy, copytree
from sys import stderr


def append(text, more_text):
    if text:
        return f'{text}, {more_text}'
    return more_text


def print_e(*args, **kwargs):
    print(*args, file=stderr, **kwargs)


def touch(filename, times=None):
    with open(filename, 'a'):
        os.utime(filename, times)


def rm(path):
    """Removes the file or directory tree at the specified path, if it exists.

    Args:
        path: Path to remove
    """
    if os.path.isdir(path):
        rmtree(path)
    elif os.path.exists(path):
        os.remove(path)


def mv(src_path, dst_path):
    """Moves the file or directory tree at the source path to the destination path.

    This method does not merge directory contents. If the destination is a directory that already
    exists, it will be removed and replaced by the source. If the destination is rooted at a path
    that does not exist, it will be created.

    Args:
        src_path: Source path
        dst_path: Destination path
    """
    if os.path.exists(dst_path):
        rm(dst_path)
    if not os.path.exists(os.path.dirname(dst_path)):
        os.makedirs(os.path.dirname(dst_path))
    for f in (glob.glob(src_path)):
        if '*' in dst_path:
            dst = os.path.join(os.path.dirname(dst_path), os.path.basename(f))
        else:
            dst = dst_path
        move(f, dst)


def cp(src_path, dst_path):
    """Copies the file or directory tree at the source path to the destination path.

    This method does not merge directory contents. If the destination is a directory that already
    exists, it will be removed and replaced by the source. If the destination is rooted at a path
    that does not exist, it will be created.

    Note that the implementation of this method differs from mv, in that it does not handle "*" in
    the destination path.

    Args:
        src_path: Source path
        dst_path: Destination path
    """
    if os.path.exists(dst_path):
        rm(dst_path)
    if not os.path.exists(os.path.dirname(dst_path)):
        os.makedirs(os.path.dirname(dst_path))
    for f in (glob.glob(src_path)):
        if os.path.isdir(f):
            copytree(f, dst_path)
        else:
            copy(f, dst_path)
