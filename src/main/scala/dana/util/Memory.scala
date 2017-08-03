// See LICENSE.BU for license details.
// See LICENSE.IBM for license details.

package dana.util

import chisel3._
import chisel3.util._
import cde._

import dana._

class MemoryInterface(implicit p: Parameters) extends DanaBundle()(p) {
  val cache = Flipped(new CacheAntwInterface)
}

class Memory(implicit p: Parameters) extends DanaModule()(p) {
  val io = IO(new MemoryInterface)

  // The output is connected, but does not do anything. So, these
  // values are just set to defaults.
  io.cache.cmd.ready            := true.B
  io.cache.load.valid           := false.B
  io.cache.load.bits.done       := false.B
  io.cache.load.bits.data       := 0.U((elementsPerBlock * elementWidth).W)
  io.cache.load.bits.cacheIndex := 0.U(log2Up(cacheNumEntries).W)
  io.cache.load.bits.addr       := 0.U(log2Up(cacheNumBlocks).W)

  // Assertions

  // This module doesn't do anything, but we should be concerned if
  // the cache starts talking to it. Consequently, I have an assertion
  // here that will fire if we see an inbound request.
  assert(!(io.cache.cmd.valid === true.B),
    "Black box memory module received a valid request from the cache")
}
