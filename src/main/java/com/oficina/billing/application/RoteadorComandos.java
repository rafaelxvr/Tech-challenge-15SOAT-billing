package com.oficina.billing.application;

import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.TipoComando;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Strategy table built from every ManipuladorComando bean; adding a command needs no change here. */
@Component
public class RoteadorComandos {
    private final Map<TipoComando, ManipuladorComando> manipuladores = new EnumMap<>(TipoComando.class);

    public RoteadorComandos(List<ManipuladorComando> lista) {
        for (ManipuladorComando m : lista) {
            if (manipuladores.put(m.tipo(), m) != null) throw new IllegalStateException("Manipulador duplicado: " + m.tipo());
        }
    }

    public boolean suporta(TipoComando tipo) { return manipuladores.containsKey(tipo); }

    public void aplicar(TipoComando tipo, Envelope comando) { manipuladores.get(tipo).executar(comando); }
}
