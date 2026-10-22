package com.oficina.billing.adapter.in.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Control S-4: size and rate limits on the webhook, evaluated before any signature or payload work. */
public class FiltroLimitesWebhook extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(FiltroLimitesWebhook.class);
    public static final String CAMINHO = "/webhooks/mercadopago";
    private final long maximoBytes;
    private final LimitadorTaxa limitador;

    public FiltroLimitesWebhook(long maximoBytes, LimitadorTaxa limitador) {
        this.maximoBytes = maximoBytes;
        this.limitador = limitador;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CAMINHO.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limitador.permitir()) {
            rejeitar(response, 429, "WEBHOOK_RATE_LIMITED");
        } else if (request.getContentLengthLong() > maximoBytes) {
            rejeitar(response, 413, "WEBHOOK_BODY_TOO_LARGE");
        } else {
            chain.doFilter(request, response);
        }
    }

    private static void rejeitar(HttpServletResponse response, int status, String codigo) {
        MDC.put("event_name", "mercadopago_webhook_rejeitado");
        MDC.put("error_code", codigo);
        try { LOG.warn("safe"); } finally { MDC.remove("event_name"); MDC.remove("error_code"); }
        response.setStatus(status);
    }
}
