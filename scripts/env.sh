#!/usr/bin/env bash
# 载入本机开发环境变量（JDK 8 = Azul Zulu 8 for macOS aarch64，tarball 安装于 ~/.jdks/zulu8）。
# 若改用系统级 JDK 8（如 brew cask zulu@8），会自动回退到 /usr/libexec/java_home 探测。
if [ -d "$HOME/.jdks/zulu8/Contents/Home" ]; then
  export JAVA_HOME="$HOME/.jdks/zulu8/Contents/Home"
else
  export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"
java -version
