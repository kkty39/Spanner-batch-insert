# Project Structure  
```
org.example
│
├── Main.java
│   └── // Main class to initialize properties and manage threads for database operations.
│
├── ClientThread.java
│   └── // Defines a thread for handling database operations.
│
├── SpannerClient.java
│   └── // Handles the interaction with Google Cloud Spanner, including transactions.
│
├── NumberGenerator.java
│   └── // Generates a sequence of numbers to use as keys in database operations.
│
└── Status.java
    └── // Enum to define the status of database operations.

```


## Class Descriptions

### 1. Main (Main.java)
- Entry point of the application.
- Initializes application properties and manages execution of client threads for database operations.
- Reads thread and operation counts from properties and starts each thread to execute operations on the Spanner database.
- Aggregates total operations done and exceptions encountered after all threads complete their execution.

### 2. ClientThread (ClientThread.java)
- Represents a thread executing operations against the Spanner database.
- Initializes `SpannerClient`, performs a defined number of insert operations, and tracks the count of operations and aborted exceptions.
- Uses `SpannerClient` for database interactions like transactions and inserts.

### 3. SpannerClient (SpannerClient.java)
- Interacts with Google Cloud Spanner.
- Manages database transactions, performs insert operations, and handles batching of inserts.
- Core component for database operations, managing transaction management and error handling.

### 4. NumberGenerator (NumberGenerator.java)
- Generates a sequence of numbers as keys for database operations.
- Utilizes `AtomicLong` for thread-safe incrementing of numbers.

### 5. Status (Status.java)
- Enumeration defining the status of database operations (`OK`, `BATCHED_OK`, `ERROR`).
- Indicates the outcome of insert operations in `SpannerClient`.

## Application Properties (application.properties)
- Contains settings like Spanner instance, database details, table name, number of batch inserts, total operations count, and thread count.

## Functionality
- Designed to test and measure performance of batch insert operations in Google Cloud Spanner using multi-threading.
- Divides total operations among multiple threads, each performing a portion of the operations.
- Useful for benchmarking and stress-testing database performance under concurrent load.
