package com.checkout.payment.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class PostPaymentRequestTest {

  @Test
  void toStringNeverThrowsEvenWithAnInvalidCardNumber() {
    // Regression test: toString() is called during logging before
    // validation has run, so it must be safe on bad input - this used to
    // throw for a too-short card number, which silently broke logging for
    // exactly the requests we most need visibility into.
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("123");
    request.setCvv("123");
    request.setCurrency("GBP");
    request.setAmount(100);

    assertThatCode(request::toString).doesNotThrowAnyException();
  }

  @Test
  void toStringDoesNotLeakFullCardNumberOrCvv() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setCvv("999");
    request.setCurrency("GBP");
    request.setAmount(100);

    String result = request.toString();

    assertThat(result).doesNotContain("2222405343248877");
    assertThat(result).doesNotContain("999");
    assertThat(result).contains("8877"); // last four only
  }

  @Test
  void getLastFourCardDigitsThrowsOnInvalidCardNumber() {
    // Distinct from toString(): this method is only ever meant to be called
    // after validation has confirmed the card number is valid, so it should
    // fail loudly if that assumption is ever violated.
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("12");

    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        request::getLastFourCardDigits);
  }
}