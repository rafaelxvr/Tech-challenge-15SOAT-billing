CREATE TABLE orcamento (
    id UUID PRIMARY KEY,
    os_id UUID NOT NULL,
    saga_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    numero_os BIGINT NOT NULL CHECK (numero_os > 0),
    versao INT NOT NULL CHECK (versao > 0),
    total NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('GERADO','APROVADO','RECUSADO','CANCELADO')),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao_registro BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_orcamento_os_versao UNIQUE (os_id, versao)
);

CREATE TABLE orcamento_item (
    orcamento_id UUID NOT NULL REFERENCES orcamento(id),
    ordem INT NOT NULL CHECK (ordem >= 0),
    tipo TEXT NOT NULL CHECK (tipo IN ('SERVICO','PECA')),
    referencia_id UUID NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(12,2) NOT NULL CHECK (preco_unitario >= 0),
    PRIMARY KEY (orcamento_id, ordem)
);

CREATE TABLE pagamento (
    id UUID PRIMARY KEY,
    orcamento_id UUID NOT NULL UNIQUE REFERENCES orcamento(id),
    os_id UUID NOT NULL,
    valor NUMERIC(12,2) NOT NULL CHECK (valor >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('PENDENTE','CONFIRMADO','RECUSADO','EXPIRADO','CANCELADO','ESTORNADO')),
    mp_preference_id VARCHAR(100),
    mp_payment_id VARCHAR(100) UNIQUE,
    checkout_url VARCHAR(2048) NOT NULL CHECK (checkout_url ~ '^https://'),
    expira_em TIMESTAMPTZ NOT NULL,
    ref_saga_id UUID NOT NULL,
    ref_os_id UUID NOT NULL,
    ref_passo INT NOT NULL CHECK (ref_passo BETWEEN 1 AND 7),
    ref_comando_id UUID NOT NULL,
    ref_correlation_id VARCHAR(36) NOT NULL,
    ref_traceparent VARCHAR(55),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao_registro BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pagamento_pendente_expira ON pagamento(expira_em) WHERE estado = 'PENDENTE';

CREATE TABLE outbox (
    message_id UUID PRIMARY KEY,
    sequencia BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    os_id UUID NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo ~ '^[A-Z][A-Za-z]{2,63}$'),
    envelope JSONB NOT NULL CHECK (jsonb_typeof(envelope) = 'object' AND octet_length(envelope::text) <= 65536),
    estado TEXT NOT NULL DEFAULT 'PENDING' CHECK (estado IN ('PENDING','PUBLISHED','BLOCKED')),
    tentativas INT NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    disponivel_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMPTZ,
    queue_message_id TEXT,
    ultimo_erro_codigo TEXT CHECK (ultimo_erro_codigo ~ '^[A-Z][A-Z0-9_]{0,63}$')
);

CREATE INDEX idx_outbox_pendente ON outbox(disponivel_em, sequencia) WHERE estado = 'PENDING';
CREATE INDEX idx_outbox_os ON outbox(os_id, sequencia);

CREATE TABLE inbox (
    message_id UUID PRIMARY KEY,
    tipo TEXT NOT NULL CHECK (tipo ~ '^[A-Z][A-Za-z]{2,63}$'),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
