#!/bin/sh
export SPARQL_ENDPOINT=http://localhost:8891/sparql
export ISQL_PORT=1112
export ISQL_USER=dba
export ISQL_PASSWORD=dba
export OUTPUT_DIR=/var/www/test-html/download
cd /var/local/test-plone/export
./export_datasets.py
