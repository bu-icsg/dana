// See LICENSE for license details.

package rocketchip

import chisel3._
import uncore.agents.{CacheName}
import rocket._
import xfiles.{XFiles, XFilesDebugConfig, DefaultXFilesConfig}
import dana.{DefaultDanaConfig, DanaNoLearningConfig, DanaConfig}
import cde.{Parameters, Config}

class XFilesDanaConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case BuildRoCC => Seq(
        RoccParameters(
          opcodes = OpcodeSet.custom0,
          generator = (p: Parameters) =>  Module(new XFiles()(p)),
          nPTWPorts = 1)
      )
      case CacheName => "L1D"
      case RoccMaxTaggedMemXacts => 1
    }}
)

// A default configuraiton that includes both X-FILES and DANa with
// default values
class DefaultXFilesDanaConfig extends Config(new DefaultXFilesConfig ++
  new DefaultDanaConfig)

class WithL2CapacityKb(kb: Int) extends Config(
  knobValues = { case "L2_CAPACITY_IN_KB" => 64 })

// VLSI Configs -- no L2
class XFilesDanaVLSIConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesConfig ++ new DanaConfig(4, 4, 2, 10240, true) ++
  new DefaultDanaConfig ++ new DefaultVLSIConfig)
class XFilesDanaNoLearningVLSIConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesConfig ++ new DanaConfig(4, 4, 2, 10240, false) ++
  new DefaultDanaConfig ++ new DefaultVLSIConfig)
// VLSI Configs -- with L2
class XFilesDanaL2VLSIConfig extends Config(new WithL2CapacityKb(1024) ++
  new WithL2Cache ++ new XFilesDanaVLSIConfig)
class XFilesDanaNoLearningL2VLSIConfig extends Config(
  new WithL2CapacityKb(1024) ++ new WithL2Cache ++
    new XFilesDanaNoLearningVLSIConfig)
// VLSI Configs -- smaller scratchpad
class XFilesDanaNoLearningSmallScratchpadVLSIConfig extends Config(
  new XFilesDanaConfig ++ new DefaultXFilesConfig ++
    new DanaConfig(4, 4, 2, 1024, false) ++ new DefaultDanaConfig ++
    new DefaultVLSIConfig)

// CPP Configs (these are the same as VLSI Configs)
class XFilesDanaCPPConfig extends Config(new XFilesDebugConfig ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultConfig)

class XFilesDanaNoLearningCPPConfig extends Config(
  new DanaNoLearningConfig ++ new XFilesDanaCPPConfig)

class XFilesDanaFPGAConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaConfig ++ new DefaultFPGAConfig)

class XFilesDanaNoLearningFPGAConfig extends Config(new XFilesDanaConfig ++
  new DanaNoLearningConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaConfig ++ new DefaultFPGASmallConfig)

class XFilesDanaPe1Epb4Config extends Config(new DanaConfig(1, 4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe2Epb4Config extends Config(new DanaConfig(2, 4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe3Epb4Config extends Config(new DanaConfig(3, 4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe4Epb4Config extends Config(new DanaConfig(4, 4) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe1Epb8Config extends Config(new DanaConfig(1, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe2Epb8Config extends Config(new DanaConfig(2, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe3Epb8Config extends Config(new DanaConfig(3, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe4Epb8Config extends Config(new DanaConfig(4, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe5Epb8Config extends Config(new DanaConfig(5, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe6Epb8Config extends Config(new DanaConfig(6, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe7Epb8Config extends Config(new DanaConfig(7, 8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe8Epb8Config extends Config(new DanaConfig(8, 8) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe16Epb16Config extends Config(new DanaConfig(16, 16) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe32Epb32Config extends Config(new DanaConfig(32, 32) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaCppPe1Epb4Config extends Config(new DanaConfig(1, 4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb4Config extends Config(new DanaConfig(2, 4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb4Config extends Config(new DanaConfig(3, 4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb4Config extends Config(new DanaConfig(4, 4) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb8Config extends Config(new DanaConfig(1, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb8Config extends Config(new DanaConfig(2, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb8Config extends Config(new DanaConfig(3, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb8Config extends Config(new DanaConfig(4, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe5Epb8Config extends Config(new DanaConfig(5, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe6Epb8Config extends Config(new DanaConfig(6, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe7Epb8Config extends Config(new DanaConfig(7, 8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb8Config extends Config(new DanaConfig(8, 8) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb16Config extends Config(new DanaConfig(1, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb16Config extends Config(new DanaConfig(2, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb16Config extends Config(new DanaConfig(3, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb16Config extends Config(new DanaConfig(4, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe5Epb16Config extends Config(new DanaConfig(5, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe6Epb16Config extends Config(new DanaConfig(6, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe7Epb16Config extends Config(new DanaConfig(7, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb16Config extends Config(new DanaConfig(8, 16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe16Epb16Config extends Config(new DanaConfig(8, 16) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb32Config extends Config(new DanaConfig(1, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb32Config extends Config(new DanaConfig(4, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb32Config extends Config(new DanaConfig(8, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe16Epb32Config extends Config(new DanaConfig(16, 32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe32Epb32Config extends Config(new DanaConfig(32, 32) ++
  new XFilesDanaCPPConfig)
