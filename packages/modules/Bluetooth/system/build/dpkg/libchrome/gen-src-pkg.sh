#!/bin/bash
# Generates Debian source and binary packages of libchrome.

if [ -z "$1" ]; then
        echo "Usage: gen-src-pkg.sh <output-dir>"
        exit 1
fi

outdir="$1"
pkgdir=libchrome-1094370
origtar=libchrome_1094370.orig.tar.gz
scriptdir="$( cd "$( dirname "$0" )" && pwd )"

# Pin the libchrome branch + commit
libchrome_branch=main
libchrome_commit=0519670b5b553bdb42e22d05448358a312c5e78e

# Pin the platform2 branch + commit
platform2_branch=main
platform2_commit=a50a38e57053510332e3fe2ba116c0a7952ad511

tmpdir=$(mktemp -d)
echo Generating source package in "${tmpdir}".

# Download platform2 source.
cd "${tmpdir}"
git clone --branch "${platform2_branch}" https://chromium.googlesource.com/chromiumos/platform2 || exit 1
(cd platform2 && git checkout "${platform2_commit}")
mkdir "${pkgdir}"
cd "${pkgdir}"
# Trim platform2, only common-mk is needed.
cp -a ../platform2/{common-mk,.gn} .

# Download libchrome source and apply Chrome OS's patches.
git clone --branch "${libchrome_branch}" https://chromium.googlesource.com/chromiumos/platform/libchrome || exit 1
cd libchrome
git checkout "${libchrome_commit}"
rm -rf .git

# Apply all patches (even conditional ones).
# If this is problematic on a future revision, we may need to parse
# "libchrome_tools/patches/patches.config" and exclude ones found
# there.
for patch in $(ls "libchrome_tools/patches/" | grep .patch); do
  patch -p1 < "libchrome_tools/patches/${patch}"
done

# Clean up temporary platform2 checkout.
cd ../..
rm -rf platform2

# Debian requires creating .orig.tar.gz.
tar czf "${origtar}" "${pkgdir}"

# Debianize the source.
cd "${pkgdir}"
yes | debmake || exit 1
cp -aT "${scriptdir}/debian/" "${tmpdir}/${pkgdir}/debian/"

# If building for docker, use the right install script.
if [ ! -z "${LIBCHROME_DOCKER}" ]; then
  mv "${tmpdir}/${pkgdir}/debian/libchrome.install.docker" \
     "${tmpdir}/${pkgdir}/debian/libchrome.install"
else
  rm -f "${tmpdir}/${pkgdir}/debian/libchrome.install.docker"
fi

# Build source package and binary package.
cd "${tmpdir}/${pkgdir}"
dpkg-buildpackage --no-sign || exit 1

# Copy the results to output dir.
cd "${tmpdir}"
mkdir -p "${outdir}/src"
cp *.dsc *.orig.tar.gz *.debian.tar.xz "${outdir}/src"
cp *.deb "${outdir}"
cd /

echo Removing temporary directory "${tmpdir}".
rm -rf "${tmpdir}"

echo Done. Check out Debian source package in "${outdir}".
