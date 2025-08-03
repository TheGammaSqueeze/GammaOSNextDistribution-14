#!/usr/bin/env python3

from signal import SIGINT
import argparse
import sys
import psutil  # type: ignore


def find_vscode_process(current_process):
    parent_process = current_process.parent()

    while parent_process.name() != "node":
        parent_process = parent_process.parent()

        if parent_process.pid == 1:
            return None

    return parent_process


def find_run_process(vscode_process, run_path):
    for child_process in vscode_process.children():
        args = child_process.cmdline()
        if len(args) < 2:
            continue

        if args[0] != "/bin/sh":
            continue

        if args[1] != run_path:
            continue

        return child_process

    return None


def main():
    parser = argparse.ArgumentParser(description="Kill QEMU from within VSCode")
    parser.add_argument("run_path", type=str, help="Path to Trusty run script")
    args = parser.parse_args()

    current_process = psutil.Process()

    vscode_process = find_vscode_process(current_process)
    if vscode_process is None:
        print("Could not find vscode's node process", file=sys.stderr)
        sys.exit(1)

    run_process = find_run_process(vscode_process, args.run_path)
    if run_process is None:
        print("Could not find the run process", file=sys.stderr)
        sys.exit(1)

    try:
        qemu_py_process = run_process.children()[0]
    except IndexError:
        print("Could not find the qemu.py process", file=sys.stderr)
        sys.exit(1)

    qemu_py_process.send_signal(SIGINT)


if __name__ == "__main__":
    main()
