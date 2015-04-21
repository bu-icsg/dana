package dana

import Chisel._

class DanaControlInterface extends DanaBundle()() {
  val tTable = (new TTableDanaInterface).flip
}

class DanaControl extends DanaModule()() {
  val io = new DanaControlInterface

  // Default values
  io.tTable.req.ready := Bool(true)   // [TODO] Not correct
  io.tTable.resp.valid := Bool(false)
  io.tTable.resp.bits.tableIndex := UInt(0)
  io.tTable.resp.bits.field := UInt(0)

  // No inbound requests, so we just handle whatever is valid coming
  // from the Transaction Table
  when (io.tTable.req.valid) {
    // Cache state is unknown and we're not waiting for the cache to
    // respond
    when (!io.tTable.req.bits.cacheValid &&
      !io.tTable.req.bits.waitingForCache) {
      // Send a request to the storage module

      // Send a response to the tTable
      io.tTable.resp.valid := Bool(true)
      io.tTable.resp.bits.tableIndex := io.tTable.req.bits.tableIndex
      io.tTable.resp.bits.field := TTABLE_WAITING_FOR_CACHE
    }
    when (io.tTable.req.bits.needsLayerInfo) {
      // Send a request to the storage module
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsRegisters) {
      // Send a request to the register file
    }
    when (io.tTable.req.bits.cacheValid &&
      io.tTable.req.bits.needsNextRegister) {
      // Send a request to the register file
      io.tTable.resp.valid := Bool(true)
    }
    // when (io.tTable.req.bits.cacheValid &&
    //   !io.tTable.req.bits.needsRegisters &&
    //   // The PE Table has at least one free entry:
    //   // io.peTable.req.ready) {
    //   // Send a request to the PE Table for an assignment
    //   // Respond to the tTable
    // }
  }
}
