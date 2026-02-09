package com.logmng.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 사용자 활동 이력 자동 기록 어노테이션
 * 
 * 컨트롤러 메서드에 이 어노테이션을 추가하면 자동으로 사용자 활동 이력이 기록됩니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ActivityLog {
    
    /**
     * 액션 타입 (예: "SEARCH", "VIEW", "EXPORT")
     * 기본값은 메서드명을 기반으로 자동 생성됩니다.
     */
    String actionType() default "";
    
    /**
     * 액션 설명
     */
    String description() default "";
    
    /**
     * 요청 파라미터를 action_detail에 포함할지 여부
     */
    boolean includeParams() default true;
    
    /**
     * 응답 데이터를 action_detail에 포함할지 여부
     */
    boolean includeResponse() default false;
}





