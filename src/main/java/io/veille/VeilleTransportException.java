package io.veille;

/** The request failed before a complete HTTP response was received (network error, timeout). */
public final class VeilleTransportException extends VeilleException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public VeilleTransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
