package work.bigbrain.model.oracle;

import lombok.Data;

@Data
public class DatabaseConnectionRequest {
    private String ip;       // 数据库服务器的 IP 地址
    private int port;        // 数据库端口
    private String username; // 用户名
    private String password; // 密码
    private String sid;      // 数据库 SID
    private String role;     // 用户角色
    private String tableName;
}
