// See LICENSE.IBM for license details

package dana

import chisel3._
import cde.{Parameters, Field}
import _root_.util.ParameterizedBundle

case object DanaPtr extends Field[Int]
case object DanaData extends Field[Int]
case object GlobalInfo extends Field[GlobalInfo_t]
case object LayerInfo extends Field[LayerInfo_t]
case object NeuronInfo extends Field[NeuronInfo_t]

class NnConfigHeader(implicit p: Parameters) extends ParameterizedBundle()(p) {
  val info = p(GlobalInfo)
  val weightsPointer         = UInt(info.ptr_weights.W)
  val firstLayerPointer      = UInt(info.ptr_first_layer.W)
  val totalLayers            = UInt(info.total_layers.W)
  val totalNeurons           = UInt(info.total_neurons.W)
  val totalWeightBlocks      = UInt(info.total_weight_blocks.W)
  val _unused                = UInt(info._unused_0.W)
  val elementsPerBlockCode   = UInt(info.binary_format.W)
  val errorFunction          = UInt(info.error_function.W)
  val decimalPoint           = UInt(info.decimal_point.W)
}

class NnConfigLayer(implicit p: Parameters) extends ParameterizedBundle()(p) {
  val info = p(LayerInfo)
  val neuronsInPreviousLayer = UInt(info.num_neurons_previous.W)
  val neuronsInLayer         = UInt(info.num_neurons.W)
  val neuronPointer          = UInt(info.ptr_neuron.W)
}

class NnConfigNeuron(implicit p: Parameters) extends ParameterizedBundle()(p) {
  val info = p(NeuronInfo)
  val bias                   = UInt(info.bias.W)
  val _unused_1              = UInt(info._unused_1.W)
  val _unused_0              = UInt(info._unused_0.W)
  val steepness              = UInt(info.steepness.W)
  val activationFunction     = UInt(info.activation_function.W)
  val numberOfWeights        = UInt(info.num_weights.W)
  val weightOffset           = UInt(info.ptr_weight_offset.W)
}
