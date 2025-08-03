<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:xalan="http://xml.apache.org/xslt"
    exclude-result-prefixes="xalan">

  <!-- FileName: propertyIndent03 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test xalan:indent-amount set to 10 -->

<xsl:output method="xml" indent="yes" encoding="UTF-8" xalan:indent-amount="10"/>

<xsl:template match="doc">
  <out>
    <xsl:element name="a">
      <xsl:element name="b">
        <xsl:element name="c">
          <xsl:element name="d">Okay</xsl:element>
        </xsl:element>
      </xsl:element>
    </xsl:element>
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
