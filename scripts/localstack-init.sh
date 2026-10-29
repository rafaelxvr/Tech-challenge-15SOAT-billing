#!/bin/bash
# Cria as filas FIFO e as DLQs no LocalStack do docker-compose.local.yml (mesmo contrato do Kind: DLQ após 5 recebimentos).
set -eu
for fila in billing-commands os-saga-replies; do
  awslocal sqs create-queue --queue-name "${fila}-dlq.fifo" \
    --attributes FifoQueue=true,ContentBasedDeduplication=false >/dev/null
  dlq_arn=$(awslocal sqs get-queue-attributes \
    --queue-url "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/${fila}-dlq.fifo" \
    --attribute-names QueueArn --query Attributes.QueueArn --output text)
  cat > /tmp/atributos.json <<JSON
{"FifoQueue":"true","ContentBasedDeduplication":"false","VisibilityTimeout":"30","RedrivePolicy":"{\"deadLetterTargetArn\":\"${dlq_arn}\",\"maxReceiveCount\":\"5\"}"}
JSON
  awslocal sqs create-queue --queue-name "${fila}.fifo" --attributes file:///tmp/atributos.json >/dev/null
done
