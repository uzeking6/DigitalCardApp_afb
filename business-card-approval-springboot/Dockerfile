FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
# Téléchargements résilients pour connexion lente / instable :
# - on NE fait PAS dependency:go-offline (il télécharge des centaines de jars inutiles)
# - on réessaie automatiquement les téléchargements coupés
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=10 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 -Dmaven.wagon.http.pool=false -Dhttp.keepAlive=false"
COPY pom.xml .
RUN mvn -B -DskipTests dependency:resolve
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
