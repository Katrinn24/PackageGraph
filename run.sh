#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"

repository="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"
java="java"
if [ -n "${JAVA_HOME:-}" ]; then
    java="$JAVA_HOME/bin/java"
fi
mvn "-Dmaven.repo.local=$repository" compile
exec "$java" -cp "target/classes:$repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar" Main "$@"
