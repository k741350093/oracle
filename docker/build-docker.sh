#!/bin/bash

# 加载基础镜像
docker load < docker/images/openjdk-19.tar

# 构建应用镜像
docker build -t oracle-service . 