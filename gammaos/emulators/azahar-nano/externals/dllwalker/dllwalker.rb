#!/usr/bin/env ruby
# frozen_string_literal: true

# +-------------------------------------------------------+
# | Copyright (c) 2026, Daniel Bradbury (OpenSauce04)     |
# | SPDX-License-Identifier: BSD-2-Clause                 |
# | Refer to the LICENSE.txt file included.               |
# +-------------------------------------------------------+

# === Initial definitions ===

ORIGIN_FILE, *DLL_DIRS = ARGV
# ^ First arg, All other args in an array

def error(message)
  abort("\e[31mFAIL: \e[0m#{message}")
end


# === Initial validity checks ===

winedump_exists = system('which winedump > /dev/null 2>&1')
if (!winedump_exists)
  error('Command \'winedump\' is missing. Have you installed wine tools?')
end

if (ORIGIN_FILE.nil?)
  error('No file path provided.')
end

if (!File.file?(ORIGIN_FILE))
  error("Provided path doesn\'t contain a file: \'#{ORIGIN_FILE}\'.")
end

is_windows_binary = system("file -L \'#{ORIGIN_FILE}\' | grep PE > /dev/null 2>&1")
if (!is_windows_binary)
  error("Provided file isn't a Windows binary: \'#{ORIGIN_FILE}\'.")
end

if (DLL_DIRS.empty?)
  error('No DLL directories provided.')
end

DLL_DIRS.each do |dir|
  if (!Dir.exist?(dir))
    if (File.file?(dir))
      error("Provided DLL directory is actually a file: \'#{dir}\'.")
    end
    error("DLL directory doesn\'t exist: \'#{dir}\'.")
  end
end


# === Main functionality ===

$final_dll_list = []
$checked_dll_list = []

def find_required_dlls(path)
  # Get winedump output from passed executable
  winedump_out = %x[ winedump -j import #{path} ]
  winedump_arr = winedump_out.split("\n")

  # Filter for only lines which mention DLL files
  filtered_lines = []
  winedump_arr.each do |line|
    if (line.end_with?('.dll'))
      filtered_lines.append(line)
    end
  end

  # Pull DLL filename from each line
  dll_name_list = []
  filtered_lines.each do |line|
    split_line = line.split(' ')
    dll_name_list.append(split_line[-1])
  end

  # Append newly discovered DLL files to the list if they are present and aren't already in the list
  dll_name_list.each do |filename|
    dll_path = ''
    DLL_DIRS.each do |dll_dir|
      try_path = "#{dll_dir}/#{filename}"
      if (File.exist?(try_path))
        dll_path = try_path
      end
    end

    if (!dll_path.empty? && !$final_dll_list.include?(dll_path))
      $final_dll_list.append(dll_path)
    else
      next
    end
  end

  # For each DLL now in the list, check *its* dependencies and add any new ones to the list
  $final_dll_list.each do |dll_name|
    if (!$checked_dll_list.include?(dll_name))
      $checked_dll_list.append(dll_name)
      find_required_dlls(dll_name)
    end
  end
end

# Start dependency resolution from provided origin file
find_required_dlls(ARGV[0])

# Tidy up DLL list before output
$final_dll_list
  .map!{ |dll_path| dll_path.gsub('//', '/') }
  .sort!

# Print DLL list to stdout, each path seperated by a newline
puts $final_dll_list
