#!/usr/bin/env bash
# Daily check that YouTube has not broken playback: real videos through both stream paths.
# Exit 0 = OK, 1 = broken (see the report), 2 = the check itself could not run.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}
export ERGOTUBE_LIVE=1
rm -f app/build/playback-check/report.txt
./gradlew -q :app:testDebugUnitTest --tests app.freshtuber.player.LivePlaybackCheck --rerun > /tmp/ergotube-playback-gradle.log 2>&1
code=$?
if [ -f app/build/playback-check/report.txt ]; then
  cat app/build/playback-check/report.txt
  grep -q "RESULT: OK" app/build/playback-check/report.txt && exit 0 || exit 1
fi
echo "CHECK DID NOT RUN (gradle exit $code). Last lines of /tmp/ergotube-playback-gradle.log:"
tail -40 /tmp/ergotube-playback-gradle.log
exit 2
