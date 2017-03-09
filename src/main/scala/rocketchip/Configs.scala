// See LICENSE.IBM for license details.
// See LICENSE.BU for license details.

package rocketchip

import chisel3._
import uncore.util.CacheName
import rocket._
import xfiles.{XFiles, DefaultXFilesConfig}
import dana.{DefaultDanaConfig, DanaNoLearningConfig, DanaConfig, PeTableNumEntries, ElementsPerBlock}
import coreplex.WithL2Cache
import config._

class XFilesDanaConfig extends Config (
  (pname,site,here) =>
  pname match {
    case BuildRoCC => Seq(
      RoccParameters(
        opcodes = OpcodeSet.custom0,
        generator = (p: Parameters) =>  Module(new XFiles()(p)),
        nPTWPorts = 1)
    )
    case CacheName             => "L1D"
    case RoccMaxTaggedMemXacts => 1
    case _ => throw new CDEMatchError
  }
)

// A default configuraiton that includes both X-FILES and DANa with
// default values
class DefaultXFilesDanaConfig extends Config(new DefaultXFilesConfig ++
  new DefaultDanaConfig)

class WithL2CapacityKb(kb: Int) extends Config(
  (pname,site,here) => pname match {
    case _ => throw new CDEMatchError
  })

// VLSI Configs -- no L2
class XFilesDanaVLSIConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesConfig ++ new DanaConfig(4, 4, 2, 10240, true) ++
  new DefaultDanaConfig ++ new DefaultConfig)
class XFilesDanaNoLearningVLSIConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesConfig ++ new DanaConfig(4, 4, 2, 10240, false) ++
  new DefaultDanaConfig ++ new DefaultConfig)
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
    new DefaultConfig)

// CPP Configs (these are the same as VLSI Configs)
class XFilesDanaCPPConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaConfig ++ new DefaultConfig)

class XFilesDanaNoLearningCPPConfig extends Config(
  new DanaNoLearningConfig ++ new XFilesDanaCPPConfig)

class XFilesDanaFPGAConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaConfig ++ new DefaultFPGAConfig)

class XFilesDanaNoLearningFPGAConfig extends Config(new XFilesDanaConfig ++
  new DanaNoLearningConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaConfig ++ new DefaultFPGASmallConfig)

// Variants that use explicit numbers of PEs
class Pes(n: Int) extends Config(
  (pname,site,here) => pname match {
    case PeTableNumEntries => n
    case _ => throw new CDEMatchError
  })
class Epb(n: Int) extends Config(
  (pname,site,here) => pname match {
    case ElementsPerBlock => n
    case _ => throw new CDEMatchError
  })

class XFilesDanaPe1Epb4Config extends Config(new Pes(1) ++ new Epb(4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe2Epb4Config extends Config(new Pes(2) ++ new Epb(4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe3Epb4Config extends Config(new Pes(3) ++ new Epb(4) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe4Epb4Config extends Config(new Pes(4) ++ new Epb(4) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe1Epb8Config extends Config(new Pes(1) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe2Epb8Config extends Config(new Pes(2) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe3Epb8Config extends Config(new Pes(3) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe4Epb8Config extends Config(new Pes(4) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe5Epb8Config extends Config(new Pes(5) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe6Epb8Config extends Config(new Pes(6) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe7Epb8Config extends Config(new Pes(7) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)
class XFilesDanaPe8Epb8Config extends Config(new Pes(8) ++ new Epb(8) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe16Epb16Config extends Config(new Pes(16) ++ new Epb(16) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaPe32Epb32Config extends Config(new Pes(32) ++ new Epb(32) ++
  new XFilesDanaFPGAConfig)

class XFilesDanaCppPe1Epb4Config extends Config(new Pes(1) ++ new Epb(4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb4Config extends Config(new Pes(2) ++ new Epb(4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb4Config extends Config(new Pes(3) ++ new Epb(4) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb4Config extends Config(new Pes(4) ++ new Epb(4) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb8Config extends Config(new Pes(1) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb8Config extends Config(new Pes(2) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb8Config extends Config(new Pes(3) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb8Config extends Config(new Pes(4) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe5Epb8Config extends Config(new Pes(5) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe6Epb8Config extends Config(new Pes(6) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe7Epb8Config extends Config(new Pes(7) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb8Config extends Config(new Pes(8) ++ new Epb(8) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb16Config extends Config(new Pes(1) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe2Epb16Config extends Config(new Pes(2) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe3Epb16Config extends Config(new Pes(3) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb16Config extends Config(new Pes(4) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe5Epb16Config extends Config(new Pes(5) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe6Epb16Config extends Config(new Pes(6) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe7Epb16Config extends Config(new Pes(7) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb16Config extends Config(new Pes(8) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe16Epb16Config extends Config(new Pes(8) ++ new Epb(16) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb32Config extends Config(new Pes(1) ++ new Epb(32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe4Epb32Config extends Config(new Pes(4) ++ new Epb(32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe8Epb32Config extends Config(new Pes(8) ++ new Epb(32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe16Epb32Config extends Config(new Pes(16) ++ new Epb(32) ++
  new XFilesDanaCPPConfig)
class XFilesDanaCppPe32Epb32Config extends Config(new Pes(32) ++ new Epb(32) ++
  new XFilesDanaCPPConfig)

class XFilesDanaCppPe1Epb4StandaloneConfig extends Config(
  new xfiles.standalone.AsStandalone ++ new XFilesDanaCppPe1Epb4Config)
