#!/bin/bash

# Log file name located one level up from the current directory
LOG_FILE="../git_upload_log.txt"

# Set the maximum chunk size (in bytes)
MAX_CHUNK_SIZE=$((300 * 1024 * 1024)) # 30 MB in bytes

# Function to commit
function commit_changes() {
    local chunk_number=$1
    echo "Committing changes for chunk $chunk_number..." | tee -a "$LOG_FILE"
    git commit --allow-empty -m "Partial upload, chunk $chunk_number" -v 2>&1 | tee -a "$LOG_FILE"
    echo "Commit completed for chunk $chunk_number." | tee -a "$LOG_FILE"
}

# Function to push
function push_changes() {
    echo "Pushing changes to remote..." | tee -a "$LOG_FILE"
    git push origin develop -v 2>&1 | tee -a "$LOG_FILE"
    echo "Push completed." | tee -a "$LOG_FILE"
}

# Check if current directory is a git repository
if [ ! -d ".git" ]; then
    echo "Error: No .git directory found. Please run this script in a Git-initialized directory." | tee -a "$LOG_FILE"
    exit 1
fi

# Initialize variables
chunk_number=1
chunk_size=0
files_added=false

# Generate a sorted list of files by size in descending order
mapfile -t sorted_files < <(find . -type f -exec du -b {} + | sort -nr | cut -f2-)

# Main loop to process sorted files
for file in "${sorted_files[@]}"; do
    if [ -f "$file" ]; then
        file_size=$(du -b "$file" | cut -f1)
        
        # If adding the current file would exceed the chunk size (and the chunk is not empty), commit and push
        if [ $chunk_size -gt 0 ] && [ $((chunk_size + file_size)) -gt $MAX_CHUNK_SIZE ]; then
            commit_changes $chunk_number
            push_changes

            # Reset chunk variables
            ((chunk_number++))
            chunk_size=0
            files_added=false
        fi

        # Add file to the chunk
        git add -f "$file" 2>&1 | tee -a "$LOG_FILE"
        echo "Adding $file (Size: $file_size bytes)" | tee -a "$LOG_FILE"
        ((chunk_size += file_size))
        files_added=true
    fi
done

# Final commit for any remaining files
if [ "$files_added" = true ]; then
    commit_changes $chunk_number
    push_changes
fi

echo "Upload complete." | tee -a "$LOG_FILE"
