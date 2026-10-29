package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.application.RecursoNaoEncontradoException;
import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.domain.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Stable codes only; internal messages never reach the response. */
@RestControllerAdvice
public class TratadorErros {
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<Map<String, String>> naoEncontrado() { return erro(HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Map<String, String>> regra() { return erro(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE"); }

    @ExceptionHandler(GatewayIndisponivelException.class)
    ResponseEntity<Map<String, String>> gateway() { return erro(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_UNAVAILABLE"); }

    private static ResponseEntity<Map<String, String>> erro(HttpStatus status, String codigo) {
        return ResponseEntity.status(status).body(Map.of("error", codigo));
    }
}
