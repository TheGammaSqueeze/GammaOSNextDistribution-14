// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/transform/map/normalization-map.h"

#include <cstdint>
#include "icing/legacy/core/icing-packed-pod.h"

namespace icing {
namespace lib {

namespace {
// A pair representing the mapping of the 'from' character to 'to' character.
struct NormalizationPair {
  // All the mapped characters can be stored in 2 bytes.
  char16_t from;
  char16_t to;
} __attribute__((packed));

// The following mappings contain multiple categories:
// 1. Hiragana -> Katakana, listed in the order of Hiragana chart rows.
//    All regular and small Hiragana characters are mapped to Katakana. Note
//    that half-width Katakana characters are not handled here.
// 2. Common full-width characters -> ASCII characters.
//    Full-width characters in the Unicode range of [0xff01, 0xff5e] are mapped
//    to the corresponding ASCII forms.
// 3. Common ideographic punctuation marks -> ASCII characters.
//    Ideographic characters are in the Unicode range of [0x3000, 0x303f]. Here
//    we list two that are frequently used in CJK and can be converted to ASCII.
// 4. Common diacritic Latin characters -> ASCII characters.
//    We list most diacritic Latin characters within the Unicode range of
//    [0x00c0, 0x017e], some from [0x01a0, 0x021b], and most from [0x1e00,
//    0x1ef9].
//
// All the characters can be stored in a single UTF16 code unit, so we use
// char16_t to store them. Size of the following array is about 2.5KiB.
constexpr NormalizationPair kNormalizationMappings[] = {
    // Part 1: Hiragana -> Katakana
    // 'a' row
    {0x3042, 0x30a2},  // Hiragana letter        A -> Katakana letter  A
    {0x3044, 0x30a4},  // Hiragana letter        I -> Katakana letter  I
    {0x3046, 0x30a6},  // Hiragana letter        U -> Katakana letter  U
    {0x3048, 0x30a8},  // Hiragana letter        E -> Katakana letter  E
    {0x304a, 0x30aa},  // Hiragana letter        O -> Katakana letter  O
    {0x3041, 0x30a2},  // Hiragana letter small  A -> Katakana letter  A
    {0x3043, 0x30a4},  // Hiragana letter small  I -> Katakana letter  I
    {0x3045, 0x30a6},  // Hiragana letter small  U -> Katakana letter  U
    {0x3047, 0x30a8},  // Hiragana letter small  E -> Katakana letter  E
    {0x3049, 0x30aa},  // Hiragana letter small  O -> Katakana letter  O
    // 'ka' row
    {0x304b, 0x30ab},  // Hiragana letter       KA -> Katakana letter KA
    {0x304d, 0x30ad},  // Hiragana letter       KI -> Katakana letter KI
    {0x304f, 0x30af},  // Hiragana letter       KU -> Katakana letter KU
    {0x3051, 0x30b1},  // Hiragana letter       KE -> Katakana letter KE
    {0x3053, 0x30b3},  // Hiragana letter       KO -> Katakana letter KO
    {0x3095, 0x30ab},  // Hiragana letter small KA -> Katakana letter KA
    {0x3096, 0x30b1},  // Hiragana letter small KE -> Katakana letter KE
    // 'sa' row
    {0x3055, 0x30b5},  // Hiragana letter       SA -> Katakana letter SA
    {0x3057, 0x30b7},  // Hiragana letter       SI -> Katakana letter SI
    {0x3059, 0x30b9},  // Hiragana letter       SU -> Katakana letter SU
    {0x305b, 0x30bb},  // Hiragana letter       SE -> Katakana letter SE
    {0x305d, 0x30bd},  // Hiragana letter       SO -> Katakana letter SO
    // 'ta' row
    {0x305f, 0x30bf},  // Hiragana letter       TA -> Katakana letter TA
    {0x3061, 0x30c1},  // Hiragana letter       TI -> Katakana letter TI
    {0x3063, 0x30c4},  // Hiragana letter small TU -> Katakana letter TU
    {0x3064, 0x30c4},  // Hiragana letter       TU -> Katakana letter TU
    {0x3066, 0x30c6},  // Hiragana letter       TE -> Katakana letter TE
    {0x3068, 0x30c8},  // Hiragana letter       TO -> Katakana letter TO
    // 'na' row
    {0x306a, 0x30ca},  // Hiragana letter       NA -> Katakana letter NA
    {0x306b, 0x30cb},  // Hiragana letter       NI -> Katakana letter NI
    {0x306c, 0x30cc},  // Hiragana letter       NU -> Katakana letter NU
    {0x306d, 0x30cd},  // Hiragana letter       NE -> Katakana letter NE
    {0x306e, 0x30ce},  // Hiragana letter       NO -> Katakana letter NO
    // 'ha' row
    {0x306f, 0x30cf},  // Hiragana letter       HA -> Katakana letter HA
    {0x3072, 0x30d2},  // Hiragana letter       HI -> Katakana letter HI
    {0x3075, 0x30d5},  // Hiragana letter       HU -> Katakana letter HU
    {0x3078, 0x30d8},  // Hiragana letter       HE -> Katakana letter HE
    {0x307b, 0x30db},  // Hiragana letter       HO -> Katakana letter HO
    // 'ma' row
    {0x307e, 0x30de},  // Hiragana letter       MA -> Katakana letter MA
    {0x307f, 0x30df},  // Hiragana letter       MI -> Katakana letter MI
    {0x3080, 0x30e0},  // Hiragana letter       MU -> Katakana letter MU
    {0x3081, 0x30e1},  // Hiragana letter       ME -> Katakana letter ME
    {0x3082, 0x30e2},  // Hiragana letter       MO -> Katakana letter MO
    // 'ya' row
    {0x3083, 0x30e4},  // Hiragana letter small YA -> Katakana letter YA
    {0x3084, 0x30e4},  // Hiragana letter       YA -> Katakana letter YA
    {0x3085, 0x30e6},  // Hiragana letter small YU -> Katakana letter YU
    {0x3086, 0x30e6},  // Hiragana letter       YU -> Katakana letter YU
    {0x3087, 0x30e8},  // Hiragana letter small YO -> Katakana letter YO
    {0x3088, 0x30e8},  // Hiragana letter       YO -> Katakana letter YO
    // 'ra' row
    {0x3089, 0x30e9},  // Hiragana letter       RA -> Katakana letter RA
    {0x308a, 0x30ea},  // Hiragana letter       RI -> Katakana letter RI
    {0x308b, 0x30eb},  // Hiragana letter       RU -> Katakana letter RU
    {0x308c, 0x30ec},  // Hiragana letter       RE -> Katakana letter RE
    {0x308d, 0x30ed},  // Hiragana letter       RO -> Katakana letter RO
    // 'wa' row
    {0x308e, 0x30ef},  // Hiragana letter small WA -> Katakana letter WA
    {0x308f, 0x30ef},  // Hiragana letter       WA -> Katakana letter WA
    {0x3090, 0x30f0},  // Hiragana letter       WI -> Katakana letter WI
    {0x3091, 0x30f1},  // Hiragana letter       WE -> Katakana letter WE
    {0x3092, 0x30f2},  // Hiragana letter       WO -> Katakana letter WO
    // 'n'
    {0x3093, 0x30f3},  // Hiragana letter        N -> Katakana letter  N
    // 'ga' row
    {0x304c, 0x30ac},  // Hiragana letter       GA -> Katakana letter GA
    {0x304e, 0x30ae},  // Hiragana letter       GI -> Katakana letter GI
    {0x3050, 0x30b0},  // Hiragana letter       GU -> Katakana letter GU
    {0x3052, 0x30b2},  // Hiragana letter       GE -> Katakana letter GE
    {0x3054, 0x30b4},  // Hiragana letter       GO -> Katakana letter GO
    // 'za' row
    {0x3056, 0x30b6},  // Hiragana letter       ZA -> Katakana letter ZA
    {0x3058, 0x30b8},  // Hiragana letter       ZI -> Katakana letter ZI
    {0x305a, 0x30ba},  // Hiragana letter       ZU -> Katakana letter ZU
    {0x305c, 0x30bc},  // Hiragana letter       ZE -> Katakana letter ZE
    {0x305e, 0x30be},  // Hiragana letter       ZO -> Katakana letter ZO
    // 'da' row
    {0x3060, 0x30c0},  // Hiragana letter       DA -> Katakana letter DA
    {0x3062, 0x30c2},  // Hiragana letter       DI -> Katakana letter DI
    {0x3065, 0x30c5},  // Hiragana letter       DU -> Katakana letter DU
    {0x3067, 0x30c7},  // Hiragana letter       DE -> Katakana letter DE
    {0x3069, 0x30c9},  // Hiragana letter       DO -> Katakana letter DO
    // 'ba' row
    {0x3070, 0x30d0},  // Hiragana letter       BA -> Katakana letter BA
    {0x3073, 0x30d3},  // Hiragana letter       BI -> Katakana letter BI
    {0x3076, 0x30d6},  // Hiragana letter       BU -> Katakana letter BU
    {0x3079, 0x30d9},  // Hiragana letter       BE -> Katakana letter BE
    {0x307c, 0x30dc},  // Hiragana letter       BO -> Katakana letter BO
    // 'pa' row
    {0x3071, 0x30d1},  // Hiragana letter       PA -> Katakana letter PA
    {0x3074, 0x30d4},  // Hiragana letter       PI -> Katakana letter PI
    {0x3077, 0x30d7},  // Hiragana letter       PU -> Katakana letter PU
    {0x307a, 0x30da},  // Hiragana letter       PE -> Katakana letter PE
    {0x307d, 0x30dd},  // Hiragana letter       PO -> Katakana letter PO
    // Additional Hiragana
    {0x3094, 0x30f4},  // Hiragana letter       VU -> Katakana letter VU
    // Part 2: Common full-width characters -> ASCII characters.
    {0xff01, 33},   // ASCII !
    {0xff02, 34},   // ASCII "
    {0xff03, 35},   // ASCII #
    {0xff04, 36},   // ASCII $
    {0xff05, 37},   // ASCII %
    {0xff06, 38},   // ASCII &
    {0xff07, 39},   // ASCII '
    {0xff08, 40},   // ASCII (
    {0xff09, 41},   // ASCII )
    {0xff0a, 42},   // ASCII *
    {0xff0b, 43},   // ASCII +
    {0xff0c, 44},   // ASCII ,
    {0xff0d, 45},   // ASCII -
    {0xff0e, 46},   // ASCII .
    {0xff0f, 47},   // ASCII /
    {0xff10, 48},   // ASCII 0
    {0xff11, 49},   // ASCII 1
    {0xff12, 50},   // ASCII 2
    {0xff13, 51},   // ASCII 3
    {0xff14, 52},   // ASCII 4
    {0xff15, 53},   // ASCII 5
    {0xff16, 54},   // ASCII 6
    {0xff17, 55},   // ASCII 7
    {0xff18, 56},   // ASCII 8
    {0xff19, 57},   // ASCII 9
    {0xff1a, 58},   // ASCII :
    {0xff1b, 59},   // ASCII ;
    {0xff1c, 60},   // ASCII <
    {0xff1d, 61},   // ASCII =
    {0xff1e, 62},   // ASCII >
    {0xff1f, 63},   // ASCII ?
    {0xff20, 64},   // ASCII @
    {0xff21, 65},   // ASCII A
    {0xff22, 66},   // ASCII B
    {0xff23, 67},   // ASCII C
    {0xff24, 68},   // ASCII D
    {0xff25, 69},   // ASCII E
    {0xff26, 70},   // ASCII F
    {0xff27, 71},   // ASCII G
    {0xff28, 72},   // ASCII H
    {0xff29, 73},   // ASCII I
    {0xff2a, 74},   // ASCII J
    {0xff2b, 75},   // ASCII K
    {0xff2c, 76},   // ASCII L
    {0xff2d, 77},   // ASCII M
    {0xff2e, 78},   // ASCII N
    {0xff2f, 79},   // ASCII O
    {0xff30, 80},   // ASCII P
    {0xff31, 81},   // ASCII Q
    {0xff32, 82},   // ASCII R
    {0xff33, 83},   // ASCII S
    {0xff34, 84},   // ASCII T
    {0xff35, 85},   // ASCII U
    {0xff36, 86},   // ASCII V
    {0xff37, 87},   // ASCII W
    {0xff38, 88},   // ASCII X
    {0xff39, 89},   // ASCII Y
    {0xff3a, 90},   // ASCII Z
    {0xff3b, 91},   // ASCII [
    {0xff3c, 92},   // ASCII forward slash
    {0xff3d, 93},   // ASCII ]
    {0xff3e, 94},   // ASCII ^
    {0xff3f, 95},   // ASCII _
    {0xff40, 96},   // ASCII `
    {0xff41, 97},   // ASCII a
    {0xff42, 98},   // ASCII b
    {0xff43, 99},   // ASCII c
    {0xff44, 100},  // ASCII d
    {0xff45, 101},  // ASCII e
    {0xff46, 102},  // ASCII f
    {0xff47, 103},  // ASCII g
    {0xff48, 104},  // ASCII h
    {0xff49, 105},  // ASCII i
    {0xff4a, 106},  // ASCII j
    {0xff4b, 107},  // ASCII k
    {0xff4c, 108},  // ASCII l
    {0xff4d, 109},  // ASCII m
    {0xff4e, 110},  // ASCII n
    {0xff4f, 111},  // ASCII o
    {0xff50, 112},  // ASCII p
    {0xff51, 113},  // ASCII q
    {0xff52, 114},  // ASCII r
    {0xff53, 115},  // ASCII s
    {0xff54, 116},  // ASCII t
    {0xff55, 117},  // ASCII u
    {0xff56, 118},  // ASCII v
    {0xff57, 119},  // ASCII w
    {0xff58, 120},  // ASCII x
    {0xff59, 121},  // ASCII y
    {0xff5a, 122},  // ASCII z
    {0xff5b, 123},  // ASCII {
    {0xff5c, 124},  // ASCII |
    {0xff5d, 125},  // ASCII }
    {0xff5e, 126},  // ASCII ~
    {0x2018, 39},   // Left single quote -> ASCII apostrophe
    {0x2019, 39},   // Right single quote -> ASCII apostrophe
    {0x201c, 34},   // Left double quote -> ASCII quote
    {0x201d, 34},   // Right double quote -> ASCII quote
    // Part 3: Common ideographic punctuation marks -> ASCII.
    // Usually used in CJK.
    {0x3001, 44},  // ASCII ,
    {0x3002, 46},  // ASCII .
    // Part 4: Common diacritic Latin characters -> ASCII characters.
    {0x00c0, 65},   // À -> A
    {0x00c1, 65},   // Á -> A
    {0x00c2, 65},   // Â -> A
    {0x00c3, 65},   // Ã -> A
    {0x00c4, 65},   // Ä -> A
    {0x00c5, 65},   // Å -> A
    {0x00c7, 67},   // Ç -> C
    {0x00c8, 69},   // È -> E
    {0x00c9, 69},   // É -> E
    {0x00ca, 69},   // Ê -> E
    {0x00cb, 69},   // Ë -> E
    {0x00cc, 73},   // Ì -> I
    {0x00cd, 73},   // Í -> I
    {0x00ce, 73},   // Î -> I
    {0x00cf, 73},   // Ï -> I
    {0x00d0, 68},   // Ð -> D
    {0x00d1, 78},   // Ñ -> N
    {0x00d2, 79},   // Ò -> O
    {0x00d3, 79},   // Ó -> O
    {0x00d4, 79},   // Ô -> O
    {0x00d5, 79},   // Õ -> O
    {0x00d6, 79},   // Ö -> O
    {0x00d8, 79},   // Ø -> O
    {0x00d9, 85},   // Ù -> U
    {0x00da, 85},   // Ú -> U
    {0x00db, 85},   // Û -> U
    {0x00dc, 85},   // Ü -> U
    {0x00dd, 89},   // Ý -> Y
    {0x00e0, 97},   // à -> a
    {0x00e1, 97},   // á -> a
    {0x00e2, 97},   // â -> a
    {0x00e3, 97},   // ã -> a
    {0x00e4, 97},   // ä -> a
    {0x00e5, 97},   // å -> a
    {0x00e7, 99},   // ç -> c
    {0x00e8, 101},  // è -> e
    {0x00e9, 101},  // é -> e
    {0x00ea, 101},  // ê -> e
    {0x00eb, 101},  // ë -> e
    {0x00ec, 105},  // ì -> i
    {0x00ed, 105},  // í -> i
    {0x00ee, 105},  // î -> i
    {0x00ef, 105},  // ï -> i
    {0x00f0, 100},  // ð -> d
    {0x00f1, 110},  // ñ -> n
    {0x00f2, 111},  // ò -> o
    {0x00f3, 111},  // ó -> o
    {0x00f4, 111},  // ô -> o
    {0x00f5, 111},  // õ -> o
    {0x00f6, 111},  // ö -> o
    {0x00f8, 111},  // ø -> o
    {0x00f9, 117},  // ù -> u
    {0x00fa, 117},  // ú -> u
    {0x00fb, 117},  // û -> u
    {0x00fc, 117},  // ü -> u
    {0x00fd, 121},  // ý -> y
    {0x00ff, 121},  // ÿ -> y
    {0x0100, 65},   // Ā -> A
    {0x0101, 97},   // ā -> a
    {0x0102, 65},   // Ă -> A
    {0x0103, 97},   // ă -> a
    {0x0104, 65},   // Ą -> A
    {0x0105, 97},   // ą -> a
    {0x0106, 67},   // Ć -> C
    {0x0107, 99},   // ć -> c
    {0x0108, 67},   // Ĉ -> C
    {0x0109, 99},   // ĉ -> c
    {0x010a, 67},   // Ċ -> C
    {0x010b, 99},   // ċ -> c
    {0x010c, 67},   // Č -> C
    {0x010d, 99},   // č -> c
    {0x010e, 68},   // Ď -> D
    {0x010f, 100},  // ď -> d
    {0x0110, 68},   // Đ -> D
    {0x0111, 100},  // đ -> d
    {0x0112, 69},   // Ē -> E
    {0x0113, 101},  // ē -> e
    {0x0114, 69},   // Ĕ -> E
    {0x0115, 101},  // ĕ -> e
    {0x0116, 69},   // Ė -> E
    {0x0117, 101},  // ė -> e
    {0x0118, 69},   // Ę -> E
    {0x0119, 101},  // ę -> e
    {0x011a, 69},   // Ě -> E
    {0x011b, 101},  // ě -> e
    {0x011c, 71},   // Ĝ -> G
    {0x011d, 103},  // ĝ -> g
    {0x011e, 71},   // Ğ -> G
    {0x011f, 103},  // ğ -> g
    {0x0120, 71},   // Ġ -> G
    {0x0121, 103},  // ġ -> g
    {0x0122, 71},   // Ģ -> G
    {0x0123, 103},  // ģ -> g
    {0x0124, 72},   // Ĥ -> H
    {0x0125, 104},  // ĥ -> h
    {0x0126, 72},   // Ħ -> H
    {0x0127, 104},  // ħ -> h
    {0x0128, 73},   // Ĩ -> I
    {0x0129, 105},  // ĩ -> i
    {0x012a, 73},   // Ī -> I
    {0x012b, 105},  // ī -> i
    {0x012c, 73},   // Ĭ -> I
    {0x012d, 105},  // ĭ -> i
    {0x012e, 73},   // Į -> I
    {0x012f, 105},  // į -> i
    {0x0130, 73},   // İ -> I
    {0x0131, 105},  // ı -> i
    {0x0134, 74},   // Ĵ -> J
    {0x0135, 106},  // ĵ -> j
    {0x0136, 75},   // Ķ -> K
    {0x0137, 107},  // ķ -> k
    {0x0139, 76},   // Ĺ -> L
    {0x013a, 108},  // ĺ -> l
    {0x013b, 76},   // Ļ -> L
    {0x013c, 108},  // ļ -> l
    {0x013d, 76},   // Ľ -> L
    {0x013e, 108},  // ľ -> l
    {0x013f, 76},   // Ŀ -> L
    {0x0140, 108},  // ŀ -> l
    {0x0141, 76},   // Ł -> L
    {0x0142, 108},  // ł -> l
    {0x0143, 78},   // Ń -> N
    {0x0144, 110},  // ń -> n
    {0x0145, 78},   // Ņ -> N
    {0x0146, 110},  // ņ -> n
    {0x0147, 78},   // Ň -> N
    {0x0148, 110},  // ň -> n
    {0x014a, 78},   // Ŋ -> N
    {0x014b, 110},  // ŋ -> n
    {0x014c, 79},   // Ō -> O
    {0x014d, 111},  // ō -> o
    {0x014e, 79},   // Ŏ -> O
    {0x014f, 111},  // ŏ -> o
    {0x0150, 79},   // Ő -> O
    {0x0151, 111},  // ő -> o
    {0x0154, 82},   // Ŕ -> R
    {0x0155, 114},  // ŕ -> r
    {0x0156, 82},   // Ŗ -> R
    {0x0157, 114},  // ŗ -> r
    {0x0158, 82},   // Ř -> R
    {0x0159, 114},  // ř -> r
    {0x015a, 83},   // Ś -> S
    {0x015b, 115},  // ś -> s
    {0x015c, 83},   // Ŝ -> S
    {0x015d, 115},  // ŝ -> s
    {0x015e, 83},   // Ş -> S
    {0x015f, 115},  // ş -> s
    {0x0160, 83},   // Š -> S
    {0x0161, 115},  // š -> s
    {0x0162, 84},   // Ţ -> T
    {0x0163, 116},  // ţ -> t
    {0x0164, 84},   // Ť -> T
    {0x0165, 116},  // ť -> t
    {0x0166, 84},   // Ŧ -> T
    {0x0167, 116},  // ŧ -> t
    {0x0168, 85},   // Ũ -> U
    {0x0169, 117},  // ũ -> u
    {0x016a, 85},   // Ū -> U
    {0x016b, 117},  // ū -> u
    {0x016c, 85},   // Ŭ -> U
    {0x016d, 117},  // ŭ -> u
    {0x016e, 85},   // Ů -> U
    {0x016f, 117},  // ů -> u
    {0x0170, 85},   // Ű -> U
    {0x0171, 117},  // ű -> u
    {0x0172, 85},   // Ų -> U
    {0x0173, 117},  // ų -> u
    {0x0174, 87},   // Ŵ -> W
    {0x0175, 119},  // ŵ -> w
    {0x0176, 89},   // Ŷ -> Y
    {0x0177, 121},  // ŷ -> y
    {0x0178, 89},   // Ÿ -> Y
    {0x0179, 90},   // Ź -> Z
    {0x017a, 122},  // ź -> z
    {0x017b, 90},   // Ż -> Z
    {0x017c, 122},  // ż -> z
    {0x017d, 90},   // Ž -> Z
    {0x017e, 122},  // ž -> z
    {0x01a0, 79},   // Ơ -> O
    {0x01a1, 111},  // ơ -> o
    {0x01af, 85},   // Ư -> U
    {0x01b0, 117},  // ư -> u
    {0x01b5, 90},   // Ƶ -> Z
    {0x01b6, 122},  // ƶ -> z
    {0x0218, 83},   // Ș -> S
    {0x0219, 115},  // ș -> s
    {0x021a, 84},   // Ț -> T
    {0x021b, 116},  // ț -> t
    {0x1e00, 65},   // Ḁ -> A
    {0x1e01, 97},   // ḁ -> a
    {0x1e02, 66},   // Ḃ -> B
    {0x1e03, 98},   // ḃ -> b
    {0x1e04, 66},   // Ḅ -> B
    {0x1e05, 98},   // ḅ -> b
    {0x1e06, 66},   // Ḇ -> B
    {0x1e07, 98},   // ḇ -> b
    {0x1e08, 67},   // Ḉ -> C
    {0x1e09, 99},   // ḉ -> c
    {0x1e0a, 68},   // Ḋ -> D
    {0x1e0b, 100},  // ḋ -> d
    {0x1e0c, 68},   // Ḍ -> D
    {0x1e0d, 100},  // ḍ -> d
    {0x1e0e, 68},   // Ḏ -> D
    {0x1e0f, 100},  // ḏ -> d
    {0x1e10, 68},   // Ḑ -> D
    {0x1e11, 100},  // ḑ -> d
    {0x1e12, 68},   // Ḓ -> D
    {0x1e13, 100},  // ḓ -> d
    {0x1e14, 69},   // Ḕ -> E
    {0x1e15, 101},  // ḕ -> e
    {0x1e16, 69},   // Ḗ -> E
    {0x1e17, 101},  // ḗ -> e
    {0x1e18, 69},   // Ḙ -> E
    {0x1e19, 101},  // ḙ -> e
    {0x1e1a, 69},   // Ḛ -> E
    {0x1e1b, 101},  // ḛ -> e
    {0x1e1c, 69},   // Ḝ -> E
    {0x1e1d, 101},  // ḝ -> e
    {0x1e1e, 70},   // Ḟ -> F
    {0x1e1f, 102},  // ḟ -> f
    {0x1e20, 71},   // Ḡ -> G
    {0x1e21, 103},  // ḡ -> g
    {0x1e22, 72},   // Ḣ -> H
    {0x1e23, 104},  // ḣ -> h
    {0x1e24, 72},   // Ḥ -> H
    {0x1e25, 104},  // ḥ -> h
    {0x1e26, 72},   // Ḧ -> H
    {0x1e27, 104},  // ḧ -> h
    {0x1e28, 72},   // Ḩ -> H
    {0x1e29, 104},  // ḩ -> h
    {0x1e2a, 72},   // Ḫ -> H
    {0x1e2b, 104},  // ḫ -> h
    {0x1e2c, 73},   // Ḭ -> I
    {0x1e2d, 105},  // ḭ -> i
    {0x1e2e, 73},   // Ḯ -> I
    {0x1e2f, 105},  // ḯ -> i
    {0x1e30, 75},   // Ḱ -> K
    {0x1e31, 107},  // ḱ -> k
    {0x1e32, 75},   // Ḳ -> K
    {0x1e33, 107},  // ḳ -> k
    {0x1e34, 75},   // Ḵ -> K
    {0x1e35, 107},  // ḵ -> k
    {0x1e36, 76},   // Ḷ -> L
    {0x1e37, 108},  // ḷ -> l
    {0x1e38, 76},   // Ḹ -> L
    {0x1e39, 108},  // ḹ -> l
    {0x1e3b, 108},  // ḻ -> l
    {0x1e3c, 76},   // Ḽ -> L
    {0x1e3d, 108},  // ḽ -> l
    {0x1e3e, 77},   // Ḿ -> M
    {0x1e3f, 109},  // ḿ -> m
    {0x1e40, 77},   // Ṁ -> M
    {0x1e41, 109},  // ṁ -> m
    {0x1e42, 77},   // Ṃ -> M
    {0x1e43, 109},  // ṃ -> m
    {0x1e44, 78},   // Ṅ -> N
    {0x1e45, 110},  // ṅ -> n
    {0x1e46, 78},   // Ṇ -> N
    {0x1e47, 110},  // ṇ -> n
    {0x1e48, 78},   // Ṉ -> N
    {0x1e49, 110},  // ṉ -> n
    {0x1e4a, 78},   // Ṋ -> N
    {0x1e4b, 110},  // ṋ -> n
    {0x1e4c, 79},   // Ṍ -> O
    {0x1e4d, 111},  // ṍ -> o
    {0x1e4e, 79},   // Ṏ -> O
    {0x1e4f, 111},  // ṏ -> o
    {0x1e50, 79},   // Ṑ -> O
    {0x1e51, 111},  // ṑ -> o
    {0x1e52, 79},   // Ṓ -> O
    {0x1e53, 111},  // ṓ -> o
    {0x1e54, 80},   // Ṕ -> P
    {0x1e55, 112},  // ṕ -> p
    {0x1e56, 80},   // Ṗ -> P
    {0x1e57, 112},  // ṗ -> p
    {0x1e58, 82},   // Ṙ -> R
    {0x1e59, 114},  // ṙ -> r
    {0x1e5a, 82},   // Ṛ -> R
    {0x1e5b, 114},  // ṛ -> r
    {0x1e5c, 82},   // Ṝ -> R
    {0x1e5d, 114},  // ṝ -> r
    {0x1e5e, 82},   // Ṟ -> R
    {0x1e5f, 114},  // ṟ -> r
    {0x1e60, 83},   // Ṡ -> S
    {0x1e61, 115},  // ṡ -> s
    {0x1e62, 83},   // Ṣ -> S
    {0x1e63, 115},  // ṣ -> s
    {0x1e64, 83},   // Ṥ -> S
    {0x1e65, 115},  // ṥ -> s
    {0x1e66, 83},   // Ṧ -> S
    {0x1e67, 115},  // ṧ -> s
    {0x1e68, 83},   // Ṩ -> S
    {0x1e69, 115},  // ṩ -> s
    {0x1e6a, 84},   // Ṫ -> T
    {0x1e6b, 116},  // ṫ -> t
    {0x1e6c, 84},   // Ṭ -> T
    {0x1e6d, 116},  // ṭ -> t
    {0x1e6e, 84},   // Ṯ -> T
    {0x1e6f, 116},  // ṯ -> t
    {0x1e70, 84},   // Ṱ -> T
    {0x1e71, 116},  // ṱ -> t
    {0x1e72, 85},   // Ṳ -> U
    {0x1e73, 117},  // ṳ -> u
    {0x1e74, 85},   // Ṵ -> U
    {0x1e75, 117},  // ṵ -> u
    {0x1e76, 85},   // Ṷ -> U
    {0x1e77, 117},  // ṷ -> u
    {0x1e78, 85},   // Ṹ -> U
    {0x1e79, 117},  // ṹ -> u
    {0x1e7a, 85},   // Ṻ -> U
    {0x1e7b, 117},  // ṻ -> u
    {0x1e7c, 86},   // Ṽ -> V
    {0x1e7d, 118},  // ṽ -> v
    {0x1e7e, 86},   // Ṿ -> V
    {0x1e7f, 118},  // ṿ -> v
    {0x1e80, 87},   // Ẁ -> W
    {0x1e81, 119},  // ẁ -> w
    {0x1e82, 87},   // Ẃ -> W
    {0x1e83, 119},  // ẃ -> w
    {0x1e84, 87},   // Ẅ -> W
    {0x1e85, 119},  // ẅ -> w
    {0x1e86, 87},   // Ẇ -> W
    {0x1e87, 119},  // ẇ -> w
    {0x1e88, 87},   // Ẉ -> W
    {0x1e89, 119},  // ẉ -> w
    {0x1e8a, 88},   // Ẋ -> X
    {0x1e8b, 120},  // ẋ -> x
    {0x1e8c, 88},   // Ẍ -> X
    {0x1e8d, 120},  // ẍ -> x
    {0x1e8e, 89},   // Ẏ -> Y
    {0x1e8f, 121},  // ẏ -> y
    {0x1e90, 90},   // Ẑ -> Z
    {0x1e91, 122},  // ẑ -> z
    {0x1e92, 90},   // Ẓ -> Z
    {0x1e93, 122},  // ẓ -> z
    {0x1e94, 90},   // Ẕ -> Z
    {0x1e95, 122},  // ẕ -> z
    {0x1e96, 104},  // ẖ -> h
    {0x1e97, 116},  // ẗ -> t
    {0x1e98, 119},  // ẘ -> w
    {0x1e99, 121},  // ẙ -> y
    {0x1e9a, 97},   // ẚ -> a
    {0x1e9b, 102},  // ẛ -> f
    {0x1ea0, 65},   // Ạ -> A
    {0x1ea1, 97},   // ạ -> a
    {0x1ea2, 65},   // Ả -> A
    {0x1ea3, 97},   // ả -> a
    {0x1ea4, 65},   // Ấ -> A
    {0x1ea5, 97},   // ấ -> a
    {0x1ea6, 65},   // Ầ -> A
    {0x1ea7, 97},   // ầ -> a
    {0x1ea8, 65},   // Ẩ -> A
    {0x1ea9, 97},   // ẩ -> a
    {0x1eaa, 65},   // Ẫ -> A
    {0x1eab, 97},   // ẫ -> a
    {0x1eac, 65},   // Ậ -> A
    {0x1ead, 97},   // ậ -> a
    {0x1eae, 65},   // Ắ -> A
    {0x1eaf, 97},   // ắ -> a
    {0x1eb0, 65},   // Ằ -> A
    {0x1eb1, 97},   // ằ -> a
    {0x1eb2, 65},   // Ẳ -> A
    {0x1eb3, 97},   // ẳ -> a
    {0x1eb4, 65},   // Ẵ -> A
    {0x1eb5, 97},   // ẵ -> a
    {0x1eb6, 65},   // Ặ -> A
    {0x1eb7, 97},   // ặ -> a
    {0x1eb8, 69},   // Ẹ -> E
    {0x1eb9, 101},  // ẹ -> e
    {0x1eba, 69},   // Ẻ -> E
    {0x1ebb, 101},  // ẻ -> e
    {0x1ebc, 69},   // Ẽ -> E
    {0x1ebd, 101},  // ẽ -> e
    {0x1ebe, 69},   // Ế -> E
    {0x1ebf, 101},  // ế -> e
    {0x1ec0, 69},   // Ề -> E
    {0x1ec1, 101},  // ề -> e
    {0x1ec2, 69},   // Ể -> E
    {0x1ec3, 101},  // ể -> e
    {0x1ec4, 69},   // Ễ -> E
    {0x1ec5, 101},  // ễ -> e
    {0x1ec6, 69},   // Ệ -> E
    {0x1ec7, 101},  // ệ -> e
    {0x1ec8, 73},   // Ỉ -> I
    {0x1ec9, 105},  // ỉ -> i
    {0x1eca, 73},   // Ị -> I
    {0x1ecb, 105},  // ị -> i
    {0x1ecc, 79},   // Ọ -> O
    {0x1ecd, 111},  // ọ -> o
    {0x1ece, 79},   // Ỏ -> O
    {0x1ecf, 111},  // ỏ -> o
    {0x1ed0, 79},   // Ố -> O
    {0x1ed1, 111},  // ố -> o
    {0x1ed2, 79},   // Ồ -> O
    {0x1ed3, 111},  // ồ -> o
    {0x1ed4, 79},   // Ổ -> O
    {0x1ed5, 111},  // ổ -> o
    {0x1ed6, 79},   // Ỗ -> O
    {0x1ed7, 111},  // ỗ -> o
    {0x1ed8, 79},   // Ộ -> O
    {0x1ed9, 111},  // ộ -> o
    {0x1eda, 79},   // Ớ -> O
    {0x1edb, 111},  // ớ -> o
    {0x1edc, 79},   // Ờ -> O
    {0x1edd, 111},  // ờ -> o
    {0x1ede, 79},   // Ở -> O
    {0x1edf, 111},  // ở -> o
    {0x1ee0, 79},   // Ỡ -> O
    {0x1ee1, 111},  // ỡ -> o
    {0x1ee2, 79},   // Ợ -> O
    {0x1ee3, 111},  // ợ -> o
    {0x1ee4, 85},   // Ụ -> U
    {0x1ee5, 117},  // ụ -> u
    {0x1ee6, 85},   // Ủ -> U
    {0x1ee7, 117},  // ủ -> u
    {0x1ee8, 85},   // Ứ -> U
    {0x1ee9, 117},  // ứ -> u
    {0x1eea, 85},   // Ừ -> U
    {0x1eeb, 117},  // ừ -> u
    {0x1eec, 85},   // Ử -> U
    {0x1eed, 117},  // ử -> u
    {0x1eee, 85},   // Ữ -> U
    {0x1eef, 117},  // ữ -> u
    {0x1ef0, 85},   // Ự -> U
    {0x1ef1, 117},  // ự -> u
    {0x1ef2, 89},   // Ỳ -> Y
    {0x1ef3, 121},  // ỳ -> y
    {0x1ef4, 89},   // Ỵ -> Y
    {0x1ef5, 121},  // ỵ -> y
    {0x1ef6, 89},   // Ỷ -> Y
    {0x1ef7, 121},  // ỷ -> y
    {0x1ef8, 89},   // Ỹ -> Y
    {0x1ef9, 121},  // ỹ -> y
};

}  // namespace

const std::unordered_map<char16_t, char16_t> *GetNormalizationMap() {
  // The map is allocated dynamically the first time this function is executed.
  static const std::unordered_map<char16_t, char16_t> *const normalization_map =
      [] {
        auto *map = new std::unordered_map<char16_t, char16_t>();
        // Size of all the mappings is about 2.5 KiB.
        constexpr int numMappings =
            sizeof(kNormalizationMappings) / sizeof(NormalizationPair);
        map->reserve(numMappings);
        for (size_t i = 0; i < numMappings; ++i) {
          map->emplace(kNormalizationMappings[i].from,
                       kNormalizationMappings[i].to);
        }
        return map;
      }();

  return normalization_map;
}

}  // namespace lib
}  // namespace icing
