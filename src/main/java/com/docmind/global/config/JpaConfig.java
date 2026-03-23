package com.docmind.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 설정.
 * @EnableJpaAuditing: BaseEntity의 @CreatedDate / @LastModifiedDate
 * 자동 채움을 위한 AuditingEntityListener 활성화.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
