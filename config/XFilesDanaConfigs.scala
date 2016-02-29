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

class XFilesDanaCPPConfig extends Config(new XFilesDanaDebugConfig ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultCPPConfig)

class XFilesDanaNoLearningCPPConfig extends Config(
  new XFilesDanaNoLearningConfig ++ new XFilesDanaCPPConfig)

class XFilesDanaFPGAConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGAConfig)

class XFilesDanaNoLearningFPGAConfig extends Config(new XFilesDanaConfig ++
  new XFilesDanaNoLearningConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGASmallConfig)

// Variants that use explicit numbers of PEs
class Dana(numPes: Int, epb: Int) extends Config(
  knobValues = {
    case "NUM_PES" => numPes
    case "ELEMENTS_PER_BLOCK" => epb })

class XFilesDanaPe1Epb4Config extends Config(new Dana(1, 4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe2Epb4Config extends Config(new Dana(2, 4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe3Epb4Config extends Config(new Dana(3, 4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe4Epb4Config extends Config(new Dana(4, 4) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe1Epb8Config extends Config(new Dana(1, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe2Epb8Config extends Config(new Dana(2, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe3Epb8Config extends Config(new Dana(3, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe4Epb8Config extends Config(new Dana(4, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe5Epb8Config extends Config(new Dana(1, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe6Epb8Config extends Config(new Dana(2, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe7Epb8Config extends Config(new Dana(3, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe8Epb8Config extends Config(new Dana(4, 8) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaCppPe1Epb4Config extends Config(new Dana(1, 4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb4Config extends Config(new Dana(2, 4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb4Config extends Config(new Dana(3, 4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb4Config extends Config(new Dana(4, 4) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb8Config extends Config(new Dana(1, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb8Config extends Config(new Dana(2, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb8Config extends Config(new Dana(3, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb8Config extends Config(new Dana(4, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe5Epb8Config extends Config(new Dana(5, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe6Epb8Config extends Config(new Dana(6, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe7Epb8Config extends Config(new Dana(7, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb8Config extends Config(new Dana(8, 8) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb16Config extends Config(new Dana(1, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb16Config extends Config(new Dana(2, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb16Config extends Config(new Dana(3, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb16Config extends Config(new Dana(4, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe5Epb16Config extends Config(new Dana(5, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe6Epb16Config extends Config(new Dana(6, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe7Epb16Config extends Config(new Dana(7, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb16Config extends Config(new Dana(8, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe16Epb16Config extends Config(new Dana(8, 16) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb32Config extends Config(new Dana(1, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb32Config extends Config(new Dana(4, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb32Config extends Config(new Dana(8, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe16Epb32Config extends Config(new Dana(16, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe32Epb32Config extends Config(new Dana(32, 32) ++
  new XFilesDanaCPPConfig)
