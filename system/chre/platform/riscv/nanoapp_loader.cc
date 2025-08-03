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
    case DT_RELA: {
      if (getDynEntry(dyn, tag) == 0) {
        LOGE("RISC-V Elf binaries must have DT_RELA dynamic entry");
        break;
      }

      // The value of the RELA entry in dynamic table is the sh_addr field
      // of ".rela.dyn" section header. We actually need to use the sh_offset
      // which is usually the same, but on occasions can be different.
      SectionHeader *dynamicRelaTablePtr = getSectionHeader(".rela.dyn");
      CHRE_ASSERT(dynamicRelaTablePtr != nullptr);
      ElfRela *reloc =
          reinterpret_cast<ElfRela *>(mBinary + dynamicRelaTablePtr->sh_offset);
      size_t relocSize = dynamicRelaTablePtr->sh_size;
      size_t nRelocs = relocSize / sizeof(ElfRela);
      LOGV("Relocation %zu entries in DT_RELA table", nRelocs);

      for (size_t i = 0; i < nRelocs; ++i) {
        ElfRela *curr = &reloc[i];
        int relocType = ELFW_R_TYPE(curr->r_info);
        ElfAddr *addr = reinterpret_cast<ElfAddr *>(mMapping + curr->r_offset);

        switch (relocType) {
          case R_RISCV_RELATIVE:
            LOGV("Resolving RISCV_RELATIVE at offset %lx",
                 static_cast<long unsigned int>(curr->r_offset));
            // TODO(b/155512914): When we move to DRAM allocations, we need to
            // check if the above address is in a Read-Only section of memory,
            // and give it temporary write permission if that is the case.
            *addr = reinterpret_cast<uintptr_t>(mMapping + curr->r_addend);
            break;

          case R_RISCV_32: {
            LOGV("Resolving RISCV_32 at offset %lx",
                 static_cast<long unsigned int>(curr->r_offset));
            size_t posInSymbolTable = ELFW_R_SYM(curr->r_info);
            auto *dynamicSymbolTable =
                reinterpret_cast<ElfSym *>(mDynamicSymbolTablePtr);
            ElfSym *sym = &dynamicSymbolTable[posInSymbolTable];
            *addr = reinterpret_cast<uintptr_t>(mMapping + sym->st_value);
            break;
          }

          default:
            LOGE("Unsupported relocation type %u", relocType);
            break;
        }
      }
      success = true;
      break;
    }
    case DT_REL:
      // Not required for RISC-V
      success = true;
      break;
    default:
      LOGE("Unsupported table tag %d", tag);
  }

  return success;
}

bool NanoappLoader::resolveGot() {
  ElfAddr *addr;
  ElfRela *reloc = reinterpret_cast<ElfRela *>(
      mMapping + getDynEntry(getDynamicHeader(), DT_JMPREL));
  size_t relocSize = getDynEntry(getDynamicHeader(), DT_PLTRELSZ);
  size_t nRelocs = relocSize / sizeof(ElfRela);
  LOGV("Resolving GOT with %zu relocations", nRelocs);

  for (size_t i = 0; i < nRelocs; ++i) {
    ElfRela *curr = &reloc[i];
    int relocType = ELFW_R_TYPE(curr->r_info);

    switch (relocType) {
      case R_RISCV_JUMP_SLOT: {
        LOGV("Resolving RISCV_JUMP_SLOT at offset %lx, %d",
             static_cast<long unsigned int>(curr->r_offset), curr->r_addend);
        addr = reinterpret_cast<ElfAddr *>(mMapping + curr->r_offset);
        size_t posInSymbolTable = ELFW_R_SYM(curr->r_info);
        void *resolved = resolveData(posInSymbolTable);
        if (resolved == nullptr) {
          LOGV("Failed to resolve symbol(%zu) at offset 0x%x", i,
               curr->r_offset);
          return false;
        }
        *addr = reinterpret_cast<ElfAddr>(resolved) + curr->r_addend;
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
