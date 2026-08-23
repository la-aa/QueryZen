package com.queryzen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用入口。exclude DataSourceAutoConfiguration：数据源由 DataSourceRegistry 手工初始化
 * （只读连接 + 审计写/读账号），避免 Spring 自动装配干扰。
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@ConfigurationPropertiesScan
public class QueryZenApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueryZenApplication.class, args);
    }
}