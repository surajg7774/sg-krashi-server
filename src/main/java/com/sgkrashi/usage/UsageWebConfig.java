package com.sgkrashi.usage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Attaches the usage-counting interceptor to the API. Kept apart from {@code WebConfig} so removing the feature is one file. */
@Configuration
public class UsageWebConfig implements WebMvcConfigurer {

    private final UsageCountingInterceptor interceptor;

    public UsageWebConfig(UsageCountingInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
