# ExactCalculator — Modern Expressive UI

This fork keeps the original calculator/evaluator implementation and redesigns the UI around the requested modern rounded Material-style design.

## Included
- Rounded calculator display card with soft mint/teal light theme.
- Matching dark theme.
- Floating bottom navigation pill: Calculator / History / Converter / Settings.
- Modern calculator header and history action.
- Modernized history cards.
- Functional Length, Weight and Temperature converter.
- Settings screen with System / Light / Dark selection.
- Optional Old Style theme switch using a warm vintage calculator palette.
- Existing scientific/evaluator/history functionality is retained in source.

## Notes
- The default portrait layout hides the advanced scientific keypad so the main screen matches the supplied reference design.
- The scientific keypad source remains in the project and can be exposed in a later dedicated Scientific screen/expansion control.
- Currency conversion is intentionally not faked; live currency data should be connected to a real data source if desired.

## Build
Place this directory at `packages/apps/ExactCalculator` (or keep the existing project path) and build the `ExactCalculator` target with the ROM's normal Android build system.


## MaxxOS About page

The Settings screen now includes an **About MaxxOS Calculator** entry. The page credits MaxxOS, Anshuman X, LineageOS and the wider open-source community, and provides a clickable link to the official source repository:

https://github.com/MaxxOS-AOSP/packages_apps_ExactCalculator
