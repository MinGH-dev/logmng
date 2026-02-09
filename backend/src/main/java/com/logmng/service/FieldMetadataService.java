package com.logmng.service;

import com.logmng.dto.response.FieldMetadataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 필드 메타데이터 서비스
 */
@Service
public class FieldMetadataService {
    
    private static final Logger log = LoggerFactory.getLogger(FieldMetadataService.class);
    
    /**
     * 로그 타입별 필드 메타데이터 조회
     */
    public List<FieldMetadataResponse> getFieldMetadata(String logType) {
        log.debug("필드 메타데이터 조회: logType={}", logType);
        
        if ("java_fw_imglog".equals(logType)) {
            return getJavaFwImglogFieldMetadata();
        } else {
            return Collections.emptyList();
        }
    }
    
    /**
     * Java FW Image Log 필드 메타데이터
     */
    private List<FieldMetadataResponse> getJavaFwImglogFieldMetadata() {
        List<FieldMetadataResponse> fields = new ArrayList<>();
        
        // insert_time
        FieldMetadataResponse insertTime = new FieldMetadataResponse();
        insertTime.setName("insert_time");
        insertTime.setLabel("삽입 시간");
        insertTime.setType("timestamp");
        insertTime.setOperatorsAllowed(Arrays.asList(">=", "<=", ">", "<", "="));
        insertTime.setSortable(true);
        insertTime.setFacetable(false);
        insertTime.setValueSource("date_picker");
        fields.add(insertTime);
        
        // application
        FieldMetadataResponse application = new FieldMetadataResponse();
        application.setName("application");
        application.setLabel("애플리케이션");
        application.setType("string");
        application.setOperatorsAllowed(Arrays.asList(":", "=", "IN", "NOT IN"));
        application.setSortable(true);
        application.setFacetable(true);
        application.setValueSource("api");
        application.setSuggestApi("/api/search/suggest?logType=java_fw_imglog&field=application");
        fields.add(application);
        
        // servicegroup
        FieldMetadataResponse servicegroup = new FieldMetadataResponse();
        servicegroup.setName("servicegroup");
        servicegroup.setLabel("서비스 그룹");
        servicegroup.setType("string");
        servicegroup.setOperatorsAllowed(Arrays.asList(":", "=", "IN", "NOT IN"));
        servicegroup.setSortable(true);
        servicegroup.setFacetable(true);
        servicegroup.setValueSource("api");
        servicegroup.setSuggestApi("/api/search/suggest?logType=java_fw_imglog&field=servicegroup");
        fields.add(servicegroup);
        
        // service
        FieldMetadataResponse service = new FieldMetadataResponse();
        service.setName("service");
        service.setLabel("서비스");
        service.setType("string");
        service.setOperatorsAllowed(Arrays.asList(":", "=", "IN", "NOT IN"));
        service.setSortable(true);
        service.setFacetable(true);
        service.setValueSource("api");
        service.setSuggestApi("/api/search/suggest?logType=java_fw_imglog&field=service");
        fields.add(service);
        
        // status
        FieldMetadataResponse status = new FieldMetadataResponse();
        status.setName("status");
        status.setLabel("상태");
        status.setType("enum");
        status.setOperatorsAllowed(Arrays.asList(":", "=", "IN", "NOT IN"));
        status.setSortable(true);
        status.setFacetable(true);
        status.setValueSource("enum");
        status.setEnumValues(Arrays.asList("input", "output", "error"));
        fields.add(status);
        
        // guid
        FieldMetadataResponse guid = new FieldMetadataResponse();
        guid.setName("guid");
        guid.setLabel("GUID");
        guid.setType("string");
        guid.setOperatorsAllowed(Arrays.asList(":", "="));
        guid.setSortable(true);
        guid.setFacetable(false);
        guid.setValueSource("freetext");
        fields.add(guid);
        
        // datastring
        FieldMetadataResponse datastring = new FieldMetadataResponse();
        datastring.setName("datastring");
        datastring.setLabel("데이터 문자열");
        datastring.setType("json");
        datastring.setOperatorsAllowed(Arrays.asList(":", "~"));
        datastring.setSortable(false);
        datastring.setFacetable(false);
        datastring.setValueSource("freetext");
        datastring.setEncrypted(true);
        fields.add(datastring);
        
        // headerstring
        FieldMetadataResponse headerstring = new FieldMetadataResponse();
        headerstring.setName("headerstring");
        headerstring.setLabel("헤더 문자열");
        headerstring.setType("json");
        headerstring.setOperatorsAllowed(Arrays.asList(":", "~"));
        headerstring.setSortable(false);
        headerstring.setFacetable(false);
        headerstring.setValueSource("freetext");
        headerstring.setEncrypted(true);
        fields.add(headerstring);
        
        return fields;
    }
}





