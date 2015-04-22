package dana

import Chisel._

trait Constants{
  // Transaction Table State Entries. nnsim-hdl equivalent:
  //   controL_types::field_enum
  val TTABLE_VALID                 = UInt(0, log2Up(10))
  val TTABLE_RESERVED              = UInt(1, log2Up(10))
  val TTABLE_CACHE_VALID           = UInt(2, log2Up(10))
  val TTABLE_LAYER                 = UInt(3, log2Up(10))
  val TTABLE_WAITING_FOR_CACHE     = UInt(4, log2Up(10))
  val TTABLE_DONE                  = UInt(5, log2Up(10))
  val TTABLE_OUTPUT_LAYER          = UInt(6, log2Up(10))
  val TTABLE_INCREMENT_NODE        = UInt(7, log2Up(10))
  val TTABLE_REGISTER_INFO         = UInt(8, log2Up(10))
  val TTABLE_REGISTER_NEXT         = UInt(9, log2Up(10))
  // Cache Request Type
  val CACHE_LOAD                   = UInt(0, log2Up(3))
  val CACHE_LAYER_INFO             = UInt(1, log2Up(3))
  val CACHE_DECREMENT_IN_USE_COUNT = UInt(2, log2Up(3))
  // Cache to control field enum. nnsim-hdl equivalent:
  //   cache_types::field_enum
  val CACHE_INFO                   = UInt(0, log2Up(4))
  val CACHE_LAYER                  = UInt(1, log2Up(4))
  val CACHE_NEURON                 = UInt(2, log2Up(4))
  val CACHE_WEIGHT                 = UInt(3, log2Up(4))
  // Cache / PE access type enum. nnsim-hdl equivalent:
  //   pe_types::pe2storage_enum
  val PE_NEURON                    = UInt(0, log2Up(2))
  val PE_WEIGHT                    = UInt(1, log2Up(2))
}
