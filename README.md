# TeamCity Docker Cloud plugin
<p>
<a href="https://tc.var.run/viewType.html?buildTypeId=TeamCityDockerCloudPlugin_Main">
    <img src="https://tc.var.run/app/rest/builds/buildType:(id:TeamCityDockerCloudPlugin_Main)/statusIcon" alt="Build status"/>
</a>
<a href="https://tc.var.run/viewLog.html?buildId=lastSuccessful&buildTypeId=TeamCityDockerCloudPlugin_Main&tab=coverage_jacoco&guest=1">
    <img src="https://tc.var.run/vr_static/dkcld_plugin_coverage_latest.png" alt="Test coverage"/>
</a>

</p>
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
<img src="doc/img/puzzle.png"> 
</td>
<td style="border: none; vertical-align:middle;">
This software has been build with the unfailing support of my company, <a href="https://www.puzzle.ch">Puzzle 
ITC</a>, kudos to them.</div>
</td>
</table>
