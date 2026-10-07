#!/bin/sh
set -u
export MSYS_NO_PATHCONV=1
JMETER="D:/tools/apache-jmeter-5.6.3/bin/jmeter"
DIR=target/perftest
BASE=http://localhost:8080

relogin() {
  JAR="$DIR/cookies.txt"
  rm -f "$JAR"
  curl -s -c "$JAR" "$BASE/login/index" -o /dev/null
  XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR")
  PASS=$(printf %s '12123456c3' | md5sum | cut -d' ' -f1)
  curl -s -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $XSRF" -d "mobile=15200001000&password=$PASS" "$BASE/login/do_login" -o /dev/null
  TOKEN=$(awk '$6=="token"{print $7}' "$JAR")
  echo "LOGIN token=$TOKEN"
  curl -s -o /dev/null -w "userinfo-check:%{http_code}\n" -H "Cookie: token=$TOKEN" "$BASE/user/info"
}

run() {
  name="$1"; shift
  echo "=== RUN $name ==="
  "$JMETER" -n -t load-test/jmeter/qps-endpoints.jmx "$@" -l "$DIR/$name.jtl" -j "$DIR/$name.log" > "$DIR/$name.out" 2>&1
  echo "=== DONE $name exit=$? ==="
}

run list-50 -Jthreads=50 -Jduration=60 -Jpath=/goods/list
run list-100 -Jthreads=100 -Jduration=60 -Jpath=/goods/list
run list-200 -Jthreads=200 -Jduration=60 -Jpath=/goods/list
run listnocache-50 -Jthreads=50 -Jduration=60 -Jpath=/goods/listWithoutCache
run listnocache-100 -Jthreads=100 -Jduration=60 -Jpath=/goods/listWithoutCache

relogin
run userinfo-50 -Jthreads=50 -Jduration=60 -Jpath=/user/info "-Jcookie=$TOKEN"

relogin
run userinfo-100 -Jthreads=100 -Jduration=60 -Jpath=/user/info "-Jcookie=$TOKEN"

echo "ALL DONE"
