package dana

import Chisel._

class MemoryInterface extends DanaBundle()() {
  val cache = (new CacheMemInterface).flip
}

class Memory extends DanaModule()() {
  val io = new MemoryInterface

  // The output is connected, but does not do anything. So, these
  // values are just set to defaults.
  io.cache.resp.valid := Bool(false)
  io.cache.resp.bits.done := Bool(false)
  io.cache.resp.bits.data := UInt(0, width = elementsPerBlock * elementWidth)
  io.cache.resp.bits.cacheIndex := UInt(0, width = log2Up(cacheNumEntries))
  io.cache.resp.bits.addr := UInt(0, log2Up(cacheNumBlocks))
  io.cache.resp.bits.inUse := Bool(false)

  // Assertions

  // This module doesn't do anything, but we should be concerned if
  // the cache starts talking to it. Consequently, I have an assertion
  // here that will fire if we see an inbound request.
  assert(io.cache.req.valid === Bool(false),
    "Black box memory module received a valid request from the cache")
}
