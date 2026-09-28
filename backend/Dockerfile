# =============================================================================
#  JalRakshak API — Railway image (Laravel 12 + FrankenPHP, PHP 8.4)
#  Build context = REPO ROOT (railway.json isko yahin point karta hai), backend/ nahi.
# =============================================================================
#  KYUN repo root: seeders `../data/*.csv` padhte hain (CsvReader::dataPath) aur model
#  provenance `../ml/outputs/*.json` se aata hai. Sirf backend/ bhejte to dono gayab.
#
#  KYUN FrankenPHP (nginx + php-fpm nahi): ek hi process, ek hi port ($PORT) — Railway
#  ke ek-container model mein sabse seedha. `php artisan serve` production ke laayak nahi
#  (single-threaded, ek atki request = poori site ruki).
#
#  KYUN PyTorch NAHI: SAR results dashboard mein pre-computed baked hain; forecast
#  endpoint bina python ke turant saaf 503 deta hai (UI use chhupa leti hai). Torch image
#  ko ~2 GB aur RAM ko ~400 MB+ badhata — free tier ki 0.5 GB limit mein asambhav.
# =============================================================================
# KYUN 8.4 (8.3 nahi): composer.lock laptop ke PHP 8.5 pe bana hai aur Symfony 8.1 packages
# (cache, string, translation...) `php >=8.4.1` maangte hain. 8.3 pe composer install fail
# hota tha. 8.4 lock ke saare packages ko satisfy karta hai.
FROM dunglas/frankenphp:1-php8.4-bookworm

# composer.lock ke hisaab se bas yahi extra chahiye; baaki (mbstring, openssl, sodium,
# dom, fileinfo...) base image mein hain.
RUN install-php-extensions pdo_mysql opcache zip pcntl intl bcmath \
    && cp "$PHP_INI_DIR/php.ini-production" "$PHP_INI_DIR/php.ini"

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer
# Build container mein root hi hai — composer ki root-warning band.
ENV COMPOSER_ALLOW_SUPERUSER=1

# Production opcache: code container ke andar badalta hi nahi, to timestamps check
# karna bekaar kaam hai.
RUN { \
      echo 'opcache.enable=1'; \
      echo 'opcache.memory_consumption=64'; \
      echo 'opcache.validate_timestamps=0'; \
      echo 'memory_limit=128M'; \
      echo 'expose_php=Off'; \
    } > "$PHP_INI_DIR/conf.d/zz-jalrakshak.ini"

WORKDIR /app

# Shared data (seeders) + sirf metrics JSON (weights nahi — wo repo mein hain hi nahi).
COPY data/ /app/data/
COPY ml/outputs/metrics.json ml/outputs/per_region_test.json ml/outputs/forecast_metrics.json /app/ml/outputs/

# Pehle sirf composer files — code badle par dependencies ki layer cache se aaye.
COPY backend/composer.json backend/composer.lock /app/backend/
RUN cd /app/backend && composer install --no-dev --no-scripts --no-autoloader --prefer-dist --no-interaction

COPY backend/ /app/backend/
RUN cd /app/backend \
    && composer dump-autoload --optimize --no-dev \
    && mkdir -p storage/framework/cache/data storage/framework/sessions storage/framework/views \
                storage/logs storage/app/firebase bootstrap/cache \
    && chmod +x docker/start.sh

WORKDIR /app/backend
ENV APP_ENV=production \
    APP_DEBUG=false \
    LOG_CHANNEL=stderr \
    CACHE_STORE=file \
    SESSION_DRIVER=file \
    QUEUE_CONNECTION=sync

CMD ["bash", "/app/backend/docker/start.sh"]
