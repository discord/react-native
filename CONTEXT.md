# iOS Shadow Rendering

This context defines the silhouettes and compositing behavior of iOS shadows rendered by React Native views.

## Language

**Border-box shadow**:
A legacy iOS shadow whose silhouette is the view's border box and resolved corner radii, independent of rendered content alpha. The caller is responsible for ensuring that this silhouette remains appropriate for its content and backdrop.

**Content-alpha shadow**:
A legacy iOS shadow whose silhouette is derived from the composited alpha of the view and its descendants.

**Backdrop precomposition**:
Rendering an opaque backplate beneath a border-box shadow host so the shadow cannot show through otherwise translucent pixels. It is a best-effort optimization applied only after border-box behavior has already been selected.

**Backdrop provider**:
A canonical view or root with a concrete opaque solid background that supplies an inherited backdrop color. Unknown or content-bearing component types terminate Fabric backdrop propagation.

**Inherited backdrop color**:
A concrete opaque color supplied by a backdrop provider and passed unchanged through transparent wrappers.

**Translucent ancestor barrier**:
A non-opaque background between a backdrop provider and a shadow host. It terminates backdrop propagation because using one unblended backplate would change the rendered pixels.

**Concrete color**:
A fixed RGBA color whose value does not depend on native appearance traits. Native dynamic and semantic system colors are not concrete colors.

**Opaque backplate**:
A border-box-shaped fill beneath a shadow host's normal background and content. It uses the uniform opaque backdrop color to prevent a border-box shadow from showing through translucent pixels.

**Uniform opaque backdrop**:
A single fully opaque color behind every point of a view's border box. Previously painted content that makes the color vary means the backdrop is not uniform.
_Avoid_: Parent background color

**Propagation source**:
Backdrop eligibility is derived once for each complete, laid-out old and new
Fabric tree at mounting-diff time. Later flattening slices read the derived
environment from a tag-indexed sidecar map. The mounted UIKit hierarchy cannot
be used because view flattening can erase logical provider boundaries.
