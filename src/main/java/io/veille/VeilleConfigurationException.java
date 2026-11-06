package io.veille;

/** The client configuration is missing or unusable. */
public final class VeilleConfigurationException extends VeilleException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public VeilleConfigurationException(String message) {
    super(message, null);
  }

  /** Creates the exception with a cause. */
  public VeilleConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
