# Use the official WordPress image with PHP 8.2 and Apache as the base
FROM localhost:5555/wordpress:php8.2-apache

# Update package lists and install required development libraries
# libgmp-dev: Required for the GMP (GNU Multiple Precision) extension
# libonig-dev: Required for the mbstring (Multibyte String) extension
RUN apt-get update \
    && apt-get install -y \
        libgmp-dev \
        libonig-dev \
    # Install and enable the PHP extensions using the built-in helper
    && docker-php-ext-install \
        gmp \
        mbstring \
    # Clean up the apt cache to reduce image size and remove temporary lists
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*