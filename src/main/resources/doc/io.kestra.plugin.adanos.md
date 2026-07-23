Use the Adanos plugin to add market sentiment and attention data to Kestra flows.

Adanos combines Reddit, X / FinTwit, financial news, and Polymarket signals for stocks. Reddit crypto sentiment is also available. The plugin provides four read-only tasks for individual assets, trending assets, comparisons, and market-level sentiment.

Create an API key at [adanos.org/register](https://adanos.org/register), save it as a Kestra secret, and reference it with `{{ secret('ADANOS_API_KEY') }}`. The key is sent in the `X-API-Key` header and is not logged.

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
    from: 2026-07-01
    to: 2026-07-07
```

Use `fetchType: STORE` for list responses that should be written to Kestra internal storage. Crypto requests currently require `assetType: CRYPTO` and `source: REDDIT`; other combinations are rejected before a network call.

The plugin uses the API's `from` and `to` date parameters and does not expose the deprecated `days` shorthand. Current endpoint details and plan limits are documented at [api.adanos.org/docs](https://api.adanos.org/docs).
