<?xml version="1.0" encoding="ISO-8859-1"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:output method="html" indent="no"/>

  <!-- FileName: outp70 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 1 Introduction -->
  <!-- Purpose: Quotes and apostrophes can be used inside themselves, without
     terminating the string, if entered as entities. -->

<xsl:template match="/">
  <HTML>
    Inside double quotes:
    1. "&quot;"  <A href="&quot;"/>
    2. "&apos;"  <A href="&apos;"/>
    Inside single quotes:
    3. '&quot;'  <A href='&quot;'/>
    4. '&apos;'  <A href='&apos;'/>
    NOTE: hrefs always have the double quotes.
  </HTML>
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
