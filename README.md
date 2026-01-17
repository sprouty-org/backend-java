# Sprouty Backend — Microservices Ecosystem

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Container-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-GKE-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)

This repository contains the core backend logic for the **Sprouty** platform. It is built as a suite of five Spring Boot microservices deployed on **Google Kubernetes Engine (GKE)**, leveraging AI for botanical identification and IoT telemetry for real-time plant monitoring.

---

## Microservices Architecture

The system follows a decoupled architectural pattern where services communicate via synchronous HTTP calls using `RestTemplate`.

| Service | Port | Description |
| :--- | :--- | :--- |
| **Gateway** | 8080 | Entry point. Handles JWT validation and dynamic routing. |
| **User Service** | 8081 | IAM hub. Verifies Firebase OIDC tokens and issues stateless JWTs. |
| **Plant Service** | 8082 | AI Pipeline. Integrates Pl@ntNet and OpenAI for species intel. Manages the plant database and user plants |
| **Sensor Service** | 8083 | Ingestion engine. Processes ESP32 telemetry (soil/temp). |
| **Notification Service** | 8084 | Dispatcher. Pushes alerts via Firebase Cloud Messaging (FCM). |



---

## Security & IAM Flow

We implement a **Token-Exchange Architecture** to ensure secure cross-service communication:

1. **Primary Auth:** The mobile client authenticates via Firebase Auth to obtain an `idToken`.
2. **Exchange:** The `User Service` validates this token and issues a locally signed **stateless JWT**.
3. **Validation:** The `Gateway Service` validates the Authorization header using a `JWT_SECRET` stored in Kubernetes Secrets before routing requests to protected resources with the user UID header.

---

## 🛠 Technology Stack

* **Language:** Java 21 (LTS)
* **Framework:** Spring Boot 3.3.2 
* **Database:** Cloud Firestore (NoSQL) via Firebase Admin SDK
* **AI/External APIs:** OpenAI (GPT-3.5 Turbo) & Pl@ntNet API
* **Documentation:** OpenAPI 3.0 (Swagger UI)
* **Build Tool:** Maven

---

## Deployment & DevOps

### Local Setup
Ensure you have **Maven** and **Docker Desktop** installed.

1. **Build all modules:**
   mvn clean install

2. **Environment Configuration:** Ensure the following environment variables (or K8s Secrets) are present:

JWT_SECRET: For token signing/verification.

FIREBASE_KEY_PATH: Path to the service account JSON for Firestore access.

Kubernetes (GKE) Orchestration

Deployment manifests are located in the /kubernetes directory.

Deploy core infrastructure (Deployments, Services, LoadBalancer)

kubectl apply -f kubernetes/infrastructure.yaml

Enable Horizontal Pod Autoscaling (HPA)

kubectl apply -f kubernetes/scaling.yaml


## Monitoring & Resilience
Each service utilizes Spring Boot Actuator to expose critical health metrics. Kubernetes uses these endpoints for self-healing:

Liveness Probe: /{service_name}/actuator/health/liveness

Readiness Probe: /{service_name}/actuator/health/readiness

Status can be monitored via kubectl get pods or the Google Cloud Console dashboard.

## API Documentation
Live Swagger documentation is available through the Gateway's public IP: http://sprouty.duckdns.org/swagger-ui.html

Author: David Muhič

Course: Software Development Processes (PRPO) 2025/2026

University: UL FRI

  
