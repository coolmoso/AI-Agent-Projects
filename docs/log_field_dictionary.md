# Log Field Dictionary

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `@timestamp` | ISO-8601 | Log timestamp | `2024-05-18T10:15:30.000Z` |
| `level` | string | Log level | `INFO`, `ERROR`, `WARN` |
| `logger_name` | string | Java class name | `org.example.qaagent.service.ChatService` |
| `message` | string | Human-readable message | `Chat completed: latency=1234ms` |
| `traceId` | string (MDC) | Unique request trace ID | `a1b2c3d4e5f6` |
| `userId` | string (MDC) | Requesting user ID | `user123` |
| `sessionId` | string (MDC) | Conversation session ID | `uuid-value` |
| `rewrittenQuery` | string (MDC) | Standalone query after rewriting | `What is the PTO policy?` |
| `confidenceScore` | double (MDC) | Retrieval confidence score | `0.72` |
| `stack_trace` | string | Exception stack trace (errors only) | `java.io.IOException...` |

## Sample Log Entries

### Successful Request
```json
{
  "@timestamp": "2024-05-18T10:15:30.123Z",
  "level": "INFO",
  "logger_name": "org.example.qaagent.service.ChatService",
  "message": "Chat completed: latency=1823ms, tokens=542, confidence=0.720",
  "traceId": "a1b2c3d4e5f6",
  "userId": "user123",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "rewrittenQuery": "What is the annual leave policy for full-time employees?"
}
```

### Refusal
```json
{
  "@timestamp": "2024-05-18T10:15:31.456Z",
  "level": "INFO",
  "logger_name": "org.example.qaagent.service.SafetyChecker",
  "message": "Request refused: reason=low_confidence",
  "traceId": "b2c3d4e5f6g7",
  "userId": "user456",
  "confidenceScore": "0.18"
}
```

### Error
```json
{
  "@timestamp": "2024-05-18T10:15:32.789Z",
  "level": "ERROR",
  "logger_name": "org.example.qaagent.service.LlmClient",
  "message": "LLM API error: 429 rate limit exceeded",
  "traceId": "c3d4e5f6g7h8",
  "stack_trace": "java.io.IOException: LLM API error: 429 ..."
}
```
