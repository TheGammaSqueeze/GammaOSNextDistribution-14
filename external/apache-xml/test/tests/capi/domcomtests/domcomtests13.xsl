<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
				xmlns:tnt="http://www.cnn.org"
				exclude-result-prefixes="tnt">

<xsl:output method="xml"/>

  <!-- FileName: dtod -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Purpose: Test has no exceptions. -->

<xsl:template match="/">
<root>
<tnt:data attr1="What in the world">DANIEL</tnt:data>
<espn:data xmlns:espn="http://www.espn.com" espn2:attr1="hello" xmlns:espn2="http://www.espn2.com">ESPN and ESPN2</espn:data>
<data2 attr1="olleh" xyz:attr2="eybdoog" xmlns:xyz="http://www.xyz.com">DICK</data2>
This is a test
</root>
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
