#!/bin/sh
# Papéis do banco `billing` (Plan 2 Local Kind, Task 2): migração (DDL) separada da execução (DML).
# As senhas chegam por variáveis de ambiente injetadas fora de banda; nenhum valor real é versionado.
set -eu
: "${BILLING_MIGRATOR_PASSWORD:?obrigatória}" "${BILLING_APP_PASSWORD:?obrigatória}"
psql -v ON_ERROR_STOP=1 -v mig_pw="$BILLING_MIGRATOR_PASSWORD" -v app_pw="$BILLING_APP_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE billing_migrator LOGIN PASSWORD :'mig_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE billing_app LOGIN PASSWORD :'app_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
REVOKE CONNECT ON DATABASE billing FROM PUBLIC;
GRANT CONNECT ON DATABASE billing TO billing_migrator, billing_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO billing_migrator;
GRANT USAGE ON SCHEMA public TO billing_app;
ALTER DEFAULT PRIVILEGES FOR ROLE billing_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO billing_app;
ALTER DEFAULT PRIVILEGES FOR ROLE billing_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO billing_app;
SQL
