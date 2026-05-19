import * as THREE from '/assets/three.module.js';

// Renders Minecraft items the way Minecraft does: by walking the official
// item-definition → model → parent-chain graph and baking the resulting
// elements + textures into a small WebGL scene. Returns an HTMLImageElement
// per material, cached forever for that page.

const RENDER_SIZE = 96;
const FACE_BRIGHTNESS = { up: 1.0, down: 0.5, north: 0.8, south: 0.8, east: 0.6, west: 0.6 };
const DEFAULT_GUI_TRANSFORM = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
const DEFAULT_BLOCK_GUI = { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] };

// Default "plains biome" tints — what Minecraft uses for the inventory icon
// when no biome context exists. Faces with a `tintindex` get multiplied by
// the entry for their material, leaving the texture's grayscale source
// (e.g. block/grass_block_top.png) as the only colour signal.
const GRASS   = 0x91BD59;
const FOLIAGE = 0x48B518;
const WATER   = 0x3F76E4;
const TINT_RGB = {
    grass_block: GRASS, short_grass: GRASS, tall_grass: GRASS,
    fern: GRASS, large_fern: GRASS, sugar_cane: GRASS,
    pink_petals: GRASS,
    oak_leaves: FOLIAGE, jungle_leaves: FOLIAGE, acacia_leaves: FOLIAGE,
    dark_oak_leaves: FOLIAGE, mangrove_leaves: FOLIAGE, vine: FOLIAGE,
    birch_leaves: 0x80A755,
    spruce_leaves: 0x619961,
    lily_pad: 0x208030,
    water: WATER, water_bucket: WATER,
    melon_stem: 0xE0C71C, pumpkin_stem: 0xE0C71C,
    attached_melon_stem: 0xE0C71C, attached_pumpkin_stem: 0xE0C71C,
    redstone_wire: 0xFF0000,
};

const itemCache = new Map();      // material → Promise<HTMLImageElement | null>
const itemDefCache = new Map();   // material → Promise<itemdef json>
const modelCache = new Map();     // path → Promise<resolved model>
const textureCache = new Map();   // path → Promise<THREE.Texture>

let renderer = null;
let scene = null;
let camera = null;

function initThree() {
    if (renderer) return;
    renderer = new THREE.WebGLRenderer({ alpha: true, antialias: false, preserveDrawingBuffer: false });
    renderer.setSize(RENDER_SIZE, RENDER_SIZE);
    renderer.setPixelRatio(1);
    renderer.setClearColor(0x000000, 0);
    scene = new THREE.Scene();
    // Frustum sized to fit a 16-unit cube at the standard 0.625 GUI scale,
    // with a touch of padding so corners don't clip after rotation.
    const half = 8.2;
    camera = new THREE.OrthographicCamera(-half, half, half, -half, -200, 200);
    camera.position.set(0, 0, 100);
    camera.lookAt(0, 0, 0);
}

function stripNs(ref) {
    if (!ref) return ref;
    const i = ref.indexOf(':');
    return i >= 0 ? ref.substring(i + 1) : ref;
}

async function loadItemDef(name) {
    if (itemDefCache.has(name)) return itemDefCache.get(name);
    const p = fetch(`/assets/items/${name}.json`).then(r => {
        if (!r.ok) throw new Error(`item def ${name}: ${r.status}`);
        return r.json();
    });
    itemDefCache.set(name, p);
    return p;
}

const BUILTIN = {
    'builtin/generated': { builtin: 'generated', textures: {}, display: {} },
    'builtin/entity':    { builtin: 'entity',    textures: {}, display: {} },
};

async function loadModel(path) {
    if (BUILTIN[path]) return BUILTIN[path];
    if (modelCache.has(path)) return modelCache.get(path);
    const p = (async () => {
        const r = await fetch(`/assets/models/${path}.json`);
        if (!r.ok) throw new Error(`model ${path}: ${r.status}`);
        const data = await r.json();
        if (!data.parent) return data;
        const parent = await loadModel(stripNs(data.parent));
        return mergeModel(parent, data);
    })();
    modelCache.set(path, p);
    return p;
}

function mergeModel(parent, child) {
    return {
        builtin:   child.builtin   ?? parent.builtin,
        elements:  child.elements  ?? parent.elements,
        gui_light: child.gui_light ?? parent.gui_light,
        textures:  { ...(parent.textures  || {}), ...(child.textures  || {}) },
        display:   { ...(parent.display   || {}), ...(child.display   || {}) },
    };
}

function resolveTextureRef(textures, ref) {
    let cur = ref;
    for (let i = 0; i < 16 && cur; i++) {
        if (!cur.startsWith('#')) return stripNs(cur);
        cur = textures[cur.substring(1)];
    }
    return null;
}

async function loadTexture(path) {
    if (textureCache.has(path)) return textureCache.get(path);
    const p = new Promise((resolve, reject) => {
        new THREE.TextureLoader().load(`/assets/textures/${path}.png`, tex => {
            tex.magFilter = THREE.NearestFilter;
            tex.minFilter = THREE.NearestFilter;
            tex.colorSpace = THREE.SRGBColorSpace;
            tex.wrapS = THREE.ClampToEdgeWrapping;
            tex.wrapT = THREE.ClampToEdgeWrapping;
            tex.generateMipmaps = false;
            // Animated textures are vertical strips; crop to the first frame
            // by repeating only the top square portion of the image.
            const img = tex.image;
            if (img && img.height > img.width) {
                const frame = img.width / img.height;
                tex.repeat.y = frame;
                tex.offset.y = 1 - frame;
                tex.needsUpdate = true;
            }
            resolve(tex);
        }, undefined, reject);
    });
    textureCache.set(path, p);
    return p;
}

// Extract the first concrete model path from an item definition's model node.
// Handles minecraft:model directly and recurses into condition / select /
// range_dispatch / etc., taking whatever fallback the structure exposes.
function extractModelPath(node) {
    if (!node) return null;
    if (typeof node === 'string') return node;
    if (node.type === 'minecraft:model' && typeof node.model === 'string') return node.model;
    if (node.type === 'minecraft:special' && typeof node.base === 'string') return node.base;
    const fields = ['fallback', 'on_false', 'on_true', 'model'];
    for (const f of fields) {
        if (node[f]) {
            const r = extractModelPath(node[f]);
            if (r) return r;
        }
    }
    for (const arrField of ['cases', 'entries']) {
        const arr = node[arrField];
        if (Array.isArray(arr)) {
            for (const e of arr) {
                const r = extractModelPath(e.model || e);
                if (r) return r;
            }
        }
    }
    return null;
}

function tintToRgb(tint) {
    if (!tint || typeof tint !== 'object') return null;
    const type = stripNs(tint.type);
    if (type === 'constant' && Number.isFinite(tint.value)) return tint.value & 0xFFFFFF;
    if (type === 'grass') return GRASS;
    if (type === 'foliage') return FOLIAGE;
    if (type === 'water') return WATER;
    return null;
}

function extractTintRgb(node, itemName) {
    if (!node || typeof node === 'string') return TINT_RGB[itemName] ?? null;
    if (Array.isArray(node.tints) && node.tints.length) {
        const rgb = tintToRgb(node.tints[0]);
        if (rgb != null) return rgb;
    }
    const fields = ['fallback', 'on_false', 'on_true', 'model'];
    for (const f of fields) {
        if (node[f] && typeof node[f] !== 'string') {
            const r = extractTintRgb(node[f], itemName);
            if (r != null) return r;
        }
    }
    for (const arrField of ['cases', 'entries']) {
        const arr = node[arrField];
        if (Array.isArray(arr)) {
            for (const e of arr) {
                const r = extractTintRgb(e.model || e, itemName);
                if (r != null) return r;
            }
        }
    }
    return TINT_RGB[itemName] ?? null;
}

// Convert MC face data (face name, element from/to, uv rect) to vertex
// positions and UVs ready for a THREE.BufferGeometry. Vertices are emitted
// in TL, BL, BR, TR order when looking at the face from outside the cuboid.
function faceQuad(face, from, to, uv) {
    const [x1, y1, z1] = from;
    const [x2, y2, z2] = to;
    const [u1, v1, u2, v2] = uv;
    let positions;
    switch (face) {
        case 'up':    positions = [x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1]; break;
        case 'down':  positions = [x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2]; break;
        case 'north': positions = [x2,y2,z1, x2,y1,z1, x1,y1,z1, x1,y2,z1]; break;
        case 'south': positions = [x1,y2,z2, x1,y1,z2, x2,y1,z2, x2,y2,z2]; break;
        case 'east':  positions = [x2,y2,z2, x2,y1,z2, x2,y1,z1, x2,y2,z1]; break;
        case 'west':  positions = [x1,y2,z1, x1,y1,z1, x1,y1,z2, x1,y2,z2]; break;
        default: return null;
    }
    const uvs = [
        u1 / 16, 1 - v1 / 16,
        u1 / 16, 1 - v2 / 16,
        u2 / 16, 1 - v2 / 16,
        u2 / 16, 1 - v1 / 16,
    ];
    return { positions, uvs };
}

// When a face omits "uv", MC derives it from the element's coords.
function defaultUV(face, from, to) {
    const [x1, y1, z1] = from;
    const [x2, y2, z2] = to;
    switch (face) {
        case 'up':
        case 'down':  return [x1, z1, x2, z2];
        case 'north':
        case 'south': return [x1, 16 - y2, x2, 16 - y1];
        case 'east':
        case 'west':  return [z1, 16 - y2, z2, 16 - y1];
        default:      return [0, 0, 16, 16];
    }
}

async function buildElementGroup(elem, textures, guiLight, tintRgb) {
    const group = new THREE.Group();
    const faces = elem.faces || {};
    const sideLit = guiLight !== 'front';
    for (const [face, data] of Object.entries(faces)) {
        const texPath = resolveTextureRef(textures, data.texture);
        if (!texPath) continue;
        let tex;
        try { tex = await loadTexture(texPath); }
        catch { continue; }
        const uv = data.uv || defaultUV(face, elem.from, elem.to);
        const quad = faceQuad(face, elem.from, elem.to, uv);
        if (!quad) continue;
        const geom = new THREE.BufferGeometry();
        geom.setAttribute('position', new THREE.Float32BufferAttribute(quad.positions, 3));
        geom.setAttribute('uv', new THREE.Float32BufferAttribute(quad.uvs, 2));
        geom.setIndex([0, 1, 2, 0, 2, 3]);
        const b = sideLit ? (FACE_BRIGHTNESS[face] ?? 1) : 1;
        const tinted = (data.tintindex !== undefined) && tintRgb != null;
        const tr = tinted ? ((tintRgb >> 16) & 0xFF) / 255 : 1;
        const tg = tinted ? ((tintRgb >>  8) & 0xFF) / 255 : 1;
        const tb = tinted ? ( tintRgb        & 0xFF) / 255 : 1;
        const mat = new THREE.MeshBasicMaterial({
            map: tex,
            color: new THREE.Color(b * tr, b * tg, b * tb),
            transparent: true,
            alphaTest: 0.01,
            side: THREE.DoubleSide,
            depthWrite: true,
            // Pull tinted overlay faces a hair toward the camera so they
            // don't z-fight the non-tinted faces underneath (e.g. grass
            // block's side overlay over its dirt-and-grass side).
            polygonOffset: tinted,
            polygonOffsetFactor: tinted ? -1 : 0,
            polygonOffsetUnits: tinted ? -1 : 0,
        });
        group.add(new THREE.Mesh(geom, mat));
    }
    if (elem.rotation) {
        const origin = elem.rotation.origin || [8, 8, 8];
        const angle = ((elem.rotation.angle || 0) * Math.PI) / 180;
        const axis = elem.rotation.axis || 'y';
        const wrapTo = new THREE.Group();
        wrapTo.position.set(origin[0], origin[1], origin[2]);
        const rot = new THREE.Group();
        if (axis === 'x') rot.rotation.x = angle;
        else if (axis === 'y') rot.rotation.y = angle;
        else if (axis === 'z') rot.rotation.z = angle;
        wrapTo.add(rot);
        const wrapBack = new THREE.Group();
        wrapBack.position.set(-origin[0], -origin[1], -origin[2]);
        rot.add(wrapBack);
        wrapBack.add(group);
        return wrapTo;
    }
    return group;
}

async function buildElementsModel(model, tintRgb) {
    const group = new THREE.Group();
    const textures = model.textures || {};
    const guiLight = model.gui_light;
    for (const elem of model.elements || []) {
        group.add(await buildElementGroup(elem, textures, guiLight, tintRgb));
    }
    return group;
}

async function buildLayeredSprite(model, tintRgb) {
    const group = new THREE.Group();
    const textures = model.textures || {};
    for (let i = 0; ; i++) {
        const ref = textures[`layer${i}`];
        if (!ref) break;
        let tex;
        try { tex = await loadTexture(stripNs(ref)); }
        catch { continue; }
        const geom = new THREE.BufferGeometry();
        geom.setAttribute('position', new THREE.Float32BufferAttribute([
             0, 16, 0,
             0,  0, 0,
            16,  0, 0,
            16, 16, 0,
        ], 3));
        geom.setAttribute('uv', new THREE.Float32BufferAttribute([0, 1, 0, 0, 1, 0, 1, 1], 2));
        geom.setIndex([0, 1, 2, 0, 2, 3]);
        const tinted = i === 0 && tintRgb != null;
        const tr = tinted ? ((tintRgb >> 16) & 0xFF) / 255 : 1;
        const tg = tinted ? ((tintRgb >>  8) & 0xFF) / 255 : 1;
        const tb = tinted ? ( tintRgb        & 0xFF) / 255 : 1;
        const mat = new THREE.MeshBasicMaterial({
            map: tex,
            color: new THREE.Color(tr, tg, tb),
            transparent: true,
            alphaTest: 0.01,
            side: THREE.DoubleSide,
        });
        const mesh = new THREE.Mesh(geom, mat);
        mesh.position.z = i * 0.05;
        group.add(mesh);
    }
    return group;
}

function atlasUv(x1, y1, x2, y2, tw = 64, th = 64) {
    return [x1 / tw, 1 - y1 / th, x1 / tw, 1 - y2 / th, x2 / tw, 1 - y2 / th, x2 / tw, 1 - y1 / th];
}

function addBoxFace(group, positions, uv, material) {
    const geom = new THREE.BufferGeometry();
    geom.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geom.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    geom.setIndex([0, 1, 2, 0, 2, 3]);
    group.add(new THREE.Mesh(geom, material));
}

function addShieldBox(group, from, to, frontUv, backUv, frontMat, backMat, sideMat) {
    const [x1, y1, z1] = from;
    const [x2, y2, z2] = to;
    const full = [0, 0, 0, 1, 1, 1, 1, 0];
    addBoxFace(group, [x1,y2,z2, x1,y1,z2, x2,y1,z2, x2,y2,z2], frontUv, frontMat); // south/front
    addBoxFace(group, [x2,y2,z1, x2,y1,z1, x1,y1,z1, x1,y2,z1], backUv, backMat);   // north/back
    addBoxFace(group, [x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1], full, sideMat);     // top
    addBoxFace(group, [x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2], full, sideMat);     // bottom
    addBoxFace(group, [x2,y2,z2, x2,y1,z2, x2,y1,z1, x2,y2,z1], full, sideMat);     // right
    addBoxFace(group, [x1,y2,z1, x1,y1,z1, x1,y1,z2, x1,y2,z2], full, sideMat);     // left
}

async function buildShieldSprite() {
    const tex = await loadTexture('entity/shield/shield_base_nopattern');
    const textured = new THREE.MeshBasicMaterial({
        map: tex,
        transparent: true,
        alphaTest: 0.01,
        side: THREE.FrontSide,
    });
    const side = new THREE.MeshBasicMaterial({ color: new THREE.Color(0.48, 0.48, 0.52) });
    const handle = new THREE.MeshBasicMaterial({ color: new THREE.Color(0.30, 0.20, 0.10) });
    const group = new THREE.Group();

    // Vanilla shields are special entity models, so they don't expose normal
    // item-model elements. Build a small cuboid approximation from the entity
    // texture atlas instead of drawing a flat sprite.
    addShieldBox(group, [2, -3, 7], [14, 19, 9], atlasUv(1, 2, 13, 24), atlasUv(15, 2, 27, 24), textured, textured, side);
    addShieldBox(group, [5, 4, 5], [11, 12, 7], atlasUv(29, 1, 35, 9), atlasUv(36, 1, 42, 9), textured, textured, handle);
    return group;
}

const SHIELD_GUI_TRANSFORM = { rotation: [15, -35, -5], translation: [0, 0, 0], scale: [0.72, 0.72, 0.72] };

function applyGuiTransform(group, display, isBlockShape) {
    const gui = (display && display.gui) || (isBlockShape ? DEFAULT_BLOCK_GUI : DEFAULT_GUI_TRANSFORM);
    const r = gui.rotation || [0, 0, 0];
    const t = gui.translation || [0, 0, 0];
    const s = gui.scale || [1, 1, 1];
    const inner = new THREE.Group();
    inner.position.set(-8, -8, -8);
    inner.add(group);
    const outer = new THREE.Group();
    outer.rotation.set(
        THREE.MathUtils.degToRad(r[0]),
        THREE.MathUtils.degToRad(r[1]),
        THREE.MathUtils.degToRad(r[2]),
    );
    outer.scale.set(s[0], s[1], s[2]);
    outer.position.set(t[0], t[1], t[2]);
    outer.add(inner);
    return outer;
}

function disposeGroup(group) {
    group.traverse(obj => {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) {
            // Don't dispose the texture — it's shared via cache.
            obj.material.dispose();
        }
    });
}

async function renderItem(name) {
    if (itemCache.has(name)) return itemCache.get(name);
    const p = (async () => {
        try {
            initThree();
            const itemDef = await loadItemDef(name);
            const modelRef = extractModelPath(itemDef.model);
            if (!modelRef) return null;
            const model = await loadModel(stripNs(modelRef));
            const isBlockShape = !!(model.elements && model.elements.length);
            const tintRgb = extractTintRgb(itemDef.model, name);
            const isShield = name === 'shield';
            const inner = isShield
                ? await buildShieldSprite()
                : isBlockShape
                    ? await buildElementsModel(model, tintRgb)
                    : await buildLayeredSprite(model, tintRgb);
            const outer = applyGuiTransform(inner, isShield ? { gui: SHIELD_GUI_TRANSFORM } : model.display, isBlockShape);
            // The next four lines must stay synchronous so concurrent
            // renderItem() callers can't interleave on the shared scene.
            scene.add(outer);
            renderer.render(scene, camera);
            const dataUrl = renderer.domElement.toDataURL('image/png');
            scene.remove(outer);
            disposeGroup(outer);
            return dataUrl;
        } catch (e) {
            console.warn('blockrenderer: failed', name, e);
            return null;
        }
    })();
    itemCache.set(name, p);
    return p;
}

export { renderItem };
