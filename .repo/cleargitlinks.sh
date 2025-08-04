#!/bin/bash

# Remove .git directories, .git symbolic links, .gitattributes, .gitignore files and symbolic links in subdirectories
find . -mindepth 2 -type d -name ".git" -exec rm -rf {} \;
find . -mindepth 2 -type l -name ".git" -exec rm -f {} \;
find . -mindepth 2 -type f -name ".gitattributes" -exec rm -f {} \;
find . -mindepth 2 -type l -name ".gitattributes" -exec rm -f {} \;
find . -mindepth 2 -type f -name ".gitignore" -exec rm -f {} \;
find . -mindepth 2 -type l -name ".gitignore" -exec rm -f {} \;

# Explanation:
# - `find .` starts a search in the current directory.
# - `-mindepth 2` ensures the search skips the root of the current working directory.
# - `-type d` and `-type f` restrict the search to directories and regular files, respectively.
# - `-type l` is used to find symbolic links.
# - `-name` specifies the name of the files/directories to look for.
# - `-exec rm -rf {} \;` and `-exec rm -f {} \;` remove each found item.
