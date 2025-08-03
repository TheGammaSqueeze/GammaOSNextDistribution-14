#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Checks dwarf CFI (unwinding) information by comparing it to disassembly.
It is only a simple heuristic check of stack pointer adjustments.
Fully inferring CFI from disassembly is not possible in general.
"""

import os, re, subprocess, collections, pathlib, bisect, collections, sys
from argparse import ArgumentParser
from dataclasses import dataclass
from functools import cache
from pathlib import Path
from typing import Any, List, Optional, Set, Tuple, Dict

arch: str = ""
ARCHES = ["i386", "x86_64", "arm", "aarch64", "riscv64"]

IGNORE : Dict[str, List[str]] = {
    # Aligns stack.
    "art_quick_osr_stub": ["i386"],
    # Intermediate invalid CFI while loading all registers.
    "art_quick_do_long_jump": ["x86_64"],
    # Saves/restores SP in other register.
    "art_quick_generic_jni_trampoline": ["arm", "i386", "x86_64"],
    # Starts with non-zero offset at the start of the method.
    "art_quick_throw_null_pointer_exception_from_signal": ARCHES,
    # Pops stack without static control flow past the opcode.
    "nterp_op_return": ["arm", "aarch64", "i386", "x86_64", "riscv64"],
}

SP = {"arm": "SP", "aarch64": "WSP", "i386": "ESP", "x86_64": "RSP", "riscv64": "X2"}
INITIAL_OFFSET = {"i386": 4, "x86_64": 8}

@cache
def get_inst_semantics(arch: str) -> List[Any]:
  """ List of regex expressions for supported instructions and their behaviour """

  rexprs = []
  def add(rexpr, adjust_offset=lambda m: 0, adjust_pc=None):
    rexprs.append((re.compile(rexpr), adjust_offset, adjust_pc))
  if arch in ["i386", "x86_64"]:
    ptr_size = {"i386": 4, "x86_64": 8}[arch]
    add(r"push. .*", lambda m: ptr_size)
    add(r"pop. .*", lambda m: -ptr_size)
    add(r"sub. \$(\w+), (?:%esp|%rsp)", lambda m: int(m[1], 0))
    add(r"add. \$(\w+), (?:%esp|%rsp)", lambda m: -int(m[1], 0))
    add(r"call. (0x\w+) <.*", lambda m: ptr_size, adjust_pc=lambda m: int(m[1], 0))
    add(r"j[a-z]* (0x\w+) <.*", adjust_pc=lambda m: int(m[1], 0))
  if arch in ["arm", "aarch64"]:
    add(r"sub sp,(?: sp,)? #(\w+)", lambda m: int(m[1], 0))
    add(r"add sp,(?: sp,)? #(\w+)", lambda m: -int(m[1], 0))
    add(r"str \w+, \[sp, #-(\d+)\]!", lambda m: int(m[1]))
    add(r"ldr \w+, \[sp\], #(\d+)", lambda m: -int(m[1]))
    add(r"stp \w+, \w+, \[sp, #-(\w+)\]!", lambda m: int(m[1], 0))
    add(r"ldp \w+, \w+, \[sp\], #(\w+)", lambda m: -int(m[1], 0))
    add(r"vpush \{([d0-9, ]*)\}", lambda m: 8 * len(m[1].split(",")))
    add(r"vpop \{([d0-9, ]*)\}", lambda m: -8 * len(m[1].split(",")))
    add(r"v?push(?:\.w)? \{([\w+, ]*)\}", lambda m: 4 * len(m[1].split(",")))
    add(r"v?pop(?:\.w)? \{([\w+, ]*)\}", lambda m: -4 * len(m[1].split(",")))
    add(r"cb\w* \w+, (0x\w+).*", adjust_pc=lambda m: int(m[1], 0))
    add(r"(?:b|bl|b\w\w) (0x\w+).*", adjust_pc=lambda m: int(m[1], 0))
  if arch in ["riscv64"]:
    add(r"addi sp, sp, (-?\w+)", lambda m: -int(m[1], 0))
    add(r"b\w* (?:\w+, )+(0x\w+).*", adjust_pc=lambda m: int(m[1], 0))
    add(r"(?:j|jal) (?:\w+, )?(0x\w+).*", adjust_pc=lambda m: int(m[1], 0))
  return rexprs

@dataclass(frozen=True)
class Error(Exception):
  address: int
  message: str

def get_arch(lib: pathlib.Path) -> str:
  """ Get architecture of the given library based on the ELF header. """

  proc = subprocess.run([args.objdump, "--file-headers", lib],
                        encoding='utf-8',
                        capture_output=True,
                        check=True)

  m = re.search("^architecture: *(.*)$", proc.stdout, re.MULTILINE)
  assert m, "Can not find ABI of ELF file " + str(lib)
  assert m.group(1) in ARCHES, "Unknown arch: " + m.group(1)
  return m.group(1)

Source = collections.namedtuple("Source", ["pc", "file", "line", "flag"])

def get_src(lib: pathlib.Path) -> List[Source]:
  """ Get source-file and line-number for all hand-written assembly code. """

  proc = subprocess.run([args.dwarfdump, "--debug-line", lib],
                        encoding='utf-8',
                        capture_output=True,
                        check=True)

  section_re = re.compile("^debug_line\[0x[0-9a-f]+\]$", re.MULTILINE)
  filename_re = re.compile('file_names\[ *(\d)+\]:\n\s*name: "(.*)"', re.MULTILINE)
  line_re = re.compile('0x([0-9a-f]{16}) +(\d+) +\d+ +(\d+)'  # pc, line, column, file
                       ' +\d+ +\d +(.*)')                     # isa, discriminator, flag

  results = []
  for section in section_re.split(proc.stdout):
    files = {m[1]: m[2] for m in filename_re.finditer(section)}
    if not any(f.endswith(".S") for f in files.values()):
      continue
    lines = line_re.findall(section)
    results.extend([Source(int(a, 16), files[fn], l, fg) for a, l, fn, fg in lines])
  return sorted(filter(lambda line: "end_sequence" not in line.flag, results))

Fde = collections.namedtuple("Fde", ["pc", "end", "data"])

def get_fde(lib: pathlib.Path) -> List[Fde]:
  """ Get all FDE blocks (in dumped text-based format) """

  proc = subprocess.run([args.dwarfdump, "--debug-frame", lib],
                        encoding='utf-8',
                        capture_output=True,
                        check=True)

  section_re = re.compile("\n(?! |\n)", re.MULTILINE)  # New-line not followed by indent.
  fda_re = re.compile(".* FDE .* pc=([0-9a-f]+)...([0-9a-f]+)")

  results = []
  for section in section_re.split(proc.stdout):
    m = fda_re.match(section)
    if m:
      fde = Fde(int(m[1], 16), int(m[2], 16), section)
      if fde.pc != 0:
        results.append(fde)
  return sorted(results)

Asm = collections.namedtuple("Asm", ["pc", "name", "data"])

def get_asm(lib: pathlib.Path) -> List[Asm]:
  """ Get all ASM blocks (in dumped text-based format) """

  proc = subprocess.run(
      [
          args.objdump,
          "--disassemble",
          "--no-show-raw-insn",
          "--disassemble-zeroes",
          lib,
      ],
      encoding="utf-8",
      capture_output=True,
      check=True,
  )

  section_re = re.compile("\n(?! |\n)", re.MULTILINE)  # New-line not followed by indent.
  sym_re = re.compile("([0-9a-f]+) <(.+)>:")

  results = []
  for section in section_re.split(proc.stdout):
    sym = sym_re.match(section)
    if sym:
      results.append(Asm(int(sym[1], 16), sym[2], section))
  return sorted(results)

Inst = collections.namedtuple("Inst", ["pc", "inst"])

def get_instructions(asm: Asm) -> List[Inst]:
  """ Extract individual instructions from disassembled code block """

  data = re.sub(r"[ \t]+", " ", asm.data)
  inst_re = re.compile(r"([0-9a-f]+): +(?:[0-9a-f]{2} +)*(.*)")
  return [Inst(int(pc, 16), inst) for pc, inst in inst_re.findall(data)]

# PC -> CFA offset (stack size at given PC; None if it not just trivial SP+<integer>)
CfaOffsets = Dict[int, Optional[int]]

def get_dwarf_cfa_offsets(insts: List[Inst], fde: Fde) -> CfaOffsets:
  """ Get CFA offsets for all instructions from DWARF """

  # Parse the CFA offset definitions from the FDE.
  sp = SP[arch]
  m = re.compile(r"(0x[0-9a-f]+): CFA=(\w*)([^:\n]*)").findall(fde.data)
  cfa = collections.deque([(int(a, 0), int(o or "0") if r == sp else None) for a, r, o in m])
  if all(offset is None for add, offset in cfa):
    # This would create result that never checks anything.
    raise Error(insts[0].pc, "No trivial CFA offsets. Add function to IGNORE list?")

  # Create map from instruction PCs to corresponding CFA offsets.
  offset: Optional[int] = INITIAL_OFFSET.get(arch, 0)
  result: CfaOffsets = {}
  for pc, inst in insts:
    while cfa and cfa[0][0] <= pc:
      offset = cfa.popleft()[1]
    result[pc] = offset
  return result

def get_inferred_cfa_offsets(insts: List[Inst]) -> CfaOffsets:
  """ Get CFA offsets for all instructions from static analysis """

  rexprs = get_inst_semantics(arch)
  offset: Optional[int] = INITIAL_OFFSET.get(arch, 0)
  result: CfaOffsets = {}
  for pc, inst in insts:
    # Set current offset for PC, unless branch already set it.
    offset = result.setdefault(pc, offset)

    # Adjust PC and offset based on the current instruction.
    for rexpr, adjust_offset, adjust_pc in rexprs:
      m = rexpr.fullmatch(inst)
      if m:
        new_offset = offset + adjust_offset(m)
        if adjust_pc:
          new_pc = adjust_pc(m)
          if insts[0].pc <= new_pc <= insts[-1].pc:
            if new_pc in result and result[new_pc] != new_offset:
              raise Error(pc, "Inconsistent branch (old={} new={})"
                              .format(result[new_pc], new_offset))
            result[new_pc] = new_offset
        else:
          offset = new_offset
        break  # First matched pattern wins.
  return result

def check_fde(fde: Fde, insts: List[Inst], srcs) -> None:
  """ Compare DWARF offsets to assembly-inferred offsets. Report differences. """

  dwarf_cfa_offsets = get_dwarf_cfa_offsets(insts, fde)
  inferred_cfa_offsets = get_inferred_cfa_offsets(insts)

  for pc, inst in insts:
    if dwarf_cfa_offsets[pc] is not None and dwarf_cfa_offsets[pc] != inferred_cfa_offsets[pc]:
      if pc in srcs:  # Only report if it maps to source code (not padding or literals).
        for inst2 in insts:
          print("0x{:08x} [{}]: dwarf={} inferred={} {}".format(
                    inst2.pc, srcs.get(inst2.pc, ""),
                    str(dwarf_cfa_offsets[inst2.pc]), str(inferred_cfa_offsets[inst2.pc]),
                    inst2.inst.strip()))
        raise Error(pc, "DWARF offset does not match inferred offset")

def check_lib(lib: pathlib.Path) -> int:
  global arch
  arch = get_arch(lib)

  assert lib.exists()
  fdes = get_fde(lib)
  asms = collections.deque(get_asm(lib))
  srcs = {src.pc: src.file + ":" + src.line for src in get_src(lib)}
  seen = set()  # Used to verify the we have covered all assembly source lines.
  fail = 0
  assert srcs, "No sources found"

  for fde in fdes:
    if fde.pc not in srcs:
      continue  # Ignore if it is not hand-written assembly.

    # Assembly instructions (one FDE can cover several assembly chunks).
    all_insts, name = [], ""
    while asms and asms[0].pc < fde.end:
      asm = asms.popleft()
      if asm.pc < fde.pc:
        continue
      insts = get_instructions(asm)
      if any(asm.name.startswith(n) and arch in a for n, a in IGNORE.items()):
        seen.update([inst.pc for inst in insts])
        continue
      all_insts.extend(insts)
      name = name or asm.name
    if not all_insts:
      continue  # No assembly

    # Compare DWARF data to assembly instructions
    try:
      check_fde(fde, all_insts, srcs)
    except Error as e:
      print("0x{:08x} [{}]: ERROR in {}: {}\n"
            .format(e.address, srcs.get(e.address, ""), name, e.message))
      fail += 1
    seen.update([inst.pc for inst in all_insts])
  for pc in sorted(set(srcs.keys()) - seen):
    print("ERROR: Missing CFI for {:08x}: {}".format(pc, srcs[pc]))
    fail += 1
  return fail


def main(argv):
  """ Check libraries provided on the command line, or use the default build output """

  libs = args.srcs
  if not libs:
    apex = Path(os.environ["OUT"]) / "symbols/apex/"
    libs = list(apex.glob("**/libart.so"))
    assert libs, "Can not find any libart.so in " + str(apex)
  for lib in libs:
    fail = check_lib(pathlib.Path(lib))
    if fail > 0:
      print(fail, "ERROR(s) in", str(lib))
      sys.exit(1)
  if args.out:
    args.out.write_bytes(b"")

if __name__ == "__main__":
  parser = ArgumentParser(description=__doc__)
  parser.add_argument("--out", type=Path, help="Output (will just generate empty file)")
  parser.add_argument("--dwarfdump", type=Path, default="llvm-dwarfdump")
  parser.add_argument("--objdump", type=Path, default="llvm-objdump")
  parser.add_argument("srcs", nargs="*", type=Path)
  args = parser.parse_args()

  main(sys.argv)
