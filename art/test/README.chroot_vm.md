# ART chroot-based testing on a Linux VM

This doc describes how to set up a Linux VM and how to run ART tests on it.

## Set up the VM

Use script art/build/buildbot-vm.sh. It has various commands (actions) described
below. First, set up some environment variables used by the script (change as
you see fit):
```
export ART_TEST_SSH_USER=ubuntu
export ART_TEST_SSH_HOST=localhost
export ART_TEST_SSH_PORT=10001
```
Create the VM (download it and do some initial setup):
```
art/tools/buildbot-vm.sh create
```
Boot the VM (login is `$ART_TEST_SSH_USER`, password is `ubuntu`):
```
art/tools/buildbot-vm.sh boot
```
Configure SSH (enter `yes` to add VM to `known_hosts` and then the password):
```
art/tools/buildbot-vm.sh setup-ssh
```
Now you have the shell (no need to enter password every time):
```
art/tools/buildbot-vm.sh connect
```
To power off the VM, do:
```
art/tools/buildbot-vm.sh quit
```
To speed up SSH access, set `UseDNS no` in /etc/ssh/sshd_config on the VM (and
apply other tweaks described in https://jrs-s.net/2017/07/01/slow-ssh-logins).

# Run ART tests
```
This is done in the same way as you would run tests in chroot on device (except
for a few extra environment variables):

export ANDROID_SERIAL=nonexistent
export ART_TEST_SSH_USER=ubuntu
export ART_TEST_SSH_HOST=localhost
export ART_TEST_SSH_PORT=10001
export ART_TEST_ON_VM=true

. ./build/envsetup.sh
lunch armv8-trunk_staging-eng  # or aosp_riscv64-trunk_staging-userdebug, etc.
art/tools/buildbot-build.sh --target # --installclean

art/tools/buildbot-cleanup-device.sh

# The following two steps can be skipped for faster iteration, but it doesn't
# always track and update dependencies correctly (e.g. if only an assembly file
# has been modified).
art/tools/buildbot-setup-device.sh
art/tools/buildbot-sync.sh

art/test/run-test --chroot $ART_TEST_CHROOT --64 --interpreter -O 001-HelloWorld
art/test.py --target -r --ndebug --no-image --64 --interpreter  # specify tests
art/tools/run-gtests.sh

art/tools/buildbot-cleanup-device.sh
```
Both test.py and run-test scripts can be used. Tweak options as necessary.

# Limitations

Limitations are mostly related to the absence of system properties on the Linux.
They are not really needed for ART tests, but they are used for test-related
things, e.g. to find out if the tests should run in debug configuration (option
`ro.debuggable`). Therefore debug configuration is currently broken.
