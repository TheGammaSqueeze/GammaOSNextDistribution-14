#!/bin/bash

# Enable verbose logging
set -x

find -name .git -prune -o -type d -empty -print  -exec touch {}/$placeholder \;

# Identify all untracked files (including hidden files and symbolic links) and save them to a file
git ls-files --others --exclude-standard --directory > untracked_files.txt

# Count the total number of untracked files
total_files=$(wc -l < untracked_files.txt)

# Calculate the number of files per portion (assuming at least 20 files)
portion=$(( (total_files + 19) / 20 ))

# Check if there are any files to process
if [ "$portion" -eq 0 ]; then
    echo "No untracked files to process."
    exit 0
fi

# Split the file list into portions
split -l "$portion" untracked_files.txt portion_

# Initialize portion counter
counter=1

# Iterate over each portion and push the files
for file in portion_*; do
    echo "Processing group $counter:"

    # Display the files being added
    echo "Adding the following files:"
    cat "$file"
    echo

    # Read each line from the file and add it to the staging area
    while IFS= read -r line || [[ -n "$line" ]]; do
        git add "$line"
    done < "$file"

    # Commit these changes with a group number in the commit message
    git commit --allow-empty -m "Adding files from group $counter"

    # Push to the master branch
    git push origin master

    # Increment the portion counter
    counter=$((counter + 1))

    # Optional: Pause or add any additional commands if needed
done

# Clean up: remove the temporary files
rm untracked_files.txt portion_*

# Disable verbose logging
set +x
