import { BufferGeometry } from 'three/src/core/BufferGeometry.js';
import { Float32BufferAttribute } from 'three/src/core/BufferAttribute.js';
import { ClampToEdgeWrapping, DoubleSide, FrontSide, NearestFilter, SRGBColorSpace } from 'three/src/constants.js';
import { Color } from 'three/src/math/Color.js';
import * as MathUtils from 'three/src/math/MathUtils.js';
import { Group } from 'three/src/objects/Group.js';
import { Mesh } from 'three/src/objects/Mesh.js';
import { MeshBasicMaterial } from 'three/src/materials/MeshBasicMaterial.js';
import { OrthographicCamera } from 'three/src/cameras/OrthographicCamera.js';
import { Scene } from 'three/src/scenes/Scene.js';
import { TextureLoader } from 'three/src/loaders/TextureLoader.js';
import { WebGLRenderer } from 'three/src/renderers/WebGLRenderer.js';
import type { Material } from 'three/src/materials/Material.js';
import type { Texture } from 'three/src/textures/Texture.js';

const RENDER_SIZE = 96;
const FACE_BRIGHTNESS: Record<string, number> = { up: 1.0, down: 0.5, north: 0.8, south: 0.8, east: 0.6, west: 0.6 };
const DEFAULT_GUI_TRANSFORM = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
const DEFAULT_BLOCK_GUI = { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] };

const GRASS = 0x91bd59;
const FOLIAGE = 0x48b518;
const WATER = 0x3f76e4;
const TINT_RGB: Record<string, number> = {
  grass_block: GRASS,
  short_grass: GRASS,
  tall_grass: GRASS,
  fern: GRASS,
  large_fern: GRASS,
  sugar_cane: GRASS,
  pink_petals: GRASS,
  oak_leaves: FOLIAGE,
  jungle_leaves: FOLIAGE,
  acacia_leaves: FOLIAGE,
  dark_oak_leaves: FOLIAGE,
  mangrove_leaves: FOLIAGE,
  vine: FOLIAGE,
  birch_leaves: 0x80a755,
  spruce_leaves: 0x619961,
  lily_pad: 0x208030,
  water: WATER,
  water_bucket: WATER,
  melon_stem: 0xe0c71c,
  pumpkin_stem: 0xe0c71c,
  attached_melon_stem: 0xe0c71c,
  attached_pumpkin_stem: 0xe0c71c,
  redstone_wire: 0xff0000
};

type Model = Record<string, any>;
type ItemDef = Record<string, any>;

const itemCache = new Map<string, Promise<string | null>>();
const itemDefCache = new Map<string, Promise<ItemDef>>();
const modelCache = new Map<string, Promise<Model>>();
const textureCache = new Map<string, Promise<Texture>>();

let renderer: WebGLRenderer | null = null;
let scene: Scene | null = null;
let camera: OrthographicCamera | null = null;

function initThree() {
  if (renderer) return;
  renderer = new WebGLRenderer({ alpha: true, antialias: false, preserveDrawingBuffer: false });
  renderer.setSize(RENDER_SIZE, RENDER_SIZE);
  renderer.setPixelRatio(1);
  renderer.setClearColor(0x000000, 0);
  scene = new Scene();
  const half = 8.2;
  camera = new OrthographicCamera(-half, half, half, -half, -200, 200);
  camera.position.set(0, 0, 100);
  camera.lookAt(0, 0, 0);
}

function stripNs(ref: string | null | undefined) {
  if (!ref) return ref;
  const index = ref.indexOf(':');
  return index >= 0 ? ref.substring(index + 1) : ref;
}

async function loadItemDef(name: string) {
  if (itemDefCache.has(name)) return itemDefCache.get(name) as Promise<ItemDef>;
  const promise = fetch(`/assets/items/${name}.json`).then((response) => {
    if (!response.ok) throw new Error(`item def ${name}: ${response.status}`);
    return response.json() as Promise<ItemDef>;
  });
  itemDefCache.set(name, promise);
  return promise;
}

const BUILTIN: Record<string, Model> = {
  'builtin/generated': { builtin: 'generated', textures: {}, display: {} },
  'builtin/entity': { builtin: 'entity', textures: {}, display: {} }
};

async function loadModel(path: string): Promise<Model> {
  if (BUILTIN[path]) return BUILTIN[path];
  if (modelCache.has(path)) return modelCache.get(path) as Promise<Model>;
  const promise = (async () => {
    const response = await fetch(`/assets/models/${path}.json`);
    if (!response.ok) throw new Error(`model ${path}: ${response.status}`);
    const data = (await response.json()) as Model;
    if (!data.parent) return data;
    const parent = await loadModel(stripNs(data.parent) ?? '');
    return mergeModel(parent, data);
  })();
  modelCache.set(path, promise);
  return promise;
}

function mergeModel(parent: Model, child: Model): Model {
  return {
    builtin: child.builtin ?? parent.builtin,
    elements: child.elements ?? parent.elements,
    gui_light: child.gui_light ?? parent.gui_light,
    textures: { ...(parent.textures || {}), ...(child.textures || {}) },
    display: { ...(parent.display || {}), ...(child.display || {}) }
  };
}

function resolveTextureRef(textures: Record<string, string>, ref: string) {
  let current: string | undefined | null = ref;
  for (let i = 0; i < 16 && current; i += 1) {
    if (!current.startsWith('#')) return stripNs(current);
    current = textures[current.substring(1)];
  }
  return null;
}

async function loadTexture(path: string) {
  if (textureCache.has(path)) return textureCache.get(path) as Promise<Texture>;
  const promise = new Promise<Texture>((resolve, reject) => {
    new TextureLoader().load(
      `/assets/textures/${path}.png`,
      (texture) => {
        texture.magFilter = NearestFilter;
        texture.minFilter = NearestFilter;
        texture.colorSpace = SRGBColorSpace;
        texture.wrapS = ClampToEdgeWrapping;
        texture.wrapT = ClampToEdgeWrapping;
        texture.generateMipmaps = false;
        const image = texture.image as HTMLImageElement | undefined;
        if (image && image.height > image.width) {
          const frame = image.width / image.height;
          texture.repeat.y = frame;
          texture.offset.y = 1 - frame;
          texture.needsUpdate = true;
        }
        resolve(texture);
      },
      undefined,
      reject
    );
  });
  textureCache.set(path, promise);
  return promise;
}

function extractModelPath(node: any): string | null {
  if (!node) return null;
  if (typeof node === 'string') return node;
  if (node.type === 'minecraft:model' && typeof node.model === 'string') return node.model;
  if (node.type === 'minecraft:special' && typeof node.base === 'string') return node.base;
  for (const field of ['fallback', 'on_false', 'on_true', 'model']) {
    if (node[field]) {
      const result = extractModelPath(node[field]);
      if (result) return result;
    }
  }
  for (const arrayField of ['cases', 'entries']) {
    const items = node[arrayField];
    if (!Array.isArray(items)) continue;
    for (const entry of items) {
      const result = extractModelPath(entry.model || entry);
      if (result) return result;
    }
  }
  return null;
}

function tintToRgb(tint: any) {
  if (!tint || typeof tint !== 'object') return null;
  const type = stripNs(tint.type);
  if (type === 'constant' && Number.isFinite(tint.value)) return tint.value & 0xffffff;
  if (type === 'grass') return GRASS;
  if (type === 'foliage') return FOLIAGE;
  if (type === 'water') return WATER;
  return null;
}

function extractTintRgb(node: any, itemName: string): number | null {
  if (!node || typeof node === 'string') return TINT_RGB[itemName] ?? null;
  if (Array.isArray(node.tints) && node.tints.length) {
    const rgb = tintToRgb(node.tints[0]);
    if (rgb != null) return rgb;
  }
  for (const field of ['fallback', 'on_false', 'on_true', 'model']) {
    if (node[field] && typeof node[field] !== 'string') {
      const result = extractTintRgb(node[field], itemName);
      if (result != null) return result;
    }
  }
  for (const arrayField of ['cases', 'entries']) {
    const items = node[arrayField];
    if (!Array.isArray(items)) continue;
    for (const entry of items) {
      const result = extractTintRgb(entry.model || entry, itemName);
      if (result != null) return result;
    }
  }
  return TINT_RGB[itemName] ?? null;
}

function faceQuad(face: string, from: number[], to: number[], uv: number[]) {
  const [x1, y1, z1] = from;
  const [x2, y2, z2] = to;
  const [u1, v1, u2, v2] = uv;
  let positions: number[];
  switch (face) {
    case 'up':
      positions = [x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1];
      break;
    case 'down':
      positions = [x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2];
      break;
    case 'north':
      positions = [x2, y2, z1, x2, y1, z1, x1, y1, z1, x1, y2, z1];
      break;
    case 'south':
      positions = [x1, y2, z2, x1, y1, z2, x2, y1, z2, x2, y2, z2];
      break;
    case 'east':
      positions = [x2, y2, z2, x2, y1, z2, x2, y1, z1, x2, y2, z1];
      break;
    case 'west':
      positions = [x1, y2, z1, x1, y1, z1, x1, y1, z2, x1, y2, z2];
      break;
    default:
      return null;
  }
  const uvs = [u1 / 16, 1 - v1 / 16, u1 / 16, 1 - v2 / 16, u2 / 16, 1 - v2 / 16, u2 / 16, 1 - v1 / 16];
  return { positions, uvs };
}

function defaultUV(face: string, from: number[], to: number[]) {
  const [x1, y1, z1] = from;
  const [x2, y2, z2] = to;
  switch (face) {
    case 'up':
    case 'down':
      return [x1, z1, x2, z2];
    case 'north':
    case 'south':
      return [x1, 16 - y2, x2, 16 - y1];
    case 'east':
    case 'west':
      return [z1, 16 - y2, z2, 16 - y1];
    default:
      return [0, 0, 16, 16];
  }
}

async function buildElementGroup(elem: any, textures: Record<string, string>, guiLight: string | undefined, tintRgb: number | null) {
  const group = new Group();
  const sideLit = guiLight !== 'front';
  for (const [face, data] of Object.entries<any>(elem.faces || {})) {
    const texturePath = resolveTextureRef(textures, data.texture);
    if (!texturePath) continue;
    let texture: Texture;
    try {
      texture = await loadTexture(texturePath);
    } catch {
      continue;
    }
    const quad = faceQuad(face, elem.from, elem.to, data.uv || defaultUV(face, elem.from, elem.to));
    if (!quad) continue;
    const geometry = new BufferGeometry();
    geometry.setAttribute('position', new Float32BufferAttribute(quad.positions, 3));
    geometry.setAttribute('uv', new Float32BufferAttribute(quad.uvs, 2));
    geometry.setIndex([0, 1, 2, 0, 2, 3]);
    const brightness = sideLit ? (FACE_BRIGHTNESS[face] ?? 1) : 1;
    const tinted = data.tintindex !== undefined && tintRgb != null;
    const red = tinted ? ((tintRgb >> 16) & 0xff) / 255 : 1;
    const green = tinted ? ((tintRgb >> 8) & 0xff) / 255 : 1;
    const blue = tinted ? (tintRgb & 0xff) / 255 : 1;
    const material = new MeshBasicMaterial({
      map: texture,
      color: new Color(brightness * red, brightness * green, brightness * blue),
      transparent: true,
      alphaTest: 0.01,
      side: DoubleSide,
      depthWrite: true,
      polygonOffset: tinted,
      polygonOffsetFactor: tinted ? -1 : 0,
      polygonOffsetUnits: tinted ? -1 : 0
    });
    group.add(new Mesh(geometry, material));
  }
  if (!elem.rotation) return group;
  const origin = elem.rotation.origin || [8, 8, 8];
  const angle = ((elem.rotation.angle || 0) * Math.PI) / 180;
  const axis = elem.rotation.axis || 'y';
  const wrapTo = new Group();
  wrapTo.position.set(origin[0], origin[1], origin[2]);
  const rotation = new Group();
  if (axis === 'x') rotation.rotation.x = angle;
  else if (axis === 'y') rotation.rotation.y = angle;
  else if (axis === 'z') rotation.rotation.z = angle;
  wrapTo.add(rotation);
  const wrapBack = new Group();
  wrapBack.position.set(-origin[0], -origin[1], -origin[2]);
  rotation.add(wrapBack);
  wrapBack.add(group);
  return wrapTo;
}

async function buildElementsModel(model: Model, tintRgb: number | null) {
  const group = new Group();
  for (const elem of model.elements || []) group.add(await buildElementGroup(elem, model.textures || {}, model.gui_light, tintRgb));
  return group;
}

async function buildLayeredSprite(model: Model, tintRgb: number | null) {
  const group = new Group();
  const textures = model.textures || {};
  for (let i = 0; ; i += 1) {
    const ref = textures[`layer${i}`];
    if (!ref) break;
    let texture: Texture;
    try {
      texture = await loadTexture(stripNs(ref) ?? '');
    } catch {
      continue;
    }
    const geometry = new BufferGeometry();
    geometry.setAttribute('position', new Float32BufferAttribute([0, 16, 0, 0, 0, 0, 16, 0, 0, 16, 16, 0], 3));
    geometry.setAttribute('uv', new Float32BufferAttribute([0, 1, 0, 0, 1, 0, 1, 1], 2));
    geometry.setIndex([0, 1, 2, 0, 2, 3]);
    const tinted = i === 0 && tintRgb != null;
    const red = tinted ? ((tintRgb >> 16) & 0xff) / 255 : 1;
    const green = tinted ? ((tintRgb >> 8) & 0xff) / 255 : 1;
    const blue = tinted ? (tintRgb & 0xff) / 255 : 1;
    const material = new MeshBasicMaterial({
      map: texture,
      color: new Color(red, green, blue),
      transparent: true,
      alphaTest: 0.01,
      side: DoubleSide
    });
    const mesh = new Mesh(geometry, material);
    mesh.position.z = i * 0.05;
    group.add(mesh);
  }
  return group;
}

function atlasUv(x1: number, y1: number, x2: number, y2: number, tw = 64, th = 64) {
  return [x1 / tw, 1 - y1 / th, x1 / tw, 1 - y2 / th, x2 / tw, 1 - y2 / th, x2 / tw, 1 - y1 / th];
}

function addBoxFace(group: Group, positions: number[], uv: number[], material: Material) {
  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new Float32BufferAttribute(positions, 3));
  geometry.setAttribute('uv', new Float32BufferAttribute(uv, 2));
  geometry.setIndex([0, 1, 2, 0, 2, 3]);
  group.add(new Mesh(geometry, material));
}

function addUnwrappedBox(group: Group, from: number[], to: number[], texU: number, texV: number, sx: number, sy: number, sz: number, material: Material) {
  const [x1, y1, z1] = from;
  const [x2, y2, z2] = to;
  const u1 = texU + sz;
  const u2 = u1 + sx;
  const u3 = u2 + sz;
  const u4 = u3 + sx;
  const v1 = texV + sz;
  const v2 = v1 + sy;
  addBoxFace(group, [x1, y2, z2, x1, y1, z2, x2, y1, z2, x2, y2, z2], atlasUv(u1, v1, u2, v2), material);
  addBoxFace(group, [x2, y2, z1, x2, y1, z1, x1, y1, z1, x1, y2, z1], atlasUv(u3, v1, u4, v2), material);
  addBoxFace(group, [x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1], atlasUv(u1, texV, u2, v1), material);
  addBoxFace(group, [x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2], atlasUv(u2, v1, u2 + sx, texV), material);
  addBoxFace(group, [x2, y2, z2, x2, y1, z2, x2, y1, z1, x2, y2, z1], atlasUv(u2, v1, u3, v2), material);
  addBoxFace(group, [x1, y2, z1, x1, y1, z1, x1, y1, z2, x1, y2, z2], atlasUv(texU, v1, u1, v2), material);
}

async function buildShieldSprite() {
  const texture = await loadTexture('entity/shield/shield_base_nopattern');
  const material = new MeshBasicMaterial({ map: texture, transparent: true, alphaTest: 0.01, side: FrontSide });
  const group = new Group();
  addUnwrappedBox(group, [-6, -11, 1], [6, 11, 2], 0, 0, 12, 22, 1, material);
  addUnwrappedBox(group, [-1, -3, -5], [1, 3, 1], 26, 0, 2, 6, 6, material);
  return group;
}

function applyGuiTransform(group: Group, display: any, isBlockShape: boolean) {
  const gui = (display && display.gui) || (isBlockShape ? DEFAULT_BLOCK_GUI : DEFAULT_GUI_TRANSFORM);
  const rotation = gui.rotation || [0, 0, 0];
  const translation = gui.translation || [0, 0, 0];
  const scale = gui.scale || [1, 1, 1];
  const inner = new Group();
  inner.position.set(-8, -8, -8);
  inner.add(group);
  const outer = new Group();
  outer.rotation.set(MathUtils.degToRad(rotation[0]), MathUtils.degToRad(rotation[1]), MathUtils.degToRad(rotation[2]));
  outer.scale.set(scale[0], scale[1], scale[2]);
  outer.position.set(translation[0], translation[1], translation[2]);
  outer.add(inner);
  return outer;
}

function disposeGroup(group: Group) {
  group.traverse((object) => {
    const mesh = object as Mesh;
    mesh.geometry?.dispose();
    const material = mesh.material;
    if (Array.isArray(material)) material.forEach((item) => item.dispose());
    else material?.dispose();
  });
}

export async function renderItem(name: string) {
  if (itemCache.has(name)) return itemCache.get(name) as Promise<string | null>;
  const promise = (async () => {
    try {
      initThree();
      if (!renderer || !scene || !camera) return null;
      const itemDef = await loadItemDef(name);
      const modelRef = extractModelPath(itemDef.model);
      if (!modelRef) return null;
      const model = await loadModel(stripNs(modelRef) ?? '');
      const isBlockShape = Boolean(model.elements && model.elements.length);
      const tintRgb = extractTintRgb(itemDef.model, name);
      const inner = name === 'shield' ? await buildShieldSprite() : isBlockShape ? await buildElementsModel(model, tintRgb) : await buildLayeredSprite(model, tintRgb);
      const outer = applyGuiTransform(inner, model.display, isBlockShape);
      scene.add(outer);
      renderer.render(scene, camera);
      const dataUrl = renderer.domElement.toDataURL('image/png');
      scene.remove(outer);
      disposeGroup(outer);
      return dataUrl;
    } catch (error) {
      console.warn('itemRenderer: failed', name, error);
      return null;
    }
  })();
  itemCache.set(name, promise);
  return promise;
}
