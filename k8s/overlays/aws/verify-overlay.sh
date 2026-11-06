#!/usr/bin/env bash
# Verifica o overlay aws do billing SEM cluster e SEM AWS (kubectl kustomize).
# Uso: verify-overlay.sh            -> offline
#      verify-overlay.sh --release  -> recusa digest placeholder
set -euo pipefail
cd "$(dirname "$0")"
fail() { echo "FALHOU: $*" >&2; exit 1; }
out="$(kubectl kustomize .)" || fail "kustomize nao renderiza"
! grep -qE '^kind: Secret' <<<"$out" || fail "Secret nao pode ser versionado"
! grep -qE 'type: (NodePort|LoadBalancer)|nodePort:' <<<"$out" || fail "Service exposto"
! grep -qE '^kind: (Ingress|Gateway|HTTPRoute)' <<<"$out" || fail "Ingress/Gateway proibido"
! grep -qE 'hostNetwork|privileged|hostPID|hostPath' <<<"$out" || fail "pod privilegiado"
grep -q 'automountServiceAccountToken: false' <<<"$out" || fail "automountServiceAccountToken deve ser false"
! grep -qiE 'localstack|:4566|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|AWS_SESSION_TOKEN' <<<"$out" || fail "endpoint/credencial local no overlay aws"
grep -qE "name: SPRING_PROFILES_ACTIVE" <<<"$out" && grep -qE "value: aws$" <<<"$out" || fail "perfil Spring aws ausente"
# Flyway usa o papel migrador (Secret); o datasource segue em DB_USERNAME/DB_PASSWORD.
for par in "SPRING_FLYWAY_USER:db_migrator_username" "SPRING_FLYWAY_PASSWORD:db_migrator_password"; do
  var="${par%%:*}"; key="${par##*:}"
  # bloco da variavel: nome + valueFrom.secretKeyRef.{key,name}; exige secretKeyRef (nao configMapKeyRef) e Secret correto
  block="$(grep -A5 -- "- name: ${var}\$" <<<"$out" | head -n 6)"
  grep -qE '^[[:space:]]+secretKeyRef:$' <<<"$block" || fail "${var} deve usar valueFrom.secretKeyRef"
  grep -qE "^[[:space:]]+key: ${key}\$" <<<"$block" || fail "${var} deve usar a chave ${key}"
  grep -qE '^[[:space:]]+name: oficina-billing-secrets$' <<<"$block" || fail "${var} deve vir do Secret oficina-billing-secrets"
done
! grep -q "BILLING_SQS_ENDPOINT" <<<"$out" || fail "BILLING_SQS_ENDPOINT nao pode existir no perfil aws"
grep -qE 'image: ghcr\.io/[^@ ]+@sha256:[0-9a-f]{64}' <<<"$out" || fail "imagem deve ser GHCR pinada por digest"
if [ "${1:-}" = "--release" ] && grep -qE 'sha256:0{64}' <<<"$out"; then
  fail "digest placeholder"
fi
echo "verify-overlay: OK"
