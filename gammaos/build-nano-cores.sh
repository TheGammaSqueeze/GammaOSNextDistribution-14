#!/bin/bash
# Builds the GammaOS Nano programs before the image build (build.sh, buildtv.sh and
# buildtv_cc.sh run this). None of them is compiled by Soong: each lives in its own
# repository, a git submodule here, and has its own build script that works with the Android
# NDK alone and installs into the repository's prebuilt/ folder, which the image then ships.
#
#   frameworks/base/cmds/gammaos-nano     build.sh         the home and its shader translator
#   gammaos/emulators/drastic-nano        build.sh         the DraStic frontend
#   gammaos/emulators/azahar-nano         build.sh         the Azahar core and its frontend
#   gammaos/emulators/melonds-nano        build-core.sh    melonDS with its frontend
#
# retroarch-nano is not rebuilt here (its build wants a different NDK release): the image
# ships the binary committed in its prebuilt/ folder.
#
# A submodule that is not checked out is skipped and left out of the image
# (device/phh/treble/gammaos-nano.mk), so a tree without them still builds. The NDK, CMake
# and Ninja are fetched once into out/<program>/ when the host has none.
#
# Signed records: each build script renews prebuilt/module.record when a file it built changed
# (the image installs the records, see device/phh/treble/gammaos-nano.mk). That needs the
# module signing key: the file MODULE_SIGNING_KEY names, else ~/.gammaos/module_signing_key.pem.
# Without the key the programs still build and the image is made without records.
#
# Commits: a program that came out different from what its repository has checked in is
# committed there (prebuilt/ only, with its renewed record), and the tree's pointer to that
# repository is committed too, so the tree commit an image is built at always names the
# programs that are in it. Nothing is pushed. A repository is left alone, with a note, when
# its record could not be signed (no key) or when it has other uncommitted changes: a program
# built from sources that are not committed is for you to commit together with them.
#
# Environment:
#   GAMMAOS_NANO_SKIP_CORES=1                        skip all of them, keep the prebuilts
#   GAMMAOS_NANO_SKIP_BUILD=1, DRASTIC_NANO_SKIP_BUILD=1,
#   AZAHAR_NANO_SKIP_BUILD=1, MELONDS_NANO_SKIP_BUILD=1    skip one
#   GAMMAOS_NANO_NO_COMMIT=1                         leave rebuilt programs uncommitted (below)
#   GAMMAOS_NANO_JOBS, DRASTIC_NANO_JOBS, AZAHAR_NANO_JOBS, MELONDS_NANO_JOBS    parallel jobs
#                                                    (default: one per CPU core, as the
#                                                    image build itself uses)

set -e
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ "${GAMMAOS_NANO_SKIP_CORES:-0}" = "1" ]; then
    echo "GammaOS Nano: GAMMAOS_NANO_SKIP_CORES=1, keeping the prebuilt programs"
    exit 0
fi

# An emulator only goes into an image together with the home (gammaos-nano.mk), so without the
# home there is nothing to build: the image is made without nano mode and boots as plain Android.
if [ ! -f frameworks/base/cmds/gammaos-nano/build.sh ]; then
    echo "GammaOS Nano: frameworks/base/cmds/gammaos-nano is not checked out, building the image without nano mode"
    exit 0
fi

# One job per core, not per thread, unless the caller says otherwise.
CORES=$(lscpu -b -p=Core,Socket 2>/dev/null | grep -v '^#' | sort -u | wc -l)
[ "${CORES:-0}" -gt 0 ] || CORES=$(nproc)
export GAMMAOS_NANO_JOBS="${GAMMAOS_NANO_JOBS:-$CORES}" DRASTIC_NANO_JOBS="${DRASTIC_NANO_JOBS:-$CORES}" \
       AZAHAR_NANO_JOBS="${AZAHAR_NANO_JOBS:-$CORES}" MELONDS_NANO_JOBS="${MELONDS_NANO_JOBS:-$CORES}"

for script in frameworks/base/cmds/gammaos-nano/build.sh \
              gammaos/emulators/drastic-nano/build.sh \
              gammaos/emulators/azahar-nano/build.sh \
              gammaos/emulators/melonds-nano/build-core.sh; do
    if [ -f "$script" ]; then
        bash "$script"
    else
        echo "GammaOS Nano: $(dirname "$script") is not checked out, building the image without it"
    fi
done

[ "${GAMMAOS_NANO_NO_COMMIT:-0}" = "1" ] && exit 0

# Commit what came out different (see "Commits" above).
COMMITTED=()
for repo in frameworks/base/cmds/gammaos-nano gammaos/emulators/drastic-nano \
            gammaos/emulators/azahar-nano gammaos/emulators/melonds-nano; do
    name=$(basename "$repo")
    [ -e "$repo/.git" ] || continue
    [ -n "$(git -C "$repo" status --porcelain --untracked-files=no -- prebuilt)" ] || continue
    # (the home's copy of the tool, the one gammaos-nano.mk checks the records with)
    if ! python3 frameworks/base/cmds/gammaos-nano/common/tools/module-pack.py check --repo "$repo" >/dev/null 2>&1; then
        echo "GammaOS Nano: $name was rebuilt but has no signed record of it, left uncommitted"
        continue
    fi
    if [ -n "$(git -C "$repo" status --porcelain --untracked-files=no --ignore-submodules=untracked -- . ':(exclude)prebuilt')" ]; then
        echo "GammaOS Nano: $name was rebuilt from sources that are not committed, its prebuilt/ is left for you to commit with them"
        continue
    fi
    if git -C "$repo" add -u -- prebuilt \
        && git -C "$repo" commit -q -m "$name: prebuilt rebuilt by the image build" -- prebuilt; then
        echo "GammaOS Nano: $name: committed the rebuilt prebuilt/ ($(git -C "$repo" rev-parse --short HEAD), not pushed)"
        COMMITTED+=("$repo")
    else
        echo "GammaOS Nano: $name: the rebuilt prebuilt/ could not be committed, left as it is"
    fi
done
if [ "${#COMMITTED[@]}" -gt 0 ] && [ -e .git ]; then
    names=$(printf '%s, ' "${COMMITTED[@]##*/}"); names=${names%, }
    if git add -- "${COMMITTED[@]}" \
        && git commit -q -m "GammaOS Nano: $names rebuilt by the image build" -- "${COMMITTED[@]}"; then
        echo "GammaOS Nano: the tree now names them ($(git rev-parse --short HEAD), not pushed)"
    else
        echo "GammaOS Nano: the tree's pointers to $names could not be committed"
    fi
fi
