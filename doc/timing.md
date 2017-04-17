# Timing Documentation

## dana.TransactionTable inFirst, inLast, etc.

These signals serve different purposes, but are grouped by their sensitivity:

### "Early Signals" -- PE Request Sensitive

This signal changes state as soon as the last processing element in a layer is allocated. This signal is used by the state machines responsible for getting the next layer information into the Transaction Table. These actions can occur as soon as the last PE is allocated, hence, the need for this signal.

* `inLastEarly`

### "Late Signals" -- Layer Response/Done Sensitive

These signals change state only when the Register File (Scratchpad Memory) responds that it has all the information needed for a specific neural network layer. These signals cannot be used by any of the Cache sate machines that can operate in the interim. These signals can be used by any PE logic.

* `inFirst`
* `inLast`
