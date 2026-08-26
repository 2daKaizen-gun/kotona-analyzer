package com.kaizen.kotona.analyzer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * @CreatedDate 가 실제로 채워지려면 Auditing 을 켜야 한다.
 *
 * <p>이 애노테이션을 메인 애플리케이션 클래스에 붙이면 @WebMvcTest 같은 슬라이스 테스트에서도
 * 함께 로드되는데, 거기엔 JPA 인프라가 없어 컨텍스트 로딩이 실패한다.
 * 별도 @Configuration 으로 분리하면 웹 슬라이스의 타입 필터가 걸러준다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
