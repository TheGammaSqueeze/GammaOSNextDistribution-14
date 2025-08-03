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
package com.android.bluetooth.btservice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class to generate a default Device Bloomfilter
 */
public class DeviceBloomfilterGenerator {
    public static final String BLOOM_FILTER_DEFAULT =
            "01070000013b23cef3cd0e063e5dd15a"
            + "1a3f14b8a2d6974ab2e5a2d37f2efa97"
            + "10e526000ae8728c41445c9a1387c123"
            + "dc63675c0b8da3d365cde65b9edf153d"
            + "12d3a1ecdf9b78b3b2f86bc294ccf7ea"
            + "f650e1fa767bcaad3b61520125d38364"
            + "4cb480d820122ad455e7e422e9bc51fd"
            + "c442628ed66154916130be24212e4f44"
            + "efed5a6bc9b7064fa7b2efe86dd4e801"
            + "72c65b972a7524b370a2bca955429385"
            + "a405671d87ead027e7cb4080713dd0bf"
            + "6b440048b14d3d55d41d1f4497143b98"
            + "3b939c0bb686f026aa3c42df96bcab6a"
            + "542f9b8b62cd30e76ac744b4a185c7aa"
            + "3433dd714e95c0f268449c56e904a4d4"
            + "8bf9d242f99fbbe3e259ee97cbafd1f7"
            + "65306274f54f67b7dfbf2423b8ef8fd6"
            + "ee3fca0e2217bb351bd3347c610fa3f7"
            + "7e5ec2d7b931f657d61784fe59e2516c"
            + "9c8f8f4bcffe0a247ed16d93347a818a"
            + "a798320da96f05dbee4c5cf31661121f"
            + "e0e6e6ce9b657df24a7b8e0b8a34e443"
            + "79dcb270d856a5431a2b6416464e9327"
            + "22bb423d6f4e620c33a5f2b1d02f9a8f"
            + "521f7b49947284af61c0dfc4d0e64ccd"
            + "1df147ae9999fdf9a3538c0ad83ee7d5"
            + "d066f0f4759bcb5c46b7c3fd57697b88"
            + "5a8f82a77d927617a6ea077ff352ff18"
            + "1ab520f1cc0d73f688b55c37761e0be7"
            + "e543e33accef44fa212f2b746a239bfd"
            + "b139e39439ffa76919419b79e4505d17"
            + "0b412ace6f21c6c34bff54eb2e16429a"
            + "cbc691d3c17aa9e0590e3d4d3acc9349"
            + "0d67e7cfbf4dfa9aeaadfead7770af8f"
            + "fb827e2376d30d027cc949712dbce0f7"
            + "3bb193dbf9201a59632ba6daf11b8a92"
            + "6bea34175531df805afd72792c9ebada"
            + "823a0b677c4ee75d745806a98a4dd754"
            + "2f5e5f665e3280385a416e94ccd8eb88"
            + "a949d2daaee0f11c238fed182e1c234c"
            + "c4021288a0f7f31807b735ea96e3e4fe"
            + "66d07484a2336971d6ac0e6a79967116"
            + "cc9eac2921ea51ec822fcc5f90c0f6b4"
            + "96845542dfe8fbd6299e7d2af66ce423"
            + "a7346d1af0f5bca2f261e9a247e214a5"
            + "aecf8d19f2e368d7f0ea9699bc313ccf"
            + "ccdb8f759d9bf4ee42a49cda2021eba7"
            + "71add727d5d8cf35143fd4ffc595ee83"
            + "6d293113cd9ddcea69c009c6f94e4605"
            + "f96efe314bbad0e5fa449b35e24d1121"
            + "c1cbcfbacd3bad9759bb5028033ebfc4"
            + "8ac390285e7b41195fa4c4512cb48bd7"
            + "2787f52eb8d260e6a9e2b02d32d57c04"
            + "fd236b933cb365d2ebc99c30fb972ac1"
            + "fcd1afcf4087c4d612eb1fefc9a03e78"
            + "de594bc828e3b1aaddb46b7f3d2e0916"
            + "8c324e1059e2d6b8535c34e4ab05bc13"
            + "adaf2d75db9d9c8f0891541b573f5782"
            + "a543f214b34bfdad7373bd6703d4b1ae"
            + "3793910ad3ccceebda27f714df06c63a"
            + "94ac90a3044f9c9494ffdc7cb050a750"
            + "0d647262b98a7f74378f525ba945ddc7"
            + "a9926b67c553b37ca370ac9016e6b34d"
            + "5966a6571bc62dfb0fea8906ce4e0739"
            + "3ff747c356734343bcdc2362baa97e2a"
            + "eb37244316ac6d0f91e0c6dada3f19d2"
            + "21f4f309db772bcca9128ee94b11dca5"
            + "58e678deabfd506f3acf269c0cdd4d66"
            + "951567041afd88acfa5afff876f024bb"
            + "7e72db189f9f9e77782aef5f565ceb12"
            + "1b9cea8200c797bf46f9e086bb6c45c7"
            + "2a8a7f521523158d005ba13f72866e4b"
            + "281abb7c01bc16e666b3d9c49ac4ef8a"
            + "d45b4a63a2d8318cfd6387fa59fc9c1e"
            + "a7f753d4a2a12a9e802ecaf24ded9075"
            + "4c476cb2d1f547ecabe06180471cf5b2"
            + "18099f595df1f96eb9bb301da60853cb"
            + "3db3ee16dc09f5c167632cb742f9b631"
            + "35ebf72aaad9f8fbd44f15d9ba77b7c2"
            + "9bc2873378bd433c0d27258dbf095c75"
            + "dd7b4ed56b2db02331a5b3817473c6b5"
            + "b3228749bf1fa16cd88903276b12ac9d"
            + "949042a04c364725f27644fd082e8e1b"
            + "2bcaa9ac54b170a67862fd3325e09896"
            + "bdf499eb1a933d255bb7bd58011379a6"
            + "20da77c55a7484c0aa19681a8fb71b8b"
            + "5f10efa2cbaf518a071651b899961dcd"
            + "953d695f8187a0a3249db6afc81492f1"
            + "03de215ca8af5c62bda273e0c46f6d4e"
            + "0f4f8025cc52532b7f4c3ef61769326e"
            + "841c9c775294ddd2aeba8b7fcb7ce8c4"
            + "66472f0551c905db5d6c7901e51ba435"
            + "9a42ed96fb170e2b6e933440de8f4b7e"
            + "7832696368f9c61c46840db11f5e411a"
            + "d64e2aa300cfd0768fb919f9434f53b0"
            + "02e9316c926fe1498ea8b8bfb1f87943"
            + "6ad5633e004878d47f3102ec93f56737"
            + "c7a4f0f723f402726f4419f1805b9bcb"
            + "c25e5536a1356f30756580aa919dcc5f"
            + "1491bdf6e4639eb56b246e6aa846f721"
            + "59684a64b413264678df77a633c1c448"
            + "0ceddd569bf36b61f9fb492ef7ad14dd"
            + "6b5460e9b267ec1aecf078e3ad180e06"
            + "35b86c65a0ae236c4cdaed5e48b33525"
            + "856a70eac296a1744932ee9a91b45821"
            + "fd7dcaa3e47ef274ed4d34ca440c71bf"
            + "d9cee7e20b85993d61acb72acaeaf969"
            + "4f6480d157c4a062a2abf5df87835df8"
            + "cafbb79aa8f2f2b6b8eae630ff25bdc9"
            + "5e4df395ce7626882cbb26de3a13d98a"
            + "b5b7f3bde86c39ab85844cfabaaa9d5e"
            + "8d6bb9f1c6d644f20c8bf59960efad58"
            + "ec5071353c1fa7da4a681a650fcbeb8f"
            + "9cc48389e5e8c0734d2d77126904addb"
            + "6cf4166e1f4cb964d658a3bba2a2fb33"
            + "0c16fc9b83f54774b826b38ca96019c8"
            + "49705809b8656d61044ee19ade74e59f"
            + "6bce4d414a11bdc1bb76cd096d88dd9d"
            + "83ca5813bdfa7cc4cd6cefbd090c928d"
            + "a944ca2012500c510f9462056ee6d99c"
            + "a76467f9999f4ecf62f7ad1c98eb5914"
            + "283c354c3fae5b527204983915648b2d"
            + "4ccda53623e4e1c4eae633f5ed3f18d1"
            + "3c25d41487014bcc72f3fb69cfe8cdc2"
            + "d157f899b935ee1501bd8131cd2bdcc1"
            + "b64c5425562fa6491d24a53047c8720a"
            + "736be13878c14326035d4b45f319f249"
            + "cab39e4332aa2e309d264be67c4fb376"
            + "d3a9698df50497276792384787a9fd1e"
            + "81c785a7491ec03b7b41625969898df5"
            + "3456585ebe6db84fd70dcf6cb2914279"
            + "ccfd1e7fc25d41f5d1020dce935a2eb6"
            + "7d45641a180f47ab3e6b8cf8f507ef27"
            + "c8c02c9fbd18519dcfd9adf0fdd4a50b"
            + "e2c33bd38df85723e9c9763b6ac5a3da"
            + "70d96dd329a42ca1e7bfed5f7f59e2ab"
            + "830ba4f968bcf3b7dc2fe6e4d5851ab6"
            + "360e5265525f153d9fd9ffc333cd946d"
            + "6c7b035dbb9ee9d1d5a62b1c721481b5"
            + "0a703f8e9ae4491a83d0fb5e2c72305d"
            + "3d045f3c43d2db5168af4e1372d5a477"
            + "ec76b55e3c734fdf9e17d4182ffd5c78"
            + "0fcf25d709f331a2bd9bd991ab9acbd8"
            + "50f701c039172dca18db78836de81f96"
            + "7e75dfa622fe6bdaa7ea896eaab576f8"
            + "3e9e39148bf5960dc4dc8f3b768415f3"
            + "67f477cd34cd47ae7b7de6d3332d42d9"
            + "cf87b883abbd016d668b5f389d72a219"
            + "c82bdd4f2c2b6768779fe2d74bf01653"
            + "1d5618d537029d86004bf48f4cc89d16"
            + "7bdffccd73134c971cff61096877a799"
            + "9d1bf238fb8c12aae9f02a08b9abdfa5"
            + "c8d1101a3d1928a7bc63973cd84b62c2"
            + "9f7c74668d3f203c84b165b5eee84881"
            + "a8b7a86cf7edf7b2a060c56b75d55286"
            + "fbf4468a573a7e77e24d32470b95680e"
            + "7155eeeea7e9522814528e2c414bbf2d"
            + "fcafa73fcbb3b7a42f19b5f057dd";

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void generateDefaultBloomfilter(String filePath) throws IOException {
        File outputFile = new File(filePath);
        outputFile.createNewFile(); // if file already exists will do nothing
        FileOutputStream fos = new FileOutputStream(filePath);
        fos.write(hexStringToByteArray(BLOOM_FILTER_DEFAULT));
        fos.close();
    }
}
