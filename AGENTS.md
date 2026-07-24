# Kestra Adanos Plugin

## What

- Provides read-only Adanos market sentiment tasks under `io.kestra.plugin.adanos`.
- Includes asset sentiment, trending, comparison, and market-level tasks.

## Why

- What user problem does this solve? Kestra workflows otherwise need hand-built HTTP requests and response handling for market sentiment data.
- Why would a team adopt this plugin in a workflow? It provides typed, secret-safe tasks for stocks and crypto with Kestra-native fetch and storage modes.
- What operational/business outcome does it enable? Teams can schedule sentiment snapshots and feed structured alternative data into alerts, reports, and research pipelines.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `adanos`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.adanos.GetAssetSentiment`
- `io.kestra.plugin.adanos.ListTrendingAssets`
- `io.kestra.plugin.adanos.CompareAssets`
- `io.kestra.plugin.adanos.GetMarketSentiment`

### Project Structure

```
plugin-adanos/
├── src/main/java/io/kestra/plugin/adanos/
├── src/test/java/io/kestra/plugin/adanos/
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
