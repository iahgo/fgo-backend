package com.example.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um endpoint REST com a funcionalidade necessária para acessá-lo.
 * O SecurityFilter verifica se o token de autorização contém essa funcionalidade.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Funcionalidade {
    String value();
}
