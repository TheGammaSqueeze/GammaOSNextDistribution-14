# Debugging the payload on microdroid

Like a normal adb device, you can debug native processes running on a
Microdroid-base VM using [`lldbclient.py`][lldbclient] script, either by
running a new process, or attaching to an existing process.  Use `vm_shell`
tool above, and then run `lldbclient.py`.

```sh
adb -s localhost:8000 shell 'mount -o remount,exec /data'
development/scripts/lldbclient.py -s localhost:8000 --chroot . --user '' \
    (-p PID | -n NAME | -r ...)
```

**Note:** We need to pass `--chroot .` to skip verifying device, because
microdroid doesn't match with the host's lunch target. We need to also pass
`--user ''` as there is no `su` binary in microdroid.

[lldbclient]: https://android.googlesource.com/platform/development/+/refs/heads/main/scripts/lldbclient.py
