#!/usr/bin/env bash
# 载入本机开发环境变量（JDK17 为 brew openjdk@17 安装的 keg-only 版本）
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"
java -version