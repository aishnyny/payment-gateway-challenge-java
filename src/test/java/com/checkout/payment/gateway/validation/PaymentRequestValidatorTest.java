package com.checkout.payment.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentRequestValidatorTest {

  private final PaymentRequestValidator validator = new PaymentRequestValidator();

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    YearMonth futureExpiry = YearMonth.now().plusYears(1);
    request.setExpiryMonth(futureExpiry.getMonthValue());
    request.setExpiryYear(futureExpiry.getYear());
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Test
  void validRequestProducesNoErrors() {
    List<String> errors = validator.validate(validRequest());

    assertThat(errors).isEmpty();
  }

  @Test
  void missingCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(null);

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("cardNumber is required");
  }

  @Test
  void nonNumericCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("2222abcd43248877");

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("cardNumber must contain only numeric characters");
  }

  @Test
  void tooShortCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("123456789");

    List<String> errors = validator.validate(request);

    assertThat(errors).anyMatch(e -> e.contains("14") && e.contains("19"));
  }

  @Test
  void expiryMonthOutOfRangeIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(13);

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("expiryMonth is required and must be between 1 and 12");
  }

  @Test
  void expiryInThePastIsRejected() {
    PostPaymentRequest request = validRequest();
    YearMonth pastExpiry = YearMonth.now().minusMonths(1);
    request.setExpiryMonth(pastExpiry.getMonthValue());
    request.setExpiryYear(pastExpiry.getYear());

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("expiryMonth and expiryYear combination must be in the future");
  }

  @Test
  void currentMonthExpiryIsRejected() {
    // Edge case worth testing explicitly: a card expiring "this month" should
    // not be treated as valid - it must be strictly after the current month.
    PostPaymentRequest request = validRequest();
    YearMonth thisMonth = YearMonth.now();
    request.setExpiryMonth(thisMonth.getMonthValue());
    request.setExpiryYear(thisMonth.getYear());

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("expiryMonth and expiryYear combination must be in the future");
  }

  @Test
  void unsupportedCurrencyIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("JPY");

    List<String> errors = validator.validate(request);

    assertThat(errors).anyMatch(e -> e.contains("currency must be one of"));
  }

  @Test
  void wrongLengthCurrencyIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("GB");

    List<String> errors = validator.validate(request);

    assertThat(errors).anyMatch(e -> e.contains("currency must be exactly 3 characters"));
  }

  @Test
  void zeroAmountIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setAmount(0);

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("amount must be a positive number, greater than 0");
  }

  @Test
  void negativeAmountIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setAmount(-50);

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("amount must be a positive number, greater than 0");
  }

  @Test
  void shortCvvIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12");

    List<String> errors = validator.validate(request);

    assertThat(errors).contains("cvv must be 3-4 characters long");
  }

  @Test
  void fourDigitCvvIsAccepted() {
    PostPaymentRequest request = validRequest();
    request.setCvv("1234");

    List<String> errors = validator.validate(request);

    assertThat(errors).isEmpty();
  }

  @Test
  void multipleInvalidFieldsAreAllReported() {
    // Confirms the validator collects every error in one pass rather than
    // stopping at the first failure - important for merchant usability.
    PostPaymentRequest request = validRequest();
    request.setCardNumber(null);
    request.setCurrency("JPY");
    request.setAmount(-1);

    List<String> errors = validator.validate(request);

    assertThat(errors).hasSize(3);
  }
}