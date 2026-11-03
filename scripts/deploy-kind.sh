#!/usr/bin/env bash
# Deploy efêmero do billing-service em um cluster Kind descartável (Plan 2 Local Kind, Task 5; controles S-2/S-3/S-5/S-7/S-10).
#
#   scripts/deploy-kind.sh up      cria o cluster, as dependências locais e implanta o SHA exato do commit
#   scripts/deploy-kind.sh verify  verifica a saúde do pod e faz a fumaça de mensageria (GerarOrcamento -> OrcamentoGerado)
#   scripts/deploy-kind.sh down    remove somente o cluster Kind descartável
#
# Variáveis opcionais: KIND_CLUSTER (padrão oficina-billing), OVERLAY (kind-fake | kind-sandbox), COMMIT_SHA,
# EVIDENCE_DIR. Segredos do deploy são gerados aleatoriamente a cada execução; o overlay kind-sandbox exige
# MP_ACCESS_TOKEN (e opcionalmente MP_WEBHOOK_SECRET) injetados fora de banda. Nenhum valor é impresso.
set -euo pipefail
set +x

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cluster="${KIND_CLUSTER:-oficina-billing}"
overlay="${OVERLAY:-kind-fake}"
sha="${COMMIT_SHA:-$(git -C "$raiz" rev-parse HEAD)}"
evidencias="${EVIDENCE_DIR:-$raiz/target/kind-evidence}"
imagem="billing-service:${sha}"
ctx="kind-${cluster}"
kc() { kubectl --context "$ctx" "$@"; }

segredo_aleatorio() { openssl rand -hex 32 | tr -d '\r\n'; }

criar_segredos() {
  local jwt migrator_pw app_pw admin_pw
  jwt="${JWT_SECRET:-$(segredo_aleatorio)}"
  migrator_pw="$(segredo_aleatorio)"
  app_pw="$(segredo_aleatorio)"
  admin_pw="$(segredo_aleatorio)"
  kc -n data create secret generic postgres-billing-credentials \
    --from-literal=admin_password="$admin_pw" \
    --from-literal=migrator_password="$migrator_pw" \
    --from-literal=app_password="$app_pw" >/dev/null
  local args=(--from-literal=db_app_password="$app_pw" --from-literal=db_migrator_password="$migrator_pw"
              --from-literal=jwt_secret="$jwt")
  if [ "$overlay" = "kind-sandbox" ]; then
    : "${MP_ACCESS_TOKEN:?MP_ACCESS_TOKEN deve ser injetado fora de banda para o overlay kind-sandbox}"
    args+=(--from-literal=mp_access_token="$MP_ACCESS_TOKEN")
    [ -n "${MP_WEBHOOK_SECRET:-}" ] && args+=(--from-literal=mp_webhook_secret="$MP_WEBHOOK_SECRET")
  else
    args+=(--from-literal=mp_webhook_secret="$(segredo_aleatorio)")
  fi
  kc -n oficina-billing create secret generic oficina-billing-secrets "${args[@]}" >/dev/null
}

subir() {
  command -v kind >/dev/null && command -v kubectl >/dev/null && command -v docker >/dev/null
  if ! kind get clusters | grep -qx "$cluster"; then
    kind create cluster --name "$cluster" --wait 120s
  fi
  kc apply -f "$raiz/k8s/local-deps/namespaces.yaml" >/dev/null
  criar_segredos
  kc apply -k "$raiz/k8s/local-deps" >/dev/null
  kc -n infra rollout status deploy/localstack --timeout=240s
  kc -n data rollout status deploy/postgres-billing --timeout=240s
  kc apply -f "$raiz/k8s/local-deps/queue-init.yaml" >/dev/null
  kc -n infra wait --for=condition=complete job/queue-init --timeout=180s

  docker build -t "$imagem" "$raiz"
  kind load docker-image "$imagem" --name "$cluster"
  kc kustomize "$raiz/k8s/overlays/$overlay" | sed "s|image: billing-service\$|image: ${imagem}|" | kc apply -f - >/dev/null
  kc -n oficina-billing rollout status deploy/oficina-billing --timeout=300s
}

verificar() {
  mkdir -p "$evidencias"
  {
    echo "commit=${sha}"
    echo "overlay=${overlay}"
    echo "imagem=${imagem}"
    echo "pod:"
    kc -n oficina-billing get pods -o wide --no-headers | awk '{print $1, $2, $3}'
    echo "imagem em execucao:"
    kc -n oficina-billing get deploy oficina-billing -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
    echo "endpoint de mensageria configurado:"
    kc -n oficina-billing get configmap oficina-billing-config -o jsonpath='{.data.BILLING_SQS_ENDPOINT}{"\n"}'
  } >"$evidencias/estado.txt"
  # A mensagem de saúde é o contrato do pod: UP sob /billing.
  kc -n oficina-billing exec deploy/oficina-billing -- wget -qO- http://127.0.0.1:8080/billing/actuator/health \
    | tee "$evidencias/health.json" | grep -q '"status":"UP"'
  kc -n oficina-billing exec deploy/oficina-billing -- wget -qO- http://127.0.0.1:8080/billing/actuator/health/readiness \
    | grep -q '"status":"UP"'
  kc -n infra delete job billing-smoke --ignore-not-found >/dev/null
  kc -n infra create configmap smoke-envelope --from-file=GerarOrcamento.json="$raiz/contracts/fixtures/GerarOrcamento.json" \
    --dry-run=client -o yaml | kc apply -f - >/dev/null
  kc apply -f "$raiz/k8s/smoke/smoke-job.yaml" >/dev/null
  if kc -n infra wait --for=condition=complete job/billing-smoke --timeout=120s >/dev/null; then
    kc -n infra logs job/billing-smoke | tee "$evidencias/smoke.txt"
  else
    kc -n infra logs job/billing-smoke | tee "$evidencias/smoke.txt" || true
    echo "fumaça de mensageria falhou" >&2
    exit 1
  fi
  # Logs do serviço só entram como evidência se passarem pelo scanner; os logs são JSON com mensagem fixa e sem payload.
  kc -n oficina-billing logs deploy/oficina-billing --tail=200 >"$evidencias/billing.log" || true
}

case "${1:-}" in
  up) subir ;;
  verify) verificar ;;
  down) kind delete cluster --name "$cluster" ;;
  *) echo "uso: $0 up|verify|down" >&2; exit 2 ;;
esac
