#Environment for local Play Demo Application with MySQL
export APP_VERSION="1.0.0"
export APP_DATE="2025-01-30"
export APP_MAINTAINER="Joe Doe <joe.doe@example.com>"
export APP_ORGANIZATION="com.example"
export APP_VENDOR="Acme Corporation"

export MYSQL_DATABASE="playdemo"
export MYSQL_USER="root"
export MYSQL_PASSWORD="trny4571"

export DB_DEFAULT_DRIVER="com.mysql.cj.jdbc.Driver"
export DB_DEFAULT_URL="jdbc:mysql://localhost/"${MYSQL_DATABASE}
export DB_DEFAULT_USERNAME=${MYSQL_USER}
export DB_DEFAULT_PASSWORD=${MYSQL_PASSWORD}

export GOOGLE_CLIENT_ID="782737132657-vs1domdevjcm9fv5sfg32lp30dd7oc7d.apps.googleusercontent.com"
export PLAY_HTTP_PORT="9555"
export PLAY_HTTP_SECRET_KEY="b2LRPkXkHCFkbefa97iLTJaBs/ncm2HChUrifg2t6q8="
export PIDFILE_PATH="playApp.pid"

export PLAY_MAILER_HOST="smtp.strato.de"
export PLAY_MAILER_PORT="465"
export PLAY_MAILER_USER="info@turnier-service.org"
export PLAY_MAILER_PASSWORD="itsorg@4572"
export PLAY_MAILER_DEBUG="yes"
export PLAY_MAILER_MOCK="yes"

