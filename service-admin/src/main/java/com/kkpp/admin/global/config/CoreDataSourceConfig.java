package com.kkpp.admin.global.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.kkpp.admin.adminauth.repository",
        "com.kkpp.admin.bnpl.repository",
        "com.kkpp.admin.order.repository",
        "com.kkpp.admin.credit.repository"
    },
    entityManagerFactoryRef = "coreEntityManagerFactory",
    transactionManagerRef = "coreTransactionManager"
)
public class CoreDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.core")
    public DataSourceProperties coreDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource coreDataSource(
        @Qualifier("coreDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean coreEntityManagerFactory(
        @Qualifier("coreDataSource") DataSource dataSource
    ) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "com.kkpp.admin.adminauth.domain",
            "com.kkpp.admin.bnpl.domain",
            "com.kkpp.admin.order.domain",
            "com.kkpp.admin.credit.domain"
        );
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "validate",
            "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"
        ));
        return em;
    }

    @Primary
    @Bean
    public PlatformTransactionManager coreTransactionManager(
        @Qualifier("coreEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
