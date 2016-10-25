package xfiles.standalone

import cde.Config
import rocket._
import rocketchip._
import uncore.tilelink._

class AsStandalone extends Config (
  topDefinitions = { (pname, site, here) =>
    pname match {
      case TileId => 0
      case NCoreplexExtClients => 0
      case TLId => "L1toL2" }}
)
