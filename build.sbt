// Common settings that could be shared between projects as based on
// the sbt quickstart
lazy val commonSettings = Seq(
  organization := "bu.edu",
  version := "0.1.0",
  scalaVersion := "2.11.6",
  // scalaVersion := "2.10.4",
  // Usually you don't care about the trace (as it provides no useful
  // information), unless sbt, scala, or java shit the bed. This can
  // be upped to a high number (e.g., 100) to get the full trace.
  traceLevel := 0,
  // Change the src directory to ./src
  scalaSource in Compile := baseDirectory.value / "src"
)

// Based on 'chisel-tutorial/examples/chisel-dependent.sbt'. If a
// command line 'chiselVersion' is defined, then that will be used.
// Otherwise, this will default to the latest.release version of
// Chisel.
// val chiselVersion = System.getProperty("chiselVersion", "latest.release")
// val chiselVersion = System.getProperty("chiselVersion", "2.3-SNAPSHOT")

val chisel = "edu.berkeley.cs" %% "chisel" % "latest.release"
// val chisel = "edu.berkeley.cs" %% "chisel" % "2.3-SNAPSHOT"

// Set the actual settings as defined above
lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "dana",
    libraryDependencies += chisel,
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:reflectiveCalls")
  )
