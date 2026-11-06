package io.veille;

import com.fasterxml.jackson.databind.JsonNode;

/** Veille returned a non-success HTTP status. */
public final class VeilleApiException extends VeilleException {
  private static final long serialVersionUID = 1L;
  private final transient RawResponse rawResponse;
  private final transient JsonNode responseBody;
  private final String errorMessage;

  /** Creates the exception. */
  public VeilleApiException(RawResponse rawResponse, JsonNode responseBody, String errorMessage) {
    super(errorMessage != null ? errorMessage : "Veille API request failed with status " + rawResponse.status() + ".", null);
    this.rawResponse = rawResponse;
    this.responseBody = responseBody;
    this.errorMessage = errorMessage;
  }

  /** HTTP status code. */
  public int status() {
    return rawResponse.status();
  }

  /** The "error" (or "message") field of the JSON body, or null. */
  public String errorMessage() {
    return errorMessage;
  }

  /** The parsed JSON body, or null when the body is not JSON. */
  public JsonNode responseBody() {
    return responseBody;
  }

  /** The buffered response. */
  public RawResponse rawResponse() {
    return rawResponse;
  }
}
