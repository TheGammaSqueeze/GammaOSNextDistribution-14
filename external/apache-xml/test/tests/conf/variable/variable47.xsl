<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
                  
<!-- FileName: variable47 -->
<!-- Document: http://www.w3.org/TR/xslt -->
<!-- DocVersion: 19991116 -->
<!-- Section: 11.2 Values of Variables and Parameters -->
<!-- Purpose: Evaluation of params with repeated use of imported stylesheet. -->
<!-- Creator: Matthew Hanson (matthew.hanson@wcom.com -->
<!-- Elaboration: One of the params passed in to baseTemplate is the result of
    a call to baseSubTemplate. Both are in the imported stylesheet, which could
    be considered a subroutine library. The ...Param0 params aren't actually
    used, but are there to potentially cause trouble. baseTemplate puts out two
    child elements, one from itself and one it got from baseSubTemplate. -->

<xsl:import href="var47imp.xsl"/>

<xsl:template match="/">
  <out>
    <xsl:call-template name="baseTemplate">
      <xsl:with-param name="baseParam0" select="'baseParam1Data'"/>
      <xsl:with-param name="baseParam1">
        <xsl:call-template name="baseSubTemplate">
          <xsl:with-param name="baseSubParam0" select="'baseSubParam0Data'"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
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
