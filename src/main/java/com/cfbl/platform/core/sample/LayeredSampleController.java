package com.cfbl.platform.core.sample;

import com.cfbl.platform.core.exception.api.ApiResponse;
import com.cfbl.platform.core.integration.model.ProviderResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Demonstrates clean layering: controller maps {@link ProviderResult} to {@link ApiResponse}.
 */
@RestController
@RequestMapping("/demo/layered")
public class LayeredSampleController {

    private final LayeredSampleService layeredSampleService;

    public LayeredSampleController(LayeredSampleService layeredSampleService) {
        this.layeredSampleService = layeredSampleService;
    }

    /**
     * Demo GET endpoint using service-layer {@code ProviderResult} mapping at controller boundary.
     */
    @GetMapping("/sample")
    public Mono<ResponseEntity<ApiResponse<String>>> fetchSample() {
        return layeredSampleService.fetchSample().map(this::toApiResponseEntity);
    }

    /**
     * Demo POST endpoint using service-layer {@code ProviderResult} mapping at controller boundary.
     */
    @PostMapping("/sample")
    public Mono<ResponseEntity<ApiResponse<String>>> createSample(@RequestBody CreateSampleInput request) {
        return layeredSampleService.createSample(request.customerId()).map(this::toApiResponseEntity);
    }

    private <T> ResponseEntity<ApiResponse<T>> toApiResponseEntity(ProviderResult<T> result) {
        ApiResponse<T> body = ApiResponse.fromProviderResult(result);
        return ResponseEntity.status(result.status()).body(body);
    }

    public record CreateSampleInput(String customerId) {
    }
}
