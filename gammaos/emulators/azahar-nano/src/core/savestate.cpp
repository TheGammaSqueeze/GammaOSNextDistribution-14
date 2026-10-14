// Copyright 2020-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <chrono>
#include <deque>
#include <memory>
#include <sstream>
#include <streambuf>
#include <zstd.h>
#include <cryptopp/hex.h>
#include <fmt/ranges.h>
#include "common/archives.h"
#include "common/file_util.h"
#include "common/logging/log.h"
#include "common/scm_rev.h"
#include "common/swap.h"
#include "common/zstd_compression.h"
#include "core/core.h"
#include "core/loader/loader.h"
#include "core/movie.h"
#include "core/savestate.h"
#include "network/network.h"

namespace Core {

#pragma pack(push, 1)
struct CSTHeader {
    std::array<u8, 4> filetype{};    /// Unique Identifier to check the file type (always "CST"0x1B)
    u64_le program_id{};             /// ID of the ROM being executed. Also called title_id
    std::array<u8, 20> revision{};   /// Git hash of the revision this savestate was created with
    u64_le time{};                   /// The time when this save state was created
    std::array<u8, 20> build_name{}; /// The build name (Canary/Nightly) with the version number
    u32_le zero{};                   /// Should be zero, just in case.
    std::array<u8, 20> build_version{}; /// Latest build version, used as compatibility.
    u32_le zero_2{};                    /// Should be zero, just in case.

    std::array<u8, 168> reserved{}; /// Make heading 256 bytes so it has consistent size
};
static_assert(sizeof(CSTHeader) == 256, "CSTHeader should be 256 bytes");
#pragma pack(pop)

constexpr std::array<u8, 4> header_magic_bytes{{'C', 'S', 'T', 0x1B}};

static std::string GetSaveStatePath(u64 program_id, u64 movie_id, u32 slot) {
    if (movie_id) {
        return fmt::format("{}{:016X}.movie{:016X}.{:02d}.cst",
                           FileUtil::GetUserPath(FileUtil::UserPath::StatesDir), program_id,
                           movie_id, slot);
    } else {
        return fmt::format("{}{:016X}.{:02d}.cst",
                           FileUtil::GetUserPath(FileUtil::UserPath::StatesDir), program_id, slot);
    }
}

static bool ValidateSaveState(const CSTHeader& header, SaveStateInfo& info, u64 program_id,
                              u64 movie_id) {
    const auto path = GetSaveStatePath(program_id, movie_id, info.slot);
    if (header.filetype != header_magic_bytes) {
        LOG_WARNING(Core, "Invalid save state file {}", path);
        return false;
    }
    info.time = header.time;

    if (header.program_id != program_id) {
        LOG_WARNING(Core, "Save state file isn't for the current game {}", path);
        return false;
    }
    const std::string revision = fmt::format("{:02x}", fmt::join(header.revision, ""));
    const std::string build_name =
        header.zero == 0 ? reinterpret_cast<const char*>(header.build_name.data()) : "";
    const std::string build_version =
        header.zero_2 == 0 ? reinterpret_cast<const char*>(header.build_version.data()) : "";

    if (revision == Common::g_scm_rev) {
        info.status = SaveStateInfo::ValidationStatus::OK;
    } else {
        info.build_name = build_name;
        info.build_version = build_version;

        // A state written by the file writer before it recorded the version has
        // an empty field: it comes from a revision of the same build line, not
        // from a different release, so it is a revision mismatch (loadable).
        info.status = (Common::g_build_version == info.build_version || info.build_version.empty())
                          ? SaveStateInfo::ValidationStatus::RevisionMismatch
                          : SaveStateInfo::ValidationStatus::BuildMismatch;
        if (info.status == SaveStateInfo::ValidationStatus::RevisionMismatch) {
            LOG_WARNING(Core, "Save state {} was made by revision {}; loading it anyway", path,
                        build_name);
        }
    }
    return true;
}

std::vector<SaveStateInfo> ListSaveStates(u64 program_id, u64 movie_id) {
    std::vector<SaveStateInfo> result;
    result.reserve(SaveStateSlotCount);
    for (u32 slot = 0; slot <= SaveStateSlotCount; ++slot) {
        const auto path = GetSaveStatePath(program_id, movie_id, slot);
        if (!FileUtil::Exists(path)) {
            continue;
        }

        SaveStateInfo info;
        info.slot = slot;

        FileUtil::IOFile file(path, "rb");
        if (!file) {
            LOG_ERROR(Core, "Could not open file {}", path);
            continue;
        }
        CSTHeader header;
        if (file.GetSize() < sizeof(header)) {
            LOG_ERROR(Core, "File too small {}", path);
            continue;
        }
        if (file.ReadBytes(&header, sizeof(header)) != sizeof(header)) {
            LOG_ERROR(Core, "Could not read from file {}", path);
            continue;
        }
        if (!ValidateSaveState(header, info, program_id, movie_id)) {
            continue;
        }

        result.emplace_back(std::move(info));
    }
    return result;
}

SaveStateInfo GetSaveStateInfo(u64 program_id, u64 movie_id, u32 slot) {
    SaveStateInfo info{};
    info.slot = std::numeric_limits<u32>::max();

    const auto path = GetSaveStatePath(program_id, movie_id, slot);
    if (!FileUtil::Exists(path)) {
        return info;
    }

    FileUtil::IOFile file(path, "rb");
    if (!file) {
        LOG_ERROR(Core, "Could not open file {}", path);
        return info;
    }
    CSTHeader header;
    if (file.GetSize() < sizeof(header)) {
        LOG_ERROR(Core, "File too small {}", path);
        return info;
    }
    if (file.ReadBytes(&header, sizeof(header)) != sizeof(header)) {
        LOG_ERROR(Core, "Could not read from file {}", path);
        return info;
    }
    if (ValidateSaveState(header, info, program_id, movie_id)) {
        info.slot = slot;
    }
    return info;
}

void System::WaitSaveWriter() const {
    if (gamma_save_writer.joinable()) {
        gamma_save_writer.join();
    }
}


namespace {

// GammaOS: save states move through memory in pieces instead of whole copies. A state is up
// to ~300 MB serialized (a New 3DS title's FCRAM); the stock path held the growing string
// stream, its str() copy and a compressed buffer at once (a 740 MB spike for Mario Kart 7),
// and loading held the decompressed vector, a string copy and the string stream's own copy.
// On a 4 GB device the low memory killer took the game during saves and loads.

/// Output stream buffer that appends to fixed-size chunks (no reallocation, no copies).
class ChunkOutBuf final : public std::streambuf {
public:
    static constexpr std::size_t ChunkSize = 4 << 20;

    std::size_t Size() const {
        if (chunks.empty()) {
            return 0;
        }
        return (chunks.size() - 1) * ChunkSize + static_cast<std::size_t>(pptr() - pbase());
    }

    /// Hands the chunks over in order; the last one is sized to its used part.
    std::deque<std::vector<char>> Take() {
        if (!chunks.empty()) {
            chunks.back().resize(static_cast<std::size_t>(pptr() - pbase()));
            chunks.back().shrink_to_fit();
        }
        setp(nullptr, nullptr);
        return std::move(chunks);
    }

protected:
    int_type overflow(int_type ch) override {
        chunks.emplace_back(ChunkSize);
        char* base = chunks.back().data();
        setp(base, base + ChunkSize);
        if (!traits_type::eq_int_type(ch, traits_type::eof())) {
            *pptr() = traits_type::to_char_type(ch);
            pbump(1);
        }
        return traits_type::not_eof(ch);
    }

    std::streamsize xsputn(const char* data, std::streamsize count) override {
        std::streamsize done = 0;
        while (done < count) {
            if (pptr() == epptr()) {
                overflow(traits_type::eof());
            }
            const std::streamsize n = std::min<std::streamsize>(count - done, epptr() - pptr());
            std::memcpy(pptr(), data + done, static_cast<std::size_t>(n));
            pbump(static_cast<int>(n));
            done += n;
        }
        return done;
    }

private:
    std::deque<std::vector<char>> chunks;
};

/// Input stream buffer that decompresses a zstd stream from a file as it is read.
class ZstdFileInBuf final : public std::streambuf {
public:
    explicit ZstdFileInBuf(FileUtil::IOFile& file_) : file{file_}, dctx{ZSTD_createDCtx()} {
        in.resize(ZSTD_DStreamInSize());
        out.resize(ZSTD_DStreamOutSize());
        setg(out.data(), out.data(), out.data());
    }
    ~ZstdFileInBuf() override {
        ZSTD_freeDCtx(dctx);
    }
    bool Failed() const {
        return failed;
    }

protected:
    int_type underflow() override {
        if (gptr() < egptr()) {
            return traits_type::to_int_type(*gptr());
        }
        while (!failed) {
            if (in_pos == in_size && !eof) {
                in_size = file.ReadBytes(in.data(), in.size());
                in_pos = 0;
                eof = in_size == 0;
            }
            if (in_pos == in_size && eof) {
                break;
            }
            ZSTD_inBuffer src{in.data(), in_size, in_pos};
            ZSTD_outBuffer dst{out.data(), out.size(), 0};
            const std::size_t ret = ZSTD_decompressStream(dctx, &dst, &src);
            in_pos = src.pos;
            if (ZSTD_isError(ret)) {
                LOG_ERROR(Core, "save state: decompression failed: {}", ZSTD_getErrorName(ret));
                failed = true;
                break;
            }
            if (dst.pos > 0) {
                setg(out.data(), out.data(), out.data() + dst.pos);
                return traits_type::to_int_type(*gptr());
            }
        }
        return traits_type::eof();
    }

private:
    FileUtil::IOFile& file;
    ZSTD_DCtx* dctx;
    std::vector<char> in;
    std::vector<char> out;
    std::size_t in_size = 0, in_pos = 0;
    bool eof = false;
    bool failed = false;
};

/// Compresses the chunks into the file as one zstd frame (with its content size, so the
/// whole-buffer decompressor reads it too), freeing each chunk once it is consumed.
bool WriteCompressedChunks(FileUtil::IOFile& file, std::deque<std::vector<char>>& chunks,
                           std::size_t total, int level, std::size_t* compressed) {
    ZSTD_CCtx* cctx = ZSTD_createCCtx();
    if (!cctx) {
        return false;
    }
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_compressionLevel, level);
    ZSTD_CCtx_setPledgedSrcSize(cctx, total);
    std::vector<char> out(ZSTD_CStreamOutSize());
    bool ok = true;
    *compressed = 0;
    const auto drain = [&](ZSTD_inBuffer& src, ZSTD_EndDirective mode) {
        for (;;) {
            ZSTD_outBuffer dst{out.data(), out.size(), 0};
            const std::size_t remaining = ZSTD_compressStream2(cctx, &dst, &src, mode);
            if (ZSTD_isError(remaining)) {
                LOG_ERROR(Core, "save state: compression failed: {}",
                          ZSTD_getErrorName(remaining));
                return false;
            }
            if (dst.pos > 0) {
                if (file.WriteBytes(out.data(), dst.pos) != dst.pos) {
                    return false;
                }
                *compressed += dst.pos;
            }
            const bool done = mode == ZSTD_e_end ? remaining == 0 : src.pos == src.size;
            if (done) {
                return true;
            }
        }
    };
    while (ok && !chunks.empty()) {
        ZSTD_inBuffer src{chunks.front().data(), chunks.front().size(), 0};
        ok = drain(src, ZSTD_e_continue);
        chunks.pop_front();
    }
    if (ok) {
        ZSTD_inBuffer empty{nullptr, 0, 0};
        ok = drain(empty, ZSTD_e_end);
    }
    ZSTD_freeCCtx(cctx);
    return ok;
}

} // namespace

void System::SaveState(u32 slot) const {
    if (app_loader) {
        if (!app_loader->SupportsSaveStates()) {
            throw std::runtime_error("The current app loader doesn't support save states");
        }
    }
    // GammaOS: one save at a time; the previous one may still be writing.
    WaitSaveWriter();

    // GammaOS: only serializing needs the emulation stopped (1.3 s for Kirby: Planet Robobot's
    // 295 MB). Compressing and writing run on a writer thread; the file is written under a
    // temporary name and renamed into place, so the slot file is never half written. Loads,
    // the next save and shutdown wait for the writer.
    const auto t0 = std::chrono::steady_clock::now();
    auto chunk_buf = std::make_shared<ChunkOutBuf>();
    {
        // Serialize (the archive is finished before the chunks move to the writer)
        std::ostream ostream{chunk_buf.get()};
        oarchive oa{ostream};
        oa&* this;
    }
    const std::size_t total = chunk_buf->Size();
    auto chunks = std::make_shared<std::deque<std::vector<char>>>(chunk_buf->Take());
    chunk_buf.reset();
    const auto t1 = std::chrono::steady_clock::now();

    const u64 movie_id = movie.GetCurrentMovieID();
    const auto path = GetSaveStatePath(title_id, movie_id, slot);
    if (!FileUtil::CreateFullPath(path)) {
        throw std::runtime_error("Could not create path " + path);
    }

    CSTHeader header{};
    header.filetype = header_magic_bytes;
    header.program_id = title_id;
    std::string rev_bytes;
    CryptoPP::StringSource ss(Common::g_scm_rev, true,
                              new CryptoPP::HexDecoder(new CryptoPP::StringSink(rev_bytes)));
    std::memcpy(header.revision.data(), rev_bytes.data(), sizeof(header.revision));
    header.time = std::chrono::duration_cast<std::chrono::seconds>(
                      std::chrono::system_clock::now().time_since_epoch())
                      .count();
    const std::string build_fullname = Common::g_build_fullname;
    std::memset(header.build_name.data(), 0, sizeof(header.build_name));
    std::memcpy(header.build_name.data(), build_fullname.c_str(),
                std::min(build_fullname.length(), sizeof(header.build_name) - 1));
    // The file writer never recorded the build version (the in-memory writer
    // below does), so every state looked like it came from another build.
    const std::string build_version = Common::g_build_version;
    std::memset(header.build_version.data(), 0, sizeof(header.build_version));
    std::memcpy(header.build_version.data(), build_version.c_str(),
                std::min(build_version.length(), sizeof(header.build_version) - 1));

    gamma_save_writer = std::thread([chunks, total, header, path, t0, t1]() {
        const auto ms = [](auto a, auto b) {
            return std::chrono::duration_cast<std::chrono::milliseconds>(b - a).count();
        };
        // GammaOS: level 1 instead of the default 3 (2.4 s -> 1.2 s for 295 MB, about 10%
        // bigger); any level loads the same. Compressed straight into the file chunk by chunk.
        const std::string tmp_path = path + ".tmp";
        std::size_t compressed = 0;
        {
            FileUtil::IOFile file(tmp_path, "wb");
            if (!file || file.WriteBytes(&header, sizeof(header)) != sizeof(header) ||
                !WriteCompressedChunks(file, *chunks, total, 1, &compressed)) {
                LOG_ERROR(Core, "save state: could not write {}", tmp_path);
                chunks->clear();
                file.Close();
                FileUtil::Delete(tmp_path);
                return;
            }
        }
        const auto t2 = std::chrono::steady_clock::now();
        if (!FileUtil::Rename(tmp_path, path)) {
            LOG_ERROR(Core, "save state: could not rename {} to {}", tmp_path, path);
            FileUtil::Delete(tmp_path);
            return;
        }
        const auto t3 = std::chrono::steady_clock::now();
        LOG_INFO(Core,
                 "save state: serialize {} ms ({} MB, emulation paused), compress and write {} ms "
                 "({} MB), rename {} ms",
                 ms(t0, t1), total >> 20, ms(t1, t2), compressed >> 20, ms(t2, t3));
    });
}

void System::LoadState(u32 slot) {
    WaitSaveWriter();
    if (app_loader) {
        if (!app_loader->SupportsSaveStates()) {
            throw std::runtime_error("The current app loader doesn't support save states");
        }
    }
    auto room_member = Network::GetRoomMember().lock();
    if (room_member && room_member->IsConnected()) {
        throw std::runtime_error("Unable to load while connected to multiplayer");
    }

    const u64 movie_id = movie.GetCurrentMovieID();
    const auto path = GetSaveStatePath(title_id, movie_id, slot);

    FileUtil::IOFile file(path, "rb");

    // load header
    CSTHeader header;
    if (!file || file.ReadBytes(&header, sizeof(header)) != sizeof(header)) {
        throw std::runtime_error("Could not read from file at " + path);
    }

    // validate header
    SaveStateInfo info;
    info.slot = slot;
    if (!ValidateSaveState(header, info, title_id, movie_id) ||
        info.status == SaveStateInfo::ValidationStatus::BuildMismatch) {
        throw std::runtime_error("Invalid savestate");
    }

    // GammaOS: decompressed while the archive reads it (see ChunkOutBuf); the whole state is
    // never held in memory.
    const auto t0 = std::chrono::steady_clock::now();
    ZstdFileInBuf zbuf{file};
    std::istream istream{&zbuf};

    // Deserialize
    iarchive ia{istream};
    ia&* this;
    if (zbuf.Failed()) {
        throw std::runtime_error("Could not decompress the savestate at " + path);
    }
    LOG_INFO(Core, "load state: read, decompress and deserialize {} ms",
             std::chrono::duration_cast<std::chrono::milliseconds>(
                 std::chrono::steady_clock::now() - t0)
                 .count());
}

std::vector<u8> System::SaveStateBuffer() const {
    std::ostringstream sstream{std::ios_base::binary};
    // Serialize
    oarchive oa{sstream};
    oa&* this;

    const std::string& str{sstream.str()};
    const auto data = std::span<const u8>{reinterpret_cast<const u8*>(str.data()), str.size()};
    auto buffer = Common::Compression::CompressDataZSTDDefault(data);

    CSTHeader header{};
    header.filetype = header_magic_bytes;
    header.program_id = title_id;
    std::string rev_bytes;
    CryptoPP::StringSource ss(Common::g_scm_rev, true,
                              new CryptoPP::HexDecoder(new CryptoPP::StringSink(rev_bytes)));
    std::memcpy(header.revision.data(), rev_bytes.data(), sizeof(header.revision));
    header.time = std::chrono::duration_cast<std::chrono::seconds>(
                      std::chrono::system_clock::now().time_since_epoch())
                      .count();
    const std::string build_fullname = Common::g_build_fullname;
    std::memset(header.build_name.data(), 0, sizeof(header.build_name));
    std::memcpy(header.build_name.data(), build_fullname.c_str(),
                std::min(build_fullname.length(), sizeof(header.build_name) - 1));

    const std::string build_version = Common::g_build_version;
    std::memset(header.build_version.data(), 0, sizeof(header.build_version));
    std::memcpy(header.build_version.data(), build_version.c_str(),
                std::min(build_version.length(), sizeof(header.build_version) - 1));

    std::vector<u8> result((u8*)&header, (u8*)&header + sizeof(header));
    std::copy(buffer.begin(), buffer.end(), std::back_inserter(result));

    return result;
}

bool System::LoadStateBuffer(std::vector<u8> buffer) {
    CSTHeader header;

    if (buffer.size() < sizeof(header)) {
        LOG_ERROR(Core, "Save state too small");
        return false;
    }

    header = *((CSTHeader*)buffer.data());

    if (header.filetype != header_magic_bytes) {
        LOG_ERROR(Core, "Invalid save state");
        return false;
    }

    if (header.program_id != title_id) {
        LOG_ERROR(Core, "Save state isn't for the current game");
        return false;
    }
    std::string revision = fmt::format("{:02x}", fmt::join(header.revision, ""));
    if (revision != Common::g_scm_rev) {
        LOG_ERROR(Core,
                  "Save state file created from a different revision (core: {}, savestate: {})",
                  Common::g_scm_rev, revision);
        return false;
    }

    std::vector<u8> state(buffer.begin() + sizeof(CSTHeader), buffer.end());
    auto decompressed = Common::Compression::DecompressDataZSTD(state);

    std::istringstream sstream{
        std::string{reinterpret_cast<char*>(decompressed.data()), decompressed.size()},
        std::ios_base::binary};
    decompressed.clear();

    // Deserialize
    iarchive ia{sstream};
    ia&* this;

    return true;
}

} // namespace Core
