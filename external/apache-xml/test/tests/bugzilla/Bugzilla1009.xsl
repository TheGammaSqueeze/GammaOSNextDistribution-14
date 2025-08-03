<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<!-- 
Bugzilla1009: Malformed attribute expression lacks line/column information 
http://nagoya.apache.org/bugzilla/show_bug.cgi?id=1009
bevan.arps@clear.net.nz (Bevan Arps)
-->

  <xsl:template match="doc">
    <out>
	<!-- Below line causes error due to unclosed '{', but:
		 error is not helpful either on cmd line or programmatically -->
	<link ref="{foo(bar)"></link>
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
