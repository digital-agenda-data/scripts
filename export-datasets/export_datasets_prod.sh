#!/bin/sh
export SPARQL_ENDPOINT=http://localhost:8890/sparql
export ISQL_PORT=1111
export ISQL_USER=dba
export ISQL_PASSWORD=dba
export OUTPUT_DIR=/var/www/html/download
cd /var/local/plone/export
./export_datasets.py
