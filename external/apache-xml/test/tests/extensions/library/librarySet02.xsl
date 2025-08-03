<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:set="http://exslt.org/sets"
    exclude-result-prefixes="set">
<xsl:output method="xml" encoding="UTF-8" indent="yes" />

  <!-- FileName: librarySet02.xsl -->
  <!-- Creator: Morris Kwan -->
  <!-- Purpose: Test of the set:trailing() extension function -->

<xsl:template match="/">
  <out>
    <test desc="selects 3, 4">
      <xsl:copy-of select="set:trailing(/doc/*, /doc/str)"/>
    </test>
    <test desc="selects 1, 2, a, 3, 4">
      <xsl:copy-of select="set:trailing(/doc/*, /doc/abc)"/>
    </test>
    <test desc="selects nothing">
      <xsl:copy-of select="set:trailing(/doc/num, /doc/str)"/>
    </test>
  </out>
</xsl:template>


  <!--
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the  "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
  -->

</xsl:stylesheet>
