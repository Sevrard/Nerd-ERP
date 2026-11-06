package com.nerd.erp.infrastructure.config.core;

import com.nerd.erp.infrastructure.security.core.ModuleAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ModuleAccessInterceptor moduleAccessInterceptor;

    public WebMvcConfig(ModuleAccessInterceptor moduleAccessInterceptor) {
        this.moduleAccessInterceptor = moduleAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduleAccessInterceptor).addPathPatterns("/api/**");
    }
}
