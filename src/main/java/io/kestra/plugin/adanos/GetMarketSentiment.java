package io.kestra.plugin.adanos;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Get market sentiment",
    description = "Retrieves aggregate Adanos sentiment and attention metrics for the selected market and source."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch Polymarket sentiment for the stock market.",
            full = true,
            code = """
                id: adanos_market_sentiment
                namespace: company.research

                tasks:
                  - id: get_market
                    type: io.kestra.plugin.adanos.GetMarketSentiment
                    apiKey: "{{ secret('ADANOS_API_KEY') }}"
                    assetType: STOCK
                    source: POLYMARKET
                """
        )
    }
)
public class GetMarketSentiment extends AbstractAdanosTask {
    @Override
    public Output run(RunContext runContext) throws Exception {
        RequestContext context = requestContext(runContext);
        return get(runContext, endpointPrefix(context) + "/market-sentiment", context.query());
    }
}
