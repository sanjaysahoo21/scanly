# =============================================================================
# Scanly Backend — Multi-stage Docker Build
# =============================================================================
# Stage 1 builds the fat JAR; Stage 2 creates a lean runtime image that
# includes Tesseract OCR so image/receipt uploads work without any host install.
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and pom first so dependency layers are cached
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Pre-download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR
COPY src src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime with Tesseract OCR ──────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.title="Scanly Backend"
LABEL org.opencontainers.image.description="Spring Boot API with Tesseract OCR for invoice image extraction"

# Install Tesseract OCR + English language data.
# Ubuntu Jammy ships Tesseract 4.1.x — plenty for invoice/receipt OCR.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-eng \
        libtesseract-dev \
    && rm -rf /var/lib/apt/lists/*

# Verify Tesseract is available in the image
RUN tesseract --version

WORKDIR /app

# Uploaded files are stored here — mount a volume in docker-compose
VOLUME ["/app/uploads"]

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
