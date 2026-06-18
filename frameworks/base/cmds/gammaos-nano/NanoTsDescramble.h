// NanoTsDescramble - shared MPEG-TS "descramble" data source.
//
// HDHomeRun / ATSC .ts captures carry a CA_descriptor (tag 0x09) in the PMT even
// though the payload is clear (transport_scrambling_control is 0), and they multiplex
// a proprietary table (table_id 0xc0) onto the PMT pid. Android's ATSParser then marks
// every track scrambled and bails with "PMT data error!", so the file will not decode.
//
// makeTsDataSource() builds an AMediaDataSource that, on every read, works over the
// enclosing 188-aligned window and for each packet on the PMT pid either rewrites the
// real PMT's CA descriptor tags to 0xFF (recomputing the section CRC) or turns a foreign
// table / continuation packet into a null packet. The extractor then sees a clean,
// clear program. Used by the video path (NanoVideo, vidBuildTracks) and the audio path
// (NanoAudio) so a scrambled-flagged .ts plays both picture and sound.
#ifndef GAMMAOS_NANO_TS_DESCRAMBLE_H
#define GAMMAOS_NANO_TS_DESCRAMBLE_H

#include <sys/types.h>
#include <media/NdkMediaDataSource.h>

namespace android {

// Sniff a bounded prefix of `fd`: returns true if it is an MPEG-TS whose PMT carries a
// CA descriptor (so Android would flag it scrambled) and sets outPmtPid. Returns false
// for non-TS / clean-PMT files (which should use the plain fd data source).
bool tsNeedsDescramble(int fd, int& outPmtPid);

// Build a descrambling AMediaDataSource for an already-detected TS fd (dup's fd). The
// returned source + its userdata must be freed with freeTsDataSource() AFTER the
// AMediaExtractor that used them is deleted. Returns nullptr on failure (outUserdata
// untouched).
AMediaDataSource* tsMakeDataSource(int fd, off64_t size, int pmtPid, void** outUserdata);

// Delete the data source + free its userdata (closes the dup'd fd). nullptr-safe.
void tsFreeDataSource(AMediaDataSource* ds, void* userdata);

} // namespace android

#endif // GAMMAOS_NANO_TS_DESCRAMBLE_H
