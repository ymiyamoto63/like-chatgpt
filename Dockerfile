# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# backendだけコピー（キャッシュ効率UP）
COPY backend/pom.xml backend/mvnw backend/.mvn /app/

RUN chmod +x mvnw

# 依存ダウンロード
RUN ./mvnw -B dependency:go-offline

# ソースコピー
COPY backend/src /app/src

# ビルド
RUN ./mvnw -B package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jdk

WORKDIR /app

# jarコピー
COPY --from=build /app/target/*.jar app.jar

# Render対応（PORT環境変数）
ENV PORT=8080

EXPOSE 8080

CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]