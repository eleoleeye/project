See the [TASK](./TASK.md) file for instructions.

Please document your solution here...

## Trade Enrichment Service

### How to Run the Service

1. Ensure you have JDK 17 and Maven installed.
2. Build the project using Maven: mvn clean install
3. Start the application: mvn spring-boot:run or via Ide

### How to use API

```bash
curl -XPOST 'http://localhost:8080/api/v1/enrich' \
--header 'Content-Type: text/csv' \
-T '/path/to/input/file/trade.csv' \
-o path/to/output/file/result.csv    
```
## General Solution Overview

The solution is designed to process large files gradually, without waiting for the entire file to be received. As soon as the file starts streaming, we begin processing it, splitting the data into chunks for parallel processing and efficient handling. This approach ensures that large files are not fully loaded into memory, preventing memory overflows.

### Key Points:
1. **Streamed File Processing**:
   - The file is processed as it is being received, without waiting for the complete file to be downloaded. This allows for immediate processing and avoids latency or delays associated with waiting for the entire file. Additionally, this approach ensures that we don’t need to keep large files in memory, which could lead to memory overflow and crashes.

2. **Chunk-Based Processing**:
   - The file is split into chunks of significant size (e.g., 10,000 lines per chunk). This chunking strategy allows for the handling of large files without overloading memory. Each chunk is processed independently, ensuring that even very large files can be processed efficiently without running out of memory.

3. **Optimized Chunk Handling**:
   - Each chunk's result is aggregated into a single string to minimize the overhead of writing individual lines to the output stream. By batching results together, the writing process becomes faster and more efficient, reducing the need for frequent writes to the output.

4. **Parallel Processing**:
   - Chunks are processed in parallel using a pool of worker threads. This parallelization speeds up file processing by distributing the workload across multiple CPU cores, allowing for faster execution, especially for large files.

5. **Single-threaded Writer**:
   - A single-threaded writer is responsible for writing the processed results into the response. The writer reads from a queue where processed chunks are placed and writes them to the output stream as they are processed, ensuring memory is managed efficiently and results are sent as soon as they are available.

6. **Asynchronous Response Streaming**:
   - The response is streamed asynchronously to the client. As soon as the first chunk is processed, the writer starts sending data back to the client. There is no need to wait for the entire file to be processed before beginning to send the response. This progressive streaming ensures that memory is not overloaded, and the client receives data without delay.

### Benefits:
- **Real-time File Processing**: We begin processing the file immediately upon receiving the first part, without waiting for the complete file to be downloaded.
- **Memory Efficiency**: By processing the file in chunks and avoiding loading the entire file into memory, we ensure that the system can handle large files without running out of memory, preventing potential crashes.
- **Parallelization**: Multiple threads process chunks concurrently, significantly improving performance for large files.
- **Efficient Writing**: Aggregating chunk results into strings and using a single writer thread ensures that writing to the output stream is fast and optimized.
- **Asynchronous Streaming**: The client starts receiving the processed data as soon as possible, reducing wait time and improving user experience by streaming the response progressively.


## Current Limitations

1. **Limited Scalability for High Load**: The service processes trades in chunks using a fixed-size thread pool (`THREAD_POOL_SIZE`). While this works efficiently for moderate loads, under high load or when there are multiple concurrent requests, the performance may degrade. The current thread pool size is bound by the number of available processors, which could lead to delays or bottlenecks when several requests are processed simultaneously.

2. **No Request Prioritization**: Currently, the service processes all requests in a First In, First Out (FIFO) manner. There is no mechanism to prioritize critical or urgent trade processing requests over others. This can become problematic in scenarios where some requests require faster processing than others. For example, in financial systems, some trades might be more critical and need to be enriched faster.

3. **Blocking Queue Without Boundaries**: The current implementation uses an unbounded `LinkedBlockingQueue`, which could lead to memory issues under high load. If the writer (output processing) is slower than the trade processing tasks, the queue can grow indefinitely, potentially exhausting system memory.

4. **No Backpressure Handling**: There is no backpressure mechanism in place to slow down the data producers when the queue is full. If the queue grows too large, the system could become overwhelmed, as the trade processing threads are unaware that the writer is lagging behind.

5. **Limited Timeout Handling for Chunk Processing**: While there is a timeout for the writer thread (`5 minutes`), there is no similar timeout handling for the chunk processing tasks. This could lead to issues where one or more chunk processing threads get stuck, delaying the overall trade enrichment process.

## Future Improvements (With More Time)

1. **Request Prioritization**: Implementing request prioritization could be a significant improvement. This could be done by using a `PriorityBlockingQueue` instead of a standard queue, allowing tasks with higher priorities to be processed first. Each request can be assigned a priority level (e.g., high, medium, low), and the system will prioritize requests with higher urgency. For instance:

   This ensures that critical trade requests are enriched before less important ones when multiple requests are processed concurrently.

2. **Dynamic Thread Pool Scaling**: Instead of a fixed-size thread pool, you could implement a dynamically scalable thread pool. This allows the system to allocate more resources during periods of high load and scale down during lighter loads. Libraries like `ForkJoinPool` or custom `ThreadPoolExecutor` configurations can help achieve this.

3. **Bounded Blocking Queue**: To avoid unbounded memory growth, switching to a `BoundedBlockingQueue` (such as `ArrayBlockingQueue`) can limit the number of tasks waiting in the queue. This forces producers (processing tasks) to wait if the queue is full, preventing the system from running out of memory. For example:

4. **Backpressure Mechanism**: A backpressure mechanism should be introduced to prevent overloading the system. When the `resultQueue` becomes full or close to full, the system can slow down or temporarily halt the processing of new trades until space becomes available. This can be achieved by checking the remaining capacity of the queue and applying backpressure when necessary:
   This helps balance the flow of data between the processing tasks and the writer, ensuring smooth operation even under high load.

5. **Improved Timeout Management**: Introducing timeouts for individual chunk processing tasks ensures that the system doesn’t get stuck waiting for slow or problematic chunks to finish. If a chunk takes too long, it can be aborted or retried without affecting the rest of the trade enrichment process.

6. **Better Error Handling and Partial Results**: Improving the current error handling mechanisms to allow partial results to be returned even in the case of failures can enhance system robustness. Instead of interrupting the entire process, it’s possible to skip or retry problematic chunks while continuing to process others.

