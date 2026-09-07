package com.checkout.payment.gateway.exception;

/**
 * Thrown when the acquiring bank cannot be reached or returns an
 * unexpected error (e.g. the 503 the simulator returns for card numbers
 * ending in 0). This is deliberately distinct from a declined payment:
 * a decline is a definite business outcome ("the bank said no"), while
 * this represents "we don't know the outcome because the bank didn't
 * respond usably" - the two should never be confused with each other.
 */
public class BankUnavailableException extends RuntimeException {

  public BankUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}