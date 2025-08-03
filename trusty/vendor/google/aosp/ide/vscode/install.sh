#!/bin/bash
set -euo pipefail

function find_workspace_root() {
    local original_wd=$(pwd)

    while true; do
        if [ $(pwd) = "/" ]; then
            break
        fi

        if [ -d ".vscode" ]; then
            pwd
            break
        fi

        cd ..
    done

    cd "$original_wd"
}

SCRIPT_PATH=$(realpath "$0")
SCRIPT_FOLDER=$(dirname "$SCRIPT_PATH")

WORKSPACE_ROOT=$(find_workspace_root)
if [ -z "$WORKSPACE_ROOT" ]; then
    echo "Could not find .vscode/ folder. Please create one in the root of your"
    echo "workspace and re-run this script."
    exit 1
fi

VSCODE_FOLDER="$WORKSPACE_ROOT/.vscode"
REPO_FOLDER="$WORKSPACE_ROOT/.repo"
if ! [ -d "$REPO_FOLDER" ]; then
    echo ".repo/ folder is not in the same location as .vscode/ - this will"
    echo "cause problems."
    echo "Exiting..."
    exit 1
fi

if ! command -v code > /dev/null; then
  echo "Could not find 'code' command."
  echo ""
  echo "1. Open VSCode"
  echo "2. Open the command palette (View > Command Palette)"
  echo "3. Type 'install' and select 'Shell Command: Install code command in PATH'"
  echo "4. Restart your shell"
  exit 1
fi

echo "Installing LLDB VSCode extension"
code --install-extension 'vadimcn.vscode-lldb@1.7.3'

function files_equal() {
    local file_a="$1"
    local file_b="$2"

    if ! [ -f "$file_a" ]; then
        false
        return
    fi

    if ! [ -f "$file_b" ]; then
        false
        return
    fi

    local md5_a=$(md5sum $file_a | cut -d' ' -f1)
    local md5_b=$(md5sum $file_b | cut -d' ' -f1)

    [[ "$md5_a" == "$md5_b" ]]
}

function copy_file() {
    local file="$1"
    local source="$SCRIPT_FOLDER/$file"
    local dest="$VSCODE_FOLDER/$file"

    if files_equal "$source" "$dest"; then
        echo "Skipping copying of $file"
        return
    fi

    if [ -f "$dest" ]; then
        echo "Backing up previous $file to $file.old"
        mv "$dest" "$dest.old"
    fi

    echo "Copying $file"
    cp "$source" "$dest"
}

copy_file 'tasks.json'
copy_file 'launch.json'

echo "Done!"
