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

## Application Properties (application.properties)
- Contains settings like Spanner instance, database details, table name, number of batch inserts, total operations count, and thread count.

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

### 3. Batching Logic and Execution
- **Checking Batch Size:** The `insert` method monitors the `bufferedMutations` size to ensure it does not exceed the predefined `batchInserts` threshold, a parameter configured in the application properties.
- **Mutation Addition and Status Update:** Mutations are continuously added to the batch until the threshold is reached, at which point the method signals a `Status.BATCHED_OK` and proceeds to process the batch.
- **Batch Processing:** Achieved by buffering the accumulated mutations in the current transaction context (`tx.buffer(bufferedMutations)`), followed by clearing the buffer for the next set of operations.

### 4. Transaction Management
- **Initialization and Commitment of Transactions:** Managed by `TransactionManager` and `TransactionContext`, the process involves initializing new transactions with `start()` and committing each batch with `commit()`.
- **Exception Handling:** In cases where an `AbortedException` occurs, the transaction is promptly aborted with the provision for a retry mechanism.

### 5. Cleanup and Handling Remaining Mutations
- **Post-operation Cleanup:** Invoked via the `cleanup()` method, this step ensures that any remaining mutations in `bufferedMutations` are committed. This scenario typically arises when the total number of operations does not align perfectly with the batch size.

### 6. Ensuring Thread Safety
- **Thread-specific Instances:** To maintain thread safety, each application thread instantiates its own `SpannerClient` object, complete with a dedicated `bufferedMutations` list. This isolation ensures that each thread operates independently on its batch.

## Impact on Efficiency and Performance
- **Optimized Efficiency:** By aggregating multiple insert operations into fewer comprehensive transactions, the batch insert mechanism significantly reduces the frequency of network calls and transaction commits to the Spanner database.
- **Enhanced Performance:** Particularly beneficial when handling large-scale insertions, this strategy markedly improves overall database performance.


### Sample Error logging
```text
Jan 22, 2024 7:04:23 AM org.example.Main initProperties
INFO: Thread count: 256
Jan 22, 2024 7:04:23 AM org.example.Main initProperties
INFO: Operation count: 1000000
Jan 22, 2024 7:04:45 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-102 - Aborted Exception occurred.
Current size of mutation buffers array: 205.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 44022275
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 44022275
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 44022275
}

	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:04:45 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-162 - Aborted Exception occurred.
Current size of mutation buffers array: 198.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 42755953
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 42755953
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 42755953
}

	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:05:04 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-31 - Aborted Exception occurred.
Current size of mutation buffers array: 481.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 30978613
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 30978613
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 30978613
}

	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:05:04 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-83 - Aborted Exception occurred.
Current size of mutation buffers array: 491.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 29643541
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 29643541
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 29643541
}

	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:05:04 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-89 - Aborted Exception occurred.
Current size of mutation buffers array: 482.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 28502474
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 28502474
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 28502474
}

	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:05:04 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-78 - Aborted Exception occurred.
Current size of mutation buffers array: 470.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 31437404
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 31437404
}

	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Transaction was aborted.
retry_delay {
  nanos: 31437404
}

	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-24 - Aborted Exception occurred.
Current size of mutation buffers array: 713.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-129 - Aborted Exception occurred.
Current size of mutation buffers array: 710.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-109 - Aborted Exception occurred.
Current size of mutation buffers array: 719.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-52 - Aborted Exception occurred.
Current size of mutation buffers array: 731.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-7 - Aborted Exception occurred.
Current size of mutation buffers array: 690.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-214 - Aborted Exception occurred.
Current size of mutation buffers array: 743.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-166 - Aborted Exception occurred.
Current size of mutation buffers array: 697.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-3 - Aborted Exception occurred.
Current size of mutation buffers array: 685.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

Jan 22, 2024 7:06:25 AM org.example.SpannerClient commit
INFO: 
--------------------------------------------------------------------------
Thread-42 - Aborted Exception occurred.
Current size of mutation buffers array: 730.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more

INFO: 
--------------------------------------------------------------------------
Thread-220 - Aborted Exception occurred.
Current size of mutation buffers array: 700.
Stack Trace:

com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:297)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:170)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl.commit(TransactionRunnerImpl.java:298)
	at com.google.cloud.spanner.TransactionManagerImpl.commit(TransactionManagerImpl.java:76)
	at com.google.cloud.spanner.SessionPool$AutoClosingTransactionManager.commit(SessionPool.java:862)
	at org.example.SpannerClient.commit(SpannerClient.java:151)
	at org.example.ClientThread.run(ClientThread.java:34)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: com.google.cloud.spanner.AbortedException: ABORTED: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerExceptionPreformatted(SpannerExceptionFactory.java:262)
	at com.google.cloud.spanner.SpannerExceptionFactory.fromApiException(SpannerExceptionFactory.java:311)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:174)
	at com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException(SpannerExceptionFactory.java:110)
	at com.google.cloud.spanner.TransactionRunnerImpl$TransactionContextImpl$CommitRunnable.lambda$run$0(TransactionRunnerImpl.java:408)
	at io.opencensus.trace.CurrentSpanUtils$RunnableInSpan.run(CurrentSpanUtils.java:125)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.gax.retrying.BasicRetryingFuture.handleAttempt(BasicRetryingFuture.java:200)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.handle(CallbackChainRetryingFuture.java:135)
	at com.google.api.gax.retrying.CallbackChainRetryingFuture$AttemptCompletionListener.run(CallbackChainRetryingFuture.java:117)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at com.google.api.core.AbstractApiFuture$InternalSettableFuture.setException(AbstractApiFuture.java:92)
	at com.google.api.core.AbstractApiFuture.setException(AbstractApiFuture.java:74)
	at com.google.api.gax.grpc.GrpcExceptionCallable$ExceptionTransformingFuture.onFailure(GrpcExceptionCallable.java:97)
	at com.google.api.core.ApiFutures$1.onFailure(ApiFutures.java:84)
	at repackaged.com.google.common.util.concurrent.Futures$CallbackListener.run(Futures.java:1127)
	at repackaged.com.google.common.util.concurrent.DirectExecutor.execute(DirectExecutor.java:31)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.executeListener(AbstractFuture.java:1286)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.complete(AbstractFuture.java:1055)
	at repackaged.com.google.common.util.concurrent.AbstractFuture.setException(AbstractFuture.java:807)
	at io.grpc.stub.ClientCalls$GrpcFuture.setException(ClientCalls.java:568)
	at io.grpc.stub.ClientCalls$UnaryStreamToFuture.onClose(ClientCalls.java:538)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.api.gax.grpc.ChannelPool$ReleasingClientCall$1.onClose(ChannelPool.java:546)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at io.grpc.PartialForwardingClientCallListener.onClose(PartialForwardingClientCallListener.java:39)
	at io.grpc.ForwardingClientCallListener.onClose(ForwardingClientCallListener.java:23)
	at io.grpc.ForwardingClientCallListener$SimpleForwardingClientCallListener.onClose(ForwardingClientCallListener.java:40)
	at com.google.cloud.spanner.spi.v1.SpannerErrorInterceptor$1$1.onClose(SpannerErrorInterceptor.java:100)
	at io.grpc.internal.ClientCallImpl.closeObserver(ClientCallImpl.java:567)
	at io.grpc.internal.ClientCallImpl.access$300(ClientCallImpl.java:71)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInternal(ClientCallImpl.java:735)
	at io.grpc.internal.ClientCallImpl$ClientStreamListenerImpl$1StreamClosed.runInContext(ClientCallImpl.java:716)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	... 1 more
Caused by: io.grpc.StatusRuntimeException: ABORTED: Database schema has changed
	at io.grpc.Status.asRuntimeException(Status.java:537)
	... 21 more


Jan 22, 2024 7:08:58 AM org.example.Main main
INFO: 1000000 operations done...
Jan 22, 2024 7:08:58 AM org.example.Main main
INFO: 16 total aborted exceptions...
Jan 22, 2024 7:08:58 AM org.example.Main main
INFO: Exiting application...
```