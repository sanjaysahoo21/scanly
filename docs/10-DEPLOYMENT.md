# 🚢 Deployment Guide

---

## 1. Deployment Options

| Option | Best For | Cost |
|---|---|---|
| **Docker Compose on a VPS** | College demo, low traffic | ₹500–1500/month (DigitalOcean, AWS Lightsail) |
| **AWS ECS / GCP Cloud Run** | Production-grade, scalable | Pay-per-use |
| **Railway / Render** | Quick deploy, no DevOps | Free tier available |

This guide focuses on **Docker Compose on a VPS** as it's the most practical for a final-year demo.

---

## 2. VPS Deployment (Docker Compose)

### 2.1 Server Requirements

| Resource | Minimum | Recommended |
|---|---|---|
| CPU | 2 vCPUs | 4 vCPUs |
| RAM | 4 GB | 8 GB |
| Disk | 40 GB SSD | 80 GB SSD |
| OS | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |

### 2.2 Server Setup

```bash
# 1. Update system
sudo apt update && sudo apt upgrade -y

# 2. Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# 3. Install Docker Compose
sudo apt install docker-compose-plugin -y

# 4. Clone the project
git clone <repository-url> /opt/docparser
cd /opt/docparser

# 5. Create production environment file
cp .env.example .env.production
nano .env.production  # Edit with production values

# 6. Start services
docker compose --env-file .env.production up --build -d

# 7. Verify
docker compose ps
curl http://localhost:8080/actuator/health
```

### 2.3 Nginx Reverse Proxy (Optional)

To serve the application on port 80/443 with SSL:

```nginx
# /etc/nginx/sites-available/docparser
server {
    listen 80;
    server_name docparser.yourdomain.com;

    # Frontend
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
    }

    # Backend API
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 10M;  # Match MAX_FILE_SIZE_MB
    }
}
```

---

## 3. CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Backend Tests
        run: |
          cd backend
          ./mvnw test

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Run Frontend Tests
        run: |
          cd frontend
          npm ci
          npm test

  deploy:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/docparser
            git pull origin main
            docker compose --env-file .env.production up --build -d
            docker compose ps
```

---

## 4. Production Checklist

### Security

- [ ] Change all default passwords (PostgreSQL, RabbitMQ)
- [ ] Use a strong, random JWT secret (32+ characters)
- [ ] Enable HTTPS with SSL certificates (Let's Encrypt)
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Disable Spring Boot Actuator endpoints in production (or secure them)
- [ ] Set CORS to allow only your domain
- [ ] Rate limit the upload endpoint
- [ ] Validate and sanitize all user inputs

### Database

- [ ] Enable PostgreSQL connection pooling (HikariCP configured)
- [ ] Set up automated daily database backups
- [ ] Create read-only database user for analytics queries
- [ ] Run `ANALYZE` on tables after bulk imports

### File Storage

- [ ] Set max upload size limit (10MB default)
- [ ] Scan uploaded files for malware (ClamAV)
- [ ] Store files outside the webroot
- [ ] Consider moving to S3/MinIO for production

### Monitoring

- [ ] Set up log aggregation (e.g., Loki, ELK)
- [ ] Monitor RabbitMQ queue depth
- [ ] Set up alerts for FAILED documents
- [ ] Monitor disk space for uploads directory
- [ ] Set up uptime monitoring (e.g., UptimeRobot)

### Performance

- [ ] Enable gzip compression on Nginx
- [ ] Set up PostgreSQL connection pooling
- [ ] Configure RabbitMQ prefetch count
- [ ] Rate limit AI API calls to stay within quota

---

## 5. Backup Strategy

```bash
# Database backup (run daily via cron)
docker exec docparser-postgres pg_dump -U docparser_user docparser > backup_$(date +%Y%m%d).sql

# Cron entry (daily at 2 AM)
0 2 * * * /opt/docparser/scripts/backup.sh >> /var/log/docparser-backup.log 2>&1
```

---

## 6. Scaling Considerations

| Bottleneck | Solution |
|---|---|
| Too many concurrent uploads | Add rate limiting, increase worker instances |
| AI API rate limits | Add token bucket / semaphore, queue backpressure |
| Database slow queries | Add indexes, connection pooling, read replicas |
| File storage full | Move to S3, implement file retention policy |
| Memory pressure | Increase server RAM, tune JVM heap size |
