<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: attribseterr03 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.3 Creating Attributes -->
  <!-- Purpose: Verify adding an attribute to node that is not an element
  	   is an error.  The attributes can be ignored.-->
  <!-- ExpectedException: Can not add xsl:attribute to xsl:attribute -->
  <!-- ExpectedException: ElemTemplateElement error: Can not add xsl:attribute to xsl:attribute -->
  <!-- ExpectedException: xsl:attribute is not allowed in this position in the stylesheet! -->
<xsl:template match="/">
  <out>
    <xsl:element name="Element1">
      <xsl:attribute name="Att1">
        <xsl:attribute name="Att2">Wrong</xsl:attribute>
          OK
        </xsl:attribute>
      </xsl:element>	  
    <xsl:attribute name="Att1">Also-Wrong</xsl:attribute>
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
