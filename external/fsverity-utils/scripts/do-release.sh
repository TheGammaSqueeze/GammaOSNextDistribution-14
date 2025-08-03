#!/bin/bash
# SPDX-License-Identifier: MIT
# Copyright 2020 Google LLC
#
# Use of this source code is governed by an MIT-style
# license that can be found in the LICENSE file or at
# https://opensource.org/licenses/MIT.

set -e -u -o pipefail
cd "$(dirname "$0")/.."

usage()
{
	echo "Usage: $0 prepare|publish VERS" 1>&2
	echo "  e.g. $0 prepare 1.0" 1>&2
	echo "       $0 publish 1.0" 1>&2
	exit 2
}

if [ $# != 2 ]; then
	usage
fi

PUBLISH=false
case $1 in
publish)
	PUBLISH=true
	;;
prepare)
	;;
*)
	usage
	;;
esac
VERS=$2
PKG=fsverity-utils-$VERS

prepare_release()
{
	git checkout -f
	git clean -fdx
	./scripts/run-tests.sh
	git clean -fdx

	major=$(echo "$VERS" | cut -d. -f1)
	minor=$(echo "$VERS" | cut -d. -f2)
	month=$(LC_ALL=C date +%B)
	year=$(LC_ALL=C date +%Y)

	sed -E -i -e "/FSVERITY_UTILS_MAJOR_VERSION/s/[0-9]+/$major/" \
		  -e "/FSVERITY_UTILS_MINOR_VERSION/s/[0-9]+/$minor/" \
		  include/libfsverity.h
	sed -E -i "/Version:/s/[0-9]+\.[0-9]+/$VERS/" \
		  lib/libfsverity.pc.in
	sed -E -i -e "/^% /s/fsverity-utils v[0-9]+(\.[0-9]+)+/fsverity-utils v$VERS/" \
		  -e "/^% /s/[a-zA-Z]+ 2[0-9]{3}/$month $year/" \
		  man/*.[1-9].md
	git commit -a --signoff --message="v$VERS"
	git tag --sign "v$VERS" --message="$PKG"

	git archive "v$VERS" --prefix="$PKG/" > "$PKG.tar"
	tar xf "$PKG.tar"
	( cd "$PKG" && make check )
	rm -r "$PKG"
}

publish_release()
{
	gpg --detach-sign --armor "$PKG.tar"
	DESTDIR=/pub/linux/kernel/people/ebiggers/fsverity-utils/v$VERS
	kup mkdir "$DESTDIR"
	kup put "$PKG.tar" "$PKG.tar.asc" "$DESTDIR/$PKG.tar.gz"
	git push
	git push --tags
}

if $PUBLISH; then
	publish_release
else
	prepare_release
fi
