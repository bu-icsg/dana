// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package rocketchip

import chisel3._
import rocket._
import cde._

class HasDanaRocc extends Config ( topDefinitions = {
  (pname,site,here) => pname match {
    case BuildRoCC => Seq(
      RoccParameters(
        opcodes = OpcodeSet.custom0,
        generator = (p: Parameters) =>  Module(new xfiles.XFiles()(p)),
        nPTWPorts = 1))
    case RoccMaxTaggedMemXacts => 1
    case uncore.agents.CacheName => "L1D"
  }})

class DanaEmulatorConfig extends Config (
  new HasDanaRocc ++
  new xfiles.DefaultXFilesConfig ++
  new dana.DanaConfig(
    numPes     = 4,
    cache      = 1,
    cacheSize  = 512 * 1024,
    scratchpad =  16 * 1024) ++
    new dana.DefaultDanaConfig ++
    new BaseConfig)
