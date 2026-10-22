package com.oficina.billing.security;

import com.oficina.billing.adapter.in.rest.FiltroLimitesWebhook;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    SecurityFilterChain seguranca(HttpSecurity http, StaffTokenVerifier verifier, FiltroLimitesWebhook limitesWebhook)
            throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/health").permitAll();
                    a.requestMatchers(HttpMethod.POST, "/webhooks/mercadopago").permitAll();
                    a.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(limitesWebhook, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new StaffJwtFilter(verifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
