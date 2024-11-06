package org.openapitools.api;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openapitools.model.oracle.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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
            System.out.println("无法连接到数据库或查询失败。请检查连接信息，错误信息：" + e.getMessage());
            res.setCode(1254400);
            res.setData("数据库连接或查询错误: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.OK);
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
        OracleResponse res = new OracleResponse();

        // 解析 params 字段中的 datasourceConfig
        String datasourceConfig = JSONObject.parseObject(requestBase.getParams()).getString("datasourceConfig");

        // 解析 datasourceConfig 为 DatabaseConnectionRequest 对象
        DatabaseConnectionRequest requestObj = JSONObject.parseObject(datasourceConfig, DatabaseConnectionRequest.class);
        String ip = requestObj.getIp();
        int port = requestObj.getPort();
        String username = requestObj.getUsername();
        String password = requestObj.getPassword();
        String sid = requestObj.getSid();
        String tableName = requestObj.getTableName(); // 要查询的表名

        String dsn = "jdbc:oracle:thin:@" + ip + ":" + port + "/" + sid; // JDBC 连接字符串

        try (Connection connection = DriverManager.getConnection(dsn, username, password)) {
            // 获取表结构信息
            OracleTableMeta tableMeta = getTableStructure(connection, tableName);

            // 设置响应
            res.setCode(0);
            res.setData(tableMeta); // 返回表元数据
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (SQLException e) {
            e.printStackTrace();
            res.setCode(1);
            res.setMsg("数据库连接或查询失败，错误信息：" + e.getMessage());
            return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/tableRecords", produces = {"application/json"}, method = RequestMethod.POST)
    public ResponseEntity<OracleResponse> recordsGet(@RequestBody(required = false) OracleBaseTableMeta requestBase) {
        try {
            // 1. 解析请求参数
            DatabaseConnectionRequest dbConfig = parseRequestParameters(requestBase);

            // 2. 建立数据库连接
            try (Connection connection = createDatabaseConnection(dbConfig)) {
                // 3. 获取表结构
                OracleTableMeta tableMeta = getTableStructure(connection, dbConfig.getTableName());

                // 4. 查询数据记录
                QueryResult queryResult = queryTableRecords(connection, tableMeta, dbConfig,
                        getPaginationParams(requestBase));

                // 5. 构建响应
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

    private OracleTableMeta getTableStructure(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<OracleFieldMeta> fieldList = getTableFields(metaData, tableName);
        updatePrimaryKeyInfo(metaData, tableName, fieldList);

        OracleTableMeta tableMeta = new OracleTableMeta();
        tableMeta.setTableName(tableName);
        tableMeta.setFields(fieldList);
        return tableMeta;
    }

    private List<OracleFieldMeta> getTableFields(DatabaseMetaData metaData, String tableName)
            throws SQLException {
        List<OracleFieldMeta> fieldList = new ArrayList<>();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                fieldList.add(createFieldMeta(columns));
            }
        }
        return fieldList;
    }

    private OracleFieldMeta createFieldMeta(ResultSet columns) throws SQLException {
        OracleFieldMeta fieldMeta = new OracleFieldMeta();
        fieldMeta.setFieldID(columns.getString("COLUMN_NAME"));
        fieldMeta.setFieldName(columns.getString("COLUMN_NAME"));
        fieldMeta.setFieldType(columns.getInt("DATA_TYPE"));
        fieldMeta.setIsPrimary(false);
        fieldMeta.setDescription(columns.getString("REMARKS"));
        return fieldMeta;
    }

    private void updatePrimaryKeyInfo(DatabaseMetaData metaData, String tableName,
                                      List<OracleFieldMeta> fieldList) throws SQLException {
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
            for (OracleFieldMeta field : fieldList) {
                field.setIsPrimary(orderedPrimaryKeys.containsValue(field.getFieldID()));
            }
        } else {
            // 没有主键时的处理
            log.warn("No primary key found for table {}. Using first column as primary key.");
            if (!fieldList.isEmpty()) {
                fieldList.get(0).setIsPrimary(true);
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
            String fieldId = field.getFieldID();
            Object value = resultSet.getObject(fieldId);
            recordData.put(fieldId, value);

            // 如果是主键字段，添加到主键值字符串
            if (field.getIsPrimary()) {
                primaryKeyValue.append(value != null ? value.toString() : "null");
            }
        }

        // 设置record的主键值
        // 如果有主键字段，使用拼接的主键值
        // 如果没有主键，使用第一个字段的值
        if (primaryKeyValue.length() > 0) {
            record.setPrimaryID(primaryKeyValue.toString());
        } else {
            Object firstFieldValue = recordData.get(fieldMetaList.get(0).getFieldID());
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
        response.setMsg(String.format(
                "{\"zh\":\"数据库连接或查询失败，错误信息：%s\", \"en\":\"Database connection or query failed, error message: %s\"}",
                e.getMessage(), e.getMessage()));
        return response;
    }

    // 构建 SQL 查询语句
    private String buildSql(Connection connection, String tableName, List<OracleFieldMeta> fields, int offset, int maxRows) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        // 获取所有主键字段
        String orderByClause = fields.stream()
                .filter(OracleFieldMeta::getIsPrimary)
                .map(OracleFieldMeta::getFieldID)
                .collect(Collectors.joining(", "));

        // 如果没有主键，使用第一个字段
        if (orderByClause.isEmpty()) {
            orderByClause = fields.get(0).getFieldID();
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
                            "  ) a WHERE ROWNUM <= %d" +  // 修改这里，直接使用 offset + maxRows
                            ") WHERE rnum > %d",          // 修改这里，直接使用 offset
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

}
