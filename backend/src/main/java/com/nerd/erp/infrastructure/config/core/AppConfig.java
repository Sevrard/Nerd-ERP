package com.nerd.erp.infrastructure.config.core;

import com.nerd.erp.infrastructure.config.gemini.GeminiProperties;
import com.nerd.erp.infrastructure.config.storage.StorageProperties;
import com.nerd.erp.infrastructure.security.jwt.JwtProperties;
import com.nerd.erp.infrastructure.config.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, BootstrapProperties.class, GeminiProperties.class, StorageProperties.class,
        MailProperties.class})
public class AppConfig {
}
