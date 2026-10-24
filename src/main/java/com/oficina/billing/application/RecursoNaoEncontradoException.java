package com.oficina.billing.application;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String recurso, Object id) {
        super("%s '%s' não encontrado".formatted(recurso, id));
    }
}
