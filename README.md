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

The Remote Shell system consists of to main components:

| Components | Description                                                                                   |
|------------|-----------------------------------------------------------------------------------------------|
| Server     | Multi-threaded server that handles client connections, command execution, and file operations |
| Client     | Interactive client that provides a shell-like interface for remote operations                 |

## Features

| Feature                | Description                                                    |
|------------------------|----------------------------------------------------------------|
| Authentication         | User-based connection system                                   |
| Remote Execution       | Execute shell commands on remote server                        |
| File transfer          | Upload/Download files between client and server                |
| Directory file transfer| Change and query working directories                           |
| Multi-threaded         | Handles multiple clients simultaneosly                         |

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
# Run script
./scripts/server.sh

# Or run manually
java src.csdev.server.ServerMain
```

### Starting the Client
```bash
# Run script
./scripts/client.sh

# Or run manually
java src.csdev.client.ClientMain
```

#### Client argument

| Parameter  | Required | Description                         |
|------------|----------|-------------------------------------|
| `username` | YES      | Short username for connection       |
| `fullname` | YES      | Full user name for identification   |
| `host`     | NO       | Server hostname(default: localhost) |

## Protocol
### Connection Details
| Settings     | Value                                    |
|--------------|------------------------------------------|
| Default Port | `8072` (configurable in `Protocol.java`) |
| Transport    | TCP with Java Objects Serialization      |
| Timeout      | 30 seconds for command execution         |

### Message Flow
1. **Connect** → Client establishes connection with credentials
2. **Authentication** → Server validates user(basic implementation)
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