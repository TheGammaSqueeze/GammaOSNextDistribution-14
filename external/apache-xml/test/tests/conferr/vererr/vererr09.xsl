<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- FileName: VERerr09 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test that version number is required. -->
  <!-- ExpectedException: stylesheet must have version attribute -->
  <!-- ExpectedException: xsl:stylesheet requires attribute: version -->

<xsl:template match="/">
  <out>
    Choosing, based on value of version property.
    <xsl:choose>
      <xsl:when test="system-property('xsl:version') &gt;= 1.0">
        We are ready to use the 1.0 feature.
      </xsl:when>
      <xsl:otherwise>
        We didn't try to use the 1.0 feature, but we should have.
        <xsl:message>This stylesheet requires XSLT 1.0 or higher</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
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
