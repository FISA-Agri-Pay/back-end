package com.kkpp.admin.global.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

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

    // core DB의 primary 접속 정보입니다.
    // 기존 DB_URL, DB_USERNAME, DB_PASSWORD 값이 여기에 바인딩됩니다.
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.core")
    public DataSourceProperties corePrimaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    // core DB의 replica 접속 정보입니다.
    // DB_REPLICA_URL이 설정되어 있으면 readOnly 트랜잭션은 이 DB로 라우팅됩니다.
    @Bean
    @ConfigurationProperties("spring.datasource.core.replica")
    public DataSourceProperties coreReplicaDataSourceProperties() {
        return new DataSourceProperties();
    }

    // JPA가 바라보는 DataSource Bean입니다.
    // 외부에서는 하나의 DataSource처럼 보이지만, 내부에서는 트랜잭션 readOnly 여부에 따라
    // primary 또는 replica 중 실제 접속 대상을 동적으로 선택합니다.
    @Primary
    @Bean
    public DataSource coreDataSource(
        @Qualifier("corePrimaryDataSourceProperties") DataSourceProperties primaryProperties,
        @Qualifier("coreReplicaDataSourceProperties") DataSourceProperties replicaProperties
    ) {
        return routingDataSource(primaryProperties, replicaProperties);
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

    private DataSource routingDataSource(
        DataSourceProperties primaryProperties,
        DataSourceProperties replicaProperties
    ) {
        // 쓰기 작업과 readOnly가 아닌 트랜잭션이 사용할 primary DB입니다.
        DataSource primaryDataSource = primaryProperties.initializeDataSourceBuilder().build();

        // 읽기 전용 트랜잭션이 사용할 replica DB입니다.
        DataSource replicaDataSource = createReplicaDataSourceOrFallback(replicaProperties, primaryDataSource);

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceLookupKey.PRIMARY, primaryDataSource);
        targetDataSources.put(DataSourceLookupKey.REPLICA, replicaDataSource);

        // 실제 라우팅 판단은 ReadWriteRoutingDataSource#determineCurrentLookupKey()에서 수행됩니다.
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();

        // LazyConnectionDataSourceProxy가 중요합니다.
        // 트랜잭션이 시작되기 전에 커넥션을 너무 일찍 잡으면 readOnly 정보가 반영되기 전이라
        // replica 라우팅이 기대대로 동작하지 않을 수 있습니다.
        // 이 프록시는 실제 SQL 실행 직전까지 커넥션 획득을 늦춰 readOnly 기반 라우팅이 가능하게 합니다.
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private DataSource createReplicaDataSourceOrFallback(
        DataSourceProperties replicaProperties,
        DataSource primaryDataSource
    ) {
        // DB_REPLICA_URL이 설정되지 않은 환경에서는 replica 분리를 비활성화하고 primary만 사용합니다.
        // 이 덕분에 local/dev/prod 설정에 replica 값을 넣지 않아도 애플리케이션은 기존처럼 실행됩니다.
        if (!StringUtils.hasText(replicaProperties.getUrl())) {
            return primaryDataSource;
        }
        return replicaProperties.initializeDataSourceBuilder().build();
    }
}
