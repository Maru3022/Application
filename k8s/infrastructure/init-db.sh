#!/bin/bash
set -e

# Create multiple databases from POSTGRES_MULTIPLE_DATABASES env var
# Format: "db1,db2,db3"
# Called from docker-entrypoint-initdb.d

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
  echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
  for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
    echo "Creating database: $db"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
      CREATE DATABASE $db;
      GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
  done
  echo "Multiple databases created"
fi
