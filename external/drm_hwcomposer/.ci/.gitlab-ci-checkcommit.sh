#! /usr/bin/env bash

check_tool_installed() {
	if ! command -v $1 &> /dev/null
	then
		echo "Please install '$1' tool"
		exit 1
	fi
}

echoerr() {
	printf "ERROR: %s\n" "$*" >&2
}

findtag() {
	local commit_body tag person
	commit_body=$1
	tag=$2
	person=$3

	# trim duplicate spaces from commit body and person
	match="$tag: $(echo $person | tr -s ' ')"

	if [ -z "$(echo "$commit_body" | tr -s ' ' | grep -i "$match")" ]; then
		echoerr "Tag is missing from commit body"
		echoerr ""
		echoerr "Looking for '"$match"' in: "
		echoerr "-----------------------------------------------------"
		echoerr "$commit_body"
		echoerr "-----------------------------------------------------"
		echoerr ""
		return 0
	fi

	return 1
}

check_tool_installed bpfmt
check_tool_installed clang-format-diff-15

git fetch https://gitlab.freedesktop.org/drm-hwcomposer/drm-hwcomposer.git

git log --pretty='%h' FETCH_HEAD..HEAD | while read h; do
	subject=$(git show -s --pretty='%s' "$h")
	if [[ $subject != drm_hwcomposer:* ]]; then
		echoerr "Invalid subject prefix: $subject"
		exit 1
	fi

	commit_body=$(git show -s --pretty=%b "$h")

	author=$(git show -s --format='%an <%ae>' "$h")
	if findtag "$commit_body" "Signed-off-by" "$author"; then
		echoerr "Author SoB tag is missing from commit $h"
		exit 1
	fi

	committer=$(git show -s --format='%cn <%ce>' "$h")
	if findtag "$commit_body" "Signed-off-by" "$committer"; then
		echoerr "Committer SoB tag is missing from commit $h"
		exit 1
	fi

	git show "$h" -- | clang-format-diff-15 -p 1 -style=file > /tmp/format-fixup.patch
	if [ -s  /tmp/format-fixup.patch ]; then
		cat /tmp/format-fixup.patch >&2
		exit 1
	fi

	find -name "*.bp" -exec bpfmt -d -s {} \; > /tmp/bpfmt.patch
	if [ -s  /tmp/bpfmt.patch ]; then
		cat /tmp/bpfmt.patch >&2
		exit 1
	fi
done
