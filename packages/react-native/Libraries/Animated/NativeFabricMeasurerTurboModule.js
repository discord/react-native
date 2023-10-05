import type {TurboModule} from '../TurboModule/RCTExport';
import * as TurboModuleRegistry from '../TurboModule/TurboModuleRegistry';

type MeasureOnSuccessCallback = (
  x: number,
  y: number,
  width: number,
  height: number,
  pageX: number,
  pageY: number,
) => void;

type MeasureInWindowOnSuccessCallback = (
  x: number,
  y: number,
  width: number,
  height: number,
) => void;

export interface Spec extends TurboModule {
  +measureNatively: (viewTag: number, callback: MeasureOnSuccessCallback) => void,
  +measureInWindowNatively: (
    viewTag: number,
    callback: MeasureInWindowOnSuccessCallback,
  ) => void,
}

export default (TurboModuleRegistry.get<Spec>(
  'NativeFabricMeasurerTurboModule',
): ?Spec);
