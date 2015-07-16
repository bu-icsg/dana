package rocketchip

import Chisel._
import uncore._
import rocket._
import dana._

import Implicits._

class XFilesDanaConfig extends ChiselConfig (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case BuildRoCC => Some(() => (Module(new XFilesDana, { case CoreName => "XFilesDana" })))
      case RoCCMaxTaggedMemXacts => 1
    }}
)

class XFilesDanaCPPConfig extends ChiselConfig(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultCPPConfig)

class XFilesDanaFPGAConfig extends ChiselConfig(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGAConfig)

class XFilesDanaFPGASmallConfig extends ChiselConfig(new XFilesDanaConfig ++
  new DefaultXFilesDanaFPGAConfig ++ new DefaultFPGASmallConfig)
