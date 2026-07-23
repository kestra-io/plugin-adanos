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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get asset sentiment",
    description = "Retrieves the current Adanos sentiment summary for one stock ticker or crypto symbol."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch Reddit sentiment for a stock.",
            full = true,
            code = """
                id: adanos_stock_sentiment
                namespace: company.research

                tasks:
                  - id: get_sentiment
                    type: io.kestra.plugin.adanos.GetAssetSentiment
                    apiKey: "{{ secret('ADANOS_API_KEY') }}"
                    assetType: STOCK
                    source: REDDIT
                    symbol: TSLA
                    from: 2026-07-01
                    to: 2026-07-07
                """
        ),
        @Example(
            title = "Fetch Reddit sentiment for a crypto asset.",
            full = true,
            code = """
                id: adanos_crypto_sentiment
                namespace: company.research

                tasks:
                  - id: get_sentiment
                    type: io.kestra.plugin.adanos.GetAssetSentiment
                    apiKey: "{{ secret('ADANOS_API_KEY') }}"
                    assetType: CRYPTO
                    source: REDDIT
                    symbol: BTC
                """
        )
    }
)
public class GetAssetSentiment extends AbstractAdanosTask {
    @Schema(title = "Ticker or symbol", description = "Stock ticker such as `TSLA` or crypto symbol such as `BTC`.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> symbol;

    @Override
    public Output run(RunContext runContext) throws Exception {
        RequestContext context = requestContext(runContext);
        String rSymbol = renderRequired(runContext, symbol, String.class, "symbol");
        String resource = context.assetType() == AssetType.CRYPTO ? "/token/" : "/stock/";

        return get(runContext, endpointPrefix(context) + resource + pathSegment(rSymbol), context.query());
    }
}
