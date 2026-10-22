-- Replay/dedup of verified Mercado Pago notifications (control S-4): one row per (x-request-id, data.id).
CREATE TABLE webhook_evento (
    request_id TEXT NOT NULL CHECK (char_length(request_id) BETWEEN 1 AND 200),
    data_id TEXT NOT NULL CHECK (char_length(data_id) BETWEEN 1 AND 200),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, data_id)
);

CREATE INDEX idx_webhook_evento_recebido ON webhook_evento(recebido_em);
