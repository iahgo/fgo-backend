package com.example.security;

import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * Contexto de segurança da requisição atual.
 * Populado pelo SecurityFilter após validação dos tokens.
 * Disponível via injeção em qualquer resource da mesma requisição.
 */
@RequestScoped
public class ContextoSeguranca {

    private int cdAgtFnco;
    private List<String> funcionalidades = new ArrayList<>();

    public int getCdAgtFnco() { return cdAgtFnco; }
    public void setCdAgtFnco(int cdAgtFnco) { this.cdAgtFnco = cdAgtFnco; }

    public List<String> getFuncionalidades() { return funcionalidades; }
    public void setFuncionalidades(List<String> funcionalidades) { this.funcionalidades = funcionalidades; }

    public boolean possui(String funcionalidade) {
        return funcionalidades.contains(funcionalidade);
    }
}
