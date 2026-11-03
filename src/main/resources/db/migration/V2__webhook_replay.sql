-- Replay/dedup of verified Mercado Pago notifications (control S-4): one row per (x-request-id, data.id).
CREATE TABLE webhook_evento (
    request_id TEXT NOT NULL CHECK (char_length(request_id) BETWEEN 1 AND 200),
    data_id TEXT NOT NULL CHECK (char_length(data_id) BETWEEN 1 AND 200),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, data_id)
);

CREATE INDEX idx_webhook_evento_recebido ON webhook_evento(recebido_em);

-- O papel de execução só recebe DELETE nesta tabela (purga de eventos antigos); as demais tabelas ficam em S/I/U.
-- Condicional: em bancos sem o papel de execução (testes com um único usuário) a concessão é ignorada.
DO $grant$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'billing_app') THEN
        GRANT DELETE ON webhook_evento TO billing_app;
    END IF;
END
$grant$;
