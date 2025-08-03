#!/bin/bash
# Generates Debian source and binary packages of modp_b64.

if [ -z "$1" ]; then
        echo "Usage: gen-src-pkg.sh <output-dir>"
        exit 1
fi

outdir="$1"
pkgdir=modp-b64-0.0.1
origtar=modp-b64_0.0.1.orig.tar.gz
scriptdir="$( cd "$( dirname "$0" )" && pwd )"
branch=release-R110-15278.B

tmpdir=$(mktemp -d)
echo Generating source package in "${tmpdir}".

# Download platform2 source.
cd "${tmpdir}"
git clone --branch "${branch}" https://chromium.googlesource.com/chromiumos/platform2 || exit 1
mkdir "${pkgdir}"
cd "${pkgdir}"
# Trim platform2, only common-mk is needed.
cp -a ../platform2/{common-mk,.gn} .

# Download modp_b64 source.
git clone --branch "${branch}" https://chromium.googlesource.com/aosp/platform/external/modp_b64 || exit 1
cd modp_b64
rm -rf .git

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
if [ ! -z "${MODP_DOCKER}" ]; then
  mv "${tmpdir}/${pkgdir}/debian/modp-b64.install.docker" \
     "${tmpdir}/${pkgdir}/debian/modp-b64.install"
else
  rm -f "${tmpdir}/${pkgdir}/debian/modp-b64.install.docker"
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
