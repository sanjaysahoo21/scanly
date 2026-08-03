# 🚀 Local Development Setup Guide

---

## 1. Prerequisites

Ensure the following tools are installed on your machine:

| Tool | Version | Purpose | Install Link |
|---|---|---|---|
| **Java JDK** | 21+ | Spring Boot backend | [Adoptium](https://adoptium.net/) |
| **Maven** | 3.9+ | Java build tool | [Maven](https://maven.apache.org/) |
| **Node.js** | 20+ LTS | React frontend | [Node.js](https://nodejs.org/) |
| **npm** | 10+ | JavaScript package manager | (comes with Node.js) |
| **Docker** | 24+ | Container runtime | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| **Docker Compose** | 2.20+ | Multi-container orchestration | (comes with Docker Desktop) |
| **Git** | 2.40+ | Version control | [Git](https://git-scm.com/) |

### Optional (for native OCR testing without Docker)

| Tool | Version | Purpose |
|---|---|---|
| **Tesseract OCR** | 5.x | OCR engine |
| **Tesseract Language Data** | `eng` | English language model |

---

## 2. Project Structure

```
final-year-project/
├── docs/                           # ← You are here
├── backend/                        # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/docparser/
│   │   │   │   ├── config/         # Spring configuration
│   │   │   │   ├── controller/     # REST controllers
│   │   │   │   ├── dto/            # Data Transfer Objects
│   │   │   │   ├── entity/         # JPA entities
│   │   │   │   ├── repository/     # Spring Data repositories
│   │   │   │   ├── service/        # Business logic
│   │   │   │   ├── worker/         # Queue consumers / workers
│   │   │   │   ├── ai/             # AI parsing engine
│   │   │   │   ├── validation/     # Validation rules engine
│   │   │   │   └── security/       # JWT auth, filters
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── schema.sql
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                       # React / Next.js application
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── services/
│   │   └── types/
│   ├── package.json
│   └── Dockerfile
├── docker-compose.yml              # Full stack orchestration
├── .env.example                    # Environment variable template
└── README.md
```

---

## 3. Environment Variables

Create a `.env` file in the project root (copy from `.env.example`):

```env
# ============================================
# Database
# ============================================
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=docparser
POSTGRES_USER=docparser_user
POSTGRES_PASSWORD=your_secure_password_here

# ============================================
# RabbitMQ
# ============================================
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# ============================================
# AI API (choose one)
# ============================================
# Google Gemini
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-2.0-flash

# OpenAI (alternative)
# OPENAI_API_KEY=your_openai_api_key_here
# OPENAI_MODEL=gpt-4o

# ============================================
# File Storage
# ============================================
UPLOAD_DIR=./uploads
MAX_FILE_SIZE_MB=10

# ============================================
# JWT Authentication
# ============================================
JWT_SECRET=your_jwt_secret_key_minimum_32_chars_long
JWT_EXPIRATION_HOURS=24

# ============================================
# Spring Boot
# ============================================
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev

# ============================================
# Frontend
# ============================================
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

---

## 4. Docker Compose Configuration

```yaml
# docker-compose.yml
version: '3.8'

services:
  # ── PostgreSQL Database ──
  postgres:
    image: postgres:16-alpine
    container_name: docparser-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-docparser}
      POSTGRES_USER: ${POSTGRES_USER:-docparser_user}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-docparser_pass}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-docparser_user}"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── RabbitMQ Message Broker ──
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: docparser-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}
    ports:
      - "5672:5672"     # AMQP protocol
      - "15672:15672"   # Management UI
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Spring Boot Backend ──
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: docparser-backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-docparser}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-docparser_user}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-docparser_pass}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER:-guest}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"
    volumes:
      - uploads_data:/app/uploads
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  # ── React Frontend ──
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: docparser-frontend
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080/api/v1
    ports:
      - "3000:3000"
    depends_on:
      - backend

volumes:
  postgres_data:
  uploads_data:
```

---

## 5. Quick Start (Step-by-Step)

### Option A: Run with Docker Compose (Recommended)

```bash
# 1. Clone the repository
git clone <repository-url>
cd final-year-project

# 2. Create environment file
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY

# 3. Start all services
docker-compose up --build -d

# 4. Check service health
docker-compose ps

# 5. Access the application
#    Frontend:       http://localhost:3000
#    Backend API:    http://localhost:8080/api/v1
#    RabbitMQ UI:    http://localhost:15672 (guest/guest)
```

### Option B: Run Services Individually (Development)

```bash
# 1. Start infrastructure (DB + RabbitMQ) with Docker
docker-compose up postgres rabbitmq -d

# 2. Start the Spring Boot backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. In a new terminal, start the React frontend
cd frontend
npm install
npm run dev

# 4. Access the application
#    Frontend:       http://localhost:3000
#    Backend API:    http://localhost:8080/api/v1
```

---

## 6. Backend Dockerfile

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install Tesseract OCR
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 7. Frontend Dockerfile

```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/package*.json ./
COPY --from=builder /app/node_modules ./node_modules
EXPOSE 3000
CMD ["npm", "start"]
```

---

## 8. Verifying the Setup

After starting all services, verify everything is running:

| Check | Command / URL | Expected Result |
|---|---|---|
| PostgreSQL | `docker exec docparser-postgres pg_isready` | "accepting connections" |
| RabbitMQ | http://localhost:15672 | Management UI login page |
| Backend Health | http://localhost:8080/actuator/health | `{"status":"UP"}` |
| Frontend | http://localhost:3000 | Login page rendered |
| Upload Test | `curl -X POST http://localhost:8080/api/v1/documents/upload -F "files=@test.pdf"` | `202 Accepted` with `job_id` |

---

## 9. Common Issues & Troubleshooting

| Issue | Cause | Fix |
|---|---|---|
| `Connection refused` on port 5432 | PostgreSQL not running | `docker-compose up postgres -d` |
| `Connection refused` on port 5672 | RabbitMQ not running | `docker-compose up rabbitmq -d` |
| `GEMINI_API_KEY not set` | Missing env variable | Add key to `.env` file |
| OCR returns empty text | Tesseract not installed in container | Check Dockerfile includes Tesseract |
| `413 Payload Too Large` | File exceeds upload limit | Increase `MAX_FILE_SIZE_MB` in `.env` |
| `401 Unauthorized` on API calls | JWT expired or missing | Re-login to get fresh token |
