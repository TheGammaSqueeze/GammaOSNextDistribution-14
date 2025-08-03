/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "chre/platform/shared/nanoapp_loader.h"

namespace chre {

bool NanoappLoader::relocateTable(DynamicHeader *dyn, int tag) {
  bool success = false;
  if (dyn == nullptr) {
    return false;
  }

  switch (tag) {
    case DT_REL: {
      if (getDynEntry(dyn, tag) == 0) {
        LOGE("ARM Elf binaries must have DT_REL dynamic entry");
        break;
      }

      ElfRel *reloc =
          reinterpret_cast<ElfRel *>(mBinary + getDynEntry(dyn, DT_REL));
      size_t relocSize = getDynEntry(dyn, DT_RELSZ);
      size_t nRelocs = relocSize / sizeof(ElfRel);
      LOGV("Relocation %zu entries in DT_REL table", nRelocs);

      bool resolvedAllSymbols = true;
      size_t i;
      for (i = 0; i < nRelocs; ++i) {
        ElfRel *curr = &reloc[i];
        int relocType = ELFW_R_TYPE(curr->r_info);
        ElfAddr *addr = reinterpret_cast<ElfAddr *>(mMapping + curr->r_offset);

        switch (relocType) {
          case R_ARM_RELATIVE:
            LOGV("Resolving ARM_RELATIVE at offset %lx",
                 static_cast<long unsigned int>(curr->r_offset));
            // TODO(b/155512914): When we move to DRAM allocations, we need to
            // check if the above address is in a Read-Only section of memory,
            // and give it temporary write permission if that is the case.
            *addr += reinterpret_cast<uintptr_t>(mMapping);
            break;

          case R_ARM_ABS32: {
            LOGV("Resolving ARM_ABS32 at offset %lx",
                 static_cast<long unsigned int>(curr->r_offset));
            size_t posInSymbolTable = ELFW_R_SYM(curr->r_info);
            auto *dynamicSymbolTable =
                reinterpret_cast<ElfSym *>(mDynamicSymbolTablePtr);
            ElfSym *sym = &dynamicSymbolTable[posInSymbolTable];
            *addr = reinterpret_cast<uintptr_t>(mMapping + sym->st_value);
            break;
          }

          case R_ARM_GLOB_DAT: {
            LOGV("Resolving type ARM_GLOB_DAT at offset %lx",
                 static_cast<long unsigned int>(curr->r_offset));
            size_t posInSymbolTable = ELFW_R_SYM(curr->r_info);
            void *resolved = resolveData(posInSymbolTable);
            if (resolved == nullptr) {
              LOGV("Failed to resolve global symbol(%zu) at offset 0x%lx", i,
                   static_cast<long unsigned int>(curr->r_offset));
              resolvedAllSymbols = false;
            }
            // TODO(b/155512914): When we move to DRAM allocations, we need to
            // check if the above address is in a Read-Only section of memory,
            // and give it temporary write permission if that is the case.
            *addr = reinterpret_cast<ElfAddr>(resolved);
            break;
          }

          case R_ARM_COPY:
            LOGE("R_ARM_COPY is an invalid relocation for shared libraries");
            break;
          default:
            LOGE("Invalid relocation type %u", relocType);
            break;
        }
      }

      if (!resolvedAllSymbols) {
        LOGE("Unable to resolve all symbols in the binary");
      } else {
        success = true;
      }
      break;
    }
    case DT_RELA:
      if (getDynEntry(dyn, tag) != 0) {
        LOGE("ARM Elf binaries with a DT_RELA dynamic entry are unsupported");
      } else {
        // Not required for ARM
        success = true;
      }
      break;
    default:
      LOGE("Unsupported table tag %d", tag);
  }

  return success;
}

bool NanoappLoader::resolveGot() {
  ElfAddr *addr;
  ElfRel *reloc = reinterpret_cast<ElfRel *>(
      mMapping + getDynEntry(getDynamicHeader(), DT_JMPREL));
  size_t relocSize = getDynEntry(getDynamicHeader(), DT_PLTRELSZ);
  size_t nRelocs = relocSize / sizeof(ElfRel);
  LOGV("Resolving GOT with %zu relocations", nRelocs);

  for (size_t i = 0; i < nRelocs; ++i) {
    ElfRel *curr = &reloc[i];
    int relocType = ELFW_R_TYPE(curr->r_info);

    switch (relocType) {
      case R_ARM_JUMP_SLOT: {
        LOGV("Resolving ARM_JUMP_SLOT at offset %lx",
             static_cast<long unsigned int>(curr->r_offset));
        addr = reinterpret_cast<ElfAddr *>(mMapping + curr->r_offset);
        size_t posInSymbolTable = ELFW_R_SYM(curr->r_info);
        void *resolved = resolveData(posInSymbolTable);
        if (resolved == nullptr) {
          LOGV("Failed to resolve symbol(%zu) at offset 0x%x", i,
               curr->r_offset);
          return false;
        }
        *addr = reinterpret_cast<ElfAddr>(resolved);
        break;
      }

      default:
        LOGE("Unsupported relocation type: %u for symbol %s", relocType,
             getDataName(getDynamicSymbol(ELFW_R_SYM(curr->r_info))));
        return false;
    }
  }
  return true;
}

}  // namespace chre
