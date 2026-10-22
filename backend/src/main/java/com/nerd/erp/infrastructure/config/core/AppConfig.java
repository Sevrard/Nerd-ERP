package com.nerd.erp.infrastructure.config.core;

import com.nerd.erp.infrastructure.config.etsy.EtsyProperties;
import com.nerd.erp.infrastructure.config.gemini.GeminiProperties;
import com.nerd.erp.infrastructure.config.storage.StorageProperties;
import com.nerd.erp.infrastructure.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, BootstrapProperties.class, GeminiProperties.class, StorageProperties.class, EtsyProperties.class})
public class AppConfig {
}
