@echo off
setlocal
pushd "%~dp0" || exit /b 1
set "repository=%MAVEN_REPO_LOCAL%"
if not defined repository set "repository=%USERPROFILE%\.m2\repository"
set "java=java.exe"
if defined JAVA_HOME set "java=%JAVA_HOME%\bin\java.exe"
call mvn "-Dmaven.repo.local=%repository%" compile
if not errorlevel 1 "%java%" -cp "target\classes;%repository%\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar" Main %*
set "status=%ERRORLEVEL%"
popd
exit /b %status%
