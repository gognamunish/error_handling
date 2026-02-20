# Retry Package Guide

This package provides reactive retry policy execution using Resilience4j.

## Classes

- `RetryPolicyExecutor`
  - Applies retry policies to a `Supplier<Mono<T>>`.
  - Integrates Resilience4j Reactor `RetryOperator`.
  - Emits retry attempt logs.
- `RetrySettings`
  - Retry configuration model (`enabled`, `maxAttempts`, `waitDurationMs`).
  - Stored per service under `PlatformProperties.ServiceDefinition`.
- `RetryInfo`
  - Runtime retry metadata returned to API clients (`attempted`, `maxAttempts`, `retried`, `exhausted`).

## Purpose

- Keep retry logic centralized and reusable.
- Avoid custom retry loops in service/adapters.
- Surface retry outcomes in both success and error API responses.

## Important Design Decision

Retry key is **service-level** (`serviceId`), not per operation/request.

Reason:
- stable key cardinality in `RetryRegistry`
- fewer cached retry instances
- predictable logs/metrics

## How It Is Used

`RestCallExecutor` calls:

1. `RetryPolicyExecutor.execute(...)` with:
   - `retryName = serviceId`
   - `RetrySettings` from `WebClientHolder` / `PlatformProperties`
   - supplier that performs one outbound call
   - predicate that marks retryable exceptions
2. On completion, caller maps attempt count into `RetryInfo`.

## Configuration

In `application.yml` under each service:

```yaml
kxt:
  platform:
    services:
      sample-api:
        retry-settings:
          enabled: true
          max-attempts: 3
          wait-duration-ms: 200
```

## Usage Rules

- Keep retry predicate strict (retry only transient failures).
- Do not use dynamic retry keys (avoid request IDs, dynamic paths, user IDs).
- Tune `maxAttempts` and `waitDurationMs` per service, not globally for all integrations.

