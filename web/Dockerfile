FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
RUN groupadd --system sma2 && useradd --system --gid sma2 --home-dir /app sma2
COPY build/classes/ /app/classes/
COPY build/lib/ /app/lib/
COPY logback.xml /app/logback.xml
USER sma2
ENV PORT=8080 NM_LICENSE_FILE=/secrets/nm.lic SMA2_REPORT_DIRECTORY=/tmp/sma2-reports
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx640m", "-Djava.awt.headless=true", "-Dlogback.configurationFile=/app/logback.xml", "-cp", "/app/classes:/app/lib/*", "tech.nmfin.algoquant.web.Sma2Web"]
