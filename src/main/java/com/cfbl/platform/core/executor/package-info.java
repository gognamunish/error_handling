/**
 * Outbound execution abstractions for provider integrations.
 *
 * <p>This package contains protocol-specific executors (REST and SOAP) that:
 * <ul>
 *   <li>execute outbound calls with {@code RetryPolicyExecutor}</li>
 *   <li>map success results to {@code ApiResponse<T>}</li>
 *   <li>map failures to {@code CreditSummaryPlatformException} hierarchy</li>
 *   <li>attach provider metadata ({@code DataProviderContext}) and retry metadata ({@code RetryInfo})</li>
 * </ul>
 *
 * <p>Use {@code RestCallExecutor} for retry-enabled WebClient flows, {@code SimpleRestExecutor}
 * for lightweight WebClient calls without retry overhead, and {@code SoapCallExecutor}
 * for supplier-driven SOAP port invocations.
 */
package com.cfbl.platform.core.executor;
