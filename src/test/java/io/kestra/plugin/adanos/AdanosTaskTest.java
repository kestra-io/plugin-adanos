package io.kestra.plugin.adanos;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdanosTaskTest extends AbstractAdanosTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void appliesDefaultReadIdleTimeout() throws Exception {
        var task = GetMarketSentiment.builder().build();
        var runContext = runContextFactory.of();

        var configuration = task.httpConfiguration(runContext);
        var timeout = configuration.getTimeout();
        var readIdleTimeout = runContext.render(timeout.getReadIdleTimeout()).as(Duration.class).orElseThrow();

        assertThat(readIdleTimeout, is(Duration.ofMinutes(5)));
        assertThat(configuration.getMaxContentLength(), is(10 * 1024 * 1024));
    }

    @Test
    void deserializesRequestOptionsFromYaml() throws Exception {
        var options = JacksonMapper.ofYaml().readValue(
            """
                connectTimeout: PT10S
                readIdleTimeout: PT30S
                """,
            AbstractAdanosTask.RequestOptions.class
        );

        var runContext = runContextFactory.of();
        assertThat(
            runContext.render(options.getConnectTimeout()).as(Duration.class).orElseThrow(),
            is(Duration.ofSeconds(10))
        );
        assertThat(
            runContext.render(options.getReadIdleTimeout()).as(Duration.class).orElseThrow(),
            is(Duration.ofSeconds(30))
        );
    }

    @Test
    void getsStockSentimentWithAuthenticationAndDates() throws Exception {
        var task = GetAssetSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .assetType(Property.ofValue(AbstractAdanosTask.AssetType.STOCK))
            .source(Property.ofValue(AbstractAdanosTask.Source.REDDIT))
            .symbol(Property.ofValue("TSLA"))
            .from(Property.ofValue(LocalDate.of(2026, 7, 1)))
            .to(Property.ofValue(LocalDate.of(2026, 7, 7)))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(FakeAdanosController.lastPath(), is("/reddit/stocks/v1/stock/TSLA"));
        assertThat(FakeAdanosController.headers().get("x-api-key"), is("test-api-key"));
        assertThat(FakeAdanosController.queryParameters().get("from"), is("2026-07-01"));
        assertThat(FakeAdanosController.queryParameters().get("to"), is("2026-07-07"));
        assertThat(((Map<?, ?>) output.getBody()).get("ticker"), is("TSLA"));
        assertThat(output.getSize(), is(1));
    }

    @Test
    void getsCryptoSentimentFromReddit() throws Exception {
        var task = GetAssetSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .assetType(Property.ofValue(AbstractAdanosTask.AssetType.CRYPTO))
            .source(Property.ofValue(AbstractAdanosTask.Source.REDDIT))
            .symbol(Property.ofValue("BTC"))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(FakeAdanosController.lastPath(), is("/reddit/crypto/v1/token/BTC"));
        assertThat(((Map<?, ?>) output.getBody()).get("symbol"), is("BTC"));
    }

    @Test
    void listsTrendingStocksWithPagination() throws Exception {
        var task = ListTrendingAssets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .source(Property.ofValue(AbstractAdanosTask.Source.X))
            .limit(Property.ofValue(2))
            .offset(Property.ofValue(4))
            .stockAssetType(Property.ofValue(ListTrendingAssets.StockAssetType.STOCK))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(FakeAdanosController.lastPath(), is("/x/stocks/v1/trending"));
        assertThat(FakeAdanosController.queryParameters().get("limit"), is("2"));
        assertThat(FakeAdanosController.queryParameters().get("offset"), is("4"));
        assertThat(FakeAdanosController.queryParameters().get("type"), is("stock"));
        assertThat(output.getSize(), is(2));
    }

    @Test
    void comparesStockSymbols() throws Exception {
        var task = CompareAssets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .source(Property.ofValue(AbstractAdanosTask.Source.NEWS))
            .symbols(Property.ofValue(List.of("TSLA", "NVDA")))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(FakeAdanosController.lastPath(), is("/news/stocks/v1/compare"));
        assertThat(FakeAdanosController.queryParameters().get("tickers"), is("TSLA,NVDA"));
        assertThat(output.getSize(), is(1));
    }

    @Test
    void comparesCryptoSymbols() throws Exception {
        var task = CompareAssets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .assetType(Property.ofValue(AbstractAdanosTask.AssetType.CRYPTO))
            .source(Property.ofValue(AbstractAdanosTask.Source.REDDIT))
            .symbols(Property.ofValue(List.of("BTC", "ETH")))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(FakeAdanosController.lastPath(), is("/reddit/crypto/v1/compare"));
        assertThat(FakeAdanosController.queryParameters().get("symbols"), is("BTC,ETH"));
        assertThat(output.getSize(), is(1));
    }

    @Test
    void rejectsMoreThanTenDistinctCompareSymbolsBeforeRequest() {
        var task = CompareAssets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .symbols(Property.ofValue(List.of(
                "AAPL", "AMD", "AMZN", "GOOG", "META", "MSFT", "NFLX", "NVDA", "TSLA", "TSM", "ORCL"
            )))
            .build();

        var error = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
        assertThat(error.getMessage(), containsString("no more than ten"));
        assertThat(FakeAdanosController.lastPath(), is((String) null));
    }

    @Test
    void getsPolymarketMarketSentiment() throws Exception {
        var task = GetMarketSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .source(Property.ofValue(AbstractAdanosTask.Source.POLYMARKET))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(FakeAdanosController.lastPath(), is("/polymarket/stocks/v1/market-sentiment"));
        assertThat(output.getBody(), notNullValue());
    }

    @Test
    void fetchesOneTrendingRecord() throws Exception {
        var task = ListTrendingAssets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .source(Property.ofValue(AbstractAdanosTask.Source.X))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(((Map<?, ?>) output.getBody()).get("ticker"), is("TSLA"));
        assertThat(output.getSize(), is(1));
    }

    @Test
    void omitsResponseBodyForNoneFetchType() throws Exception {
        var task = GetMarketSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .source(Property.ofValue(AbstractAdanosTask.Source.POLYMARKET))
            .fetchType(Property.ofValue(FetchType.NONE))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getBody(), is((Object) null));
        assertThat(output.getUri(), is((Object) null));
        assertThat(output.getSize(), is(0));
    }

    @Test
    void rejectsBlankApiKeyBeforeRequest() {
        var task = GetAssetSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue(" "))
            .symbol(Property.ofValue("TSLA"))
            .build();

        var error = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
        assertThat(error.getMessage(), containsString("apiKey"));
        assertThat(FakeAdanosController.lastPath(), is((String) null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void storesTrendingRecordsInInternalStorage() throws Exception {
        var task = ListTrendingAssets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .assetType(Property.ofValue(AbstractAdanosTask.AssetType.CRYPTO))
            .source(Property.ofValue(AbstractAdanosTask.Source.REDDIT))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var output = task.run(runContextFactory.of());

        assertThat(output.getUri(), notNullValue());
        assertThat(output.getBody(), is((Object) null));
        assertThat(output.getSize(), is(2));

        var rows = new CopyOnWriteArrayList<Map<String, Object>>();
        try (var input = new BufferedReader(new InputStreamReader(
            storageInterface.get(TenantService.MAIN_TENANT, null, output.getUri())
        ))) {
            FileSerde.reader(input, row -> rows.add((Map<String, Object>) row));
        }

        assertThat(rows.size(), is(2));
        assertThat(rows.getFirst().get("symbol"), is("BTC"));
    }

    @Test
    void rejectsUnsupportedCryptoSourceBeforeRequest() {
        var task = GetMarketSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .assetType(Property.ofValue(AbstractAdanosTask.AssetType.CRYPTO))
            .source(Property.ofValue(AbstractAdanosTask.Source.NEWS))
            .build();

        var error = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
        assertThat(error.getMessage(), containsString("Reddit source only"));
        assertThat(FakeAdanosController.lastPath(), is((String) null));
    }

    @Test
    void rejectsReversedDateWindowBeforeRequest() {
        var task = GetMarketSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .from(Property.ofValue(LocalDate.of(2026, 7, 8)))
            .to(Property.ofValue(LocalDate.of(2026, 7, 1)))
            .build();

        var error = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
        assertThat(error.getMessage(), containsString("on or after"));
        assertThat(FakeAdanosController.lastPath(), is((String) null));
    }

    @Test
    void preservesAdanosErrorStatusAndBody() {
        var task = GetAssetSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .symbol(Property.ofValue("FAIL"))
            .build();

        var error = assertThrows(HttpClientResponseException.class, () -> task.run(runContextFactory.of()));
        assertThat(error.getMessage(), containsString("response code '429'"));
        assertThat(error.getMessage(), containsString("Monthly request limit reached"));
    }

    @Test
    void rejectsEmptySuccessfulResponseWithEndpointContext() {
        var task = GetAssetSentiment.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api"))
            .apiKey(Property.ofValue("test-api-key"))
            .symbol(Property.ofValue("EMPTY"))
            .build();

        var error = assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of()));
        assertThat(error.getMessage(), containsString("empty response body"));
        assertThat(error.getMessage(), containsString("/reddit/stocks/v1/stock/EMPTY"));
    }
}
