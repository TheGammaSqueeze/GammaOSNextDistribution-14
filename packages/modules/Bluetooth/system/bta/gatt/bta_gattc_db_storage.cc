/******************************************************************************
 *
 *  Copyright 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_gattc"

#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>
#include <dirent.h>
#include <sys/stat.h>

#include <string>
#include <vector>

#include "bta/gatt/bta_gattc_int.h"
#include "gatt/database.h"
#include "os/log.h"
#include "stack/include/gattdefs.h"
#include "types/bluetooth/uuid.h"

using namespace bluetooth;

using gatt::StoredAttribute;
using std::string;
using std::vector;

#ifdef TARGET_FLOSS
#define GATT_CACHE_PREFIX "/var/lib/bluetooth/gatt/gatt_cache_"
#define GATT_CACHE_VERSION 6

#define GATT_HASH_MAX_SIZE 30
#define GATT_HASH_PATH_PREFIX "/var/lib/bluetooth/gatt/gatt_hash_"
#define GATT_HASH_PATH "/var/lib/bluetooth/gatt"
#define GATT_HASH_FILE_PREFIX "gatt_hash_"
#else
#define GATT_CACHE_PREFIX "/data/misc/bluetooth/gatt_cache_"
#define GATT_CACHE_VERSION 6

#define GATT_HASH_MAX_SIZE 30
#define GATT_HASH_PATH_PREFIX "/data/misc/bluetooth/gatt_hash_"
#define GATT_HASH_PATH "/data/misc/bluetooth"
#define GATT_HASH_FILE_PREFIX "gatt_hash_"
#endif

// Default expired time is 7 days
#define GATT_HASH_EXPIRED_TIME 604800

static void bta_gattc_hash_remove_least_recently_used_if_possible();

static void bta_gattc_generate_cache_file_name(char* buffer, size_t buffer_len,
                                               const RawAddress& bda) {
  snprintf(buffer, buffer_len, "%s%02x%02x%02x%02x%02x%02x", GATT_CACHE_PREFIX,
           bda.address[0], bda.address[1], bda.address[2], bda.address[3],
           bda.address[4], bda.address[5]);
}

static void bta_gattc_generate_hash_file_name(char* buffer, size_t buffer_len,
                                              const Octet16& hash) {
  snprintf(buffer, buffer_len, "%s%s", GATT_HASH_PATH_PREFIX,
           base::HexEncode(hash.data(), 16).c_str());
}

static gatt::Database EMPTY_DB;

/*******************************************************************************
 *
 * Function         bta_gattc_load_db
 *
 * Description      Load GATT database from storage.
 *
 * Parameter        fname: input file name
 *
 * Returns          non-empty GATT database on success, empty GATT database
 *                  otherwise
 *
 ******************************************************************************/
static gatt::Database bta_gattc_load_db(const char* fname) {
  FILE* fd = fopen(fname, "rb");
  if (!fd) {
    log::error("can't open GATT cache file {} for reading, error: {}", fname,
               strerror(errno));
    return EMPTY_DB;
  }

  uint16_t cache_ver = 0;
  uint16_t num_attr = 0;

  if (fread(&cache_ver, sizeof(uint16_t), 1, fd) != 1) {
    log::error("can't read GATT cache version from: {}", fname);
    goto done;
  }

  if (cache_ver != GATT_CACHE_VERSION) {
    log::error("wrong GATT cache version: {}", fname);
    goto done;
  }

  if (fread(&num_attr, sizeof(uint16_t), 1, fd) != 1) {
    log::error("can't read number of GATT attributes: {}", fname);
    goto done;
  }

  {
    std::vector<StoredAttribute> attr(num_attr);

    if (fread(attr.data(), sizeof(StoredAttribute), num_attr, fd) != num_attr) {
      log::error("can't read GATT attributes: {}", fname);
      goto done;
    }
    fclose(fd);

    bool success = false;
    gatt::Database result = gatt::Database::Deserialize(attr, &success);
    return success ? result : EMPTY_DB;
  }

done:
  fclose(fd);
  return EMPTY_DB;
}

/*******************************************************************************
 *
 * Function         bta_gattc_cache_load
 *
 * Description      Load GATT cache from storage for server.
 *
 * Parameter        bd_address: remote device address
 *
 * Returns          non-empty GATT database on success, empty GATT database
 *                  otherwise
 *
 ******************************************************************************/
gatt::Database bta_gattc_cache_load(const RawAddress& server_bda) {
  char fname[255] = {0};
  bta_gattc_generate_cache_file_name(fname, sizeof(fname), server_bda);
  return bta_gattc_load_db(fname);
}

/*******************************************************************************
 *
 * Function         bta_gattc_hash_load
 *
 * Description      Load GATT cache from storage for server.
 *
 * Parameter        hash: 16-byte value
 *
 * Returns          non-empty GATT database on success, empty GATT database
 *                  otherwise
 *
 ******************************************************************************/
gatt::Database bta_gattc_hash_load(const Octet16& hash) {
  char fname[255] = {0};
  bta_gattc_generate_hash_file_name(fname, sizeof(fname), hash);
  return bta_gattc_load_db(fname);
}

void StoredAttribute::SerializeStoredAttribute(const StoredAttribute& attr,
                                               std::vector<uint8_t>& bytes) {
  size_t original_size = bytes.size();
  // handle
  bytes.push_back(attr.handle & 0xff);
  bytes.push_back(attr.handle >> 8);
  auto uuid = attr.type.To128BitBE();
  bytes.insert(bytes.cend(), uuid.cbegin(), uuid.cend());

  if (attr.type.Is16Bit()) {
    switch (attr.type.As16Bit()) {
      /* primary or secondary service definition */
      case GATT_UUID_PRI_SERVICE:
      case GATT_UUID_SEC_SERVICE:
        uuid = attr.value.service.uuid.To128BitBE();
        bytes.insert(bytes.cend(), uuid.cbegin(), uuid.cend());
        bytes.push_back(attr.value.service.end_handle & 0xff);
        bytes.push_back(attr.value.service.end_handle >> 8);
        break;
      case GATT_UUID_INCLUDE_SERVICE:
        /* included service definition */
        bytes.push_back(attr.value.included_service.handle & 0xff);
        bytes.push_back(attr.value.included_service.handle >> 8);
        bytes.push_back(attr.value.included_service.end_handle & 0xff);
        bytes.push_back(attr.value.included_service.end_handle >> 8);
        uuid = attr.value.included_service.uuid.To128BitBE();
        bytes.insert(bytes.cend(), uuid.cbegin(), uuid.cend());
        break;
      case GATT_UUID_CHAR_DECLARE:
        /* characteristic definition */
        bytes.push_back(attr.value.characteristic.properties);
        bytes.push_back(0);  // Padding byte
        bytes.push_back(attr.value.characteristic.value_handle & 0xff);
        bytes.push_back(attr.value.characteristic.value_handle >> 8);
        uuid = attr.value.characteristic.uuid.To128BitBE();
        bytes.insert(bytes.cend(), uuid.cbegin(), uuid.cend());
        break;
      case GATT_UUID_CHAR_EXT_PROP:
        /* for descriptor we store value only for
         * «Characteristic Extended Properties» */
        bytes.push_back(attr.value.characteristic_extended_properties & 0xff);
        bytes.push_back(attr.value.characteristic_extended_properties >> 8);
        break;
      default:
        // log::verbose("Unhandled type UUID 0x{:04x}", attr.type.As16Bit());
        break;
    }
  }
  // padding
  for (size_t i = bytes.size() - original_size;
       i < StoredAttribute::kSizeOnDisk; i++) {
    bytes.push_back(0);
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_store_db
 *
 * Description      Storess GATT db.
 *
 * Parameter        fname: output file name
 *                  attr: attributes to save.
 *
 * Returns          true on success, false otherwise
 *
 ******************************************************************************/
static bool bta_gattc_store_db(const char* fname,
                               const std::vector<StoredAttribute>& attr) {
  FILE* fd = fopen(fname, "wb");
  if (!fd) {
    log::error("can't open GATT cache file for writing: {}", fname);
    return false;
  }

  uint16_t cache_ver = GATT_CACHE_VERSION;
  if (fwrite(&cache_ver, sizeof(uint16_t), 1, fd) != 1) {
    log::error("can't write GATT cache version: {}", fname);
    fclose(fd);
    return false;
  }

  uint16_t num_attr = attr.size();
  if (fwrite(&num_attr, sizeof(uint16_t), 1, fd) != 1) {
    log::error("can't write GATT cache attribute count: {}", fname);
    fclose(fd);
    return false;
  }

  std::vector<uint8_t> db_bytes;
  db_bytes.reserve(num_attr * StoredAttribute::kSizeOnDisk);
  for (const auto attribute : attr) {
    StoredAttribute::SerializeStoredAttribute(attribute, db_bytes);
  }

  if (fwrite(db_bytes.data(), sizeof(uint8_t), db_bytes.size(), fd) !=
      db_bytes.size()) {
    log::error("can't write GATT cache attributes: {}", fname);
    fclose(fd);
    return false;
  }

  fclose(fd);
  return true;
}

/*******************************************************************************
 *
 * Function         bta_gattc_cache_write
 *
 * Description      This callout function is executed by GATT when a server
 *                  cache is available to save. Before calling this API, make
 *                  sure the device is bonded. Otherwise you might get lots of
 *                  address caches for unbonded devices.
 *
 * Parameter        server_bda: server bd address of this cache belongs to
 *                  database: attributes to save.
 * Returns
 *
 ******************************************************************************/
void bta_gattc_cache_write(const RawAddress& server_bda,
                           const gatt::Database& database) {
  char addr_file[255] = {0};
  char hash_file[255] = {0};
  Octet16 hash = database.Hash();
  bta_gattc_generate_cache_file_name(addr_file, sizeof(addr_file), server_bda);
  bta_gattc_generate_hash_file_name(hash_file, sizeof(hash_file), hash);

  bool result = bta_gattc_hash_write(hash, database);
  // Only link addr_file to hash file when hash_file is created successfully.
  if (result) {
    bta_gattc_cache_link(server_bda, hash);
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_cache_link
 *
 * Description      Link address-database file to hash-database file
 *
 * Parameter        server_bda: server bd address of this cache belongs to
 *                  hash: 16-byte value
 *
 * Returns          true on success, false otherwise
 *
 ******************************************************************************/
void bta_gattc_cache_link(const RawAddress& server_bda, const Octet16& hash) {
  char addr_file[255] = {0};
  char hash_file[255] = {0};
  bta_gattc_generate_cache_file_name(addr_file, sizeof(addr_file), server_bda);
  bta_gattc_generate_hash_file_name(hash_file, sizeof(hash_file), hash);

  unlink(addr_file);  // remove addr file first if the file exists
  if (link(hash_file, addr_file) == -1) {
    log::error("link {} to {}, errno={}", addr_file, hash_file, errno);
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_hash_write
 *
 * Description      This callout function is executed by GATT when a server
 *                  cache is available to save for specific hash.
 *
 * Parameter        hash: 16-byte value
 *                  database: gatt::Database instance.
 *
 * Returns          true on success, false otherwise
 *
 ******************************************************************************/
bool bta_gattc_hash_write(const Octet16& hash, const gatt::Database& database) {
  char fname[255] = {0};
  bta_gattc_generate_hash_file_name(fname, sizeof(fname), hash);
  bta_gattc_hash_remove_least_recently_used_if_possible();
  return bta_gattc_store_db(fname, database.Serialize());
}

/*******************************************************************************
 *
 * Function         bta_gattc_cache_reset
 *
 * Description      This callout function is executed by GATTC to reset cache in
 *                  application
 *
 * Parameter        server_bda: server bd address of this cache belongs to
 *
 * Returns          void.
 *
 ******************************************************************************/
void bta_gattc_cache_reset(const RawAddress& server_bda) {
  log::verbose("");
  char fname[255] = {0};
  bta_gattc_generate_cache_file_name(fname, sizeof(fname), server_bda);
  unlink(fname);
}

/*******************************************************************************
 *
 * Function         bta_gattc_hash_remove_least_recently_used_if_possible
 *
 * Description      When the max size reaches, find the oldest item and remove
 *                  it if possible
 *
 * Parameter
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_gattc_hash_remove_least_recently_used_if_possible() {
  std::unique_ptr<DIR, decltype(&closedir)> dirp(opendir(GATT_HASH_PATH),
                                                 &closedir);
  if (dirp == nullptr) {
    log::error("open dir error, dir={}", GATT_HASH_PATH);
    return;
  }

  time_t current_time = time(NULL);
  time_t lru_time = current_time;
  size_t count = 0;
  string candidate_item;
  vector<string> expired_items;

  log::debug("<-----------Start Local Hash Cache---------->");
  dirent* dp;
  while ((dp = readdir(dirp.get())) != nullptr) {
    if (strncmp(".", dp->d_name, 1) == 0 || strncmp("..", dp->d_name, 2) == 0) {
      continue;
    }

    // pattern match: gatt_hash_
    size_t fname_len = strlen(dp->d_name);
    size_t pattern_len = strlen(GATT_HASH_FILE_PREFIX);
    if (pattern_len > fname_len) {
      continue;
    }

    // check if the file name has gatt_hash_ as prefix
    char tmp[255] = {0};
    strncpy(tmp, dp->d_name, pattern_len);
    if (strncmp(tmp, GATT_HASH_FILE_PREFIX, pattern_len) != 0) {
      continue;
    }

    // increase hash file count
    count++;

    // generate the full path, in order to get the state of the file
    snprintf(tmp, 255, "%s/%s", GATT_HASH_PATH, dp->d_name);

    struct stat buf;
    int result = lstat(tmp, &buf);
    log::debug("name={}, result={}, linknum={}, mtime={}", dp->d_name, result,
               (unsigned long)buf.st_nlink, (unsigned long)buf.st_mtime);

    // if hard link count of the file is 1, it means no trusted device links to
    // the inode. It is safe to be a candidate to be removed
    if (buf.st_nlink == 1) {
      if (buf.st_mtime < lru_time) {
        lru_time = buf.st_mtime;
        // Find the LRU candidate during for-loop itreation.
        candidate_item.assign(tmp);
      }

      if (buf.st_mtime + GATT_HASH_EXPIRED_TIME < current_time) {
        // Add expired item.
        expired_items.emplace_back(tmp);
      }
    }
  }
  log::debug("<-----------End Local Hash Cache------------>");

  // if the number of hash files exceeds the limit, remove the cadidate item.
  if (count > GATT_HASH_MAX_SIZE && !candidate_item.empty()) {
    unlink(candidate_item.c_str());
    log::debug("delete hash file (size), name={}", candidate_item);
  }

  // If there is any file expired, also delete it.
  for (string expired_item : expired_items) {
    unlink(expired_item.c_str());
    log::debug("delete hash file (expired), name={}", expired_item);
  }
}
