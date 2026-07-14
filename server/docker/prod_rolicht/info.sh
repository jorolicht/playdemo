#!/bin/bash

docker compose logs -f wp-cli

docker compose logs -f --tail=100 playsrv
