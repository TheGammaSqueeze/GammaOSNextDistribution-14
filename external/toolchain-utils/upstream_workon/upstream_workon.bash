#!/bin/bash -eu
#
# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

USAGE=\
'Usage: upstream-workon [-h]
                       init <PACKAGE> <DEV_WORK_DIR>
                       link <PACKAGE> <DEV_WORK_DIR>
                       build <PACKAGE>
                       install <PACKAGE>
                       clean <PACKAGE>
                       help'

set +e
read -r -d '' HELP <<'EOF'
Usage: upstream-workon [-h]
                       init <PACKAGE> <DEV_WORK_DIR>
                       link <PACKAGE> <DEV_WORK_DIR>
                       build <PACKAGE>
                       install <PACKAGE>
                       clean <PACKAGE>
                       help

Flags:
    -h  --help      Print this help message

Commands:
    init            Initialize in a developer workdir using a new tree
    link            Link an existing developer source dir to portage workdir
    build           Build the package using ebuild ... compile
    install         Install the package using ebuild ... install
    clean           Clean up your work without deleting the developer workdir
    help            Print this help message

Examples:

    # Start work
    mkdir "$HOME/llvm"
    upstream-workon init sys-devel/llvm "$HOME/llvm"

    # Link your existing work
    upstream-workon link sys-devel/llvm "$HOME/llvm"

    # Compile your work
    upstream-workon build sys-devel/llvm

    # Install your changes to the chroot
    upstream-workon install sys-devel/llvm

    # Clean up
    upstream-workon clean sys-devel/llvm

EOF
set -e

incorrect_number_of_arguments() {
    echo 'ERROR: Please use correct command syntax' >&2
    echo "${USAGE}" >&2
    exit 1
}

print_experimental_warning() {
    echo >&2
    echo '!!! WARNING: This tool is EXPERIMENTAL--please do not rely on the API.' >&2
    echo '!!! WARNING: Please recommend new features for Version 2, but this' >&2
    echo '!!! WARNING: implementation will not be actively developed and' >&2
    echo '!!! WARNING: exists only to receive feedback and minor fixes.' >&2
}

# ------------------------------------------------------------------------------
# Actual logic
# ------------------------------------------------------------------------------

# We probably can just pass through "USE", but I think this gives a bit more
# flexibility in the future.
USE_FLAGS="${USE:-}"

if [[ -n "${USE_FLAGS}" ]]; then
    echo 'USE flags set to:'
    echo "    ${USE_FLAGS}"
fi

init() {
    local package="$1"
    local desired_src_loc="$2"
    local ebuild_loc
    ebuild_loc="$(resolve_ebuild_for "${package}")"

    local ebuild_name
    ebuild_name="$(basename "${ebuild_loc}" | sed 's/\.ebuild$//g')"
    local package_name
    # SC2001 complains about not using variable replace syntax.
    # However, variable remove syntax is not sufficiently expansive
    # to do this replacement easily.
    # shellcheck disable=2001
    package_name="$(sed 's/-r[0-9]\+$//g' <<< "${ebuild_name}")"
    local ebuild_category
    ebuild_category="$(basename "$(dirname "$(dirname "${ebuild_loc}")")")"
    local portage_dir='/var/tmp/portage'

    local work_dir="${portage_dir}/${ebuild_category}/${ebuild_name}/work/${package_name}"

    ebuild "${ebuild_loc}" clean
    USE="${USE_FLAGS}" ebuild "${ebuild_loc}" unpack

    # May need to init git if it doesn't already exist.
    # Probably could just use git -C instead of the pushd/popd.
    pushd "${work_dir}" >& /dev/null
    if [[ ! -d '.git' ]]; then
        git init
        git add .
        git commit -m 'Initial commit'
    fi
    popd >& /dev/null

    USE="${USE_FLAGS}" ebuild "${ebuild_loc}" configure

    cp -r -p "${work_dir}/." "${desired_src_loc}"
    local backup_dir="${work_dir}.bk"
    mv "${work_dir}" "${backup_dir}"
    ln -s "$(realpath "${desired_src_loc}")" "${work_dir}"

    pushd "${desired_src_loc}" >& /dev/null

    git add .
    git commit -m 'Ebuild configure commit'
    popd >& /dev/null

    echo
    echo '----------------------------------------'
    echo 'Successfully created local mirror!'
    echo "Developer work directory set up at: ${desired_src_loc}"
    echo 'To build the package, run:'
    echo "    upstream-workon build ${package}"
    echo 'To install the package, run:'
    echo "    sudo upstream-workon install ${package}"
    echo "To clean up (without deleting ${desired_src_loc}), run:"
    echo "    upstream-workon clean ${package}"
    echo "WARNING: Moving original workdir to ${backup_dir}, consider deleting" >&2
}

clean() {
    local package="$1"
    echo 'WARNING: You may need to run this with sudo' >&2
    local ebuild_loc
    ebuild_loc="$(resolve_ebuild_for "${package}")"

    ebuild "${ebuild_loc}" clean

    echo '----------------------------------------'
    echo "Successfully cleaned up ${package}!"
}


compile() {
    local package="$1"
    local ebuild_loc
    ebuild_loc="$(resolve_ebuild_for "${package}")"

    USE="${USE_FLAGS}" ebuild "${ebuild_loc}" compile

    echo '----------------------------------------'
    echo "Successfully compiled ${package}!"
}


install_src() {
    local package="$1"
    echo 'WARNING: You may need to run this with sudo' >&2
    local ebuild_loc
    ebuild_loc="$(resolve_ebuild_for "${package}")"

    USE="${USE_FLAGS}" ebuild "${ebuild_loc}" install

    echo '----------------------------------------'
    echo "Successfully installed ${package}!"
}

link_src() {
    local package="$1"
    local desired_src_loc="$2"
    local ebuild_loc
    ebuild_loc="$(resolve_ebuild_for "${package}")"

    local ebuild_name
    ebuild_name="$(basename "${ebuild_loc}" | sed 's/\.ebuild$//g')"
    local package_name
    # shellcheck disable=2001
    package_name="$(sed 's/-r[0-9]\+$//g' <<< "${ebuild_name}")"
    local ebuild_category
    ebuild_category="$(basename "$(dirname "$(dirname "${ebuild_loc}")")")"
    local portage_dir='/var/tmp/portage'

    local work_dir="${portage_dir}/${ebuild_category}/${ebuild_name}/work/${package_name}"

    local backup_dir="${work_dir}.bk"

    # Because of some annoying permissions issues, we have to configure directly in
    # /var/tmp/portage/...
    # We then copy over those changes into our local source directory.
    # To make sure the proper deletions get done, we delete everything except
    # your local git directory.

    ebuild "${ebuild_loc}" clean
    USE="${USE_FLAGS}" ebuild "${ebuild_loc}" configure
    # TODO(ajordanr): This is a rough edge, and I don't want users to delete their
    # home directory without knowing what they are doing. So we're copying
    # everything instead.
    # TODO(ajordanr): This will ignore git submodules, which I don't want.
    mv "${desired_src_loc}" "${desired_src_loc}.bk"
    mkdir "${desired_src_loc}"
    cp -rP "${desired_src_loc}.bk/.git" "${desired_src_loc}/.git"
    rsync -a --exclude=".git" "${work_dir}"/* "${desired_src_loc}"
    rsync -a --exclude=".git" "${work_dir}"/.[^.]* "${desired_src_loc}"
    mv "${work_dir}" "${backup_dir}"
    ln -s "$(realpath "${desired_src_loc}")" "${work_dir}"

    echo '----------------------------------------'
    echo 'Successfully linked to local mirror!'
    echo "Developer work directory linked to: ${desired_src_loc}"
    echo "WARNING: Moving original workdir to ${backup_dir}, consider deleting" >&2
    echo "WARNING: Moving original dev dir to ${desired_src_loc}.bk, consider deleting" >&2
}

resolve_ebuild_for() {
    equery w "$1"
}

CMD="${1:-}"

case "${CMD}" in
    -h|--help|help)
        shift
        echo "${HELP}"
        print_experimental_warning
        exit 1
        ;;
    init)
        shift
        [[ -z "${1:-}" || -z "${2:-}" ]] && incorrect_number_of_arguments
        print_experimental_warning
        init "$1" "$2"
        ;;
    link)
        shift
        [[ -z "${1:-}" || -z "${2:-}" ]] && incorrect_number_of_arguments
        print_experimental_warning
        link_src "$1" "$2"
        ;;
    build)
        shift
        [[ -z "${1:-}" ]] && incorrect_number_of_arguments
        print_experimental_warning
        compile "$1"
        ;;
    clean)
        shift
        [[ -z "${1:-}" ]] && incorrect_number_of_arguments
        print_experimental_warning
        clean "$1"
        ;;
    install)
        shift
        [[ -z "${1:-}" ]] && incorrect_number_of_arguments
        print_experimental_warning
        install_src "$1"
        ;;
    *)
        incorrect_number_of_arguments
        ;;
esac
