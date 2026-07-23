package io.kestra.plugin.adanos;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Controller("/api")
@Produces(MediaType.APPLICATION_JSON)
public class FakeAdanosController {
    private static final Map<String, String> headers = new ConcurrentHashMap<>();
    private static final Map<String, String> queryParameters = new ConcurrentHashMap<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();

    public static Map<String, String> headers() {
        return headers;
    }

    public static Map<String, String> queryParameters() {
        return queryParameters;
    }

    public static String lastPath() {
        return lastPath.get();
    }

    public static void reset() {
        headers.clear();
        queryParameters.clear();
        lastPath.set(null);
    }

    @Get("/reddit/stocks/v1/stock/{symbol}")
    public HttpResponse<String> redditStock(HttpRequest<?> request, @PathVariable String symbol) {
        capture(request, "/reddit/stocks/v1/stock/" + symbol);
        if (symbol.equals("FAIL")) {
            return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).body("""
                {"detail":{"error":"rate_limit_exceeded","message":"Monthly request limit reached."}}
                """);
        }
        return HttpResponse.ok("""
            {"ticker":"%s","found":true,"buzz_score":82.4,"sentiment_score":0.31,"mentions":125}
            """.formatted(symbol));
    }

    @Get("/reddit/crypto/v1/token/{symbol}")
    public HttpResponse<String> redditCrypto(HttpRequest<?> request, @PathVariable String symbol) {
        capture(request, "/reddit/crypto/v1/token/" + symbol);
        return HttpResponse.ok("""
            {"symbol":"%s","found":true,"buzz_score":74.2,"sentiment_score":0.17,"mentions":91}
            """.formatted(symbol));
    }

    @Get("/x/stocks/v1/trending")
    public HttpResponse<String> xTrending(HttpRequest<?> request) {
        capture(request, "/x/stocks/v1/trending");
        return HttpResponse.ok("""
            [
              {"ticker":"TSLA","buzz_score":87.5,"trend":"rising"},
              {"ticker":"NVDA","buzz_score":76.1,"trend":"stable"}
            ]
            """);
    }

    @Get("/reddit/crypto/v1/trending")
    public HttpResponse<String> cryptoTrending(HttpRequest<?> request) {
        capture(request, "/reddit/crypto/v1/trending");
        return HttpResponse.ok("""
            [
              {"symbol":"BTC","buzz_score":90.1},
              {"symbol":"ETH","buzz_score":71.3}
            ]
            """);
    }

    @Get("/news/stocks/v1/compare")
    public HttpResponse<String> newsCompare(HttpRequest<?> request) {
        capture(request, "/news/stocks/v1/compare");
        return HttpResponse.ok("""
            {"tickers":["TSLA","NVDA"],"winner":"TSLA"}
            """);
    }

    @Get("/reddit/crypto/v1/compare")
    public HttpResponse<String> cryptoCompare(HttpRequest<?> request) {
        capture(request, "/reddit/crypto/v1/compare");
        return HttpResponse.ok("""
            {"symbols":["BTC","ETH"],"winner":"BTC"}
            """);
    }

    @Get("/polymarket/stocks/v1/market-sentiment")
    public HttpResponse<String> polymarketMarket(HttpRequest<?> request) {
        capture(request, "/polymarket/stocks/v1/market-sentiment");
        return HttpResponse.ok("""
            {"market_count":42,"sentiment_score":0.08,"trend":"stable"}
            """);
    }

    private void capture(HttpRequest<?> request, String path) {
        lastPath.set(path);
        headers.clear();
        request.getHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));

        queryParameters.clear();
        request.getParameters().forEach((name, values) -> queryParameters.put(name, values.getFirst()));
    }
}
