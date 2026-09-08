package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public class PostPaymentResponse {
  private UUID id;
  private PaymentStatus status;
  private int cardNumberLastFour;
  private int expiryMonth;
  private int expiryYear;
  private String currency;
  private int amount;
  // Only ever populated when status is REJECTED. Deliberately not used for
  // DECLINED - see DESIGN.md: rejection reasons describe our own input
  // format rules and are safe to expose, but detailed decline reasons from
  // the bank are a known card-testing attack vector and are not surfaced.
  // @JsonInclude ensures this field is omitted from the JSON entirely when
  // null, rather than showing up as "rejectionReasons": null on every
  // Authorized/Declined response.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> rejectionReasons;


  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public int getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(int cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
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

  public List<String> getRejectionReasons() {
    return rejectionReasons;
  }

  public void setRejectionReasons(List<String> rejectionReasons) {
    this.rejectionReasons = rejectionReasons;
  }

  @Override
  public String toString() {
    return "PostPaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", rejectionReasons=" + rejectionReasons +
        '}';
  }
}