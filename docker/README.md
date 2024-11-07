# Docker 构建说明

本项目包含了预下载的 OpenJDK 19 基础镜像，位于 `docker/images/openjdk-19.tar`。

## 构建步骤

1. 确保已安装 Docker
2. 运行构建脚本：
   ```bash
   # Linux/Mac
   ./docker/build-docker.sh
   
   # Windows
   docker/build-docker.bat
   ```

## 注意事项
- 基础镜像大小约为 XXX MB
- 如需更新基础镜像，请参考项目文档 