# Exception Package Guide

This package provides the platform-standard error model and API error envelope.

## Package Layout

- `api/`
  - `ApiGlobalExceptionHandler`: central controller advice.
  - `ApiResponseMapper`: maps platform exceptions to `ApiResponse<Void>`.
  - `ApiResponse`: shared response envelope for success and error.
- `core/`
  - `CreditSummaryPlatformException`: base exception type.
  - `CreditSummaryDataCollectionException`, `CreditSummaryBusinessException`, `CreditSummaryPresentationException`: layer-specific exceptions.
  - `LayerType`: canonical layer identity (`PLATFORM`, `DATA_COLLECTION`, `BUSINESS`, `PRESENTATION`).
  - `ErrorCode`: error code to HTTP status mapping.
  - `DataProviderContext`: provider metadata (service, endpoint, protocol, timing).
  - `UpstreamInfo`: upstream raw failure details.

## Purpose

- Keep all API error responses consistent.
- Keep layer-specific failures explicit (`DATA_COLLECTION`, `BUSINESS`, `PRESENTATION`).
- Carry provider and retry metadata to API clients for observability and debugging.

## Standard Flow

1. Service/adapter throws `CreditSummaryPlatformException` (or subclass).
2. Exception optionally carries:
   - `DataProviderContext`
   - `UpstreamInfo`
   - `RetryInfo`
3. `ApiGlobalExceptionHandler` catches it and delegates to `ApiResponseMapper`.
4. `ApiResponseMapper` builds the API error envelope.

## Usage Rules

- Throw platform exceptions from internal layers; do not leak third-party exceptions to controllers.
- Prefer layer-specific exception subclasses instead of raw `CreditSummaryPlatformException`.
- Always attach `DataProviderContext` for provider calls.
- Use `ErrorCode` values for HTTP status mapping; do not hardcode status codes in services.
- Keep error messages safe for API output (no stack traces/secrets).

## Extension Checklist

When adding a new error type:

1. Add/confirm `ErrorCode`.
2. Add exception subclass (if layer-specific behavior is needed).
3. Ensure mapper/handler path is covered.
4. Add tests for:
   - HTTP status
   - response body code/layer/reason
   - metadata/retry fields when applicable
