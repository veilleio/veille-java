package io.veille.params;

import io.veille.VeilleParams;
import io.veille.VeilleSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET /v1/vat/rates}.
 *
 * <p>Generated from the Veille OpenAPI document. Do not edit by hand.
 */
public final class VatRatesParams implements VeilleParams {
  private final Map<String, Object> values;

  private VatRatesParams(Map<String, Object> values) {
    this.values = values;
  }

  /** Returns a new builder. */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Map<String, Object> toMap() {
    return values;
  }

  /** Builder for {@link VatRatesParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Base currency ISO 4217 code (defaults to EUR). Default: "EUR". */
    public Builder base(String value) {
      values.put("base", value);
      return this;
    }

    /** Comma-separated target currency codes to filter (e.g. USD,GBP,JPY). */
    public Builder symbols(String value) {
      values.put("symbols", value);
      return this;
    }

    /** Comma-separated list of fields to keep in the response, with dot notation for nested fields (e.g. name,location.city). Does not change the credit cost. */
    public Builder fields(String value) {
      values.put("fields", value);
      return this;
    }

    /** Process the request asynchronously: the response is a job_id to poll on /v1/jobs/{id} (see waitForJob). Default: false. */
    public Builder isAsync(Boolean value) {
      values.put("is_async", value);
      return this;
    }

    /** Return HTTP 200 even when an error occurs; the error is then in the response body. Default: false. */
    public Builder softFail(Boolean value) {
      values.put("soft_fail", value);
      return this;
    }

    /** Validates required parameters and returns the immutable {@link VatRatesParams}. */
    public VatRatesParams build() {
      return new VatRatesParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
