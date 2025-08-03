## dexopt_chroot_setup

dexopt_chroot_setup is a component of ART Service. It sets up the chroot
environment for Pre-reboot Dexopt, to dexopt apps on a best-effort basis before
the reboot after a Mainline update or an OTA update is downloaded, to support
seamless updates.

It requires elevated permissions that are not available to system_server, such
as mounting filesystems. It publishes a binder interface that is internal to ART
Service's Java code.
