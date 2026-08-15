FROM wordpress:cli

USER root

# Configure mariadb client skip-ssl and helper scripts
RUN echo -e "\n[client]\nskip-ssl = true" >> /etc/my.cnf

RUN for cmd in mariadb mysql mariadb-check mysqlcheck mariadb-dump mysqldump; do \
      echo -e "#!/bin/sh\nexec /usr/bin/$cmd \"\$@\" --skip-ssl" > /usr/local/bin/$cmd && \
      chmod +x /usr/local/bin/$cmd ; \
    done
