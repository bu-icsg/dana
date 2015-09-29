# Regression Tests
A set of canonical tests to make sure that we're not horribly breaking things.

## Structure and Organization
Each encapsulated test which goes in its own directory in "tests" and has a format of "t-*". Each test is expected to have a Makefile that will respond to a `make` and the Makefile should error out if any check run by that Makefile fails.
