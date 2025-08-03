#!/bin/bash
# SPDX-License-Identifier: MIT
# Copyright 2020 Google LLC
#
# Use of this source code is governed by an MIT-style
# license that can be found in the LICENSE file or at
# https://opensource.org/licenses/MIT.
#
#
# Test script for fsverity-utils.  Runs 'make check' in lots of configurations,
# runs static analysis, and does a few other tests.
#
# Note: for more test coverage, in addition to running this script, also build
# fsverity-utils into a kvm-xfstests test appliance and run
# 'kvm-xfstests -c ext4,f2fs -g verity'

set -e -u -o pipefail
cd "$(dirname "$0")/.."

log()
{
	echo "[$(date)] $*" 1>&2
}

fail()
{
	echo "FAIL: $*" 1>&2
	exit 1
}

TMPDIR=$(mktemp -d -t libfsverity_test.XXXXXXXXX)
trap 'rm -r "$TMPDIR"' EXIT

# Both stdout and stderr go to log file.
# Only stderr goes to terminal.
echo "Starting fsverity-utils tests.  See run-tests.log for full output."
rm -f run-tests.log
exec >> run-tests.log
exec 2> >(tee -ia run-tests.log 1>&2)

MAKE="make -j$(getconf _NPROCESSORS_ONLN)"

TEST_FUNCS=()

static_linking_test()
{
	log "Build and test with statically linking"
	$MAKE CFLAGS="-Werror"
	if ldd fsverity | grep libfsverity.so; then
		fail "fsverity binary should be statically linked to libfsverity by default"
	fi
	./fsverity --version

	log "Check that all global symbols are prefixed with \"libfsverity_\""
	if nm libfsverity.a | grep ' T ' | grep -v " libfsverity_"; then
		fail "Some global symbols are not prefixed with \"libfsverity_\""
	fi
}
TEST_FUNCS+=(static_linking_test)

dynamic_linking_test()
{
	log "Build and test with dynamic linking"
	$MAKE CFLAGS="-Werror" USE_SHARED_LIB=1 check
	if ! ldd fsverity | grep libfsverity.so; then
		fail "fsverity binary should be dynamically linked to libfsverity when USE_SHARED_LIB=1"
	fi

	log "Check that all exported symbols are prefixed with \"libfsverity_\""
	if nm libfsverity.so | grep ' T ' | grep -v " libfsverity_"; then
		fail "Some exported symbols are not prefixed with \"libfsverity_\""
	fi
}
TEST_FUNCS+=(dynamic_linking_test)

cplusplus_test()
{
	$MAKE CFLAGS="-Werror" libfsverity.so
	log "Test using libfsverity from C++ program"
	cat > "$TMPDIR/test.cc" <<EOF
#include <libfsverity.h>
#include <iostream>
int main()
{
	std::cout << libfsverity_get_digest_size(FS_VERITY_HASH_ALG_SHA256) << std::endl;
}
EOF
	c++ -Wall -Werror "$TMPDIR/test.cc" -Iinclude -L. -lfsverity -o "$TMPDIR/test"
	[ "$(LD_LIBRARY_PATH=. "$TMPDIR/test")" = "32" ]
	rm "${TMPDIR:?}"/*
}
TEST_FUNCS+=(cplusplus_test)

untracked_files_test()
{
	log "Check that build doesn't produce untracked files"
	$MAKE CFLAGS="-Werror" all test_programs
	if git status --short | grep -q '^??'; then
		git status
		fail "Build produced untracked files (check 'git status').  Missing gitignore entry?"
	fi
}
TEST_FUNCS+=(untracked_files_test)

uninstall_test()
{
	log "Test that 'make uninstall' uninstalls all files"
	make DESTDIR="$TMPDIR" install
	if [ "$(find "$TMPDIR" -type f -o -type l | wc -l)" = 0 ]; then
		fail "'make install' didn't install any files"
	fi
	make DESTDIR="$TMPDIR" uninstall
	if [ "$(find "$TMPDIR" -type f -o -type l | wc -l)" != 0 ]; then
		fail "'make uninstall' didn't uninstall all files"
	fi
	rm -r "${TMPDIR:?}"/*
}
TEST_FUNCS+=(uninstall_test)

dash_test()
{
	log "Build, install, and uninstall with dash"
	make clean SHELL=/bin/dash
	make DESTDIR="$TMPDIR" SHELL=/bin/dash install
	make DESTDIR="$TMPDIR" SHELL=/bin/dash uninstall
}
TEST_FUNCS+=(dash_test)

license_test()
{
	log "Check that all files have license and copyright info"
	list="$TMPDIR/filelist"
	filter_license_info() {
		# files to exclude from license and copyright info checks
		grep -E -v '(\.gitignore|LICENSE|.*\.md|testdata|fsverity_uapi\.h|libfsverity\.pc\.in)'
	}
	git grep -L 'SPDX-License-Identifier: MIT' \
		| filter_license_info > "$list" || true
	if [ -s "$list" ]; then
		fail "The following files are missing an appropriate SPDX license identifier: $(<"$list")"
	fi
	# For now some people still prefer a free-form license statement, not just SPDX.
	git grep -L 'Use of this source code is governed by an MIT-style' \
		| filter_license_info > "$list" || true
	if [ -s "$list" ]; then
		fail "The following files are missing an appropriate license statement: $(<"$list")"
	fi
	git grep -L '\<Copyright\>' | filter_license_info > "$list" || true
	if [ -s "$list" ]; then
		fail "The following files are missing a copyright statement: $(<"$list")"
	fi
	rm "$list"
}
TEST_FUNCS+=(license_test)

gcc_test()
{
	log "Build and test with gcc (-O2)"
	$MAKE CC=gcc CFLAGS="-O2 -Werror" check

	log "Build and test with gcc (-O3)"
	$MAKE CC=gcc CFLAGS="-O3 -Werror" check
}
TEST_FUNCS+=(gcc_test)

clang_test()
{
	log "Build and test with clang (-O2)"
	$MAKE CC=clang CFLAGS="-O2 -Werror" check

	log "Build and test with clang (-O3)"
	$MAKE CC=clang CFLAGS="-O3 -Werror" check
}
TEST_FUNCS+=(clang_test)

32bit_test()
{
	log "Build and test with gcc (32-bit)"
	$MAKE CC=gcc CFLAGS="-O2 -Werror -m32" check
}
TEST_FUNCS+=(32bit_test)

sanitizers_test()
{
	log "Build and test with clang + UBSAN"
	$MAKE CC=clang \
		CFLAGS="-O2 -Werror -fsanitize=undefined -fno-sanitize-recover=undefined" \
		check

	log "Build and test with clang + ASAN"
	$MAKE CC=clang \
		CFLAGS="-O2 -Werror -fsanitize=address -fno-sanitize-recover=address" \
		check

	log "Build and test with clang + unsigned integer overflow sanitizer"
	$MAKE CC=clang \
		CFLAGS="-O2 -Werror -fsanitize=unsigned-integer-overflow -fno-sanitize-recover=unsigned-integer-overflow" \
		check

	log "Build and test with clang + CFI"
	$MAKE CC=clang CFLAGS="-O2 -Werror -fsanitize=cfi -flto -fvisibility=hidden" \
		AR=llvm-ar check
}
TEST_FUNCS+=(sanitizers_test)

valgrind_test()
{
	log "Build and test with valgrind"
	$MAKE TEST_WRAPPER_PROG="valgrind --quiet --error-exitcode=100 --leak-check=full --errors-for-leak-kinds=all" \
		CFLAGS="-O2 -Werror" check
}
TEST_FUNCS+=(valgrind_test)

boringssl_test()
{
	log "Build and test using BoringSSL instead of OpenSSL"
	log "-> Building BoringSSL"
	$MAKE boringssl
	log "-> Building fsverity-utils linked to BoringSSL"
	$MAKE CFLAGS="-O2 -Werror" LDFLAGS="-Lboringssl/build/crypto" \
		CPPFLAGS="-Iboringssl/include" LDLIBS="-lcrypto -lpthread" check
}
TEST_FUNCS+=(boringssl_test)

openssl1_test()
{
	log "Build and test using OpenSSL 1.0"
	$MAKE CFLAGS="-O2 -Werror" LDFLAGS="-L/usr/lib/openssl-1.0" \
		CPPFLAGS="-I/usr/include/openssl-1.0" check
}
TEST_FUNCS+=(openssl1_test)

openssl3_test()
{
	log "Build and test using OpenSSL 3.0"
	OSSL3=$HOME/src/openssl/inst/usr/local
	LD_LIBRARY_PATH="$OSSL3/lib64" $MAKE CFLAGS="-O2 -Werror" \
		LDFLAGS="-L$OSSL3/lib64" CPPFLAGS="-I$OSSL3/include" check
}
TEST_FUNCS+=(openssl3_test)

unsigned_char_test()
{
	log "Build and test using -funsigned-char"
	$MAKE CFLAGS="-O2 -Werror -funsigned-char" check
}
TEST_FUNCS+=(unsigned_char_test)

signed_char_test()
{
	log "Build and test using -fsigned-char"
	$MAKE CFLAGS="-O2 -Werror -fsigned-char" check
}
TEST_FUNCS+=(signed_char_test)

windows_build_test()
{
	log "Cross-compile for Windows (32-bit)"
	$MAKE CC=i686-w64-mingw32-gcc CFLAGS="-O2 -Werror"

	log "Cross-compile for Windows (64-bit)"
	$MAKE CC=x86_64-w64-mingw32-gcc CFLAGS="-O2 -Werror"
}
TEST_FUNCS+=(windows_build_test)

sparse_test()
{
	log "Run sparse"
	./scripts/run-sparse.sh
}
TEST_FUNCS+=(sparse_test)

clang_analyzer_test()
{
	log "Run clang static analyzer"
	scan-build --status-bugs make CFLAGS="-O2 -Werror" all test_programs
}
TEST_FUNCS+=(clang_analyzer_test)

shellcheck_test()
{
	log "Run shellcheck"
	shellcheck scripts/*.sh 1>&2
}
TEST_FUNCS+=(shellcheck_test)

test_exists()
{
	local tst=$1
	local func
	for func in "${TEST_FUNCS[@]}"; do
		if [ "${tst}_test" = "$func" ]; then
			return 0
		fi
	done
	return 1
}

if [[ $# == 0 ]]; then
	for func in "${TEST_FUNCS[@]}"; do
		eval "$func"
	done
else
	for tst; do
		if ! test_exists "$tst"; then
			echo 1>&2 "Unknown test: $tst"
			exit 2
		fi
	done
	for tst; do
		eval "${tst}_test"
	done
fi

log "All tests passed!"
