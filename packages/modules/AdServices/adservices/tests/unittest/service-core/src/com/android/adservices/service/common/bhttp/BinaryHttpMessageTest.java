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

package com.android.adservices.service.common.bhttp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Test examples from <a
 * href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html">Binary
 * Representation of HTTP Messages</a>
 */
public class BinaryHttpMessageTest {

    private BinaryHttpMessageDeserializer mBinaryHttpMessageDeserializer;

    @Before
    public void setup() {
        mBinaryHttpMessageDeserializer = new BinaryHttpMessageDeserializer();
    }

    @Test
    public void testEncodeAndDecodeRequestGetNoBody() {
        // GET /hello.txt HTTP/1.1
        // User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
        // Host: www.example.com
        // Accept-Language: en, mi
        testEncodeAndDecode(
                // common_typos_disable
                // 00000000: 00034745 54056874 74707300 0a2f6865  ..GET.https../he
                // 00000010: 6c6c6f2e 74787440 6c0a7573 65722d61  llo.txt@l.user-a
                // 00000020: 67656e74 34637572 6c2f372e 31362e33  gent4curl/7.16.3
                // 00000030: 206c6962 6375726c 2f372e31 362e3320   libcurl/7.16.3
                // 00000040: 4f70656e 53534c2f 302e392e 376c207a  OpenSSL/0.9.7l z
                // 00000050: 6c69622f 312e322e 3304686f 73740f77  lib/1.2.3.host.w
                // 00000060: 77772e65 78616d70 6c652e63 6f6d0f61  ww.example.com.a
                // 00000070: 63636570 742d6c61 6e677561 67650665  ccept-language.e
                // 00000080: 6e2c206d 6900                        n, mi..
                // common_typos_enable
                new int[] {
                    0x00034745, 0x54056874, 0x74707300, 0x0a2f6865, 0x6c6c6f2e, 0x74787440,
                    0x6c0a7573, 0x65722d61, 0x67656e74, 0x34637572, 0x6c2f372e, 0x31362e33,
                    0x206c6962, 0x6375726c, 0x2f372e31, 0x362e3320, 0x4f70656e, 0x53534c2f,
                    0x302e392e, 0x376c207a, 0x6c69622f, 0x312e322e, 0x3304686f, 0x73740f77,
                    0x77772e65, 0x78616d70, 0x6c652e63, 0x6f6d0f61, 0x63636570, 0x742d6c61,
                    0x6e677561, 0x67650665, 0x6e2c206d, 0x69000000
                },
                3,
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("GET")
                                        .setScheme("https")
                                        .setAuthority("")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField(
                                                "User-Agent",
                                                "curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l"
                                                        + " zlib/1.2.3")
                                        .appendField("Host", "www.example.com")
                                        .appendField("Accept-Language", "en, mi")
                                        .build())
                        .build());
    }

    @Test
    public void testEncodeAndDecodeRequestGetWithAuthority() {
        // GET https://www.example.com/hello.txt HTTP/1.1
        // User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
        // Accept-Language: en, mi
        testEncodeAndDecode(
                // common_typos_disable
                // 00000000: 00034745 54056874 7470730f 7777772e  ..GET.https.www.
                // 00000010: 6578616d 706c652e 636f6d0a 2f68656c  example.com./hel
                // 00000020: 6c6f2e74 78744057 0a757365 722d6167  lo.txt@W.user-ag
                // 00000030: 656e7434 6375726c 2f372e31 362e3320  ent4curl/7.16.3
                // 00000040: 6c696263 75726c2f 372e3136 2e33204f  libcurl/7.16.3 O
                // 00000050: 70656e53 534c2f30 2e392e37 6c207a6c  penSSL/0.9.7l zl
                // 00000060: 69622f31 2e322e33 0f616363 6570742d  ib/1.2.3.accept-
                // 00000070: 6c616e67 75616765 06656e2c 206d6900  language.en, mi.
                // common_typos_enable
                new int[] {
                    0x00034745, 0x54056874, 0x7470730f, 0x7777772e, 0x6578616d, 0x706c652e,
                    0x636f6d0a, 0x2f68656c, 0x6c6f2e74, 0x78744057, 0x0a757365, 0x722d6167,
                    0x656e7434, 0x6375726c, 0x2f372e31, 0x362e3320, 0x6c696263, 0x75726c2f,
                    0x372e3136, 0x2e33204f, 0x70656e53, 0x534c2f30, 0x2e392e37, 0x6c207a6c,
                    0x69622f31, 0x2e322e33, 0x0f616363, 0x6570742d, 0x6c616e67, 0x75616765,
                    0x06656e2c, 0x206d6900
                },
                1,
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("GET")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField(
                                                "User-Agent",
                                                "curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l"
                                                        + " zlib/1.2.3")
                                        .appendField("Accept-Language", "en, mi")
                                        .build())
                        .build());
    }

    @Test
    public void testEncodeAndDecodeRequestPostBody() {

        // POST /hello.txt HTTP/1.1
        // User-Agent: not/telling
        // Host: www.example.com
        // Accept-Language: en
        //
        // Some body that I used to post.
        testEncodeAndDecode(
                // common_typos_disable
                // 00000000: 0004504f 53540568 74747073 000a2f68  ..POST.https../h
                // 00000010: 656c6c6f 2e747874 3f0a7573 65722d61  ello.txt?.user-a
                // 00000020: 67656e74 0b6e6f74 2f74656c 6c696e67  gent.not/telling
                // 00000030: 04686f73 740f7777 772e6578 616d706c  .host.www.exampl
                // 00000040: 652e636f 6d0f6163 63657074 2d6c616e  e.com.accept-lan
                // 00000050: 67756167 6502656e 20536f6d 6520626f  guage.en Some bo
                // 00000060: 64792074 68617420 49207573 65642074  dy that I used t
                // 00000070: 6f20706f 73742e0d 0a                 o post....
                // common_typos_enable
                new int[] {
                    0x0004504f, 0x53540568, 0x74747073, 0x000a2f68, 0x656c6c6f, 0x2e747874,
                    0x3f0a7573, 0x65722d61, 0x67656e74, 0x0b6e6f74, 0x2f74656c, 0x6c696e67,
                    0x04686f73, 0x740f7777, 0x772e6578, 0x616d706c, 0x652e636f, 0x6d0f6163,
                    0x63657074, 0x2d6c616e, 0x67756167, 0x6502656e, 0x20536f6d, 0x6520626f,
                    0x64792074, 0x68617420, 0x49207573, 0x65642074, 0x6f20706f, 0x73742e0d,
                    0x0a000000
                },
                3,
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField("User-Agent", "not/telling")
                                        .appendField("Host", "www.example.com")
                                        .appendField("Accept-Language", "en")
                                        .build())
                        .setContent("Some body that I used to post.\r\n".getBytes())
                        .build());
    }

    @Test
    public void testRequestEquals() {
        assertEquals(
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField("User-Agent", "not" + "/telling")
                                        .build())
                        .setContent("hello, world!\r\n".getBytes())
                        .build(),
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField("User-Agent", "not" + "/telling")
                                        .build())
                        .setContent("hello, world!\r\n".getBytes())
                        .build());
    }

    @Test
    public void testRequestNotEqual() {
        BinaryHttpMessage original =
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder().appendField("User-Agent", "not/telling").build())
                        .setContent("hello, world!\r\n".getBytes())
                        .build();

        BinaryHttpMessage differentControlData =
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("PUT")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder().appendField("User-Agent", "not/telling").build())
                        .setContent("hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentControlData);

        BinaryHttpMessage differentHeader =
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder().appendField("User-Agent", "told/you").build())
                        .setContent("hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentHeader);

        BinaryHttpMessage noHeader =
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(Fields.EMPTY_FIELDS)
                        .setContent("hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, noHeader);

        BinaryHttpMessage differentBody =
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder().appendField("User-Agent", "not/telling").build())
                        .setContent("goodbye, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentBody);

        BinaryHttpMessage noBody =
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("POST")
                                        .setScheme("https")
                                        .setAuthority("www.example.com")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder().appendField("User-Agent", "not/telling").build())
                        .build();
        assertNotEquals(original, noBody);
    }

    @Test
    public void testEncodeAndDecodeResponseNoBody() {
        // HTTP/1.1 404 Not Found
        // Server: Apache
        testEncodeAndDecode(
                // common_typos_disable
                // 0141940e 06736572 76657206 41706163  .A...server.Apac
                // 686500                               he..
                // common_typos_enable
                new int[] {0x0141940e, 0x06736572, 0x76657206, 0x41706163, 0x68650000},
                2,
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(404).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .build());
    }

    @Test
    public void testEncodeAndDecodeResponseWithBody() {
        // HTTP/1.1 200 OK
        // Server: Apache
        //
        // Hello, world!
        testEncodeAndDecode(
                // common_typos_disable
                // 0140c80e 06736572 76657206 41706163  .@...server.Apac
                // 68650f48 656c6c6f 2c20776f 726c6421  he.Hello, world!
                // 0d0a                                 ....
                // common_typos_enable
                new int[] {
                    0x0140c80e,
                    0x06736572,
                    0x76657206,
                    0x41706163,
                    0x68650f48,
                    0x656c6c6f,
                    0x2c20776f,
                    0x726c6421,
                    0x0d0a0000
                },
                2,
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build());
    }

    @Test
    public void testEncodeAndDecodeMultiInformationalWithBody() {
        // HTTP/1.1 102 Processing
        // Running: "sleep 15"
        //
        // HTTP/1.1 103 Early Hints
        // Link: </style.css>; rel=preload; as=style
        // Link: </script.js>; rel=preload; as=script
        //
        // HTTP/1.1 200 OK
        // Date: Mon, 27 Jul 2009 12:28:53 GMT
        // Server: Apache
        // Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
        // ETag: "34aa387-d-1568eb00"
        // Accept-Ranges: bytes
        // Content-Length: 51
        // Vary: Accept-Encoding
        // Content-Type: text/plain
        //
        // Hello World! My content includes a trailing CRLF.
        testEncodeAndDecode(
                // common_typos_disable
                // 01406613 0772756e 6e696e67 0a22736c  .@f..running."sl
                // 65657020 31352240 67405304 6c696e6b  eep 15"@g@S.link
                // 233c2f73 74796c65 2e637373 3e3b2072  #</style.css>; r
                // 656c3d70 72656c6f 61643b20 61733d73  el=preload; as=s
                // 74796c65 046c696e 6b243c2f 73637269  tyle.link$</scri
                // 70742e6a 733e3b20 72656c3d 7072656c  pt.js>; rel=prel
                // 6f61643b 2061733d 73637269 707440c8  oad; as=script@.
                // 40ca0464 6174651d 4d6f6e2c 20323720  @..date.Mon, 27
                // 4a756c20 32303039 2031323a 32383a35  Jul 2009 12:28:5
                // 3320474d 54067365 72766572 06417061  3 GMT.server.Apa
                // 6368650d 6c617374 2d6d6f64 69666965  che.last-modifie
                // 641d5765 642c2032 32204a75 6c203230  d.Wed, 22 Jul 20
                // 30392031 393a3135 3a353620 474d5404  09 19:15:56 GMT.
                // 65746167 14223334 61613338 372d642d  etag."34aa387-d-
                // 31353638 65623030 220d6163 63657074  1568eb00".accept
                // 2d72616e 67657305 62797465 730e636f  -ranges.bytes.co
                // 6e74656e 742d6c65 6e677468 02353104  ntent-length.51.
                // 76617279 0f416363 6570742d 456e636f  vary.Accept-Enco
                // 64696e67 0c636f6e 74656e74 2d747970  ding.content-typ
                // 650a7465 78742f70 6c61696e 3348656c  e.text/plain3Hel
                // 6c6f2057 6f726c64 21204d79 20636f6e  lo World! My con
                // 74656e74 20696e63 6c756465 73206120  tent includes a
                // 74726169 6c696e67 2043524c 462e0d0a  trailing CRLF...
                // common_typos_enable
                new int[] {
                    0x01406613, 0x0772756e, 0x6e696e67, 0x0a22736c, 0x65657020, 0x31352240,
                    0x67405304, 0x6c696e6b, 0x233c2f73, 0x74796c65, 0x2e637373, 0x3e3b2072,
                    0x656c3d70, 0x72656c6f, 0x61643b20, 0x61733d73, 0x74796c65, 0x046c696e,
                    0x6b243c2f, 0x73637269, 0x70742e6a, 0x733e3b20, 0x72656c3d, 0x7072656c,
                    0x6f61643b, 0x2061733d, 0x73637269, 0x707440c8, 0x40ca0464, 0x6174651d,
                    0x4d6f6e2c, 0x20323720, 0x4a756c20, 0x32303039, 0x2031323a, 0x32383a35,
                    0x3320474d, 0x54067365, 0x72766572, 0x06417061, 0x6368650d, 0x6c617374,
                    0x2d6d6f64, 0x69666965, 0x641d5765, 0x642c2032, 0x32204a75, 0x6c203230,
                    0x30392031, 0x393a3135, 0x3a353620, 0x474d5404, 0x65746167, 0x14223334,
                    0x61613338, 0x372d642d, 0x31353638, 0x65623030, 0x220d6163, 0x63657074,
                    0x2d72616e, 0x67657305, 0x62797465, 0x730e636f, 0x6e74656e, 0x742d6c65,
                    0x6e677468, 0x02353104, 0x76617279, 0x0f416363, 0x6570742d, 0x456e636f,
                    0x64696e67, 0x0c636f6e, 0x74656e74, 0x2d747970, 0x650a7465, 0x78742f70,
                    0x6c61696e, 0x3348656c, 0x6c6f2057, 0x6f726c64, 0x21204d79, 0x20636f6e,
                    0x74656e74, 0x20696e63, 0x6c756465, 0x73206120, 0x74726169, 0x6c696e67,
                    0x2043524c, 0x462e0d0a
                },
                0,
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(103)
                                                        .appendHeaderField(
                                                                "Link",
                                                                "</style.css>;"
                                                                        + " rel=preload;"
                                                                        + " as=style")
                                                        .appendHeaderField(
                                                                "Link",
                                                                "</script.js>;"
                                                                        + " rel=preload;"
                                                                        + " as=script")
                                                        .build())
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField("Date", "Mon, 27 Jul 2009 12:28:53 GMT")
                                        .appendField("Server", "Apache")
                                        .appendField(
                                                "Last-Modified", "Wed, 22 Jul 2009 19:15:56 GMT")
                                        .appendField("ETag", "\"34aa387-d-1568eb00\"")
                                        .appendField("Accept-Ranges", "bytes")
                                        .appendField("Content-Length", "51")
                                        .appendField("Vary", "Accept-Encoding")
                                        .appendField("Content-Type", "text/plain")
                                        .build())
                        .setContent(
                                "Hello World! My content includes a trailing CRLF.\r\n".getBytes())
                        .build());
    }

    @Test
    public void testResponseEquals() {
        assertEquals(
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build(),
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build());
    }

    @Test
    public void testResponseNotEqual() {
        BinaryHttpMessage original =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();

        BinaryHttpMessage differentStatus =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(201)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentStatus);

        BinaryHttpMessage differentHeader =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "python3").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentHeader);

        BinaryHttpMessage noHeader =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.EMPTY_FIELDS)
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, noHeader);

        BinaryHttpMessage differentBody =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Goodbye, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentBody);

        BinaryHttpMessage noBody =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(102)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .build();
        assertNotEquals(original, noBody);

        BinaryHttpMessage differentInformational =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder()
                                        .setFinalStatusCode(200)
                                        .addInformativeResponse(
                                                InformativeResponse.builder()
                                                        .setInformativeStatusCode(198)
                                                        .appendHeaderField(
                                                                "Running", "\"sleep 15\"")
                                                        .build())
                                        .build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, differentInformational);

        BinaryHttpMessage noInformational =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        assertNotEquals(original, noInformational);
    }

    @Test
    public void testPaddingRequest() {
        // GET /hello.txt HTTP/1.1
        // User-Agent: curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l zlib/1.2.3
        // Host: www.example.com
        // Accept-Language: en, mi
        testPadding(
                BinaryHttpMessage.knownLengthRequestBuilder(
                                RequestControlData.builder()
                                        .setMethod("GET")
                                        .setScheme("https")
                                        .setAuthority("")
                                        .setPath("/hello.txt")
                                        .build())
                        .setHeaderFields(
                                Fields.builder()
                                        .appendField(
                                                "User-Agent",
                                                "curl/7.16.3 libcurl/7.16.3 OpenSSL/0.9.7l"
                                                        + " zlib/1.2.3")
                                        .appendField("Host", "www.example.com")
                                        .appendField("Accept-Language", "en, mi")
                                        .build())
                        .build(),
                10);
    }

    @Test
    public void testPaddingResponse() {
        // HTTP/1.1 200 OK
        // Server: Apache
        //
        // Hello, world!
        testPadding(
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build(),
                10);
    }

    private void testPadding(BinaryHttpMessage message, int paddingLength) {
        final byte[] encoded = message.serialize();

        BinaryHttpMessage paddedMessage =
                BinaryHttpMessage.builder()
                        .setFramingIndicator(message.getFramingIndicator())
                        .setControlData(message.getControlData())
                        .setHeaderFields(message.getHeaderFields())
                        .setContent(message.getContent())
                        .setPaddingLength(paddingLength)
                        .build();

        byte[] paddedEncoded = paddedMessage.serialize();

        assertEquals(paddedEncoded.length, encoded.length + paddingLength);
        assertArrayStartWithAndPaddedWithZeros(encoded, paddedEncoded);

        assertEquals(
                mBinaryHttpMessageDeserializer.deserialize(encoded),
                mBinaryHttpMessageDeserializer.deserialize(paddedEncoded));
    }

    private void testEncodeAndDecode(
            int[] expectedEncodedMessage, int trim, BinaryHttpMessage message) {
        final byte[] expectedEncodedBytes = fromIntArray(expectedEncodedMessage, trim);
        final byte[] encodedMessage = message.serialize();
        assertArrayStartWithAndPaddedWithZeros(expectedEncodedBytes, encodedMessage);
        assertEquals(message, mBinaryHttpMessageDeserializer.deserialize(expectedEncodedBytes));
    }

    private static void assertArrayStartWithAndPaddedWithZeros(byte[] target, byte[] tested) {
        assertArrayEquals(target, Arrays.copyOf(tested, target.length));
        for (int i = target.length; i < tested.length; i++) {
            assertEquals(0, tested[i]);
        }
    }

    private byte[] fromIntArray(int[] input, int trim) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(input.length * Integer.BYTES);
        for (int i : input) {
            byteBuffer.putInt(i);
        }
        byte[] result = new byte[byteBuffer.capacity() - trim];
        byteBuffer.position(0);
        byteBuffer.get(result);
        return result;
    }
}
