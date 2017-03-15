// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana

import chisel3._
import chisel3.util._
import config._

class MemoryInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val cache = (new CacheAntwInterface).flip
}

class Memory(implicit p: Parameters) extends DanaModule()(p) {
  val io = IO(new MemoryInterface)

  // The output is connected, but does not do anything. So, these
  // values are just set to defaults.
  io.cache.req.ready            := true.B
  io.cache.resp.valid           := false.B
  io.cache.resp.bits.done       := false.B
  io.cache.resp.bits.data       := 0.U((elementsPerBlock * elementWidth).W)
  io.cache.resp.bits.cacheIndex := 0.U(log2Up(cacheNumEntries).W)
  io.cache.resp.bits.addr       := 0.U(log2Up(cacheNumBlocks).W)

  // Assertions

  // This module doesn't do anything, but we should be concerned if
  // the cache starts talking to it. Consequently, I have an assertion
  // here that will fire if we see an inbound request.
  assert(!(io.cache.req.valid === true.B),
    "Black box memory module received a valid request from the cache")
}
