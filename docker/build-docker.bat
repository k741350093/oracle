@echo off
REM 加载基础镜像
docker load < docker/images/openjdk-19.tar

REM 构建应用镜像
docker build -t oracle-service . 