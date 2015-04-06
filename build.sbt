// Common settings that could be shared between projects as based on
// the sbt quickstart
lazy val commonSettings = Seq(
  organization := "bu.edu",
  version := "0.1.0",
  scalaVersion := "2.11.6",
  // Change the src directory to ./src
  scalaSource in Compile := baseDirectory.value / "src"
)

// Based on 'chisel-tutorial/examples/chisel-dependent.sbt'. If a
// command line 'chiselVersion' is defined, then that will be used.
// Otherwise, this will default to the latest.release version of
// Chisel.
val chiselVersion = System.getProperty("chiselVersion", "latest.release")
val chisel = "edu.berkeley.cs" %% "chisel" % chiselVersion

// Set the actual settings as defined above
lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "dana",
    libraryDependencies += chisel
  )
