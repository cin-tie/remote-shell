# Remote Shell Server/Client System

A Java-based remote shell system for Unix/Linux/macOS that allows clients to connect to a server and execute commands, transfer files, navigate in directories.

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Installation](#installation)
- [Protocol](#protocol)
- [Logging](#logging)

## Overview

The Remote Shell system consists of two main components:

| Components | Description                                                                                   |
|------------|-----------------------------------------------------------------------------------------------|
| Server     | Multi-threaded server that handles client connections, command execution, and file operations |
| Client     | Interactive client that provides a shell-like interface for remote operations                 |

## Features

| Feature                | Description                                                |
|------------------------|------------------------------------------------------------|
| Authentication         | User-based connection system with optional server password |
| Remote Execution       | Execute shell commands on remote server                    |
| File transfer          | Upload/Download files between client and server            |
| Directory file transfer| Change and query working directories                       |
| Multi-threaded         | Handles multiple clients simultaneously                     |

## Architecture
```
Client Application → Network → Server Application
     ↓                                ↓
Message Objects                   Thread Pool
     ↓                                ↓
Protocol Layer                  Command Execution
```

## Installation
### Prerequisites
- Java 8 or higher
- Network connectivity between client and server
### Building from source
```bash
# Clone the repository
git clone https://github.com/cin-tie/remote-shell

# Rum script
./scripts/compile.sh

# Or compile manually
javac src/csdev/**/*.java src/csdev/*.java
 
```
## Usage
### Starting the Server
```bash
# Without password
./scripts/server.sh

# With password
./scripts/server.sh -p password
./scripts/server.sh -p "password"

# Or run manually
java -cp build csdev.server.ServerMain
java -cp build csdev.server.ServerMain "password"
```

### Starting the Client
```bash
# Auto-generated guest user (guest_from_ip)
./scripts/client.sh

# Localhost without password
./scripts/client.sh -u john "John Doe"

# Specific host without password
./scripts/client.sh -u john "John Doe" -h 127.0.0.1

# Localhost with password
./scripts/client.sh -u john "John Doe" -p "password"

# All arguments specified
./scripts/client.sh -u john "John Doe" -h 127.0.0.1 -p password

# Or run manually
java -cp build ** john "John Doe" 127.0.0.1 [password]
```

#### Client argument

| Parameter   | Required | Description                                  |
|-------------|----------|----------------------------------------------|
| `-u/--user` | YES*     | Short username and full name for connection  |
| `-h/--host` | NO       | Server hostname(default: localhost)          |
| `-p/--pass` | NO       | Server password if authentication is enabled |

*If `--user` is not provided, client auto-generate "guest_from_ip" username

## Protocol
### Connection Details
| Settings     | Value                                    |
|--------------|------------------------------------------|
| Default Port | `8072` (configurable in `Protocol.java`) |
| Transport    | TCP with Java Objects Serialization      |
| Timeout      | 30 seconds for command execution         |

### Message Flow
1. **Connect** → Client establishes connection with credentials
2. **Authentication** → Server validates user(basic implementation with password)
3. **Command Session** → Client sends commands, server returns results
4. **Disconnect** → Graceful termination or timeout

## Logging
| Level    | Usage                          |
|----------|--------------------------------|
| `INFO`   | General operation messages     |
| `ERROR`  | Error conditions and failures  |
| `WARN`   | Warning conditions             |
| `DEBUG`  | Detailed debugging information |
| `SERVER` | Server-specific events         |
| `CLIENT` | Client-specific events         |
Enable debug by setting `Logger.debugEnabled = true` for detailed troubleshooting
---

**Version**: 1.2
**Author**: cin-tie
**Licence**: Educational Use

