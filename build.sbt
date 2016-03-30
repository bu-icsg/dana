lazy val commonSettings = Seq(
  organization := "berkeley.edu",
  version := "0.1.0",
  scalaVersion := "2.11.7"
)

val chisel = "edu.berkeley.cs" %% "chisel" % "latest.release"

lazy val dirRC      = sys.env.getOrElse("DIR_ROCKETCHIP", "..")
// lazy val chisel     = project in file(sys.env.getOrElse("CHISEL_SUBMODULE", "chisel2"))
// lazy val cde        = project in file(dirRC + "context-dependent-environments")
// lazy val hardfloat  = (project in file(dirRC + "hardfloat")).dependsOn(chisel)
// lazy val junctions  = (project in file(dirRC + "junctions")).dependsOn(chisel, cde)
// lazy val uncore     = (project in file(dirRC + "uncore")).dependsOn(junctions)
// lazy val rocket     = (project in file(dirRC + "rocket")).dependsOn(hardfloat, uncore)
// lazy val zscale     = (project in file(dirRC + "zscale")).dependsOn(rocket)
// lazy val groundtest = (project in file(dirRC + "groundtest")).dependsOn(rocket)

// // lazy val rocketChip = RootProject(file(dirRC))
// lazy val rocketchip = (project in file(dirRC)).dependsOn(zscale, groundtest)

// lazy val rocketChip = ProjectRef(file(dirRC), "rocketchip")
// lazy val rocket = ProjectRef(file(dirRC + "/rocket"), "rocket")
// lazy val chisel = ProjectRef(file(dirRC + "/chisel"), "chisel")
// lazy val junctions = ProjectRef(file(dirRC + "/junctions"), "junctions")


lazy val rocketChipSources = settingKey[Seq[String]]("list of all the other needed sources")
import java.io.File
val sourceSettings = Seq(
  // rocketChipSources := Seq("chisel, cde, hardfloat, junctions, uncore, rocket"),
  // unmanagedSourceDirectories in Compile ++= rocketChipSources.value.map(
  //   dirRC / _ / "src/main/scala")
  // unmanagedSourceDirectories in Compile ++= rocketChipSources.value.map(
  //   baseDirectory.value / ".." / _ / "src/main/scala")
  // unmanagedSourceDirectories ++=  / "chisel" / "src/main/scala"
  // unmanagedSourceDirectories in Compile ++= Seq(dirRC.value + "src/main/scala")
  // unmanagedSourceDirectories in Compile += baseDirectory.value / "../chisel",
  unmanagedSourceDirectories in Compile += baseDirectory.value / "../rocket/src/main/scala",
  unmanagedSourceDirectories in Compile += baseDirectory.value / "../uncore/src/main/scala",
  unmanagedSourceDirectories in Compile += baseDirectory.value / "../junctions/src/main/scala",
  unmanagedSourceDirectories in Compile += baseDirectory.value / "../context-dependent-environments/src/main/scala",
  unmanagedSourceDirectories in Compile += baseDirectory.value / "../hardfloat/src/main/scala",
  // unmanagedSourceDirectories in Compile += baseDirectory.value / "../zscale/src/main/scala",
  // unmanagedSourceDirectories in Compile += baseDirectory.value / "../groundtest/src/main/scala",
  // unmanagedSourceDirectories in Compile += baseDirectory.value / "../src/main/scala",
  mainClass in Compile := Some("xfiles.Test")
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(sourceSettings: _*).
  settings(
    libraryDependencies += chisel,
    name := "xfiles-dana"
  )
