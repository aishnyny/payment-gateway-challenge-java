package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class PostPaymentRequest implements Serializable {

  // Full card number is required here only so we can forward it to the
  // acquiring bank. It must never be persisted or logged beyond this point -
  // see toString(), which deliberately omits it and the CVV.
  @JsonProperty("card_number")
  private String cardNumber;
  @JsonProperty("expiry_month")
  private int expiryMonth;
  @JsonProperty("expiry_year")
  private int expiryYear;
  private String currency;
  private int amount;
  // String, not int: CVVs can have leading zeros (e.g. "012"), which an int
  // would silently drop.
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  // Sent to the bank simulator, which expects a single "MM/YYYY" string
  // rather than separate month/year fields.
  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  // Should only ever be called after validation has confirmed cardNumber is
  // 14-19 numeric digits. If that's not true, something upstream has a bug -
  // better to fail loudly here than silently store or return a truncated
  // or missing value.
  public String getLastFourCardDigits() {
    if (cardNumber == null || cardNumber.length() < 4) {
      throw new IllegalStateException(
          "Cannot extract last four digits: card number is missing or invalid. "
              + "This should have been caught by validation before reaching this point.");
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }

  // Deliberately excludes cardNumber and cvv - this object gets logged
  // during request handling, and full card numbers must never end up in logs.
  //
  // Uses a safe, non-throwing digit extraction here rather than
  // getLastFourCardDigits(): this method can be called via logging before
  // validation has run, so it must never throw on a bad/short card number -
  // that would break the very log line meant to give visibility into the
  // request, which matters most for exactly the invalid ones.
  @Override
  public String toString() {
    String lastFour = (cardNumber != null && cardNumber.length() >= 4)
        ? cardNumber.substring(cardNumber.length() - 4)
        : "invalid";
    return "PostPaymentRequest{" +
        "cardNumberLastFour=" + lastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}