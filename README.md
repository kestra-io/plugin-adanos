<p align="center">
  <a href="https://kestra.io">
    <img src="https://kestra.io/banner.png" alt="Kestra workflow orchestrator" />
  </a>
</p>

# Kestra Adanos Plugin

Read-only [Adanos Market Sentiment API](https://adanos.org/) tasks for Kestra workflows. The plugin exposes structured sentiment and attention data for stocks from Reddit, X / FinTwit, financial news, and Polymarket, plus Reddit crypto sentiment.

## Tasks

| Task | Purpose |
| --- | --- |
| `GetAssetSentiment` | Get sentiment for one stock ticker or crypto symbol. |
| `ListTrendingAssets` | List assets with the strongest current attention signals. |
| `CompareAssets` | Compare sentiment and buzz across multiple assets. |
| `GetMarketSentiment` | Get aggregate market-level sentiment. |

All tasks support Kestra `FetchType` handling:

- `FETCH` returns the complete JSON response.
- `FETCH_ONE` returns one response record.
- `STORE` writes records to Kestra internal storage.
- `NONE` omits the response body.

## Authentication

Create an API key at [adanos.org/register](https://adanos.org/register) and store it as a Kestra secret. The plugin sends it only in the `X-API-Key` header and excludes it from task string representations.

```yaml
id: adanos_daily_sentiment
namespace: company.research

tasks:
  - id: get_tsla_sentiment
    type: io.kestra.plugin.adanos.GetAssetSentiment
    apiKey: "{{ secret('ADANOS_API_KEY') }}"
    assetType: STOCK
    source: REDDIT
    symbol: TSLA
    from: 2026-07-01
    to: 2026-07-07
```

Stock tasks support `REDDIT`, `X`, `NEWS`, and `POLYMARKET`. Crypto tasks currently support `REDDIT`. Unsupported combinations fail before an HTTP request is made.

The plugin uses the API's `from` and `to` date parameters. It intentionally does not expose the deprecated `days` shorthand.

## Plans and API documentation

The four core tasks use endpoints available with Free API keys. Adanos applies plan-specific request quotas and date-window limits. See the [API documentation](https://api.adanos.org/docs) and [pricing page](https://adanos.org/pricing) for current limits.

## Development

Java 21 is required.

```bash
./gradlew test
./gradlew lintPluginDocs
./gradlew shadowJar
```

The test suite uses an embedded HTTP controller and never calls the live Adanos API.

## License

Apache 2.0 © Kestra Technologies
