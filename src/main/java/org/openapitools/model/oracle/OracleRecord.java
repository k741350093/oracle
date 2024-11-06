package org.openapitools.model.oracle;

import lombok.Data;

import java.util.Map;

@Data
public class OracleRecord {
    private String primaryID;
    private Map<String,Object> data;
}
