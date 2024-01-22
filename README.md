# Project Structure  
```text
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
# Batch Insert Workflow in SpannerClient

## Overview
This section outlines the workflow of the batch insert mechanism implemented in the `SpannerClient` class. The process is designed to optimize interactions with the Google Cloud Spanner database by efficiently grouping multiple insert operations into fewer transactions, thereby enhancing performance and reducing network load.

## Workflow Details

### 1. Mutation Buffer Initialization
- **Initialization:** The `bufferedMutations` list, an `ArrayList<Mutation>`, is initialized at the start. Each `Mutation` instance encapsulates a proposed change, such as an insert or update operation, to be committed to the Spanner database.

### 2. Building and Buffering Mutations
- **Creation of Mutations:** The `insert(String key)` method is responsible for generating a new mutation for every insert operation requested.
- **Mutation Construction:** Utilizing the `Mutation.newInsertOrUpdateBuilder(TABLE_NAME)` method, these mutations are populated with specific key-value pairs representing the data to be inserted.
- **Buffering Mutations:** Constructed mutations are added sequentially to the `bufferedMutations` list for subsequent batch processing.
- **Note on Empty Commits:** During each operation, there may be empty commits as mutations are initially just cached in the local `bufferedMutations` array.

### 3. Batching Logic and Execution
- **Checking Batch Size:** The `insert` method monitors the `bufferedMutations` size to ensure it does not exceed the predefined `batchInserts` threshold, a parameter configured in the application properties.
- **Mutation Addition and Status Update:** Mutations are continuously added to the batch until the threshold is reached, at which point the method signals a `Status.BATCHED_OK` and proceeds to process the batch.
- **Batch Processing:** Achieved by buffering the accumulated mutations in the current transaction context (`tx.buffer(bufferedMutations)`), followed by clearing the buffer for the next set of operations.

### 4. Transaction Management
- **Initialization and Commitment of Transactions:** Managed by `TransactionManager` and `TransactionContext`, the process involves initializing new transactions with `start()` and committing each batch with `commit()`.
- **Exception Handling and Logging:** In cases where an `AbortedException` occurs, the transaction is promptly aborted. The `commit()` method logs the occurrence of this exception along with the size of the `bufferedMutations` array to indicate if the aborted exception occurred during empty commits.

### 5. Cleanup and Handling Remaining Mutations
- **Post-operation Cleanup:** Invoked via the `cleanup()` method, this step ensures that any remaining mutations in `bufferedMutations` are committed. This scenario typically arises when the total number of operations does not align perfectly with the batch size.

### 6. Ensuring Thread Safety
- **Thread-specific Instances:** To maintain thread safety, each application thread instantiates its own `SpannerClient` object, complete with a dedicated `bufferedMutations` list. This isolation ensures that each thread operates independently on its batch.

### 7. Schema Used
```sql
CREATE TABLE usertable (
  id STRING(MAX),
  field0 STRING(MAX),
) PRIMARY KEY(id);
```

---

For further information or specific queries regarding the batch insert workflow, please refer to the inline comments within the `SpannerClient` class code or contact the development team.


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
- In each operation, we add one mutation to the local cached array, and when the size of array reaches batch size, we put the array into mutation buffer.

### 4. NumberGenerator (NumberGenerator.java)
- Generates a sequence of numbers as keys for database operations.
- Utilizes `AtomicLong` for thread-safe incrementing of numbers.

### 5. Status (Status.java)
- Enumeration defining the status of database operations (`OK`, `BATCHED_OK`, `ERROR`).
- Indicates the outcome of insert operations in `SpannerClient`.

### 6. Application Properties (application.properties)
- Contains settings like Spanner instance, database details, table name, number of batch inserts, total operations count, and thread count.

## Sample Error logging
Mostly two types of error: 
1. Database schema has changed (but no modifications on schema actually).  
2. Transaction was aborted and retry_delay was given (happens in empty commits as there are mutations remained in the local cache array).
```text
---------------------------------------------------------------------------------------------------------------
Thread-138 in SpannerClient.commit() method- AbortedException occurred.
Current size of mutation buffers array: 94.
Stack Trace:com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed



---------------------------------------------------------------------------------------------------------------
Thread-0 in SpannerClient.commit() method- AbortedException occurred.
Current size of mutation buffers array: 77.
Stack Trace:com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 38655229
}
```