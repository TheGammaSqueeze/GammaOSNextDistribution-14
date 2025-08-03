#!/usr/bin/env python3

import argparse
import sys

def parse_args():
	parser = argparse.ArgumentParser(description="Align structs on single lines in BEGIN...END ALIGNED SECTION blocks")
	parser.add_argument('file', metavar="FILE", nargs='?', help="If provided, a file to process.  Otherwise stdin")
	return parser.parse_args()

def braces_match(line):
	open_braces = 0
	for c in line:
		if c == '{':
			open_braces = open_braces + 1
		elif c == '}':
			if open_braces == 0:
				sys.exit("Too many close braces on line:\n{}", line)
			open_braces = open_braces - 1
	return (open_braces == 0)

assert(braces_match("{{{}}}"))
assert(not braces_match("{{}{}"))

def is_partial_comment(line):
	stripped = line.strip()
	return stripped.startswith("/*") and not stripped.endswith("*/")

assert(not is_partial_comment("/* */"))
assert(not is_partial_comment("/* /* */"))
assert(is_partial_comment("/* */ /"))
assert(is_partial_comment("/* */ /*"))

def collineate(lines):
	ret = []
	current_line = ""
	for line in lines:
		current_line = current_line + line
		if braces_match(current_line) and not is_partial_comment(current_line):
			ret.append(current_line)
			current_line = ""
	if current_line != "":
		ret.append(current_line)
	return ret

assert(collineate(["/*", "...", "*/"]) == ["/*...*/"])
assert(collineate(["{", "/*", "*/", "}"]) == ["{/**/}"])
assert(collineate(["{", "{", "}", "}"]) == ["{{}}"])
assert(collineate(["{", "}", "{", "}"]) == ["{}", "{}"])

def is_line_ignored(line):
	stripped = line.lstrip()
	return (stripped == "") or stripped.startswith("/*") or stripped.startswith("//")

def pack(line):
	if is_line_ignored(line):
		return line

	ret = ""
	in_leading_whitespace = True
	for c in line:
		if (c == " ") or (c == "\t"):
			if in_leading_whitespace:
				ret = ret + c
		else:
			in_leading_whitespace = False
			ret = ret + c
	return ret

def prettify(line):
	if is_line_ignored(line):
		return line

	ret = ""
	for c in line:
		if (c == ","):
			ret = ret + ", "
		elif (c == "="):
			ret = ret + " = "
		elif (c == "{"):
			ret = ret + "{ "
		elif (c == "}"):
			ret = ret + " }"
		else:
			ret = ret + c
	return ret

def has_an_offset(c):
	return (c == "{") or (c == ",")

def get_offsets(line):
	if is_line_ignored(line):
		return None
	ret = []
	offset = -1
	for c in line:
		offset = offset + 1
		if has_an_offset(c):
			ret.append(offset)
			offset = 0
	return ret

def check_offsets(lines, offsets_list):
	reference_index = -1
	for line in lines:
		reference_index = reference_index + 1
		if not is_line_ignored(line):
			break
	if reference_index == len(lines):
		return # no lines to check
	index = -1
	for offsets in offsets_list:
		index = index + 1
		if offsets == None:
			continue
		if len(offsets) != len(offsets_list[reference_index]):
			sys.exit("Lines have differing numbers of offsets:\n" + lines[index] + "\n" + lines[reference_index])

def collect_max_offsets(offsets_list):
	max_offsets = None
	for offsets in offsets_list:
		if offsets == None:
			continue
		if max_offsets == None:
			max_offsets = offsets
			continue
		for index in range(len(max_offsets)):
			if max_offsets[index] < offsets[index]:
				max_offsets[index] = offsets[index]
	return max_offsets

def fix_offsets(line, target_offsets):
	ret = ""
	offset_index = 0
	offset = -1
	for c in line:
		offset = offset + 1
		ret = ret + c
		if has_an_offset(c):
			ret = ret + " " * (target_offsets[offset_index] - offset)
			offset_index = offset_index + 1
			offset = 0
	return ret

def align_section(lines, args):
	lines = collineate(lines)
	lines = [pack(l) for l in lines]
	lines = [prettify(l) for l in lines]
	lines = [l.rstrip() for l in lines]
	offsets_list = [get_offsets(l) for l in lines]
	target_offsets = collect_max_offsets(offsets_list)
	lines = [fix_offsets(l, target_offsets) for l in lines]
	return lines

def align_file(lines, args):
	ret = []
	lines_to_align = None

	for line in lines:
		if lines_to_align is None:
			if "BEGIN ALIGNED SECTION" in line:
				lines_to_align = []
			ret.append(line)
		else:
			if "END ALIGNED SECTION" in line:
				ret = ret + align_section(lines_to_align, args)
				lines_to_align = None
				ret.append(line)
			else:
				lines_to_align.append(line)

	if lines_to_align is not None:
		sys.exit("Incomplete aligned section")

	return ret

if __name__ == '__main__':
	args = parse_args()
	lines = []

	if args.file:
		with open(args.file) as fd:
			lines = [l.rstrip() for l in fd.readlines()]
	else:
		lines = [l.rstrip() for l in sys.stdin.readlines()]

	for line in align_file(lines, args):
		print(line)
