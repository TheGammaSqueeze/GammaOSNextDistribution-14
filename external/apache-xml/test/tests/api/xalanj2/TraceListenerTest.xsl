<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:template match="/">
    <doc>
      <mode-none>
        <xsl:apply-templates select="item" /><!-- ElemTemplateElement[xsl:apply-templates;L7;C46;select=itemtest=item; -->
      </mode-none>
      <mode-ala>
        <xsl:apply-templates select="list" mode="ala" /><!-- selected:ElemTemplateElement[xsl:apply-templates;L10;C57;select=list -->
      </mode-ala>
    </doc>
  </xsl:template>

  <xsl:template match="item">
    <pie>
      <xsl:copy/>
    </pie>
  </xsl:template>

  <xsl:template match="list" mode="ala">
    <icecream>text-literal-chars<xsl:text>xsl-text-content</xsl:text><xsl:copy-of select="."/><!-- ElemTemplateElement[xsl:copy-of;L22;C96;select=.select=.; -->
    </icecream>
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
