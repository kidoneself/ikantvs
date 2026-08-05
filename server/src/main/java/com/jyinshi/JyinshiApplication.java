package com.jyinshi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用入口。
 *
 * <p>每个业务域自成一个顶级包（identity / content / search / transfer / ops /
 * analytics），跨域基础设施统一放在 {@code common}。
 * 会员/计费域已取消。新增功能 = 新增一个域包，不要往 common 或其它域里塞。详见 {@code .cursor/rules/architecture.mdc}。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.jyinshi.**.mapper")
public class JyinshiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JyinshiApplication.class, args);
    }
}
