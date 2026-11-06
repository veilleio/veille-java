package io.veille;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import io.veille.params.*;

/**
 * Official Veille API client. Create one instance and reuse it; it is thread-safe.
 *
 * <pre>{@code
 * VeilleClient client = VeilleClient.fromEnv(); // reads VEILLE_API_KEY
 * ApiResponse response = client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").build());
 * }</pre>
 */
public final class VeilleClient {
  /** Production API origin. */
  public static final String DEFAULT_BASE_URL = "https://api.veille.io";

  /** Default client-side deadline per request. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

  /** Environment variable read by {@link #fromEnv()}. */
  public static final String API_KEY_ENVIRONMENT_VARIABLE = "VEILLE_API_KEY";

  /** SDK version sent in the User-Agent header. */
  public static final String VERSION = "0.1.0";

  /** Job statuses {@link #waitForJob} stops on. */
  public static final List<String> JOB_TERMINAL_STATUSES = List.of("CompletedJob", "Error", "NotFound", "BadRequest");

  /** Tasks run at the same time by {@link #batch}. */
  public static final int DEFAULT_CONCURRENCY = 10;

  /** Retries after a network error or a 408, 429, 500, 502, 503 or 504 response. */
  public static final int MAX_RETRIES = 3;

  /** Each retry waits this many times longer than the previous one (a Retry-After header wins). */
  public static final double RETRY_BACKOFF_FACTOR = 1.5;

  /** Delay before the first retry. */
  public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

  /** Longest delay between two attempts. */
  public static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

  private static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 500, 502, 503, 504);

  private static final List<String> ERROR_MESSAGE_KEYS = List.of("error", "message", "error_message", "detail");

  private final String apiKey;
  private final String baseUrl;
  private final Duration timeout;
  private final Map<String, String> headers;
  private final VeilleTransport transport;
  private final int maxRetries;
  private final Duration retryDelay;

  private VeilleClient(Builder builder) {
    String key = builder.apiKey;
    if (key == null || key.isBlank()) {
      throw new VeilleConfigurationException(
          "A non-blank Veille API key is required. Pass apiKey(...) or set " + API_KEY_ENVIRONMENT_VARIABLE + ".");
    }
    if (key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) {
      throw new VeilleConfigurationException("The Veille API key cannot be used in an HTTP header.");
    }
    String base = builder.baseUrl.replaceAll("/+$", "");
    URI uri;
    try {
      uri = URI.create(base);
    } catch (IllegalArgumentException e) {
      throw new VeilleConfigurationException("baseUrl must be an absolute http or https URL.", e);
    }
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) {
      throw new VeilleConfigurationException("baseUrl must be an absolute http or https URL.");
    }
    if (builder.timeout != null && (builder.timeout.isNegative() || builder.timeout.isZero())) {
      throw new VeilleConfigurationException("timeout must be positive, or null to disable it.");
    }
    this.apiKey = key;
    this.baseUrl = base;
    this.timeout = builder.timeout;
    this.headers = Map.copyOf(builder.headers);
    if (builder.maxRetries < 0) {
      throw new VeilleConfigurationException("maxRetries must be zero or more.");
    }
    if (builder.retryDelay == null || builder.retryDelay.isNegative()) {
      throw new VeilleConfigurationException("retryDelay must be zero or more.");
    }
    this.maxRetries = builder.maxRetries;
    this.retryDelay = builder.retryDelay;
    this.transport = builder.transport != null ? builder.transport : VeilleTransport.defaultTransport();
  }

  /** Creates a client that reads the API key from {@value #API_KEY_ENVIRONMENT_VARIABLE}. */
  public static VeilleClient fromEnv() {
    return builder().build();
  }

  /** Creates a client with an explicit API key. */
  public static VeilleClient of(String apiKey) {
    return builder().apiKey(apiKey).build();
  }

  /** Returns a new builder. The API key defaults to {@value #API_KEY_ENVIRONMENT_VARIABLE}. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Sends any request, including endpoints released after this SDK version. GET parameters travel
   * in the query string; POST parameters travel in a JSON body.
   */
  public ApiResponse request(String method, String path, Map<String, ?> params) {
    return request(method, path, params, RequestOptions.defaults());
  }

  /** {@link #request(String, String, Map)} with per-request options. */
  public ApiResponse request(String method, String path, Map<String, ?> params, RequestOptions options) {
    String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
    return send(verb, path, params == null ? Map.of() : params, options, false);
  }

  /**
   * Polls {@code GET /v1/jobs/{id}} until the job reaches a terminal status. The delay starts at {@code
   * interval} (default 3 s) and grows 1.5x per poll up to 60 s; {@code timeout} defaults to 12 hours.
   *
   * @param onProgress called with the job document after every poll, may be null
   */
  public ApiResponse waitForJob(String jobId, Duration interval, Duration timeout, Consumer<JsonNode> onProgress) {
    if (jobId == null || jobId.isBlank()) {
      throw new VeilleSerializationException("A non-blank job id is required.", "id");
    }
    Duration pause = interval == null ? Duration.ofSeconds(3) : interval;
    Duration deadline = timeout == null ? Duration.ofHours(12) : timeout;
    Duration maxPause = pause.compareTo(Duration.ofMinutes(1)) > 0 ? pause : Duration.ofMinutes(1);
    long started = System.nanoTime();
    while (true) {
      ApiResponse last = request("GET", "/v1/jobs/{id}", Map.of("id", jobId));
      JsonNode job = last.json();
      if (onProgress != null) {
        onProgress.accept(job);
      }
      if (JOB_TERMINAL_STATUSES.contains(job.path("status").asText(""))) {
        return last;
      }
      if (Duration.ofNanos(System.nanoTime() - started).plus(pause).compareTo(deadline) > 0) {
        throw new VeilleJobTimeoutException(jobId, last);
      }
      try {
        Thread.sleep(pause.toMillis());
        Duration next = Duration.ofMillis(pause.toMillis() * 3 / 2);
        pause = next.compareTo(maxPause) > 0 ? maxPause : next;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new VeilleTransportException("waitForJob was interrupted.", e);
      }
    }
  }

  /** Polls a job with the default interval (3 s, growing to 60 s) and timeout (12 h). */
  public ApiResponse waitForJob(String jobId) {
    return waitForJob(jobId, null, null, null);
  }

  /**
   * Domain Validation. Check any domain instantly. Detects disposable email providers, public webmail, relay services, and education domains. Returns DNS health with MX provider detection, domain history, and optional SSL/TLS certificate transparency. Credit cost: 2 credits per call.
   *
   * <p>{@code GET /v1/intelligence/domain}
   */
  public ApiResponse intelligenceDomain(IntelligenceDomainParams params) {
    return intelligenceDomain(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/intelligence/domain} with per-request options. */
  public ApiResponse intelligenceDomain(IntelligenceDomainParams params, RequestOptions options) {
    return call("intelligence_domain", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Email Validation. Validate any email address in a single call. Detects disposable and temporary inboxes, identifies role accounts, public webmail, relay services, and returns a 0-100 risk score. For deep deliverability verification (SMTP check, catch-all de… Credit cost: 3 credits per call.
   *
   * <p>{@code GET /v1/intelligence/email}
   */
  public ApiResponse intelligenceEmail(IntelligenceEmailParams params) {
    return intelligenceEmail(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/intelligence/email} with per-request options. */
  public ApiResponse intelligenceEmail(IntelligenceEmailParams params, RequestOptions options) {
    return call("intelligence_email", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Email Qualification. Deep email deliverability verification. Performs full Email Validation plus SMTP inbox check, catch-all detection, and returns a qualification verdict (valid, invalid, risky, disposable, unknown). Credit cost: 5 credits per call.
   *
   * <p>{@code GET /v1/intelligence/emailq}
   */
  public ApiResponse intelligenceEmailq(IntelligenceEmailqParams params) {
    return intelligenceEmailq(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/intelligence/emailq} with per-request options. */
  public ApiResponse intelligenceEmailq(IntelligenceEmailqParams params, RequestOptions options) {
    return call("intelligence_emailq", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * IP Reputation. Assess the reputation of any IP address. Detects VPNs, proxies, Tor exit nodes, datacenter hosting, abusive traffic, and bots. Returns geolocation, network owner, and a 0-100 threat score. Credit cost: 2 credits per call.
   *
   * <p>{@code GET /v1/intelligence/ip}
   */
  public ApiResponse intelligenceIp(IntelligenceIpParams params) {
    return intelligenceIp(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/intelligence/ip} with per-request options. */
  public ApiResponse intelligenceIp(IntelligenceIpParams params, RequestOptions options) {
    return call("intelligence_ip", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Phone Validation. Validate any phone number in E.164 format. Detects disposable and virtual numbers, identifies carrier and line type (mobile/fixed/VoIP), returns country and timezone, and computes a 0-100 risk score. Credit cost: 1 credit per call.
   *
   * <p>{@code GET /v1/intelligence/phone}
   */
  public ApiResponse intelligencePhone(IntelligencePhoneParams params) {
    return intelligencePhone(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/intelligence/phone} with per-request options. */
  public ApiResponse intelligencePhone(IntelligencePhoneParams params, RequestOptions options) {
    return call("intelligence_phone", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Jobs. Poll the status, progress (0–100) and result of a job started with is_async=true. Polling is free. Poll until status is terminal: CompletedJob, Error, NotFound or BadRequest.
   *
   * <p>{@code GET /v1/jobs/{id}}
   */
  public ApiResponse jobs(JobsParams params) {
    return jobs(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/jobs/{id}} with per-request options. */
  public ApiResponse jobs(JobsParams params, RequestOptions options) {
    return call("jobs", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Stats. Infrastructure health for an engine or endpoint over the last hour. Not scoped to your account or tenant. Enabled per account: HTTP 403 means contact support to activate it.
   *
   * <p>{@code GET /v1/stats}
   */
  public ApiResponse stats(StatsParams params) {
    return stats(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/stats} with per-request options. */
  public ApiResponse stats(StatsParams params, RequestOptions options) {
    return call("stats", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Usage. Retrieve real-time usage statistics and credit balance for your Veille account.
   *
   * <p>{@code GET /v1/usage}
   */
  public ApiResponse usage() {
    return usage(RequestOptions.defaults());
  }

  /** {@code GET /v1/usage} with per-request options. */
  public ApiResponse usage(RequestOptions options) {
    return call("usage", Map.of(), options);
  }

  /**
   * VAT Validation. Validate a European VAT number against the VIES database. Returns company name, registered address, and validity status for any EU member state. Credit cost: 1 credit per call.
   *
   * <p>{@code GET /v1/vat}
   */
  public ApiResponse vat(VatParams params) {
    return vat(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/vat} with per-request options. */
  public ApiResponse vat(VatParams params, RequestOptions options) {
    return call("vat", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * IBAN Validation. Validate any IBAN and retrieve associated bank details. Returns the BIC/SWIFT code, bank name, country, BBAN structural breakdown, and SEPA zone membership. Credit cost: 1 credit per call.
   *
   * <p>{@code GET /v1/vat/iban}
   */
  public ApiResponse vatIban(VatIbanParams params) {
    return vatIban(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/vat/iban} with per-request options. */
  public ApiResponse vatIban(VatIbanParams params, RequestOptions options) {
    return call("vat_iban", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Exchange Rates. Get current ECB exchange rates for any supported base currency. Optionally filter to specific target currencies. Updated daily from the European Central Bank. Credit cost: 1 credit per call.
   *
   * <p>{@code GET /v1/vat/rates}
   */
  public ApiResponse vatRates(VatRatesParams params) {
    return vatRates(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/vat/rates} with per-request options. */
  public ApiResponse vatRates(VatRatesParams params, RequestOptions options) {
    return call("vat_rates", params == null ? Map.of() : params.toMap(), options);
  }

  private ApiResponse call(String name, Map<String, Object> params, RequestOptions options) {
    Endpoints.Endpoint endpoint = Endpoints.ALL.get(name);
    RequestOptions opts = options == null ? RequestOptions.defaults() : options;
    String method = opts.method() == null ? endpoint.methods().get(0) : opts.method();
    if (!endpoint.methods().contains(method)) {
      throw new VeilleConfigurationException(
          endpoint.path() + " supports " + String.join(", ", endpoint.methods()) + ", not " + method + ".");
    }
    for (String required : endpoint.required()) {
      if (params.get(required) == null) {
        throw new VeilleSerializationException("The \"" + required + "\" parameter is required.", required);
      }
    }
    return send(method, endpoint.path(), params, opts, endpoint.json());
  }

  private ApiResponse send(
      String method, String path, Map<String, ?> params, RequestOptions options, boolean expectJson) {
    if (!"GET".equals(method) && !"POST".equals(method)) {
      throw new VeilleConfigurationException("Unsupported HTTP method: " + method + ".");
    }
    if (path == null || !path.startsWith("/")) {
      throw new VeilleConfigurationException("The request path must start with \"/\".");
    }
    RequestOptions opts = options == null ? RequestOptions.defaults() : options;
    Serialization.Serialized serialized = Serialization.serialize(method, path, params);
    String url = baseUrl + serialized.path() + (serialized.query().isEmpty() ? "" : "?" + serialized.query());

    Map<String, String> requestHeaders = new LinkedHashMap<>();
    requestHeaders.put("Accept", "application/json, */*;q=0.8");
    requestHeaders.put("User-Agent", "veille-java/" + VERSION);
    requestHeaders.putAll(headers);
    requestHeaders.putAll(opts.headers());
    requestHeaders.put("x-api-key", apiKey);
    byte[] body = null;
    if (serialized.body() != null) {
      requestHeaders.put("Content-Type", "application/json");
      body = Serialization.toJson(serialized.body());
    }
    Duration requestTimeout = opts.timeout() != null ? opts.timeout() : timeout;

    int retries = opts.maxRetries() != null ? Math.max(opts.maxRetries(), 0) : maxRetries;
    RawResponse raw;
    for (int attempt = 0; ; attempt++) {
      String retryAfter = null;
      try {
        raw = transport.send(new VeilleTransport.Request(method, URI.create(url), requestHeaders, body, requestTimeout));
        if (raw.status() >= 200 && raw.status() <= 299) {
          break;
        }
        if (attempt >= retries || !isRetryable(raw)) {
          throw apiException(raw);
        }
        List<String> values = raw.headers().get("retry-after");
        retryAfter = values == null || values.isEmpty() ? null : values.get(0);
      } catch (HttpTimeoutException e) {
        throw new VeilleTransportException("Veille request timed out.", e);
      } catch (IOException e) {
        if (attempt >= retries) {
          throw new VeilleTransportException("Veille request failed before an HTTP response was received.", e);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new VeilleTransportException("Veille request was interrupted.", e);
      }
      pause(retryDelay(attempt, retryAfter));
    }
    ApiResponse response = new ApiResponse(raw);
    if (response.isJson()) {
      try {
        response.json();
      } catch (VeilleResponseDecodingException e) {
        throw new VeilleResponseDecodingException(
            "Veille returned a successful JSON response that could not be decoded.", raw, e);
      }
    } else if (expectJson) {
      throw new VeilleResponseDecodingException(
          "Veille returned a successful non-JSON response for a JSON operation.", raw, null);
    }
    return response;
  }

  /**
   * Server errors and plan rate limits are retried. The API reports rate limits as 401 (or 429) with a "Rate limit
   * exceeded" message; per-key quotas ("...: quota daily") do not reset within seconds.
   */
  private static boolean isRetryable(RawResponse raw) {
    if (RETRYABLE_STATUSES.contains(raw.status())) {
      return true;
    }
    if (raw.status() != 401 && raw.status() != 429) {
      return false;
    }
    String error = apiException(raw).errorMessage();
    String message = error == null ? "" : error.toLowerCase(Locale.ROOT);
    if (message.contains("quota")) {
      return false;
    }
    return message.contains("rate limit") || (raw.status() == 429 && message.isEmpty());
  }

  private Duration retryDelay(int attempt, String retryAfter) {
    if (retryAfter != null) {
      try {
        double seconds = Double.parseDouble(retryAfter.trim());
        if (seconds >= 0) {
          return Duration.ofMillis((long) (Math.min(seconds, 60) * 1000));
        }
      } catch (NumberFormatException e) {
        // An HTTP date: fall back to exponential backoff.
      }
    }
    double millis = retryDelay.toMillis() * Math.pow(RETRY_BACKOFF_FACTOR, attempt);
    return Duration.ofMillis((long) Math.min(millis, MAX_RETRY_DELAY.toMillis()));
  }

  private static void pause(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VeilleTransportException("Veille request was interrupted while waiting to retry.", e);
    }
  }

  /**
   * Runs {@code task} for every item on {@code concurrency} threads and returns one {@link BatchResult} per
   * item, in input order. A failing item never stops the others; the client is thread-safe.
   *
   * <pre>{@code
   * List<BatchResult<ApiResponse>> results = client.batch(ids, 5, id -> client.someEndpoint(...));
   * }</pre>
   */
  public <I, T> List<BatchResult<T>> batch(Collection<? extends I> items, int concurrency, Function<? super I, ? extends T> task) {
    if (concurrency < 1) {
      throw new VeilleConfigurationException("concurrency must be a positive integer.");
    }
    List<I> list = new ArrayList<>(items);
    if (list.isEmpty()) {
      return List.of();
    }
    ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, list.size()), runnable -> {
      Thread thread = new Thread(runnable, "veille-batch");
      thread.setDaemon(true);
      return thread;
    });
    try {
      List<Future<T>> futures = new ArrayList<>(list.size());
      for (I item : list) {
        futures.add(pool.submit(() -> task.apply(item)));
      }
      List<BatchResult<T>> results = new ArrayList<>(list.size());
      for (int i = 0; i < futures.size(); i++) {
        try {
          results.add(new BatchResult<>(i, futures.get(i).get(), null));
        } catch (ExecutionException e) {
          results.add(new BatchResult<>(i, null, e.getCause()));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new VeilleTransportException("batch was interrupted.", e);
        }
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  /** {@link #batch(Collection, int, Function)} with {@link #DEFAULT_CONCURRENCY} threads. */
  public <I, T> List<BatchResult<T>> batch(Collection<? extends I> items, Function<? super I, ? extends T> task) {
    return batch(items, DEFAULT_CONCURRENCY, task);
  }

  private static VeilleApiException apiException(RawResponse raw) {
    JsonNode body = null;
    String message = null;
    try {
      body = Serialization.MAPPER.readTree(raw.body());
    } catch (IOException e) {
      body = null;
    }
    if (body != null && body.isObject()) {
      for (String key : ERROR_MESSAGE_KEYS) {
        JsonNode value = body.get(key);
        if (value != null && value.isTextual() && !value.asText().isEmpty()) {
          message = value.asText();
          break;
        }
      }
    }
    return new VeilleApiException(raw, body, message);
  }

  /** Builder for {@link VeilleClient}. */
  public static final class Builder {
    private String apiKey = System.getenv(API_KEY_ENVIRONMENT_VARIABLE);
    private String baseUrl = DEFAULT_BASE_URL;
    private Duration timeout = DEFAULT_TIMEOUT;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private VeilleTransport transport;
    private int maxRetries = MAX_RETRIES;
    private Duration retryDelay = DEFAULT_RETRY_DELAY;

    private Builder() {}

    /** Retries after a network error or a 408/429/5xx response; 0 disables retries. Default 3. */
    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /** Delay before the first retry, growing 1.5x per retry. Default 1 second. */
    public Builder retryDelay(Duration retryDelay) {
      this.retryDelay = retryDelay;
      return this;
    }

    /** API key sent in the {@code x-api-key} header. */
    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /** API origin, for tests or proxies. */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
      return this;
    }

    /** Client-side deadline per request; null disables it. */
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Header sent with every request. */
    public Builder header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    /** Custom transport; defaults to {@link java.net.http.HttpClient} without redirects. */
    public Builder transport(VeilleTransport transport) {
      this.transport = transport;
      return this;
    }

    /** Validates the configuration and creates the client. */
    public VeilleClient build() {
      return new VeilleClient(this);
    }
  }
}
