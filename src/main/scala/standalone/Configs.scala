// See LICENSE.IBM for license details.

package xfiles.standalone

import config._
import rocket._
import rocketchip._
import uncore.tilelink._

class AsStandalone extends Config (
  (pname, site, here) => {
    pname match {
      case TileLinkRAMSize => 1024 * 1024}
  })
