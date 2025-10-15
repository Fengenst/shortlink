package com.tenseed.shortlink.aggregation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 短链接聚合应用
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
        "com.tenseed.shortlink.admin",
        "com.tenseed.shortlink.project",
        "com.tenseed.shortlink.aggregation"
})
@MapperScan(value = {
        "com.tenseed.shortlink.project.dao.mapper",
        "com.tenseed.shortlink.admin.dao.mapper"
})
public class AggregationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}