package io.kestra.plugin.adanos;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Compare assets",
    description = "Compares Adanos sentiment and attention metrics for multiple stocks or crypto assets."
)
@Plugin(
    examples = {
        @Example(
            title = "Compare financial-news sentiment for stocks.",
            full = true,
            code = """
                id: adanos_compare_stocks
                namespace: company.research

                tasks:
                  - id: compare
                    type: io.kestra.plugin.adanos.CompareAssets
                    apiKey: "{{ secret('ADANOS_API_KEY') }}"
                    assetType: STOCK
                    source: NEWS
                    symbols:
                      - TSLA
                      - NVDA
                      - AMD
                """
        )
    }
)
public class CompareAssets extends AbstractAdanosTask {
    @Schema(
        title = "Tickers or symbols",
        description = "Between 2 and 10 distinct stock tickers or crypto symbols to compare."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<String>> symbols;

    @Override
    public Output run(RunContext runContext) throws Exception {
        RequestContext context = requestContext(runContext);
        List<String> rSymbols = runContext.render(symbols).asList(String.class);

        if (rSymbols == null) {
            throw new IllegalArgumentException("`symbols` must not be empty.");
        }

        List<String> normalized = rSymbols.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.startsWith("$") ? value.substring(1) : value)
            .map(value -> value.toUpperCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        if (normalized.size() < 2) {
            throw new IllegalArgumentException("`symbols` must contain at least two distinct values.");
        }
        if (normalized.size() > 10) {
            throw new IllegalArgumentException("`symbols` must contain no more than ten distinct values.");
        }

        String parameter = context.assetType() == AssetType.CRYPTO ? "symbols" : "tickers";
        context.query().put(parameter, String.join(",", normalized));
        return get(runContext, endpointPrefix(context) + "/compare", context.query());
    }
}
