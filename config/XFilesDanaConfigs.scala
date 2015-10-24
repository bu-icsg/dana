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
      case BuildRoCC => Some((p: Parameters) =>
        Module(new XFilesDana()(p.alterPartial({ case CoreName => "XFilesDana" }))))
      case CacheName => "L1D"
      case RoccMaxTaggedMemXacts => 1
    }}
)

class XFilesDanaCPPConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultCPPConfig)

class XFilesDanaFPGAConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends Config(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGASmallConfig)
