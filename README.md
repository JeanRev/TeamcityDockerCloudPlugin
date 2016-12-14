# TeamCity Docker Cloud Plugin
[![status](https://builds.gradle.org/app/rest/builds/buildType:GradleScriptKotlin_Master/statusIcon)](https://tc.var.run/viewType.html?buildTypeId=TeamCityDockerCloudPlugin_Main&guest=1)
[![coverage](https://tc.var.run/vr_static/dkcld_plugin_coverage_latest.svg)](https://tc.var.run/viewLog.html?buildId=lastSuccessful&buildTypeId=TeamCityDockerCloudPlugin_Main&tab=coverage_idea&guest=1)
[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

This plugin leverages the TeamCity Cloud API in order to start and dispose TeamCity agents hosted in Docker 
containers on demand. Containers can be configured, and tested, directly from the TeamCity configuration interface.
This cloud provider also has built-in support for Official TeamCity agent images, and resolve them automatically
according to your TeamCity version.

## Requirements
- TeamCity 10.x or greater.
- A Docker daemon accessible through either TCP or Unix Sockets, supporting the remote API v1.24 (Engine version 
1.12.x).

Browser requirements are the same than TeamCity itself (IE9+ or recent version of Firefox/Chrome/Safari/Opera/Edge).

## Quick start
1. Upload the plugin archive using the "_Administration_ | _Plugin List_" section of your TeamCity instance, or copy it
to the _plugins_ subfolder in the TeamCity Data Directory.
2. Access the cloud configuration page ("_Administration_ | _Agent Cloud_") and add a new cloud profile using _Docker_ as Cloud type.
3. Configure the access settings to your Docker instance.
4. Register and configure a Docker image hosting the build agent. You can test the image directly from the same configuration page.

By default, the [official TeamCity agent images](https://hub.docker.com/r/jetbrains/teamcity-agent/) will be used. These
images are usable without any additional configuration, but you may need to tune the container settings a little depending on your build requirements.

## Build
This project uses Gradle [Kotlin](https://kotlinlang.org/) scripts to build and package the plugin. To create the plugin archive simple run the
<code>tcDist</code> task using the provided gradle wrapper:

```bash
./gradlew tcDist
```

Due to the cutting edge nature of Kotlin support in Gradle, using a local Gradle distribution is currently discouraged.
For more information about building and testing please check the [Wiki](https://github.com/JeanRev/TeamcityDockerCloudPlugin/wiki).

## License
Unless otherwise specified in their header, all files are distributed under the Apache License 2.0.

## Acknowledgments
This plugin bundles the following third-party libraries:
- [Jersey](https://jersey.java.net/), the JAX-RS reference implementation, along with the _Apache HttpClient_ connector
([CDDL License](https://glassfish.java.net/public/CDDLv1.0.html)).
- [Jackson](http://wiki.fasterxml.com/JacksonHome), a fast and lightweight JSON processor ([Apache License](http://www.apache.org/licenses/LICENSE-2.0)).
- [Apache HttpComponents](http://hc.apache.org/httpcomponents-client-ga/) Java HTTP client ([Apache License](http://www.apache.org/licenses/LICENSE-2.0)).
- [junixsocket](https://github.com/kohlschutter/junixsocket), Unix domain sockets for Java ([Apache License](http://www.apache.org/licenses/LICENSE-2.0)).
- [xterm.js](http://xtermjs.org/), a terminal front in Javascript ([MIT License](https://opensource.org/licenses/MIT))
- [clipboard.js](https://clipboardjs.com/), clipboard manager for Javascript ([MIT License](https://opensource.org/licenses/MIT))
- [ua-parser.js](http://faisalman.github.io/ua-parser-js/), a lightweight User-Agent parser for Javascript ([MIT License](https://opensource.org/licenses/MIT)).

In addition, the [docker-java](https://github.com/docker-java/docker-java) library was also a source of inspiration.

Lot of thanks to the respective authors of these software.
<table>
<tr>
<td style="border: none; vertical-align:middle;">
<a href="https://www.puzzle.ch" target="_blank">
<img src="https://tc.var.run/vr_static/puzzle.png"> 
</a>
</td>
<td style="border: none; vertical-align:middle;">
This software has been built thanks to the unfailing support of my company
(<a href="https://www.puzzle.ch">Puzzle ITC</a>) to Open Source projects. Kudos to them.</div>
</td>
</table>
