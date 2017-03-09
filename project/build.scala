// See LICENSE.IBM for license details.

import sbt._
import Keys._
import complete._
import complete.DefaultParsers._

object BuildSettings extends Build {

  override lazy val settings = super.settings ++ Seq(
    organization := "ibm",
    version      := "0.1",
    scalaVersion := "2.11.7",
    parallelExecution in Global := false,
    traceLevel   := 15,
    scalacOptions ++= Seq("-deprecation","-unchecked"),
    scalacOptions ++= Seq("-Xmax-classfile-name","128"),
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.12.4" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test")
  )

  // lazy val chisel = project in file("../chisel3")
  // lazy val firrtl = project in file("../firrtl")
  // lazy val chisel = RootProject(file("../chisel3"))
  lazy val chisel = RootProject(file("../chisel3"))
  lazy val firrtl = RootProject(file("../firrtl"))
  // lazy val cde = RootProject(file("../context-dependent-environments"))
  lazy val rocketChip = RootProject(file(".."))

  // lazy val cde    = project in file("submodules/context-dependent-environments")
  lazy val xfiles_dana = (project in file(".")).
    settings().
    dependsOn(rocketChip, chisel, firrtl)
}
