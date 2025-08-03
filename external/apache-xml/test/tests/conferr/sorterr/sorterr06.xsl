<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: SORTerr06 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 10 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test use of xsl:sort inside a directive where it's not allowed. -->
  <!-- ExpectedException: xsl:sort can only be used with xsl:apply-templates or xsl:for-each. -->
  <!-- ExpectedException: (StylesheetHandler) xsl:sort can only be used with xsl:apply-templates or xsl:for-each. -->
  <!-- ExpectedException: org.apache.xalan.xslt.XSLProcessorException: (StylesheetHandler) xsl:sort can only be used with xsl:apply-templates or xsl:for-each. -->
  <!-- ExpectedException: xsl:sort is not allowed in this position in the stylesheet! -->

<xsl:template match="/">
  <Out>
    <xsl:comment>
      <xsl:sort/>
      <xsl:text>Comment content</xsl:text>
    </xsl:comment>
  </Out>
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
