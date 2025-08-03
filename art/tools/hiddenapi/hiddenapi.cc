/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <openssl/sha.h>

#include <fstream>
#include <iostream>
#include <iterator>
#include <map>
#include <set>
#include <string>
#include <string_view>
#include <vector>

#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "base/bit_utils.h"
#include "base/hiddenapi_flags.h"
#include "base/mem_map.h"
#include "base/os.h"
#include "base/stl_util.h"
#include "base/string_view_cpp20.h"
#include "base/unix_file/fd_file.h"
#include "dex/art_dex_file_loader.h"
#include "dex/class_accessor-inl.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_structs.h"

namespace art {
namespace hiddenapi {

const char kErrorHelp[] = "\nSee go/hiddenapi-error for help.";

static int original_argc;
static char** original_argv;

static std::string CommandLine() {
  std::vector<std::string> command;
  command.reserve(original_argc);
  for (int i = 0; i < original_argc; ++i) {
    command.push_back(original_argv[i]);
  }
  return android::base::Join(command, ' ');
}

static void UsageErrorV(const char* fmt, va_list ap) {
  std::string error;
  android::base::StringAppendV(&error, fmt, ap);
  LOG(ERROR) << error;
}

static void UsageError(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  UsageErrorV(fmt, ap);
  va_end(ap);
}

NO_RETURN static void Usage(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  UsageErrorV(fmt, ap);
  va_end(ap);

  UsageError("Command: %s", CommandLine().c_str());
  UsageError("Usage: hiddenapi [command_name] [options]...");
  UsageError("");
  UsageError("  Command \"encode\": encode API list membership in boot dex files");
  UsageError("    --input-dex=<filename>: dex file which belongs to boot class path");
  UsageError("    --output-dex=<filename>: file to write encoded dex into");
  UsageError("        input and output dex files are paired in order of appearance");
  UsageError("");
  UsageError("    --api-flags=<filename>:");
  UsageError("        CSV file with signatures of methods/fields and their respective flags");
  UsageError("");
  UsageError("    --max-hiddenapi-level=<max-target-*>:");
  UsageError("        the maximum hidden api level for APIs. If an API was originally restricted");
  UsageError("        to a newer sdk, turn it into a regular unsupported API instead.");
  UsageError("        instead. The full list of valid values is in hiddenapi_flags.h");
  UsageError("");
  UsageError("    --no-force-assign-all:");
  UsageError("        Disable check that all dex entries have been assigned a flag");
  UsageError("");
  UsageError("  Command \"list\": dump lists of public and private API");
  UsageError("    --dependency-stub-dex=<filename>: dex file containing API stubs provided");
  UsageError("      by other parts of the bootclasspath. These are used to resolve");
  UsageError("      dependencies in dex files specified in --boot-dex but do not appear in");
  UsageError("      the output");
  UsageError("    --boot-dex=<filename>: dex file which belongs to boot class path");
  UsageError("    --public-stub-classpath=<filenames>:");
  UsageError("    --system-stub-classpath=<filenames>:");
  UsageError("    --test-stub-classpath=<filenames>:");
  UsageError("    --core-platform-stub-classpath=<filenames>:");
  UsageError("        colon-separated list of dex/apk files which form API stubs of boot");
  UsageError("        classpath. Multiple classpaths can be specified");
  UsageError("");
  UsageError("    --out-api-flags=<filename>: output file for a CSV file with API flags");
  UsageError("    --fragment: the input is only a fragment of the whole bootclasspath and may");
  UsageError("      not include a complete set of classes. That requires the tool to ignore");
  UsageError("      missing classes and members. Specify --verbose to see the warnings.");
  UsageError("    --verbose: output all warnings, even when --fragment is specified.");
  UsageError("");

  exit(EXIT_FAILURE);
}

template<typename E>
static bool Contains(const std::vector<E>& vec, const E& elem) {
  return std::find(vec.begin(), vec.end(), elem) != vec.end();
}

class DexClass : public ClassAccessor {
 public:
  explicit DexClass(const ClassAccessor& accessor) : ClassAccessor(accessor) {}

  const uint8_t* GetData() const { return dex_file_.GetClassData(GetClassDef()); }

  const dex::TypeIndex GetSuperclassIndex() const { return GetClassDef().superclass_idx_; }

  bool HasSuperclass() const { return dex_file_.IsTypeIndexValid(GetSuperclassIndex()); }

  std::string_view GetSuperclassDescriptor() const {
    return HasSuperclass() ? dex_file_.StringByTypeIdx(GetSuperclassIndex()) : "";
  }

  std::set<std::string_view> GetInterfaceDescriptors() const {
    std::set<std::string_view> list;
    const dex::TypeList* ifaces = dex_file_.GetInterfacesList(GetClassDef());
    for (uint32_t i = 0; ifaces != nullptr && i < ifaces->Size(); ++i) {
      list.insert(dex_file_.StringByTypeIdx(ifaces->GetTypeItem(i).type_idx_));
    }
    return list;
  }

  inline bool IsPublic() const { return HasAccessFlags(kAccPublic); }
  inline bool IsInterface() const { return HasAccessFlags(kAccInterface); }

  inline bool Equals(const DexClass& other) const {
    bool equals = strcmp(GetDescriptor(), other.GetDescriptor()) == 0;

    if (equals) {
      LOG(FATAL) << "Class duplication: " << GetDescriptor() << " in " << dex_file_.GetLocation()
          << " and " << other.dex_file_.GetLocation();
    }

    return equals;
  }

 private:
  uint32_t GetAccessFlags() const { return GetClassDef().access_flags_; }
  bool HasAccessFlags(uint32_t mask) const { return (GetAccessFlags() & mask) == mask; }

  static std::string JoinStringSet(const std::set<std::string_view>& s) {
    return "{" + ::android::base::Join(std::vector<std::string>(s.begin(), s.end()), ",") + "}";
  }
};

class DexMember {
 public:
  DexMember(const DexClass& klass, const ClassAccessor::Field& item)
      : klass_(klass), item_(item), is_method_(false) {
    DCHECK_EQ(GetFieldId().class_idx_, klass.GetClassIdx());
  }

  DexMember(const DexClass& klass, const ClassAccessor::Method& item)
      : klass_(klass), item_(item), is_method_(true) {
    DCHECK_EQ(GetMethodId().class_idx_, klass.GetClassIdx());
  }

  inline const DexClass& GetDeclaringClass() const { return klass_; }

  inline bool IsMethod() const { return is_method_; }
  inline bool IsVirtualMethod() const { return IsMethod() && !GetMethod().IsStaticOrDirect(); }
  inline bool IsConstructor() const { return IsMethod() && HasAccessFlags(kAccConstructor); }

  inline bool IsPublicOrProtected() const {
    return HasAccessFlags(kAccPublic) || HasAccessFlags(kAccProtected);
  }

  // Constructs a string with a unique signature of this class member.
  std::string GetApiEntry() const {
    std::stringstream ss;
    ss << klass_.GetDescriptor() << "->" << GetName() << (IsMethod() ? "" : ":")
       << GetSignature();
    return ss.str();
  }

  inline bool operator==(const DexMember& other) const {
    // These need to match if they should resolve to one another.
    bool equals = IsMethod() == other.IsMethod() &&
                  GetName() == other.GetName() &&
                  GetSignature() == other.GetSignature();

    // Soundness check that they do match.
    if (equals) {
      CHECK_EQ(IsVirtualMethod(), other.IsVirtualMethod());
    }

    return equals;
  }

 private:
  inline uint32_t GetAccessFlags() const { return item_.GetAccessFlags(); }
  inline bool HasAccessFlags(uint32_t mask) const { return (GetAccessFlags() & mask) == mask; }

  inline std::string_view GetName() const {
    return IsMethod() ? item_.GetDexFile().GetMethodName(GetMethodId())
                      : item_.GetDexFile().GetFieldName(GetFieldId());
  }

  inline std::string GetSignature() const {
    return IsMethod() ? item_.GetDexFile().GetMethodSignature(GetMethodId()).ToString()
                      : item_.GetDexFile().GetFieldTypeDescriptor(GetFieldId());
  }

  inline const ClassAccessor::Method& GetMethod() const {
    DCHECK(IsMethod());
    return down_cast<const ClassAccessor::Method&>(item_);
  }

  inline const dex::MethodId& GetMethodId() const {
    DCHECK(IsMethod());
    return item_.GetDexFile().GetMethodId(item_.GetIndex());
  }

  inline const dex::FieldId& GetFieldId() const {
    DCHECK(!IsMethod());
    return item_.GetDexFile().GetFieldId(item_.GetIndex());
  }

  const DexClass& klass_;
  const ClassAccessor::BaseItem& item_;
  const bool is_method_;
};

class ClassPath final {
 public:
  ClassPath(const std::vector<std::string>& dex_paths, bool ignore_empty) {
    OpenDexFiles(dex_paths, ignore_empty);
  }

  template <typename Fn>
  void ForEachDexClass(const DexFile* dex_file, Fn fn) {
    for (ClassAccessor accessor : dex_file->GetClasses()) {
      fn(DexClass(accessor));
    }
  }

  template<typename Fn>
  void ForEachDexClass(Fn fn) {
    for (auto& dex_file : dex_files_) {
      for (ClassAccessor accessor : dex_file->GetClasses()) {
        fn(DexClass(accessor));
      }
    }
  }

  template<typename Fn>
  void ForEachDexMember(Fn fn) {
    ForEachDexClass([&fn](const DexClass& klass) {
      for (const ClassAccessor::Field& field : klass.GetFields()) {
        fn(DexMember(klass, field));
      }
      for (const ClassAccessor::Method& method : klass.GetMethods()) {
        fn(DexMember(klass, method));
      }
    });
  }

  std::vector<const DexFile*> GetDexFiles() const {
    return MakeNonOwningPointerVector(dex_files_);
  }

  void UpdateDexChecksums() {
    for (auto& dex_file : dex_files_) {
      // Obtain a writeable pointer to the dex header.
      DexFile::Header* header = const_cast<DexFile::Header*>(&dex_file->GetHeader());
      // Recalculate checksum and overwrite the value in the header.
      header->checksum_ = dex_file->CalculateChecksum();
    }
  }

 private:
  void OpenDexFiles(const std::vector<std::string>& dex_paths, bool ignore_empty) {
    std::string error_msg;

    for (const std::string& filename : dex_paths) {
      DexFileLoader dex_file_loader(filename);
      DexFileLoaderErrorCode error_code;
      bool success = dex_file_loader.Open(/* verify= */ true,
                                          /* verify_checksum= */ true,
                                          /*allow_no_dex_files=*/ ignore_empty,
                                          &error_code,
                                          &error_msg,
                                          &dex_files_);
      CHECK(success) << "Open failed for '" << filename << "' " << error_msg;
    }
  }

  // Opened dex files. Note that these are opened as `const` but may be written into.
  std::vector<std::unique_ptr<const DexFile>> dex_files_;
};

class HierarchyClass final {
 public:
  HierarchyClass() {}

  void AddDexClass(const DexClass& klass) {
    CHECK(dex_classes_.empty() || klass.Equals(dex_classes_.front()));
    dex_classes_.push_back(klass);
  }

  void AddExtends(HierarchyClass& parent) {
    CHECK(!Contains(extends_, &parent));
    CHECK(!Contains(parent.extended_by_, this));
    extends_.push_back(&parent);
    parent.extended_by_.push_back(this);
  }

  const DexClass& GetOneDexClass() const {
    CHECK(!dex_classes_.empty());
    return dex_classes_.front();
  }

  // See comment on Hierarchy::ForEachResolvableMember.
  template<typename Fn>
  bool ForEachResolvableMember(const DexMember& other, Fn fn) {
    std::vector<HierarchyClass*> visited;
    return ForEachResolvableMember_Impl(other, fn, true, true, visited);
  }

  // Returns true if this class contains at least one member matching `other`.
  bool HasMatchingMember(const DexMember& other) {
    return ForEachMatchingMember(other, [](const DexMember&) { return true; });
  }

  // Recursively iterates over all subclasses of this class and invokes `fn`
  // on each one. If `fn` returns false for a particular subclass, exploring its
  // subclasses is skipped.
  template<typename Fn>
  void ForEachSubClass(Fn fn) {
    for (HierarchyClass* subclass : extended_by_) {
      if (fn(subclass)) {
        subclass->ForEachSubClass(fn);
      }
    }
  }

 private:
  template<typename Fn>
  bool ForEachResolvableMember_Impl(const DexMember& other,
                                    Fn fn,
                                    bool allow_explore_up,
                                    bool allow_explore_down,
                                    std::vector<HierarchyClass*> visited) {
    if (std::find(visited.begin(), visited.end(), this) == visited.end()) {
      visited.push_back(this);
    } else {
      return false;
    }

    // First try to find a member matching `other` in this class.
    bool found = ForEachMatchingMember(other, fn);

    // If not found, see if it is inherited from parents. Note that this will not
    // revisit parents already in `visited`.
    if (!found && allow_explore_up) {
      for (HierarchyClass* superclass : extends_) {
        found |= superclass->ForEachResolvableMember_Impl(
            other,
            fn,
            /* allow_explore_up */ true,
            /* allow_explore_down */ false,
            visited);
      }
    }

    // If this is a virtual method, continue exploring into subclasses so as to visit
    // all overriding methods. Allow subclasses to explore their superclasses if this
    // is an interface. This is needed to find implementations of this interface's
    // methods inherited from superclasses (b/122551864).
    if (allow_explore_down && other.IsVirtualMethod()) {
      for (HierarchyClass* subclass : extended_by_) {
        subclass->ForEachResolvableMember_Impl(
            other,
            fn,
            /* allow_explore_up */ GetOneDexClass().IsInterface(),
            /* allow_explore_down */ true,
            visited);
      }
    }

    return found;
  }

  template<typename Fn>
  bool ForEachMatchingMember(const DexMember& other, Fn fn) {
    bool found = false;
    auto compare_member = [&](const DexMember& member) {
      // TODO(dbrazdil): Check whether class of `other` can access `member`.
      if (member == other) {
        found = true;
        fn(member);
      }
    };
    for (const DexClass& dex_class : dex_classes_) {
      for (const ClassAccessor::Field& field : dex_class.GetFields()) {
        compare_member(DexMember(dex_class, field));
      }
      for (const ClassAccessor::Method& method : dex_class.GetMethods()) {
        compare_member(DexMember(dex_class, method));
      }
    }
    return found;
  }

  // DexClass entries of this class found across all the provided dex files.
  std::vector<DexClass> dex_classes_;

  // Classes which this class inherits, or interfaces which it implements.
  std::vector<HierarchyClass*> extends_;

  // Classes which inherit from this class.
  std::vector<HierarchyClass*> extended_by_;
};

class Hierarchy final {
 public:
  Hierarchy(ClassPath& classpath, bool fragment, bool verbose) : classpath_(classpath) {
    BuildClassHierarchy(fragment, verbose);
  }

  // Perform an operation for each member of the hierarchy which could potentially
  // be the result of method/field resolution of `other`.
  // The function `fn` should accept a DexMember reference and return true if
  // the member was changed. This drives a performance optimization which only
  // visits overriding members the first time the overridden member is visited.
  // Returns true if at least one resolvable member was found.
  template<typename Fn>
  bool ForEachResolvableMember(const DexMember& other, Fn fn) {
    HierarchyClass* klass = FindClass(other.GetDeclaringClass().GetDescriptor());
    return (klass != nullptr) && klass->ForEachResolvableMember(other, fn);
  }

  // Returns true if `member`, which belongs to this classpath, is visible to
  // code in child class loaders.
  bool IsMemberVisible(const DexMember& member) {
    if (!member.IsPublicOrProtected()) {
      // Member is private or package-private. Cannot be visible.
      return false;
    } else if (member.GetDeclaringClass().IsPublic()) {
      // Member is public or protected, and class is public. It must be visible.
      return true;
    } else if (member.IsConstructor()) {
      // Member is public or protected constructor and class is not public.
      // Must be hidden because it cannot be implicitly exposed by a subclass.
      return false;
    } else {
      // Member is public or protected method, but class is not public. Check if
      // it is exposed through a public subclass.
      // Example code (`foo` exposed by ClassB):
      //   class ClassA { public void foo() { ... } }
      //   public class ClassB extends ClassA {}
      HierarchyClass* klass = FindClass(member.GetDeclaringClass().GetDescriptor());
      CHECK(klass != nullptr);
      bool visible = false;
      klass->ForEachSubClass([&visible, &member](HierarchyClass* subclass) {
        if (subclass->HasMatchingMember(member)) {
          // There is a member which matches `member` in `subclass`, either
          // a virtual method overriding `member` or a field overshadowing
          // `member`. In either case, `member` remains hidden.
          CHECK(member.IsVirtualMethod() || !member.IsMethod());
          return false;  // do not explore deeper
        } else if (subclass->GetOneDexClass().IsPublic()) {
          // `subclass` inherits and exposes `member`.
          visible = true;
          return false;  // do not explore deeper
        } else {
          // `subclass` inherits `member` but does not expose it.
          return true;   // explore deeper
        }
      });
      return visible;
    }
  }

 private:
  HierarchyClass* FindClass(const std::string_view& descriptor) {
    auto it = classes_.find(descriptor);
    if (it == classes_.end()) {
      return nullptr;
    } else {
      return &it->second;
    }
  }

  void BuildClassHierarchy(bool fragment, bool verbose) {
    // Create one HierarchyClass entry in `classes_` per class descriptor
    // and add all DexClass objects with the same descriptor to that entry.
    classpath_.ForEachDexClass([this](const DexClass& klass) {
      classes_[klass.GetDescriptor()].AddDexClass(klass);
    });

    // Connect each HierarchyClass to its successors and predecessors.
    for (auto& entry : classes_) {
      HierarchyClass& klass = entry.second;
      const DexClass& dex_klass = klass.GetOneDexClass();

      if (!dex_klass.HasSuperclass()) {
        CHECK(dex_klass.GetInterfaceDescriptors().empty())
            << "java/lang/Object should not implement any interfaces";
        continue;
      }

      auto add_extends = [&](const std::string_view& extends_desc) {
        HierarchyClass* extends = FindClass(extends_desc);
        if (extends != nullptr) {
          klass.AddExtends(*extends);
        } else if (!fragment || verbose) {
          auto severity = verbose ? ::android::base::WARNING : ::android::base::FATAL;
          LOG(severity)
              << "Superclass/interface " << extends_desc
              << " of class " << dex_klass.GetDescriptor() << " from dex file \""
              << dex_klass.GetDexFile().GetLocation() << "\" was not found. "
              << "Either it is missing or it appears later in the classpath spec.";
        }
      };

      add_extends(dex_klass.GetSuperclassDescriptor());
      for (const std::string_view& iface_desc : dex_klass.GetInterfaceDescriptors()) {
        add_extends(iface_desc);
      }
    }
  }

  ClassPath& classpath_;
  std::map<std::string_view, HierarchyClass> classes_;
};

// Builder of dex section containing hiddenapi flags.
class HiddenapiClassDataBuilder final {
 public:
  explicit HiddenapiClassDataBuilder(const DexFile& dex_file)
      : num_classdefs_(dex_file.NumClassDefs()),
        next_class_def_idx_(0u),
        class_def_has_non_zero_flags_(false),
        dex_file_has_non_zero_flags_(false),
        data_(sizeof(uint32_t) * (num_classdefs_ + 1), 0u) {
    *GetSizeField() = GetCurrentDataSize();
  }

  // Notify the builder that new flags for the next class def
  // will be written now. The builder records the current offset
  // into the header.
  void BeginClassDef(uint32_t idx) {
    CHECK_EQ(next_class_def_idx_, idx);
    CHECK_LT(idx, num_classdefs_);
    GetOffsetArray()[idx] = GetCurrentDataSize();
    class_def_has_non_zero_flags_ = false;
  }

  // Notify the builder that all flags for this class def have been
  // written. The builder updates the total size of the data struct
  // and may set offset for class def in header to zero if no data
  // has been written.
  void EndClassDef(uint32_t idx) {
    CHECK_EQ(next_class_def_idx_, idx);
    CHECK_LT(idx, num_classdefs_);

    ++next_class_def_idx_;

    if (!class_def_has_non_zero_flags_) {
      // No need to store flags for this class. Remove the written flags
      // and set offset in header to zero.
      data_.resize(GetOffsetArray()[idx]);
      GetOffsetArray()[idx] = 0u;
    }

    dex_file_has_non_zero_flags_ |= class_def_has_non_zero_flags_;

    if (idx == num_classdefs_ - 1) {
      if (dex_file_has_non_zero_flags_) {
        // This was the last class def and we have generated non-zero hiddenapi
        // flags. Update total size in the header.
        *GetSizeField() = GetCurrentDataSize();
      } else {
        // This was the last class def and we have not generated any non-zero
        // hiddenapi flags. Clear all the data.
        data_.clear();
      }
    }
  }

  // Append flags at the end of the data struct. This should be called
  // between BeginClassDef and EndClassDef in the order of appearance of
  // fields/methods in the class data stream.
  void WriteFlags(const ApiList& flags) {
    uint32_t dex_flags = flags.GetDexFlags();
    EncodeUnsignedLeb128(&data_, dex_flags);
    class_def_has_non_zero_flags_ |= (dex_flags != 0u);
  }

  // Return backing data, assuming that all flags have been written.
  const std::vector<uint8_t>& GetData() const {
    CHECK_EQ(next_class_def_idx_, num_classdefs_) << "Incomplete data";
    return data_;
  }

 private:
  // Returns pointer to the size field in the header of this dex section.
  uint32_t* GetSizeField() {
    // Assume malloc() aligns allocated memory to at least uint32_t.
    CHECK(IsAligned<sizeof(uint32_t)>(data_.data()));
    return reinterpret_cast<uint32_t*>(data_.data());
  }

  // Returns pointer to array of offsets (indexed by class def indices) in the
  // header of this dex section.
  uint32_t* GetOffsetArray() { return &GetSizeField()[1]; }
  uint32_t GetCurrentDataSize() const { return data_.size(); }

  // Number of class defs in this dex file.
  const uint32_t num_classdefs_;

  // Next expected class def index.
  uint32_t next_class_def_idx_;

  // Whether non-zero flags have been encountered for this class def.
  bool class_def_has_non_zero_flags_;

  // Whether any non-zero flags have been encountered for this dex file.
  bool dex_file_has_non_zero_flags_;

  // Vector containing the data of the built data structure.
  std::vector<uint8_t> data_;
};

// Edits a dex file, inserting a new HiddenapiClassData section.
class DexFileEditor final {
 public:
  // Add dex file to copy to output (possibly several files for multi-dex).
  void Add(const DexFile* dex, const std::vector<uint8_t>&& hiddenapi_data) {
    // We do not support non-standard dex encodings, e.g. compact dex.
    CHECK(dex->IsStandardDexFile());
    inputs_.emplace_back(dex, std::move(hiddenapi_data));
  }

  // Writes the edited dex file into a file.
  void WriteTo(const std::string& path) {
    CHECK_GT(inputs_.size(), 0u);
    std::vector<uint8_t> output;

    // Copy the old dex files into the backing data vector.
    std::vector<size_t> header_offset;
    for (size_t i = 0; i < inputs_.size(); i++) {
      const DexFile* dex = inputs_[i].first;
      header_offset.push_back(output.size());
      std::copy(dex->Begin(), dex->End(), std::back_inserter(output));

      // Clear the old map list (make it into padding).
      const dex::MapList* map = dex->GetMapList();
      size_t map_off = dex->GetHeader().map_off_;
      size_t map_size = sizeof(map->size_) + map->size_ * sizeof(map->list_[0]);
      CHECK_LE(map_off, output.size()) << "Map list past the end of file";
      CHECK_EQ(map_size, output.size() - map_off) << "Map list expected at the end of file";
      std::fill_n(output.data() + map_off, map_size, 0);
    }

    // Append the hidden api data into the backing data vector.
    std::vector<size_t> hiddenapi_offset;
    for (size_t i = 0; i < inputs_.size(); i++) {
      const std::vector<uint8_t>& hiddenapi_data = inputs_[i].second;
      output.resize(RoundUp(output.size(), kHiddenapiClassDataAlignment));  // Align.
      hiddenapi_offset.push_back(output.size());
      std::copy(hiddenapi_data.begin(), hiddenapi_data.end(), std::back_inserter(output));
    }

    // Append modified map lists.
    std::vector<uint32_t> map_list_offset;
    for (size_t i = 0; i < inputs_.size(); i++) {
      output.resize(RoundUp(output.size(), kMapListAlignment));  // Align.

      const DexFile* dex = inputs_[i].first;
      const dex::MapList* map = dex->GetMapList();
      std::vector<dex::MapItem> items(map->list_, map->list_ + map->size_);

      // Check the header entry.
      CHECK(!items.empty());
      CHECK_EQ(items[0].type_, DexFile::kDexTypeHeaderItem);
      CHECK_EQ(items[0].offset_, header_offset[i]);

      // Check and remove the old map list entry (it does not have to be last).
      auto is_map_list = [](auto it) { return it.type_ == DexFile::kDexTypeMapList; };
      auto it = std::find_if(items.begin(), items.end(), is_map_list);
      CHECK(it != items.end());
      CHECK_EQ(it->offset_, dex->GetHeader().map_off_);
      items.erase(it);

      // Write new map list.
      if (!inputs_[i].second.empty()) {
        uint32_t payload_offset = hiddenapi_offset[i];
        items.push_back(dex::MapItem{DexFile::kDexTypeHiddenapiClassData, 0, 1u, payload_offset});
      }
      map_list_offset.push_back(output.size());
      items.push_back(dex::MapItem{DexFile::kDexTypeMapList, 0, 1u, map_list_offset.back()});
      uint32_t item_count = items.size();
      Append(&output, &item_count, 1);
      Append(&output, items.data(), items.size());
    }

    // Update headers.
    for (size_t i = 0; i < inputs_.size(); i++) {
      uint8_t* begin = output.data() + header_offset[i];
      auto* header = reinterpret_cast<DexFile::Header*>(begin);
      header->map_off_ = map_list_offset[i];
      if (i + 1 < inputs_.size()) {
        CHECK_EQ(header->file_size_, header_offset[i + 1] - header_offset[i]);
      } else {
        // Extend last dex file until the end of the file.
        header->data_size_ = output.size() - header->data_off_;
        header->file_size_ = output.size() - header_offset[i];
      }
      header->SetDexContainer(header_offset[i], output.size());
      size_t sha1_start = offsetof(DexFile::Header, file_size_);
      SHA1(begin + sha1_start, header->file_size_ - sha1_start, header->signature_.data());
      header->checksum_ = DexFile::CalculateChecksum(begin, header->file_size_);
    }

    // Write the output file.
    CHECK(!output.empty());
    std::ofstream ofs(path.c_str(), std::ofstream::out | std::ofstream::binary);
    ofs.write(reinterpret_cast<const char*>(output.data()), output.size());
    ofs.flush();
    CHECK(ofs.good());
    ofs.close();

    ReloadDex(path.c_str());
  }

 private:
  static constexpr size_t kMapListAlignment = 4u;
  static constexpr size_t kHiddenapiClassDataAlignment = 4u;

  void ReloadDex(const char* filename) {
    std::string error_msg;
    ArtDexFileLoader loader(filename);
    std::vector<std::unique_ptr<const DexFile>> dex_files;
    bool ok = loader.Open(/*verify*/ true,
                          /*verify_checksum*/ true,
                          &error_msg,
                          &dex_files);
    CHECK(ok) << "Failed to load edited dex file: " << error_msg;
  }

  template <typename T>
  void Append(std::vector<uint8_t>* output, const T* src, size_t len) {
    const uint8_t* ptr = reinterpret_cast<const uint8_t*>(src);
    std::copy(ptr, ptr + len * sizeof(T), std::back_inserter(*output));
  }

  std::vector<std::pair<const DexFile*, const std::vector<uint8_t>>> inputs_;
};

class HiddenApi final {
 public:
  HiddenApi() : force_assign_all_(true) {}

  void Run(int argc, char** argv) {
    switch (ParseArgs(argc, argv)) {
    case Command::kEncode:
      EncodeAccessFlags();
      break;
    case Command::kList:
      ListApi();
      break;
    }
  }

 private:
  enum class Command {
    kEncode,
    kList,
  };

  Command ParseArgs(int argc, char** argv) {
    // Skip over the binary's path.
    argv++;
    argc--;

    if (argc > 0) {
      const char* raw_command = argv[0];
      const std::string_view command(raw_command);
      if (command == "encode") {
        for (int i = 1; i < argc; ++i) {
          const char* raw_option = argv[i];
          const std::string_view option(raw_option);
          if (StartsWith(option, "--input-dex=")) {
            boot_dex_paths_.push_back(std::string(option.substr(strlen("--input-dex="))));
          } else if (StartsWith(option, "--output-dex=")) {
            output_dex_paths_.push_back(std::string(option.substr(strlen("--output-dex="))));
          } else if (StartsWith(option, "--api-flags=")) {
            api_flags_path_ = std::string(option.substr(strlen("--api-flags=")));
          } else if (option == "--no-force-assign-all") {
            force_assign_all_ = false;
          } else if (StartsWith(option, "--max-hiddenapi-level=")) {
            std::string value = std::string(option.substr(strlen("--max-hiddenapi-level=")));
            max_hiddenapi_level_ = ApiList::FromName(value);
          } else {
            Usage("Unknown argument '%s'", raw_option);
          }
        }
        return Command::kEncode;
      } else if (command == "list") {
        for (int i = 1; i < argc; ++i) {
          const char* raw_option = argv[i];
          const std::string_view option(raw_option);
          if (StartsWith(option, "--dependency-stub-dex=")) {
            const std::string path(std::string(option.substr(strlen("--dependency-stub-dex="))));
            dependency_stub_dex_paths_.push_back(path);
            // Add path to the boot dex path to resolve dependencies.
            boot_dex_paths_.push_back(path);
          } else if (StartsWith(option, "--boot-dex=")) {
            boot_dex_paths_.push_back(std::string(option.substr(strlen("--boot-dex="))));
          } else if (StartsWith(option, "--public-stub-classpath=")) {
            stub_classpaths_.push_back(std::make_pair(
                std::string(option.substr(strlen("--public-stub-classpath="))),
                ApiStubs::Kind::kPublicApi));
          } else if (StartsWith(option, "--system-stub-classpath=")) {
            stub_classpaths_.push_back(std::make_pair(
                std::string(option.substr(strlen("--system-stub-classpath="))),
                ApiStubs::Kind::kSystemApi));
          } else if (StartsWith(option, "--test-stub-classpath=")) {
            stub_classpaths_.push_back(std::make_pair(
                std::string(option.substr(strlen("--test-stub-classpath="))),
                ApiStubs::Kind::kTestApi));
          } else if (StartsWith(option, "--core-platform-stub-classpath=")) {
            stub_classpaths_.push_back(std::make_pair(
                std::string(option.substr(strlen("--core-platform-stub-classpath="))),
                ApiStubs::Kind::kCorePlatformApi));
          } else if (StartsWith(option, "--out-api-flags=")) {
            api_flags_path_ = std::string(option.substr(strlen("--out-api-flags=")));
          } else if (option == "--fragment") {
            fragment_ = true;
          } else if (option == "--verbose") {
            verbose_ = true;
          } else {
            Usage("Unknown argument '%s'", raw_option);
          }
        }
        return Command::kList;
      } else {
        Usage("Unknown command '%s'", raw_command);
      }
    } else {
      Usage("No command specified");
    }
  }

  void EncodeAccessFlags() {
    if (boot_dex_paths_.empty()) {
      Usage("No input DEX files specified");
    } else if (output_dex_paths_.size() != boot_dex_paths_.size()) {
      Usage("Number of input DEX files does not match number of output DEX files");
    }

    // Load dex signatures.
    std::map<std::string, ApiList> api_list = OpenApiFile(api_flags_path_);

    // Iterate over input dex files and insert HiddenapiClassData sections.
    bool max_hiddenapi_level_error = false;
    for (size_t i = 0; i < boot_dex_paths_.size(); ++i) {
      const std::string& input_path = boot_dex_paths_[i];
      const std::string& output_path = output_dex_paths_[i];

      ClassPath boot_classpath({input_path}, /* ignore_empty= */ false);
      DexFileEditor dex_editor;
      for (const DexFile* input_dex : boot_classpath.GetDexFiles()) {
        HiddenapiClassDataBuilder builder(*input_dex);
        boot_classpath.ForEachDexClass(input_dex, [&](const DexClass& boot_class) {
          builder.BeginClassDef(boot_class.GetClassDefIndex());
          if (boot_class.GetData() != nullptr) {
            auto fn_shared = [&](const DexMember& boot_member) {
              auto signature = boot_member.GetApiEntry();
              auto it = api_list.find(signature);
              bool api_list_found = (it != api_list.end());
              CHECK(!force_assign_all_ || api_list_found)
                  << "Could not find hiddenapi flags for dex entry: " << signature;
              if (api_list_found && it->second.GetIntValue() > max_hiddenapi_level_.GetIntValue()) {
                ApiList without_domain(it->second.GetIntValue());
                LOG(ERROR) << "Hidden api flag " << without_domain << " for member " << signature
                           << " in " << input_path << " exceeds maximum allowable flag "
                           << max_hiddenapi_level_;
                max_hiddenapi_level_error = true;
              } else {
                builder.WriteFlags(api_list_found ? it->second : ApiList::Sdk());
              }
            };
            auto fn_field = [&](const ClassAccessor::Field& boot_field) {
              fn_shared(DexMember(boot_class, boot_field));
            };
            auto fn_method = [&](const ClassAccessor::Method& boot_method) {
              fn_shared(DexMember(boot_class, boot_method));
            };
            boot_class.VisitFieldsAndMethods(fn_field, fn_field, fn_method, fn_method);
          }
          builder.EndClassDef(boot_class.GetClassDefIndex());
        });
        dex_editor.Add(input_dex, std::move(builder.GetData()));
      }
      dex_editor.WriteTo(output_path);
    }

    if (max_hiddenapi_level_error) {
      LOG(ERROR)
          << "Some hidden API flags could not be encoded within the dex file as"
          << " they exceed the maximum allowable level of " << max_hiddenapi_level_
          << " which is determined by the min_sdk_version of the source Java library.\n"
          << "The affected DEX members are reported in previous error messages.\n"
          << "The unsupported flags are being generated from the maxTargetSdk property"
          << " of the member's @UnsupportedAppUsage annotation.\n"
          << "See b/172453495 and/or contact art-team@ or compat-team@ for more info.\n";
      exit(EXIT_FAILURE);
    }
  }

  std::map<std::string, ApiList> OpenApiFile(const std::string& path) {
    CHECK(!path.empty());
    std::ifstream api_file(path, std::ifstream::in);
    CHECK(!api_file.fail()) << "Unable to open file '" << path << "' " << strerror(errno);

    std::map<std::string, ApiList> api_flag_map;

    size_t line_number = 1;
    bool errors = false;
    for (std::string line; std::getline(api_file, line); line_number++) {
      // Every line contains a comma separated list with the signature as the
      // first element and the api flags as the rest
      std::vector<std::string> values = android::base::Split(line, ",");
      CHECK_GT(values.size(), 1u) << path << ":" << line_number
          << ": No flags found: " << line << kErrorHelp;

      const std::string& signature = values[0];

      CHECK(api_flag_map.find(signature) == api_flag_map.end()) << path << ":" << line_number
          << ": Duplicate entry: " << signature << kErrorHelp;

      ApiList membership;

      std::vector<std::string>::iterator apiListBegin = values.begin() + 1;
      std::vector<std::string>::iterator apiListEnd = values.end();
      bool success = ApiList::FromNames(apiListBegin, apiListEnd, &membership);
      if (!success) {
        LOG(ERROR) << path << ":" << line_number
            << ": Some flags were not recognized: " << line << kErrorHelp;
        errors = true;
        continue;
      } else if (!membership.IsValid()) {
        LOG(ERROR) << path << ":" << line_number
            << ": Invalid combination of flags: " << line << kErrorHelp;
        errors = true;
        continue;
      }

      api_flag_map.emplace(signature, membership);
    }
    CHECK(!errors) << "Errors encountered while parsing file " << path;

    api_file.close();
    return api_flag_map;
  }

  // A special flag added to the set of flags in boot_members to indicate that
  // it should be excluded from the output.
  static constexpr std::string_view kExcludeFromOutput{"exclude-from-output"};

  void ListApi() {
    if (boot_dex_paths_.empty()) {
      Usage("No boot DEX files specified");
    } else if (stub_classpaths_.empty()) {
      Usage("No stub DEX files specified");
    } else if (api_flags_path_.empty()) {
      Usage("No output path specified");
    }

    // Complete list of boot class path members. The associated boolean states
    // whether it is public (true) or private (false).
    std::map<std::string, std::set<std::string_view>> boot_members;

    // Deduplicate errors before printing them.
    std::set<std::string> unresolved;

    // Open all dex files.
    ClassPath boot_classpath(boot_dex_paths_, /* ignore_empty= */ false);
    Hierarchy boot_hierarchy(boot_classpath, fragment_, verbose_);

    // Mark all boot dex members private.
    boot_classpath.ForEachDexMember([&](const DexMember& boot_member) {
      boot_members[boot_member.GetApiEntry()] = {};
    });

    // Open all dependency API stub dex files.
    ClassPath dependency_classpath(dependency_stub_dex_paths_, /* ignore_empty= */ false);

    // Mark all dependency API stub dex members as coming from the dependency.
    dependency_classpath.ForEachDexMember([&](const DexMember& boot_member) {
      boot_members[boot_member.GetApiEntry()] = {kExcludeFromOutput};
    });

    // Resolve each SDK dex member against the framework and mark it as SDK.
    for (const auto& cp_entry : stub_classpaths_) {
      // Ignore any empty stub jars as it just means that they provide no APIs
      // for the current kind, e.g. framework-sdkextensions does not provide
      // any public APIs.
      ClassPath stub_classpath(android::base::Split(cp_entry.first, ":"), /*ignore_empty=*/true);
      Hierarchy stub_hierarchy(stub_classpath, fragment_, verbose_);
      const ApiStubs::Kind stub_api = cp_entry.second;

      stub_classpath.ForEachDexMember(
          [&](const DexMember& stub_member) {
            if (!stub_hierarchy.IsMemberVisible(stub_member)) {
              // Typically fake constructors and inner-class `this` fields.
              return;
            }
            bool resolved = boot_hierarchy.ForEachResolvableMember(
                stub_member,
                [&](const DexMember& boot_member) {
                  std::string entry = boot_member.GetApiEntry();
                  auto it = boot_members.find(entry);
                  CHECK(it != boot_members.end());
                  it->second.insert(ApiStubs::ToString(stub_api));
                });
            if (!resolved) {
              unresolved.insert(stub_member.GetApiEntry());
            }
          });
    }

    // Print errors.
    if (!fragment_ || verbose_) {
      for (const std::string& str : unresolved) {
        LOG(WARNING) << "unresolved: " << str;
      }
    }

    // Write into public/private API files.
    std::ofstream file_flags(api_flags_path_.c_str());
    for (const auto& entry : boot_members) {
      std::set<std::string_view> flags = entry.second;
      if (flags.empty()) {
        // There are no flags so it cannot be from the dependency stub API dex
        // files so just output the signature.
        file_flags << entry.first << std::endl;
      } else if (flags.find(kExcludeFromOutput) == flags.end()) {
        // The entry has flags and is not from the dependency stub API dex so
        // output it.
        file_flags << entry.first << ",";
        file_flags << android::base::Join(entry.second, ",") << std::endl;
      }
    }
    file_flags.close();
  }

  // Whether to check that all dex entries have been assigned flags.
  // Defaults to true.
  bool force_assign_all_;

  // Paths to DEX files which should be processed.
  std::vector<std::string> boot_dex_paths_;

  // Paths to DEX files containing API stubs provided by other parts of the
  // boot class path which the DEX files in boot_dex_paths depend.
  std::vector<std::string> dependency_stub_dex_paths_;

  // Output paths where modified DEX files should be written.
  std::vector<std::string> output_dex_paths_;

  // Set of public API stub classpaths. Each classpath is formed by a list
  // of DEX/APK files in the order they appear on the classpath.
  std::vector<std::pair<std::string, ApiStubs::Kind>> stub_classpaths_;

  // Path to CSV file containing the list of API members and their flags.
  // This could be both an input and output path.
  std::string api_flags_path_;

  // Maximum allowable hidden API level that can be encoded into the dex file.
  //
  // By default this returns a GetIntValue() that is guaranteed to be bigger than
  // any valid value returned by GetIntValue().
  ApiList max_hiddenapi_level_;

  // Whether the input is only a fragment of the whole bootclasspath and may
  // not include a complete set of classes. That requires the tool to ignore missing
  // classes and members.
  bool fragment_ = false;

  // Whether to output all warnings, even when `fragment_` is set.
  bool verbose_ = false;
};

}  // namespace hiddenapi
}  // namespace art

int main(int argc, char** argv) {
  art::hiddenapi::original_argc = argc;
  art::hiddenapi::original_argv = argv;
  android::base::InitLogging(argv);
  art::MemMap::Init();
  art::hiddenapi::HiddenApi().Run(argc, argv);
  return EXIT_SUCCESS;
}
