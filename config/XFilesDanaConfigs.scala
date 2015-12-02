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

class XFilesDanaNoLearningCPPConfig extends Config(new XFilesDanaNoLearningConfig ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++ new DefaultCPPConfig)

class XFilesDanaFPGAConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGAConfig)

class XFilesDanaNoLearningFPGAConfig extends Config(new XFilesDanaNoLearningConfig ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGASmallConfig)

// Variants that use explicit numbers of PEs
class DanaPE1 extends Config (
  topDefinitions = { (pname,site,here) => pname match {
      case PeTableNumEntries => 1 }})

class DanaPE2 extends Config (
  topDefinitions = { (pname,site,here) => pname match {
      case PeTableNumEntries => 2 }})

class DanaPE3 extends Config (
  topDefinitions = { (pname,site,here) => pname match {
      case PeTableNumEntries => 3 }})

class DanaPE4 extends Config (
  topDefinitions = { (pname,site,here) => pname match {
      case PeTableNumEntries => 4 }})

class DanaPE5 extends Config (
  topDefinitions = { (pname,site,here) => pname match {
      case PeTableNumEntries => 5 }})

class DanaPE6 extends Config (
  topDefinitions = { (pname,site,here) => pname match {
      case PeTableNumEntries => 6 }})

class XFilesDanaPE1Config extends Config(new DanaPE1 ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultFPGAConfig)

class XFilesDanaPE2Config extends Config(new DanaPE2 ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultFPGAConfig)

class XFilesDanaPE3Config extends Config(new DanaPE3 ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultFPGAConfig)

class XFilesDanaPE4Config extends Config(new DanaPE4 ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultFPGAConfig)

class XFilesDanaPE5Config extends Config(new DanaPE5 ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultFPGAConfig)

class XFilesDanaPE6Config extends Config(new DanaPE6 ++
  new XFilesDanaConfig ++ new DefaultXFilesDanaFPGAConfig ++
  new DefaultFPGAConfig)
