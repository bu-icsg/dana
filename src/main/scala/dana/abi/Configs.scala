// See LICENSE.IBM for license details

package dana.abi

import chisel3._
import cde._

case class GlobalInfo_t (
  decimal_point: Int,
  error_function: Int,
  binary_format: Int,
  _unused_0: Int,
  total_weight_blocks: Int,
  total_neurons: Int,
  total_layers: Int,
  ptr_first_layer: Int,
  ptr_weights: Int
)

case class LayerInfo_t (
  ptr_neuron: Int,
  num_neurons: Int,
  num_neurons_previous: Int
)

case class NeuronInfo_t (
  ptr_weight_offset: Int,
  num_weights: Int,
  activation_function: Int,
  steepness: Int,
  _unused_0: Int,
  _unused_1: Int,
  bias: Int
)

class Abi32Bit extends Config ( topDefinitions = {
  (pname,site,here) => pname match {
    case DanaPtrBits  => 32
    case DanaDataBits => 32

    case GlobalInfo => GlobalInfo_t (
      decimal_point       = 3,
      error_function      = 1,
      binary_format       = 3,
      _unused_0           = 9,
      total_weight_blocks = 16,
      total_neurons       = 16,
      total_layers        = 16,
      ptr_first_layer     = site(DanaPtrBits),
      ptr_weights         = site(DanaPtrBits))

    case LayerInfo => LayerInfo_t (
      ptr_neuron           = site(DanaPtrBits),
      num_neurons          = 16,
      num_neurons_previous = 16)

    case NeuronInfo => NeuronInfo_t (
      ptr_weight_offset   = site(DanaPtrBits),
      num_weights         = 16,
      activation_function = 5,
      steepness           = 3,
      _unused_0           = 8,
      _unused_1           = 32,
      bias                = site(DanaDataBits))
    case DecimalPointOffset => 7
    case SteepnessOffset => 4
  }}
)
