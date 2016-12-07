// See LICENSE for license details.

package xfiles

import chisel3._
import scala.collection.mutable.LinkedHashSet

class AssemblyTests(rocc: String, testDir: String,
  names: LinkedHashSet[String])(envName: String) extends
    rocketchip.AssemblyTestSuite(rocc, names)(envName) {
  override val dir = s"$$(base_dir)/$rocc/tests/build/$testDir"
}

class RegressionTests(rocc: String, testDir: String,
  names: LinkedHashSet[String]) extends rocketchip.RegressionTestSuite(names) {
  override val dir = s"$$(base_dir)/$rocc/tests/build/$testDir"
  override val makeTargetName = s"$rocc-regression-tests"
}

object XFilesDanaTestSuites {
  val smoke = LinkedHashSet (
    "debug")

  val xfilesDanaRegrTestNames = LinkedHashSet (
  "xfiles-dana-p-debug")

  val xfilesDanaSmoke = new AssemblyTests("xfiles-dana", "smoke", smoke)(_)
  val xfilesDanaRegressions = new RegressionTests("xfiles-dana", "smoke",
    xfilesDanaRegrTestNames)
}
