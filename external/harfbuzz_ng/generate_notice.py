#!/usr/bin/env python3

from enum import Enum
from pathlib import Path
from typing import Sequence
from typing import Tuple
from fontTools import ttLib
import tempfile
import subprocess
import json
import argparse
import contextlib
import os
import re
import sys

# list of specific files to be ignored.
IGNORE_FILE_NAME = [
  # Exclude myself
  "generate_notice.py",

  # License files
  "LICENSE",
  "LICENSE_APACHE2.TXT",
  "LICENSE_FSFAP.TXT",
  "LICENSE_GPLv2.TXT",
  "LICENSE_GPLv2_WITH_AUTOCONF_EXCEPTION.TXT",
  "LICENSE_GPLv3_WITH_AUTOCONF_EXCEPTION.TXT",
  "LICENSE_HPND_SELL_VARIANT.TXT",
  "LICENSE_ISC.TXT",
  "LICENSE_MIT_MODERN_VARIANT.TXT",
  "LICENSE_OFL.TXT",
  "METADATA",
  "MODULE_LICENSE_MIT",
  "NOTICE",

  # dictionary which has Copyright word
  "perf/texts/en-words.txt",

  # broken unreadable font file for fuzzing target
  "test/fuzzing/fonts/sbix-extents.ttf",
]

IGNORE_DIR_IF_NO_COPYRIGHT = [
    "test",
    "perf",
]

NO_COPYRIGHT_FILES = [
  ".ci/build-win32.sh",
  ".ci/build-win64.sh",
  ".ci/deploy-docs.sh",
  ".ci/publish_release_artifact.sh",
  ".ci/requirements-fonttools.in",
  ".ci/requirements-fonttools.txt",
  ".ci/requirements.in",
  ".ci/requirements.txt",
  ".ci/win32-cross-file.txt",
  ".ci/win64-cross-file.txt",
  ".circleci/config.yml",
  ".clang-format",
  ".codecov.yml",
  ".editorconfig",
  ".github/dependabot.yml",
  ".github/workflows/arm-ci.yml",
  ".github/workflows/cifuzz.yml",
  ".github/workflows/configs-build.yml",
  ".github/workflows/coverity-scan.yml",
  ".github/workflows/linux-ci.yml",
  ".github/workflows/macos-ci.yml",
  ".github/workflows/msvc-ci.yml",
  ".github/workflows/msys2-ci.yml",
  ".github/workflows/scorecard.yml",
  "AUTHORS",
  "BUILD.md",
  "CMakeLists.txt",
  "CONFIG.md",
  "Makefile.am",
  "NEWS",
  "OWNERS",
  "README",
  "README.android",
  "README.md",
  "README.mingw.md",
  "README.python.md",
  "RELEASING.md",
  "SECURITY.md",
  "TESTING.md",
  "TEST_MAPPING",
  "THANKS",
  "autogen.sh",
  "configure.ac",
  "docs/HarfBuzz.png",
  "docs/HarfBuzz.svg",
  "docs/Makefile.am",
  "docs/features.dot",
  "docs/harfbuzz-docs.xml",
  "docs/harfbuzz-overrides.txt",
  "docs/harfbuzz-sections.txt",
  "docs/meson.build",
  "docs/repacker.md",
  "docs/serializer.md",
  "docs/subset-preprocessing.md",
  "docs/usermanual-buffers-language-script-and-direction.xml",
  "docs/usermanual-clusters.xml",
  "docs/usermanual-fonts-and-faces.xml",
  "docs/usermanual-getting-started.xml",
  "docs/usermanual-glyph-information.xml",
  "docs/usermanual-install-harfbuzz.xml",
  "docs/usermanual-integration.xml",
  "docs/usermanual-object-model.xml",
  "docs/usermanual-opentype-features.xml",
  "docs/usermanual-shaping-concepts.xml",
  "docs/usermanual-utilities.xml",
  "docs/usermanual-what-is-harfbuzz.xml",
  "docs/version.xml.in",
  "docs/wasm-shaper.md",
  "harfbuzz.doap",
  "meson.build",
  "meson_options.txt",
  "mingw-configure.sh",
  "replace-enum-strings.cmake",
  "src/ArabicPUASimplified.txt",
  "src/ArabicPUATraditional.txt",
  "src/Makefile.am",
  "src/Makefile.sources",
  "src/OT/Layout/GPOS/Anchor.hh",
  "src/OT/Layout/GPOS/AnchorFormat1.hh",
  "src/OT/Layout/GPOS/AnchorFormat2.hh",
  "src/OT/Layout/GPOS/AnchorFormat3.hh",
  "src/OT/Layout/GPOS/AnchorMatrix.hh",
  "src/OT/Layout/GPOS/ChainContextPos.hh",
  "src/OT/Layout/GPOS/Common.hh",
  "src/OT/Layout/GPOS/ContextPos.hh",
  "src/OT/Layout/GPOS/CursivePos.hh",
  "src/OT/Layout/GPOS/CursivePosFormat1.hh",
  "src/OT/Layout/GPOS/ExtensionPos.hh",
  "src/OT/Layout/GPOS/GPOS.hh",
  "src/OT/Layout/GPOS/LigatureArray.hh",
  "src/OT/Layout/GPOS/MarkArray.hh",
  "src/OT/Layout/GPOS/MarkBasePos.hh",
  "src/OT/Layout/GPOS/MarkBasePosFormat1.hh",
  "src/OT/Layout/GPOS/MarkLigPos.hh",
  "src/OT/Layout/GPOS/MarkLigPosFormat1.hh",
  "src/OT/Layout/GPOS/MarkMarkPos.hh",
  "src/OT/Layout/GPOS/MarkMarkPosFormat1.hh",
  "src/OT/Layout/GPOS/MarkRecord.hh",
  "src/OT/Layout/GPOS/PairPos.hh",
  "src/OT/Layout/GPOS/PairPosFormat1.hh",
  "src/OT/Layout/GPOS/PairPosFormat2.hh",
  "src/OT/Layout/GPOS/PairSet.hh",
  "src/OT/Layout/GPOS/PairValueRecord.hh",
  "src/OT/Layout/GPOS/PosLookup.hh",
  "src/OT/Layout/GPOS/PosLookupSubTable.hh",
  "src/OT/Layout/GPOS/SinglePos.hh",
  "src/OT/Layout/GPOS/SinglePosFormat1.hh",
  "src/OT/Layout/GPOS/SinglePosFormat2.hh",
  "src/OT/Layout/GPOS/ValueFormat.hh",
  "src/OT/Layout/GSUB/AlternateSet.hh",
  "src/OT/Layout/GSUB/AlternateSubst.hh",
  "src/OT/Layout/GSUB/AlternateSubstFormat1.hh",
  "src/OT/Layout/GSUB/ChainContextSubst.hh",
  "src/OT/Layout/GSUB/Common.hh",
  "src/OT/Layout/GSUB/ContextSubst.hh",
  "src/OT/Layout/GSUB/ExtensionSubst.hh",
  "src/OT/Layout/GSUB/GSUB.hh",
  "src/OT/Layout/GSUB/Ligature.hh",
  "src/OT/Layout/GSUB/LigatureSet.hh",
  "src/OT/Layout/GSUB/LigatureSubst.hh",
  "src/OT/Layout/GSUB/LigatureSubstFormat1.hh",
  "src/OT/Layout/GSUB/MultipleSubst.hh",
  "src/OT/Layout/GSUB/MultipleSubstFormat1.hh",
  "src/OT/Layout/GSUB/ReverseChainSingleSubst.hh",
  "src/OT/Layout/GSUB/ReverseChainSingleSubstFormat1.hh",
  "src/OT/Layout/GSUB/Sequence.hh",
  "src/OT/Layout/GSUB/SingleSubst.hh",
  "src/OT/Layout/GSUB/SingleSubstFormat1.hh",
  "src/OT/Layout/GSUB/SingleSubstFormat2.hh",
  "src/OT/Layout/GSUB/SubstLookup.hh",
  "src/OT/Layout/GSUB/SubstLookupSubTable.hh",
  "src/OT/glyf/CompositeGlyph.hh",
  "src/OT/glyf/Glyph.hh",
  "src/OT/glyf/GlyphHeader.hh",
  "src/OT/glyf/SimpleGlyph.hh",
  "src/OT/glyf/SubsetGlyph.hh",
  "src/OT/glyf/VarCompositeGlyph.hh",
  "src/OT/glyf/composite-iter.hh",
  "src/OT/glyf/coord-setter.hh",
  "src/OT/glyf/glyf-helpers.hh",
  "src/OT/glyf/glyf.hh",
  "src/OT/glyf/loca.hh",
  "src/OT/glyf/path-builder.hh",
  "src/addTable.py",
  "src/check-c-linkage-decls.py",
  "src/check-externs.py",
  "src/check-header-guards.py",
  "src/check-includes.py",
  "src/check-libstdc++.py",
  "src/check-static-inits.py",
  "src/check-symbols.py",
  "src/fix_get_types.py",
  "src/gen-arabic-joining-list.py",
  "src/gen-arabic-pua.py",
  "src/gen-arabic-table.py",
  "src/gen-def.py",
  "src/gen-emoji-table.py",
  "src/gen-harfbuzzcc.py",
  "src/gen-hb-version.py",
  "src/gen-indic-table.py",
  "src/gen-os2-unicode-ranges.py",
  "src/gen-ragel-artifacts.py",
  "src/gen-tag-table.py",
  "src/gen-ucd-table.py",
  "src/gen-use-table.py",
  "src/gen-vowel-constraints.py",
  "src/harfbuzz-cairo.pc.in",
  "src/harfbuzz-config.cmake.in",
  "src/harfbuzz-gobject.pc.in",
  "src/harfbuzz-icu.pc.in",
  "src/harfbuzz-subset.cc",
  "src/harfbuzz-subset.pc.in",
  "src/harfbuzz.cc",
  "src/harfbuzz.pc.in",
  "src/hb-ot-shaper-arabic-joining-list.hh",
  "src/hb-ot-shaper-arabic-pua.hh",
  "src/hb-ot-shaper-arabic-table.hh",
  "src/hb-ot-shaper-indic-table.cc",
  "src/hb-ot-shaper-use-table.hh",
  "src/hb-ot-shaper-vowel-constraints.cc",
  "src/hb-ot-tag-table.hh",
  "src/hb-ucd-table.hh",
  "src/hb-unicode-emoji-table.hh",
  "src/justify.py",
  "src/meson.build",
  "src/ms-use/IndicPositionalCategory-Additional.txt",
  "src/ms-use/IndicShapingInvalidCluster.txt",
  "src/ms-use/IndicSyllabicCategory-Additional.txt",
  "src/relative_to.py",
  "src/sample.py",
  "src/test-use-table.cc",
  "src/update-unicode-tables.make",
  "src/wasm/graphite/Makefile",
  "src/wasm/graphite/shape.cc",
  "src/wasm/rust/harfbuzz-wasm/Cargo.toml",
  "src/wasm/rust/harfbuzz-wasm/src/lib.rs",
  "src/wasm/sample/c/Makefile",
  "src/wasm/sample/c/shape-fallback.cc",
  "src/wasm/sample/c/shape-ot.cc",
  "src/wasm/sample/rust/hello-wasm/Cargo.toml",
  "src/wasm/sample/rust/hello-wasm/src/lib.rs",
  "subprojects/.gitignore",
  "subprojects/cairo.wrap",
  "subprojects/freetype2.wrap",
  "subprojects/glib.wrap",
  "subprojects/google-benchmark.wrap",
  "subprojects/packagefiles/ragel/meson.build",
  "subprojects/ragel.wrap",
  "util/Makefile.am",
  "util/Makefile.sources",
  "util/meson.build",
]

class CommentType(Enum):
  C_STYLE_BLOCK = 1  # /* ... */
  C_STYLE_BLOCK_AS_LINE = 2  # /* ... */ but uses multiple lines of block comments.
  C_STYLE_LINE = 3 # // ...
  SCRIPT_STYLE_HASH = 4 #  # ...
  OPENTYPE_NAME = 5
  OPENTYPE_COLLECTION_NAME = 6
  UNKNOWN = 10000


# Helper function of showing error message and immediate exit.
def fatal(msg: str):
  sys.stderr.write(str(msg))
  sys.stderr.write("\n")
  sys.exit(1)


def warn(msg: str):
  sys.stderr.write(str(msg))
  sys.stderr.write("\n")

def debug(msg: str):
  # sys.stderr.write(str(msg))
  # sys.stderr.write("\n")
  pass


def cleanup_and_join(out_lines: Sequence[str]):
  while not out_lines[-1].strip():
    out_lines.pop(-1)

  # If all lines starts from empty space, strip it out.
  while all([len(x) == 0 or x[0] == ' ' for x in out_lines]):
    out_lines = [x[1:] for x in out_lines]

  if not out_lines:
    fatal("Failed to get copyright info")
  return "\n".join(out_lines)


def get_comment_type(copyright_line: str, path_str: str) -> CommentType:
  # vms_make.com contains multiple copyright header as a string constants.
  if copyright_line.startswith("#"):
    return CommentType.SCRIPT_STYLE_HASH
  if copyright_line.startswith("//"):
    return CommentType.C_STYLE_LINE
  return CommentType.C_STYLE_BLOCK

def extract_copyright_font(path_str: str) -> str:
  path = Path(path_str)
  if path.suffix in ['.ttf', '.otf', '.dfont']:
    return extract_from_opentype_name(path, 0)
  elif path.suffix in ['.ttc', '.otc']:
    return extract_from_opentype_collection_name(path)


# Extract copyright notice and returns next index.
def extract_copyright_at(lines: Sequence[str], i: int, path: str) -> Tuple[str, int]:
  commentType = get_comment_type(lines[i], path)

  if commentType == CommentType.C_STYLE_BLOCK:
    return extract_from_c_style_block_at(lines, i, path)
  elif commentType == CommentType.C_STYLE_LINE:
    return extract_from_c_style_lines_at(lines, i, path)
  elif commentType == CommentType.SCRIPT_STYLE_HASH:
    return extract_from_script_hash_at(lines, i, path)
  else:
    fatal("Uknown comment style: %s" % lines[i])

def extract_from_opentype_collection_name(path: str) -> str:

  with open(path, mode="rb") as f:
    head = f.read(12)
  
  if head[0:4].decode() != 'ttcf':
    fatal('Invalid magic number for TTC file: %s' % path)
  numFonts = int.from_bytes(head[8:12], byteorder="big")

  licenses = set()
  for i in range(0, numFonts):
    license = extract_from_opentype_name(path, i)
    licenses.add(license)

  return '\n\n'.join(licenses)

def extract_from_opentype_name(path: str, index: int) -> str:

  def get_preferred_name(nameID: int, ttf):
    def get_score(platID: int, encID: int):
      if platID == 3 and encID == 10:
        return 0
      elif platID == 0 and encID == 6:
        return 1
      elif platID == 0 and encID == 4:
        return 2
      elif platID == 3 and encID == 1:
        return 3
      elif platID == 0 and encID == 3:
        return 4
      elif platID == 0 and encID == 2:
        return 5
      elif platID == 0 and encID == 1:
        return 6
      elif platID == 0 and encID == 0:
        return 7
      else:
        return 10000

    best_score = 1000000
    best_name = None

    if 'name' not in ttf:
      return None

    for name in ttf['name'].names:
      if name.nameID != nameID:
        continue

      score = get_score(name.platformID, name.platEncID)
      if score < best_score:
        best_score = score
        best_name = name

    return best_name

  def get_notice_from_cff(ttf):
    if 'CFF ' not in ttf:
      return None

    # Looks like there is no way of getting Notice line in CFF table.
    # Use the line that has "Copyright" in the string pool.
    cff = ttf['CFF '].cff
    for string in cff.strings:
      if 'Copyright' in string:
        return string
    return None

  with contextlib.closing(ttLib.TTFont(path, 0, fontNumber=index)) as ttf:
    copyright = get_preferred_name(0, ttf)
    if not copyright:
      copyright = get_notice_from_cff(ttf)
    if not copyright:
      return None

    license_description = get_preferred_name(13, ttf)

    if license_description:
      copyright = str(copyright) + "\n\n" + str(license_description)
    else:
      copyright = str(copyright)

    license_url = get_preferred_name(14, ttf)

    if license_url:
      copyright = str(copyright) + "\n\n" + str(license_url)
    else:
      copyright = str(copyright)

    return copyright

def extract_from_c_style_lines_at(
    lines: Sequence[str], i: int, path: str) -> Tuple[str, int]:
  def is_copyright_end(line):
    if line.startswith("//"):
      return False
    else:
      return True
  start = i
  while i < len(lines):
    if is_copyright_end(lines[i]):
      break
    i += 1
  end = i

  if start == end:
    fatal("Failed to get copyright info")

  out_lines = []
  for line in lines[start:end]:
    if line.startswith("//# "):  # Andorid.bp uses //# style
      out_lines.append(line[4:])
    elif line.startswith("//#"):  # Andorid.bp uses //# style
      out_lines.append(line[3:])
    elif line.startswith("// "):
      out_lines.append(line[3:])
    elif line == "//":
      out_lines.append(line[2:])
    else:
      out_lines.append(line)

  return (cleanup_and_join(out_lines), i + 1)


def extract_from_script_hash_at(
    lines: Sequence[str], i: int, path: str) -> Tuple[str, int]:
  if lines[i].strip()[0] != "#":
    return (None, i + 1)
  def is_copyright_end(lines: str, i: int) -> bool:
    if "#" not in lines[i]:
      return True
    # treat double spacing as end of license header
    if lines[i] == "#" and lines[i+1] == "#":
      return True
    return False

  start = i
  while i < len(lines):
    if is_copyright_end(lines, i):
      break
    i += 1
  end = i

  if start == end:
    fatal("Failed to get copyright info")

  out_lines = []
  for line in lines[start:end]:
    if line.startswith("# "):
      out_lines.append(line[2:])
    elif line == "#":
      out_lines.append(line[1:])
    else:
      out_lines.append(line)

  return (cleanup_and_join(out_lines), i + 1)


def extract_from_c_style_block_at(
    lines: Sequence[str], i: int, path: str) -> Tuple[str, int]:

  def is_copyright_end(lines: str, i: int) -> bool:
    if "*/" in lines[i]:
      return True
    if lines[i] == " *" and lines[i + 1] == " *":
      return True
    if lines[i] == "" and lines[i + 1] == "":
      return True
    return False

  start = i
  i += 1 # include at least one line
  while i < len(lines):
    if is_copyright_end(lines, i):
      break
    i += 1
  end = i + 1

  out_lines = []
  for line in lines[start:end]:
    clean_line = line

    # Strip begining "/*" chars
    if clean_line.startswith("/* "):
      clean_line = clean_line[3:]
    if clean_line == "/*":
      clean_line = clean_line[2:]

    # Strip ending "*/" chars
    if clean_line.endswith(" */"):
      clean_line = clean_line[:-3]
    if clean_line.endswith("*/"):
      clean_line = clean_line[:-2]

    # Strip starting " *" chars
    if clean_line.startswith(" * "):
      clean_line = clean_line[3:]
    if clean_line == " *":
      clean_line = clean_line[2:]

    # hb-aots-tester.cpp has underline separater which can be dropped.
    if path.endswith("test/shape/data/aots/hb-aots-tester.cpp"):
      clean_line = clean_line.replace("_", "")

    # Strip trailing spaces
    clean_line = clean_line.rstrip()

    out_lines.append(clean_line)

  return (cleanup_and_join(out_lines), i + 1)


# Returns true if the line shows the start of copyright notice.
def is_copyright_line(line: str, path: str) -> bool:
  if "Copyright" not in line:
    return False

  # For avoiding unexpected mismatches, exclude quoted Copyright string.
  if "`Copyright'" in line:
    return False
  if "\"Copyright\"" in line:
    return False

  if "OpCode_Copyright" in line:
    return False

  if path.endswith("src/hb-ot-name.h") and "HB_OT_NAME_ID_COPYRIGHT" in line:
    return False

  return True

def assert_mandatory_copyright(path_str: str):
  path = Path(path_str)
  toplevel_dir = str(path).split(os.sep)[0]

  if toplevel_dir in IGNORE_DIR_IF_NO_COPYRIGHT:
    return

  fatal("%s does not contain Copyright line" % path)


# Extract the copyright notice and put it into copyrights arg.
def do_file(path: str, copyrights: set, no_copyright_files: set):
  raw = Path(path).read_bytes()
  basename = os.path.basename(path)
  dirname = os.path.dirname(path)

  is_font = (dirname.endswith('./test/fuzzing/fonts') or
             Path(path).suffix in ['.ttf', '.otf', '.dfont', '.ttc', '.otc'])

  if is_font:
    notice = extract_copyright_font(path)
    if not notice:
      assert_mandatory_copyright(path)
      return

    if not notice in copyrights:
      copyrights[notice] = []
    copyrights[notice].append(path)
  else:
    try:
      content = raw.decode("utf-8")
    except UnicodeDecodeError:
      content = raw.decode("iso-8859-1")

    if not "Copyright" in content:
      if path in no_copyright_files:
        no_copyright_files.remove(path)
      else:
        assert_mandatory_copyright(path)
      return

    lines = content.splitlines()

    # The COPYING in the in-house dir has full OFL license with description.
    # Use the OFL license description body.
    if path.endswith("test/shape/data/in-house/COPYING") or path.endswith("test/COPYING"):
      notice = cleanup_and_join(lines[9:])
      copyrights.setdefault(notice, [])
      copyrights[notice].append(path)
      return

    # The COPYING in the top dir has MIT-Modern-Variant license with description.
    # Use the entire file as a license notice.
    if path.endswith("COPYING") and str(Path(path)) == 'COPYING':
      notice = cleanup_and_join(lines)
      copyrights.setdefault(notice, [])
      copyrights[notice].append(path)
      return

    i = 0
    license_found = False
    while i < len(lines):
      if is_copyright_line(lines[i], path):
        (notice, nexti) = extract_copyright_at(lines, i, path)
        if notice:
          copyrights.setdefault(notice, [])
          copyrights[notice].append(path)
          license_found = True

        i = nexti
      else:
        i += 1

    if not license_found:
      assert_mandatory_copyright(path)

def do_check(path, format):
  if not path.endswith('/'): # make sure the path ends with slash
    path = path + '/'

  file_to_ignore = set([os.path.join(path, x) for x in IGNORE_FILE_NAME])
  no_copyright_files = set([os.path.join(path, x) for x in NO_COPYRIGHT_FILES])
  copyrights = {}

  for directory, sub_directories, filenames in os.walk(path):
    # skip .git directory
    if ".git" in sub_directories:
      sub_directories.remove(".git")

    for fname in filenames:
      fpath = os.path.join(directory, fname)
      if fpath in file_to_ignore:
        file_to_ignore.remove(fpath)
        continue

      do_file(fpath, copyrights, no_copyright_files)

  if len(file_to_ignore) != 0:
    fatal("Following files are listed in IGNORE_FILE_NAME but doesn't exists,.\n"
          + "\n".join(file_to_ignore))

  if len(no_copyright_files) != 0:
    fatal("Following files are listed in NO_COPYRIGHT_FILES but doesn't exists.\n"
          + "\n".join(no_copyright_files))

  if format == Format.notice:
    print_notice(copyrights, False)
  elif format == Format.notice_with_filename:
    print_notice(copyrights, True)
  elif format == Format.html:
    print_html(copyrights)
  elif format == Format.json:
    print_json(copyrights)

def print_html(copyrights):
  print('<html>')
  print("""
  <head>
    <style>
      table {
        font-family: monospace
      }

      table tr td {
        padding: 10px 10px 10px 10px
      }
    </style>
  </head>
  """)
  print('<body>')
  print('<table border="1" style="border-collapse:collapse">')
  for notice in sorted(copyrights.keys()):
    files = sorted(copyrights[notice])

    print('<tr>')
    print('<td>')
    print('<ul>')
    for file in files:
      print('<li>%s</li>' % file)
    print('</ul>')
    print('</td>')

    print('<td>')
    print('<p>%s</p>' % notice.replace('\n', '<br>'))
    print('</td>')

    print('</tr>')


  print('</table>')
  print('</body></html>')

def print_notice(copyrights, print_file):
  # print the copyright in sorted order for stable output.
  for notice in sorted(copyrights.keys()):
    if print_file:
      files = sorted(copyrights[notice])
      print("\n".join(files))
      print()
    print(notice)
    print()
    print("-" * 67)
    print()

def print_json(copyrights):
  print(json.dumps(copyrights))

class Format(Enum):
  notice = 'notice'
  notice_with_filename = 'notice_with_filename'
  html = 'html'
  json = 'json'

  def __str__(self):
    return self.value

def main():
  parser = argparse.ArgumentParser(description="Collect notice headers.")
  parser.add_argument("--format", dest="format", type=Format, choices=list(Format),
                      default=Format.notice, help="print filename before the license notice")
  parser.add_argument("--target", dest="target", action='store',
                      required=True, help="target directory to collect notice headers")
  res = parser.parse_args()
  do_check(res.target, res.format)

if __name__ == "__main__":
  main()

