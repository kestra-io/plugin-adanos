package io.kestra.plugin.adanos;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
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
    title = "List trending assets",
    description = "Lists stocks or crypto assets with the strongest Adanos attention and sentiment signals."
)
@Plugin(
    examples = {
        @Example(
            title = "List trending stocks from X / FinTwit.",
            full = true,
            code = """
                id: adanos_trending_stocks
                namespace: company.research

                tasks:
                  - id: list_trending
                    type: io.kestra.plugin.adanos.ListTrendingAssets
                    apiKey: "{{ secret('ADANOS_API_KEY') }}"
                    assetType: STOCK
                    source: X
                    limit: 20
                    offset: 0
                """
        )
    }
)
public class ListTrendingAssets extends AbstractAdanosTask {
    @Schema(title = "Maximum results", description = "Maximum number of assets to return, from 1 to 100.")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<@Min(1) @Max(100) Integer> limit = Property.ofValue(20);

    @Schema(title = "Pagination offset", description = "Number of matching assets to skip before returning results.")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<@Min(0) Integer> offset = Property.ofValue(0);

    @Schema(
        title = "Stock asset filter",
        description = "Optional stock-only filter. It is not sent for crypto requests."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<StockAssetType> stockAssetType = Property.ofValue(StockAssetType.ALL);

    @Override
    public Output run(RunContext runContext) throws Exception {
        RequestContext context = requestContext(runContext);
        int rLimit = runContext.render(limit).as(Integer.class).orElse(20);
        int rOffset = runContext.render(offset).as(Integer.class).orElse(0);

        if (rLimit < 1 || rLimit > 100) {
            throw new IllegalArgumentException("`limit` must be between 1 and 100.");
        }
        if (rOffset < 0) {
            throw new IllegalArgumentException("`offset` must be zero or greater.");
        }

        context.query().put("limit", rLimit);
        context.query().put("offset", rOffset);
        if (context.assetType() == AssetType.STOCK) {
            StockAssetType rType = runContext.render(stockAssetType).as(StockAssetType.class).orElse(StockAssetType.ALL);
            context.query().put("type", rType.name().toLowerCase());
        }

        return get(runContext, endpointPrefix(context) + "/trending", context.query());
    }

    public enum StockAssetType {
        STOCK,
        ETF,
        ALL
    }
}
