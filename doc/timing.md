# Timing Documentation

# inFirst, inLast, etc.
These govern the state of the transaction as it relates to being in the first or last layer. This is counterintuitive (and potentially dangerous or wrong), but an understanding of when these are actually set is helpful:
* `inFirst` -- Asserts when a new transaction is received, deasserts when the first PE assignment of the next layer goes out
* `inLastEarly` -- Asserts as soon as the last PE assignment in the second to last layer goes out
* `inLast` -- Asserts as soon as all PEs in the second to last layer are finished

For a two-layer network, the following timing is possible (and likely):
```
 Transaction shows up, inFirst asserts
    | Last PE assigned in first layer, inLastEarly asserts
    |    | All PEs write data, inLast asserts
    |    |    | First PE in last layer assigned, inFirst deasserts
    |    |    |    |
    v    v    v    v
----*----*----*----*
```
