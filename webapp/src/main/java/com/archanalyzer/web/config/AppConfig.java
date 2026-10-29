package com.archanalyzer.web.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Advanced Java Module 6 (Spring Framework: "Dependency Injection and
 * Inversion of Control -- Initializing a Spring application"). Every bean
 * here is wired via constructor/field injection rather than manual `new`,
 * which is the concrete demonstration of IoC the syllabus asks for.
 *
 * Configuration values (DB credentials) come from environment variables
 * (PART 39: never hardcode secrets) — see .env.example at the project root.
 */
@Configuration
@ComponentScan(basePackages = "com.archanalyzer.web")
@EnableTransactionManagement
public class AppConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env("DB_URL", "jdbc:mysql://localhost:3306/archanalyzer"));
        config.setUsername(env("DB_USERNAME", "root"));
        config.setPassword(env("DB_PASSWORD", ""));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setInitializationFailTimeout(-1); // Avoid context startup failure if DB is offline at boot
        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.archanalyzer.web.entity");

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "none"); // Avoid fail-on-validate if DB is offline at boot
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        props.setProperty("hibernate.show_sql", "false");
        emf.setJpaProperties(props);
        return emf;
    }

    @Bean
    public PlatformTransactionManager transactionManager(
            jakarta.persistence.EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    private String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
