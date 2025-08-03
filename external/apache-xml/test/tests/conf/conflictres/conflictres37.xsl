<?xml version="1.0" encoding="UTF-8"?>

  <!-- FileName: conflictres37 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.5 -->
  <!-- Creator: Ilene Seelemann -->
  <!-- Purpose: Test that qname with predicate has precedence over ncname:*, 
                which in turn has precedence over * in a match pattern. -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0"
    xmlns:xf="http://xml.apache.org/cocoon/xmlform/2002">
    
    <xsl:template match="/doc">
      <out>
         <xsl:apply-templates/>
       </out>
     </xsl:template>
     <xsl:template match="xf:output[@form]">
        <OutWithForm>
           <xsl:value-of select="."/>
        </OutWithForm>
     </xsl:template>
     <xsl:template match="xf:*">
        <OutWithoutForm>
        <xsl:value-of select="."/>
        </OutWithoutForm>
     </xsl:template>
     <xsl:template match="*">
         <General>
         <xsl:value-of select="."/>
         </General>
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
