package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestValidator {

  // Assumption: the README says "ensure your submission validates against
  // no more than 3 currency codes" - interpreted as: support exactly these
  // three, reject anything else.
  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("GBP", "USD", "EUR");

  private static final int MIN_CARD_NUMBER_LENGTH = 14;
  private static final int MAX_CARD_NUMBER_LENGTH = 19;

  /**
   * Returns a list of validation error messages. An empty list means the
   * request is valid. All fields are checked, not just the first failure,
   * so a merchant can fix everything in one pass rather than resubmitting
   * repeatedly.
   */
  public List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    validateCardNumber(request.getCardNumber(), errors);
    validateExpiry(request.getExpiryMonth(), request.getExpiryYear(), errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);

    return errors;
  }

  private void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      errors.add("cardNumber is required");
      return;
    }
    if (!cardNumber.matches("\\d+")) {
      errors.add("cardNumber must contain only numeric characters");
    }
    if (cardNumber.length() < MIN_CARD_NUMBER_LENGTH || cardNumber.length() > MAX_CARD_NUMBER_LENGTH) {
      errors.add("cardNumber must be between " + MIN_CARD_NUMBER_LENGTH
          + " and " + MAX_CARD_NUMBER_LENGTH + " characters long");
    }
  }

  private void validateExpiry(int expiryMonth, int expiryYear, List<String> errors) {
    // expiryMonth and expiryYear are primitive int, not Integer, so a
    // missing field from the merchant's JSON deserializes to 0 rather than
    // null - there's no way to distinguish "not provided" from "provided
    // as zero" here. That's why these floor checks double as both the
    // "required" check and the "valid range" check at once.
    if (expiryMonth < 1 || expiryMonth > 12) {
      errors.add("expiryMonth is required and must be between 1 and 12");
      // Don't attempt the future-date check against a month we know is invalid.
      return;
    }
    if (expiryYear < 1) {
      errors.add("expiryYear is required");
      return;
    }
    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
    if (!expiry.isAfter(YearMonth.now())) {
      errors.add("expiryMonth and expiryYear combination must be in the future");
    }
  }

  private void validateCurrency(String currency, List<String> errors) {
    if (currency == null || currency.isEmpty()) {
      errors.add("currency is required and must be one of " + SUPPORTED_CURRENCIES);
      return;
    }
    if (currency.length() != 3) {
      errors.add("currency must be exactly 3 characters and one of " + SUPPORTED_CURRENCIES);
      return;
    }
    if (!SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
      errors.add("currency must be one of " + SUPPORTED_CURRENCIES);
    }
  }

  private void validateAmount(int amount, List<String> errors) {
    // Assumption: "required" plus representing a monetary amount implies
    // it must be a positive value - a zero or negative payment doesn't
    // make sense, even though the README doesn't say this explicitly.
    if (amount <= 0) {
      errors.add("amount must be a positive integer");
    }
  }

  private void validateCvv(String cvv, List<String> errors) {
    if (cvv == null || cvv.isEmpty()) {
      errors.add("cvv is required");
      return;
    }
    if (!cvv.matches("\\d+")) {
      errors.add("cvv must contain only numeric characters");
    }
    if (cvv.length() < 3 || cvv.length() > 4) {
      errors.add("cvv must be 3-4 characters long");
    }
  }
}