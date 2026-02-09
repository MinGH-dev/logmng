package com.logmng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 로그 관리 시스템 메인 애플리케이션
 * 
 * @author Log Management System
 * @version 1.0.0
 */
@SpringBootApplication
public class LogManagementApplication {
    
    private static final Logger log = LoggerFactory.getLogger(LogManagementApplication.class);
    
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LogManagementApplication.class);
        Environment env = app.run(args).getEnvironment();
        
        logApplicationStartup(env);
    }
    
    /**
     * 애플리케이션 시작 정보 로깅
     */
    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "9200");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";
        
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("호스트 주소를 확인할 수 없습니다", e);
        }
        
        log.info("\n----------------------------------------------------------\n\t" +
                "🚀 Application '{}' is running! Access URLs:\n\t" +
                "Local:      \t{}://localhost:{}{}\n\t" +
                "External:   \t{}://{}:{}{}\n\t" +
                "Health:     \t{}://localhost:{}{}/api/health\n\t" +
                "Profile(s): \t{}\n" +
                "----------------------------------------------------------",
                env.getProperty("spring.application.name", "logmng-backend"),
                protocol,
                serverPort,
                contextPath,
                protocol,
                hostAddress,
                serverPort,
                contextPath,
                protocol,
                serverPort,
                contextPath,
                env.getActiveProfiles().length == 0 ? 
                        env.getDefaultProfiles() : env.getActiveProfiles()
        );
    }
}

