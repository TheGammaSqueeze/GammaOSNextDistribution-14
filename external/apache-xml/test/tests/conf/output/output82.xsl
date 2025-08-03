<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">


  <!-- FileName: output82 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.2 -->
  <!-- Creator: Ito Kazumitsu -->
  <!-- Purpose: Generic test, verifies that DOCTYPE gets generated correctly,
  	   and that the default namespace xml is in scope for LRE use. -->

<xsl:output method="xml"
            doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN"
            doctype-system="xhtml1-transitional.dtd"
            indent="yes"
            encoding="UTF-8"/>


<xsl:template match='/'>
  <html>
    <xsl:attribute name="lang">ja</xsl:attribute>
    <xsl:attribute name="xml:lang">ja</xsl:attribute>
    <head><title>test</title></head><body>test</body>
  </html>
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
