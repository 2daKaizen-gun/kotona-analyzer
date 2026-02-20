package com.kaizen.kotona;

import io.github.cdimascio.dotenv.Dotenv; // Dotenv 임포트
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// exclude setting
@SpringBootApplication
public class KotonaApplication {
    public static void main(String[] args) {
        // 스프링 실행 전 .env 파일 읽어서 시스템 변수 등록
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        // 스프링 부트 실행
        SpringApplication.run(KotonaApplication.class, args);
    }
}