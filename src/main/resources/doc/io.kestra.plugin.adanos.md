# How to use the Adanos plugin

Use the Adanos plugin to add market sentiment and attention data to Kestra flows. Adanos combines Reddit, X / FinTwit, financial news, and Polymarket signals for stocks. Reddit crypto sentiment is also available.

## Authentication

Create an API key at [adanos.org/register](https://adanos.org/register), save it as a [Kestra secret](https://kestra.io/docs/concepts/secret), and provide it through the `apiKey` property. The plugin sends the key in the `X-API-Key` header and excludes it from task logs.

```yaml
apiKey: "{{ secret('ADANOS_API_KEY') }}"
```

The optional `baseUrl` property defaults to `https://api.adanos.org`.

## Tasks

- `GetAssetSentiment` retrieves the current sentiment summary for one asset. It requires `symbol` and accepts the shared optional properties below.
- `ListTrendingAssets` lists assets with the strongest attention and sentiment signals. It accepts optional `limit`, `offset`, and stock-only `stockAssetType` properties in addition to the shared properties.
- `CompareAssets` compares sentiment for multiple assets. It requires between 2 and 10 distinct values in `symbols` and accepts the shared optional properties.
- `GetMarketSentiment` retrieves source-level market sentiment and accepts the shared optional properties.

Every task requires `apiKey`. Shared optional properties are `baseUrl`, `assetType`, `source`, `from`, `to`, `fetchType`, and `options`.

All tasks support `FETCH`, `FETCH_ONE`, `STORE`, and `NONE` result handling through `fetchType`. Use `fetchType: STORE` to write response records to Kestra internal storage.

Crypto requests require `assetType: CRYPTO` and `source: REDDIT`; unsupported source combinations are rejected before a network call. The plugin uses the API's `from` and `to` date parameters and does not expose the deprecated `days` shorthand. Current endpoint details and plan limits are documented at [api.adanos.org/docs](https://api.adanos.org/docs).

The following flow compares the financial-news sentiment of three stocks:

```yaml
id: adanos_market_research
namespace: company.research

tasks:
  - id: compare_stocks
    type: io.kestra.plugin.adanos.CompareAssets
    apiKey: "{{ secret('ADANOS_API_KEY') }}"
    assetType: STOCK
    source: NEWS
    symbols:
      - TSLA
      - NVDA
      - AMD
```
