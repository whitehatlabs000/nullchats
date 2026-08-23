<div align="center">
  <img src="src/main/webapp/assets/favicon-96x96.png" alt="NullChats Logo" width="100"/>
  <h1>NullChats</h1>
  🔗 <strong><a href="https://nullchats.com">nullchats.com / the only website </a></strong>
  <p><strong>A manifesto for absolute privacy. Secure, anonymous, and untraceable messaging.</strong></p>

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Java: 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Docker: Ready](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker&logoColor=white)](https://www.docker.com/)
</div>

---

**NullChats** is a high-performance, open-source messaging platform designed with uncompromising cryptographic security. It strips away social features, metadata, and tracking, allowing you to interact securely as a pure cryptographic entity.

## ✨ Core Features

* **🔒 AES-GCM Encryption:** Messages are encrypted using AES-256 in GCM mode (NoPadding). We use AAD (Additional Authenticated Data) to cryptographically bind the sender and receiver IDs to the payload, ensuring messages cannot be tampered with or transplanted across chats.
* **🕵️‍♂️ Zero Profiles:** No public profiles and no followers. You interact using a simple username without tying it to a phone number, email, or real-world identity. Automatically generated robotic avatars represent all users.
* **🛡️ Integrated Anti-Abuse:** The application includes a built-in Web Application Firewall (WAF) to manage rate limits, block malicious IPs, and handle spam protection directly from the core.
* **🗑️ Self-Destructing Capabilities:** Easily delete entire conversations, instantly wiping them from the database and automatically clearing the multimedia cache.

## 🚀 Deployment Guide

NullChats is designed to be easily deployed using Docker, ensuring that the Application Server (Tomcat), the Database (MySQL), and the Avatar Generator (DiceBear) run in perfect isolation without port collisions.

### Prerequisites
* [Docker & Docker Compose](https://docs.docker.com/get-docker/)
* Java 17 & Maven (To build the application core)

### Step 1: Build the Core
Clone the repository and compile the Java application. This will generate the `target/voidchats.war` file.

```bash
git clone https://github.com/whitehatlabs000/nullchats.git
cd nullchats
sudo apt update && sudo apt install openjdk-17-jdk -y
chmod +x mvnw
# Compile the project
./mvnw clean install
```

### Step 2: Spin up the Infrastructure

```bash
docker compose up -d --build
```