<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: conditionalerr17 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 9.1 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test xsl:if that has bad content in "test" attribute. -->
  <!-- ExpectedException: Invalid token -->
  <!-- Note the below line may not work, as it's been escaped. This 
       test also causes problems with ConsoleLogger, so we may 
       want to change the test somewhat -->
  <!-- ExpectedException: Could not find function: &#58490;&#57332; -->
  <!-- ExpectedException: Could not find function: --><!-- Provide minimal detection: should be reviewed -sc -->

<xsl:template match="/">
  <out>
    <xsl:if test="Œnot(name(.)='')">
      <xsl:text>string</xsl:text>
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
