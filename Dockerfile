FROM crpi-bquedaa9y4amn71x.cn-hangzhou.personal.cr.aliyuncs.com/k741350093/openjdk:21-ea-slim

# 设置工作目录
WORKDIR /app

# 复制Maven构建的jar文件到容器中
COPY target/oracle-1.0.0.jar app.jar

# 设置时区为亚洲/上海
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 设置编码和Java启动参数
ENV LANG=C.UTF-8
ENV JAVA_OPTS="-Dfile.encoding=UTF-8 --add-opens java.base/java.lang=ALL-UNNAMED"

# 暴露应用端口
EXPOSE 7016

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]