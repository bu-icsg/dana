package rocketchip

import Chisel._
import uncore._
import rocket._
import dana._
import cde.{Parameters, Config}

import Implicits._

class XFilesDanaConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case BuildRoCC => Seq(
        RoccParameters(
          opcodes = OpcodeSet.custom0,
          generator = (p: Parameters) =>  Module(new XFilesDana()(
            p.alterPartial({ case CoreName => "XFilesDana" }))))
      )
      case CacheName => "L1D"
      case RoccMaxTaggedMemXacts => 1
    }}
)

class XFilesDanaNoLearningConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case LearningEnabled => false }}
)

class XFilesDanaCPPConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultCPPConfig)

class XFilesDanaNoLearningCPPConfig extends Config(
  new XFilesDanaNoLearningConfig ++ new XFilesDanaCPPConfig)

class XFilesDanaFPGAConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGAConfig)

class XFilesDanaNoLearningFPGAConfig extends Config(new XFilesDanaConfig ++
  new XFilesDanaNoLearningConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGASmallConfig)

// Variants that use explicit numbers of PEs
class DanaPEX(numPes: Int) extends Config(
  knobValues = { case "NUM_PES" => numPes })

class XFilesDanaPE1Config extends Config(new DanaPEX(1) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPE2Config extends Config(new DanaPEX(2) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPE3Config extends Config(new DanaPEX(3) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPE4Config extends Config(new DanaPEX(4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPE5Config extends Config(new DanaPEX(5) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPE6Config extends Config(new DanaPEX(6) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaCppPe1Config extends Config(new DanaPEX(1) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Config extends Config(new DanaPEX(2) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Config extends Config(new DanaPEX(3) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Config extends Config(new DanaPEX(4) ++
  new XFilesDanaCPPConfig)
