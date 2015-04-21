package dana

import Chisel._

trait Constants{
  // Transaction Table State Entries mimicking the
  val TTABLE_VALID             = UInt(0, log2Up(10))
  val TTABLE_RESERVED          = UInt(1, log2Up(10))
  val TTABLE_CACHE_VALID       = UInt(2, log2Up(10))
  val TTABLE_LAYER             = UInt(3, log2Up(10))
  val TTABLE_WAITING_FOR_CACHE = UInt(4, log2Up(10))
  val TTABLE_DONE              = UInt(5, log2Up(10))
  val TTABLE_OUTPUT_LAYER      = UInt(6, log2Up(10))
  val TTABLE_INCREMENT_NODE    = UInt(7, log2Up(10))
  val TTABLE_REGISTER_INFO     = UInt(8, log2Up(10))
  val TTABLE_REGISTER_NEXT     = UInt(9, log2Up(10))
}
