// See LICENSE for license details.

package dana

import Chisel._
import cde.{Parameters, Field}

class MemoryInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val cache = (new CacheMemInterface).flip
}

class Memory(implicit p: Parameters) extends DanaModule()(p) {
  val io = new MemoryInterface

  // The output is connected, but does not do anything. So, these
  // values are just set to defaults.
  io.cache.req.ready := Bool(true)
  io.cache.resp.valid := Bool(false)
  io.cache.resp.bits.done := Bool(false)
  io.cache.resp.bits.data := UInt(0, width = elementsPerBlock * elementWidth)
  io.cache.resp.bits.cacheIndex := UInt(0, width = log2Up(cacheNumEntries))
  io.cache.resp.bits.addr := UInt(0, log2Up(cacheNumBlocks))

  // Assertions

  // This module doesn't do anything, but we should be concerned if
  // the cache starts talking to it. Consequently, I have an assertion
  // here that will fire if we see an inbound request.
  assert(!(io.cache.req.valid === Bool(true)),
    "Black box memory module received a valid request from the cache")
}
