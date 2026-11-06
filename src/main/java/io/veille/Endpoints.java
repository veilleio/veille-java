package io.veille;

import java.util.List;
import java.util.Map;

/**
 * Path, methods, response kind and parameter names of every documented operation.
 *
 * <p>Generated from the Veille OpenAPI document. Do not edit by hand.
 */
public final class Endpoints {
  private Endpoints() {}

  /** One documented operation. */
  public record Endpoint(String path, List<String> methods, boolean json, List<String> required) {}

  /** Every documented operation keyed by its snake_case name. */
  public static final Map<String, Endpoint> ALL = Map.ofEntries(
      Map.entry("intelligence_domain", new Endpoint("/v1/intelligence/domain", List.of("GET"), true, List.of("query"))),
      Map.entry("intelligence_email", new Endpoint("/v1/intelligence/email", List.of("GET"), true, List.of("query"))),
      Map.entry("intelligence_emailq", new Endpoint("/v1/intelligence/emailq", List.of("GET"), true, List.of("query"))),
      Map.entry("intelligence_ip", new Endpoint("/v1/intelligence/ip", List.of("GET"), true, List.of("query"))),
      Map.entry("intelligence_phone", new Endpoint("/v1/intelligence/phone", List.of("GET"), true, List.of("query"))),
      Map.entry("jobs", new Endpoint("/v1/jobs/{id}", List.of("GET"), true, List.of("id"))),
      Map.entry("stats", new Endpoint("/v1/stats", List.of("GET"), true, List.of())),
      Map.entry("usage", new Endpoint("/v1/usage", List.of("GET"), true, List.of())),
      Map.entry("vat", new Endpoint("/v1/vat", List.of("GET"), true, List.of("query"))),
      Map.entry("vat_iban", new Endpoint("/v1/vat/iban", List.of("GET"), true, List.of("query"))),
      Map.entry("vat_rates", new Endpoint("/v1/vat/rates", List.of("GET"), true, List.of()))
  );
}
