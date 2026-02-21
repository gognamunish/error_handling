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

## Why Resilience4j (Not Spring Retry)

- This codebase uses reactive `WebClient` + Reactor `Mono`; Resilience4j provides native Reactor operators.
- Retry policies are service-driven from `PlatformProperties.ServiceDefinition.retrySettings`.
- Runtime retry metadata (`RetryInfo`) is attached to API success/error envelopes.
- Spring Retry is stronger in imperative `@Retryable` method interception; for these reactive pipelines, Resilience4j keeps the flow cleaner and more explicit.

## Important Design Decision

Retry key is **service-level with protocol namespace** (`rest:<serviceId>`, `soap:<serviceId>`), not per operation/request.

Reason:
- stable key cardinality in `RetryRegistry`
- fewer cached retry instances
- predictable logs/metrics

## How It Is Used

`RestCallExecutor` and `SoapCallExecutor` call:

1. `RetryPolicyExecutor.execute(...)` with:
   - `retryName = rest:<serviceId>` for REST
   - `retryName = soap:<serviceId>` for SOAP
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
