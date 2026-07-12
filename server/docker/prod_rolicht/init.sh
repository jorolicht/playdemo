#!/bin/bash

mkdir wp_data
mkdir db_data
mkdir db_init
sudo chown -R 33:33 wp_data
sudo chown -R 33:33 db_init
sudo chown -R 999:999 db_data
sudo chmod -R 775 wp_data
sudo chmod -R 700 db_data
