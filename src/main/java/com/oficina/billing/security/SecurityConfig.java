package com.oficina.billing.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, StaffTokenVerifier verifier, Environment ambiente) throws Exception {
        boolean simulacao = ambiente.acceptsProfiles(Profiles.of("gateway-fake"));
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/health").permitAll();
                    a.requestMatchers(HttpMethod.POST, "/webhooks/mercadopago").permitAll();
                    if (simulacao) a.requestMatchers(HttpMethod.POST, "/simulacoes/**").permitAll();
                    a.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new StaffJwtFilter(verifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
