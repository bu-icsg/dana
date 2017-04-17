// See LICENSE.IBM for license details.

package dana

import chisel3._
import cde._
import xfiles.{BuildXFilesBackend, XFilesBackendParameters}
import dana.util._

class DefaultDanaConfig extends Config ( topDefinitions = {
  (pname,site,here) =>
  pname match {
    // NN Config Global Info
    case DecimalPointOffset        => 7
    case DecimalPointWidth         => 3
    case SteepnessOffset           => 4
    case LambdaWidth               => 16
    case LearningRateWidth         => 16
    case NNConfigPointerWidth      => 16
    case TotalLayersWidth          => 16
    case TotalNeuronsWidth         => 16
    case TotalWeightBlocksWidth    => 16
    case ElementsPerBlockCodeWidth => 2
    case ErrorFunctionWidth        => 1
    case NNConfigUnusedWidth       => 10
    // NN Config Layer Info
    case ElementWidth              => 32
    case ElementsPerBlock          => 4
    case SteepnessWidth            => 3
    case ActivationFunctionWidth   => 5
    case NumberOfWeightsWidth      => 8
    // NN Config Neuron Info
    case NeuronsInPrevLayerWidth   => 10
    case NeuronsInLayerWidth       => 10
    case NeuronPointerWidth        => 12
    // ANTW Parameters
    case AntwRobEntries            => 32
    // Field widths
    case NnidWidth                 => 16
    case FeedbackWidth             => 12
    // Processing Element Table
    case PeTableNumEntries         => 1
    case PeCooldownWidth           => 8
    // Configuration Cache
    case CacheNumEntries           => 2
    case CacheDataSize             => 32 * 1024
    // Register File
    case RegisterFileNumElements   => 10240
    // Enables support for in-hardware learning
    case LearningEnabled           => true
    case BitsPerBlock              => site(ElementsPerBlock) * site(ElementWidth)
    case BytesPerBlock             => site(BitsPerBlock) / 8
    case RegFileNumBlocks          => divUp(site(RegisterFileNumElements),
      site(ElementsPerBlock))
    case CacheNumBlocks            => divUp(divUp((site(CacheDataSize) * 8),
      site(ElementWidth)), site(ElementsPerBlock))
    case NNConfigNeuronWidth       => 64
    case BuildXFilesBackend        => XFilesBackendParameters(
      generator = (p: Parameters)  => Module(new Dana()(p)),
      csrFile_gen = (p: Parameters) => Module(new dana.CSRFile()(p)),
      csrStatus_gen = (p: Parameters) => new DanaStatus()(p),
      csrProbes_gen = (p: Parameters) => new DanaProbes()(p),
      info = packInfo(site(ElementsPerBlock), site(PeTableNumEntries),
        site(CacheNumEntries)))
    case EnableCacheDump           => false
    case _ => throw new CDEMatchError
  }}
)

class DanaNoLearningConfig extends Config ( topDefinitions = {
  (pname,site,here) =>
  pname match {
    case LearningEnabled => false
    case _ => throw new CDEMatchError
  }}
)

class DanaConfig
  (numPes:      Int     = 1,
    epb:        Int     = 4,
    cache:      Int     = 2,
    scratchpad: Int     = 10240,
    learning:   Boolean = true)
    extends Config( topDefinitions = {
  (pname,site,here) => pname match {
    case LearningEnabled         => learning
    case PeTableNumEntries       => numPes
    case ElementsPerBlock        => epb
    case CacheNumEntries         => cache
    case RegisterFileNumElements => scratchpad
    case _ => throw new CDEMatchError
  }})
