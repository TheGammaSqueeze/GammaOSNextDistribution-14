<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: copy40 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.3 Using Values of Variables & Parameters with xsl:copy-of. -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Use key() to get nodes from various places, then copy and mark. -->

<xsl:key name="k" use="@origin" match="a" />

<xsl:template match="/">
  <out>
    <xsl:for-each select="key('k','Albany')">
      <xsl:copy>
        <xsl:copy-of select="@level"/>
        <xsl:attribute name="data"><!-- Mark copied 'a' nodes with their data -->
          <xsl:value-of select="text()"/>
        </xsl:attribute>
      </xsl:copy><xsl:text>
</xsl:text>
    </xsl:for-each>
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
