package org.openapitools.api;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openapitools.model.oracle.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.NativeWebRequest;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/oracle")
public class OracleApi {
    private static Log log = LogFactory.getLog(OracleApi.class);

    @Autowired
    private NativeWebRequest request;

    @GetMapping(value = "/meta.json", produces = {"application/json"})
    public ResponseEntity<String> getMetaJson() {
        String metaJson = """  
        {  
            "schemaVersion": 1,  
            "version": "1.0.0",  
            "type": "data_connector",  
            "extraData": {  
                "disabledPeriodicSync": false,  
                "dataSourceConfigUiUri": "https://www.bigbrain.work/oracle_sync/index.html",  
                "initHeight": 650,  
                "initWidth": 600  
            },  
            "protocol": {  
                "type": "http",  
                "httpProtocol": {  
                    "uris": [  
                        {  
                            "type": "tableMeta",  
                            "uri": "/tableMeta"  
                        },  
                        {  
                            "type": "records",  
                            "uri": "/tableRecords"  
                        }  
                    ]  
                }  
            }  
        }""";

        return new ResponseEntity<>(metaJson, HttpStatus.OK);
    }

    @RequestMapping(value = "/list", produces = {"application/json"}, method = RequestMethod.POST)
    public ResponseEntity<OracleResponse> tableList(@RequestBody(required = false) DatabaseConnectionRequest requestObj) {
        OracleResponse res = new OracleResponse();
        ArrayList<String> list = new ArrayList<>(); // 用于获取数据库所有的表名

        // 从请求对象中获取连接信息
        String host = requestObj.getIp();
        String port = String.valueOf(requestObj.getPort());
        String serviceName = requestObj.getSid();
        String username = requestObj.getUsername();
        String password = requestObj.getPassword();

        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            // 创建连接字符串
            String dsn = "jdbc:oracle:thin:@" + host + ":" + port + "/" + serviceName;

            // 建立数据库连接
            connection = DriverManager.getConnection(dsn, username, password);

            // 获取表名
            statement = connection.createStatement();
            String tableQuery = "SELECT table_name FROM user_tables"; // 查询当前用户的所有表
            resultSet = statement.executeQuery(tableQuery);

            while (resultSet.next()) {
                list.add(resultSet.getString("table_name")); // 将表名添加到列表中
            }

            // 设置响应内容
            JSONObject json = new JSONObject();
            json.put("tableList", list);
            res.setCode(0);
            res.setData(json);
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (SQLException e) {
            System.out.println("无法连接到数库或查询失败。请检查连接信息，错误信息：" + e.getMessage());
            res.setCode(1254400);
            res.setMsg("");
            res.setData("数据库连接或查询错误: " + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.OK);
        } finally {
            // 关闭资源
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @RequestMapping(value = "/tableMeta", produces = {"application/json"}, method = RequestMethod.POST)
    public ResponseEntity<OracleResponse> tableGet(@RequestBody(required = false) OracleBaseTableMeta requestBase) {
        try {
            DatabaseConnectionRequest dbConfig = parseRequestParameters(requestBase);
            
            try (Connection connection = createDatabaseConnection(dbConfig)) {
                // 表结构接口：只返回第一个主键
                OracleTableMeta tableMeta = getTableStructure(connection, dbConfig.getTableName(), dbConfig.getSid(), true);
                
                OracleResponse res = new OracleResponse();
                res.setCode(0);
                res.setMsg("");
                res.setData(tableMeta);
                return new ResponseEntity<>(res, HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error("Get table meta failed", e);
            return new ResponseEntity<>(buildErrorResponse(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/tableRecords", produces = {"application/json"}, method = RequestMethod.POST)
    public ResponseEntity<OracleResponse> recordsGet(@RequestBody(required = false) OracleBaseTableMeta requestBase) {
        try {
            DatabaseConnectionRequest dbConfig = parseRequestParameters(requestBase);

            try (Connection connection = createDatabaseConnection(dbConfig)) {
                // 查询数据接口：保留所有主键
                OracleTableMeta tableMeta = getTableStructure(connection, dbConfig.getTableName(), dbConfig.getSid(), false);

                QueryResult queryResult = queryTableRecords(connection, tableMeta, dbConfig,
                        getPaginationParams(requestBase));

                return new ResponseEntity<>(buildSuccessResponse(queryResult), HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error("Query table records failed", e);
            return new ResponseEntity<>(buildErrorResponse(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private DatabaseConnectionRequest parseRequestParameters(OracleBaseTableMeta requestBase) {
        String datasourceConfig = JSONObject.parseObject(requestBase.getParams())
                .getString("datasourceConfig");
        return JSONObject.parseObject(datasourceConfig, DatabaseConnectionRequest.class);
    }

    private PaginationParams getPaginationParams(OracleBaseTableMeta requestBase) {
        JSONObject params = JSONObject.parseObject(requestBase.getParams());
        return new PaginationParams(
                params.getString("pageToken"),
                Math.min(params.getInteger("maxPageSize"), 200)
        );
    }

    private Connection createDatabaseConnection(DatabaseConnectionRequest config) throws SQLException {
        String dsn = String.format("jdbc:oracle:thin:@%s:%d/%s",
                config.getIp(), config.getPort(), config.getSid());
        return DriverManager.getConnection(dsn, config.getUsername(), config.getPassword());
    }

    private OracleTableMeta getTableStructure(Connection connection, String tableName, String sid, boolean onlyFirstPrimary) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<OracleFieldMeta> fieldList = getTableFields(metaData, tableName, sid);
        updatePrimaryKeyInfo(metaData, tableName, fieldList, onlyFirstPrimary);

        OracleTableMeta tableMeta = new OracleTableMeta();
        tableMeta.setTableName(tableName);
        tableMeta.setFields(fieldList);
        return tableMeta;
    }

    private List<OracleFieldMeta> getTableFields(DatabaseMetaData metaData, String tableName, String sid)
            throws SQLException {
        List<OracleFieldMeta> fieldList = new ArrayList<>();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                fieldList.add(createFieldMeta(columns, sid, tableName));
            }
        }
        return fieldList;
    }

    private OracleFieldMeta createFieldMeta(ResultSet columns, String sid, String tableName) throws SQLException {
        OracleFieldMeta fieldMeta = new OracleFieldMeta();
        String columnName = columns.getString("COLUMN_NAME");
        // 构造新的fieldID格式：sid_tableName_fieldName
        String fieldId = String.format("%s_%s_%s", sid, tableName, columnName);
        
        fieldMeta.setFieldID(fieldId);
        fieldMeta.setFieldName(columnName);
        fieldMeta.setIsPrimary(false);
        fieldMeta.setDescription(columns.getString("REMARKS"));
        
        // 获取Oracle数据类型
        int oracleType = columns.getInt("DATA_TYPE");
        
        // 映射Oracle数据类型到自定义字段类型
        int fieldType = mapOracleTypeToFieldType(oracleType, columns);
        fieldMeta.setFieldType(fieldType);
        
        return fieldMeta;
    }

    private int mapOracleTypeToFieldType(int oracleType, ResultSet columns) throws SQLException {
        // 使用java.sql.Types中的常量进行比较
        switch (oracleType) {
            // 数字类型
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
                // 检查是否是货币类型（通过列名或注释判断）
                String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                String remarks = columns.getString("REMARKS");
                if (columnName.contains("price") || 
                    columnName.contains("amount") || 
                    columnName.contains("money") ||
                    (remarks != null && (remarks.contains("货币") || remarks.contains("金额")))) {
                    return 8; // 货币类型
                }
                return 2; // 普通数字类型
                
            // 日期时间类型
            case Types.DATE:
            case Types.TIMESTAMP:
            case Types.TIME:
                return 5; // 日期类型
                
            // CLOB/TEXT类型
            case Types.CLOB:
            case Types.LONGVARCHAR:
                return 1; // 多行文本
                
            // CHAR/VARCHAR类型
            case Types.CHAR:
            case Types.VARCHAR:
                String colName = columns.getString("COLUMN_NAME").toLowerCase();
                // 根据列名特征判断特殊类型
                if (colName.contains("phone") || colName.contains("mobile") || 
                    colName.contains("tel")) {
                    return 9; // 电话号码
                }
                if (colName.contains("url") || colName.contains("link")) {
                    return 10; // 超链接
                }
                if (colName.contains("location") || colName.contains("position") ||
                    colName.contains("coordinate")) {
                    return 13; // 地理位置
                }
                if (colName.contains("barcode") || colName.contains("qrcode")) {
                    return 6; // 条码
                }
                return 1; // 默认为多行文本
                
            // 布尔类型
            case Types.BOOLEAN:
            case Types.BIT:
                return 7; // 复选框
                
            // 其他类型默认作为文本处理
            default:
                return 1; // 多行文本
        }
    }

    private void updatePrimaryKeyInfo(DatabaseMetaData metaData, String tableName,
                                      List<OracleFieldMeta> fieldList, boolean onlyFirstPrimary) throws SQLException {
        // 使用TreeMap按KEY_SEQ排序保存主键信息
        Map<Short, String> orderedPrimaryKeys = new TreeMap<>();
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName)) {
            while (primaryKeys.next()) {
                String columnName = primaryKeys.getString("COLUMN_NAME");
                short keySeq = primaryKeys.getShort("KEY_SEQ");
                orderedPrimaryKeys.put(keySeq, columnName);
            }
        }

        if (!orderedPrimaryKeys.isEmpty()) {
            // 更新字段的主键信息
            if (onlyFirstPrimary) {
                // 表结构接口：只设置第一个主键
                String firstPrimaryKey = orderedPrimaryKeys.values().iterator().next();
                for (OracleFieldMeta field : fieldList) {
                    // 使用fieldName比较，因为fieldID包含了sid和tableName前缀
                    field.setIsPrimary(field.getFieldName().equals(firstPrimaryKey));
                }
            } else {
                // 查询数据接口：保留所有主键
                for (OracleFieldMeta field : fieldList) {
                    field.setIsPrimary(orderedPrimaryKeys.containsValue(field.getFieldName()));
                }
            }
        } else {
            // 没有主键时的处理
            log.warn("No primary key found for table {}. Using first column as primary key.");
            if (!fieldList.isEmpty()) {
                fieldList.get(0).setIsPrimary(true);
                for (int i = 1; i < fieldList.size(); i++) {
                    fieldList.get(i).setIsPrimary(false);
                }
            }
        }
    }

    private QueryResult queryTableRecords(Connection connection, OracleTableMeta tableMeta,
                                          DatabaseConnectionRequest dbConfig, PaginationParams paginationParams) throws SQLException {

        // 修改这里：传入整个fields列表，而不是单个字段
        String sql = buildSql(connection,
                dbConfig.getTableName(),
                tableMeta.getFields(),  // 修改：传入完整的字段列表
                paginationParams.getOffset(),
                paginationParams.getMaxRows());

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // 不再需要设置PreparedStatement参数，因为我们直接在SQL中使用了值
            return executeQuery(stmt, tableMeta.getFields(), paginationParams);
        }
    }

    private Optional<String> findPrimaryKeyField(List<OracleFieldMeta> fields) {
        return fields.stream()
                .filter(OracleFieldMeta::getIsPrimary)
                .map(OracleFieldMeta::getFieldID)
                .findFirst();
    }


    private QueryResult executeQuery(PreparedStatement stmt, List<OracleFieldMeta> fieldMetaList,
                                     PaginationParams paginationParams) throws SQLException {
        List<OracleRecord> records = new ArrayList<>();
        try (ResultSet resultSet = stmt.executeQuery()) {
            while (resultSet.next()) {
                records.add(createRecord(resultSet, fieldMetaList));
            }
        }

        boolean hasMore = records.size() == paginationParams.getMaxRows();
        String nextPageToken = hasMore ?
                String.valueOf(paginationParams.getOffset() + paginationParams.getMaxRows()) : null;

        return new QueryResult(records, hasMore, nextPageToken);
    }

    private OracleRecord createRecord(ResultSet resultSet, List<OracleFieldMeta> fieldMetaList)
            throws SQLException {
        OracleRecord record = new OracleRecord();
        Map<String, Object> recordData = new HashMap<>();

        // 构建主键值的字符串表示
        StringBuilder primaryKeyValue = new StringBuilder();

        // 处理所有字段
        for (OracleFieldMeta field : fieldMetaList) {
            String columnName = field.getFieldName();
            String fieldId = field.getFieldID();
            Object value;
            
            // 对日期类型进行特殊处理
            if (field.getFieldType() == 5) { // 5 表示日期类型
                Timestamp timestamp = resultSet.getTimestamp(columnName);
                value = timestamp != null ? timestamp.getTime() : null; // 转换为毫秒时间戳
            } else {
                value = resultSet.getObject(columnName);
            }

            // 使用fieldID作为map的key
            recordData.put(fieldId, value);

            // 如果是主键字段，添加到主键值字符串
            if (field.getIsPrimary()) {
                if (primaryKeyValue.length() > 0) {
                    primaryKeyValue.append("_");
                }
                primaryKeyValue.append(value != null ? value.toString() : "null");
            }
        }

        // 设置record的主键值
        if (primaryKeyValue.length() > 0) {
            record.setPrimaryID(primaryKeyValue.toString());
        } else {
            String firstFieldId = fieldMetaList.get(0).getFieldID();
            Object firstFieldValue = recordData.get(firstFieldId);
            record.setPrimaryID(firstFieldValue != null ? firstFieldValue.toString() : null);
        }

        record.setData(recordData);
        return record;
    }

    private OracleResponse buildSuccessResponse(QueryResult queryResult) {
        OracleResponse response = new OracleResponse();
        response.setCode(0);
        response.setMsg("");

        OracleResponseData responseData = new OracleResponseData();
        responseData.setNextPageToken(queryResult.getNextPageToken());
        responseData.setHasMore(queryResult.isHasMore());
        responseData.setRecords(queryResult.getRecords());
        response.setData(responseData);

        return response;
    }

    private OracleResponse buildErrorResponse(Exception e) {
        OracleResponse response = new OracleResponse();
        response.setCode(1254500);
        response.setMsg("");
        return response;
    }

    // 构建 SQL 查询语句
    private String buildSql(Connection connection, String tableName, List<OracleFieldMeta> fields, int offset, int maxRows) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        // 获取所有主键字段，使用原始的列名
        String orderByClause = fields.stream()
                .filter(OracleFieldMeta::getIsPrimary)
                .map(OracleFieldMeta::getFieldName)  // 使用fieldName而不是fieldID
                .collect(Collectors.joining(", "));

        // 如果没有主键，使用第一个字段的原始列名
        if (orderByClause.isEmpty()) {
            orderByClause = fields.get(0).getFieldName();  // 使用fieldName而不是fieldID
        }

        // Oracle 12c及以上版本使用OFFSET FETCH语法
        if (metaData.getDatabaseMajorVersion() >= 12) {
            return String.format(
                    "SELECT * FROM %s ORDER BY %s OFFSET %d ROWS FETCH NEXT %d ROWS ONLY",
                    tableName,
                    orderByClause,
                    offset,
                    maxRows
            );
        } else {
            // Oracle 11g及以下版本使用ROWNUM
            return String.format(
                    "SELECT * FROM (" +
                            "  SELECT a.*, ROWNUM rnum FROM (" +
                            "    SELECT * FROM %s ORDER BY %s" +
                            "  ) a WHERE ROWNUM <= %d" +
                            ") WHERE rnum > %d",
                    tableName,
                    orderByClause,
                    offset + maxRows,
                    offset
            );
        }
    }

    // 设置 PreparedStatement 的参数
    private void setPreparedStatementParams(PreparedStatement preparedStatement, Connection connection, int offset, int maxRows) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String databaseProductName = metaData.getDatabaseProductName().toLowerCase();

        if (databaseProductName.contains("oracle") && metaData.getDatabaseMajorVersion() >= 12) {
            preparedStatement.setInt(1, offset);
            preparedStatement.setInt(2, maxRows);
        } else {
            preparedStatement.setInt(1, offset);
            preparedStatement.setInt(2, maxRows);
            preparedStatement.setInt(3, offset);
        }
    }

    // 新增一个通用的异常处理方法
    private ResponseEntity<OracleResponse> handleException(Exception e, String operation) {
        log.error(operation + " failed", e);
        return new ResponseEntity<>(buildErrorResponse(e), HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
