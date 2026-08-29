# Bundled Pikchr runtime

https://www.npmjs.com/package/pikchr-js

https://cdn.jsdelivr.net/npm/pikchr-js@0.1.4/pikchr.js

`pikchr.js` provides the self-contained browser runtime used by the standalone and Markdown Pikchr renderers.
It is copied unchanged from the published `pikchr-js` version `0.1.4` artifact at the jsDelivr URL above.
The artifact exposes `loadPikchr()`, which resolves to a renderer with a public `render()` API used by `PikchrRendering`.

To update it, select a versioned `pikchr-js` release, copy its published `pikchr.js` here unchanged, and update the package version and license notices together.
The single-file form is required because previews are loaded from local cache files where fetching a separate `.wasm` file is not reliable across SWT browser backends.
