#!/usr/bin/env bash
set -e
mvn -B clean package
echo
echo "JAR gerado em: target/HitomicDraw-1.0.0.jar"
