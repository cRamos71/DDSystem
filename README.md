# Distributed Drive System

## Overview
This project implements a distributed file-sharing and synchronization system similar to Google Drive (without a web interface). It is designed to deepen the understanding and practical skills related to distributed systems, synchronization algorithms, and concurrent access management.

## Features
- **User Authentication and Management (R1)**
  - Secure user registration and login.
  - Session management for accessing user-specific workspace.

- **Workspace Organization (R2)**
  - **Local Section**: Users manage their shared folders and files locally.
  - **Shared Section**: Access to folders and files shared by other users.
  - Supports basic operations: create, delete, rename, move, upload, and list.

- **Update Propagation (R3)**
  - Real-time update propagation across users.
  - Implemented using:
    - Observer pattern with synchronous/transient communication (Java RMI).
    - Publish/Subscribe pattern with asynchronous/persistent communication (RabbitMQ).

- **Concurrent Access Management (R4)**
  - Automatic serialization of simultaneous access/modifications to shared resources.
  - Ensures exclusive and persistent changes.

- **Fault Tolerance (R5)**
  - Dual-instance server deployment for redundancy.
  - Real-time synchronization and replication of user data.
  - Seamless continuity of service in the event of server failure.

## Technologies Used
- Java RMI
- RabbitMQ
- Design Patterns: Observer, Publish/Subscribe, Factory Method, Visitor
