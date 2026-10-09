# romscan: shader analysis tools for azahar-nano

Python tools used to study which 3DS fragment shaders can be predicted from a ROM.
Run them from this folder with a decrypted ROM copied in as `sm3dl.3ds` (paths are
hardcoded for Super Mario 3D Land, title 0004000000053F00).

- `romfs.py`: lists the RomFS files of a decrypted NCSD/NCCH image.
- `unpack.py`: Yaz0 + NARC unpacking of every `.szs` into `x/`.
- `code.py`: extracts the ExeFS `.code` (backward LZ) into `x/code.bin`.
- `tevfind.py`, `tevmat.py`: find the PICA combiner stage commands
  (`[sources][0x804F00C0..F8][operands][combiner][color][scale]`) in model data and
  compare them with a recorded shader cache.
- `matregs.py`: registers present around material command blocks.
- `hybridpred.py`: offline test of predicting full shader keys (not viable).
- `mklist.py`: writes the baked shader precompile list read by the core
  (`/data/system/azahar-nano/baked/<title id>.bin`).
- `cache/vkch.py`, `cache/fsan.py`: decode the core's transferable `.vkch` caches.
