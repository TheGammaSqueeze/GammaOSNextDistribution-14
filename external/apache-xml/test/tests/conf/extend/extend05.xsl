<xsl:stylesheet version="1.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:saxon="http://icl.com/saxon"
                extension-element-prefixes="saxon">

<!-- Written by Tom Amiro -->
  <!-- FileName: ac137.xsl -->
  <!-- Document: http://www.w3.org/TR/xslt11 -->
  <!-- DocVersion: 200001212 -->
  <!-- Section: 14 Extensions -->
  <!-- Purpose: Using element-available function to test for vendor extensions  -->

<xsl:template match="/">
<out>
  <xsl:if test="element-available('saxon:entity-ref')">
    <saxon:entity-ref name="nbsp" />
  </xsl:if>
  <xsl:if test="element-available('saxon:entity-ref') = false">
    <xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text>
  </xsl:if>
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
