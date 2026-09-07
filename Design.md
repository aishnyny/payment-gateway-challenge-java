# Design Notes

Here's a rundown of the key decisions and assumptions I made while building this.

## Architecture

Pretty standard layered setup:

- **Controller** (`PaymentGatewayController`)
  Kept this thin. It just handles HTTP and hands off to the service.

- **Service** (`PaymentGatewayService`)
  This is where the actual orchestration happens: validate, call the bank, persist.

- **Validator** (`PaymentRequestValidator`)
  Pulled all the field validation into its own class.
  I could then test it in isolation without needing to spin up any Spring context.

- **Bank Client** (`BankClient`)
  The only piece of the system that actually talks to the bank simulator.
  Everything about the bank response gets translated here.

- **Repository**
  Used the in-memory one that was already provided.

I split it this way so each piece does one job.
Each one can be tested on its own, without adding more layers than the problem actually needs.

## Assumptions

A few things in the brief weren't fully spelled out.
Here's what I decided, and why:

- **Which 3 currencies to support.**
  The brief says "validate against no more than 3 currency codes" but doesn't say which.
  I went with **GBP, USD, EUR**.

- **Amount has to be positive.**
  It says amount is required and must be an integer, but doesn't explicitly say positive.
  A payment of £0 or a negative amount doesn't make sense, so I added that check myself.

- **Expiry has to be strictly in the future.**
  A card expiring this month should count as expired, not valid.
  So it has to be after the current month, not just "this month or later."

## Key decisions

**I never store or log the full card number or CVV.**
They're only needed for the split second it takes to call the bank.
I made sure `PostPaymentRequest.toString()` leaves both out.
Only the last four digits of the card ever get stored or sent back to the merchant.
This matters because request objects tend to get logged automatically when something goes wrong.
A full card number ending up in a log file would be a real compliance problem, not just a theoretical one.

**Rejected payments don't get saved.**
The brief says that for a Rejected outcome, "no payment could be created."
So unlike Authorized or Declined, I don't persist these.
The response still comes back with a generated id, so the shape stays consistent.
But if you try to GET that id afterwards, you'll correctly get a 404, since there's genuinely nothing stored for it.

**A bank being unavailable is not the same thing as a decline.**
The simulator returns a 503 for any card number ending in 0.
I didn't want to lump that in with Declined.
A decline is a real outcome - the bank looked at it and said no.
"The bank didn't respond" means we don't actually know what happened, which is different.
I built a separate `BankUnavailableException` for this.
It comes back to the caller as its own 503, so it's obvious these are two different situations, not the same thing.

**Validation reports everything wrong at once, not just the first issue.**
`PaymentRequestValidator.validate()` checks every field and returns all the failures together.
It doesn't stop at the first one.
Felt more useful for whoever's calling this - nobody wants to fix one field, resubmit, and immediately hit the next error.

**I reused the request object to call the bank.**
`BankClient` just sends the `PostPaymentRequest` straight to the simulator, since the fields happen to match up.
It's a bit of a shortcut.
In a bigger system I'd probably want a separate model for what gets sent to the bank.
That way a change on either side doesn't automatically force a change on the other.
For this exercise though, it didn't feel worth the extra layer.

**Some things fail loudly on purpose.**
For example: pulling the last four digits off a card number should only happen after validation has confirmed it's valid.
If that's ever not true, it throws an exception rather than quietly returning something wrong.
That situation should never actually happen, so if it does, I want to know about it immediately.
For the Rejected path specifically, the card number itself might be *why* it got rejected.
So I used a separate, safer version there that just returns a fallback instead of throwing, since that's an expected outcome, not a bug.

## What I didn't do, on purpose

**HTTPS.**
This runs over plain HTTP, same as the bank simulator itself.
In an actual production setup, TLS would normally get handled at the infrastructure level (load balancer/API gateway) rather than configured inside the app.
It wouldn't be optional there.
Setting it up here would've added a fair bit of config for something that doesn't really change what's being tested, especially given the instructions specifically said not to over-engineer this.

**Real persistence.**
Stuck with the in-memory repo that was already given rather than wiring up an actual database.

**Splitting the bank request into its own model.**
Mentioned above - a known shortcut, not something I just didn't think about.

## Testing

- `PaymentRequestValidatorTest` covers every field, valid and invalid.
  Plus a couple of edge cases I thought were worth calling out specifically - like a card expiring in the current month, and making sure multiple bad fields all get reported together rather than just the first one.
- I also ran this end to end manually against the actual simulator.
  Checked all four outcomes (Authorized, Declined, Rejected, bank unavailable) and both endpoints, not just trusting the unit tests on their own.

## A bug I found while testing, worth mentioning

While running the full suite, all tests passed, but the logs showed a `toString()` failure on `PostPaymentRequest` for the invalid-card-number test case.
It turned out my request-logging line runs *before* validation, and `toString()` was calling a version of the last-four-digits method that deliberately throws on bad input.
So logging a request would crash exactly when the card number was invalid - which is exactly when good logging matters most.
I fixed `toString()` to use a safe, non-throwing extraction instead, and added a regression test for it (`PostPaymentRequestTest`).
Worth noting: the test suite going green didn't catch this on its own - it only showed up because I actually read the log output, not just the pass/fail summary.