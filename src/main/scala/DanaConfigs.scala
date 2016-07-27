// See LICENSE for license details.

package dana

import Chisel._
import cde.{Parameters, Config, Dump, Knob}
import xfiles.{BuildXFilesBackend, XFilesBackendParameters}

class DefaultDanaConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    def divUp (dividend: Int, divisor: Int): Int = {
      (dividend + divisor - 1) / divisor}
    def packInfo (): Int = {
      var x = site(ElementsPerBlock) << (6 + 4);
      x = x | site(PeTableNumEntries) << 4;
      x = x | site(CacheNumEntries);
      x}
    pname match {
      // NN Config Global Info
      case DecimalPointOffset => Dump("NNCONFIG_DECIMAL_POINT_OFFSET", 7)
      case DecimalPointWidth => Dump("NNCONFIG_DECIMAL_POINT_WIDTH", 3)
      case SteepnessOffset => Dump("NNCONFIG_SteepnessOffset", 4)
      case LambdaWidth => Dump("NNCONFIG_LambdaWidth", 16)
      case LearningRateWidth => Dump("NNCONFIG_LearningRateWidth", 16)
      case NNConfigPointerWidth => Dump("NNCONFIG_NNConfigPointerWidth", 16)
      case TotalLayersWidth => Dump("NNCONFIG_TotalLayersWidth", 16)
      case TotalNeuronsWidth => Dump("NNCONFIG_TotalNeuronsWidth", 16)
      case TotalWeightBlocksWidth => Dump("NNCONFIG_TotalWeightBlocksWidth", 16)
      case ElementsPerBlockCodeWidth => Dump("NNCONFIG_ElementsPerBlockCodeWidth", 2)
      case ErrorFunctionWidth => Dump("NNCONFIG_ErrorFunctionWidth", 1)
      case NNConfigUnusedWidth => Dump("NNCONFIG_UnusedWidth", 10)
      // NN Config Layer Info
      case ElementWidth => Dump("ELEMENT_WIDTH", 32)
      case ElementsPerBlock => Dump(Knob("ELEMENTS_PER_BLOCK"))
      case SteepnessWidth => Dump("NNCONFIG_SteepnessWidth", 3)
      case ActivationFunctionWidth => Dump("NNCONFIG_ActivationFunctionWidth", 5)
      case NumberOfWeightsWidth => Dump("NNCONFIG_NumberOfWeightsWidth", 8)
      // NN Config Neuron Info
      case NeuronsInPrevLayerWidth => Dump("NNCONFIG_neuronsInPrevLayerWidth", 10)
      case NeuronsInLayerWidth => Dump("NNCONFIG_neuronsInLayerWidth", 10)
      case NeuronPointerWidth => Dump("NNCONFIG_neuronPointerWidth", 12)
      // ANTW Parameters
      case AntwRobEntries => 32
      // Field widths
      case NnidWidth => Dump("NNID_WIDTH", 16)
      case FeedbackWidth => Dump("FEEDBACK_WIDTH", 12)
      // Processing Element Table
      case PeTableNumEntries => Dump(Knob("NUM_PES"))
      // Configuration Cache
      case CacheNumEntries => Dump(Knob("CACHE_NUM_ENTRIES"))
      case CacheDataSize => 32 * 1024
      // Register File
      case RegisterFileNumElements => Dump(Knob("REGISTER_FILE_NUM_ELEMENTS"))
      // Enables support for in-hardware learning
      case LearningEnabled => true
      case BitsPerBlock => site(ElementsPerBlock) * site(ElementWidth)
      case RegFileNumBlocks => divUp(site(RegisterFileNumElements),
        site(ElementsPerBlock))
      case CacheNumBlocks => divUp(divUp((site(CacheDataSize) * 8),
        site(ElementWidth)), site(ElementsPerBlock))
      case NNConfigNeuronWidth => 64
      case BuildXFilesBackend => XFilesBackendParameters(
          generator = (p: Parameters) => Module(new Dana()(p)),
          info = packInfo())
    }},
  // [TODO] Add constraints
  // topConstraints = List(
  //   { ex => ex(ElementWidth) == 32 },
  //   { ex => ex(ActivationFunctionWidth) <= 5 }
  // ),
  knobValues = {
    case "ELEMENTS_PER_BLOCK" => 4
    case "NUM_PES" => 1
    case "CACHE_NUM_ENTRIES" => 2
    case "REGISTER_FILE_NUM_ELEMENTS" => 10240
  }
)

class DanaNoLearningConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case LearningEnabled => false
    }}
)
