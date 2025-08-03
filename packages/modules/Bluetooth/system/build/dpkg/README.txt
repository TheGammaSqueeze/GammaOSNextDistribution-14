This directory contains scripts to build Debian packages as dependencies for
building Fluoride on Linux.

To generate the Debian packages, you need:

* Debian 10 (Buster)
* gn, get binary from https://chrome-infra-packages.appspot.com/dl/gn/gn/linux-amd64/+/latest
* apt-get install git debmake
* Other build dependencies are package dependent, you will get error message
  mentioning the list of missing packages

Steps to build debian packages (modp_b64 first):
```
$ cd build/dpkg/modp_b64
$ ./gen-src-pkg /tmp/modp_b64
```

If the above succeeded, there will be a .deb file in /tmp/modp_b64. You can
install this binary package locally like:
```
$ sudo dpkg -i /tmp/modp_b64/modp-b64_0.0.1-1_amd64.deb
```

After installing modp_b64, you can do the same steps with libchrome in
build/dpkg/libchrome.
