<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:xalan="http://xml.apache.org/xalan"
                              exclude-result-prefixes="xalan">

   <!-- This stylesheet tests the evaluate extension function for the 
        case where the expression to be evaluated contains a top-level variable -->

   <xsl:variable name="this" select="root" />

   <xsl:template match="root">
      <test>
         <xsl:apply-templates/>
      </test>
   </xsl:template>

   <xsl:template match="reference">
      <xsl:variable name="var-ref" select="concat('$this/', @ref)" />
      <xsl:for-each select="xalan:evaluate($var-ref)">
         <out value="{@value}" />
      </xsl:for-each>
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
