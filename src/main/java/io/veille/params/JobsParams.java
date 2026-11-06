package io.veille.params;

import io.veille.VeilleParams;
import io.veille.VeilleSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET /v1/jobs/{id}}.
 *
 * <p>Generated from the Veille OpenAPI document. Do not edit by hand.
 */
public final class JobsParams implements VeilleParams {
  private final Map<String, Object> values;

  private JobsParams(Map<String, Object> values) {
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

  /** Builder for {@link JobsParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The job_id returned by an endpoint called with is_async=true. */
    public Builder id(String value) {
      values.put("id", value);
      return this;
    }

    /** Validates required parameters and returns the immutable {@link JobsParams}. */
    public JobsParams build() {
      if (values.get("id") == null) {
        throw new VeilleSerializationException("The \"id\" parameter is required.", "id");
      }
      return new JobsParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
