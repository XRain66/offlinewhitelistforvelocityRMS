@echo off
set DIR=%~dp0
set GRADLE_HOME=%DIR%gradle\wrapper\gradle-wrapper.jar
java -jar "%GRADLE_HOME%" %*
