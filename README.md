# Veille Java SDK

The official Java SDK for the [Veille](https://www.veille.io) fraud prevention API.

Validate emails, domains, IPs, VAT numbers and IBANs in milliseconds to block fake sign-ups, bots and fraud.

The SDK exposes **11 typed methods**, one per documented endpoint, each with a parameter builder, plus a generic `request()`. It uses `java.net.http.HttpClient` and Jackson.

## Requirements

- Java 17 or newer

## Installation

The SDK is not on Maven Central. Build it from source, which installs it in your local Maven repository:

```sh
git clone https://github.com/veilleio/veille-java.git
cd veille-java
mvn -q install -DskipTests
```

Then add it to your project. Maven:

```xml
<dependency>
  <groupId>io.veille</groupId>
  <artifactId>veille</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle, with `mavenLocal()` in your repositories:

```kotlin
implementation("io.veille:veille:0.1.0")
```

## Get your API key

[Sign up for Veille](https://app.veille.io/register) or sign in, then copy your API key from the [dashboard](https://app.veille.io). `VeilleClient.fromEnv()` reads the `VEILLE_API_KEY` environment variable.

## Quick start

```java
import io.veille.ApiResponse;
import io.veille.VeilleClient;
import io.veille.params.IntelligenceEmailParams;

VeilleClient client = VeilleClient.fromEnv();
ApiResponse response = client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").build());
System.out.println(response.json().toPrettyString());
```

Parameter builders mirror the [API reference](https://app.veille.io/openapi); `build()` checks required parameters. Use `response.as(MyType.class)` to map the JSON onto your own types.

## Async jobs

Any call accepts `is_async: true` (useful past the 60-second server limit): the response is then a `job_id`. `waitForJob` polls `GET /v1/jobs/{id}` until a terminal status (`CompletedJob`, `Error`, `NotFound`, `BadRequest`):

```java
ApiResponse submitted = client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").isAsync(true).build());
ApiResponse job = client.waitForJob(
    submitted.json().get("job_id").asText(),
    Duration.ofSeconds(3),
    Duration.ofHours(12),
    progress -> System.out.println(progress.path("status").asText() + " " + progress.path("progress").asInt()));
```

## GET or POST

Methods use GET when the endpoint supports it (query string). POST sends the same parameters as JSON:

## Any endpoint

```java
ApiResponse response = client.request("GET", "/v1/usage", Map.of());
```

## Errors

```java
try {
  client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").build());
} catch (VeilleApiException e) {
  System.err.println(e.status() + ": " + e.getMessage());
}
```

| Exception | When |
| --- | --- |
| `VeilleConfigurationException` | Missing API key, invalid options or unsupported method. |
| `VeilleSerializationException` | A required parameter is missing or a value can't be sent. |
| `VeilleTransportException` | Network failure, timeout or interruption. |
| `VeilleApiException` | Non-2xx status (`status()`, `errorMessage()`, `responseBody()`, `rawResponse()`). |
| `VeilleResponseDecodingException` | A success response could not be decoded. |

All of them extend the unchecked `VeilleException`.

## Retries

Requests are retried up to 3 times after a network error, a `408`, `500`, `502`, `503` or `504`, or a rate limit — which the API returns as `401` (or `429`) with a `Rate limit exceeded` message. Per-key quota errors (`…: quota daily`) are not retried, nor are `400`, `402`, `404` or client-side timeouts. The delay starts at 1 second and grows 1.5× per retry (capped at 30 seconds); a `Retry-After` header wins.

```java
VeilleClient client = VeilleClient.builder().maxRetries(5).retryDelay(Duration.ofMillis(500)).build();
client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").build(), RequestOptions.builder().maxRetries(0).build()); // one request without retries
```

## Batch requests

`batch()` runs a function per item on 10 threads (`concurrency`) and returns one `BatchResult` per item, in input order. A failing item never stops the others.

```java
List<String> inputs = List.of("jane.doe@example.com", "john@mailinator.com", "contact@veille.io");
List<BatchResult<ApiResponse>> results = client.batch(inputs, 5, value ->
    client.intelligenceEmail(IntelligenceEmailParams.builder().query(value).build()));
for (BatchResult<ApiResponse> result : results) {
  if (result.ok()) {
    System.out.println(result.value().json());
  } else {
    System.err.println(inputs.get(result.index()) + ": " + result.error().getMessage());
  }
}
```

| Constant | Default | Description |
| --- | --- | --- |
| `VeilleClient.DEFAULT_CONCURRENCY` | `10` | Tasks run at the same time by batch |
| `VeilleClient.DEFAULT_TIMEOUT` | `Duration.ofSeconds(120)` | Request timeout |
| `VeilleClient.MAX_RETRIES` | `3` | Retry attempts after a network error, a 408/5xx or a rate limit |
| `VeilleClient.RETRY_BACKOFF_FACTOR` | `1.5` | Exponential backoff multiplier |
| `VeilleClient.DEFAULT_RETRY_DELAY` | `Duration.ofSeconds(1)` | Delay before the first retry |

## Configuration

```java
VeilleClient client = VeilleClient.builder()
    .apiKey(System.getenv("VEILLE_API_KEY"))
    .timeout(Duration.ofSeconds(60)) // default 120 s, null disables
    .header("X-Trace", "abc")
    .transport(VeilleTransport.fromHttpClient(myHttpClient)) // optional
    .build();
```

## Endpoints

11 methods, generated from the [OpenAPI document](https://app.veille.io/openapi).

| Method | HTTP | Description |
| --- | --- | --- |
| `intelligenceDomain()` | GET `/v1/intelligence/domain` | Check any domain instantly. Detects disposable email providers, public webmail, relay services, and education domains. Returns DNS health w… |
| `intelligenceEmail()` | GET `/v1/intelligence/email` | Validate any email address in a single call. Detects disposable and temporary inboxes, identifies role accounts, public webmail, relay serv… |
| `intelligenceEmailq()` | GET `/v1/intelligence/emailq` | Deep email deliverability verification. Performs full Email Validation plus SMTP inbox check, catch-all detection, and returns a qualificat… |
| `intelligenceIp()` | GET `/v1/intelligence/ip` | Assess the reputation of any IP address. Detects VPNs, proxies, Tor exit nodes, datacenter hosting, abusive traffic, and bots. Returns geol… |
| `intelligencePhone()` | GET `/v1/intelligence/phone` | Validate any phone number in E.164 format. Detects disposable and virtual numbers, identifies carrier and line type (mobile/fixed/VoIP), re… |
| `jobs()` | GET `/v1/jobs/{id}` | Poll the status, progress (0–100) and result of a job started with is_async=true. Polling is free. Poll until status is terminal: Completed… |
| `stats()` | GET `/v1/stats` | Infrastructure health for an engine or endpoint over the last hour. Not scoped to your account or tenant. Enabled per account: HTTP 403 mea… |
| `usage()` | GET `/v1/usage` | Retrieve real-time usage statistics and credit balance for your Veille account. |
| `vat()` | GET `/v1/vat` | Validate a European VAT number against the VIES database. Returns company name, registered address, and validity status for any EU member s… |
| `vatIban()` | GET `/v1/vat/iban` | Validate any IBAN and retrieve associated bank details. Returns the BIC/SWIFT code, bank name, country, BBAN structural breakdown, and SEPA… |
| `vatRates()` | GET `/v1/vat/rates` | Get current ECB exchange rates for any supported base currency. Optionally filter to specific target currencies. Updated daily from the Eur… |

## All Veille SDKs

| SDK | Package | Install |
| --- | --- | --- |
| Node.js | [`@veilleio/sdk`](https://www.npmjs.com/package/@veilleio/sdk) | `npm install @veilleio/sdk` |
| Python | [`veille`](https://pypi.org/project/veille/) | `pip install veille` |
| Go | [`veille-go`](https://pkg.go.dev/github.com/veilleio/veille-go) | `go get github.com/veilleio/veille-go` |
| .NET | [`Veille`](https://www.nuget.org/packages/Veille) | `dotnet add package Veille` |
| Ruby | [`veille`](https://rubygems.org/gems/veille) | `gem install veille` |
| PHP | [`veille/sdk`](https://packagist.org/packages/veille/sdk) | `composer require veille/sdk` |
| Java | [`veille-java`](https://github.com/veilleio/veille-java) | build from source, see its README |
| CLI | [`@veilleio/cli`](https://www.npmjs.com/package/@veilleio/cli) | `npm install -g @veilleio/cli` |
| MCP server | [`@veilleio/mcp`](https://www.npmjs.com/package/@veilleio/mcp) | `npx -y @veilleio/mcp` |

## License

MIT — see [LICENSE](LICENSE).
