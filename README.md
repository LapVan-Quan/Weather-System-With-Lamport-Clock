# Assignment 2
## Project Purpose
In this assignment, students will build a client/server system that aggregates and distributes weather data in JSON format using a RESTful API. The goal is to understand how to design and implement a system that supports multiple clients, multiple servers, and ensures consistency using Lamport clocks.

It consists of three main components:

**1. ContentServer:** Reads weather data from a file and periodically sends it to the Aggregation Server via PUT.

**2. AggregationServer:** Receives weather updates from multiple ContentServers, stores them, enforces Lamport ordering, and serves data to clients.

**3. GETClient:** Sends GET requests to the AggregationServer and retrieves consistent weather data snapshots.

The system demonstrates:
- Producer – Consumer design.
- Handling concurrent PUT/GET requests correctly.
- Persistence of aggregated data.
- Recovery ability when crash occurs.
- Integration and unit testing with JUnit 5.

## Setup Instructions
### 1. Prerequisites

Java 22

Make (for running build commands)

### 2. Dependencies

All third-party libraries are included in the libs/ folder:

- Gson 2.10.1 – JSON parsing/serialization
- JUnit Platform Console Standalone 1.10.2 – test runner for JUnit 5
- etc.


## How to Build and Run
### Build the main code
```bash 
    make build
```

### Run the Aggregation Server
```bash
    make run-server
```

### Run Content Servers

Open new terminals and run:
```bash
    make run-content-1
    make run-content-2
```
These will send weather updates from sample text files to the server.

### Run GET Clients

Open new terminals and run:
```bash
    make run-client-1
    make run-client-2
```
These will request weather snapshots from the server.

## Running Tests
### Compile tests
```bash
    make compile-test
```

### Run Integration test
```bash
    make integration-test
```

### Run JsonUtil test
```bash
    make json-util-test
```

### Run LamportClock test
```bash
    make lamport-clock-test
```

