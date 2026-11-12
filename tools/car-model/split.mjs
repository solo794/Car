// Turns the Sketchfab "Aeolus Yixuan" (CC-BY-4.0, teenlin3) GLB into an app-ready,
// rigged GLB: separate pivoted nodes for 4 doors, trunk, hood, 4 wheels (spin) with
// calipers on the steer node, simplified geometry and smaller textures.
import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import { simplify, weld, prune, dedup, textureCompress } from '@gltf-transform/functions';
import { MeshoptSimplifier } from 'meshoptimizer';
import sharp from 'sharp';

const [inPath, outPath, ratioArg] = process.argv.slice(2);
const RATIO = parseFloat(ratioArg || '0.12');
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);
const doc = await io.read(inPath);
const root = doc.getRoot();
const scene = root.listScenes()[0];
const material = root.listMaterials()[0];

// ---------- base colour texture (to tell red calipers from wheel) ----------
const baseTex = material.getBaseColorTexture();
const { data: px, info: pinfo } = await sharp(Buffer.from(baseTex.getImage())).raw().toBuffer({ resolveWithObject: true });
function texRGB(u, v) {
  u = ((u % 1) + 1) % 1; v = ((v % 1) + 1) % 1;
  const x = Math.min(pinfo.width - 1, Math.floor(u * pinfo.width));
  const y = Math.min(pinfo.height - 1, Math.floor(v * pinfo.height));
  const i = (y * pinfo.width + x) * pinfo.channels;
  return [px[i] / 255, px[i + 1] / 255, px[i + 2] / 255];
}

// ---------- geometry landmarks (model units; car is 1.175 long, +z front, +x = car's left) ----------
const WHEELS = { fl: [1, 0.315], fr: [-1, 0.315], rl: [1, -0.321], rr: [-1, -0.321] };
const WY = 0.080, WR = 0.086;
const FRONT_DOOR = [-0.02, 0.19], REAR_DOOR = [-0.25, -0.02];
const DOOR_X = 0.245;
const TRUNK_HINGE = [-0.45, 0.33], HOOD_HINGE = [0.25, 0.295];

const GLASS_RGB = [0.553, 0.627, 0.604];
function isGlass(cy, uv) {
  if (cy < 0.24) return false;
  const [R, G, B] = texRGB(uv[0], uv[1]);
  return Math.abs(R - GLASS_RGB[0]) + Math.abs(G - GLASS_RGB[1]) + Math.abs(B - GLASS_RGB[2]) < 0.06;
}

function classify(cx, cy, cz, ny, uv) {
  const side = cx >= 0 ? 1 : -1, ax = Math.abs(cx);
  for (const [k, [s, zc]] of Object.entries(WHEELS)) {
    if (s !== side) continue;
    const r = Math.hypot(cz - zc, cy - WY);
    if (r < WR && ax > 0.165) {
      const [R, G, B] = texRGB(uv[0], uv[1]);
      return (R > 0.4 && G < 0.22 && B < 0.22) ? 'caliper_' + k : 'wheel_' + k;
    }
  }
  const nearRearArch = Math.hypot(cz + 0.321, cy - WY) < 0.115;
  const thr = cy < 0.27 ? 0.20 : 0.20 - (cy - 0.27) * 0.4;
  if (cy > 0.075 && cy < 0.385 && ax > thr) {
    // The front edge follows the raked A-pillar above the belt line.
    const frontEdge = cy < 0.27 ? FRONT_DOOR[1] : FRONT_DOOR[1] - (cy - 0.27) * 0.85;
    if (cz > FRONT_DOOR[0] && cz < frontEdge) return side > 0 ? 'door_fl' : 'door_fr';
    if (cz > REAR_DOOR[0] && cz <= FRONT_DOOR[0] && !nearRearArch) return side > 0 ? 'door_rl' : 'door_rr';
  }
  if (cz > 0.26 && cy > 0.205 && cy < 0.300 && ny > 0.45 && ax < 0.235) return 'hood';
  if (cz < TRUNK_HINGE[0] && cy > 0.20 && ax < 0.205 && (ny > 0.35 || cy > 0.29)) return 'trunk';
  return 'body';
}

// Pivot (node translation) of each part; geometry is stored relative to it.
const pivots = {
  body: [0, 0, 0],
  door_fl: [DOOR_X, 0.24, FRONT_DOOR[1]], door_fr: [-DOOR_X, 0.24, FRONT_DOOR[1]],
  door_rl: [DOOR_X, 0.24, FRONT_DOOR[0]], door_rr: [-DOOR_X, 0.24, FRONT_DOOR[0]],
  trunk: [0, TRUNK_HINGE[1], TRUNK_HINGE[0]], hood: [0, HOOD_HINGE[1], HOOD_HINGE[0]],
};
for (const [k, [s, zc]] of Object.entries(WHEELS)) {
  pivots['wheel_' + k] = [s * 0.22, WY, zc];
  pivots['caliper_' + k] = [s * 0.22, WY, zc];
}

// ---------- bake world transforms & collect triangles per part ----------
function worldMatrix(node) {
  return node.getWorldMatrix();
}
function xform(m, x, y, z, w) {
  return [m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w, m[2] * x + m[6] * y + m[10] * z + m[14] * w];
}
const parts = {}; // name -> { pos:[], nor:[], uv:[], tan:[], idx:[] }
function part(name) {
  return parts[name] ??= { pos: [], nor: [], uv: [], tan: [], idx: [], map: new Map() };
}
const meshNodes = [];
scene.traverse(n => { if (n.getMesh()) meshNodes.push(n); });
let primId = 0;
for (const node of meshNodes) {
  const m = worldMatrix(node);
  for (const prim of node.getMesh().listPrimitives()) {
    const P = prim.getAttribute('POSITION').getArray();
    const N = prim.getAttribute('NORMAL')?.getArray();
    const T = prim.getAttribute('TEXCOORD_0')?.getArray();
    const TA = prim.getAttribute('TANGENT')?.getArray();
    const I = prim.getIndices().getArray();
    const vcount = P.length / 3;
    const wp = new Float32Array(P.length), wn = N ? new Float32Array(N.length) : null;
    for (let v = 0; v < vcount; v++) {
      const p = xform(m, P[v * 3], P[v * 3 + 1], P[v * 3 + 2], 1);
      wp.set(p, v * 3);
      if (N) {
        const q = xform(m, N[v * 3], N[v * 3 + 1], N[v * 3 + 2], 0);
        const l = Math.hypot(...q) || 1;
        wn.set([q[0] / l, q[1] / l, q[2] / l], v * 3);
      }
    }
    for (let t = 0; t < I.length; t += 3) {
      const a = I[t], b = I[t + 1], c = I[t + 2];
      const cx = (wp[a * 3] + wp[b * 3] + wp[c * 3]) / 3;
      const cy = (wp[a * 3 + 1] + wp[b * 3 + 1] + wp[c * 3 + 1]) / 3;
      const cz = (wp[a * 3 + 2] + wp[b * 3 + 2] + wp[c * 3 + 2]) / 3;
      // face normal (y component)
      const e1 = [wp[b * 3] - wp[a * 3], wp[b * 3 + 1] - wp[a * 3 + 1], wp[b * 3 + 2] - wp[a * 3 + 2]];
      const e2 = [wp[c * 3] - wp[a * 3], wp[c * 3 + 1] - wp[a * 3 + 1], wp[c * 3 + 2] - wp[a * 3 + 2]];
      const fn = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
      const ny = Math.abs(fn[1]) / (Math.hypot(...fn) || 1);
      const uv = T ? [(T[a * 2] + T[b * 2] + T[c * 2]) / 3, (T[a * 2 + 1] + T[b * 2 + 1] + T[c * 2 + 1]) / 3] : [0, 0];
      const base = classify(cx, cy, cz, ny, uv);
      const glass = !base.startsWith('wheel') && !base.startsWith('caliper') && isGlass(cy, uv);
      const name = glass ? base + '|glass' : base;
      const pp = part(name), pv = pivots[base];
      for (const v of [a, b, c]) {
        const key = primId * 4194304 + v;
        let ni = pp.map.get(key);
        if (ni === undefined) {
          ni = pp.pos.length / 3;
          pp.map.set(key, ni);
          pp.pos.push(wp[v * 3] - pv[0], wp[v * 3 + 1] - pv[1], wp[v * 3 + 2] - pv[2]);
          if (wn) pp.nor.push(wn[v * 3], wn[v * 3 + 1], wn[v * 3 + 2]);
          if (T) pp.uv.push(T[v * 2], T[v * 2 + 1]);
          if (TA) {
            const q = xform(m, TA[v * 4], TA[v * 4 + 1], TA[v * 4 + 2], 0);
            const l = Math.hypot(...q) || 1;
            pp.tan.push(q[0] / l, q[1] / l, q[2] / l, TA[v * 4 + 3]);
          }
        }
        pp.idx.push(ni);
      }
    }
    primId++;
  }
}

// ---------- rebuild the scene ----------
for (const n of scene.listChildren()) n.dispose();
root.listMeshes().forEach(m => m.dispose());
const buffer = root.listBuffers()[0];
// Root scaled to metres (real car length 4.68 m) and named so the app can find it.
const carNode = doc.createNode('car').setScale([3.983, 3.983, 3.983]);
scene.addChild(carNode);
const steerNodes = {};
for (const k of Object.keys(WHEELS)) {
  const sn = doc.createNode('steer_' + k).setTranslation(pivots['wheel_' + k]);
  carNode.addChild(sn);
  steerNodes[k] = sn;
}
// Tinted, see-through glass so the interior shows (the source baked glass into the opaque texture).
const glassMat = doc.createMaterial('glass')
  .setBaseColorFactor([0.02, 0.035, 0.04, 0.32])
  .setMetallicFactor(0.0)
  .setRoughnessFactor(0.04)
  .setAlphaMode('BLEND')
  .setDoubleSided(true);
const partNodes = {};
const counts = {};
for (const [name, pp] of Object.entries(parts)) {
  const isGlassPart = name.endsWith('|glass');
  const baseName = name.replace('|glass', '');
  const prim = doc.createPrimitive().setMaterial(isGlassPart ? glassMat : material)
    .setAttribute('POSITION', doc.createAccessor().setType('VEC3').setArray(new Float32Array(pp.pos)).setBuffer(buffer))
    .setIndices(doc.createAccessor().setType('SCALAR').setArray(new Uint32Array(pp.idx)).setBuffer(buffer));
  if (pp.nor.length) prim.setAttribute('NORMAL', doc.createAccessor().setType('VEC3').setArray(new Float32Array(pp.nor)).setBuffer(buffer));
  if (pp.uv.length && !isGlassPart) prim.setAttribute('TEXCOORD_0', doc.createAccessor().setType('VEC2').setArray(new Float32Array(pp.uv)).setBuffer(buffer));
  if (pp.tan.length && !isGlassPart) prim.setAttribute('TANGENT', doc.createAccessor().setType('VEC4').setArray(new Float32Array(pp.tan)).setBuffer(buffer));
  const door = baseName.match(/^door_(\w\w)$/);
  const nodeName = isGlassPart ? (door ? 'glass_' + door[1] : baseName + '_glass') : baseName;
  const node = doc.createNode(nodeName).setMesh(doc.createMesh(nodeName).addPrimitive(prim));
  const wheel = baseName.match(/^(wheel|caliper)_(\w\w)$/);
  if (wheel) steerNodes[wheel[2]].addChild(node);           // relative to the steer pivot
  else if (isGlassPart && door) (partNodes[baseName] ??= doc.createNode(baseName).setTranslation(pivots[baseName])).addChild(node);
  else if (isGlassPart) carNode.addChild(node.setTranslation(pivots[baseName]));
  else {
    // A door node may already exist (created to hold its window glass): give it the mesh.
    const existing = partNodes[baseName];
    if (existing) { existing.setMesh(node.getMesh()); node.dispose(); }
    else carNode.addChild(partNodes[baseName] = node.setTranslation(pivots[baseName]));
  }
  counts[nodeName] = pp.idx.length / 3;
}
for (const n of Object.values(partNodes)) if (!n.getParentNode()) carNode.addChild(n);
console.log('triangles per part:', counts);

await doc.transform(
  weld(),
  simplify({ simplifier: MeshoptSimplifier, ratio: RATIO, error: 0.0008, lockBorder: true }),
  dedup(),
  prune(),
  textureCompress({ encoder: sharp, targetFormat: 'jpeg', slots: /^baseColor/, resize: [2048, 2048], quality: 88 }),
  textureCompress({ encoder: sharp, targetFormat: 'jpeg', slots: /^(normal|metallicRoughness)/, resize: [1024, 1024], quality: 90 }),
);
root.getAsset().extras = {
  ...(root.getAsset().extras || {}),
  note: 'Rigged/simplified for Shine Launcher. Original: "Aeolus Yixuan" by teenlin3, CC-BY-4.0, https://sketchfab.com/3d-models/aeolus-yixuan-3b164088d4114ae787ff5c1504d75837',
};
let tri = 0;
root.listMeshes().forEach(m => m.listPrimitives().forEach(p => tri += p.getIndices().getCount() / 3));
console.log('final triangles:', tri);
await io.write(outPath, doc);
