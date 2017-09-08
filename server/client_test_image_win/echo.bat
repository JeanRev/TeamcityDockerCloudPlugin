@echo off
REM Simple script to echo any line of input.
:Start
SET /P text=
ECHO %text%
GOTO Start
