// See LICENSE.IBM for license details.

package xfiles

import chisel3._
import scala.collection.mutable.LinkedHashSet

class AssemblyTests(rocc: String, testDir: String,
  names: LinkedHashSet[String])(envName: String) extends
    rocketchip.AssemblyTestSuite(rocc + "-" + testDir, names)(envName) {
  override val dir = s"$$(base_dir)/$rocc/tests/build/$testDir"
}

class RegressionTests(rocc: String, testDir: String,
  names: LinkedHashSet[String]) extends rocketchip.RegressionTestSuite(names) {
  override val dir = s"$$(base_dir)/$rocc/tests/build/$testDir"
}

object XFilesDanaTestSuites {
  val smoke = LinkedHashSet (
    "debug",
    "id"
  )

  val nets = LinkedHashSet (
    "xorSigmoidSymmetric",
    "xorSigmoidSymmetric-smp"
  )

  val xfilesDanaRegrTestNames = LinkedHashSet (
    "xfiles-dana-smoke-p-debug",
    "xfiles-dana-nets-p-xorSigmoidSymmetric",
    "xfiles-dana-nets-p-xorSigmoidSymmetric-smp"
  )

  val xfilesDanaSmoke = new AssemblyTests("xfiles-dana", "smoke", smoke)(_)
  val xfilesDanaNets = new AssemblyTests("xfiles-dana", "nets", nets)(_)
  val xfilesDanaRegressions = new RegressionTests("xfiles-dana", "all_tests",
    xfilesDanaRegrTestNames)
}
