#!/usr/bin/env bash
# Controle S-7: falha antes do upload se um artefato contiver token, segredo ou PII reconhecível.
# Uso: scripts/scan-artifacts.sh <diretorio>...   (imprime apenas o arquivo e o padrão, nunca o valor encontrado)
set -uo pipefail
set +x

[ "$#" -ge 1 ] || { echo "uso: $0 <diretorio>..." >&2; exit 2; }

declare -A padroes=(
  [token_mercadopago]='(APP_USR|TEST)-[0-9]{8,}-[0-9]{6}-[0-9a-f]{20,}'
  [bearer_jwt]='eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
  [chave_privada]='-----BEGIN [A-Z ]*PRIVATE KEY-----'
  [chave_aws]='AKIA[0-9A-Z]{16}'
  [senha_atribuida]='(password|passwd|senha|secret|token)[A-Za-z_]*["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9+/_=-]{16,}'
  [cpf]='[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}'
  [email]='[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.(com|com\.br|net|org)\b'
)

achou=0
for diretorio in "$@"; do
  [ -d "$diretorio" ] || continue
  for nome in "${!padroes[@]}"; do
    while IFS= read -r arquivo; do
      echo "ACHADO: padrão '${nome}' em ${arquivo}" >&2
      achou=1
    done < <(grep -rlEI --exclude-dir=jacoco-resources -- "${padroes[$nome]}" "$diretorio" 2>/dev/null || true)
  done
done

if [ "$achou" -ne 0 ]; then
  echo "scan-artifacts: artefatos não podem ser publicados" >&2
  exit 1
fi
echo "scan-artifacts: nenhum token ou PII reconhecido"
