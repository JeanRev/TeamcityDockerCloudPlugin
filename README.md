# TeamCity Docker Cloud plugin
[![status](https://builds.gradle.org/app/rest/builds/buildType:GradleScriptKotlin_Master/statusIcon)](https://tc.var.run/viewType.html?buildTypeId=TeamCityDockerCloudPlugin_Main)
[![coverage](https://tc.var.run/vr_static/dkcld_plugin_coverage_latest.png)](https://tc.var.run/viewLog.html?buildId=lastSuccessful&buildTypeId=TeamCityDockerCloudPlugin_Main&tab=coverage_idea&guest=1)
[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

This TeamCity plugin leverages the TeamCity Cloud API in order to start and dispose TeamCity agent hosted in Docker 
containers on demand.

## Requirements
- TeamCity 10.x or greater.
- A Docker daemon accessible through either TCP or Unix Socket, supporting the remote API v1.24 (Engine version 
1.12.x).

## License
Unless otherwise specified in their header, all files are distributed under the Apache License 2.0.

## Acknowledgments
<table>
<tr>
<td style="border: none; vertical-align:middle;">
<a href="https://www.puzzle.ch" target="_blank">
<img src="doc/img/puzzle.png"> 
</a>
</td>
<td style="border: none; vertical-align:middle;">
This software has been build with the unfailing support of my company, <a href="https://www.puzzle.ch" target="_blank">Puzzle 
ITC</a>, kudos to them.</div>
</td>
</table>
