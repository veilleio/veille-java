package io.veille.params;

import io.veille.VeilleParams;
import io.veille.VeilleSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET /v1/vat/iban}.
 *
 * <p>Generated from the Veille OpenAPI document. Do not edit by hand.
 */
public final class VatIbanParams implements VeilleParams {
  private final Map<String, Object> values;

  private VatIbanParams(Map<String, Object> values) {
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

  /** Builder for {@link VatIbanParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The IBAN to validate (e.g. FR7630006000011234567890189). */
    public Builder query(String value) {
      values.put("query", value);
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

    /** Validates required parameters and returns the immutable {@link VatIbanParams}. */
    public VatIbanParams build() {
      if (values.get("query") == null) {
        throw new VeilleSerializationException("The \"query\" parameter is required.", "query");
      }
      return new VatIbanParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
