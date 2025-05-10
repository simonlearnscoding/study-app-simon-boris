package com.studyappbackend._security;

import com.studyappbackend._security.jwt.JwtAuthenticationFilter;
import com.studyappbackend._security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
@EnableSpringDataWebSupport(pageSerializationMode =
        EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .requiresChannel(channel -> channel
                        .anyRequest().requiresSecure() // Force HTTPS for all requests.
                )
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/api/auth/**", "/reset-password/**", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/customer/**", "/api/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers("/manager/**", "/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/driver/**", "/api/driver/**").hasAnyRole("DRIVER", "ADMIN")
                        .anyRequest()
                        .authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint((request, response, authException)
                                -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
