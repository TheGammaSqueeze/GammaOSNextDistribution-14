<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"> 

  <!-- FileName: NUMBERFORMAT42 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.3 -->
  <!-- Creator: David Bertoni -->
  <!-- Purpose: Test of multiple decimal-format elements with identical names.
    This is allowed as long as all attributes are identical (including defaults). -->

<xsl:decimal-format name="decimal2" zero-digit='0' NaN='not a number'/>
<xsl:decimal-format name="decimal2" NaN='not a number' decimal-separator='.'/>

<xsl:template match="doc">
  <out>
    <xsl:value-of select="format-number('NaN','###','decimal2')"/>
    <xsl:text>, </xsl:text>
    <xsl:value-of select="format-number(3.2,'###.0','decimal2')"/>
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
