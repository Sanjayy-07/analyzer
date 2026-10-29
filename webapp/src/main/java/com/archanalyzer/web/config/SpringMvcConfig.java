package com.archanalyzer.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.context.annotation.Bean;

/**
 * Advanced Java Module 6 (Spring Framework: "Developing web applications --
 * Displaying information, Processing form submission, Validating form
 * input. Working with view controllers, Choosing a view template library").
 * Resolves logical view names to JSP files under /WEB-INF/jsp/, which is
 * where the "view template library" the syllabus refers to (JSP itself,
 * per Module 4) is wired into Spring MVC.
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.archanalyzer.web")
@Import(AppConfig.class)
public class SpringMvcConfig implements WebMvcConfigurer {

    @Bean
    public ViewResolver viewResolver() {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/WEB-INF/jsp/");
        resolver.setSuffix(".jsp");
        return resolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("/js/");
    }
}
