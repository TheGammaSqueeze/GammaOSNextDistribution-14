/*
 * Copyright (C) 2026 GammaOS
 *
 * The share configuration itself: where a share is stored, how its password is encoded, and how a
 * mount is started and stopped.
 *
 * This is split out of the daemon so the nano menu links the same code. A share added in nano and
 * a share added in Settings have to be byte-identical in the properties, and the password in
 * particular has to be encoded the same way or the daemon decrypts rubbish and the mount fails with
 * a confusing "permission denied". One implementation removes that whole class of bug.
 *
 * Settings and TvSettings are Java and cannot link this, so they use
 * com.android.internal.gammaos.GammaShareConfig, which is a deliberate mirror of this file. If the
 * property names or the password encoding change here, change them there too.
 */

#ifndef GAMMAOS_SHARE_CONFIG_H
#define GAMMAOS_SHARE_CONFIG_H

#include <string>
#include <vector>

namespace gammaos {
namespace sharefs {

enum class ShareType {
    kUnknown = 0,
    kSmb,      // SMB2/SMB3 via libsmb2
    kWebdav,   // WebDAV over HTTP(S) via libcurl
    kFtp,      // FTP/FTPS via libcurl
    kNfs,      // NFSv3 via libnfs
};

ShareType shareTypeFromString(const std::string& s);
const char* shareTypeName(ShareType t);      // "SMB", "NFS", ... for display
const char* shareTypeKey(ShareType t);       // "smb", "nfs", ... as stored

// A share as configured by the user.
struct ShareConfig {
    int         slot = 0;  // 1..kMaxShares; which persist.gammaos.share.<n>.* set this came from
    std::string name;      // display name, also the directory under /mnt/shares
    ShareType   type = ShareType::kUnknown;
    std::string host;      // hostname or address
    int         port = 0;  // 0 = protocol default
    std::string path;      // SMB share name, or the remote directory for WebDAV/FTP/NFS
    std::string user;      // empty = anonymous / guest; unused for NFS, which
                           // authorises by address rather than by credentials
    std::string password;  // plain text in memory; encrypted on the way to a property
    std::string domain;    // SMB workgroup, optional
    bool        readOnly = false;
    bool        enabled = false;   // the user's intent, not whether it is mounted right now
    // Encrypt the connection: https for WebDAV, ftps for FTP. This has to be its own flag rather
    // than being inferred from the port, because a NAS commonly serves WebDAV over TLS on a port
    // of its own (Synology's default is 5006) and inferring from 443 would make that unreachable.
    // Ignored by SMB, which negotiates its own encryption, and by NFS.
    bool        useTls = false;
};

// How many shares the system offers. Kept small deliberately: each one is a resident process
// holding a connection, and these are memory-constrained devices.
constexpr int kMaxShares = 4;

// Credentials are stored encrypted so a share password is not sitting in plain text on a device
// that is frequently rooted. This is obfuscation against casual inspection, not a defence against
// someone who already has root; see the note in share_config.cpp.
std::string encryptSecret(const std::string& plain);
std::string decryptSecret(const std::string& stored);

// Read every configured share, in slot order. Passwords come back DECRYPTED.
std::vector<ShareConfig> loadShares();

// Read one slot (1..kMaxShares). Returns false if that slot has no share in it.
bool loadShare(int slot, ShareConfig* out);

// Write a share into its slot. Encrypts the password. Does not start or stop anything.
void saveShare(const ShareConfig& cfg);

// Clear a slot completely, stopping its mount first.
void deleteShare(int slot);

// The first unused slot, or 0 when all of them are taken.
int firstFreeSlot();

// Set the enabled flag, which is what init watches to start or stop the mount.
void setShareEnabled(int slot, bool enabled);

// Is this share's FUSE mount live right now? Reads the kernel mount table, so it never blocks on
// an unreachable server the way statting the mount point would.
bool isShareMounted(const std::string& name);

// Everything wrong with this share that would stop it mounting, as a short sentence, or "" when it
// looks complete. Used to explain a greyed-out Enable rather than letting the mount fail silently.
std::string shareProblem(const ShareConfig& cfg);

}  // namespace sharefs
}  // namespace gammaos

#endif  // GAMMAOS_SHARE_CONFIG_H
