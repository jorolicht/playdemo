#Docker Test Environment for Play Demo Application

export APP_VERSION="1.0.0"
export APP_DATE="2025-01-30"
export APP_MAINTAINER="Joe Doe <joe.doe@example.com>"
export APP_ORGANIZATION="com.example"
export APP_VENDOR="Acme Corporation"

export BUILD_ENV="development"
export APP_MAINTAINER="Joe Doe <joe.doe@example.com>"
export APP_ORGANIZATION="com.example"
export APP_VENDOR="Acme Corporation"
export WORKDIR="/opt/docker"
export DEMON_USER=robert
export DEMON_USERID=501

export DB_DEFAULT_URL="jdbc:mysql://db:3306/trnydb"
export DB_DEFAULT_DRIVER="com.mysql.cj.jdbc.Driver"
export DB_DEFAULT_ROOT_PASSWORD="rootpassword"
export DB_DEFAULT_USERNAME="playuser"
export DB_DEFAULT_PASSWORD="playpassword"

export PLAY_HTTP_PORT="9500" 
export PLAY_HTTP_HOST="localhost"
export PLAY_HTTP_SECRET_KEY="b2LRPkXkHCFkbefa97iLTJaBs/ncm2HChUrifg2t6q8="
export PIDFILE_PATH="/dev/null"

export PLAY_MAILER_HOST="smtp.strato.de"
export PLAY_MAILER_PORT="465"
export PLAY_MAILER_USER="info@turnier-service.org"
export PLAY_MAILER_PASSWORD="itsorg@4572"
export PLAY_MAILER_DEBUG="yes" # no
export PLAY_MAILER_MOCK="yes"  # no

export GOOGLE_REDIRECT_URL="http://localhost:9000/authenticate/google"
#export GOOGLE_REDIRECT_URL="https://www.turnier-service.info/authenticate/google"
export GOOGLE_CLIENT_ID="782737132657-3gk4lrm5uuh25fi81bmt94vveiklfm0l.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="Oe0ifMt-Acob-KSjpZCiYJjT"

export AUTHENTICATOR_SIGNER_KEY="roli4572"
export AUTHENTICATOR_CRYPTER_KEY="roli4572"