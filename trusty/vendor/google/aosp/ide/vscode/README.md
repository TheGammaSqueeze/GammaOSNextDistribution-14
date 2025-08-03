# Trusty VSCode Support

## Quickstart

Once you have VSCode installed, open the Trusty code base in VSCode and create a
`.vscode` folder in the root (it may already exist if you've adjusted any
settings). This folder is needed to tell the install script where the VSCode
workspace begins.

Next run the install script:

```shell
$ ./trusty/vendor/google/aosp/ide/vscode/install.sh
```

This will configure VSCode to understand how to start up Trusty in the emulator
and attach the debugger.

## Building

The configuration files that are provided do not build Trusty for you. You'll
need to do that on the command line:

```shell
$ ./trusty/vendor/google/aosp/scripts/build.py --skip-tests qemu-generic-arm64-test-debug
```

## Debugging

Before starting, go ahead and set your breakpoints by clicking to the left of
the line number in a file. You can do this for kernel code as well as TA code.
Because our integration with LLDB is hacky, the debugger will lose track of a
process after syscalls are made. You'll need to add breakpoints after each
syscall to step past them.

As long as you've built the `qemu-generic-arm64-test-debug` Trusty project
you can switch to the Run/Debug view (View > Run) and click the button that says
`Debug with Prebuilt`.

Now you're debugging like a pro.
