package io.kestra.plugin.adanos;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString(exclude = "apiKey")
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractAdanosTask extends Task implements RunnableTask<AbstractAdanosTask.Output> {
    private static final String DEFAULT_BASE_URL = "https://api.adanos.org";

    @Schema(
        title = "Adanos API key",
        description = "API key sent in the `X-API-Key` request header. Create one at https://adanos.org/register."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    protected Property<String> apiKey;

    @Schema(title = "Adanos API base URL", description = "Base URL for Adanos API requests.")
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> baseUrl = Property.ofValue(DEFAULT_BASE_URL);

    @Schema(
        title = "Asset type",
        description = "Whether to query stock or crypto sentiment. Crypto currently supports the Reddit source only."
    )
    @Builder.Default
    @PluginProperty(group = "main")
    protected Property<AssetType> assetType = Property.ofValue(AssetType.STOCK);

    @Schema(
        title = "Sentiment source",
        description = "Stocks support Reddit, X / FinTwit, News, and Polymarket. Crypto currently supports Reddit."
    )
    @Builder.Default
    @PluginProperty(group = "main")
    protected Property<Source> source = Property.ofValue(Source.REDDIT);

    @Schema(title = "From date", description = "Inclusive UTC start date for the query window.")
    @PluginProperty(group = "processing")
    protected Property<LocalDate> from;

    @Schema(
        title = "To date",
        description = "Inclusive UTC end date for the query window. Omit it to use the current UTC date."
    )
    @PluginProperty(group = "processing")
    protected Property<LocalDate> to;

    @Schema(
        title = "Result handling mode",
        description = "`FETCH` returns the complete response, `FETCH_ONE` returns the first list item or response object, `STORE` writes records to Kestra internal storage, and `NONE` omits the response body."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Schema(title = "HTTP request options", description = "Timeouts for the Adanos HTTP request.")
    @PluginProperty(group = "advanced")
    protected RequestOptions options;

    protected Output get(RunContext runContext, String path, Map<String, Object> queryParameters) throws Exception {
        String rBaseUrl = runContext.render(baseUrl).as(String.class).orElse(DEFAULT_BASE_URL);
        String rApiKey = renderRequired(runContext, apiKey, String.class, "apiKey");
        FetchType rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        URI uri = URI.create(trimTrailingSlash(rBaseUrl) + path + queryString(queryParameters));

        HttpRequest request = HttpRequest.builder()
            .uri(uri)
            .method("GET")
            .addHeader("Accept", "application/json")
            .addHeader("X-API-Key", rApiKey)
            .build();

        runContext.logger().debug("Requesting Adanos endpoint {}", path);

        try (HttpClient client = new HttpClient(runContext, httpConfiguration(runContext))) {
            HttpResponse<String> response = client.request(request, String.class);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Adanos returned an empty response body for endpoint `" + path + "`.");
            }

            Object body = JacksonMapper.ofJson().readValue(responseBody, new TypeReference<>() {});
            return handleFetch(runContext, body, rFetchType);
        }
    }

    protected RequestContext requestContext(RunContext runContext) throws IllegalVariableEvaluationException {
        AssetType rAssetType = runContext.render(assetType).as(AssetType.class).orElse(AssetType.STOCK);
        Source rSource = runContext.render(source).as(Source.class).orElse(Source.REDDIT);

        if (rAssetType == AssetType.CRYPTO && rSource != Source.REDDIT) {
            throw new IllegalArgumentException("Crypto sentiment currently supports the Reddit source only.");
        }

        Map<String, Object> query = new LinkedHashMap<>();
        LocalDate rFrom = renderDate(runContext, from);
        LocalDate rTo = renderDate(runContext, to);

        if (rFrom != null && rTo != null && rTo.isBefore(rFrom)) {
            throw new IllegalArgumentException("`to` must be on or after `from`.");
        }

        if (rFrom != null) {
            query.put("from", rFrom);
        }
        if (rTo != null) {
            query.put("to", rTo);
        }

        return new RequestContext(rAssetType, rSource, query);
    }

    protected String endpointPrefix(RequestContext context) {
        if (context.assetType() == AssetType.CRYPTO) {
            return "/reddit/crypto/v1";
        }

        return "/" + context.source().path() + "/stocks/v1";
    }

    protected String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    protected <T> T renderRequired(RunContext runContext, Property<T> property, Class<T> type, String name)
        throws IllegalVariableEvaluationException {
        if (property == null) {
            throw new IllegalArgumentException("`" + name + "` must not be empty.");
        }

        return runContext.render(property).as(type)
            .filter(value -> !String.valueOf(value).isBlank())
            .orElseThrow(() -> new IllegalArgumentException("`" + name + "` must not be empty."));
    }

    private LocalDate renderDate(RunContext runContext, Property<LocalDate> property)
        throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }

        return runContext.render(property).as(LocalDate.class).orElse(null);
    }

    private Output handleFetch(RunContext runContext, Object body, FetchType rFetchType) throws Exception {
        List<?> records = body instanceof List<?> list ? list : List.of(body);
        int size = records.size();

        return switch (rFetchType) {
            case FETCH -> Output.builder().body(body).size(size).build();
            case FETCH_ONE -> Output.builder()
                .body(records.isEmpty() ? null : records.getFirst())
                .size(records.isEmpty() ? 0 : 1)
                .build();
            case STORE -> {
                java.io.File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                    FileSerde.writeAll(writer, Flux.fromIterable(records)).block();
                }

                yield Output.builder()
                    .uri(runContext.storage().putFile(tempFile))
                    .size(size)
                    .build();
            }
            default -> Output.builder().size(0).build();
        };
    }

    private String queryString(Map<String, Object> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }

        StringBuilder query = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() == null || String.valueOf(entry.getValue()).isBlank()) {
                continue;
            }

            if (!first) {
                query.append('&');
            }
            query.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
            first = false;
        }

        return first ? "" : query.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    HttpConfiguration httpConfiguration(RunContext runContext) throws IllegalVariableEvaluationException {
        var timeout = TimeoutConfiguration.builder();
        if (options != null) {
            Property<Duration> connectTimeout = renderedDuration(runContext, options.connectTimeout);
            Property<Duration> readIdleTimeout = renderedDuration(runContext, options.readIdleTimeout);
            if (connectTimeout != null) {
                timeout.connectTimeout(connectTimeout);
            }
            if (readIdleTimeout != null) {
                timeout.readIdleTimeout(readIdleTimeout);
            }
        }
        return HttpConfiguration.builder()
            .timeout(timeout.build())
            .build();
    }

    private Property<Duration> renderedDuration(RunContext runContext, Property<Duration> property)
        throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }
        return runContext.render(property).as(Duration.class).map(Property::ofValue).orElse(null);
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    public enum AssetType {
        STOCK,
        CRYPTO
    }

    public enum Source {
        REDDIT("reddit"),
        X("x"),
        NEWS("news"),
        POLYMARKET("polymarket");

        private final String path;

        Source(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    protected record RequestContext(AssetType assetType, Source source, Map<String, Object> query) {
    }

    @Getter
    @Builder
    public static class RequestOptions {
        @Schema(title = "Connection timeout", description = "Time allowed to establish a connection before failing.")
        @PluginProperty(group = "execution")
        private final Property<Duration> connectTimeout;

        @Schema(title = "Read idle timeout", description = "How long a read may stay idle before closing.")
        @Builder.Default
        @PluginProperty(group = "execution")
        private final Property<Duration> readIdleTimeout = Property.ofValue(Duration.of(5, ChronoUnit.MINUTES));
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Response item count", description = "Number of root response records returned by Adanos.")
        private final Integer size;

        @Schema(
            title = "Response body",
            description = "Adanos response payload. Available for `FETCH` and `FETCH_ONE`."
        )
        private final Object body;

        @Schema(title = "Stored response URI", description = "Kestra internal storage URI available for `STORE`.")
        private final URI uri;
    }
}
