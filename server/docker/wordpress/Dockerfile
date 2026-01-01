# Erweiterungen auf dem Server, gmp und mbstring. 
FROM wordpress:php8.2-apache

RUN apt-get update \
    && apt-get install -y \
        libgmp-dev \
        libonig-dev \
    && docker-php-ext-install \
        gmp \
        mbstring \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*