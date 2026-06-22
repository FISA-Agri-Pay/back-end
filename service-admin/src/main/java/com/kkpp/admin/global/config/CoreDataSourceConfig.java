package com.kkpp.admin.global.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CoreDataSourceConfig.class);

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

    // spring.datasource.core.hikari 하위의 풀 설정을 바인딩하는 홀더입니다.
    // primary/replica 두 풀에 동일하게 적용되며, 커스텀 DataSource라 자동 설정이 닿지 않으므로
    // 여기서 명시적으로 읽어 buildHikariDataSource()에서 각 풀에 복사합니다.
    @Bean
    @ConfigurationProperties("spring.datasource.core.hikari")
    public HikariConfig coreHikariConfig() {
        return new HikariConfig();
    }

    // JPA가 바라보는 DataSource Bean입니다.
    // 외부에서는 하나의 DataSource처럼 보이지만, 내부에서는 트랜잭션 readOnly 여부에 따라
    // primary 또는 replica 중 실제 접속 대상을 동적으로 선택합니다.
    @Primary
    @Bean
    public DataSource coreDataSource(
        @Qualifier("corePrimaryDataSourceProperties") DataSourceProperties primaryProperties,
        @Qualifier("coreReplicaDataSourceProperties") DataSourceProperties replicaProperties,
        HikariConfig hikariConfig
    ) {
        return routingDataSource(primaryProperties, replicaProperties, hikariConfig);
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
        DataSourceProperties replicaProperties,
        HikariConfig hikariConfig
    ) {
        // 쓰기 작업과 readOnly가 아닌 트랜잭션이 사용할 primary DB입니다.
        DataSource primaryDataSource = buildHikariDataSource(primaryProperties, hikariConfig);

        // 읽기 전용 트랜잭션이 사용할 replica DB입니다.
        DataSource replicaDataSource = createReplicaDataSourceOrFallback(replicaProperties, primaryDataSource, hikariConfig);

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
        DataSource primaryDataSource,
        HikariConfig hikariConfig
    ) {
        // DB_REPLICA_URL이 설정되지 않은 환경에서는 replica 분리를 비활성화하고 primary만 사용합니다.
        // 이 덕분에 local/dev/prod 설정에 replica 값을 넣지 않아도 애플리케이션은 기존처럼 실행됩니다.
        if (!StringUtils.hasText(replicaProperties.getUrl())) {
            log.warn("core replica URL이 비어 있어 PRIMARY로 폴백합니다. readOnly 트랜잭션도 PRIMARY를 사용합니다.");
            return primaryDataSource;
        }
        return buildHikariDataSource(replicaProperties, hikariConfig);
    }

    // DataSourceProperties로 HikariDataSource를 만든 뒤 coreHikariConfig의 풀 설정만 복사합니다.
    // 접속 정보(jdbcUrl/username/password)는 각 properties에서 이미 채워졌으므로 건드리지 않고,
    // 풀 관련 설정만 명시적으로 덮어써 primary/replica에 동일한 풀 정책을 적용합니다.
    // (HikariConfig#copyStateTo는 jdbcUrl 등 모든 필드를 덮어쓰므로 사용하지 않습니다.)
    // minimum-idle은 의도적으로 복사하지 않습니다. 미설정 시 HikariCP가 maximum-pool-size와 동일하게
    // 취급하여 고정 크기 풀로 동작합니다.
    private DataSource buildHikariDataSource(DataSourceProperties properties, HikariConfig hikariConfig) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
        dataSource.setMaximumPoolSize(hikariConfig.getMaximumPoolSize());
        dataSource.setMaxLifetime(hikariConfig.getMaxLifetime());
        dataSource.setKeepaliveTime(hikariConfig.getKeepaliveTime());
        dataSource.setConnectionTimeout(hikariConfig.getConnectionTimeout());
        dataSource.setLeakDetectionThreshold(hikariConfig.getLeakDetectionThreshold());
        return dataSource;
    }
}
