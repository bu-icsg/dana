# Regression Tests
A set of canonical tests to make sure that we're not horribly breaking things.

## Structure and Organization
Each encapsulated test which goes in its own directory in "tests". The directory name for the test can technically be named whatever you want, but the top-level Makefile will only run tests of format "t-*". While test directories can be named "t-*", it's better to name them something else and then add symbolic links so that they can be turned on and off easily. Each test is expected to have a Makefile that will respond to a `make`. The Makefile should error out on any failures in subsequent tests.
