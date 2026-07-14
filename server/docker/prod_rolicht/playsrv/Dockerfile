# Start with a base image for Java
FROM eclipse-temurin:21-jdk AS mainstage

ARG DEMON_USER
ARG DEMON_USERID
ARG APP_ENVIRONMENT
ARG APP_VERSION
ARG APP_HOME=/app
ARG PLAY_WP_URL
ARG PLAY_WP_CPT

ENV APP_ENVIRONMENT=${APP_ENVIRONMENT}
ENV APP_VERSION=${APP_VERSION}
ENV PLAY_LOGFILE_PATH=${APP_HOME}/logs
ENV PLAY_PIDFILE_PATH=${APP_HOME}/run/server.pid
ENV PLAY_WP_URL=${PLAY_WP_URL}
ENV PLAY_WP_CPT=${PLAY_WP_CPT}  

USER root

# Set working directory, with default value

WORKDIR ${APP_HOME}


# 1. Skript kopieren und ausführbar machen
COPY ./playsrv/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# 👉 ungezippte Play stage kopieren
COPY ./playsrv/stage/bin ./bin
COPY ./playsrv/stage/conf ./conf
COPY ./playsrv/stage/lib ./lib
COPY ./playsrv/stage/share ./share

RUN rm -rf conf/messages || true


# create daemon user 
RUN id -u ${DEMON_USER} 1>/dev/null 2>&1 || (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid ${DEMON_USERID} --gid 0 ${DEMON_USER} || adduser -S -u ${DEMON_USERID} -G root ${DEMON_USER} ))

# Create a volume for logs with owner
RUN mkdir logs && touch logs/application.log
RUN chown -R ${DEMON_USER}:0 ${APP_HOME}

VOLUME ["${APP_HOME}/logs"]

# Create a volume for database
#RUN mkdir ${APP_HOME}/data && chown ${DEMON_USER} ${APP_HOME}/data
#VOLUME ["${APP_HOME}/data"]

USER ${DEMON_USERID}:0

# for testing
# CMD exec /bin/bash -c "trap : TERM INT; sleep infinity & wait"
ENTRYPOINT ["/usr/local/bin/entrypoint.sh", "./bin/server", "-Dapplication.home=${APP_HOME}"]

