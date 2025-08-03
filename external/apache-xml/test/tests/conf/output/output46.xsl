<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: output46 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16 Output -->
  <!-- Purpose: All xsl:output elements are merged into a single element. While repeats of
    most attributes are just tested for conflicts, cdata-section-elements will contain the
    union of the specified values. Both example and test should be wrapped by CDATA, and
    the output should be XML (since cdata-section-elements only applies to XML). -->

<xsl:output cdata-section-elements="test" encoding="UTF-8" indent="no"/>
<xsl:output method="xml" cdata-section-elements="example"/>

<xsl:template match="/">
  <out>
    <example>&lt;foo></example>
    <plain>bar &amp; ban</plain>
    <test>!&gt;</test>
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
