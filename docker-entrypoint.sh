#!/bin/sh
set -eu

PORT="${PORT:-10000}"
case "$PORT" in
  ''|*[!0-9]*)
    echo "PORT must be a numeric TCP port." >&2
    exit 1
    ;;
esac
if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "PORT must be between 1 and 65535." >&2
  exit 1
fi

# Tomcat's stock HTTP connector listens on 8080. Render supplies PORT at runtime.
sed -i "s/port=\"8080\"/port=\"$PORT\"/" "$CATALINA_HOME/conf/server.xml"
exec "$CATALINA_HOME/bin/catalina.sh" run